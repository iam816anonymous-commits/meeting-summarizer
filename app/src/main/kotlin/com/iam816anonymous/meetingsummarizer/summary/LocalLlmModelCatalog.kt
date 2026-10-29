package com.iam816anonymous.meetingsummarizer.summary

import java.io.File
import java.security.MessageDigest

/**
 * Downloadable LiteRT-LM artifacts with pinned revisions and SHA-256 digests.
 * Size values marked non-exact are conservative download caps; the SHA-256 digest remains
 * the authoritative identity check. Each tier exposes two artifact choices, but some choices
 * are quantization/template variants of the same base family rather than independent models.
 */
object LocalLlmModelCatalog {
    private const val QWEN06_REVISION = "6aa2daf"
    // The upstream LiquidAI source revision is not a revision in litert-community/LFM2-700M.\n    // Resolve the published LiteRT artifact from its repository and enforce the catalog SHA-256 below.\n    private const val LFM2_REVISION = "main"
    private const val QWEN17_INT4_REVISION = "d9b8a91"
    private const val QWEN17_INT8_REVISION = "4b46d76"
    private const val MB = 1024L * 1024L
    private const val GB = 1024L * MB
    // Qwen3 is a multilingual family; these language options are candidates, not a claim of tested summary quality.
    private val QWEN_LANGUAGES = setOf("en", "hi", "te", "kn", "ta", "ml", "bn", "mr", "gu", "ur", "fr", "de", "es", "ja", "zh")

