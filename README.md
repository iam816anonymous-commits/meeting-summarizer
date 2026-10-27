# Meeting Summarizer Agent

An offline-first Android meeting assistant. The user explicitly starts and stops each recording. The planned app will save a transcript, summarize decisions and open questions, and prioritize action items and discussion points relevant to the configured user profile.

## Project status
- [Research and phased build plan](docs/BUILD_PLAN.md)
- [Phase 0 audit](docs/PHASE_0_AUDIT.md)
- [Phase 1 audit](docs/PHASE_1_AUDIT.md)
- [Phase 2 audit](docs/PHASE_2_AUDIT.md)
- **Current phase:** Phase 2 — UI and workflow state machine.
- **Implemented:** native Android app shell, interactive demo workflow, deterministic demo repository, workflow reducer and unit tests.
- **Not implemented yet:** real microphone recording, transcription, persistent meeting storage, action extraction from real transcripts, local LLM, cloud AI.

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
