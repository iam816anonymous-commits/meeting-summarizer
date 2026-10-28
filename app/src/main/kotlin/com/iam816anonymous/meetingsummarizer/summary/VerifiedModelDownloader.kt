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
    val sha256: String
)

sealed interface ModelInstallResult {
    data class Installed(val file: File) : ModelInstallResult
    data class Rejected(val reason: String) : ModelInstallResult
}

/**
 * Explicit opt-in, HTTPS-only model installer. It follows a small number of HTTPS redirects
 * for Hugging Face's object storage, caps bytes, hashes while streaming, and only atomically
 * exposes a file after exact-size and SHA-256 validation.
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
        var connection: HttpURLConnection? = null
        try {
            if (!modelDirectory.exists() && !modelDirectory.mkdirs()) throw IllegalStateException("Could not create model directory.")
            connection = openFollowingRedirects(artifact.downloadUrl)
            if (connection.responseCode !in 200..299) throw IllegalStateException("Model server returned HTTP ${connection.responseCode}.")
            val advertisedLength = connection.contentLengthLong
            if (advertisedLength > 0L && advertisedLength != artifact.expectedBytes) throw IllegalStateException("Artifact size does not match the catalog.")
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        if (written > artifact.expectedBytes || written > MAX_MODEL_BYTES) throw IllegalStateException("Model download exceeded the allowed size.")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(written, artifact.expectedBytes)
                    }
                }
            }
            if (written != artifact.expectedBytes) throw IllegalStateException("Model download was incomplete.")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hash.equals(artifact.sha256, true)) throw IllegalStateException("Model checksum verification failed.")
            if (destination.exists() && !destination.delete()) throw IllegalStateException("Could not replace the previous model.")
            if (!partial.renameTo(destination)) throw IllegalStateException("Could not finalize installed model.")
            ModelInstallResult.Installed(destination)
        } catch (error: Exception) {
            partial.delete()
            ModelInstallResult.Rejected(error.message ?: "Model download failed.")
        } finally {
            connection?.disconnect()
        }
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
        const val MAX_MODEL_BYTES = 2500L * 1024 * 1024
        const val MIN_WORKING_SPACE_BYTES = 256L * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
