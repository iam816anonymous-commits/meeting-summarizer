package com.iam816anonymous.meetingsummarizer.transcription

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/** Offline-only English recognizer backed by sherpa-onnx's INT8 small Zipformer model. */
class SherpaOnnxEnglishSpeechEngine : SpeechEngine {
    override val engineId: String = "sherpa-onnx-zipformer-small-en-int8"
    private val decoder = AndroidAudioDecoder()

    override fun isReady(modelDirectory: File): Boolean =
        listOf(
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.int8.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
            "tokens.txt"
        ).all { File(modelDirectory, it).isFile }

    override fun transcribe(
        audioFile: File,
        sessionId: String,
        modelDirectory: File,
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
                "Install the offline English model before transcribing.",
                false
            )
        }

        var recognizer: OfflineRecognizer? = null
        var count = 0L
        return try {
            val model = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(modelDirectory, "encoder-epoch-99-avg-1.int8.onnx").absolutePath,
                    decoder = File(modelDirectory, "decoder-epoch-99-avg-1.int8.onnx").absolutePath,
                    joiner = File(modelDirectory, "joiner-epoch-99-avg-1.int8.onnx").absolutePath
                ),
                tokens = File(modelDirectory, "tokens.txt").absolutePath,
                modelType = "transducer",
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
                            sequence = count,
                            startMs = startMs,
                            endMs = endMs,
                            text = text
                        )
                    )
                    count++
                }
            }
            SpeechEngineResult.Success(count.toInt())
        } catch (_: OutOfMemoryError) {
            SpeechEngineResult.Failure(
                SpeechEngineResult.FailureCode.OUT_OF_MEMORY,
                "The offline model exceeded available memory. Try a shorter recording or close other apps.",
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
