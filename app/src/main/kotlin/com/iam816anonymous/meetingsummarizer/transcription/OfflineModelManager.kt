package com.iam816anonymous.meetingsummarizer.transcription

import android.content.Context
import android.os.StatFs
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Explicit, user-triggered installer for the small English INT8 model.
 * Downloads only model files; meeting audio and transcripts are never sent anywhere.
 */
class OfflineModelManager(context: Context) {
    private val appContext = context.applicationContext

    fun modelDirectory(): File =
        File(File(appContext.filesDir, MODEL_ROOT), MODEL_DIRECTORY)

    fun isInstalled(): Boolean {
        val directory = modelDirectory()
        return File(directory, ENCODER).isFile &&
            File(directory, DECODER).isFile &&
            File(directory, JOINER).isFile &&
            File(directory, TOKENS).isFile
    }

    @Throws(Exception::class)
    fun install(
        onProgress: (Int) -> Unit = {},
        onStage: (String) -> Unit = {}
    ): File {
        if (isInstalled()) return modelDirectory()
        val available = StatFs(appContext.filesDir.absolutePath).availableBytes
        if (available < MIN_INSTALL_SPACE_BYTES) {
            throw IllegalStateException("At least 150 MB of free storage is required to install the offline English model.")
        }

        val root = File(appContext.filesDir, MODEL_ROOT)
        if (!root.exists() && !root.mkdirs()) throw IllegalStateException("Could not create the private model directory.")
        val archive = File(root, "english-model.tar.bz2.part")
        val extraction = File(root, "english-model-extracting")
        runCatching { archive.delete() }
        deleteRecursively(extraction)
        if (!extraction.mkdirs()) throw IllegalStateException("Could not create a temporary model directory.")

        try {
            onStage("Connecting to model host")
            onStage("Downloading model")
            download(archive, onProgress)
            onStage("Installing model files")
            extractSafely(archive, extraction)
            onStage("Verifying model")
            val extracted = extraction.walkTopDown().firstOrNull {
                it.isDirectory && it.name == MODEL_DIRECTORY
            } ?: throw IllegalStateException("The downloaded model archive did not contain the expected English model.")
            val required = listOf(ENCODER, DECODER, JOINER, TOKENS)
            if (required.any { !File(extracted, it).isFile }) {
                throw IllegalStateException("The English model archive is incomplete. Please retry the download.")
            }

            val destination = modelDirectory()
            deleteRecursively(destination)
            if (!extracted.renameTo(destination)) {
                copyDirectory(extracted, destination)
            }
            if (!isInstalled()) {
                deleteRecursively(destination)
                throw IllegalStateException("The installed English model failed its integrity check.")
            }
            onProgress(100)
            onStage("Installed")
            return destination
        } finally {
            runCatching { archive.delete() }
            deleteRecursively(extraction)
        }
    }

    private fun download(destination: File, onProgress: (Int) -> Unit) {
        val connection = (URL(MODEL_ARCHIVE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MeetingSummarizer-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Model download failed with HTTP ${connection.responseCode}.")
            }
            val expected = connection.contentLengthLong
            var copied = 0L
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (expected > 0L) onProgress(((copied * 100L) / expected).toInt().coerceIn(0, 99))
                    }
                }
            }
            if (destination.length() == 0L) throw IllegalStateException("The model download was empty.")
        } finally {
            connection.disconnect()
        }
    }

    private fun extractSafely(archive: File, destination: File) {
        var totalBytes = 0L
        BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val output = File(destination, entry.name).canonicalFile
                    val rootPath = destination.canonicalPath + File.separator
                    if (!output.path.startsWith(rootPath) || entry.isSymbolicLink || entry.isLink) {
                        throw IllegalStateException("The model archive contains an unsafe path.")
                    }
                    if (entry.isDirectory) {
                        if (!output.exists() && !output.mkdirs()) throw IllegalStateException("Could not create model directory.")
                        continue
                    }
                    totalBytes += entry.size
                    if (totalBytes > MAX_EXTRACTED_BYTES) {
                        throw IllegalStateException("The model archive exceeds the supported extraction size.")
                    }
                    output.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IllegalStateException("Could not create model files.") }
                    output.outputStream().buffered().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = tar.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                        }
                    }
                }
            }
        }
    }

    private fun copyDirectory(source: File, destination: File) {
        if (!destination.mkdirs()) throw IllegalStateException("Could not finalize the model directory.")
        source.walkTopDown().forEach { item ->
            val target = File(destination, item.relativeTo(source).path)
            if (item.isDirectory) {
                if (!target.exists() && !target.mkdirs()) throw IllegalStateException("Could not finalize model files.")
            } else {
                item.inputStream().use { input -> target.outputStream().use(input::copyTo) }
            }
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.exists()) file.deleteRecursively()
    }

    companion object {
        const val MODEL_DIRECTORY = "sherpa-onnx-zipformer-small-en-2023-06-26"
        private const val MODEL_ROOT = "models"
        private const val ENCODER = "encoder-epoch-99-avg-1.int8.onnx"
        private const val DECODER = "decoder-epoch-99-avg-1.int8.onnx"
        private const val JOINER = "joiner-epoch-99-avg-1.int8.onnx"
        private const val TOKENS = "tokens.txt"
        private const val MIN_INSTALL_SPACE_BYTES = 150L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 500L * 1024L * 1024L
        private const val MODEL_ARCHIVE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-small-en-2023-06-26.tar.bz2"
    }
}
