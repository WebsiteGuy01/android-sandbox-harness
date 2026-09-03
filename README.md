# Android Sandbox Harness

This repository contains a non-privileged host application and a minimal test-plugin APK. The host loads the plugin artifact from its own private `filesDir`, links the `sandbox_native` JNI library through CMake, and runs the diagnostic pipeline from an IO coroutine.

## Layout

| Path | Role |
|---|---|
| `host/` | Android host application, Kotlin sandbox classes, JNI bridge, and CMake library. |
| `test-plugin/` | Minimal APK containing `com.example.testplugin.PluginEntryPoint`. |
| `host/src/main/assets/` | Generated staging destination for `test_plugin.apk`. |

## Build

Use Android Studio with Android Gradle Plugin `8.5.2`, Kotlin `2.0.21`, compile SDK 35, NDK, and CMake 3.22.1 installed. From the project root, use either Android Studio’s Gradle sync/build actions or a project Gradle wrapper configured by the IDE.

```bash
./gradlew :test-plugin:assembleDebug :host:assembleDebug
```

The host’s `stageTestPlugin` task builds `test-plugin:assembleDebug`, copies its APK to `host/src/main/assets/test_plugin.apk`, and is wired into `host:preBuild`. The resulting host APK is under `host/build/outputs/apk/debug/`.

The current sandbox environment does not include a Gradle executable or Android SDK, so configuration was inspected structurally rather than compiled here. Compilation should be performed in Android Studio or on a machine with the requested SDK, NDK, CMake, and Gradle toolchain.

## Hardware test

Install the host APK on the Android 10 device and launch it. `MainActivity` invokes `SandboxTestRunner` on `Dispatchers.IO`. The expected report conditions are:

```kotlin
report.validPathAccepted
report.outOfBoundsRejected
report.symlinkRejected
```

Each should be `true`. The native mapper returns `-1` for a missing or inaccessible out-of-bounds target as well as for a canonical path outside the private sandbox. The symlink test is only meaningful when the device permits creation of a symlink inside the host’s private directory; if symlink creation is unavailable, the report records rejection without claiming that the symlink branch was exercised.

## Security boundary

The native implementation uses `realpath()` before validation and then opens only a canonical descendant of the configured sandbox root. It does not access `/data/system`, change SELinux policy, hook ART, grant permissions, or impersonate system authentication services. The lifecycle and plugin code likewise use ordinary application APIs only.
