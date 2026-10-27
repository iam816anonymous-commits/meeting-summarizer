# Android API-Level Compatibility Matrix

This is a planning matrix, not a declaration that runtime behavior has already been tested. Confirm current target/compile SDK policy and dependencies during Phase 1.

| Platform area | Constraint to handle | Required validation |
|---|---|---|
| Microphone permission | Runtime permission is required; denial must not crash or start capture | Grant, deny, revoke and retry |
| Foreground service | Microphone capture while backgrounded must use a correctly declared service and visible notification | Start from visible activity; lock screen; stop from notification |
| Android 12 / API 31+ | Background foreground-service starts are restricted | Verify start path is user initiated and app is visible |
| Android 13 / API 33+ | Notification permission behavior affects notification visibility | Test granted/denied states and explain recording status |
| Android 14 / API 34+ | Foreground-service type and type-specific permission requirements apply | Manifest and runtime tests on API 34+ |
| Android 15 / API 35+ | Additional foreground-service limits and platform behavior may apply | Test target/compile SDK combination and real device behavior |
| Storage | Scoped storage favors app-private files; avoid broad storage access | Create, recover, export and delete app-owned files |
| Database | Schema evolution must preserve user data | Upgrade/migration tests from each supported schema |
| Audio input | Device routing, Bluetooth and microphone selection vary | Test built-in mic and common route changes |
| Offline speech | Model availability and supported languages vary by engine | Airplane-mode cold start and sustained recording |
| Battery/thermal | Long-running capture/inference may be throttled | 15/30/60-minute runs and interruption recovery |

## Policy
Choose and document a minimum SDK based on user reach and supported dependency requirements, not guesswork. Compile/target SDK must satisfy current Play distribution rules at release time. Re-check official Android documentation and foreground-service requirements before implementation and before release.

## References
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/changes
- https://developer.android.com/reference/android/speech/SpeechRecognizer
