# Noizey agent guidance

## Build and validation

- Use Gradle's normal cache directory at `/Users/arthack/.gradle` on the root
  volume. Do not redirect or symlink it to another volume.
- Before installing a change, run:

  ```sh
  ./gradlew testDebugUnitTest lintDebug assembleDebug
  ```

## Phone installation

- Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk` so the
  phone's presets and settings are retained. Never uninstall the existing app
  to work around a signing mismatch without the human's explicit approval.
- The existing phone installation uses the legacy Android debug key at
  `/Volumes/Scratch/coldstorage/mike/.android/debug.keystore`. Sign the APK
  with that key for an in-place update; its alias and passwords use the Android
  debug-keystore defaults.
- Verify the changed behavior on the phone after installation.

## End-of-session cleanup

- After the APK is installed and verified, stop Noizey's Gradle daemon with
  `./gradlew --stop`.
- Once no other Gradle work is active, confirm `/Users/arthack/.gradle` is a
  real directory rather than a symlink, then remove that cache directory. It
  is disposable and Gradle will recreate it locally during the next Noizey
  development session.
