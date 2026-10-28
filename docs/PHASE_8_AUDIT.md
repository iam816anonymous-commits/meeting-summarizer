# Phase 8 — Adaptive on-device LLM feasibility gate

## Implemented in this batch
- Add a device preflight that reads Android API level, supported ABI, total RAM, currently available RAM and app-volume free storage.
- Use conservative screening thresholds (6 GB total RAM, 2 GB currently available RAM, 2.5 GB free storage, supported Android/native ABI).
- If any check fails, clearly recommend deterministic-only mode and do not download or start a model.
- A passing result is labelled only as a candidate for a controlled trial, never as proof of runtime/model compatibility.
- Keep the existing deterministic summary path available in all cases.
- Unit tests cover low RAM, low storage/unsupported ABI, and candidate eligibility.

## Important boundary
This commit does not yet bundle or download an LLM runtime/model. Resource screening must not be misrepresented as a model benchmark. Runtime/model selection and an actual inference trial are the next substep after CI and device constraints are known.

## Acceptance
- CI unit tests, APK assembly, lint and artifact verification pass.
- Verify on device that the assessment reports real values and that a 4 GB RAM phone is told to remain deterministic-only.
- No model download or inference can be triggered by this preflight.
