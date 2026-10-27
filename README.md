# Meeting Summarizer Agent

An offline-first Android meeting assistant. The user explicitly starts and stops each recording. The app will save a transcript, summarize decisions and open questions, and prioritize action items and discussion points relevant to the configured user profile.

## Project status
- [Research and phased build plan](docs/BUILD_PLAN.md)
- [Phase 0 audit](docs/PHASE_0_AUDIT.md)
- [Phase 1 audit](docs/PHASE_1_AUDIT.md)
- **Current phase:** Phase 1 — Android project foundation and CI.
- **Implemented so far:** minimal native Android app shell and GitHub Actions build/lint workflow.
- **Not implemented yet:** microphone recording, transcription, summarization, action-item extraction, local LLM, cloud AI.

## Product principles
- Recording is opt-in per meeting, visibly indicated, and easy to stop.
- Local capture and saved data must not depend on network access.
- Cloud AI is optional, disabled by default, and requires explicit opt-in.
- Extracted tasks must link to transcript evidence; uncertain ownership is labelled for review.
- Never invent transcript text, speaker identities, owners, or deadlines.
- Target low-memory Android devices; benchmark on the actual target phone before choosing models.

## Build
The project pins Android Gradle Plugin, Kotlin and Gradle versions. GitHub Actions installs Gradle, runs debug APK assembly and Android lint, then publishes the APK as a workflow artifact. A verified Gradle Wrapper JAR and scripts are not yet committed; use Android Studio's Gradle tooling or CI until the wrapper can be generated and checked in.

## Privacy
Only record meetings when permitted by applicable law, workplace policy, and participant consent requirements. The app must make recording visible and must not record secretly or auto-start on boot.
