# Security and Privacy Model

## Data classification
Meeting audio, transcripts, participant names, summaries, action items and meeting metadata are sensitive private data by default.

## Recording consent and visibility
- Recording is off by default and requires a deliberate user action.
- Show a clear in-app recording state and a persistent notification for the microphone foreground service.
- Provide an obvious stop action and stop promptly when requested.
- Do not record on boot, hide the notification, or bypass platform permissions.
- The user is responsible for following local law, participant-consent rules and workplace policy; provide a reminder before the first recording.

## Local storage
- Store data in app-private directories and use least-privilege Android permissions.
- Evaluate encryption-at-rest for audio and database files before release; document key lifecycle and backup behavior.
- Do not put transcript/audio contents in logs, crash reports, analytics or clipboard automatically.
- Use database migrations and safe deletion semantics.
- If data is exported or shared, show a user-initiated preview/confirmation.

## Network boundary
- Offline mode makes no network request for capture, transcription or baseline summarization.
- Cloud AI is disabled by default and gated by explicit settings plus a per-operation disclosure where appropriate.
- Send minimum necessary text, not raw audio by default. Redaction is best-effort and must not be marketed as guaranteed anonymization.
- Never silently upload meetings or use meeting content for training.
- Secrets are not committed; use local development configuration and CI secrets only when needed.

## Threats and mitigations
- **Malicious transcript prompt:** treat transcript text as data, not instructions.
- **Hallucinated task:** require evidence quote/segment IDs and confidence; user reviews uncertain assignments.
- **Data leakage:** no content-bearing logs; explicit export and network boundary.
- **Lost work:** incremental durable writes and recovery.
- **Unauthorized recording:** explicit start, notification, permission, visible stop control.
- **Dependency compromise:** pin versions, review licenses, monitor security updates.

## Incident response
A privacy/security defect blocks release. Preserve diagnostic metadata without exposing meeting content, document impact, and fix before resuming release work.
