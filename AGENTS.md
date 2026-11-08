# AGENTS.md

## Build

- Run Gradle from the repo root. There are no unit tests; the verifiers are `./gradlew :app:assembleDebug` and `:app:lintDebug`. UI and blocking behavior can only be verified on a device.
- `gradle.properties` pins `org.gradle.java.home=/opt/homebrew/opt/openjdk@17`. This is machine-specific and breaks builds elsewhere. Do not copy the pattern; move it to `~/.gradle/gradle.properties` if touching it.
- `local.properties` (`sdk.dir`) is gitignored and per-machine.

## Blocking logic depends on third-party view IDs

- Detection in `TallGateAccessibilityService` matches resource IDs of Instagram and YouTube (`IG_ID`, `YT_ID`). Those apps change IDs between versions and the IDs here come from UI dumps on real builds, so never rename or "clean up" them from guesswork. Re-dump on a device (`uiautomator dump`) before changing any.
- Instagram quirk: the Home "For you" tab embeds the Reels viewer while the bottom `tab_bar` is still visible. Hence the two-branch logic: tab bar visible -> click the "Feed" tab; tab bar hidden -> treat the Reels viewer as standalone and press back.
- YouTube Shorts and TikTok detection are unverified on a device. TikTok is a text match ("for you" / "추천") with a depth limit and may false-positive.

## Accessibility service constraints

- Package scope is declared twice: `android:packageNames` and `accessibilityEventTypes` in `res/xml/tallgate_accessibility_service.xml`, and the `PKG_*` constants in the service. Adding an app requires updating both; the XML list is what prevents the service from receiving events from other apps (privacy and the disclosure text rely on this).
- `rootFor(event)` guards against acting on a window from a different package than the event. Keep it for every new handler.
- Debounce uses `SystemClock.elapsedRealtime` (not wall clock). Back/click actions fire from content-changed events, so removing the debounce causes repeated back presses.
- The service does nothing until `BlockPreferences.isDisclosureAccepted` is true. `MainActivity` redirects to `DisclosureActivity` before inflating its binding. This is a Google Play prominent-disclosure requirement, so keep the disclosure copy consistent with what the service actually reads (no storage, no network, no `INTERNET` permission).
- Force-stopping the app disables the accessibility service; re-enable with `adb shell settings put secure enabled_accessibility_services dev.yhw.tallgate/.TallGateAccessibilityService` and `accessibility_enabled 1`.

## Persisted state

- SharedPreferences file `tallgate_block_prefs` and the `key_*` names in `BlockPreferences` are stored on user devices; renaming resets users' settings.

## Strings

- English `values/strings.xml` is the default; `values-ko/strings.xml` must mirror every key. Brand and target app names are `translatable="false"`.

## Release

- Distribution is a GitHub Release `v1.0.0` with asset `TallGate.apk`, linked from the site and README via `releases/latest/download/TallGate.apk`. The maintainer updates the asset in place (`gh release upload v1.0.0 --clobber`) and moves the tag, instead of cutting new releases. Push, release, and tag changes only when explicitly asked.
- The APK is signed with the debug key. Switching to a release key forces existing installs to uninstall first.
- `docs/` is the GitHub Pages site (`main:/docs`). README and `docs/index.html` are maintained by the owner in English; do not rewrite their copy.
- `applicationId` `dev.yhw.tallgate` is permanent if ever uploaded to Google Play.
