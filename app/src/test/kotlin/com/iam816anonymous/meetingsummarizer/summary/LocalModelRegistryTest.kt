package com.iam816anonymous.meetingsummarizer.summary

import org.junit.Assert.*
import org.junit.Test

class LocalModelRegistryTest {
    private val registry = LocalModelRegistry()
    @Test fun selectsSmallestEligibleProfileFirst() {
        val result = registry.firstEligible(ModelDeviceSnapshot(35, setOf("arm64-v8a"), 1800L * 1024 * 1024, 2L * 1024 * 1024 * 1024))
        assertNotNull(result)
        assertEquals("slm-very-small", result?.profile?.id)
    }
    @Test fun rejectsUnsupportedAbiAndInsufficientStorage() {
        val results = registry.evaluate(ModelDeviceSnapshot(35, setOf("x86"), 8L * 1024 * 1024 * 1024, 100L * 1024 * 1024))
        assertTrue(results.all { !it.eligible })
        assertTrue(results.all { item -> item.reasons.any { it.contains("storage") } })
    }
    @Test fun noCandidateWhenRamIsTooLow() {
        assertNull(registry.firstEligible(ModelDeviceSnapshot(26, setOf("armeabi-v7a"), 300L * 1024 * 1024, 4L * 1024 * 1024 * 1024)))
    }
    @Test fun candidatesAreOrderedByEstimatedPeakMemory() {
        val results = registry.evaluate(ModelDeviceSnapshot(35, setOf("arm64-v8a"), 8L * 1024 * 1024 * 1024, 8L * 1024 * 1024 * 1024))
        assertEquals("slm-very-small", results.first().profile.id)
        assertEquals("llm-medium", results.last().profile.id)
    }
}