    val models = listOf(
        LocalLlmModel(
            id = "qwen3-0.6b-nothink-int4",
            displayName = "Qwen3 0.6B · Direct answers",
            description = "Very small quantized model for concise notes. Qwen3 is multilingual; quality for each language still needs device testing. Same base as the reasoning variant.",
            tierLabel = "Very small",
            estimatedDownloadSize = "331 MiB",
            supportedLanguages = QWEN_LANGUAGES,
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/$QWEN06_REVISION/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            expectedBytes = 347_251_840L,
            minimumBytes = 347_251_840L,
            sizeIsExact = true,
            sha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minTotalRamBytes = 3L * GB,
            minAvailableRamBytes = 1400L * MB,
            minFreeStorageBytes = 650L * MB,
            estimatedPeakRamBytes = 1400L * MB
        ),
        LocalLlmModel(
            id = "qwen3-0.6b-reasoning-int4",
            displayName = "Qwen3 0.6B · Reasoning variant",
            description = "Same base weights with a reasoning-style template; may be slower and produce longer responses.",
            tierLabel = "Very small",
            estimatedDownloadSize = "331 MiB",
            supportedLanguages = QWEN_LANGUAGES,
            fileName = "qwen3_0.6b_q4_block32_ekv1280.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/$QWEN06_REVISION/qwen3_0.6b_q4_block32_ekv1280.litertlm",
            expectedBytes = 347_251_840L,
            minimumBytes = 347_251_840L,
            sizeIsExact = true,
            sha256 = "312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minTotalRamBytes = 3L * GB,
            minAvailableRamBytes = 1600L * MB,
            minFreeStorageBytes = 650L * MB,
            estimatedPeakRamBytes = 1600L * MB
        ),
        LocalLlmModel(
            id = "lfm2-700m-int4",
            displayName = "LFM2 700M · INT4",
            description = "Compact Liquid AI model for extraction and concise summaries. Publisher lists English, Arabic, Chinese, French, German, Japanese, Korean and Spanish.",
            tierLabel = "Small",
            estimatedDownloadSize = "about 481 MB",
            supportedLanguages = setOf("en", "ar", "zh", "fr", "de", "ja", "ko", "es"),
            fileName = "LFM2-700M_int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/LFM2-700M/resolve/$LFM2_REVISION/LFM2-700M_int4.litertlm",
            expectedBytes = 520_000_000L,
            minimumBytes = 450_000_000L,
            sizeIsExact = false,
            sha256 = "4ecfc71299a304e97e8f0ef5134085ecc4b5d414755b8c1709f6b552d20250b9",
            license = "LFM Open License v1.0",
            minAndroidApi = 26,
            minTotalRamBytes = 4L * GB,
            minAvailableRamBytes = 1800L * MB,
            minFreeStorageBytes = 800L * MB,
            estimatedPeakRamBytes = 1900L * MB
        ),
        LocalLlmModel(
            id = "lfm2-700m-int8",
            displayName = "LFM2 700M · INT8",
            description = "Larger quantized file for the same LFM2 base family; needs more storage and memory than INT4.",
            tierLabel = "Small",
            estimatedDownloadSize = "about 808 MB",
            supportedLanguages = setOf("en", "ar", "zh", "fr", "de", "ja", "ko", "es"),
            fileName = "LFM2-700M_int8.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/LFM2-700M/resolve/$LFM2_REVISION/LFM2-700M_int8.litertlm",
            expectedBytes = 850_000_000L,
            minimumBytes = 780_000_000L,
            sizeIsExact = false,
            sha256 = "ccc95007d868878ac749073cbc28b88097cb7d437fca3f9aec72f535cc7fd99b",
            license = "LFM Open License v1.0",
            minAndroidApi = 26,
            minTotalRamBytes = 4L * GB,
            minAvailableRamBytes = 2L * GB,
            minFreeStorageBytes = 1100L * MB,
            estimatedPeakRamBytes = 2100L * MB
        ),
        LocalLlmModel(
            id = "qwen3-1.7b-int4",
            displayName = "Qwen3 1.7B · INT4",
            description = "Medium-tier multilingual Qwen3 model with a smaller quantized artifact. Not recommended on most 4 GB phones; language quality is not yet benchmarked here.",
            tierLabel = "Medium",
            estimatedDownloadSize = "about 977 MB",
            supportedLanguages = QWEN_LANGUAGES,
            fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/$QWEN17_INT4_REVISION/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            expectedBytes = 1_050_000_000L,
            minimumBytes = 900_000_000L,
            sizeIsExact = false,
            sha256 = "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minTotalRamBytes = 6L * GB,
            minAvailableRamBytes = 2500L * MB,
            minFreeStorageBytes = 1400L * MB,
            estimatedPeakRamBytes = 3L * GB
        ),
        LocalLlmModel(
            id = "qwen3-1.7b-int8",
            displayName = "Qwen3 1.7B · INT8",
            description = "Higher-memory multilingual Qwen3 artifact. Requires substantially more RAM and storage; CPU latency may be high and language quality is not yet benchmarked here.",
            tierLabel = "Medium",
            estimatedDownloadSize = "about 2.06 GB",
            supportedLanguages = QWEN_LANGUAGES,
            fileName = "Qwen3_1.7B.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/$QWEN17_INT8_REVISION/Qwen3_1.7B.litertlm",
            expectedBytes = 2_250_000_000L,
            minimumBytes = 1_800_000_000L,
            sizeIsExact = false,
            sha256 = "66064a4e9269cb693e124c4e3040bcb8a446b10bca42663896329495add3861c",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minTotalRamBytes = 8L * GB,
            minAvailableRamBytes = 3L * GB,
            minFreeStorageBytes = 2700L * MB,
            estimatedPeakRamBytes = 4L * GB
        ),
        LocalLlmModel(
            id = "qwen3-4b-mixed-int4",
            displayName = "Qwen3 4B · Mixed INT4",
            description = "Large multilingual model with mixed INT4 weights. Requires high memory and several gigabytes of storage; not suitable for most 4 GB phones.",
            tierLabel = "Large",
            estimatedDownloadSize = "about 2.66 GB",
            supportedLanguages = QWEN_LANGUAGES,
            fileName = "qwen3_4b_mixed_int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-4B/resolve/2f7415b/qwen3_4b_mixed_int4.litertlm",
            expectedBytes = 3_000_000_000L,
            minimumBytes = 2_500_000_000L,
            sizeIsExact = false,
            sha256 = "569fe029f51e55b6377f98ebd7f6d26431bc73e1d632034f5389f2fd720da701",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minTotalRamBytes = 12L * GB,
            minAvailableRamBytes = 6L * GB,
            minFreeStorageBytes = 3300L * MB,
            estimatedPeakRamBytes = 6L * GB
        ),
        LocalLlmModel(
            id = "qwen3-4b-channelwise-int8",
            displayName = "Qwen3 4B · Channelwise INT8",
            description = "Higher-precision Large-tier artifact from the same Qwen3 4B family; requires substantially more RAM and storage.",
            tierLabel = "Large",
            estimatedDownloadSize = "about 5.67 GB",
            supportedLanguages = QWEN_LANGUAGES,
            fileName = "qwen3_4b_channelwise_int8_float32kv.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-4B/resolve/4d54595/qwen3_4b_channelwise_int8_float32kv.litertlm",
            expectedBytes = 6_200_000_000L,
            minimumBytes = 5_300_000_000L,
            sizeIsExact = false,
            sha256 = "3a44aeffa0a3b02b453215f77440069f98efc015c600cb6520b2a01109f4c063",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minTotalRamBytes = 16L * GB,
            minAvailableRamBytes = 8L * GB,
            minFreeStorageBytes = 6500L * MB,
            estimatedPeakRamBytes = 8L * GB
        ),
        LocalLlmModel(
            id = "qwen3-8b-mixed-int4",
            displayName = "Qwen3 8B · Mixed INT4",
            description = "Very large multilingual model with mixed INT4 weights. Requires high-end device memory and more than 5 GB of free storage.",
            tierLabel = "Very large",
            estimatedDownloadSize = "about 4.89 GB",
            supportedLanguages = QWEN_LANGUAGES,
            fileName = "qwen3_8b_mixed_int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-8B/resolve/17bed0e/qwen3_8b_mixed_int4.litertlm",
            expectedBytes = 5_400_000_000L,
            minimumBytes = 4_600_000_000L,
            sizeIsExact = false,
            sha256 = "cb4e6d0de4bbf6656d177812cf0c6a983967dedd17e7f88e84b901c3a9862a42",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minTotalRamBytes = 20L * GB,
            minAvailableRamBytes = 10L * GB,
            minFreeStorageBytes = 5700L * MB,
            estimatedPeakRamBytes = 10L * GB
        ),
        LocalLlmModel(
            id = "qwen3-8b-channelwise-int8",
            displayName = "Qwen3 8B · Channelwise INT8",
            description = "Largest catalog artifact with channelwise INT8 weights. Requires very high memory and over 8 GB of model storage; not suitable for ordinary phones.",
            tierLabel = "Very large",
            estimatedDownloadSize = "about 8.31 GB",
            supportedLanguages = QWEN_LANGUAGES,
            fileName = "qwen3_8b_channelwise_int8_float32kv.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-8B/resolve/9984555/qwen3_8b_channelwise_int8_float32kv.litertlm",
            expectedBytes = 9_100_000_000L,
            minimumBytes = 8_000_000_000L,
            sizeIsExact = false,
            sha256 = "2c24953f2ade203216d6588376dbabcecdd0aebac11667d4c24f1819e418e82c",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minTotalRamBytes = 24L * GB,
            minAvailableRamBytes = 16L * GB,
            minFreeStorageBytes = 9500L * MB,
            estimatedPeakRamBytes = 16L * GB
        )
    )

