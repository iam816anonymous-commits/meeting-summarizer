# Phase 10 — Model download responsiveness and diagnostics

## Current phase
Phase 10 (reliability, privacy and performance hardening), specifically the model download UX and diagnostics. This does not claim device acceptance.

## Findings
- English model download runs on a worker thread and the installer exposes byte progress, but the Activity did not consume the byte callback, so users could not distinguish a slow connection from a stalled transfer.
- Download stages were not consistently reflected in the English model card.
- A successful CI build cannot establish that GitHub Releases is reachable from the user's mobile network or that the archive can be downloaded and extracted on their phone.

## Patch
- Display the active connection/download/install/verify stage.
- Show downloaded bytes and total bytes when the server advertises content length; otherwise show downloaded bytes and explicitly indicate that total size is unknown.
- Show a spinner while connecting, installing, or verifying and a determinate progress bar when a percentage is available.
- Update the UI on a throttled cadence from the worker thread, rather than per 64 KiB read.
- Preserve install errors in the existing status message; model verification remains required before an installed state is shown.

## Acceptance checks
1. Tap Download English model: screen stays responsive and shows Connecting.
2. When bytes arrive, the downloaded-byte counter increases.
3. Installation and verification show distinct stages/spinner.
4. A successful install survives app restart and works in Airplane mode.
5. On a blocked network, the app displays a specific HTTP/connection/timeout error and allows retry.
6. Repeat with multilingual Whisper and one optional LLM model; each must show progress and final verification status.

## Limits
The app cannot prove host reachability without testing the user's actual network. Physical-device validation remains pending.