package com.iam816anonymous.meetingsummarizer.summary

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class CloudSummaryValidationTest {
    @Test fun rejectsHttpEndpointBeforeNetwork() {
        try {
            OpenAiCompatibleCloudSummaryProvider().summarize(
                CloudProviderConfig("http://example.com/v1/chat/completions", "test", "key"),
                listOf(CloudTranscriptSegment(1, 0, 1, "Decision")))
            fail("HTTP endpoint must be rejected")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("HTTPS")) }
    }
    @Test fun rejectsEmptyAndOversizedTranscriptsBeforeNetwork() {
        val provider = OpenAiCompatibleCloudSummaryProvider()
        try { provider.summarize(CloudProviderConfig("https://example.com/v1/chat/completions", "test", "key"), emptyList()); fail() }
        catch (_: IllegalArgumentException) { }
        try {
            provider.summarize(CloudProviderConfig("https://example.com/v1/chat/completions", "test", "key"),
                listOf(CloudTranscriptSegment(1, 0, 1, "x".repeat(80001))))
            fail()
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("too long")) }
    }
    @Test fun rejectsUnknownEvidenceIds() {
        val method = OpenAiCompatibleCloudSummaryProvider::class.java.getDeclaredMethod("parseAndValidate", String::class.java, Set::class.java).apply { isAccessible = true }
        val valid = """{"summary":"Ship Friday","decisions":[{"text":"Ship","evidence_segment_ids":[4]}],"action_items":[]}"""
        val parsed = method.invoke(OpenAiCompatibleCloudSummaryProvider(), valid, setOf(4L)) as CloudSummaryResult
        assertEquals("Ship Friday", parsed.summary)
        val invalid = """{"summary":"Ship Friday","decisions":[{"text":"Ship","evidence_segment_ids":[99]}],"action_items":[]}"""
        try { method.invoke(OpenAiCompatibleCloudSummaryProvider(), invalid, setOf(4L)); fail("unknown ID should fail") }
        catch (e: InvocationTargetException) { assertTrue(e.cause!!.message!!.contains("does not exist")) }
    }
    @Test fun rejectsClaimsWithoutEvidence() {
        val method = OpenAiCompatibleCloudSummaryProvider::class.java.getDeclaredMethod("parseAndValidate", String::class.java, Set::class.java).apply { isAccessible = true }
        val invalid = """{"summary":"Summary","decisions":[{"text":"Claim","evidence_segment_ids":[]}],"action_items":[]}"""
        try { method.invoke(OpenAiCompatibleCloudSummaryProvider(), invalid, setOf(1L)); fail("missing evidence should fail") }
        catch (e: InvocationTargetException) { assertTrue(e.cause!!.message!!.contains("must cite")) }
    }
}
