# Meeting Summarizer Agent

An offline-first Android meeting assistant. The user explicitly starts and stops each recording. The app saves a transcript, summarizes decisions and open questions, and prioritizes action items and discussion points relevant to the configured user profile.

## Product principles
- Recording is opt-in per meeting, visibly indicated, and easy to stop.
- Local capture and saved data must not depend on network access.
- Cloud AI is optional, disabled by default, and requires explicit opt-in.
- Extracted tasks must link to transcript evidence; uncertain ownership is labelled for review.
- Never invent transcript text, speaker identities, owners, or deadlines.
- Target low-memory Android devices; benchmark on the actual target phone before choosing models.

## Project status
- [Research and phased build plan](docs/BUILD_PLAN.md)
- [Phase 0 audit](docs/PHASE_0_AUDIT.md)
- Phase 0 freezes the specifications. App implementation begins only after its scope and constraints are checked.

## Development
The implementation stack and build commands will be pinned during Phase 1 after checking the current Android toolchain and CI environment. Do not infer that the application is runnable until a successful build and device smoke test are recorded.

## Privacy
Only record meetings when permitted by applicable law, workplace policy, and participant consent requirements. The app must make recording visible and must not record secretly or auto-start on boot.
