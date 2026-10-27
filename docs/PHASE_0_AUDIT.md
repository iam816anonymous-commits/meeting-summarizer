# Phase 0 Audit — Specification Freeze

**Status:** Draft for review; no app implementation is authorized by this document alone.  
**Scope:** Product and engineering contracts only. No audio capture, model integration, UI implementation, or production release in Phase 0.

## Deliverables
- [x] `SKILL_DEFINITION.md`
- [x] `SUBAGENT_LIFECYCLE.md`
- [x] `RESOURCE_BUDGET.md`
- [x] `MOBILE_TO_DESKTOP_SCALING.md`
- [x] `SKILL_MANIFEST_SCHEMA.md`
- [x] `ISOLATION_MODEL.md`
- [x] `ORCHESTRATION_PROTOCOL.md`
- [x] `SECURITY_MODEL.md`
- [x] `PHASE_SCOPE_GOVERNANCE_RULE.md`
- [x] `API_LEVEL_MATRIX.md`

## Product invariants
1. A meeting is never recorded unless the user explicitly starts it.
2. Recording state is visible through the app and an ongoing foreground notification while required.
3. Stop must stop microphone capture promptly and durably finalize the current audio segment.
4. Core recording, transcript storage, basic summary and action-item review must remain usable offline.
5. Network AI is opt-in, clearly disclosed, and never a prerequisite for saving a meeting.
6. User-related items must carry transcript evidence and uncertainty state; no invented owners or dates.
7. Data survives process death and is not silently deleted to recover storage.
8. Model/runtime selection is evidence-based and benchmarked on the target physical device.
9. No accessibility-service tricks, hidden capture, boot-time recording, or bypass of Android permission rules.
10. Changes must be phase-scoped and tested before they are considered complete.

## Architecture decisions to carry into Phase 1
- Android-native Kotlin application; Compose UI is the default candidate.
- Room/SQLite for local structured records; file-backed encrypted audio storage to be evaluated.
- A repository boundary separates UI from recording, transcription, summarization and persistence.
- Audio capture, speech recognition, extraction and optional model inference are separate components.
- Baseline extraction is deterministic and evidence-linked; LLM output is an optional enrichment.
- No mandatory backend, account, Docker, or cloud service for the first working release.
- Keep cloud provider integration behind an interface and disabled by default.

## Open decisions — resolve before relevant implementation
- Exact target phone and Android/API level.
- Meeting languages, language switching, and expected audio conditions.
- Audio retention default and whether the user wants audio deletion after transcription.
- Whether the target device supports the selected offline speech model within memory/latency budgets.
- Whether speaker separation is required for the first release or deferred.
- Legal/workplace consent workflow appropriate to the user's use cases.

## Acceptance criteria for Phase 0
- Every listed specification exists and has no contradictory recording/privacy rule.
- Phase boundaries and prohibited scope expansion are explicit.
- The architecture has a fallback path when offline model inference fails.
- Unknowns are recorded rather than silently guessed.
- A reviewer can derive Phase 1 tasks and tests from these specifications.

## Exit status
The specification files are now present in this commit. This is a **documentation/specification milestone**, not evidence that an Android app has been built, compiled, installed, or tested. Phase 1 should begin with a repository/toolchain audit and minimal reproducible Android build.
