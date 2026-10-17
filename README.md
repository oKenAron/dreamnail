# Dream Nail

An early-stage Android project.

## Features

- **Sleep timer**: lowers the media volume over the last 30 seconds, pauses playback in other apps, then restores the original volume. Working skeleton.
- **Mono audio toggle**: planned
- **Sleep-onset based alarm**: planned

## Build

Requirements:

- Android 17 (API 37) device
- JDK 25 for the Gradle daemon (see [DEVELOPMENT.md](DEVELOPMENT.md#build))

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Notes

Android 17 silently ignores volume changes from apps in the background unless they run a suitable foreground service ([Background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)).
The sleep timer therefore starts its foreground service while the app is visible and keeps it running until the timer ends.
Details, research and test results are in [DEVELOPMENT.md](DEVELOPMENT.md).

## Development

Developed with [Claude Code](https://claude.com/claude-code) (AI coding assistant). Commits written with it are marked `Co-Authored-By: Claude`.

## License

[MIT](LICENSE)
