# Phase Scope Governance Rule

## Mandatory rule
Every change must name its current phase, allowed deliverables, acceptance tests and explicit exclusions before implementation. No later-phase work is pulled forward for convenience.

## Required pre-change alignment
1. Read this file and `docs/BUILD_PLAN.md`.
2. Identify the active phase and its stated deliverables.
3. Check the requested change against that phase.
4. If scope is ambiguous, stop and ask before changing files.
5. Record deviations and why they are necessary.

## Phase boundaries
- Phase 0: specification and architecture audit only.
- Phase 1: build foundation and CI; no production audio/model features.
- Phase 2: UI/state machine using deterministic or fake repositories; no real capture.
- Phase 3: explicit recording lifecycle and persistence.
- Phase 4: offline transcription engine integration and device benchmark.
- Phase 5: transcript review, edits and recovery.
- Phase 6: deterministic evidence-linked summary and extraction.
- Phase 7: personal relevance profile and ranking.
- Phase 8: optional local LLM evaluation/integration.
- Phase 9: optional cloud provider behind consent.
- Phase 10: reliability, privacy and security hardening.
- Phase 11: release validation and physical-device test.
- Phase 12: later enhancements only after release gates.

## Change packaging
Prepare and validate a complete phase batch before publishing it. Prefer one coherent commit for the batch; do not commit each file as it is authored. Do not claim CI/device validation unless results exist.

## Completion report
Report phase, files changed, tests actually run, results, known failures, unresolved decisions and next phase. A documentation commit is not an app implementation or a successful build.
