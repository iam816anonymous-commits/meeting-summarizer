package com.iam816anonymous.meetingsummarizer.summary

import org.junit.Assert.assertTrue
import org.junit.Test
import com.iam816anonymous.meetingsummarizer.transcription.TranscriptSegment
import java.io.File

class LocalMeetingSummaryServiceTest {
    @Test fun rejectsTranscriptThatExceedsSmallModelBudgetWithoutCallingRuntime() {
        val model = LocalLlmModelCatalog.models.first()
        val dir = createTempDir(prefix = "summary-service-test")
        try {
            val service = LocalMeetingSummaryService()
            val segments = listOf(TranscriptSegment("session", 0, 0, 1000, "The team will review the budget on Friday."))
            val result = service.summarize(model, dir, segments, maxInputCharacters = 20)
            assertTrue(result is MeetingLlmSummaryResult.Fallback)
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsMissingOrCorruptModelWithoutInference() {
        val model = LocalLlmModelCatalog.models.first()
        val dir = createTempDir(prefix = "summary-service-test")
        try {
            model.installedFile(dir).writeText("not a model")
            val service = LocalMeetingSummaryService()
            val segments = listOf(TranscriptSegment("session", 0, 0, 1000, "The team will review the budget on Friday."))
            val result = service.summarize(model, dir, segments)
            assertTrue(result is MeetingLlmSummaryResult.Fallback)
        } finally { dir.deleteRecursively() }
    }
}