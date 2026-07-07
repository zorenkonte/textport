# Textport

A tiny, native Android app that reads the **SMS messages stored on your device**
and exports them to a file you save yourself. It's a personal backup tool meant
to be **sideloaded** — see the [Google Play caveat](#google-play-caveat) below.

Textport never uploads anything. Messages are read from the system SMS provider,
formatted on-device, and written to a location **you** pick via the Android file
picker. There is no network code.

## Features

- Read SMS from the system content provider (`Telephony.Sms`) with the
  `READ_SMS` runtime permission, off the main thread.
- Preview messages in a scrollable list (address, direction, timestamp, body).
- Export to **JSON**, **CSV**, or **HTML**:
  - **JSON** — an object with `exported_at`, `count`, and a `messages` array;
    ISO-8601 timestamps.
  - **CSV** — RFC-4180 quoting, header row, `body` column last.
  - **HTML** — a single self-contained, chat-style page grouped by thread,
    with light/dark support via `prefers-color-scheme`.
- Saves through the Storage Access Framework, so **no storage permission** is
  needed and you choose the destination. Suggested filename:
  `textport-YYYY-MM-DD.<ext>`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- MVVM: `ViewModel` + `StateFlow`, Kotlin coroutines
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)
- `compileSdk`/`targetSdk` 35, `minSdk` 26, JDK 17

## Build & install

The Gradle wrapper is committed, so a JDK 17 and the Android SDK are all you need.

```bash
# Debug build (auto-signed with the Android debug key, directly installable)
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run the unit tests (export formatters)
./gradlew testDebugUnitTest

# Unsigned release build
./gradlew assembleRelease
```

You can also open the project in Android Studio and Run it on a device or
emulator.

### Prebuilt APKs (GitHub Actions)

The [`Release APK`](.github/workflows/release.yml) workflow builds a signed,
installable APK. Push a version tag (e.g. `git tag v1.0 && git push --tags`) to
publish it as a GitHub Release asset, or trigger the workflow manually from the
**Actions** tab to download it as a build artifact.

By default the workflow signs with a fresh self-signed key each run — installable,
but you must uninstall a previous build before installing a newer one. To sign
with a stable key (so releases upgrade in place), set these repository secrets:
`KEYSTORE_BASE64` (base64 of your `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`.

### Granting the permission

On first load, Textport requests the `READ_SMS` permission. If you deny it (or
disable it later in **Settings → Apps → Textport → Permissions**), the app shows
a clear message and reads nothing. Grant it and tap **Load messages** again.

## Scope

**SMS only.** Textport reads plain text messages from the SMS provider. It does
**not** read:

- **MMS** (picture/group messages) — different provider and structure.
- **RCS** (Google Messages "chat features") — not exposed to third-party apps.
- **iMessage / other chat apps** — not on Android.

**iOS is intentionally out of scope:** Apple provides no API for apps to read the
Messages store, so an equivalent iOS app isn't possible.

## Google Play caveat

Google Play restricts the `READ_SMS` permission to apps that are the device's
**default SMS handler**. Textport is a read-only backup utility, not an SMS app,
so it is **not intended for Play distribution** — install it by sideloading the
APK (see [Build & install](#build--install)).

## License

This project's code is released under the MIT License. See the existing
[`LICENSE`](LICENSE) file for the full text.