    /** Smallest eligible installed models are preferred. Unsupported language combinations are excluded. */
    fun recommendations(
        languageCode: String,
        androidApi: Int,
        totalRamBytes: Long,
        availableRamBytes: Long,
        freeStorageBytes: Long
    ): List<LocalLlmModel> = models
        .filter { model ->
            model.supportsLanguage(languageCode) &&
                androidApi >= model.minAndroidApi &&
                totalRamBytes >= model.minTotalRamBytes &&
                availableRamBytes >= model.minAvailableRamBytes &&
                availableRamBytes >= model.estimatedPeakRamBytes &&
                freeStorageBytes >= maxOf(model.minFreeStorageBytes, model.expectedBytes + VerifiedModelDownloader.MIN_WORKING_SPACE_BYTES)
        }
        .sortedWith(compareBy<LocalLlmModel> { tierRank(it.tierLabel) }.thenBy { it.estimatedPeakRamBytes })

    private fun tierRank(label: String): Int = when (label) {
        "Very small" -> 0
        "Small" -> 1
        "Medium" -> 2
        "Large" -> 3
        "Very large" -> 4
        else -> Int.MAX_VALUE
    }
}

data class LocalLlmModel(
    val id: String,
    val displayName: String,
    val description: String,
    val tierLabel: String,
    val estimatedDownloadSize: String,
    val supportedLanguages: Set<String>,
    val fileName: String,
    val downloadUrl: String,
    /** Exact size for pinned original artifacts; otherwise a conservative maximum download size. */
    val expectedBytes: Long,
    val minimumBytes: Long,
    val sizeIsExact: Boolean,
    val sha256: String,
    val license: String,
    val minAndroidApi: Int,
    val minTotalRamBytes: Long,
    val minAvailableRamBytes: Long,
    val minFreeStorageBytes: Long,
    val estimatedPeakRamBytes: Long
) {
    fun supportsLanguage(code: String): Boolean = code.lowercase() in supportedLanguages

    fun installedFile(directory: File): File = File(directory, fileName)

    fun isInstalled(directory: File): Boolean {
        val file = installedFile(directory)
        if (!file.isFile) return false
        val size = file.length()
        return if (sizeIsExact) size == expectedBytes else size in minimumBytes..expectedBytes
    }

    /** Expensive checksum validation; call only from a background thread. */
    fun verifyInstalled(directory: File): Boolean {
        val file = installedFile(directory)
        if (!file.isFile) return false
        val size = file.length()
        if (if (sizeIsExact) size != expectedBytes else size !in minimumBytes..expectedBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(sha256, ignoreCase = true)
    }
}
