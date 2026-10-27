package com.iam816anonymous.meetingsummarizer.transcription

/** Immutable recognized speech with audio-relative millisecond timestamps. */
data class TranscriptSegment(
    val sessionId: String,
    val sequence: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speakerLabel: String? = null,
    val confidence: Float? = null,
    val isFinal: Boolean = true
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(sequence >= 0L) { "sequence must be non-negative" }
        require(startMs >= 0L) { "startMs must be non-negative" }
        require(endMs >= startMs) { "endMs must be >= startMs" }
        require(text.isNotBlank() || !isFinal) { "final transcript text must not be blank" }
        require(confidence == null || confidence in 0.0f..1.0f) {
            "confidence must be between 0 and 1"
        }
    }
}
