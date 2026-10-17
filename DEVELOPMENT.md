# Development Notes

Design decisions, research findings and on-device test results for Dream Nail.

## Overview

Dream Nail is an early-stage Android project. Currently planned features:

1. **Sleep timer**: fade out and pause media playback from other apps after a set time
2. **Mono audio toggle**: quickly switch the system's mono audio setting
3. **Wake-up alarm based on actual sleep onset**

Package name: `io.github.okenaron.dreamnail`

## Build

- Kotlin + Jetpack Compose
- `compileSdk` / `minSdk` / `targetSdk` = 37 (Android 17). The app is only tested on a Pixel 9a running Android 17, so older versions are not supported for now.
- The Gradle daemon requires **JDK 25** (`gradle/gradle-daemon-jvm.properties`). Either let Gradle download one automatically, or point it to a local JDK 25 in `~/.gradle/gradle.properties`, e.g. the JBR bundled with Android Studio:
  ```properties
  org.gradle.java.installations.paths=/opt/android-studio/jbr
  ```
- Build and install:
  ```bash
  ./gradlew assembleDebug
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

## Workflow

- One feature at a time. Each feature is developed on a `feature/<name>` branch and merged into `main` after it passes testing on a real device.
- Build a working skeleton first, then add features incrementally.
- Decisions and their background are recorded in this file.

## Feature 1: Sleep timer

**Goal:** shortly before the timer ends, gradually lower the media volume, then pause playback in other apps (YouTube, Spotify, etc.), and finally restore the original volume.

### Decisions (2026-10-17)

- **A foreground service started from the app screen**, kept running with an ongoing notification until the timer ends. The reason is the Android 17 restriction described below. An ongoing notification is acceptable (a commercial sleep timer app that fades out correctly behaves the same way).
- Foreground service type `specialUse` (the app is sideloaded, not distributed via Google Play). Manifest permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`.
- Fade-out **starts 30 seconds before the end** and reaches 0 exactly at the end → send `KEYCODE_MEDIA_PAUSE` → restore the original volume after a short delay.
- Duration is chosen with a **slider (or a dial), not preset buttons**.
- The notification permission is requested *before* starting the service. Otherwise the foreground notification is posted before the permission is granted and never shows up.
- No exact alarm (`SCHEDULE_EXACT_ALARM`) in the first version: timing is done with a coroutine inside the service. Add an exact alarm or a wake lock if timing turns out to be inaccurate with the screen off.

### Test results (2026-10-17, Pixel 9a / Android 17)

- `setStreamVolume` took effect at every step (the value read back matched); no `AudioHardening` log entries.
- A 1-minute timer (including with the screen off) paused on time and restored the volume.
- Long-run accuracy is still to be verified during normal use (criteria: 60 minutes, media playing, screen off most of the time). Low priority.

### Research: Android 17 background audio hardening

