# Phase 5 Audit — Transcript Review, Search and Session Recovery

## Scope
Make locally recognized transcript text inspectable and correctable, searchable across saved sessions, and safely deletable. Keep all meeting data app-private and offline. This phase does not add summary generation or cloud services.

## Implemented
- SQLite schema migration adds an explicit edited marker without dropping existing transcript data.
- Transcript correction UI saves a non-blank correction against the existing session/segment key.
- ASR persistence does not overwrite a segment that has been manually corrected.
- Search across stored transcript text uses a parameterized SQLite query.
- Session deletion removes the session's transcript segments, transcription jobs and session audio directory after explicit confirmation.
- Interrupted transcription jobs remain recoverable as pending; users can reopen the recordings list and restart transcription.
- Shared offline ASR model is retained when deleting a meeting because it is not meeting-specific and is required for future offline transcription.

## Acceptance and verification
- CI must pass unit tests, debug APK assembly, Android lint, APK existence and artifact upload.
- Manual device checks remain required: correct a transcript, force-stop/reopen, verify correction persists; search a distinctive phrase; delete one meeting and confirm its audio and transcript are gone while other sessions remain.
- Database migration is additive and does not drop existing rows.
- No physical-device test is claimed from CI.

## Known limitations
- Search is local and case-insensitive; it searches transcript text, not audio.
- Recovery is user-triggered from the recordings screen; background/WorkManager execution is not introduced in this phase.
- The shared model is not deleted with a meeting because it is not an artifact owned exclusively by that session.
