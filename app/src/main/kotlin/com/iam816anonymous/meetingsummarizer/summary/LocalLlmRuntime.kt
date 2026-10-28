package com.iam816anonymous.meetingsummarizer.summary

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

sealed interface LocalLlmResult {
    data class Success(val text: String, val elapsedMs: Long) : LocalLlmResult
    data class Unavailable(val reason: String) : LocalLlmResult
    data class Failure(val reason: String) : LocalLlmResult
}

/** Optional LiteRT-LM adapter for an installed .litertlm file. Invoke off the UI thread. */
class LocalLlmRuntime(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
    suspend fun generate(modelFile: File, prompt: String, maxInputCharacters: Int): LocalLlmResult {
        if (!modelFile.isFile || modelFile.length() <= 0L || !modelFile.name.endsWith(".litertlm", ignoreCase = true)) {
            return LocalLlmResult.Unavailable("No supported local model is installed.")
        }
        if (prompt.isBlank()) return LocalLlmResult.Failure("There is no transcript content to summarize.")
        if (prompt.length > maxInputCharacters) return LocalLlmResult.Failure("Transcript context exceeds this model's input budget.")
        val startedAt = System.nanoTime()
        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    var engine: Engine? = null
                    var initialized = false
                    try {
                        val candidate = Engine(EngineConfig(modelPath = modelFile.absolutePath))
                        candidate.initialize()
                        engine = candidate
                        initialized = true
                        val messages = candidate.createConversation().use { it.sendMessageAsync(prompt).toList() }
                        val output = messages.joinToString(separator = "") { it.toString() }.trim()
                        if (output.isEmpty()) LocalLlmResult.Failure("The model returned an empty response.")
                        else LocalLlmResult.Success(output, (System.nanoTime() - startedAt) / 1_000_000)
                    } finally {
                        // LiteRT-LM documents close as invalid if initialize() itself failed.
                        if (initialized) runCatching { engine?.close() }
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            LocalLlmResult.Failure("Local model timed out; keep using the offline summary.")
        } catch (_: OutOfMemoryError) {
            LocalLlmResult.Failure("Model exceeded available memory; keep using the offline summary.")
        } catch (error: Exception) {
            LocalLlmResult.Failure("Local model failed (" + error.javaClass.simpleName + "); keep using the offline summary.")
        }
    }
    companion object { const val DEFAULT_TIMEOUT_MS = 30_000L }
}
