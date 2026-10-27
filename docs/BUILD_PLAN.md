# Meeting Summarizer Agent — Research and Phase-by-Phase Build Plan

**Status:** Phase 0 specification freeze in progress; implementation proceeds phase by phase after the Phase 0 audit  
**Target:** Android app that lives on the phone, starts/stops recording only on the user's command, transcribes meetings offline, produces a useful summary, and highlights only items relevant to the user.  
**Primary principle:** Offline-first, privacy-first, small-device-aware. Network AI is an optional enhancement, never a dependency for core meeting capture or saved data.

---

## 1. Product definition

Build a mobile-resident meeting agent. The user explicitly taps **Start meeting** (and later **Stop meeting**) or uses a clearly disclosed in-app action/notification. While active, it captures room audio, processes it locally, and creates:

1. A concise meeting summary.
2. Decisions and unresolved questions.
3. **My action items** — tasks assigned to, promised by, or clearly requiring follow-up from the user.
4. Deadlines and owners when explicitly stated.
5. Important points that affect the user, separated from general discussion.
6. A transcript that can be searched and corrected.
7. Confidence/uncertainty markers rather than fabricated names, owners, or deadlines.

The app must not secretly or continuously listen. Recording is off by default, starts only after an explicit user action, shows a persistent Android recording notification, and has a prominent stop control.

### Definition of “points related to me”

This needs explicit identity context, not guesswork. On first use, the user configures their preferred name, common name variants, and optionally how they are referred to at work. During a meeting, the agent identifies possible references such as:
- Direct assignment: “You will send the report by Friday.”
- First-person commitment in the user's identified speech: “I’ll send the report.”
- Explicit mention: “Can you ask [configured name] to review this?”
- Dependency or follow-up: a decision blocks the user's work, but only when supported by the transcript.

Every extracted item should include its supporting transcript quote and timestamp. If speaker identity or ownership is uncertain, show **Needs confirmation**. Do not silently equate a speaker label such as “Speaker 2” with the user. Speaker diarization can separate voices imperfectly; it does not automatically know a real-world person's name.

---

## 2. Research findings and engineering implications

### 2.1 Offline speech recognition is achievable on Android

**Recommended first engine to benchmark: sherpa-onnx.** It supports Android, streaming and non-streaming speech recognition, voice activity detection (VAD), speaker diarization and Kotlin/Java APIs, with local processing and no internet requirement for inference. This is a strong fit for offline capture and incremental transcripts. Select the actual model only after measuring English and the user's real meeting languages on the target phone.

