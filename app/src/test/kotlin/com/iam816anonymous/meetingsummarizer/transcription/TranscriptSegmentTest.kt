package com.iam816anonymous.meetingsummarizer.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptSegmentTest {
    @Test
    fun acceptsTimestampedFinalSegment() {
        val segment = TranscriptSegment("meeting-1", 0, 250, 1_200, "Review the plan.", confidence = 0.94f)
        assertEquals("meeting-1", segment.sessionId)
        assertEquals(950L, segment.endMs - segment.startMs)
        assertTrue(segment.isFinal)
    }

    @Test
    fun allowsEmptyPartialButRejectsEmptyFinal() {
        val partial = TranscriptSegment("meeting-1", 1, 1_200, 1_300, "", isFinal = false)
        assertFalse(partial.isFinal)
        assertTrue(runCatching {
            TranscriptSegment("meeting-1", 2, 1_300, 1_400, "")
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun rejectsInvalidTimestampsAndConfidence() {
        assertTrue(runCatching { TranscriptSegment("s", 0, 100, 50, "hello") }
            .exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { TranscriptSegment("s", 0, 0, 100, "hello", confidence = 1.1f) }
            .exceptionOrNull() is IllegalArgumentException)
    }
}
