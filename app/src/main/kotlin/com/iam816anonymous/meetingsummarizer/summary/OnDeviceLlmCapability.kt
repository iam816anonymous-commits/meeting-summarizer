package com.iam816anonymous.meetingsummarizer.summary

data class DeviceRuntimeSnapshot(
    val androidApi: Int,
    val supportedAbi: Boolean,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long
)

enum class LlmReadiness { NOT_RECOMMENDED, TRIAL_CANDIDATE }

data class LlmCapabilityAssessment(
    val readiness: LlmReadiness,
    val reasons: List<String>,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long
) {
    val mayAttemptTrial: Boolean get() = readiness == LlmReadiness.TRIAL_CANDIDATE
}

/** Conservative resource gate, not a runtime compatibility guarantee. */
class OnDeviceLlmCapability {
    fun assess(snapshot: DeviceRuntimeSnapshot): LlmCapabilityAssessment {
        val reasons = mutableListOf<String>()
        if (snapshot.androidApi < MIN_ANDROID_API) reasons += "Android version is below the planned runtime trial baseline."
        if (!snapshot.supportedAbi) reasons += "CPU architecture is outside the tested native-runtime target set."
        if (snapshot.totalRamBytes < MIN_TOTAL_RAM_BYTES) reasons += "Total RAM is below the conservative 6 GB screening threshold."
        if (snapshot.availableRamBytes < MIN_AVAILABLE_RAM_BYTES) reasons += "Currently available RAM is below the 2 GB screening threshold."
        if (snapshot.freeStorageBytes < MIN_FREE_STORAGE_BYTES) reasons += "Free storage is below the 2.5 GB screening threshold."
        return LlmCapabilityAssessment(
            if (reasons.isEmpty()) LlmReadiness.TRIAL_CANDIDATE else LlmReadiness.NOT_RECOMMENDED,
            reasons,
            snapshot.totalRamBytes.coerceAtLeast(0),
            snapshot.availableRamBytes.coerceAtLeast(0),
            snapshot.freeStorageBytes.coerceAtLeast(0)
        )
    }
    companion object {
        const val MIN_ANDROID_API = 26
        const val MIN_TOTAL_RAM_BYTES = 6L * 1024 * 1024 * 1024
        const val MIN_AVAILABLE_RAM_BYTES = 2L * 1024 * 1024 * 1024
        const val MIN_FREE_STORAGE_BYTES = 2560L * 1024 * 1024
    }
}
