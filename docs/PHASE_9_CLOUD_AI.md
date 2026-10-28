# Phase 9 — Optional Cloud AI

- Disabled by default; separate from recording and offline transcription.
- Provider-neutral `CloudSummaryProvider` interface and OpenAI-compatible HTTPS chat-completions adapter.
- User-configurable endpoint/model; no shared key embedded in source or APK.
- API key encrypted at rest using AES/GCM and a key generated inside Android Keystore.
- Global opt-in plus a per-meeting confirmation after previewing the exact minimized/redacted transcript payload.
- Email-like and phone-like patterns are redacted as best-effort only. Only final transcript text, segment IDs and timestamps are sent; raw audio, paths, profile and unrelated meetings are not sent.
- HTTPS validation, no redirects, request/response bounds, connect/read timeouts and one retry for transient I/O/HTTP 429/5xx.
- JSON output validation; every decision/action item must cite existing transcript segment IDs. Unknown IDs, missing evidence, malformed JSON and oversized output are rejected.
- Cloud results remain separate from deterministic findings. Provider/network/validation failures leave the local transcript and offline summary available.

## Remaining release gates
The adapter targets OpenAI-compatible chat-completions responses; vendor-specific APIs may need a separate adapter. Validate on a physical device and against a real provider before release, including cancellation, network logs, airplane-mode fallback, key migration/backup behavior, provider retention terms, and long transcripts. Evidence IDs provide traceability, not proof of semantic truth.
