# Skill Definition

## Purpose
Define the bounded capabilities of the Meeting Summarizer Agent. A "skill" is an internal, typed capability invoked by the application orchestrator; it is not an autonomous permission to access arbitrary device data.

## Core skills
- **MeetingCapture:** start/stop microphone capture after an explicit user action; emit timestamped audio segments and lifecycle events.
- **SpeechToText:** convert an audio segment to text using a configured local engine; report language, timing and confidence where supported.
- **TranscriptStore:** persist transcript segments and edits locally, with stable IDs and source timestamps.
- **MeetingExtractor:** identify candidate decisions, tasks, deadlines, commitments, questions and topics from transcript text.
- **PersonalRelevanceRanker:** rank candidate items against the user's configured name/aliases and preferences without claiming certainty unsupported by evidence.
- **SummaryComposer:** create an evidence-linked summary from stored transcript and extracted items.
- **ReviewExport:** allow user correction, copy/share only after a user-initiated action.

## Skill contract
Each skill must define input/output types, permissions, cancellation, timeout behavior, errors, resource estimates, and tests. Skills return structured results and do not directly manipulate UI state. Side effects are explicit and auditable.

## Reliability rules
- A skill must not silently discard data on failure.
- Partial results are marked partial.
- Unknown or low-confidence ownership becomes "Needs confirmation".
- All extracted factual claims reference transcript segment IDs and timestamps when available.
- The deterministic baseline works without an LLM or network.
- Optional model output is treated as untrusted candidate content and validated before persistence.

## Out of scope
Secret recording, continuous ambient surveillance, automatic meeting joining, bypassing Android permissions, fabricated speaker identification, and autonomous sharing of meeting data.
