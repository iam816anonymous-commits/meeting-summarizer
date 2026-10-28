# Model artifact research — 2026-10-28

This document records concrete downloadable model artifacts and the evidence needed before the app advertises them as installable. Model names are not enough: inference implementation, runtime version, memory use, and checksums must match.

## Summary: what is actually ready?

- **Text generation:** Two real LiteRT-LM Qwen3-0.6B INT4 files exist with published SHA-256 and exact byte lengths. The current app pins LiteRT-LM 0.16.1; these artifacts are documented for LiteRT-LM 0.16.0-era builds, but that is not proof that the app's exact Android dependency loads them. Treat as downloadable candidates pending an Android instrumentation/device smoke test. The current app's verified downloader is not yet wired to the UI.
- **ASR:** A sherpa-onnx Whisper model collection publishes multilingual and Indic-language packs. The app currently implements only a Zipformer English recognizer and its existing downloader expects a tar.bz2 archive. These Whisper packs are **not drop-in replacements**. They require a Whisper engine configuration and separate model-file integrity verification. Do not show them as install-and-use choices until that integration is complete.
- **Device fit:** 4 GB RAM does not guarantee an LLM will run. Qwen3-0.6B INT4 model files are ~331 MiB, but measured peak process memory can approach 1 GB on newer flagship devices; the user's target device is much weaker. Keep local deterministic summaries as fallback.

## Text-generation candidates (actual single-file artifacts)

| Choice | Artifact | Published size | SHA-256 | Direct download |
|---|---|---:|---|---|
| Compact, direct answers (no-think template) | `qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm` | 347,251,840 bytes (~331.2 MiB) | `2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139` | https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm |
| Reasoning template (thinking on) | `qwen3_0.6b_q4_block32_ekv1280.litertlm` | 347,251,840 bytes (~331.2 MiB) | `312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715` | https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_q4_block32_ekv1280.litertlm |

- Publisher/model card: https://huggingface.co/litert-community/Qwen3-0.6B-int4
- License reported by model card: Apache-2.0.
- These two files differ in embedded thinking behavior; they are not different model sizes/quality tiers.
- Do not select the MediaTek MT6993 NPU artifact for a generic Android phone; it is chipset-targeted.
- The alternative Qwen3-0.6B dynamic INT8 artifact is 614,230,168 bytes, SHA-256 `346f9b2e76b95ed904351d4000e68036ab02db4dfad94d34980d021279ce754f`, and its model card says it needs LiteRT-LM 0.18.0+. It is **not compatible with the currently pinned 0.16.1 dependency by the publisher's own notes**, so do not offer it until the runtime is upgraded and validated.

## Speech-to-text candidates (sherpa-onnx Whisper; not yet integrated)

Publisher/model card: https://huggingface.co/ippocode/indic-asr-onnx

The published pack table reports these approximate total sizes:
- `whisper-tiny`: ~99 MB; multilingual, including English and 90+ other languages.
- `whisper-small-hi`: ~358 MB; Hindi fine-tune.
- `whisper-tiny-te`: ~99 MB; Telugu fine-tune.
- `whisper-small-ta`: ~358 MB; Tamil fine-tune.
- `whisper-small-ml`: ~358 MB; Malayalam fine-tune.
- `whisper-tiny-kn`: ~99 MB; Kannada fine-tune.

Pack layout: `encoder.int8.onnx`, `decoder.int8.onnx`, and `tokens.txt`. Android/Kotlin example uses sherpa-onnx `OfflineWhisperModelConfig(encoder, decoder, language, task)` with `OfflineRecognizerConfig`, not the existing Zipformer transducer configuration.

Direct model collection: https://huggingface.co/ippocode/indic-asr-onnx/tree/main

**Important integrity limitation:** the pack table gives approximate sizes but this research pass did not establish immutable revision IDs and SHA-256 for every file in each pack. Therefore these are identified artifacts, not yet approved for verified in-app installation. The app must fetch/pin exact revisions, record exact byte sizes and SHA-256 for all three files per pack, then verify each downloaded file before activation.

### Language choices and truthful coverage

| Category | Candidate 1 | Candidate 2 | What we can claim today |
|---|---|---|---|
| English transcription | Existing Zipformer English model (already integrated) | Whisper multilingual tiny (~99 MB; not integrated) | Only the existing Zipformer is currently wired into the app |
| Hindi transcription | Whisper small-hi (~358 MB; not integrated) | Whisper multilingual tiny (~99 MB; not integrated) | Candidate models found; no app support claim until Whisper engine and tests exist |
| Telugu transcription | Whisper tiny-te (~99 MB; not integrated) | Whisper multilingual tiny (~99 MB; not integrated) | Candidate models found; multilingual quality for Telugu needs evaluation |
| Mixed Hindi-English / Hinglish | Whisper multilingual tiny (~99 MB; not integrated) | No second reviewed pack proven to support code-switching in this collection | Do not promise two supported Hinglish models yet; benchmark code-switched speech first |
| Telugu-English mixed speech | Whisper tiny-te (~99 MB; not integrated) | Whisper multilingual tiny (~99 MB; not integrated) | Candidates only; code-switch quality is unverified |

The multilingual Whisper model can be a second *candidate* for Hindi/Telugu, but this does not prove parity with a language-specific fine-tune. Do not label an ASR model "supported" solely because its model card lists the language.

## Integration gate before adding in-app Download buttons

1. Pin a repository commit/revision for each artifact; do not rely on mutable `main` URLs in production.
2. Record the exact bytes and SHA-256 for every file in a pack.
3. Add a generic per-file HTTPS downloader with bounded redirects, size caps, partial-file cleanup, checksum verification, and enough free-space checks.
4. Implement sherpa-onnx Whisper separately from the current Zipformer engine; choose the Whisper language code explicitly and keep English Zipformer as fallback.
5. Add unit tests for catalog integrity metadata and download failure cases, plus a device smoke test that loads each model and transcribes a known short sample.
6. Show download size, estimated working storage, languages/task, runtime, and compatibility as distinct fields. "RAM/storage eligible" is only a heuristic, not proof of runtime compatibility.
7. Keep all model downloads opt-in. Do not bundle multi-hundred-megabyte models into the APK or send audio/transcripts to a server.

## Research sources

- LiteRT-LM Qwen3-0.6B INT4 artifacts, file sizes and hashes: https://huggingface.co/litert-community/Qwen3-0.6B-int4
- Qwen3-0.6B LiteRT-LM variants and runtime notes: https://huggingface.co/litert-community/Qwen3-0.6B
- Sherpa-ONNX Whisper pack inventory and Android configuration example: https://huggingface.co/ippocode/indic-asr-onnx
- Sherpa-ONNX Kotlin Whisper config: https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/OfflineRecognizer.kt
