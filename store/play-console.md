# Play Console answers

Paste-ready answers for the Play Console "App content" forms. The store listing text lives in `metadata/android/<locale>/` (fastlane supply layout).
Keep these in sync with `app/src/main/res/xml/tallgate_accessibility_service.xml`, the in-app Disclosure strings, and `docs/privacy.html`.

## Store listing

- Category: Tools
- Privacy policy: https://yoonhyunwoo.github.io/tallgate/privacy.html (Korean: https://yoonhyunwoo.github.io/tallgate/privacy.ko.html)
- Contains ads: No
- Target age: 13 and over, confirmed (including under-13 would trigger the Families policy)
- Public contact email: yjs88zerg@gmail.com (shown publicly on the Play listing)

## Data safety

- Does the app collect or share any required user data types? **No.**
- Basis: accessibility data is evaluated in memory on the device only; the app has no `INTERNET` permission; only user settings are stored locally.
- This relies on Play's definition that data processed only on-device and never transmitted is not "collected". Re-read the definition in the Console help before submitting.

## Content rating (IARC)

Category: Utility, Productivity, Communication or Other. Answer "No" to violence, sexual content, language, controlled substances, gambling, user-generated content, user interaction, location sharing and digital purchases.

## App access (reviewer instructions)

```
No login is required. To test:
1. Install TallGate and open it. Read the Accessibility Disclosure and tap "Accept and Continue".
2. Tap "Open Accessibility Settings", find TallGate, turn it on and confirm.
3. Return to TallGate. Keep "Block Instagram Reels", "Block YouTube Shorts" and "Block TikTok For You" on.
4. Open the Instagram, YouTube or TikTok app (they must be installed and signed in). Open a Reels, Shorts or "For You" screen. TallGate exits it and shows a toast ("Reels blocked", "Shorts blocked", "TikTok feed blocked").
Standard YouTube videos and open Instagram DM chats are not affected.
```

## Accessibility API declaration

Primary purpose is accessibility tool: **No.**

Core functionality:

```
TallGate is a digital-wellbeing utility. Its sole purpose is to detect short-form video feeds (Instagram Reels, YouTube Shorts, TikTok "For You") when they open and immediately exit them, helping users avoid unintentional video consumption.

The AccessibilityService API is used to (1) read, only inside Instagram, YouTube and TikTok, view identifiers, view labels and the selected tab to recognize a short-form screen, and (2) perform a system back action or switch to the standard feed tab. Events from every other app are excluded by the service's packageNames configuration (com.instagram.android, com.google.android.youtube, com.zhiliaoapp.musically, com.ss.android.ugc.trill).
```

Why the API is needed:

```
Android offers no other API to detect which screen is showing inside another app or to leave it. UsageStats only reports which app is in the foreground and cannot tell a Shorts player from a regular video. canRetrieveWindowContent is required to read the view identifiers and labels used for detection.
```

Data collection and sharing:

```
TallGate does not collect, store, log, transmit or share on-screen content or personal data. Detection runs in memory on the device. The app has no INTERNET permission. Only the user's own settings (feed toggles, feedback options, disclosure consent) are stored locally.
```

Prominent disclosure:

```
Before the service can be enabled, TallGate shows a full-screen "Accessibility Disclosure" that explains what is accessed, why, and what is not collected. The service does nothing until the user taps "Accept and Continue". The user can turn the service off at any time in Android Settings > Accessibility.
```

Note: TikTok detection matches node text (`"for you"` / `"추천"`), so "view labels" above includes that text. Keep this wording honest if the detection logic changes.

## Demo video script (about 2 minutes)

Record a build installed from the Play internal testing track, so the sideload "Allow restricted settings" step does not appear. Show touches, narrate or caption in English, upload as unlisted on YouTube and paste the URL into the form.

1. First launch: the "Accessibility Disclosure" screen (information accessed, purpose, privacy).
2. Tap "Accept and Continue"; the main screen shows "Accessibility permission required".
3. Tap "Open Accessibility Settings"; turn TallGate on in system Accessibility settings and confirm.
4. Back in the app: the four "Blocked feeds" toggles and the "Feedback" toggles.
5. Instagram: the home "For you" tab returns to Feed with a "Reels blocked" toast; opening fullscreen Reels closes it.
6. YouTube: opening Shorts closes it with "Shorts blocked"; a regular video plays normally.
7. TikTok: entering "For You" exits with "TikTok feed blocked".
8. Turn TallGate off in Android Settings > Accessibility.

Do not show the Decline path until its behaviour has been verified on a device.

## Verify on a device before submitting

- Instagram: an open DM chat is not closed; a Reel shared inside a DM may be (the DM guard only checks for the chat composer).
- Toast strings match the current `strings.xml`.
- Instagram, YouTube and TikTok view IDs still match the installed app versions (AGENTS.md: IDs come from real `uiautomator dump` output).
