package com.iam816anonymous.meetingsummarizer.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingWorkflowReducerTest {
    @Test
    fun startDemoMovesIdleToActiveAndRecordsStartTime() {
        val next = MeetingWorkflowReducer.reduce(
            MeetingWorkflowState(),
            MeetingWorkflowAction.StartDemo(nowEpochMs = 1234L)
        )

        assertEquals(MeetingScreenState.DEMO_ACTIVE, next.screen)
        assertEquals(1234L, next.startedAtEpochMs)
    }

    @Test
    fun repeatedStartDoesNotResetAnActiveDemo() {
        val active = MeetingWorkflowState(
            screen = MeetingScreenState.DEMO_ACTIVE,
            startedAtEpochMs = 1234L
        )

        val next = MeetingWorkflowReducer.reduce(
            active,
            MeetingWorkflowAction.StartDemo(nowEpochMs = 9999L)
        )

        assertEquals(active, next)
    }

    @Test
    fun stopMovesActiveDemoToResults() {
        val active = MeetingWorkflowState(
            screen = MeetingScreenState.DEMO_ACTIVE,
            startedAtEpochMs = 1234L
        )

        val next = MeetingWorkflowReducer.reduce(active, MeetingWorkflowAction.StopDemo)

        assertEquals(MeetingScreenState.RESULTS_READY, next.screen)
        assertNull(next.startedAtEpochMs)
    }

    @Test
    fun stopOutsideActiveDemoIsIgnored() {
        val idle = MeetingWorkflowState()

        assertEquals(
            idle,
            MeetingWorkflowReducer.reduce(idle, MeetingWorkflowAction.StopDemo)
        )
    }

    @Test
    fun resultsCanBeOpenedFromHomeButNotWhileDemoIsActive() {
        val idle = MeetingWorkflowState()
        val active = MeetingWorkflowState(
            screen = MeetingScreenState.DEMO_ACTIVE,
            startedAtEpochMs = 1L
        )

        assertEquals(
            MeetingScreenState.RESULTS_READY,
            MeetingWorkflowReducer.reduce(
                idle,
                MeetingWorkflowAction.OpenSampleResults
            ).screen
        )
        assertEquals(
            active,
            MeetingWorkflowReducer.reduce(
                active,
                MeetingWorkflowAction.OpenSampleResults
            )
        )
    }

    @Test
    fun returningHomeDoesNotSilentlyDiscardAnActiveSession() {
        val active = MeetingWorkflowState(
            screen = MeetingScreenState.DEMO_ACTIVE,
            startedAtEpochMs = 1L
        )

        assertEquals(
            active,
            MeetingWorkflowReducer.reduce(active, MeetingWorkflowAction.ReturnHome)
        )
    }

    @Test
    fun returnHomeFromResultsClearsTheWorkflow() {
        val results = MeetingWorkflowState(screen = MeetingScreenState.RESULTS_READY)

        val next = MeetingWorkflowReducer.reduce(results, MeetingWorkflowAction.ReturnHome)

        assertEquals(MeetingScreenState.IDLE, next.screen)
        assertNull(next.startedAtEpochMs)
    }
}
