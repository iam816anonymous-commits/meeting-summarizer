# Phase 8 Research — On-device LLMs and the Best Next Direction

**Status:** Research only. No LLM dependency, model download, or runtime has been integrated.  
**Research date:** 2026-10-28  
**Decision:** Do not enable an LLM by default yet. First establish a transcript-quality baseline and device budget, then run a small, opt-in prototype behind the deterministic summarizer.

## Executive decision

Phase 8 is technically feasible, but it should not be treated as a fix for incorrect speech recognition. The current Zipformer model is an English LibriSpeech model, and the current implementation decodes each 30-second window in a fresh recognizer stream. This can produce plausible but incorrect words, particularly for Indian English accents, names, technical vocabulary, overlapping speakers, or speech whose context crosses a window boundary. A language model that summarizes this transcript can make the prose smoother while preserving or amplifying the underlying error.

Recommended order:

1. **Phase 4.5 — ASR evaluation and decoding audit:** compare the saved transcript with a known, manually verified reference transcript; calculate word error rate (WER); inspect audio conversion, window boundaries and timestamps.
2. **Phase 8A — LLM feasibility benchmark:** choose a candidate based on actual device RAM, Android version, free storage and acceptable delay; measure model-load time, peak memory, time to first token, full-summary latency, heat and battery.
3. **Phase 8B — Optional prototype:** add a disabled-by-default local LLM adapter, with deterministic findings preserved as the trusted baseline and evidence validation applied to every generated claim.
4. Only consider enabling it by default after quality, latency, reliability and privacy gates pass.

## Findings from the current repository

- Android app uses Kotlin, minSdk 26, Java/Kotlin 17, and sherpa-onnx v1.13.8.
- The installed ASR is sherpa-onnx-zipformer-small-en-2023-06-26, INT8. The upstream model documentation says it was trained on LibriSpeech and supports English.
- SherpaOnnxEnglishSpeechEngine creates one recognizer for a file but creates a new inference stream for every decoded chunk. No confidence score is persisted or shown.
- AndroidAudioDecoder decodes audio to 16 kHz mono and emits 30-second chunks. This pipeline needs its own tests before changing the model.
- DeterministicMeetingSummarizer extracts rule-matched findings from transcript segments and attaches evidence (session, sequence, timestamps and quote). This is a valuable fallback and must not be replaced by unverified generated prose.
- The repository's plan correctly requires target-device benchmarks, structured output validation, cancellation/time budgets, segment citations and deterministic fallback.

## Runtime and model options

### Option A — LiteRT-LM (preferred first prototype, subject to compatibility)

Google's current LiteRT-LM Kotlin API supports on-device text generation and documents Android CPU/GPU backends, conversation APIs, and structured/tool-oriented workflows. Model files are stored separately on-device, not bundled in the APK. This aligns with an offline-first app and avoids introducing a server.

**Initial candidate to benchmark:** litert-community/Qwen3-0.6B (Qwen3-0.6B.litertlm), which Google's Android development guidance lists as a small starting candidate. It is a candidate, not a recommendation to ship: verify the current model card, license, exact file size, context length, tokenizer/runtime compatibility and performance before downloading or coding against it.

**Important memory warning from the current model card:** the 586 MB INT8 build is documented with peak private memory around 2.7 GB on CPU and 4.3 GB on GPU on a listed TECNO LJ9 test device; those are not the user's confirmed phone and do not predict its result, but they show why file size alone is misleading. Do not assume the 0.6B INT8 build is safe on a 4 GB phone. The model card also lists smaller INT4 variants (about 329 MB or 475 MiB), but their actual peak memory, output quality and compatibility still need measurement. If the target phone has only 4 GB RAM, the go/no-go bar should be especially strict; a deterministic-only release may be preferable to a crash-prone LLM.

**Do not start with Gemma 4 E2B or Gemma 3n E2B/E4B on an unprofiled low-RAM phone.** Google's current published examples include multi-gigabyte model files and flagship-device benchmarks. Those figures do not predict performance on a budget phone. A small model may also struggle with long meeting transcripts; use bounded, evidence-bearing chunks and hierarchical synthesis rather than feeding an entire long meeting into one prompt.

Potential costs: another native/runtime dependency, ABI and GPU compatibility work, hundreds of MB of storage, additional RAM during inference, slow cold starts, and a new failure surface. Initialize off the main thread; serialize engine/conversation ownership; close resources exactly once; support cancellation; never let an LLM block recording or transcript review.

### Option B — Google AI Edge Gallery (manual feasibility check)

The official Gallery can run supported models locally and benchmark them on the actual phone. Its current documented app requirement is Android 12+, so it may not be installable on an Android 8/9 target. If the user's phone is Android 12 or newer, use it to compare candidate models before integrating any SDK. If it is older, use a minimal in-app/standalone LiteRT-LM prototype instead; do not infer compatibility from Gallery support.

### Option C — Cloud summarization (not the next step)

