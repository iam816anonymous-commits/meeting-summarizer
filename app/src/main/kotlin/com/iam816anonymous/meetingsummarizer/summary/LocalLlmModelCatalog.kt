package com.iam816anonymous.meetingsummarizer.summary

/**
 * Reviewed LiteRT-LM artifacts. Revision is immutable; hashes and byte counts are from
 * the publisher's Hugging Face Xet metadata. Both choices share Qwen3-0.6B weights but
 * bake different generation templates, so they are variants, not distinct base models.
 */
object LocalLlmModelCatalog {
    private const val REVISION = "6aa2daf"
    private const val BASE_URL = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/$REVISION/"

    val models = listOf(
        LocalLlmModel(
            id = "qwen3-0.6b-nothink-int4",
            displayName = "Qwen3 0.6B · Direct answers",
            description = "Smaller quantized model variant for concise summaries and action items. English and Chinese listed by publisher.",
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            downloadUrl = BASE_URL + "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            expectedBytes = 347_251_840L,
            sha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minAvailableRamBytes = 1400L * 1024 * 1024,
            minFreeStorageBytes = 650L * 1024 * 1024,
            estimatedPeakRamBytes = 1400L * 1024 * 1024
        ),
        LocalLlmModel(
            id = "qwen3-0.6b-reasoning-int4",
            displayName = "Qwen3 0.6B · Reasoning variant",
            description = "Same base weights with reasoning-style prompt template; may be slower and produce longer answers.",
            fileName = "qwen3_0.6b_q4_block32_ekv1280.litertlm",
            downloadUrl = BASE_URL + "qwen3_0.6b_q4_block32_ekv1280.litertlm",
            expectedBytes = 347_251_840L,
            sha256 = "312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715",
            license = "Apache-2.0",
            minAndroidApi = 26,
            minAvailableRamBytes = 1600L * 1024 * 1024,
            minFreeStorageBytes = 650L * 1024 * 1024,
            estimatedPeakRamBytes = 1600L * 1024 * 1024
        )
    )
}

data class LocalLlmModel(
    val id: String,
    val displayName: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val sha256: String,
    val license: String,
    val minAndroidApi: Int,
    val minAvailableRamBytes: Long,
    val minFreeStorageBytes: Long,
    val estimatedPeakRamBytes: Long
) {
    fun installedFile(directory: java.io.File): java.io.File = java.io.File(directory, fileName)
    fun isInstalled(directory: java.io.File): Boolean {
        val file = installedFile(directory)
        return file.isFile && file.length() == expectedBytes
    }
}
