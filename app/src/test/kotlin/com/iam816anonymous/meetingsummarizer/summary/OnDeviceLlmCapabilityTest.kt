package com.iam816anonymous.meetingsummarizer.summary

import org.junit.Assert.*
import org.junit.Test

class OnDeviceLlmCapabilityTest {
    private val gate = OnDeviceLlmCapability()
    @Test fun lowRamDeviceIsBlockedWithReason() {
        val r = gate.assess(DeviceRuntimeSnapshot(28, true, 4L shl 30, 900L shl 20, 5L shl 30))
        assertEquals(LlmReadiness.NOT_RECOMMENDED, r.readiness)
        assertFalse(r.mayAttemptTrial)
        assertTrue(r.reasons.any { it.contains("6 GB") })
        assertTrue(r.reasons.any { it.contains("2 GB") })
    }
    @Test fun lowStorageAndUnsupportedAbiBlockTrial() {
        val r = gate.assess(DeviceRuntimeSnapshot(35, false, 8L shl 30, 3L shl 30, 512L shl 20))
        assertFalse(r.mayAttemptTrial)
        assertTrue(r.reasons.any { it.contains("architecture") })
        assertTrue(r.reasons.any { it.contains("storage") })
    }
    @Test fun capableResourcesOnlyAllowTrialCandidateNotGuarantee() {
        val r = gate.assess(DeviceRuntimeSnapshot(35, true, 8L shl 30, 4L shl 30, 8L shl 30))
        assertEquals(LlmReadiness.TRIAL_CANDIDATE, r.readiness)
        assertTrue(r.mayAttemptTrial)
        assertTrue(r.reasons.isEmpty())
    }
}