A cloud adapter can improve quality on capable models, but conflicts with the default privacy goal unless users explicitly opt in. It requires clear disclosure of what transcript text is uploaded, redaction, cancellation, timeouts, provider-key handling and a no-network fallback. Keep it as a later optional phase, not a shortcut around local-device evaluation.

### Option D — Improve deterministic extraction first

This is the lowest-risk path for trustworthiness. Expand fixture tests for negation, tentative proposals, speaker/owner ambiguity, deadlines, repeated statements, transcript corrections and false positives. Keep each finding attached to an exact transcript quote and show “candidate / needs review” when the rule cannot prove ownership. It improves reliability without a large model or memory cost, although it will not create fluent narrative summaries.

## Critical parallel track: investigate ASR before summarization

The upstream Zipformer small English model documentation identifies LibriSpeech as its training corpus. That does not guarantee good recognition of meeting-room audio, Indian English, names, code-switching or domain-specific terms. Do not assume the user's audio is bad merely because the model is wrong.

For a short sample, record:
- exact spoken reference text (manually checked);
- actual app transcript;
- recording format and duration;
- whether errors are substitutions, omissions or insertions;
- whether errors cluster near 30-second boundaries;
- whether audio contains multiple speakers, noise or non-English phrases.

Calculate WER = (substitutions + deletions + insertions) / reference word count. Compare the current model on at least 3 representative clips. Then compare a feasible alternative such as whisper.cpp's tiny.en or base.en on the same clips. The upstream whisper.cpp project supports Android and quantized models, but its documented approximate memory figures (tiny ~273 MB, base ~388 MB, small ~852 MB) are not total app RAM and are not device-specific guarantees. small is likely too costly on a constrained phone; start with tiny/base only if device measurements permit it. Compare accuracy and speed rather than replacing sherpa-onnx on intuition.

Also audit resampling/downmixing and boundary handling. Do not add overlap or context heuristics blindly; verify with a reproducible fixture and timestamp tests first.

## Phase 8 architecture proposal

Keep the current deterministic pipeline as the baseline:

1. Load corrected, final transcript segments from local storage.
2. Select a bounded group of segments; preserve stable segment IDs and timestamps.
3. Ask the optional model to return a strict schema: summary bullets and candidate action items, each citing one or more supplied segment IDs. Treat model output as untrusted input.
4. Validate schema, lengths, IDs, quotes and references. Reject unknown IDs, unsupported dates/owners, malformed output, and empty/oversized responses.
5. Display generated narrative separately from deterministic evidence-linked findings. Never overwrite the transcript or deterministic findings.
6. On model disabled/missing, out-of-memory, timeout, cancellation or validation failure, show the deterministic result without blocking access to the meeting.
7. Add explicit “Generate AI summary” control and a clear local-processing indicator. No network calls in local-only mode.
8. Log only non-sensitive timings and failure categories; never log transcript text or audio.

## Go/no-go gates

Do not merge model integration until these are answered:

- [ ] Exact target phone model, Android version/API level, RAM and available storage.
- [ ] At least 3 reference audio clips with corrected transcripts and measured WER for current ASR.
- [ ] Candidate model's current license, file size, context limit and supported runtime verified from its model card.
- [ ] Cold-start time, end-to-end latency, peak app memory, thermal behavior and battery measured on the target phone.
- [ ] Long transcript is processed in bounded chunks; no unbounded prompt/context growth.
- [ ] Output schema validation and invalid-evidence rejection have unit tests.
- [ ] Timeout, cancellation, OOM and missing-model paths preserve deterministic results.
- [ ] Offline/network test confirms transcript/audio are not sent anywhere.
- [ ] Quality is compared against deterministic output using a fixed evaluation set, including factuality and unsupported-claim checks.

## Recommended next action

**Proceed with Phase 8 research and evaluation scaffolding, not full LLM integration yet.** First get one representative test recording and its exact spoken text from the user. In parallel, prepare a small ASR comparison/evaluation set and verify the target device. Once we have those facts, select the smallest viable runtime/model and implement the optional adapter behind a feature flag. If the immediate priority is “the transcript must match what was said,” prioritize the ASR audit before spending time on a summarization model.

## Sources

- LiteRT-LM Android setup: https://developers.google.com/edge/litert-lm/android
- LiteRT-LM API overview and runtime/model guidance: https://developers.google.com/edge/litert-lm/api_overview
- Google's Android on-device LLM development guidance and candidate model files: https://developer.android.com/agents/skills/device-ai/litert/litert-lm/references/get-a-model
- Google AI Edge Gallery and Android requirements: https://github.com/google-ai-edge/gallery
- sherpa-onnx Zipformer small English model and training-corpus note: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html
- whisper.cpp Android support, quantization and approximate model memory figures: https://github.com/ggml-org/whisper.cpp
- LiteRT-LM performance table (flagship devices only; not a proxy for this target phone): https://developers.google.com/edge/litert-lm/overview

**Evidence caveat:** Published model sizes and flagship benchmarks are only screening inputs. They do not establish performance, compatibility or accuracy on the user's phone. Physical-device measurements remain mandatory.
