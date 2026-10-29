package com.iam816anonymous.meetingsummarizer.summary

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Retryable, resumable HTTPS download primitive. Callers must verify the final artifact digest. */
internal object ReliableModelDownload {
    data class Progress(val writtenBytes: Long, val totalBytes: Long)

    fun download(
        initialUrl: String,
        partialFile: File,
        expectedBytes: Long,
        maxBytes: Long,
        allowedExactHosts: Set<String>,
        allowedHostSuffixes: Set<String>,
        onProgress: (Progress) -> Unit = {}
    ): File {
        require(maxBytes > 0L && expectedBytes <= maxBytes) { "Model size exceeds the download limit." }
        partialFile.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create model download directory.") }
        // If the process died after receiving the final byte but before checksum/activation,
        // reuse the complete partial file; the caller will still verify its pinned digest.
        if (expectedBytes > 0L && partialFile.isFile && partialFile.length() == expectedBytes) return partialFile
        var lastError: Exception? = null
        var attemptsMade = 0
        for (attempt in 0 until MAX_ATTEMPTS) {
            attemptsMade = attempt + 1
            try {
                // A connection may fail just after writing the final byte; let the caller verify it.
                if (expectedBytes > 0L && partialFile.isFile && partialFile.length() == expectedBytes) return partialFile
                downloadAttempt(initialUrl, partialFile, expectedBytes, maxBytes, allowedExactHosts, allowedHostSuffixes, onProgress)
                val length = partialFile.length()
                if (length <= 0L || (expectedBytes > 0L && length != expectedBytes)) {
                    throw RetryableDownloadException("Download is incomplete: received $length bytes.")
                }
                return partialFile
            } catch (error: Exception) {
                lastError = error
                if (!isRetryable(error) || attempt == MAX_ATTEMPTS - 1) break
                try {
                    Thread.sleep(RETRY_DELAY_MS * (1L shl attempt))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Model download was interrupted.", interrupted)
                }
            }
        }
        val failure = lastError ?: IOException("Unknown model download error.")
        if (!isRetryable(failure)) throw failure
        throw IOException("Model download failed after $attemptsMade attempts: ${failure.message ?: "network error"}", failure)
    }

    private fun downloadAttempt(
        initialUrl: String, partialFile: File, expectedBytes: Long, maxBytes: Long,
        allowedExactHosts: Set<String>, allowedHostSuffixes: Set<String>,
        onProgress: (Progress) -> Unit
    ) {
        var url = URL(initialUrl)
        var redirects = 0
        while (true) {
            requireAllowed(url, allowedExactHosts, allowedHostSuffixes)
            var connection: HttpURLConnection? = null
            try {
                val offset = partialFile.length()
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MeetingSummarizer-Android")
                    if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
                }
                connection.connect()
                val code = connection.responseCode
                if (code in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location") ?: throw PermanentDownloadException("Model host returned a redirect without a destination.")
                    if (redirects++ >= MAX_REDIRECTS) throw PermanentDownloadException("Too many model download redirects.")
                    url = URL(url, location)
                    requireAllowed(url, allowedExactHosts, allowedHostSuffixes)
                    connection.disconnect()
                    connection = null
                    continue
                }
                if (code !in 200..299) {
                    if (code == 408 || code == 429 || code in 500..599) throw RetryableHttpException("Model host returned HTTP $code.")
                    throw PermanentDownloadException("Model host returned HTTP $code.")
                }
                val append = offset > 0L && code == HttpURLConnection.HTTP_PARTIAL
                if (append) {
                    val contentRange = connection.getHeaderField("Content-Range")
                    if (contentRange == null || !contentRange.startsWith("bytes $offset-")) {
                        throw PermanentDownloadException("Model host returned an invalid range response.")
                    }
                } else if (offset > 0L && !partialFile.delete()) {
                    throw PermanentDownloadException("Could not restart the partial model download.")
                }
                val currentOffset = if (append) offset else 0L
                val responseLength = connection.contentLengthLong
                if (expectedBytes > 0L && currentOffset == 0L && responseLength > 0L && responseLength != expectedBytes) {
                    throw PermanentDownloadException("Published file size differs from the pinned model manifest.")
                }
                val total = when {
                    expectedBytes > 0L -> expectedBytes
                    responseLength > 0L -> currentOffset + responseLength
                    else -> -1L
                }
                var written = currentOffset
                connection.inputStream.use { input ->
                    FileOutputStream(partialFile, append).buffered().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            written += count
                            if (written > maxBytes || (expectedBytes > 0L && written > expectedBytes)) {
                                throw PermanentDownloadException("Model download exceeded its permitted size.")
                            }
                            output.write(buffer, 0, count)
                            onProgress(Progress(written, total))
                        }
                    }
                }
                if (written <= 0L || (expectedBytes > 0L && written != expectedBytes)) {
                    throw RetryableDownloadException("Download ended before all expected bytes arrived.")
                }
                return
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun requireAllowed(url: URL, exact: Set<String>, suffixes: Set<String>) {
        val host = url.host.lowercase()
        val allowed = url.protocol.equals("https", true) &&
            (host in exact || suffixes.any { suffix -> host == suffix.removePrefix(".") || host.endsWith(suffix) })
        if (!allowed) throw PermanentDownloadException("Model host redirected to an untrusted location.")
    }

    private fun isRetryable(error: Exception): Boolean =
        error !is PermanentDownloadException && error is IOException

    private class PermanentDownloadException(message: String) : IOException(message)
    private class RetryableDownloadException(message: String) : IOException(message)
    private class RetryableHttpException(message: String) : IOException(message)

    private const val MAX_ATTEMPTS = 4
    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val RETRY_DELAY_MS = 750L
    private const val BUFFER_SIZE = 64 * 1024
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
}
