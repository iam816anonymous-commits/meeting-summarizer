package com.iam816anonymous.meetingsummarizer.transcription

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/**
 * Offline Whisper engine for a verified Whisper pack.
 *
 * Expected directory layout:
 *   encoder.int8.onnx
 *   decoder.int8.onnx
 *   tokens.txt
 *
 * This deliberately does not download files itself. The model manager must first verify
 * the exact revision, byte size, and SHA-256 for every pack file before activation.
 */
class SherpaOnnxWhisperSpeechEngine(
    private val languageCode: String,
    private val modelId: String
) : SpeechEngine {
    override val engineId: String = "sherpa-onnx-whisper-$modelId"
    private val decoder = AndroidAudioDecoder()

    init {
        require(languageCode.matches(Regex("^[a-z]{2,3}$"))) { "Invalid Whisper language code." }
        require(modelId.matches(Regex("^[a-zA-Z0-9._-]+$"))) { "Invalid model id." }
    }

    override fun isReady(modelDirectory: File): Boolean =
        listOf("encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt")
            .all { File(modelDirectory, it).isFile && File(modelDirectory, it).length() > 0L }

    override fun transcribe(
        audioFile: File,
        sessionId: String,
        modelDirectory: File,
        sequenceOffset: Long,
        timeOffsetMs: Long,
        onSegment: (TranscriptSegment) -> Unit
    ): SpeechEngineResult {
        if (!audioFile.isFile || audioFile.length() <= 0L) {
            return SpeechEngineResult.Failure(
                SpeechEngineResult.FailureCode.UNSUPPORTED_AUDIO,
                "The audio file is missing or empty.",
                false
            )
        }
        if (!isReady(modelDirectory)) {
            return SpeechEngineResult.Failure(
                SpeechEngineResult.FailureCode.MODEL_MISSING,
                "The selected Whisper model is not installed or failed its file checks.",
                false
            )
        }

        var recognizer: OfflineRecognizer? = null
        var sequence = sequenceOffset
        return try {
            val model = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(modelDirectory, "encoder.int8.onnx").absolutePath,
                    decoder = File(modelDirectory, "decoder.int8.onnx").absolutePath,
                    language = languageCode,
                    task = "transcribe",
                    tailPaddings = 300
                ),
                tokens = File(modelDirectory, "tokens.txt").absolutePath,
                modelType = "whisper",
                numThreads = 2
            )
            recognizer = OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                    modelConfig = model
                )
            )
            decoder.decodeChunks(audioFile) { samples, startMs, endMs ->
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Transcription was cancelled.")
                }
                val stream = recognizer!!.createStream()
                val text = try {
                    stream.acceptWaveform(samples, 16_000)
                    recognizer!!.decode(stream)
                    recognizer!!.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
                if (text.isNotBlank()) {
                    onSegment(
                        TranscriptSegment(
                            sessionId = sessionId,
                            sequence = sequence++,
                            startMs = timeOffsetMs + startMs,
                            endMs = timeOffsetMs + endMs,
                            text = text
                        )
                    )
                }
            }
            SpeechEngineResult.Success((sequence - sequenceOffset).toInt())
        } catch (_: OutOfMemoryError) {
            SpeechEngineResult.Failure(
                SpeechEngineResult.FailureCode.OUT_OF_MEMORY,
                "The selected Whisper model exceeded available memory. Select a smaller model or close other apps.",
                true
            )
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            SpeechEngineResult.Failure(
                SpeechEngineResult.FailureCode.CANCELLED,
                "Transcription was cancelled. Saved transcript segments were preserved.",
                true
            )
        } catch (error: UnsupportedOperationException) {
            SpeechEngineResult.Failure(
                SpeechEngineResult.FailureCode.UNSUPPORTED_AUDIO,
                error.message ?: "This audio format is not supported.",
                false
            )
        } catch (error: Exception) {
            SpeechEngineResult.Failure(
                SpeechEngineResult.FailureCode.ENGINE_ERROR,
                (error.message ?: error.javaClass.simpleName).take(500),
                true
            )
        } finally {
            runCatching { recognizer?.release() }
        }
    }
}
