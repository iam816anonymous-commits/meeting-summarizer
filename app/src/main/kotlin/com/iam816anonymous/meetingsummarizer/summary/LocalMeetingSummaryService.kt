package com.iam816anonymous.meetingsummarizer.summary

import com.iam816anonymous.meetingsummarizer.transcription.TranscriptSegment
import java.io.File
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

sealed interface MeetingLlmSummaryResult {
    data class Accepted(val text: String, val elapsedMs: Long, val modelName: String) : MeetingLlmSummaryResult
    data class Fallback(val reason: String) : MeetingLlmSummaryResult
}

/**
 * Single-flight, evidence-constrained local summary service. It never replaces the
 * deterministic result on failure. Only a model artifact that passes its pinned SHA-256
 * check may reach the runtime. The runtime is called off the UI thread.
 */
class LocalMeetingSummaryService(
    private val runtime: LocalLlmRuntime = LocalLlmRuntime(),
    private val validator: LocalLlmOutputValidator = LocalLlmOutputValidator()
) {
    private val inferenceLock = ReentrantLock()

    data class ActivationResult(val success: Boolean, val message: String, val elapsedMs: Long = 0L)

    /**
     * Activates a verified artifact by performing real local inference. A downloaded file is
     * not considered a usable brain until initialization and this smoke test both succeed.
     */
    fun activate(model: LocalLlmModel, modelDirectory: File): ActivationResult {
        val file = model.installedFile(modelDirectory)
        val verified = try { model.verifyInstalled(modelDirectory) } catch (_: Exception) { false }
        if (!verified) return ActivationResult(false, "Model size or SHA-256 verification failed. Download it again.")
        return inferenceLock.withLock {
            val result = try {
                runBlocking {
                    runtime.generate(file, "Reply with exactly this token: MODEL_READY", 512)
                }
            } catch (error: Exception) {
                LocalLlmResult.Failure(error.message ?: "Model activation failed.")
            }
            when (result) {
                is LocalLlmResult.Success -> if (result.text.contains("MODEL_READY", ignoreCase = true)) {
                    ActivationResult(true, "Model loaded and generation test passed.", result.elapsedMs)
                } else {
                    ActivationResult(false, "Model generated text but failed the activation self-test.", result.elapsedMs)
                }
                is LocalLlmResult.Unavailable -> ActivationResult(false, result.reason)
                is LocalLlmResult.Failure -> ActivationResult(false, result.reason)
            }
        }
    }

    fun releaseRuntime() {
        runBlocking { runtime.close() }
    }

    fun summarize(model: LocalLlmModel, modelDirectory: File, segments: List<TranscriptSegment>, maxInputCharacters: Int = 5_500): MeetingLlmSummaryResult {
        if (segments.isEmpty()) return MeetingLlmSummaryResult.Fallback("There are no transcript segments to summarize.")
        val ordered = segments.filter { it.isFinal && it.text.isNotBlank() }.sortedWith(compareBy<TranscriptSegment> { it.startMs }.thenBy { it.sequence })
        if (ordered.isEmpty()) return MeetingLlmSummaryResult.Fallback("No final transcript text is available.")
        val file = model.installedFile(modelDirectory)
        val verified = try { model.verifyInstalled(modelDirectory) } catch (error: Exception) { false }
        if (!verified) return MeetingLlmSummaryResult.Fallback("The selected model failed size or SHA-256 verification. Download it again.")
        val transcript = ordered.joinToString("\n") { "[${it.startMs}ms | segment ${it.sequence + 1}] ${it.text.trim()}" }
        val evidenceQuotes = ordered.map { it.text.trim() }.filter { it.length >= 12 }.toSet()
        if (evidenceQuotes.isEmpty()) return MeetingLlmSummaryResult.Fallback("Transcript evidence is too short for safe model validation.")
        val prompt = buildString {
            append("You are an accurate meeting-note assistant. Use only facts explicitly present in the transcript. Do not invent names, dates, owners, or decisions. Mark uncertainty. ")
            append("Return a concise summary, decisions, action items with only explicit owners/deadlines, and open questions. At the end add a line named Evidence quote and copy one complete transcript sentence exactly. Do not output JSON.\n\nTRANSCRIPT:\n")
            append(transcript)
        }
        if (prompt.length > maxInputCharacters) return MeetingLlmSummaryResult.Fallback("Transcript exceeds the safe context limit for this small model. Use the evidence-linked offline summary or shorten the transcript.")
        return inferenceLock.withLock {
            val generated = try { runBlocking { runtime.generate(file, prompt, maxInputCharacters) } }
            catch (error: Exception) { LocalLlmResult.Failure(error.message ?: "Local inference failed.") }
            when (generated) {
                is LocalLlmResult.Success -> when (val validation = validator.validate(generated.text, evidenceQuotes, 8_000)) {
                    is LocalLlmOutputValidator.Validation.Accepted -> MeetingLlmSummaryResult.Accepted(validation.text, generated.elapsedMs, model.displayName)
                    is LocalLlmOutputValidator.Validation.Rejected -> MeetingLlmSummaryResult.Fallback("AI output was not accepted by the evidence check: ${validation.reason}")
                }
                is LocalLlmResult.Unavailable -> MeetingLlmSummaryResult.Fallback(generated.reason)
                is LocalLlmResult.Failure -> MeetingLlmSummaryResult.Fallback(generated.reason)
            }
        }
    }
}