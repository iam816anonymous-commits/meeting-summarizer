# Resource Budget and Performance Gates

## Target profile
Initial target is a mid/low-memory Android phone, approximately 4 GB RAM and 64 GB storage. The exact handset and OS version must be confirmed before model selection. These are design constraints, not claims that any specific model already fits.

## Resource principles
- Audio capture must not require loading an LLM.
- Use bounded audio chunks and incremental persistence; avoid holding the whole meeting in RAM.
- Only one memory-heavy inference engine runs at a time.
- Model downloads are explicit, size-labelled, resumable where feasible, and removable by the user.
- Keep database indexes and cached transcript/model outputs bounded.
- Expose storage usage and provide a user-controlled cleanup flow that never silently deletes unsynced meeting data.
- Respect battery saver, thermal throttling, app lifecycle and Android background-execution constraints.

## Initial engineering budgets (targets to validate)
- Stop control: UI responds immediately; capture shutdown is initiated without waiting for transcription.
- Audio write: append bounded segments and recover after process interruption where platform lifecycle permits.
- UI: no model inference or blocking file/database work on the main thread.
- Memory: do not choose a local LLM until measured peak memory is safe on the target device; speech recognition is benchmarked independently.
- Storage: show estimated recording size and current retained audio/model footprint.
- Battery/thermal: benchmark 15-, 30- and 60-minute sessions; document degradation and temperature behavior.

## Benchmark protocol
Record handset model, Android version, language, sample duration, noise conditions, model file size, real-time factor, peak memory, CPU load, battery delta, transcript error observations, and failure/recovery behavior. Repeat each run and compare the same audio across engines.

## Release gate
No engine is declared "offline-ready" solely because its library supports Android. It must pass a physical-device test in airplane mode, sustain a representative session, preserve partial data after interruption, and meet acceptable accuracy/latency thresholds defined with real samples.
