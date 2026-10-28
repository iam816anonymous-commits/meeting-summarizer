# Phase 6 Follow-up — Real ASR regression

## Observed issue
A device-generated 30-second transcript was saved as one all-caps segment without sentence punctuation. The initial summarizer primarily split at punctuation/newlines and also suppressed candidate-task findings whenever a deadline was present.

## Changes
- Add conservative split boundaries for common meeting-discourse cues in punctuationless speech-recognition output.
- Allow a sentence to produce both a candidate task and a deadline finding.
- Expand task verbs for prioritization, stabilization, finalization, migration and reconvening.
- Add a regression fixture based on the observed meeting transcript; assert that Thursday and Friday deadlines and separate Kotlin, QA regression and documentation actions are extracted with evidence quotes shorter than the entire chunk.

## Limitations
- This is heuristic punctuation restoration, not semantic understanding.
- Each finding retains the original segment timestamp range; exact sentence-level timestamps are not available.
- Findings remain candidates for user review, not verified task assignments.
