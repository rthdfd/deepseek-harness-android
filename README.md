# DSH Android

Unofficial Android client for DeepSeek Harness.

## Build

The repository builds with Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, and JDK 17. The workflow in `.github/workflows/android.yml` runs `lint assembleDebug assembleRelease` and uploads the APK artifacts on every push to `main`.

## Runtime

The APK manages a local Termux backend and opens the DSH web UI from `http://127.0.0.1:3080`.

First run requires Termux, its `RUN_COMMAND` permission for this app, and the `allow-external-apps` property.

## License

MIT. See NOTICE.md for upstream attribution.
