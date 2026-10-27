# Phase 2 Audit — UI and Workflow State Machine

**Phase:** 2  
**Scope:** usable app navigation and explicit meeting workflow using deterministic demo data only.  
**Explicit exclusions:** microphone permission, recording service, real audio capture, transcription, database persistence, AI inference, cloud calls, and real meeting claims.

## Deliverables
- [x] Replace the Phase 1 placeholder screen with a structured mobile UI.
- [x] Implement Home, demo-in-progress, and sample-results screens.
- [x] Implement a deterministic workflow reducer with valid/invalid transition handling.
- [x] Add a demo repository with clearly labelled fixture data.
- [x] Add unit tests for workflow transitions.
- [x] Run unit tests and Android lint in CI.
- [ ] Verify a successful CI run and download the debug APK.
- [ ] Install and smoke-test on an emulator or physical device.

## Safety and correctness
- Phase 2 does not request microphone permission and does not record audio.
- The active screen must clearly say that it is a demo and the microphone is not being used.
- Sample summaries and tasks are explicitly marked as sample data.
- UI rendering must not block the main thread with model inference or file operations.
- State transitions must be centralized and unit-testable, not duplicated across button handlers.

## State model
- `IDLE`: no active demo session.
- `DEMO_ACTIVE`: user started a demo session; no real capture is occurring.
- `RESULTS_READY`: sample results are shown after the demo session is ended, or the user opens the sample result preview.

## Acceptance criteria
1. Start demo moves from `IDLE` to `DEMO_ACTIVE`.
2. Stop demo moves from `DEMO_ACTIVE` to `RESULTS_READY`.
3. Invalid actions do not produce impossible states.
4. Home/results navigation is functional and never claims that sample content came from a real meeting.
5. Unit tests cover normal and invalid transitions.
6. CI executes unit tests, lint, debug assembly and checks the APK file before upload.

## Status
Source and tests are being committed together as one phase batch. This is not a real recording or summarization implementation. Phase 2 is not complete until CI and device smoke tests pass.
