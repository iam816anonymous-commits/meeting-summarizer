package com.iam816anonymous.meetingsummarizer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties

/** Explicitly started microphone service with private, bounded M4A segments. */
class RecordingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var sessionDirectory: File? = null
    private var currentAudioFile: File? = null
    private var currentSegmentStartedAt = 0L
    private var segmentNumber = 0
    private var sessionId: String? = null
    private var recording = false
    private var currentState = RecordingLifecycleState.IDLE
    private var currentError: String? = null

    override fun onCreate() {
        super.onCreate()
        val saved = getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        currentState = runCatching {
            RecordingLifecycleState.valueOf(saved.getString(KEY_STATE, RecordingLifecycleState.IDLE.name)!!)
        }.getOrDefault(RecordingLifecycleState.IDLE)
        currentError = saved.getString(KEY_ERROR, null)
        sessionId = saved.getString(KEY_SESSION_ID, null)
    }

    private val rotateSegment = object : Runnable {
        override fun run() {
            if (!recording) return
            if (!finishCurrentSegment(markComplete = true)) {
                failAndStop("Audio segment could not be finalized safely.")
                return
            }
            if (!hasEnoughStorage()) {
                failAndStop("Recording stopped because available storage is too low. Completed segments were preserved.")
                return
            }
            try {
                startSegment()
                handler.postDelayed(this, SEGMENT_LENGTH_MS)
            } catch (error: Exception) {
                failAndStop("Could not start the next audio segment: " + (error.message ?: error.javaClass.simpleName))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_QUERY_STATE -> {
                if (!recording && currentState in setOf(
                        RecordingLifecycleState.PREPARING,
                        RecordingLifecycleState.RECORDING,
                        RecordingLifecycleState.STOPPING
                    )
                ) {
                    recoverInterruptedSegments(this)
                    publishState(
                        RecordingLifecycleState.INTERRUPTED,
                        "The recording service is no longer active. Any unfinished audio segment was marked incomplete."
                    )
                } else {
                    publishState(currentState, currentError)
                }
                if (!recording) {
                    removeRecordingNotification()
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                if (recording) {
                    stopRecordingNormally()
                } else {
                    if (currentState in setOf(
                            RecordingLifecycleState.PREPARING,
                            RecordingLifecycleState.RECORDING,
                            RecordingLifecycleState.STOPPING
                        )
                    ) {
                        recoverInterruptedSegments(this)
                        publishState(
                            RecordingLifecycleState.INTERRUPTED,
                            "Stop was received after the recorder stopped unexpectedly. Any unfinished segment was marked incomplete."
                        )
                    } else {
                        publishState(currentState, currentError)
                    }
                    removeRecordingNotification()
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (recording) {
                    publishState(RecordingLifecycleState.RECORDING)
                    return START_NOT_STICKY
                }
                try {
                    createNotificationChannel()
                    startAsForeground()
                    beginRecording()
                } catch (error: Exception) {
                    publishState(RecordingLifecycleState.ERROR, error.message ?: error.javaClass.simpleName)
                    removeRecordingNotification()
                    stopSelf()
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun beginRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission is not granted.")
        }
        if (!hasEnoughStorage()) throw IllegalStateException("At least 50 MB of free storage is required to begin recording.")

        publishState(RecordingLifecycleState.PREPARING)
        sessionId = "meeting-" + System.currentTimeMillis()
        sessionDirectory = File(File(filesDir, RECORDINGS_DIRECTORY), sessionId!!).apply {
            if (!exists() && !mkdirs()) throw IllegalStateException("Could not create private recording directory.")
        }
        segmentNumber = 0
        startSegment()
        recording = true
        handler.postDelayed(rotateSegment, SEGMENT_LENGTH_MS)
        publishState(RecordingLifecycleState.RECORDING)
    }

    private fun startSegment() {
        if (!hasEnoughStorage()) throw IllegalStateException("Available storage is below the 50 MB safety threshold.")
        val directory = sessionDirectory ?: throw IllegalStateException("Recording session directory is missing.")
        segmentNumber += 1
        val file = File(directory, "segment-%04d.m4a".format(segmentNumber))
        currentAudioFile = file
        currentSegmentStartedAt = System.currentTimeMillis()
        if (!writeMetadata(file, "RECORDING", currentSegmentStartedAt, null, null, null)) {
            currentAudioFile = null
            throw IllegalStateException("Could not persist audio segment metadata.")
        }

        val next = MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(44_100)
            next.setAudioEncodingBitRate(96_000)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
            recorder = next
        } catch (error: Exception) {
            try { next.reset() } catch (_: Exception) {}
            try { next.release() } catch (_: Exception) {}
            writeMetadata(file, "INCOMPLETE", currentSegmentStartedAt, System.currentTimeMillis(), null, error.message)
            currentAudioFile = null
            throw error
        }
    }

    private fun finishCurrentSegment(markComplete: Boolean): Boolean {
        val activeRecorder = recorder
        val audioFile = currentAudioFile
        if (activeRecorder == null || audioFile == null) return true
        recorder = null
        var stopped = false
        try {
            activeRecorder.stop()
            stopped = true
        } catch (_: RuntimeException) {
            stopped = false
        } finally {
            try { activeRecorder.reset() } catch (_: Exception) {}
            try { activeRecorder.release() } catch (_: Exception) {}
        }
        val endedAt = System.currentTimeMillis()
        val validFile = audioFile.isFile && audioFile.length() > 0L
        val complete = markComplete && stopped && validFile
        val checksum = if (validFile) sha256(audioFile) else null
        val metadataSaved = writeMetadata(
            audioFile,
            if (complete) "COMPLETE" else "INCOMPLETE",
            currentSegmentStartedAt,
            endedAt,
            checksum,
            if (complete) null else "Recorder did not finalize a valid segment."
        )
        currentAudioFile = null
        // Never report a segment as safely finalized when its integrity sidecar could not be saved.
        return (complete && metadataSaved) || !markComplete
    }

    private fun stopRecordingNormally() {
        if (!recording) return
        recording = false
        handler.removeCallbacks(rotateSegment)
        publishState(RecordingLifecycleState.STOPPING)
        val complete = finishCurrentSegment(markComplete = true)
        if (complete) {
            publishState(RecordingLifecycleState.COMPLETED)
        } else {
            publishState(RecordingLifecycleState.INTERRUPTED, "The final audio segment could not be finalized. Earlier completed segments remain available.")
        }
        removeRecordingNotification()
    }

    private fun failAndStop(message: String) {
        recording = false
        handler.removeCallbacks(rotateSegment)
        finishCurrentSegment(markComplete = false)
        publishState(RecordingLifecycleState.ERROR, message)
        removeRecordingNotification()
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(rotateSegment)
        if (recording) {
            recording = false
            finishCurrentSegment(markComplete = false)
            publishState(RecordingLifecycleState.INTERRUPTED, "Recording service ended unexpectedly; the current segment is marked incomplete.")
        }
        removeRecordingNotification()
        super.onDestroy()
    }

    /**
     * Remove the foreground-service notification explicitly as well as detaching it.
     * This makes cleanup idempotent across the in-app stop action, notification action,
     * query-state recovery, startup failure, and service destruction.
     */
    private fun removeRecordingNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun hasEnoughStorage(): Boolean = try {
        StatFs(filesDir.absolutePath).availableBytes >= MIN_FREE_BYTES
    } catch (_: Exception) {
        false
    }

    private fun writeMetadata(
        audioFile: File,
        status: String,
        startedAt: Long,
        endedAt: Long?,
        checksum: String?,
        error: String?
    ): Boolean {
        val values = Properties().apply {
            setProperty("sessionId", sessionId ?: "unknown")
            setProperty("segment", audioFile.name)
            setProperty("status", status)
            setProperty("startedAtEpochMs", startedAt.toString())
            endedAt?.let { setProperty("endedAtEpochMs", it.toString()) }
            setProperty("sizeBytes", if (audioFile.exists()) audioFile.length().toString() else "0")
            checksum?.let { setProperty("sha256", it) }
            error?.let { setProperty("error", it.take(300)) }
        }
        val target = File(audioFile.parentFile, audioFile.name + ".meta")
        val temporary = File(audioFile.parentFile, audioFile.name + ".meta.tmp")
        return try {
            temporary.outputStream().use { values.store(it, "Meeting Summarizer audio segment metadata") }
            if (target.exists() && !target.delete()) throw IllegalStateException("Could not replace segment metadata.")
            if (!temporary.renameTo(target)) throw IllegalStateException("Could not atomically write segment metadata.")
            true
        } catch (_: Exception) {
            // Missing sidecars are unverified, never implicitly complete.
            false
        }
    }

    private fun sha256(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Meeting recording", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Visible status and stop control for active microphone recording."
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 1002, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 1001, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setContentTitle("Meeting recording is active")
            .setContentText("Audio is being saved privately on this device.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Stop recording", stopPendingIntent).build())
        if (openPendingIntent != null) builder.setContentIntent(openPendingIntent)
        return builder.build()
    }

    private fun publishState(state: RecordingLifecycleState, error: String? = null) {
        currentState = state
        currentError = error
        getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, state.name)
            .putString(KEY_ERROR, error)
            .putString(KEY_SESSION_ID, sessionId)
            .apply()
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).apply {
            putExtra(EXTRA_STATE, state.name)
            if (error != null) putExtra(EXTRA_ERROR, error)
            putExtra(EXTRA_SESSION_ID, sessionId)
        })
    }

    companion object {
        const val ACTION_START = "com.iam816anonymous.meetingsummarizer.action.START_RECORDING"
        const val ACTION_STOP = "com.iam816anonymous.meetingsummarizer.action.STOP_RECORDING"
        const val ACTION_QUERY_STATE = "com.iam816anonymous.meetingsummarizer.action.QUERY_RECORDING_STATE"
        const val ACTION_STATE = "com.iam816anonymous.meetingsummarizer.action.RECORDING_STATE"
        const val EXTRA_STATE = "recording_state"
        const val EXTRA_ERROR = "recording_error"
        const val EXTRA_SESSION_ID = "recording_session_id"
        private const val STATE_PREFS = "recording_state"
        private const val KEY_STATE = "state"
        private const val KEY_ERROR = "error"
        private const val KEY_SESSION_ID = "session_id"
        private const val CHANNEL_ID = "meeting_recording"
        private const val NOTIFICATION_ID = 4201
        private const val RECORDINGS_DIRECTORY = "recordings"
        private const val MIN_FREE_BYTES = 50L * 1024L * 1024L
        private const val SEGMENT_LENGTH_MS = 5L * 60L * 1000L

        fun recoverInterruptedSegments(context: Context) {
            val root = File(context.filesDir, RECORDINGS_DIRECTORY)
            if (!root.exists()) return
            root.walkTopDown().filter { it.isFile && it.name.endsWith(".m4a.meta") }.forEach { meta ->
                try {
                    val properties = Properties()
                    meta.inputStream().use { properties.load(it) }
                    if (properties.getProperty("status") == "RECORDING") {
                        properties.setProperty("status", "INCOMPLETE")
                        properties.setProperty("recoveredAtEpochMs", System.currentTimeMillis().toString())
                        properties.setProperty("error", "Previous recording did not record a confirmed clean stop.")
                        val temp = File(meta.parentFile, meta.name + ".tmp")
                        temp.outputStream().use { properties.store(it, "Recovered interrupted audio segment") }
                        if (meta.exists()) meta.delete()
                        temp.renameTo(meta)
                    }
                } catch (_: Exception) {
                    // Corrupt sidecars are not assumed complete.
                }
            }
        }
    }
}
