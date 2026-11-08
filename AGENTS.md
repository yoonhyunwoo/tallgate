# AGENTS.md

## Build & Testing

- Run Gradle from the repository root. Verification commands: `./gradlew :app:assembleDebug` and `:app:lintDebug` (no unit tests exist; UI and blocking behavior require on-device testing).
- Do not put machine-specific paths (e.g. `org.gradle.java.home`) in the repo `gradle.properties`; CI uses `setup-java` 25 and the path would break it. Keep them in `~/.gradle/gradle.properties`. CI (`.github/workflows/ci.yml`) runs the same `assembleDebug` + `lintDebug` pair. Toolchain: JDK 25, Gradle 9.6, AGP 9.5 (built-in Kotlin, so no `kotlin-android` plugin); bytecode target stays 17 via `compileOptions`.
- `local.properties` (`sdk.dir`) is machine-specific and gitignored.

## View ID & Detection Logic

- Third-party IDs in `TallGateAccessibilityService` (`IG_ID`, `YT_ID`) originate from device UI dumps (`uiautomator dump`). Never guess or rename them without re-verifying against real builds.
- Instagram handling: The home "For you" tab embeds the Reels viewer while the bottom `tab_bar` remains visible. If `tab_bar` is present, switch to the "Feed" tab; if hidden, treat as a standalone Reels viewer and trigger back navigation.
- YouTube Shorts and TikTok detection: TikTok uses text matching (`"for you"` / `"추천"`) with a depth limit and can produce false positives.

## Accessibility Service Constraints

- Package scope must be declared in both `res/xml/tallgate_accessibility_service.xml` (`android:packageNames`) and `TallGateAccessibilityService.kt` (`PKG_*`). The XML configuration restricts event delivery from other apps.
- `rootFor(event)` ensures operations target only the active window of the triggering package. Maintain this guard in all event handlers.
- Debounce relies on `SystemClock.elapsedRealtime()` (not wall clock). Content-change events fire frequently; removing debounce causes repeated back presses.
- Prominent disclosure: The service requires `BlockPreferences.isDisclosureAccepted == true` to operate. `MainActivity` redirects to `DisclosureActivity` if consent is pending. Disclosure copy must accurately reflect service operations (no storage, no network, no `INTERNET` permission).
- Re-enabling after force-stop:
  ```bash
  adb shell settings put secure enabled_accessibility_services dev.yhw.tallgate/.TallGateAccessibilityService
  adb shell settings put secure accessibility_enabled 1
  ```

## Persisted State

- `tallgate_block_prefs` SharedPreferences and `key_*` names in `BlockPreferences` are persisted on user devices. Do not rename keys without migration.

## Localization

- English (`values/strings.xml`) is default; Korean (`values-ko/strings.xml`) must mirror all keys. Brand and target app names must retain `translatable="false"`.

## Releases & Distribution

- Distribution uses GitHub Release `v1.0.0` with asset `TallGate.apk`. Do not cut new releases unless explicitly instructed; updates replace the asset in place (`gh release upload v1.0.0 --clobber`) and move the tag.
- The APK is debug-signed.
- GitHub Pages is served from `docs/` (`main:/docs`).
- `applicationId` `dev.yhw.tallgate` must remain constant.