Reference: [sherpa-onnx repository](https://github.com/k2-fsa/sherpa-onnx), [Android examples](https://github.com/k2-fsa/sherpa-onnx/tree/master/android), [documentation](https://k2-fsa.github.io/sherpa/onnx/).

**Alternative to benchmark: whisper.cpp.** It has an Android sample and can run compact Whisper models locally. It is useful as a quality/latency comparison, but native build and model size/latency must be measured before choosing it for a 4 GB RAM phone.

Reference: [whisper.cpp Android example](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android).

**Do not treat Android's built-in SpeechRecognizer as the guaranteed offline engine.** Android documentation notes that its implementation may stream audio to remote servers and that it is not intended for continuous recognition due to battery and bandwidth consumption. On-device recognition availability also varies by installed recognition service/device/language. It may be a fallback only after runtime capability checks, never the offline foundation.

Reference: [Android SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer).

### 2.2 Offline summarization needs a separate strategy

Speech-to-text and summarization are separate workloads. An offline transcript does not automatically mean an offline high-quality summary.

Use a layered design:
- **Baseline offline:** deterministic extraction and ranking rules for decisions, commitments, deadlines, names, open questions, and candidate action items. Every result links to transcript evidence.
- **Optional on-device LLM:** investigate Google's LiteRT-LM and compatible small quantized models only after a device benchmark. Keep it optional because generation speed, RAM use, model download size, thermal throttling and battery impact can be unacceptable on lower-memory devices.
- **Optional online model:** when internet is available and the user has explicitly enabled cloud processing, send only the minimum required text (prefer transcript chunks or a redacted transcript, not raw audio by default) to a configurable provider. Show which mode was used. If network access fails, continue locally and never lose the meeting.

Reference: [Google AI Edge LiteRT-LM Android guide](https://ai.google.dev/edge/litert-lm/android), [LiteRT-LM samples](https://github.com/google-ai-edge/LiteRT-LM).

Do not choose a model from benchmark headlines alone. Run a repeatable benchmark on the target physical device with actual meeting samples, target languages, long sessions, and noisy-room audio.

### 2.3 Recording lifecycle is an Android platform constraint

Microphone capture while the app is not visible must be designed around Android foreground-service rules, not treated as an unrestricted background daemon. Start recording from a visible activity following a user action, request microphone permission, and use a properly declared microphone foreground service with a persistent notification and a stop action. Android 12+ restricts starting foreground services from the background, and Android 14+ applies explicit microphone foreground-service permission/type and while-in-use requirements. Do not attempt to auto-start recording on boot.

References:
- [Foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Foreground-service types and requirements](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android 14 foreground-service changes](https://developer.android.com/develop/background-work/services/fgs/changes)

The app must handle interruption by calls, audio focus changes, microphone denial, process death, low storage, battery optimization, and OEM-specific background killing. Never claim a recording is complete if audio chunks were lost.

### 2.4 “Agent living in the mobile” should mean a reliable stateful workflow

Do not start with a general-purpose autonomous agent or a large always-running LLM. Start with a bounded, auditable pipeline:

**User command → recording session → audio chunking → local ASR → durable transcript segments → evidence-grounded extraction → personal relevance ranking → reviewable summary.**

The agent can feel alive through a persistent session state, recording notification, live transcript, progress, recovery, and a clear post-meeting workflow—not through invisible microphone access or uncontrolled background activity.

### 2.5 Privacy and meeting consent are core requirements

Meetings may contain confidential or personal information. Before recording, display a clear notice and a reminder to obtain consent/authorization from participants as required by the meeting context and applicable law/policy. Do not claim that a single consent rule applies to every situation. Provide local-only mode, visible recording status, explicit cloud opt-in, data export, per-meeting deletion, and a configurable retention policy. Never upload audio or transcript to a provider without an explicit setting and clear disclosure.

---

## 3. Target device and platform assumptions

Initial target is a physical Android phone with roughly **4 GB RAM and 64 GB storage**. The design must remain usable when a local LLM is unavailable. Use Kotlin, Jetpack Compose, Room/SQLite, coroutines/Flow, and a modular Android architecture. Avoid Docker and avoid a mandatory server/database service.

Suggested compatibility target: API 27+ if the chosen native libraries and dependencies can support it; validate the exact min/target SDK against current Android requirements during Phase 0. Microphone foreground-service behavior must be tested on actual Android versions and the target OEM. Physical-device tests are mandatory; emulator-only success is insufficient.

**Resource policy**
- Store audio as bounded, recoverable chunks; do not keep the entire meeting in RAM.
- Stream recognition incrementally where the selected engine/model supports it.
- Use a bounded queue with backpressure; never let slow inference grow memory without limit.
- Persist transcript segments frequently so process death does not erase the meeting.
- Make model downloads explicit, resumable, size-visible, and removable.
- Let the user choose whether to retain audio after transcription. Default retention should be conservative and clearly disclosed.
- Monitor battery, thermal throttling, free disk space, and recognition lag during long-session tests.

---

## 4. Proposed architecture

Keep the core pipeline provider-independent so local and online models can be swapped without rewriting session logic.

```text
Jetpack Compose UI
  ├─ Start / Pause / Resume / Stop
  ├─ Live transcript and session status
  ├─ Summary / My action items / Decisions
  └─ Review, edit, export, delete
          |
Session Coordinator (explicit finite-state machine)
          |
Microphone Foreground Service + Audio Capture
          |
Chunk Store (temporary files) ── Recovery / integrity metadata
          |
Audio Pipeline: VAD / level checks / chunk boundaries
          |
Speech Engine Interface
  ├─ Offline sherpa-onnx (candidate default)
  ├─ Offline whisper.cpp (benchmark alternative)
  └─ Optional OS/provider adapter (never assumed offline)
          |
Timestamped Transcript Repository (Room / SQLite)
          |
Extraction Pipeline
  ├─ Deterministic evidence-based rules (always available)
  ├─ Optional local LLM adapter
  └─ Optional cloud LLM adapter (explicit opt-in)
          |
Personal Relevance + Action Item Ranker
          |
Summary Repository (Room / SQLite)
```

### Suggested modules

Start as one Android app with clean packages, not premature microservices:
- `app`: Compose screens, navigation, permission education.
- `core/session`: session state machine and domain models.
- `core/audio`: audio interfaces, chunk metadata, recovery contracts.
- `core/transcription`: ASR interface and transcript segment model.
- `core/summary`: evidence-grounded extraction and summary schema.
- `core/personalization`: profile, aliases, relevance scoring, confirmation policy.
- `data/local`: Room DAOs, migrations, file storage, retention/deletion.
- `data/speech/sherpa`: offline engine adapter.
- `data/summary/local`: deterministic offline summarizer.
- `data/summary/llm`: optional local/cloud adapters.
- `service/recording`: foreground service, notification actions, lifecycle.
- `benchmark`: reproducible device profiling and evaluation fixtures.

Do not add every module on day one if it increases build fragility. Keep clear interfaces and split modules when the boundary is justified.

### Core data entities

- **MeetingSession:** ID, title, start/end times, state, language, selected processing mode, audio-retention choice, integrity flags.
- **AudioChunk:** session ID, sequence, file reference, start/end offsets, checksum, completion status.
- **TranscriptSegment:** session ID, sequence, start/end timestamps, text, optional speaker label, recognition confidence when available, edited flag.
- **MeetingSummary:** session ID, generated time, engine/mode, summary text, review status.
- **ActionItem:** text, proposed owner, due date, priority, relevance type, confidence, review status, evidence segment IDs/quotes.
- **Decision / OpenQuestion:** text, confidence, evidence segment IDs.
- **UserProfile:** preferred name, aliases, optional work context, speaker-identification state, privacy preferences.
- **ProcessingJob:** type, progress, retry state, last error; enables resumable post-meeting processing.

Use schema migrations from the first database version. Keep secrets out of source control and logs.

---

## 5. Meeting workflow and state machine

Required states:

`IDLE → PREPARING → RECORDING ↔ PAUSED → FINALIZING → TRANSCRIBING → SUMMARIZING → REVIEW_READY`

Failures transition to a recoverable state with a visible error. A stop request must stop microphone capture immediately, then finish pending chunks and post-processing safely.

1. User taps **Start meeting**.
2. App explains/requests microphone permission if needed and confirms recording disclosure.
3. App starts the foreground service while the activity is visible; notification becomes visible.
4. Capture audio into bounded chunks. Persist chunk metadata and transcript updates.
5. Run offline streaming ASR if the chosen model supports it. If ASR falls behind, preserve audio and show lag instead of dropping it silently.
6. User taps **Stop meeting** or the notification's stop action.
7. Finalize audio/chunk integrity; finish pending transcription.
8. Extract decisions, commitments, questions, deadlines, candidate tasks, and evidence quotes.
9. Rank personal relevance using profile aliases, assigned ownership, first-person commitments, and dependency language. Flag ambiguity.
10. Produce summary using the offline rules first. If explicitly enabled and available, allow local LLM refinement or user-approved cloud refinement.
11. Present review screen with **My action items** first, then decisions, general summary, open questions, and transcript.
12. User can correct text/ownership, mark complete, export, or delete the meeting.

**Important limitation:** A phone microphone can capture nearby in-person conversation. It generally cannot capture the clean internal audio of arbitrary third-party meeting/calling apps because of Android audio-capture and privacy restrictions. Do not promise that it can record Zoom/Teams/phone-call audio in all circumstances. For those use cases, support importing an authorized recording or transcript as a separate workflow, after verifying file format and source permissions.

---

## 6. Summary quality and “relevant to me” logic

### Output format

- **Executive summary:** 3–8 concise bullets, depending on meeting length.
- **My action items:** verb-led task, owner, deadline, confidence, evidence quote/time.
- **Decisions:** what was decided, not merely discussed.
- **Dependencies affecting me:** only if the relationship is explicit in evidence.
- **Questions for me / follow-ups:** unresolved items assigned to or requiring response from the user.
- **Other key points:** brief context, lower priority than personal actions.
- **Uncertain items:** statements needing user confirmation.
- **Transcript:** searchable, timestamped and editable.

### Extraction rules

1. Preserve evidence. Every important summary/action item must point to transcript segment IDs and timestamps.
2. Distinguish **proposal**, **discussion**, **decision**, and **commitment**.
3. Never infer a deadline when none was stated.
4. Do not mark an item as assigned to the user merely because their name was mentioned.
5. Do not assume the diarization label identifies a person.
6. If a pronoun such as “you” has unclear referent, mark owner uncertain.
7. Avoid duplicate action items across repeated discussion.
8. Let the user edit the profile and correct attribution; learn only from explicit corrections.
9. When cloud/local LLM output conflicts with transcript evidence, show the evidence and require review rather than silently trusting the model.
10. Use structured output validation; malformed model output must fall back to the deterministic summarizer.

### Evaluation metrics

- Word error rate (WER) and named-entity accuracy for transcription.
- Action-item precision and recall.
- Owner attribution accuracy.
- Deadline extraction accuracy.
- Evidence coverage: percentage of extracted items with valid transcript evidence.
- Hallucination rate: claims with no supporting evidence.
- End-to-end processing time and real-time factor.
- Peak RAM, battery drain per hour, thermal behavior, disk usage and lost-audio rate.
- Recovery success after app process kill, screen lock, incoming call, low storage, and network loss.

A “good summary” is not enough: **correct owner attribution and no fabricated commitments are release-blocking requirements.**

---

## 7. Offline-first and online-enhancement policy

### Always available offline
- Start/stop/pause session, audio capture, local file persistence.
- Offline ASR with a model installed and supported language.
- Transcript review/edit/search.
- Deterministic summary and action-item candidates.
- Personal relevance ranking with uncertainty flags.
- Room/SQLite storage, export, deletion and recovery.

### Optional, user-controlled enhancement
- Downloadable local ASR models/languages.
- Optional on-device LLM for better rewriting/long-context synthesis if benchmarks show acceptable performance.
- Optional cloud summarization when online, only after explicit opt-in and disclosure.
- Optional model/provider settings and per-meeting mode selection.

### Failure behavior
- No internet: no blocking spinner; remain fully functional in local mode.
- No local ASR model: explain that offline transcription cannot run until a compatible model is installed; allow authorized audio import/queueing.
- Local LLM unavailable/too slow: use deterministic summary.
- Cloud provider fails or rate-limits: retry safely or fall back locally; do not duplicate tasks.
- Low storage: warn before starting when possible; stop safely before storage exhaustion and preserve completed chunks.
- Low confidence: mark for review; do not invent.

The cloud provider must sit behind an interface and must not be hardwired to one vendor. API keys must not be embedded in the APK. If a provider requires a secret key, use an appropriately secured user-supplied key or a later secured backend; do not ship a shared secret in the app.

---

## 8. Phase-by-phase implementation plan

**Governance rule:** Work on one phase at a time. Before modifying source code, state the active phase, scope, excluded work, acceptance criteria, and files expected to change. Do not pull later-phase features forward. Each phase ends with a written audit and evidence from build/tests or a physical-device test. No phase is complete based only on “code added.”

### Phase 0 — Clean-room research, repository audit, and frozen specification
**Goal:** Remove ambiguity before coding.

**Tasks**
- Inspect repository state, default branch, permissions, existing workflows and files.
- Record Android/API targets, build tools, Kotlin/Compose versions and native-library compatibility requirements.
- Benchmark/compare sherpa-onnx versus whisper.cpp for candidate languages and 4 GB RAM hardware.
- Verify foreground-service behavior for supported Android versions and OEM restrictions.
- Define data lifecycle, consent UX, threat model, profile/alias setup and cloud opt-in.
- Freeze architecture, data schema, phase governance, test plan and model evaluation criteria.

**Deliverables**
- `docs/RESEARCH_AND_ARCHITECTURE.md`
- `docs/PHASE_PLAN.md` (this plan may later be split into this canonical file)
- `docs/PRIVACY_AND_THREAT_MODEL.md`
- `docs/DEVICE_BENCHMARK_PLAN.md`
- `docs/PHASE_0_AUDIT.md`

**Acceptance**
- Every major technology decision has a rationale and source link.
- Model choice is labeled provisional until tested on physical hardware.
- Risks and unsupported promises are explicit.
- No production feature implementation before Phase 0 approval.

### Phase 1 — Build foundation and CI
**Goal:** A reproducible Android project that builds cleanly.

**Tasks**
- Kotlin + Compose app shell; package conventions and dependency catalog.
- Room/SQLite setup with versioned migrations.
- Unit-test infrastructure, lint/static checks, CI workflow and debug APK artifact.
- API compatibility review; no Docker or mandatory backend.

**Acceptance**
- Clean checkout can run Gradle tests, lint, and `:app:assembleDebug`.
- CI proves commit SHA and uploads a verified APK artifact.
- No unresolved build/lint errors; failures are fixed at root cause.

### Phase 2 — UX shell and session state machine (no real recording yet)
**Goal:** Wire UI to real domain state, not hard-coded demo values.

**Tasks**
- Screens: Home, New Meeting, Live Session, Transcript, Summary, My Tasks, Settings/Privacy.
- Explicit states and valid transitions; loading/error/empty states.
- Fake audio/ASR interfaces only in tests, not represented as real capture.

**Acceptance**
- Every button has a defined action.
- Invalid transitions are rejected and covered by unit tests.
- State survives activity recreation where expected.

### Phase 3 — Recording lifecycle and durable audio capture
**Goal:** Reliable, visible, user-controlled recording.

**Tasks**
- Runtime microphone permission and disclosure screen.
- Microphone foreground service, persistent notification and notification stop action.
- Chunked audio files, metadata, checksums, disk-space guard and recovery.
- Handle screen lock, app background, interruption, service destruction and OEM behavior.

**Acceptance**
- Physical-device recording works while screen is off after the user starts it visibly.
- Stop action always stops capture; no silent auto-start.
- Process death cannot silently mark a partial recording as complete.
- Recording state and audio integrity are accurately reported.

### Phase 4 — Offline transcription spike and engine integration
**Goal:** Produce timestamped transcript segments without network access.

**Tasks**
- Build a small standalone benchmark for sherpa-onnx candidate models; compare whisper.cpp only if useful.
- Evaluate target languages, noise, code-switching, names, battery, memory, latency and model size.
- Integrate selected engine behind a speech-engine interface.
- Persist partial/final segments and recover pending work.

**Acceptance**
- Airplane-mode transcription succeeds with installed model.
- No dependence on Android's cloud-capable SpeechRecognizer for the offline guarantee.
- Long-session test shows bounded memory and no unexplained missing chunks.
- Chosen model and limitations are documented.

### Phase 5 — Transcript editing, search and session recovery
**Goal:** Transcript becomes durable, inspectable source evidence.

**Tasks**
- Live transcript updates, timestamps, optional speaker labels, edit/correction UI.
- Search, session history, resumable post-processing.
- Audio retention preference, export and deletion behavior.

**Acceptance**
- Transcript persists across force-stop/relaunch and database migration tests.
- Corrections are preserved and used by later summary generation.
- Delete operation removes the meeting's database rows and retained audio/model artifacts associated only with that meeting.

### Phase 6 — Deterministic offline summaries and evidence-linked tasks
**Goal:** Useful offline output before adding LLMs.

**Tasks**
- Rule-based extraction of decisions, deadlines, commitments, questions and candidate tasks.
- Evidence quote/time linkage and deduplication.
- Summary schema and confidence/ambiguity markers.

**Acceptance**
- Works entirely offline.
- Each generated action item links to valid transcript evidence.
- No unsupported deadline/owner is silently fabricated.
- Unit and fixture-based tests cover negation, tentative proposals, repeated items and ambiguous pronouns.

### Phase 7 — Personal profile and “My action items”
**Goal:** Correctly prioritize the user's work.

**Tasks**
- Preferred name, aliases, work context and privacy controls.
- Distinguish explicit assignment, self-commitment, mention and dependency.
- Optional speaker enrollment/confirmation experiment; never assume speaker identity from diarization alone.
- Review/confirm/reject workflow and feedback from explicit corrections.

**Acceptance**
- Evaluation set includes direct assignments, indirect references, same-name people, pronouns, speaker-label uncertainty and false positives.
- Uncertain attribution is labeled; user can correct it.
- “My action items” is the primary post-meeting view and can be filtered by confidence/status.

### Phase 8 — Optional on-device LLM summarization
**Goal:** Improve wording and long-context synthesis without breaking offline mode.

**Tasks**
- Benchmark LiteRT-LM and small quantized candidates on target device.
- Use structured schema-constrained output where supported, strict validation, cancellation and time budgets.
- Keep deterministic extraction as ground truth/fallback; LLM must cite transcript segment IDs.

**Acceptance**
- No crash or unbounded memory growth on the target device.
- If the local model is missing, disabled, too slow or fails validation, deterministic mode still works.
- Quality improvement is measured against the Phase 0 evaluation set before enabling by default.

### Phase 9 — Optional cloud-provider adapter
**Goal:** Offer enhanced summaries when online, with explicit consent.

**Tasks**
- Provider-neutral interface and user-configurable provider settings.
- Explicit opt-in, per-meeting mode selection, redaction/minimization, cancellation, timeout and retry.
- Never upload raw audio by default; disclose exactly what text is sent.
- Key management that does not embed a shared API key in the APK.

**Acceptance**
- Airplane mode and provider failure do not block capture, transcript access or offline summary.
- Network logs verify that no transcript/audio leaves the device in local-only mode.
- Cloud use is visible in meeting metadata and privacy settings.

### Phase 10 — Reliability, privacy and performance hardening
**Goal:** Prove robustness, not just demo functionality.

**Tasks**
- Threat model review, exported component checks, encrypted-at-rest option assessment, backup policy and secure logging.
- Database migration and deletion tests.
- Long-meeting, low-storage, process-kill, notification stop, incoming-call and network-flap scenarios.
- Battery/thermal/memory profiling on physical target device.

**Acceptance**
- No recording without explicit user start.
- No sensitive transcript/audio in logs or analytics.
- Failure states are recoverable and honest; no lost audio is reported as success.
- Performance thresholds are measured and documented.

### Phase 11 — Release candidate and physical-device validation
**Goal:** Deliver a reliable installable APK.

**Tasks**
- Clean-room build from the exact commit.
- Lint, unit tests, integration tests, dependency/license review and manifest review.
- Manual test matrix on the actual target phone and at least one additional supported Android version if available.
- User acceptance test with representative meeting samples and correction of critical defects.

**Acceptance**
- `:app:assembleDebug` passes locally and in CI; release build checked if configured.
- APK artifact exists, is downloadable, and corresponds to the tested commit.
- No open release-blocking defects in capture, transcript persistence, attribution, privacy or offline fallback.

### Phase 12 — Controlled follow-up improvements
**Goal:** Expand only based on measured failure data.

Possible later work: language/model packs, improved diarization, calendar integration, reminders/export, encrypted backup, speaker enrollment, document/context import and richer cloud providers. Each must have a separate scope and privacy review. Do not add these before the core offline pipeline is dependable.

---

## 9. Test strategy and release gates

### Required automated tests
- Session state-machine transition tests.
- Chunk sequencing, checksum and incomplete-file recovery.
- Room schema/migration and repository tests.
- Transcript segment ordering and timestamp tests.
- Extraction fixtures: assignment vs suggestion, decision vs discussion, negation, deadlines, aliases, repeated tasks, ambiguous owners.
- Evidence-link validation and structured-output validation.
- Provider fallback, cancellation, timeout and retry tests.
- No-network tests proving offline pipeline does not invoke cloud adapters.
- UI ViewModel tests for every primary action and error state.

### Required physical-device scenarios
1. Record a 2-minute meeting, stop, inspect transcript and summary.
2. Record a longer meeting with screen off; verify ongoing notification and no audio gaps.
3. Enable airplane mode before starting and keep it on through summary generation.
4. Disable/remove local LLM and verify deterministic offline summary still works.
5. Kill the app process during recording, then relaunch and inspect recovery/integrity status.
6. Trigger notification stop and verify microphone closes promptly.
7. Handle microphone denial, low storage, interruption, battery saver and thermal throttling.
8. Test realistic noise, multiple speakers, names, English and the user's selected additional language(s).
9. Include examples where another person shares the user's name and where “you” is ambiguous.
10. Verify local-only mode causes no network upload.
11. Edit a transcript segment and verify downstream output reflects the correction.
12. Delete a meeting and verify retained audio and transcript data are actually removed.

### Release-blocking defects
- Unrequested or invisible recording.
- Silent audio loss or false claim of complete recording.
- Offline mode requiring internet.
- Fabricated action item, owner or deadline presented as fact.
- Transcript upload in local-only mode.
- App crash/data loss during common lifecycle events.
- APK missing from CI or not tied to the tested commit.

---

## 10. Initial decisions vs. open questions

### Provisional decisions
- Android-native Kotlin + Compose.
- Room/SQLite; no required server or Docker.
- Offline-first pipeline with deterministic summary always available.
- Benchmark sherpa-onnx first; compare whisper.cpp if results justify it.
- Local LLM and cloud LLM are optional adapters.
- User explicitly starts/stops recording.
- Evidence-linked personal action items, with uncertainty shown.

### Must be answered/validated in Phase 0
- Exact target phone model and Android version(s).
- Primary meeting languages (English, Telugu, Hindi, mixed-language/code-switching, etc.).
- Expected meeting duration and acceptable post-processing delay.
- Whether audio must be retained after transcription.
- Whether the user wants speaker-name attribution or only transcript-based personal task detection initially.
- Acceptable model download size, storage ceiling, battery drain and offline accuracy.
- Current repository/CI starting state and min SDK compatibility of selected native dependencies.

Do not block initial architecture on every preference. Use conservative defaults, document assumptions, and benchmark the risky technical choices.

---

## 11. Source register

1. Android SpeechRecognizer API — limitations and recognition-service behavior: https://developer.android.com/reference/android/speech/SpeechRecognizer
2. Android foreground-service background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
3. Android foreground-service types: https://developer.android.com/develop/background-work/services/fgs/service-types
4. Android foreground-service behavior changes: https://developer.android.com/develop/background-work/services/fgs/changes
5. sherpa-onnx source and capabilities: https://github.com/k2-fsa/sherpa-onnx
6. sherpa-onnx Android examples: https://github.com/k2-fsa/sherpa-onnx/tree/master/android
7. sherpa-onnx documentation: https://k2-fsa.github.io/sherpa/onnx/
8. whisper.cpp Android example: https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android
9. Google AI Edge LiteRT-LM Android guide: https://ai.google.dev/edge/litert-lm/android
10. LiteRT-LM Android samples: https://github.com/google-ai-edge/LiteRT-LM

**Research caveat:** These sources establish platform and project capabilities, not that a particular model will meet latency, accuracy, RAM or battery targets on this user's phone. Those claims require repeatable physical-device measurements. Re-check official documentation and dependency compatibility during Phase 0 because Android requirements and model runtimes evolve.
