package com.iam816anonymous.meetingsummarizer.summary

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

sealed interface LocalLlmResult {
    data class Success(val text: String, val elapsedMs: Long) : LocalLlmResult
    data class Unavailable(val reason: String) : LocalLlmResult
    data class Failure(val reason: String) : LocalLlmResult
}

/**
 * Process-scoped LiteRT-LM runtime. It keeps one initialized engine for the active model,
 * serializes access to the native engine, and closes/replaces it when a different artifact
 * is selected. Call from background work; never from the UI thread.
 */
class LocalLlmRuntime(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
    private val engineMutex = Mutex()
    private var engine: Engine? = null
    private var loadedCanonicalPath: String? = null
    private var loadedLength: Long = -1L
    private var loadedModified: Long = -1L

    suspend fun generate(modelFile: File, prompt: String, maxInputCharacters: Int): LocalLlmResult {
        if (!modelFile.isFile || modelFile.length() <= 0L || !modelFile.name.endsWith(".litertlm", ignoreCase = true)) {
            return LocalLlmResult.Unavailable("No supported local model is installed.")
        }
        if (prompt.isBlank()) return LocalLlmResult.Failure("There is no transcript content to summarize.")
        if (maxInputCharacters <= 0 || prompt.length > maxInputCharacters) {
            return LocalLlmResult.Failure("Transcript context exceeds this model's input budget.")
        }
        val startedAt = System.nanoTime()
        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    engineMutex.withLock {
                        val canonicalPath = modelFile.canonicalPath
                        val currentLength = modelFile.length()
                        val currentModified = modelFile.lastModified()
                        if (engine != null && (loadedCanonicalPath != canonicalPath ||
                                loadedLength != currentLength || loadedModified != currentModified)) {
                            closeEngineLocked()
                        }
                        if (engine == null) {
                            val candidate = Engine(EngineConfig(modelPath = canonicalPath))
                            try {
                                candidate.initialize()
                                engine = candidate
                                loadedCanonicalPath = canonicalPath
                                loadedLength = currentLength
                                loadedModified = currentModified
                            } catch (error: Throwable) {
                                // LiteRT-LM must not be closed if initialize() did not complete.
                                throw error
                            }
                        }
                        val activeEngine = checkNotNull(engine)
                        val output = activeEngine.createConversation().use { conversation ->
                            conversation.sendMessageAsync(prompt).toList().joinToString(separator = "") { it.toString() }.trim()
                        }
                        if (output.isEmpty()) LocalLlmResult.Failure("The model returned an empty response.")
                        else LocalLlmResult.Success(output, (System.nanoTime() - startedAt) / 1_000_000)
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            LocalLlmResult.Failure("Local model timed out; the offline summary remains available.")
        } catch (_: OutOfMemoryError) {
            // The engine may hold native allocations after an OOM. Release it before fallback.
            runCatching { close() }
            LocalLlmResult.Failure("Model exceeded available memory; the offline summary remains available.")
        } catch (error: Exception) {
            LocalLlmResult.Failure("Local model failed (" + error.javaClass.simpleName + "): " +
                (error.message ?: "initialization or inference error") + ". The offline summary remains available.")
        }
    }

    /** Releases native memory when the owning app/service is shutting down. */
    suspend fun close() {
        withContext(Dispatchers.IO) {
            engineMutex.withLock { closeEngineLocked() }
        }
    }

    private fun closeEngineLocked() {
        runCatching { engine?.close() }
        engine = null
        loadedCanonicalPath = null
        loadedLength = -1L
        loadedModified = -1L
    }

    companion object { const val DEFAULT_TIMEOUT_MS = 60_000L }
}
