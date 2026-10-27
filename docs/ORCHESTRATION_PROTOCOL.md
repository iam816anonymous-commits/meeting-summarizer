# Orchestration Protocol

## Meeting state machine
`IDLE -> REQUESTING_PERMISSION -> STARTING -> RECORDING -> STOPPING -> PROCESSING -> READY`

Recoverable/error states: `PAUSED_BY_SYSTEM`, `PARTIAL`, `FAILED`, `CANCELLED`. Never transition into `RECORDING` without a successful microphone permission check and capture start confirmation.

## Commands and events
Commands are explicit user intents: `StartMeeting`, `StopMeeting`, `PauseProcessing`, `ResumeProcessing`, `RetryTranscription`, `EditTranscriptSegment`, `RegenerateSummary`, `DeleteMeeting`, `ExportMeeting`.

Events include permission outcomes, capture started/stopped, segment persisted, transcript segment ready, worker progress/completion/failure, and meeting finalization. Events carry meeting ID, timestamp and correlation ID.

## Ordering and idempotency
- Start is idempotent only for the currently active meeting; it must not create duplicate capture services.
- Stop is safe to call repeatedly and prioritizes microphone shutdown.
- Persist audio segment metadata before dispatching transcription for that segment.
- Processing consumes stable segment IDs and may be retried without duplicating transcript rows.
- Finalization waits for capture shutdown and durable writes, not for optional cloud availability.
- UI derives state from durable/domain state rather than maintaining an independent conflicting copy.

## Backpressure
When transcription falls behind, capture and disk persistence take priority. Queue growth must be bounded; spill to app-private storage and report lag. Never drop audio silently. If storage is critically low, warn the user and stop safely rather than pretending the meeting is fully recorded.

## Failure handling
Every transition has an observable outcome. If the process is killed, mark the meeting interrupted on next launch and offer recovery; never silently resume recording. Cloud failures fall back to local baseline processing.
