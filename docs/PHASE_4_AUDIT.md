# Phase 4 Audit — Offline English Transcription

## Scope and boundaries
Phase 4 covers offline ASR integration, an explicit model installation path, timestamped transcript persistence, and recovery of interrupted per-audio-chunk work.

Excluded: summary generation, action-item extraction, cloud ASR, speaker identity claims, and unrelated recording UX changes.

## Research and model decision
- Runtime: sherpa-onnx Kotlin/Android API. Official documentation says recognition is performed locally without internet access: https://k2-fsa.github.io/sherpa/onnx/android/index.html.
- Initial model: `sherpa-onnx-zipformer-small-en-2023-06-26`, using its INT8 encoder/decoder/joiner files. Official model documentation lists an INT8 encoder of about 25 MB, a 1.2 MB INT8 decoder, and a 253 KB INT8 joiner: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html.
- The model is English-only and was trained on LibriSpeech. It is a small-device integration candidate, not a claim of meeting-domain accuracy. Names, accents, cross-talk, noisy rooms, and spontaneous business speech need physical-device evaluation.
- The model is not bundled into the APK. The user explicitly downloads and installs it once; the UI checks required files before enabling transcription. Model archive extraction rejects path traversal and links and enforces a size ceiling.
- Internet permission is used only for the explicit model download path. The speech engine itself does not use network APIs. Meeting audio and transcript text remain app-private.
- Android's cloud-capable `SpeechRecognizer` is not used as the offline guarantee.

## Implemented in this branch
- `SpeechEngine` interface with model readiness and structured failure results.
- `SherpaOnnxEnglishSpeechEngine` backed by sherpa-onnx's INT8 small Zipformer English model.
- M4A/AAC decoding through Android `MediaExtractor` / `MediaCodec`, downmixing and resampling to 16 kHz mono.
- Audio is processed in bounded 30-second windows rather than loading an entire meeting into memory.
- Explicit model download, safe extraction, and required-file verification.
- Recordings UI actions to install the model, transcribe completed sessions, and view timestamped transcript segments.
- SQLite persistence for transcript segments and per-audio-chunk jobs. Segments are saved as they are recognized; interrupted RUNNING jobs are recovered to PENDING on app launch.
- Session-wide sequence and timestamp offsets preserve ordering across the recorder's five-minute audio files.

## Limitations and risks
- The small model is a provisional candidate. A physical-device accuracy, latency, RAM, battery and thermal benchmark is still required before it can be considered release-ready.
- Model download requires an internet connection once; transcription after installation is intended to work with Airplane mode enabled.
- Transcription currently runs while the app process is alive. If the process is killed, saved segments remain and job recovery makes unfinished audio eligible for retry, but the user must reopen the app and start the retry.
- The transcript represents recognized speech only. Empty output is possible for silence or audio the model cannot recognize; the app must not invent text.
- Native runtime dependency size and final APK size must be checked in CI. The configured Android ABIs are arm64-v8a and armeabi-v7a.

## Verification and acceptance
- Automated acceptance: unit tests, APK assembly, Android lint, APK existence, and artifact upload in GitHub Actions.
- Physical-device acceptance remains required: install model while online; enable Airplane mode; record clear English speech; transcribe; compare text against the spoken words; close/reopen and confirm transcript persistence; repeat with a longer meeting; inspect latency, heat, battery and memory.
- CI success cannot prove microphone behavior, model download reliability, actual speech accuracy, or offline inference on the target phone.

## Status
IN PROGRESS until CI passes and the physical-device Airplane-mode test succeeds.
