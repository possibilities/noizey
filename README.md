# Noizey

Noizey is an offline, dark-only Android sound mixer built for focus, rest, and masking distraction. It combines real-time colored noise with procedural nature soundscapes, keeps playing when its UI is dismissed, and deliberately mixes with other apps instead of taking exclusive audio focus.

## What is included

- Brown, pink, white, gray, green, blue, and violet noise
- Deep fan and cabin-hum generators
- Soft rain, window rain, heavy rain, distant thunder, ocean, stream, waterfall, wind, forest night, and fireplace models
- Eight factory presets and unlimited on-device custom presets
- Per-layer mute and volume, a master level, and click-free preset crossfades
- 15–120 minute sleep timers with a gentle final 30-second fade
- Media-session notification, lock-screen, headset, and Bluetooth play/pause controls
- Foreground playback that survives leaving the app or dismissing its task
- Intentional coexistence with YouTube, podcasts, music, and other audio apps

Every sound is synthesized locally in real time. There are no network calls, ads, analytics, accounts, downloaded recordings, loop seams, or third-party sound licenses.

## Build

Requirements: JDK 17 and Android SDK 36.

```sh
./gradlew assembleDebug
```

Run tests and static analysis:

```sh
./gradlew testDebugUnitTest lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Playback behavior

The mixer lives in a Media3 `MediaSessionService` with the `mediaPlayback` foreground-service type. A partial wake lock is held only while sound is actively rendering. Swiping Noizey away does not stop an ongoing mix. Play/pause is available from the app, notification, lock screen, headset, and system media controls; the app also has an explicit Stop control.

Noizey does **not** request Android audio focus. That is intentional: requesting focus would normally pause or fade the other app, while Noizey's core promise is to sit underneath it. Android's system mixer therefore combines Noizey with other non-exclusive playback. A phone force-stop, Android's Active Apps **Stop** action, or an exclusive hardware/audio route can still stop playback; apps are not allowed to bypass those system controls.

## Architecture

- Jetpack Compose + Material 3, one responsive screen
- `SimpleBasePlayer` + Media3 `MediaSessionService`
- Streaming stereo float PCM through `AudioTrack`
- Stateful procedural DSP with gain smoothing and soft limiting
- `SharedPreferences` persistence for the last mix and custom presets
- Pure JVM tests for preset serialization and every DSP generator

The app targets Android 16 / API 36 and supports Android 8 / API 26 and newer.
