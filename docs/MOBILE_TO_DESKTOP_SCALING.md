# Mobile-to-Desktop Scaling

## Principle
Desktop support is a future platform adapter, not a reason to complicate the Android MVP. Keep domain logic and storage contracts independent of Android UI and microphone APIs where practical.

## Layer boundaries
- **Domain:** meeting, transcript, evidence, task, summary and state-machine models.
- **Application:** use cases and orchestration.
- **Ports/interfaces:** audio source, speech engine, local model, persistence, export and optional remote inference.
- **Android adapters:** microphone foreground service, notification, Room database, Android file protection, Compose UI.
- **Future desktop adapters:** OS-specific microphone capture, filesystem/database and desktop UI.

## Portability requirements
- Do not reference Android classes in pure domain models.
- Keep timestamps and IDs platform-neutral.
- Version persisted schemas and define migrations.
- Model providers must be injected through interfaces rather than imported into domain logic.
- No platform-neutral abstraction may hide platform permission or recording-state behavior.

## Desktop is not in initial scope
Do not add desktop code, sync, server deployment, multi-device accounts or cross-platform UI in Phases 0–11 unless a scope change is explicitly approved. The mobile MVP remains local-first and usable without a desktop.
