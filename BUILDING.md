# Building

## Requirements

- JDK 17 (JDK 21 also works)
- Android SDK with platform 34 (Android Studio installs this automatically)
- No other tools: the Gradle wrapper downloads Gradle 8.14.3 itself

## Android Studio

1. *File → Open…* and select the repository root.
2. Let Gradle sync (it downloads the Android Gradle Plugin 8.7.3, Kotlin
   2.0.21 and Compose from Google Maven / Maven Central).
3. Run the `app` configuration on a device or emulator (min SDK 26).

## Command line

```bash
# Debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug

# Install on a connected device
./gradlew :app:installDebug
```

## Tests

```bash
# Engine tests: rules, scoring, undo, shuffle determinism, persistence,
# statistics, keyboard/cursor model (pure JVM, no Android SDK needed)
./gradlew :engine:test

# App unit tests: ViewModel over a fake store (JVM)
./gradlew :app:testDebugUnitTest

# Compose UI tests (need a device or emulator)
./gradlew :app:connectedDebugAndroidTest
```

The engine module has no Android dependencies, so in restricted
environments without an Android SDK you can still build and test all game
logic:

```bash
./gradlew :engine:test --configure-on-demand
```

## Continuous integration

`.github/workflows/build.yml` runs the engine tests, the app unit tests
and assembles the debug APK on every push; the APK is attached to the
workflow run as the `solitaire-debug-apk` artifact and also published to
the rolling `latest` release, giving a stable direct download link that
needs no GitHub login (handy on a phone):

```
https://github.com/Jackque602/iPod-Solitare-for-Android/releases/latest/download/app-debug.apk
```

The Compose UI test job runs on an emulator when the workflow is started
manually (*Actions → Build & Test → Run workflow*).

## Signing

Debug builds are signed with the committed shared keystore at
`signing/debug.keystore` (passwords: `android`). This keeps the
signature identical across every machine and CI run so a newer debug
APK always installs over an older one. The key is deliberately public
and protects nothing; for a Play Store release, create a private
keystore and a proper `release` signing config instead.

## Reproducible deals

Shuffles are driven by a self-contained SplitMix64 PRNG inside the engine
(no platform randomness), so a given seed deals the identical game on
every device and app version. `GoldenDealTest` pins seed 1's exact layout
to guarantee this stays true.
