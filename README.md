# Meeting Summarizer Agent

An offline-first Android meeting assistant. The user explicitly starts and stops each recording. The planned app will save a transcript, summarize decisions and open questions, and prioritize action items and discussion points relevant to the configured user profile.

## Project status
- [Research and phased build plan](docs/BUILD_PLAN.md)
- [Phase 0 audit](docs/PHASE_0_AUDIT.md)
- [Phase 1 audit](docs/PHASE_1_AUDIT.md)
- [Phase 2 audit](docs/PHASE_2_AUDIT.md)
- [Phase 3 audit — recording lifecycle](docs/PHASE_3_AUDIT.md)
- [Phase 4 audit — offline English transcription](docs/PHASE_4_AUDIT.md)
- [Phase 5 audit — transcript review, search and recovery](docs/PHASE_5_AUDIT.md)
- **Current work:** Phase 5 — transcript correction, local search, meeting deletion and recovery UX (under CI validation).
- **Implemented:** native Android recording, app-private segmented audio, local English ASR integration, timestamped transcript persistence, transcript correction/search and meeting deletion on the Phase 5 branch.
- **Not implemented yet:** deterministic summaries and evidence-linked task extraction (Phase 6), personal relevance ranking, local LLM, optional cloud AI.
- **Validation note:** CI does not replace physical-device tests for offline recognition, migration/persistence, search, deletion and notification behavior.

## Product principles
- Recording is opt-in per meeting, visibly indicated, and easy to stop.
- Local capture and saved data must not depend on network access.
- Cloud AI is optional, disabled by default, and requires explicit opt-in.
- Extracted tasks must link to transcript evidence; uncertain ownership is labelled for review.
- Never invent transcript text, speaker identities, owners, or deadlines.
- Target low-memory Android devices; benchmark on the actual target phone before choosing models.

## Build
The project pins Android Gradle Plugin, Kotlin and Gradle versions. GitHub Actions assembles the debug APK, runs unit tests and Android lint, then publishes the APK as a workflow artifact. A verified Gradle Wrapper JAR and scripts are not yet committed; use Android Studio's Gradle tooling or CI until the wrapper can be generated and checked in.

## Privacy
The Phase 2 demo does not request microphone permission and does not record audio. Future recording must be visibly indicated and must not start secretly or on boot. Only record meetings when permitted by applicable law, workplace policy, and participant consent requirements.
