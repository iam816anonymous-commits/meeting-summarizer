package com.iam816anonymous.meetingsummarizer.summary

import org.junit.Assert.*
import org.junit.Test

class OnDeviceModelBenchmarkTest {
    @Test fun aggregatesLatencyMemoryQualityAndFailures() {
        val report = OnDeviceModelBenchmark().summarize(listOf(
            ModelBenchmarkSample("slm", 100, 500, 900, 100, true, null, 80, 79),
            ModelBenchmarkSample("slm", 200, 700, 1200, 80, false, null, 79, 78, "timeout")
        )).single()
        assertEquals(2, report.runs)
        assertEquals(150L, report.medianColdStartMs)
        assertEquals(600L, report.medianGenerationMs)
        assertEquals(1200L, report.maximumPeakAppMemoryBytes)
        assertEquals(0.5, report.acceptedOutputRate, 0.0001)
        assertEquals(1, report.failures["timeout"])
    }
    @Test fun emptyInputReturnsNoReports() {
        assertTrue(OnDeviceModelBenchmark().summarize(emptyList()).isEmpty())
    }
}
