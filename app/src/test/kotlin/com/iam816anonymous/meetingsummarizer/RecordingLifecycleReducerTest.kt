package com.iam816anonymous.meetingsummarizer

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingLifecycleReducerTest {
    @Test fun startMustBeRequestedBeforeCaptureIsRecording() {
        assertEquals(RecordingLifecycleState.PREPARING, RecordingLifecycleReducer.reduce(RecordingLifecycleState.IDLE, RecordingLifecycleEvent.StartRequested))
        assertEquals(RecordingLifecycleState.RECORDING, RecordingLifecycleReducer.reduce(RecordingLifecycleState.PREPARING, RecordingLifecycleEvent.CaptureStarted))
    }

    @Test fun duplicateStartDoesNotResetActiveRecording() {
        assertEquals(RecordingLifecycleState.RECORDING, RecordingLifecycleReducer.reduce(RecordingLifecycleState.RECORDING, RecordingLifecycleEvent.StartRequested))
    }

    @Test fun onlySuccessfulStopCanMarkRecordingCompleted() {
        val stopping = RecordingLifecycleReducer.reduce(RecordingLifecycleState.RECORDING, RecordingLifecycleEvent.StopRequested)
        assertEquals(RecordingLifecycleState.STOPPING, stopping)
        assertEquals(RecordingLifecycleState.COMPLETED, RecordingLifecycleReducer.reduce(stopping, RecordingLifecycleEvent.StopSucceeded))
        assertEquals(RecordingLifecycleState.INTERRUPTED, RecordingLifecycleReducer.reduce(RecordingLifecycleState.RECORDING, RecordingLifecycleEvent.StopFailed))
    }

    @Test fun serviceInterruptionNeverMarksAudioCompleted() {
        assertEquals(RecordingLifecycleState.INTERRUPTED, RecordingLifecycleReducer.reduce(RecordingLifecycleState.RECORDING, RecordingLifecycleEvent.ServiceInterrupted))
    }

    @Test fun fatalFailureIsVisible() {
        assertEquals(RecordingLifecycleState.ERROR, RecordingLifecycleReducer.reduce(RecordingLifecycleState.PREPARING, RecordingLifecycleEvent.Failed))
    }
}
