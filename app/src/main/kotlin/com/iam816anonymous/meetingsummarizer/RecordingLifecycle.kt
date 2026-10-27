package com.iam816anonymous.meetingsummarizer

enum class RecordingLifecycleState { IDLE, PREPARING, RECORDING, STOPPING, COMPLETED, INTERRUPTED, ERROR }

object RecordingLifecycleReducer {
    fun reduce(current: RecordingLifecycleState, event: RecordingLifecycleEvent): RecordingLifecycleState =
        when (event) {
            RecordingLifecycleEvent.StartRequested ->
                if (current == RecordingLifecycleState.RECORDING || current == RecordingLifecycleState.PREPARING) current
                else RecordingLifecycleState.PREPARING
            RecordingLifecycleEvent.CaptureStarted ->
                if (current == RecordingLifecycleState.PREPARING) RecordingLifecycleState.RECORDING else current
            RecordingLifecycleEvent.StopRequested ->
                if (current == RecordingLifecycleState.RECORDING) RecordingLifecycleState.STOPPING else current
            RecordingLifecycleEvent.StopSucceeded ->
                if (current == RecordingLifecycleState.STOPPING) RecordingLifecycleState.COMPLETED else current
            RecordingLifecycleEvent.StopFailed ->
                if (current == RecordingLifecycleState.STOPPING || current == RecordingLifecycleState.RECORDING) RecordingLifecycleState.INTERRUPTED else current
            RecordingLifecycleEvent.Failed -> RecordingLifecycleState.ERROR
            RecordingLifecycleEvent.ServiceInterrupted ->
                if (current == RecordingLifecycleState.RECORDING || current == RecordingLifecycleState.STOPPING) RecordingLifecycleState.INTERRUPTED else current
        }
}

sealed interface RecordingLifecycleEvent {
    data object StartRequested : RecordingLifecycleEvent
    data object CaptureStarted : RecordingLifecycleEvent
    data object StopRequested : RecordingLifecycleEvent
    data object StopSucceeded : RecordingLifecycleEvent
    data object StopFailed : RecordingLifecycleEvent
    data object Failed : RecordingLifecycleEvent
    data object ServiceInterrupted : RecordingLifecycleEvent
}
