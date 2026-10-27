# Subagent / Worker Lifecycle

The first release may run all workers in one Android process. These lifecycle rules allow later isolation without requiring a distributed-agent framework.

## States
`CREATED -> READY -> RUNNING -> CANCELLING -> COMPLETED | FAILED | CANCELLED`

A worker may also emit `PARTIAL` results while running. Terminal states are immutable; retries create a new attempt ID.

## Required metadata
- `jobId`, `meetingId`, `workerType`, `attempt`
- creation/start/finish timestamps
- input artifact IDs and content hashes
- state, progress, cancellation token
- structured error code and retryability
- model/engine version and configuration fingerprint

## Lifecycle rules
1. Validate inputs before scheduling.
2. Check cancellation between bounded chunks.
3. Do not retry non-idempotent side effects without an idempotency key.
4. Persist completed segment results before acknowledging success.
5. A process restart must reconcile interrupted work from durable state.
6. Never restart microphone recording automatically after process death; require the user to start again.
7. Worker failure must not delete the original audio or transcript.
8. Limit concurrent CPU/RAM-heavy work; transcription and local LLM inference should not run concurrently by default on low-memory devices.

## Retry policy
Retry only transient failures, with bounded attempts and backoff. Model-not-found, unsupported language, permission denial and invalid input are actionable errors, not infinite retry candidates. User cancellation is not a failure.
