# Android Sandbox Harness

A modular, non-root Android application virtualization harness for compatibility research and controlled plugin testing. The project runs entirely in ordinary Android application userspace and is designed to load a secondary APK on an Android 10 (API 29) host while supporting guests that target API 30 or newer.

> This project is an educational test harness, not a replacement Android runtime. It does not provide root access, alter SELinux policy, bypass Android permissions, hook ART, or execute code with platform privileges.

## Overview

The harness separates a host application from a reusable `:sandbox-core` Android library. The host stages a guest APK in its private storage, verifies the guest signing certificate, constructs an isolated class-loading context, merges the guest resources for XML inflation, and delegates selected UI and lifecycle operations through ordinary Android APIs.

The current repository includes three Gradle modules:

| Module or path | Purpose |
|---|---|
| `:sandbox-core` | Reusable context, class-loading, resource, lifecycle, routing, compatibility, verification, and diagnostic components. |
| `:host` | Android application that owns the process, registers the proxy Activity, stages the test APK, and presents guest UI. |
| `:test-plugin` | Minimal guest APK implementing the `SandboxPlugin` contract and demonstrating resource inflation and secondary-screen routing. |
| `host/src/main/cpp/` | JNI and CMake implementation of the traversal-safe private-storage path mapper. |
| `.github/workflows/android.yml` | Automated Android build, artifact upload, checksum verification, and release workflow. |

## Core architecture

The implementation is organized around four engineering pillars. Each pillar operates within Android’s normal application-level boundaries and has an explicit compatibility or isolation purpose.

### 1. Bytecode isolation

The host stages the guest APK and uses a `DexClassLoader` to resolve guest classes from the staged artifact. `PluginContext` owns the guest class loader and associates it with the guest package name and private sandbox directory. The host loads only the declared entry point after the APK has passed signature verification.

This is class-loader isolation rather than a separate operating-system process. Guest code therefore remains subject to the host process’s permissions and lifecycle. It must not be treated as a security boundary against hostile code; the design is intended for trusted, allowlisted compatibility-test plugins.

### 2. Resource merging

`PluginContext` exposes guest resources through a reflected `AssetManager` containing the guest APK’s asset path. The context also proxies `LayoutInflater` requests by cloning the host inflater into the plugin context. Consequently, a guest can inflate its own `R.layout` and drawable or string resources instead of accidentally resolving identifiers against the host application’s resource table.

Resource loading is designed for compatibility testing. It does not modify the system resource table and does not grant the guest access to resources outside the host process’s ordinary application APIs.

### 3. UI multiplexing

Guest activities are not independently registered in the host manifest. To route a guest intent without requiring dynamic manifest entries, `PluginContext.startActivity()` rewrites the destination to the manifest-registered `ProxyActivity` and stores the requested guest class name in an extra such as `TARGET_GUEST_ACTIVITY`.

`ProxyActivity` acts as a controlled shell. It reads the routed target, resolves the guest class through the isolated loader, and delegates the supported UI lifecycle to the guest component. This is component multiplexing through one host-owned Activity; it does not bypass Android’s ActivityManager or manifest enforcement.

### 4. Service proxying and API compatibility

`PluginContext.getSystemService()` intercepts services that require special handling. The layout-inflater service is rebound to the plugin context, the window service provides a foundation for API 30+ metrics adaptation, and unsupported services are requested through guarded calls that degrade to `null` rather than crashing the host.

Concrete adapters such as `WindowMetricsAdapter` translate modern window queries to API 29 display metrics. Back-dispatch compatibility code safely absorbs newer callback-registration patterns when the Android 10 host has no equivalent API. The intended behavior is graceful feature degradation, not emulation of privileged framework services.

## Security gate

Every executable guest APK must pass `PluginVerifier` before the host constructs `PluginContext` or loads a guest class. The verifier asks `PackageManager.getPackageArchiveInfo()` to inspect the uninstalled archive and requests both legacy and modern certificate metadata flags:

```kotlin
val flags = PackageManager.GET_SIGNATURES or
    PackageManager.GET_SIGNING_CERTIFICATES
```

On API 28 and newer, the verifier reads `SigningInfo.apkContentsSigners` for APKs with multiple signers and otherwise reads `SigningInfo.signingCertificateHistory`. If those values are unavailable, it falls back to the legacy `PackageInfo.signatures` array. The first certificate is hashed with SHA-256; the verifier also records all available certificate fingerprints for diagnostics.

The expected certificate fingerprint is supplied by the host build configuration as an explicit allowlist value, embedded in `BuildConfig.PLUGIN_SIGNATURE_SHA256`. The CI pipeline derives that value from the freshly built guest APK with `apksigner` and passes it to the host Gradle build. A production deployment should replace this build-local arrangement with a reviewed, stable fingerprint managed as a trusted release configuration.

Before comparison, both expected and extracted values are normalized by removing colons and spaces and converting to uppercase. An empty allowlist, an APK that cannot be parsed, an unsigned archive, or a certificate mismatch fails closed. A mismatch diagnostic includes the normalized `Expected` and `Got` values so a developer can identify formatting or signing-key differences without inspecting opaque logs.

