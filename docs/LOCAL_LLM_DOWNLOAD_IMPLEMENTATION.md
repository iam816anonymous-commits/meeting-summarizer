# Local LLM download implementation

## Scope of this phase

The Languages & models screen exposes two pinned `.litertlm` artifacts from the LiteRT Community Qwen3-0.6B INT4 repository. Downloads are explicit opt-in, use HTTPS, follow only a bounded set of approved HTTPS redirects, enforce exact byte count and SHA-256, and write to an internal app-private directory. The app offers a background-thread CPU load/generation smoke test after download.

## Catalog choices

- Direct-answer/no-think variant: 347,251,840 bytes; SHA-256 `2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139`.
- Reasoning-template variant: 347,251,840 bytes; SHA-256 `312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715`.
- Repository revision: `6aa2daf`; license: Apache-2.0.
- These are variants of the same Qwen3-0.6B base weights, not two independent model families. They are published for English and Chinese; do not promise broad Indic-language quality.

## Runtime and device safety

- The app uses its existing pinned LiteRT-LM Android dependency (0.16.1) and artifact revision documented for the 0.16.0-era runtime. The in-app smoke test is the compatibility gate for this exact app/runtime/device combination.
- Model initialization and generation execute on a worker thread, never the UI thread.
- The downloader validates size before loading, and only finalizes a model after checksum verification. Partial files are removed after failure.
- Storage and available-RAM checks are conservative filters, not a guarantee. A model can still fail to initialize or be too slow on older hardware.
- The target device is a 4 GB RAM Android phone. Test one model at a time on that handset, ideally with other apps closed; capture peak memory, latency, thermal behaviour, and process death before claiming support.
- The existing deterministic summarizer remains the reliable fallback. This phase provides a real model download and inference smoke test; it does not yet route saved meeting summaries through the LLM automatically.
- English transcription is unchanged.

## Follow-up release gates

1. Run Android CI and verify a physical-device download, checksum, load, and generation cycle.
2. Add a managed inference service with a single-thread owner and explicit close/cancel lifecycle.
3. Integrate LLM summarization behind a user-selectable experimental option; validate output with the existing output validator and preserve deterministic fallback.
4. Only add genuinely distinct model families or language-specific speech packs after pinning exact revisions, hashes, licenses, runtime requirements, and physical-device tests.