package com.iam816anonymous.meetingsummarizer.summary

data class LocalModelProfile(
    val id: String, val displayName: String, val format: String, val minAndroidApi: Int,
    val supportedAbis: Set<String>, val minimumAvailableRamBytes: Long, val minimumFreeStorageBytes: Long,
    val estimatedPeakRamBytes: Long, val expectedModelBytes: Long, val maxInputCharacters: Int, val maxOutputTokens: Int
)

data class ModelDeviceSnapshot(val androidApi: Int, val supportedAbis: Set<String>, val availableRamBytes: Long, val freeStorageBytes: Long)
data class ModelCandidateDecision(val profile: LocalModelProfile, val eligible: Boolean, val reasons: List<String>)

/**
 * Ranks resource profiles from smallest upward. Budgets are screening heuristics, not guarantees.
 * Profiles intentionally contain no unverified URLs: catalog artifacts require reviewed license,
 * immutable URL, exact size, SHA-256 and runtime compatibility before becoming downloadable.
 */
class LocalModelRegistry(profiles: List<LocalModelProfile> = defaultProfiles()) {
    private val profiles = profiles.sortedWith(compareBy<LocalModelProfile> { it.estimatedPeakRamBytes }.thenBy { it.expectedModelBytes })

    fun evaluate(device: ModelDeviceSnapshot): List<ModelCandidateDecision> = profiles.map { profile ->
        val reasons = buildList {
            if (device.androidApi < profile.minAndroidApi) add("Android API level is too old.")
            if (device.supportedAbis.intersect(profile.supportedAbis).isEmpty()) add("No supported CPU ABI.")
            if (device.availableRamBytes < profile.minimumAvailableRamBytes) add("Not enough currently available RAM.")
            if (device.availableRamBytes < profile.estimatedPeakRamBytes) add("Available RAM is below the conservative peak-memory budget.")
            if (device.freeStorageBytes < profile.minimumFreeStorageBytes) add("Not enough free storage for model plus working space.")
        }
        ModelCandidateDecision(profile, reasons.isEmpty(), reasons)
    }

    fun firstEligible(device: ModelDeviceSnapshot): ModelCandidateDecision? = evaluate(device).firstOrNull { it.eligible }

    companion object {
        private const val MB = 1024L * 1024L
        private const val GB = 1024L * MB
        fun defaultProfiles(): List<LocalModelProfile> = listOf(
            LocalModelProfile("slm-very-small", "Very small local model (experimental)", "litertlm", 26,
                setOf("arm64-v8a", "armeabi-v7a"), 1200L * MB, 700L * MB, 1400L * MB, 350L * MB, 6000, 256),
            LocalModelProfile("slm-small", "Small local model (experimental)", "litertlm", 26,
                setOf("arm64-v8a", "armeabi-v7a"), 2L * GB, 1200L * MB, 2200L * MB, 600L * MB, 12000, 384),
            LocalModelProfile("llm-medium", "Larger local model (experimental)", "litertlm", 26,
                setOf("arm64-v8a"), 4L * GB, 2500L * MB, 4500L * MB, 1500L * MB, 20000, 512)
        )
    }
}