Sources: [SimonMarquis/SleepTimer](https://github.com/SimonMarquis/SleepTimer) at commit `d5ec295`, and the Android 17 documentation.

- **How SleepTimer works:** no service runs during the countdown, only a notification with a countdown. When the time is up, `setExactAndAllowWhileIdle` starts a `shortService` foreground service, which lowers the volume one step per second with `adjustStreamVolume(ADJUST_LOWER)` down to 0, sends a pause key event, and restores the volume with `setStreamVolume` 2 seconds later. It has no launcher icon, only a Quick Settings tile and a notification (+10 / -10 / cancel), and asks for the "Alarms & reminders" (`SCHEDULE_EXACT_ALARM`) permission.
- **[Android 17 Background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio):** volume changes from the background (`setStreamVolume`, `adjustStreamVolume`, ...) are **silently ignored** unless the app has a visible activity or is running a foreground service that is **not** of type `SHORT_SERVICE`. Apps targeting API 37 additionally need that foreground service to have while-in-use (WIU) capability. Foreground services started from the background usually do not get WIU; exceptions include notification clicks, widget interactions and media key events from external devices. The page does not say whether `dispatchMediaKeyEvent` is restricted.
- According to the documentation, SleepTimer's approach (a `shortService` started by an alarm in the background) should not be able to change the volume on Android 17, which contradicts its README. **However, on a Pixel 9a with Android 17 it does fade out correctly** (volume steps down after the timer ends, then playback pauses). The restriction does not seem to be fully enforced on this device yet (root cause not investigated). Dream Nail follows the documented requirements anyway, so it should keep working if the restriction is tightened.
- Debugging: `adb logcat | grep AudioHardening`, `adb shell dumpsys audio`

## Feature 2: Mono audio toggle (planned)

- Mono audio is a system setting (`master_mono` in `Settings.System`). Regular apps cannot write it by default.
- Plan: grant the permission once from a computer via adb, after which the app can toggle it directly:
  ```bash
  adb shell pm grant io.github.okenaron.dreamnail android.permission.WRITE_SECURE_SETTINGS
  ```
  - The permission must be declared in the manifest, or the grant fails.
  - The grant is lost on uninstall (an update install keeps it).
  - Shizuku could remove the need for a computer (to be decided).
- Provide a Quick Settings tile (`TileService`) to toggle it from the notification shade.
- Fallback: without the permission, the tile opens the system accessibility settings.
- To verify first: whether writing `master_mono` takes effect immediately.

## Feature 3: Wake-up alarm based on actual sleep onset (requirements changed 2026-10-17, design open)

- **Purpose: avoid oversleeping.** Reliability matters more than precision.
- The alarm should be based on when sleep **actually** starts, not an estimate made before going to bed. This may require phone usage data or wearable data (candidate wearable: Pixel Watch / Wear OS).
- The original plan (bedtime + time to fall asleep + N × 90-minute cycles, handed to the system clock app via `AlarmClock.ACTION_SET_ALARM`) is no longer enough:
  - The user cannot interact at sleep onset, so the app must detect it and set the alarm in the background. Background activity launches are restricted, so `ACTION_SET_ALARM` will likely not work, and the app may need its own alarm (`AlarmManager.setAlarmClock` + a full-screen notification).
  - A detection failure must never cause oversleeping, so a fallback is required (e.g. a fixed latest time that always rings).
- Candidate data sources (none verified yet): phone usage (`UsageStatsManager`, last screen-off / lock time), Google Sleep API (current status unknown), Health Connect (Pixel Watch / Fitbit sleep sessions, possibly written only after waking up, so better for calibration afterwards), a Wear OS companion app (most accurate, most work).
- Initial idea: build it in stages. Start with phone usage data plus a fallback alarm, then add watch data to improve accuracy. To be refined after Feature 1.

## Development order (revised 2026-10-17)

1. Feature 1 (sleep timer): moderate difficulty, and it introduces foreground services, notifications and permissions that Feature 3 also needs
2. Feature 2 (mono audio toggle)
3. Feature 3 (sleep-onset alarm): the hardest after the requirements change

## References and licenses

- Open-source projects are used as **references** for ideas (fading, keeping services alive, compatibility). Dream Nail's code is written independently, not copied.
  - MIT / Apache-2.0: permissive; keep the copyright notice if code is reused (Apache-2.0 also requires stating changes).
  - GPL: copyleft; distributing an app containing GPL code requires releasing the whole app under the GPL.
- [SimonMarquis/SleepTimer](https://github.com/SimonMarquis/SleepTimer): **Apache-2.0**. Used as a design reference. If code is ever reused, keep its copyright notice (Copyright 2020 Simon Marquis) and mark the changes.
- A closed-source commercial sleep timer app from Google Play is used only as a reference for features and interaction (custom duration, notification controls, fade-out before stopping). It is not decompiled and none of its code or assets are used.
- Exact alarms: `setExactAndAllowWhileIdle` requires `SCHEDULE_EXACT_ALARM` on Android 14+, which is not granted by default to newly installed apps; the user has to enable it in system settings.

## Open questions

- Mono audio permission: adb grant or Shizuku
