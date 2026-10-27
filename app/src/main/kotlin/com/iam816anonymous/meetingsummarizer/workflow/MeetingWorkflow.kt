package com.iam816anonymous.meetingsummarizer.workflow

/**
 * Pure workflow state for the Phase 2 UI prototype.
 *
 * This state intentionally describes a demo session, not a real recording.
 * Real capture states will be introduced in Phase 3 with service-backed events.
 */
enum class MeetingScreenState {
    IDLE,
    DEMO_ACTIVE,
    RESULTS_READY
}

data class MeetingWorkflowState(
    val screen: MeetingScreenState = MeetingScreenState.IDLE,
    val startedAtEpochMs: Long? = null
)

sealed interface MeetingWorkflowAction {
    data class StartDemo(val nowEpochMs: Long) : MeetingWorkflowAction
    data object StopDemo : MeetingWorkflowAction
    data object OpenSampleResults : MeetingWorkflowAction
    data object ReturnHome : MeetingWorkflowAction
}

object MeetingWorkflowReducer {
    fun reduce(
        current: MeetingWorkflowState,
        action: MeetingWorkflowAction
    ): MeetingWorkflowState = when (action) {
        is MeetingWorkflowAction.StartDemo -> {
            if (current.screen == MeetingScreenState.DEMO_ACTIVE) {
                current
            } else {
                MeetingWorkflowState(
                    screen = MeetingScreenState.DEMO_ACTIVE,
                    startedAtEpochMs = action.nowEpochMs
                )
            }
        }

        MeetingWorkflowAction.StopDemo -> {
            if (current.screen == MeetingScreenState.DEMO_ACTIVE) {
                MeetingWorkflowState(screen = MeetingScreenState.RESULTS_READY)
            } else {
                current
            }
        }

        MeetingWorkflowAction.OpenSampleResults ->
            if (current.screen == MeetingScreenState.DEMO_ACTIVE) current
            else MeetingWorkflowState(screen = MeetingScreenState.RESULTS_READY)

        MeetingWorkflowAction.ReturnHome ->
            if (current.screen == MeetingScreenState.DEMO_ACTIVE) current
            else MeetingWorkflowState(screen = MeetingScreenState.IDLE)
    }
}
