package com.iam816anonymous.meetingsummarizer.transcription

import java.io.File

/**
 * Local-only ASR contract. Implementations must not make network requests.
 * Call transcribe() from a worker thread and honor interruption/cancellation.
 * Model assets are app-managed files, not bundled in the APK by default.
 */
interface SpeechEngine {
    val engineId: String
    fun isReady(modelDirectory: File): Boolean

    fun transcribe(
        audioFile: File,
        sessionId: String,
        modelDirectory: File,
        onSegment: (TranscriptSegment) -> Unit
    ): SpeechEngineResult
}

sealed class SpeechEngineResult {
    data class Success(val segmentCount: Int) : SpeechEngineResult() {
        init { require(segmentCount >= 0) }
    }

    data class Failure(
        val code: FailureCode,
        val message: String,
        val retryable: Boolean
    ) : SpeechEngineResult()

    enum class FailureCode {
        MODEL_MISSING, UNSUPPORTED_AUDIO, OUT_OF_MEMORY, CANCELLED, ENGINE_ERROR
    }
}
