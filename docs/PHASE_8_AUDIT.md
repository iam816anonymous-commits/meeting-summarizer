# Phase 8 — Adaptive local-model feasibility and guarded runtime

## Implemented in this branch

- Device resource snapshot: Android API, supported ABI, total/available RAM and free storage.
- Model registry ranks resource profiles from the smallest tier upward and reports why each profile is ineligible.
- Optional LiteRT-LM runtime adapter for an already-installed `.litertlm` file. It runs off the UI thread, bounds prompt size, uses a timeout, closes runtime resources and returns a typed failure instead of replacing deterministic results.
- Explicit-opt-in model downloader primitive requiring HTTPS, a reviewed expected byte count and SHA-256, available storage, a bounded artifact size and finalization from a temporary file.
- Output guard rejects empty/oversized output and output without a supplied transcript evidence anchor. This is a conservative guard, not a semantic factuality proof.
- In-memory benchmark aggregation for cold start, generation latency, peak memory, accepted-output rate and failure categories. It stores no transcript text.
- Unit tests cover registry ranking, device ineligibility, output guardrails and benchmark aggregation.
- README updated with status and remaining release gates.

## Important limits

- No model artifact is bundled, and the registry deliberately has no hard-coded download URLs. Each artifact must be verified for license, URL, exact size, SHA-256, runtime compatibility and actual resource use before it is offered in the UI.
- The runtime and downloader are primitives, not yet wired into a complete user-facing install-and-generate flow. The current button is a resource assessment only; it does not download or run inference.
- Resource budgets are screening heuristics. Only an on-device trial can establish whether a given model is usable on a particular phone.
- The current output validator only checks a verbatim evidence anchor; it cannot guarantee that every generated claim is supported. Generated prose must remain clearly labelled and separate from evidence-linked deterministic findings.
- Timeout/cancellation around native inference must be tested on physical devices; native work may not stop immediately when coroutine cancellation occurs.
- No physical-device benchmark has been run from CI.

## Next acceptance gates

1. Verify an exact model artifact, license, checksum and runtime compatibility.
2. Wire explicit user consent, download progress/cancel/retry and installed-model management into the UI.
3. Wire generation into the meeting-summary screen with strict structured output, segment-ID validation and deterministic fallback.
4. Benchmark cold start, end-to-end latency, peak memory, thermal/battery effects and factuality on the target device with at least three corrected reference transcripts.
5. Verify no model/network work occurs unless explicitly requested, and test low-memory, timeout, cancellation, malformed output and model-missing cases on device.
