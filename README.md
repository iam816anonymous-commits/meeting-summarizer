# Meeting Summarizer Agent

An offline-first Android meeting assistant. The user explicitly starts and stops each recording. The app stores transcripts locally, extracts evidence-linked decisions and candidate action items, and prioritizes actions relevant to the configured profile.

## Project status

- [Research and phased build plan](docs/BUILD_PLAN.md)
- [Phase 0 audit](docs/PHASE_0_AUDIT.md)
- [Phase 1 audit](docs/PHASE_1_AUDIT.md)
- [Phase 2 audit](docs/PHASE_2_AUDIT.md)
- [Phase 3 audit — recording lifecycle](docs/PHASE_3_AUDIT.md)
- [Phase 4 audit — offline English transcription](docs/PHASE_4_AUDIT.md)
- [Phase 5 audit — transcript review, search and recovery](docs/PHASE_5_AUDIT.md)
- [Phase 6 audit — deterministic offline summaries and evidence-linked findings](docs/PHASE_6_AUDIT.md)
- [Phase 6 follow-up — punctuationless ASR regression fix](docs/PHASE_6_FOLLOWUP_AUDIT.md)
- [Phase 7 audit — personal action items and progress UX](docs/PHASE_7_AUDIT.md)
- [Phase 8 research — local model and ASR evaluation](docs/PHASE_8_RESEARCH.md)
- [Phase 8 audit — adaptive model profiles, optional runtime and safety scaffolding](docs/PHASE_8_AUDIT.md)
- [Model artifact research — exact files, sizes, checksums, language candidates and integration gates](docs/MODEL_ARTIFACT_RESEARCH.md)

### Current implementation status

- **Current phase:** Phase 10 — reliability, privacy and performance hardening. The current subtask is model-download responsiveness, visible progress, and actionable failure reporting.
- **Latest model-download UI patch:** English, multilingual Whisper and optional local-model download flows are intended to run off the UI thread, show a spinner/stage, and report downloaded bytes/progress where available. The English installer exposes byte callbacks; Whisper verifies pinned file sizes and SHA-256; the generic local-model downloader also verifies catalog size/hash before activation.
- **Important acceptance boundary:** CI can verify compilation and unit tests, but it cannot prove that the user's mobile network can reach GitHub Releases or Hugging Face, that a full archive downloads on the phone, or that the installed model runs successfully. Do not mark model downloads device-verified until the phone test is completed.
- **Pending Phase 10 acceptance:** (1) install the current APK on the target phone; (2) tap Download English model and confirm the UI remains responsive while the stage and byte counter advance; (3) confirm install/verification succeeds and the model remains installed after app restart; (4) repeat with multilingual Whisper and one optional local LLM artifact; (5) test blocked/slow network, timeout, redirect, insufficient storage, corrupt/incomplete download, retry, and app backgrounding; (6) confirm the English model transcribes in Airplane mode; (7) record download duration, free storage, memory, heat and battery observations. Capture exact error text if any step fails.
- **Release blockers still open:** physical-device validation for recording notification cleanup, offline ASR accuracy/latency, transcript edit/search/delete persistence, Phase 6 action extraction quality, Phase 7 personal-action attribution/review persistence, model downloads and runtime memory/performance. CI passing is necessary but not sufficient.

- **Implemented:** recording lifecycle, local English ASR, timestamped transcript storage, transcript correction/search/deletion/recovery, deterministic evidence-linked summaries, and personal action-item ranking.
- **Phase 8 scaffolding in this branch:** ordered resource profiles (smallest eligible tier first), LiteRT-LM adapter for installed `.litertlm` files, opt-in verified-download primitive, conservative output guard, and privacy-preserving benchmark aggregation.
- **Implemented on the model-options branch:** recordings screen now has a language picker and explicit download for a pinned multilingual Whisper Tiny pack; all three files are checked against exact byte counts and SHA-256 before activation. Transcription dispatch uses the selected Whisper language/model, blocks non-English transcription through the English-only engine, and preserves the English Zipformer as the default. **Still incomplete:** in-app Qwen download/selection and model-backed summary integration, more language-specific fine-tuned packs, strict structured evidence validation, and real-phone benchmarks. Whisper Tiny is a multilingual baseline, not a promise of equal accuracy for every language. The capability button still assesses resource tiers; it does not yet download or execute a text-generation model.
- **Fallback guarantee:** model failure or absence must never prevent access to the deterministic offline summary. No model is forced, and no model is downloaded automatically.
- **Validation:** GitHub Actions runs unit tests, debug APK assembly, Android lint, APK verification and artifact upload. CI is not a substitute for physical-device testing.

## Product and privacy principles

- Recording is opt-in per meeting, visibly indicated, and easy to stop.
- Recording and deterministic summaries work locally and do not depend on network access.
- Any model download requires explicit user action, HTTPS, a reviewed artifact checksum and sufficient free storage.
- Cloud AI remains optional and disabled by default; local model inference must not send transcripts or audio over the network.
- Extracted tasks must link to transcript evidence; uncertain ownership is labelled for review.
- Never invent transcript text, speaker identities, owners or deadlines.
- Model resource profiles are screening heuristics, not compatibility guarantees. Test each model on the actual device before offering it as supported.

## Build

The project pins Android Gradle Plugin, Kotlin and Gradle versions. GitHub Actions assembles the debug APK, runs unit tests and Android lint, then publishes the APK as a workflow artifact. A verified Gradle Wrapper JAR and scripts are not yet committed; use Android Studio's Gradle tooling or CI until the wrapper can be generated and checked in.

## Privacy

Audio, transcripts and deterministic findings are stored locally. The app must not upload meeting audio or transcript text as part of local model inference. Only record meetings when permitted by applicable law, workplace policy and participant consent requirements.


## Optional cloud-enhanced summaries (Phase 9)

Cloud summarization is off by default and separate from recording/transcription. Open **menu → Cloud AI privacy & settings**, configure an HTTPS OpenAI-compatible chat-completions endpoint, model and your own API key, then explicitly enable it. The key is encrypted at rest using Android Keystore; no shared key is bundled. For each meeting, the app previews the redacted transcript and requires confirmation. Only transcript text, segment IDs and timestamps are sent, never raw audio or profile preferences. Common email/phone patterns are redacted, but redaction is not a privacy guarantee. Provider terms and retention policies still apply. Network/provider failures do not replace or remove the deterministic offline summary.
