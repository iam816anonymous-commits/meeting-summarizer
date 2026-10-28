# Model download responsiveness follow-up

## Problem
The English model downloader reports progress for every 64 KiB read, while the Activity rebuilt its entire view hierarchy for every callback. This can starve the main thread and make the screen appear frozen. Connecting, extraction and verification stages also lacked consistent loading feedback.

## Changes
- Throttle English model UI redraws to changes in the visible percentage.
- Show indeterminate progress during connecting, installation and verification.
- Show stage and percentage for English, multilingual Whisper and optional local LLM model downloads.
- Keep network transfer and extraction on worker threads.
- Preserve explicit download failures and model integrity verification.

## Validation
CI must pass unit tests, APK assembly, Android lint, APK existence and artifact upload. Physical-device tests must confirm network transfer, visible progress, successful install, failure recovery and offline transcription. A successful build alone does not prove the user's network can reach the model host.
