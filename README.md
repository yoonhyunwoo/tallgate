# TallGate

An Android app that detects and blocks Instagram Reels, YouTube Shorts, and the TikTok recommendation feed using Android's Accessibility Service. Operates by inspecting the active UI node tree without modifying target application packages.

## Features

- **Instagram**
  - **Reels blocking**: Automatically redirects the home 'For you' tab to the 'Feed' tab; dismisses fullscreen Reels viewers using the system back action (`GLOBAL_ACTION_BACK`) while preserving active DM chat threads.
  - **Explore tab blocking**: Redirects to the home feed when the Explore (Search) tab is selected (disabled by default).
- **YouTube**
  - **Shorts blocking**: Triggers the system back action upon detecting the Shorts player.
- **TikTok**
  - **For You feed blocking**: Triggers the system back action upon detecting the 'For You' feed.
- **Feedback**
  - Configurable haptic feedback and toast notifications on block events.
- **Privacy**
  - Reads on-screen element info only from the supported apps, entirely on-device. Nothing is stored or shared, and the app declares no internet permission. A disclosure and consent screen is shown on first launch, and the service stays inactive until you accept.

## How It Works

`TallGateAccessibilityService` listens for window and content change events from supported applications:

1. **Event Filtering**: Listens only to `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED` events from target packages (`com.instagram.android`, `com.google.android.youtube`, and TikTok).
2. **Node Inspection**: Searches the active window's node hierarchy for view identifiers and states associated with short-form players (e.g., `clips_viewer_container`, `reel_watch_fragment_root`).
3. **Redirection & Debounce**: Redirects tabs or performs `GLOBAL_ACTION_BACK` when matches occur, with an 800ms debounce window to prevent repeated triggers.

## Build & Installation

### Requirements
- Android 8.0 (API level 26) or higher
- JDK 17+ (CI and the maintainer build with JDK 25; Gradle 9.8, AGP 9.5)

### Build

```bash
# Build debug APK
./gradlew assembleDebug

# Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch, review and accept the accessibility disclosure. The app then opens accessibility settings; enable the service under **Settings > Accessibility > Installed apps > TallGate**.

On Android 13+, a sideloaded APK may have the accessibility toggle greyed out. If so, open **Settings > Apps > TallGate**, tap the **⋮** menu, choose **Allow restricted settings**, then enable the service again.
