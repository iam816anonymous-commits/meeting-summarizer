package com.iam816anonymous.meetingsummarizer.summary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest

data class VerifiedModelArtifact(
    val id: String,
    val downloadUrl: String,
    val fileName: String,
    val expectedBytes: Long,
    val sha256: String,
    val sizeIsExact: Boolean = true
)

sealed interface ModelInstallResult {
    data class Installed(val file: File) : ModelInstallResult
    data class Rejected(val reason: String) : ModelInstallResult
}

/**
 * Explicit opt-in HTTPS model installer. Downloads are retryable and resumable, but a model
 * is exposed only after its size bounds and pinned SHA-256 digest have been validated.
 */
class VerifiedModelDownloader {
    suspend fun install(
        artifact: VerifiedModelArtifact,
        modelDirectory: File,
        freeStorageBytes: Long,
        userOptedIn: Boolean,
        onProgress: (writtenBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ModelInstallResult = withContext(Dispatchers.IO) {
        if (!userOptedIn) return@withContext ModelInstallResult.Rejected("Model download requires explicit opt-in.")
        if (!isAllowedUrl(artifact.downloadUrl)) return@withContext ModelInstallResult.Rejected("Only approved HTTPS model hosts are allowed.")
        if (artifact.expectedBytes <= 0L || artifact.expectedBytes > MAX_MODEL_BYTES) return@withContext ModelInstallResult.Rejected("Model size is outside the allowed limit.")
        if (freeStorageBytes < artifact.expectedBytes + MIN_WORKING_SPACE_BYTES) return@withContext ModelInstallResult.Rejected("Not enough free storage for the model and working space.")
        if (!artifact.fileName.endsWith(".litertlm", true) || artifact.fileName.contains("/") || artifact.fileName.contains("\\")) {
            return@withContext ModelInstallResult.Rejected("Invalid model file name.")
        }
        if (!artifact.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) return@withContext ModelInstallResult.Rejected("A valid catalog SHA-256 is required.")

        val destination = File(modelDirectory, artifact.fileName)
        val partial = File(modelDirectory, artifact.fileName + ".part")
        val backup = File(modelDirectory, artifact.fileName + ".previous")
        try {
            if (!modelDirectory.exists() && !modelDirectory.mkdirs()) throw IOException("Could not create model directory.")
            ReliableModelDownload.download(
                initialUrl = artifact.downloadUrl,
                partialFile = partial,
                expectedBytes = if (artifact.sizeIsExact) artifact.expectedBytes else -1L,
                maxBytes = artifact.expectedBytes,
                allowedExactHosts = emptySet(),
                allowedHostSuffixes = setOf(".huggingface.co", ".hf.co", ".xethub.hf.co"),
                onProgress = { progress -> onProgress(progress.writtenBytes, progress.totalBytes) }
            )
            if (partial.length() <= 0L || partial.length() > artifact.expectedBytes ||
                (artifact.sizeIsExact && partial.length() != artifact.expectedBytes)) {
                partial.delete()
                throw IOException("Model size verification failed. The partial download was discarded.")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            partial.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hash.equals(artifact.sha256, true)) {
                partial.delete()
                throw IOException("Model checksum verification failed. The partial download was discarded; please retry.")
            }

            backup.delete()
            if (destination.exists() && !destination.renameTo(backup)) {
                throw IOException("Could not safely preserve the previously installed model.")
            }
            if (!partial.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                throw IOException("Could not finalize installed model.")
            }
            backup.delete()
            ModelInstallResult.Installed(destination)
        } catch (error: Exception) {
            // Preserve only a resumable partial after transient transport retries are exhausted.
            if (error.message?.startsWith("Model download failed after") != true) partial.delete()
            ModelInstallResult.Rejected(error.message ?: "Model download failed.")
        }
    }

    private fun isAllowedUrl(value: String): Boolean = runCatching {
        val url = URL(value)
        val host = url.host.lowercase()
        url.protocol.equals("https", true) &&
            (host == "huggingface.co" || host.endsWith(".huggingface.co") ||
                host == "hf.co" || host.endsWith(".hf.co") || host.endsWith(".xethub.hf.co"))
    }.getOrDefault(false)

    companion object {
        const val MAX_MODEL_BYTES = 10L * 1024 * 1024 * 1024
        const val MIN_WORKING_SPACE_BYTES = 256L * 1024 * 1024
    }
}
