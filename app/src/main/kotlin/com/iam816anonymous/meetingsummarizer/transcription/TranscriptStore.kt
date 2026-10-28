package com.iam816anonymous.meetingsummarizer.transcription

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** App-private SQLite persistence for transcript segments and resumable audio-chunk jobs. */
class TranscriptStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE transcript_segments (
                session_id TEXT NOT NULL, sequence INTEGER NOT NULL,
                start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL, text TEXT NOT NULL,
                speaker_label TEXT, confidence REAL, is_final INTEGER NOT NULL,
                PRIMARY KEY (session_id, sequence)
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE transcription_jobs (
                session_id TEXT NOT NULL, audio_path TEXT NOT NULL, status TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT, updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (session_id, audio_path)
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX transcript_session_time ON transcript_segments(session_id, start_ms)")
        db.execSQL("CREATE INDEX transcription_jobs_status ON transcription_jobs(status, updated_at_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Additive migration: preserve all existing transcript and job rows.
        if (oldVersion < 2) db.execSQL("ALTER TABLE transcript_segments ADD COLUMN is_edited INTEGER NOT NULL DEFAULT 0")
        if (oldVersion > newVersion) throw IllegalStateException("Cannot downgrade database from $oldVersion to $newVersion")
    }

    fun saveSegment(segment: TranscriptSegment) {
        // Human corrections are source-of-truth and must not be overwritten by a later ASR retry.
        readableDatabase.query("transcript_segments", arrayOf("is_edited"),
            "session_id = ? AND sequence = ?", arrayOf(segment.sessionId, segment.sequence.toString()),
            null, null, null).use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) != 0) return
        }
        val values = ContentValues().apply {
            put("session_id", segment.sessionId)
            put("sequence", segment.sequence)
            put("start_ms", segment.startMs)
            put("end_ms", segment.endMs)
            put("text", segment.text)
            put("speaker_label", segment.speakerLabel)
            if (segment.confidence == null) putNull("confidence") else put("confidence", segment.confidence)
            put("is_final", if (segment.isFinal) 1 else 0)
            put("is_edited", 0)
        }
        val rowId = writableDatabase.insertWithOnConflict(
            "transcript_segments", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
        check(rowId != -1L) { "Failed to persist transcript segment sequence ${segment.sequence}" }
    }

    fun deleteSegments(sessionId: String) {
        writableDatabase.delete("transcript_segments", "session_id = ?", arrayOf(sessionId))
    }

    fun deleteUneditedSegments(sessionId: String) {
        writableDatabase.delete("transcript_segments", "session_id = ? AND is_edited = 0", arrayOf(sessionId))
    }

    fun updateSegmentText(sessionId: String, sequence: Long, correctedText: String): Boolean {
        val normalized = correctedText.trim()
        if (sessionId.isBlank() || normalized.isBlank() || normalized.length > MAX_SEGMENT_TEXT_LENGTH) return false
        val values = ContentValues().apply { put("text", normalized); put("is_edited", 1); put("is_final", 1) }
        return writableDatabase.update("transcript_segments", values,
            "session_id = ? AND sequence = ?", arrayOf(sessionId, sequence.toString())) == 1
    }

    /** Parameterized, case-insensitive search across all stored sessions. */
    fun searchSegments(query: String, limit: Int = 100): List<TranscriptSegment> {
        val term = query.trim()
        if (term.isEmpty() || limit <= 0) return emptyList()
        val result = mutableListOf<TranscriptSegment>()
        readableDatabase.query("transcript_segments",
            arrayOf("session_id", "sequence", "start_ms", "end_ms", "text", "speaker_label", "confidence", "is_final"),
            "instr(lower(text), lower(?)) > 0", arrayOf(term), null, null,
            "session_id ASC, start_ms ASC, sequence ASC", limit.coerceAtMost(MAX_SEARCH_RESULTS).toString()
        ).use { cursor -> while (cursor.moveToNext()) result += cursorToSegment(cursor) }
        return result
    }

    fun deleteMeeting(sessionId: String) {
        require(sessionId.isNotBlank())
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("transcript_segments", "session_id = ?", arrayOf(sessionId))
            writableDatabase.delete("transcription_jobs", "session_id = ?", arrayOf(sessionId))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun listSegments(sessionId: String): List<TranscriptSegment> {
        val result = mutableListOf<TranscriptSegment>()
        readableDatabase.query(
            "transcript_segments",
            arrayOf("session_id", "sequence", "start_ms", "end_ms", "text", "speaker_label", "confidence", "is_final"),
            "session_id = ?", arrayOf(sessionId), null, null, "start_ms ASC, sequence ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val confidenceIndex = cursor.getColumnIndexOrThrow("confidence")
                result += cursorToSegment(cursor)
            }
        }
        return result
    }

    fun enqueueChunk(sessionId: String, audioPath: String) {
        require(sessionId.isNotBlank() && audioPath.isNotBlank())
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("audio_path", audioPath)
            put("status", JobStatus.PENDING.name)
            put("attempts", 0)
            put("updated_at_ms", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "transcription_jobs", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun markChunkRunning(sessionId: String, audioPath: String) {
        writableDatabase.execSQL(
            "UPDATE transcription_jobs SET status = ?, last_error = NULL, attempts = attempts + 1, updated_at_ms = ? WHERE session_id = ? AND audio_path = ?",
            arrayOf(JobStatus.RUNNING.name, System.currentTimeMillis(), sessionId, audioPath)
        )
    }

    fun markChunkComplete(sessionId: String, audioPath: String) =
        updateJob(sessionId, audioPath, JobStatus.COMPLETE, null)

    fun markChunkFailed(sessionId: String, audioPath: String, error: String) =
        updateJob(sessionId, audioPath, JobStatus.FAILED, error.take(MAX_ERROR_LENGTH))

    /** RUNNING means the worker may have died before recording a terminal state. */
    fun recoverInterruptedJobs(): Int {
        val values = ContentValues().apply {
            put("status", JobStatus.PENDING.name)
            put("updated_at_ms", System.currentTimeMillis())
        }
        return writableDatabase.update(
            "transcription_jobs", values, "status = ?",
            arrayOf(JobStatus.RUNNING.name)
        )
    }

    fun listPendingChunks(): List<PendingChunk> {
        val result = mutableListOf<PendingChunk>()
        readableDatabase.query(
            "transcription_jobs",
            arrayOf("session_id", "audio_path", "attempts", "last_error"),
            "status IN (?, ?)", arrayOf(JobStatus.PENDING.name, JobStatus.FAILED.name),
            null, null, "updated_at_ms ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += PendingChunk(
                    sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id")),
                    audioPath = cursor.getString(cursor.getColumnIndexOrThrow("audio_path")),
                    attempts = cursor.getInt(cursor.getColumnIndexOrThrow("attempts")),
                    lastError = cursor.getString(cursor.getColumnIndexOrThrow("last_error"))
                )
            }
        }
        return result
    }

    private fun cursorToSegment(cursor: android.database.Cursor): TranscriptSegment {
        val confidenceIndex = cursor.getColumnIndexOrThrow("confidence")
        return TranscriptSegment(
            sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id")),
            sequence = cursor.getLong(cursor.getColumnIndexOrThrow("sequence")),
            startMs = cursor.getLong(cursor.getColumnIndexOrThrow("start_ms")),
            endMs = cursor.getLong(cursor.getColumnIndexOrThrow("end_ms")),
            text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
            speakerLabel = cursor.getString(cursor.getColumnIndexOrThrow("speaker_label")),
            confidence = if (cursor.isNull(confidenceIndex)) null else cursor.getFloat(confidenceIndex),
            isFinal = cursor.getInt(cursor.getColumnIndexOrThrow("is_final")) != 0
        )
    }

    private fun updateJob(sessionId: String, audioPath: String, status: JobStatus, error: String?) {
        val values = ContentValues().apply {
            put("status", status.name)
            put("updated_at_ms", System.currentTimeMillis())
            if (error == null) putNull("last_error") else put("last_error", error)
        }
        writableDatabase.update(
            "transcription_jobs", values, "session_id = ? AND audio_path = ?",
            arrayOf(sessionId, audioPath)
        )
    }

    enum class JobStatus { PENDING, RUNNING, COMPLETE, FAILED }

    data class PendingChunk(
        val sessionId: String,
        val audioPath: String,
        val attempts: Int,
        val lastError: String?
    )

    companion object {
        private const val DATABASE_NAME = "meeting_transcripts.db"
        private const val DATABASE_VERSION = 2
        private const val MAX_ERROR_LENGTH = 500
        private const val MAX_SEGMENT_TEXT_LENGTH = 10_000
        private const val MAX_SEARCH_RESULTS = 500
    }
}