```text
Signature mismatch.
Expected: AABBCCDDEEFF...
Got:      112233445566...
```

The verifier is an allowlist gate, not cryptographic proof that guest code is benign. The host must still treat plugins as trusted test artifacts and should keep permissions, storage, and network access constrained according to the test objective.

## Integration guide

A guest module depends on `:sandbox-core` and implements `SandboxPlugin`. The interface provides a stable host-plugin contract consisting of a name, protocol version, and view factory:

```kotlin
package com.example.testplugin

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.example.sandbox.core.SandboxPlugin

class PluginEntryPoint : SandboxPlugin {
    override fun getPluginName(): String = "sandbox-test-plugin"

    override fun getProtocolVersion(): Int = 1

    override fun createView(
        hostActivity: Activity,
        pluginContext: Context
    ): View? {
        return LayoutInflater.from(pluginContext)
            .inflate(R.layout.plugin_main, null)
    }
}
```

The guest should use the supplied `pluginContext` when inflating resources or initiating guest intents. A routed intent should identify the guest class name, while the host remains responsible for the manifest-registered `ProxyActivity` and for deciding which guest classes are permitted to run.

A host integration creates the runner with an explicit allowlist. The runner stages the APK, verifies its certificate, and only then resolves the entry point:

```kotlin
val runner = SandboxTestRunner(
    hostContext = applicationContext,
    assetName = "test_plugin.apk",
    pluginPackageName = "com.example.testplugin",
    entryPointClassName = "com.example.testplugin.PluginEntryPoint",
    allowedPluginFingerprints = setOf(BuildConfig.PLUGIN_SIGNATURE_SHA256)
)

val report = withContext(Dispatchers.IO) {
    runner.run()
}
check(report.signatureVerified)
check(report.pluginClassLoaded)
```

The host should run staging, archive inspection, signature verification, and class loading away from the main thread. UI attachment and lifecycle callbacks must return to the Activity’s main thread.

## Private storage and native path validation

Guest file access is mapped beneath the host’s private sandbox directory. The native `sandbox_native` library canonicalizes requested paths with `realpath()` and rejects paths that resolve outside the configured guest root, including traversal attempts and symlink redirects. Validated files are opened only after the canonical containment check succeeds.

This mapper is a userspace containment mechanism for plugin file operations. It does not weaken Android filesystem permissions and cannot grant access to files that the host process could not ordinarily access.

## Automated pipeline

The GitHub Actions workflow in `.github/workflows/android.yml` builds and releases the project on pushes to the main branches and on manual dispatch. The sequence is:

| Stage | Action |
|---|---|
| Environment | Configure Java 17, Android SDK platforms and build tools, the pinned NDK, and CMake. |
| Production fixture | Download the pinned passive-inspection APK and verify its hardcoded SHA-256 checksum before staging it as `production-guest.apk`. |
| Guest build | Compile `:test-plugin:assembleDebug` and copy `test-plugin-debug.apk` into `host/src/main/assets/test_plugin.apk`. |
| Trust configuration | Use `apksigner --print-certs` to derive the test plugin’s SHA-256 certificate fingerprint and export it to the host build. |
| Verification and host build | Run `:sandbox-core:testDebugUnitTest`, assemble `:sandbox-core`, and assemble `:host`. |
| Distribution | Rename the host debug APK to `app-debug.apk`, upload host and plugin APK artifacts, and create a tagged GitHub Release such as `build-21` for successful non-pull-request builds. |

The release step is configured with repository write permission and attaches the generated APKs to the release. Pull requests still compile and test but do not publish releases.

## Local setup and hardware testing

Use Android Studio or a CI-capable machine with Java 17, Android SDK 29 and 35, Android build tools 35.0.0, NDK `26.3.11579264`, CMake 3.22.1, and the project’s Android Gradle Plugin and Kotlin versions. The repository wrapper has historically required regeneration in CI; if the checked-in wrapper is unavailable, use a valid Gradle 8.7 installation or regenerate the wrapper before building.

```bash
# Build the guest APK, library tests, library, and host application.
./gradlew :test-plugin:assembleDebug
./gradlew :sandbox-core:testDebugUnitTest :sandbox-core:assembleDebug :host:assembleDebug
```

Install the generated host APK on an Android 10 device only after building it with the intended plugin fingerprint. Launching the host should display the production APK’s passive manifest diagnostic and the test plugin’s UI. The diagnostic runner should report a verified signature, an isolated class loader, successful resource inflation, and accepted or rejected native path checks as appropriate.

## Scope and limitations

This project deliberately stays within non-privileged Android userspace. It does not attempt root access, platform-security bypasses, permission auto-granting, credential fabrication, system-service impersonation, arbitrary third-party APK execution, or process-level isolation. Third-party APKs in the workflow are used for passive manifest inspection only; the executable guest in the demonstration is the allowlisted `test-plugin` artifact.

The framework is suitable for compatibility research, resource-loading experiments, component-routing demonstrations, and trusted plugin test harnesses. It should not be presented as a hardened hostile-plugin sandbox without adding a separate process boundary, a carefully designed IPC policy, strict resource and permission controls, and a broader security review.
