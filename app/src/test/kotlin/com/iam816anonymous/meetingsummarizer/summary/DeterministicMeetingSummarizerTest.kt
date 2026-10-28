package com.iam816anonymous.meetingsummarizer.summary

import com.iam816anonymous.meetingsummarizer.transcription.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicMeetingSummarizerTest {
    private val summarizer = DeterministicMeetingSummarizer()

    @Test
    fun linksEveryFindingToExactTranscriptEvidenceAndTimestamp() {
        val source = TranscriptSegment("meeting-a", 4, 12_000, 15_000, "I will send the report by Friday.")
        val summary = summarizer.summarize(listOf(source))
        assertTrue(summary.findings.isNotEmpty())
        summary.findings.forEach {
            assertEquals(source.sessionId, it.evidence.sessionId)
            assertEquals(source.sequence, it.evidence.sequence)
            assertEquals(source.startMs, it.evidence.startMs)
            assertEquals(source.endMs, it.evidence.endMs)
            assertEquals(source.text, it.evidence.quote)
        }
        assertTrue(summary.findings.any { it.kind == SummaryFindingKind.COMMITMENT && it.explicitActor != null })
        assertTrue(summary.findings.any { it.kind == SummaryFindingKind.DEADLINE && it.deadlineText != null })
    }

    @Test
    fun negatedActionsAreNotPromotedToCommitmentsOrTasks() {
        val summary = summarizer.summarize(listOf(
            TranscriptSegment("m", 0, 0, 1000, "I will not send the report."),
            TranscriptSegment("m", 1, 1000, 2000, "We don't need to deploy today.")
        ))
        assertFalse(summary.findings.any { it.kind == SummaryFindingKind.COMMITMENT || it.kind == SummaryFindingKind.CANDIDATE_TASK || it.kind == SummaryFindingKind.DEADLINE })
    }

    @Test
    fun tentativeProposalIsMarkedLowConfidenceAndNotAsDecision() {
        val summary = summarizer.summarize(listOf(
            TranscriptSegment("m", 0, 0, 1000, "Maybe we could consider moving the launch.")
        ))
        assertTrue(summary.findings.any { it.kind == SummaryFindingKind.TENTATIVE_PROPOSAL })
        assertFalse(summary.findings.any { it.kind == SummaryFindingKind.DECISION })
        assertEquals(SummaryConfidence.LOW, summary.findings.first().confidence)
    }

    @Test
    fun repeatedFindingIsDeduplicatedButKeepsFirstValidEvidence() {
        val first = TranscriptSegment("m", 0, 0, 1000, "Please review the draft.")
        val repeated = TranscriptSegment("m", 1, 1000, 2000, "Please review the draft!")
        val summary = summarizer.summarize(listOf(first, repeated))
        assertEquals(1, summary.findings.count { it.kind == SummaryFindingKind.CANDIDATE_TASK })
        assertEquals(0L, summary.findings.first { it.kind == SummaryFindingKind.CANDIDATE_TASK }.evidence.sequence)
    }

    @Test
    fun ambiguousPronounsLowerConfidenceAndDoNotInventOwner() {
        val summary = summarizer.summarize(listOf(
            TranscriptSegment("m", 0, 0, 1000, "They need to send it by Friday.")
        ))
        val finding = summary.findings.first { it.kind == SummaryFindingKind.DEADLINE }
        assertEquals(SummaryConfidence.LOW, finding.confidence)
        assertNotNull(finding.ambiguityNote)
        assertEquals(null, finding.explicitActor)
    }

    @Test
    fun questionsAreSurfacedWithoutBecomingTasks() {
        val summary = summarizer.summarize(listOf(
            TranscriptSegment("m", 0, 0, 1000, "Who will own the launch?")
        ))
        assertTrue(summary.findings.any { it.kind == SummaryFindingKind.QUESTION })
        assertFalse(summary.findings.any { it.kind == SummaryFindingKind.CANDIDATE_TASK })
    }

    @Test
    fun nonFinalSegmentsAreIgnored() {
        val summary = summarizer.summarize(listOf(
            TranscriptSegment("m", 0, 0, 1000, "Please review the draft.", isFinal = false)
        ))
        assertTrue(summary.findings.isEmpty())
    }
}
