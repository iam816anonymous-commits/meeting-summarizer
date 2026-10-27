# Phase 3 Audit — Recording Lifecycle and Durable Audio Capture

## Scope
Phase 3 adds explicit, user-initiated microphone capture with a microphone foreground service, persistent stop notification, app-private chunk files, segment sidecar metadata, SHA-256 checksums for successfully finalized segments, and interrupted-segment recovery. Speech recognition and summarization remain out of scope.

## Implemented
- Runtime RECORD_AUDIO permission and a pre-recording disclosure/consent reminder.
- Foreground service declared with microphone type and Android 14+ foreground-service permission.
- Persistent low-importance notification with a Stop recording action.
- Five-minute M4A/AAC segments stored in app-private filesDir/recordings.
- Segment metadata sidecars written through a temporary file and rename; finalized segments include timestamps, byte size, and SHA-256 when available.
- 50 MB free-space guard before start and each segment rotation.
- A segment is marked COMPLETE only when MediaRecorder.stop() succeeds, a non-empty file exists, and its metadata sidecar is persisted successfully. Metadata persistence failure is surfaced as an unsuccessful finalization rather than silently treated as success. A segment left in RECORDING state is recovered as INCOMPLETE on next launch.
- Recording state is persisted locally and refreshed when the activity returns to the foreground, avoiding a stale recording indicator after notification actions or backgrounding.
- Recording completion is now surfaced in the UI with a saved-file count and total byte size; a local Recordings screen lists sessions, segment sizes, and sidecar status, and supports on-device playback for verification.
- The Recordings screen is available without a computer because it reads the app-private recordings directory directly.
- Pure lifecycle reducer tests cover start, duplicate start, successful/failed stop, interruption and fatal error.

## Verification
CI must pass unit tests, APK assembly, lint, APK existence and artifact upload. Physical-device verification is still required for permission prompts, screen-off recording, notification Stop, calls/audio-focus interruptions, low storage and OEM battery management. Do not call Phase 3 fully accepted until that device smoke test is recorded.

## Known limitations
- Segment rotation is five minutes; device-specific MediaRecorder behavior must be tested on physical hardware.
- Android cannot guarantee capture of protected/internal audio from arbitrary third-party meeting apps. This implementation captures room audio from the phone microphone only.
- Android notification permission behavior differs by OS version; recording status is also managed by Android's foreground-service system.
- Recording is local-only; no transcription or cloud upload is implemented.
