package com.iam816anonymous.meetingsummarizer.summary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
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
 * Explicit opt-in, HTTPS-only model installer. It follows a small number of HTTPS redirects
 * for Hugging Face's object storage, caps bytes, hashes while streaming, and only atomically
 * exposes a file after catalog size-bound and SHA-256 validation.
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
        try {
            if (!modelDirectory.exists() && !modelDirectory.mkdirs()) throw IllegalStateException("Could not create model directory.")
            ReliableModelDownload.download(
                initialUrl = artifact.downloadUrl,
                partialFile = partial,
                expectedBytes = if (artifact.sizeIsExact) artifact.expectedBytes else -1L,
                maxBytes = artifact.expectedBytes,
                allowedExactHosts = emptySet(),
                allowedHostSuffixes = setOf(".huggingface.co", ".xethub.hf.co"),
                onProgress = { progress -> onProgress(progress.writtenBytes, progress.totalBytes) }
            )
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
                throw IllegalStateException("Model checksum verification failed. The partial download was discarded; please retry.")
            }
            if (destination.exists() && !destination.delete()) throw IllegalStateException("Could not replace the previous model.")
            if (!partial.renameTo(destination)) throw IllegalStateException("Could not finalize installed model.")
            ModelInstallResult.Installed(destination)
        } catch (error: Exception) {
            // Keep a partial only after retryable transport failure so the next attempt can resume.
            if (error.message?.startsWith("Model download failed after") != true) partial.delete()
            ModelInstallResult.Rejected(error.message ?: "Model download failed.")
        }
    private fun openFollowingRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) { hop ->
            if (!isAllowedUrl(currentUrl)) throw IllegalStateException("Redirect target is not an approved HTTPS model host.")
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
            connection.connect()
            if (connection.responseCode in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || hop == MAX_REDIRECTS) throw IllegalStateException("Model host returned too many or invalid redirects.")
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return connection
            }
        }
        throw IllegalStateException("Model download redirect limit exceeded.")
    }

    private fun isAllowedUrl(value: String): Boolean = runCatching {
        val url = URL(value)
        val host = url.host.lowercase()
        url.protocol.equals("https", true) &&
            (host == "huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(".xethub.hf.co"))
    }.getOrDefault(false)

    companion object {
        const val MAX_MODEL_BYTES = 10L * 1024 * 1024 * 1024
        const val MIN_WORKING_SPACE_BYTES = 256L * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
