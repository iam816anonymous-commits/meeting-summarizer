# Phase 1 Audit — Android Foundation and CI

**Phase:** 1  
**Scope:** establish an Android app project and automated validation.  
**Explicit exclusions:** no microphone capture, foreground recording service, transcription engine, database schema, summary logic, personal-relevance engine, LLM, cloud API, or meeting workflow.

## Deliverables in this batch
- [x] Android Gradle project configuration with pinned plugin/language versions.
- [x] Native Android application module and minimal launch activity.
- [x] GitHub Actions workflow for push, pull request and manual dispatch.
- [x] CI runs debug APK assembly and Android lint.
- [x] CI uploads the generated debug APK as an artifact.
- [x] Build notes and phase-scope documentation.
- [ ] Successful remote CI run and artifact download verified.
- [ ] Physical-device install/launch smoke test.
- [ ] Gradle wrapper JAR and scripts committed after obtaining a verified official wrapper artifact.

## Toolchain
- Android Gradle Plugin: 9.3.1
- Gradle: 9.5.0
- Kotlin: Android Gradle Plugin built-in Kotlin support (no separate Kotlin Android Gradle plugin)
- JDK: 17
- Compile/target SDK: 36
- Minimum SDK: 26

The project uses AGP's built-in Kotlin support. Do not apply `org.jetbrains.kotlin.android` alongside AGP 9+, because AGP reports that plugin as incompatible/redundant. Kotlin JVM bytecode target is explicitly set to 17.

## Validation
1. `gradle --no-daemon --stacktrace :app:testDebugUnitTest`
2. `gradle --no-daemon --stacktrace :app:assembleDebug`
3. `gradle --no-daemon --stacktrace :app:lintDebug`
4. Confirm `app/build/outputs/apk/debug/app-debug.apk` exists and is non-empty.
5. Upload that APK as a workflow artifact.
6. After CI succeeds, install and launch on an emulator or physical Android device before declaring the phase complete.

## Status
Project files and CI workflow are being published as coherent commits. Until the workflow runs, build/lint status is **not yet verified**. This environment does not have Gradle or an Android SDK installed, so no local Android build is claimed.
