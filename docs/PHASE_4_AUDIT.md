# Phase 4 Audit — Offline Transcription Spike and Engine Integration

## Scope and boundaries
Phase 4 covers offline ASR evaluation, a provider-neutral speech-engine contract, timestamped transcript persistence, and recovery of interrupted per-audio-chunk work.

Excluded: summary generation, action-item extraction, cloud ASR, speaker identity claims, and changes to the Phase 3 recording UX unless integration proves them necessary.

## Engineering decisions
- Primary candidate: sherpa-onnx. Official documentation describes local Android inference without internet access and provides Android/Kotlin examples: https://k2-fsa.github.io/sherpa/onnx/android/index.html and https://github.com/k2-fsa/sherpa-onnx/tree/master/android.
- Alternative: whisper.cpp. Evaluate only if sherpa-onnx fails language/accuracy or device-performance gates: https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android.
- No model is production-selected until language, accuracy, latency, memory, model footprint, battery and noise tests run on the target physical phone.
- Android SpeechRecognizer is not used as the offline guarantee.
- Model files are not bundled into the APK by default, avoiding silent APK bloat and allowing explicit installation/removal later.
- Transcript segments use audio-relative millisecond timestamps and stable sequence numbers. SQLite stores segments and per-chunk processing jobs.
- RUNNING jobs are recovered to PENDING after restart. Failed jobs retain a bounded error string and can be retried without deleting audio.

## Implemented in this initial slice
- SpeechEngine interface with readiness checks, incremental segment callbacks and explicit retryable/non-retryable failure results.
- Validated TranscriptSegment domain model.
- TranscriptStore SQLite persistence for segments and resumable chunk-job metadata.
- Unit tests for segment timestamp, text and confidence invariants.

## Benchmark protocol still required
Run the same short and long recordings on the target phone with airplane mode enabled after model installation. Record:
- exact phone model, Android version, CPU/ABI and available storage;
- model name/version, quantization, downloaded size and unpacked size;
- language(s), including English/Telugu code-switching if required;
- word error rate against a corrected reference transcript, names/numbers accuracy and noisy-room performance;
- real-time factor (processing seconds / audio seconds), startup latency, peak memory, battery and thermal impact;
- whether every input chunk yields persisted segments or a recoverable error.

Do not promote an engine to default until measurements are recorded and the model license is checked.

## Acceptance status
IN PROGRESS. This slice establishes the contract and persistence foundation; it does not yet contain a production ASR adapter or model assets. Next: integrate the selected sherpa-onnx runtime/model-loading path and wire recovered audio chunks to the engine. Final acceptance requires airplane-mode transcription and long-session bounded-memory tests on the physical target phone.

## Verification
CI must pass unit tests, APK assembly, Android lint, APK existence and artifact upload. Physical-device performance and offline behavior cannot be claimed from CI alone.
