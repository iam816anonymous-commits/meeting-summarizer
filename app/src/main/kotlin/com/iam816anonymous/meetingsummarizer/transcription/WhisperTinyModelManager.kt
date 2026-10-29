package com.iam816anonymous.meetingsummarizer.transcription

import android.content.Context
import android.os.StatFs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the pinned, multilingual sherpa-ONNX Whisper Tiny pack.
 * Every file is verified by exact byte count and SHA-256 before the pack is activated.
 */
class WhisperTinyModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val root get() = File(File(appContext.filesDir, "models"), MODEL_DIRECTORY)

    data class Progress(val fileName: String, val percent: Int)

    fun modelDirectory(): File = root
    /**
     * Fast UI-safe installed check. Full SHA-256 validation is performed during installation,
     * not on every Activity render (hashing ~100 MiB on the main thread can freeze or crash
     * low-memory phones).
     */
    fun isInstalled(): Boolean = ARTIFACTS.all { artifact ->
        val file = File(root, artifact.localName)
        file.isFile && file.length() == artifact.bytes
    }

    /** Expensive integrity check for a background worker or diagnostic screen only. */
    fun verifyInstalled(): Boolean = ARTIFACTS.all { artifact ->
        val file = File(root, artifact.localName)
        file.isFile && file.length() == artifact.bytes && sha256(file).equals(artifact.sha256, true)
    }

    @Throws(Exception::class)
    fun install(onProgress: (Progress) -> Unit = {}, onStage: (String) -> Unit = {}): File {
        if (verifyInstalled()) return root
        val free = StatFs(appContext.filesDir.absolutePath).availableBytes
        if (free < REQUIRED_FREE_BYTES) {
            throw IllegalStateException("Multilingual Whisper needs about 250 MB free for download, verification and model files.")
        }
        if (!root.exists() && !root.mkdirs()) throw IllegalStateException("Could not create the private Whisper model directory.")
        val staging = File(root.parentFile, "$MODEL_DIRECTORY.installing")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IllegalStateException("Could not create a temporary model directory.")
        try {
            ARTIFACTS.forEachIndexed { index, artifact ->
                onStage("Downloading ${artifact.localName}")
                val target = File(staging, artifact.localName)
                downloadAndVerify(artifact, target) { filePercent ->
                    // Report pack-wide progress rather than restarting the bar at each file.
                    val overallPercent = ((index * 100L + filePercent.coerceIn(0, 99)) / ARTIFACTS.size)
                        .toInt().coerceIn(0, 99)
                    onProgress(Progress(artifact.localName, overallPercent))
                }
            }
            onStage("Verifying Whisper model")
            // Each file's exact size and SHA-256 were already verified while streaming it to disk.
            // Re-hashing the entire pack here doubled disk I/O and made installation look stalled
            // for minutes on low-end phones. Do a fast pack completeness check before activation.
            ARTIFACTS.forEach { artifact ->
                val file = File(staging, artifact.localName)
                if (!file.isFile || file.length() != artifact.bytes) {
                    throw IllegalStateException("Verified model file is missing or incomplete: ${artifact.localName}.")
                }
            }
            val backup = File(root.parentFile, "$MODEL_DIRECTORY.previous")
            backup.deleteRecursively()
            if (root.exists() && !root.renameTo(backup)) throw IllegalStateException("Could not replace the existing model safely.")
            if (!staging.renameTo(root)) {
                if (backup.exists()) backup.renameTo(root)
                throw IllegalStateException("Could not activate the verified Whisper model.")
            }
            backup.deleteRecursively()
            onProgress(Progress("Complete", 100))
            onStage("Installed")
            return root
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun downloadAndVerify(artifact: Artifact, destination: File, onProgress: (Int) -> Unit) {
        val partial = File(destination.parentFile, destination.name + ".part")
        try {
            com.iam816anonymous.meetingsummarizer.summary.ReliableModelDownload.download(
                initialUrl = artifact.url,
                partialFile = partial,
                expectedBytes = artifact.bytes,
                maxBytes = artifact.bytes,
                allowedExactHosts = setOf("huggingface.co", "hf.co"),
                allowedHostSuffixes = setOf(".huggingface.co", ".hf.co", ".xethub.hf.co"),
                onProgress = { progress ->
                    onProgress(((progress.writtenBytes * 100L) / artifact.bytes).toInt().coerceIn(0, 99))
                }
            )
            val actualHash = sha256(partial)
            if (partial.length() != artifact.bytes || !actualHash.equals(artifact.sha256, true)) {
                partial.delete()
                throw IllegalStateException("SHA-256 or size verification failed for ${artifact.localName}. The incomplete file was discarded.")
            }
            if (!partial.renameTo(destination)) throw IllegalStateException("Could not finalize ${artifact.localName}.")
        } catch (error: Exception) {
            // Preserve a partial file only when all transport retries were exhausted.
            if (error.message?.startsWith("Model download failed after") != true) partial.delete()
            throw error
        }
    }

    private fun isAllowedHost(host: String): Boolean {
        val value = host.lowercase()
        return value == "huggingface.co" || value.endsWith(".huggingface.co") ||
            value == "hf.co" || value.endsWith(".hf.co") ||
            value == "xethub.hf.co" || value.endsWith(".xethub.hf.co")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Artifact(
        val localName: String,
        val url: String,
        val bytes: Long,
        val sha256: String
    )

    companion object {
        const val MODEL_DIRECTORY = "whisper-tiny-multilingual"
        const val MODEL_BYTES = 103_609_903L
        private const val REQUIRED_FREE_BYTES = 250L * 1024L * 1024L
        private const val MAX_REDIRECTS = 5
        // Immutable Hugging Face revision; hashes and byte sizes are published in the model manifest.
        private const val BASE_URL = "https://huggingface.co/newbeeforever/whisper-tiny-sherpa-onnx/resolve/cab99de/"
        private val ARTIFACTS = listOf(
            Artifact("encoder.int8.onnx", BASE_URL + "tiny-encoder.int8.onnx", 12_937_772L,
                "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
            Artifact("decoder.int8.onnx", BASE_URL + "tiny-decoder.int8.onnx", 89_855_401L,
                "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"),
            Artifact("tokens.txt", BASE_URL + "tiny-tokens.txt", 816_730L,
                "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")
        )
    }
}
