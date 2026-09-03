# Releases and Build History

Successful builds are published automatically as GitHub Releases by the `Android Build` workflow. Each release is tagged `build-<run-number>` and includes `app-debug.apk` and `test-plugin-debug.apk` as downloadable assets. Pull-request builds compile and upload CI artifacts but do not publish releases.

## Build history

| Run | Status | Commit | Change or result |
|---:|---|---|---|
| [33717440021](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33717440021) | Failed | `4237a86` | Initial wrapper was incomplete; Gradle wrapper initialization failed. |
| [33719074241](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33719074241) | Failed | `3e1aab2` | Wrapper regeneration exposed Java/Kotlin JVM-target mismatch. |
| [33719674107](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33719674107) | Failed | `ea7a93b` | Host source compatibility errors were detected. |
| [33722192423](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33722192423) | Passed | `66ebbdd` | Restored sandbox source layout and fixed host Kotlin compatibility. |
| [33723825191](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33723825191) | Passed | `d2ecd7e` | Added on-device crash diagnostic screen. |
| [33725861120](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33725861120) | Failed | `7b54769` | Plugin UI string-template invocation error. |
| [33731864552](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33731864552) | Passed | `818d03f` | Fixed reflective lifecycle methods to return `Unit`. |
| [33733878086](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33733878086) | Passed | `80d208b` | Added plugin resource inflation and XML layout support. |
| [33736673475](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33736673475) | Passed | `0d6a001` | Aligned `createView(Activity, Context)` reflection signature. |
| [33738501236](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33738501236) | Passed | `e5f6df8` | Added explicit reflection lookup and on-screen UI error reporting. |
| [33739659844](https://github.com/WebsiteGuy01/android-sandbox-harness/actions/runs/33739659844) | Pending at documentation time | `c3408b4` | Added context-bound `LayoutInflater` service handling. |

## Release assets

The GitHub Releases page is the authoritative download location for successful builds. The workflow also uploads short-retention Actions artifacts for CI inspection. Releases are created only after the host and test-plugin APKs have been built and uploaded successfully.
