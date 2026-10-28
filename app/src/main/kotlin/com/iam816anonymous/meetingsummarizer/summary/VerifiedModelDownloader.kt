package com.iam816anonymous.meetingsummarizer.summary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class VerifiedModelArtifact(val id: String, val downloadUrl: String, val fileName: String, val expectedBytes: Long, val sha256: String)
sealed interface ModelInstallResult {
    data class Installed(val file: File) : ModelInstallResult
    data class Rejected(val reason: String) : ModelInstallResult
}

/** Must be called only after explicit user action; partial or unverified files are never installable. */
class VerifiedModelDownloader {
    suspend fun install(artifact: VerifiedModelArtifact, modelDirectory: File, freeStorageBytes: Long, userOptedIn: Boolean): ModelInstallResult =
        withContext(Dispatchers.IO) {
            if (!userOptedIn) return@withContext ModelInstallResult.Rejected("Model download requires explicit opt-in.")
            if (!artifact.downloadUrl.startsWith("https://", ignoreCase = true)) return@withContext ModelInstallResult.Rejected("Only HTTPS model downloads are allowed.")
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
                connection = (URL(artifact.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                }
                connection.connect()
                if (connection.responseCode !in 200..299) throw IllegalStateException("Model server returned HTTP " + connection.responseCode + ".")
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

    companion object {
        const val MAX_MODEL_BYTES = 2500L * 1024 * 1024
        const val MIN_WORKING_SPACE_BYTES = 256L * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
