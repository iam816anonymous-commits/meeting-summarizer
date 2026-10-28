# Phase 6 Audit — Deterministic Offline Summaries and Evidence-Linked Tasks

## Scope
Rule-based extraction from locally stored transcript segments. No LLM, network service, or generated facts.

## Implemented
- Deterministic extraction of decisions, explicit commitments, deadline mentions, open questions, candidate tasks, and tentative proposals.
- Each finding points to the source session, transcript sequence, audio-relative timestamps, and verbatim sentence evidence.
- Negated action language is conservatively excluded from positive action extraction.
- Tentative language is labeled as tentative with low confidence, not presented as a confirmed decision.
- Repeated normalized findings are deduplicated while preserving the first evidence link.
- Ambiguous pronouns lower confidence and explicitly state that owners/targets are not inferred.
- Summary is generated on demand in the transcript view; all processing is local.

## Validation
CI must pass unit tests, APK assembly, Android lint, APK existence and artifact upload.
Fixture tests cover evidence linkage, negation, tentative proposals, duplicates, ambiguous pronouns, questions, and non-final segments.
Physical-device verification remains required for usability and long-transcript performance.

## Limitations
- This is deliberately conservative pattern matching, not semantic understanding. False negatives are expected; the UI must invite transcript review.
- Evidence timestamps refer to the containing transcript segment because the recognizer's segment-level timestamp does not precisely timestamp each sentence.
- Deadline text is quoted from the transcript; no date normalization or inferred owner assignment is performed.
- Summaries are computed on demand and are not yet persisted as separate records.
