# Google Play release material

`listing/` contains the English (United States) store copy and versioned graphics. `console/` records the policy answers used for the current release; they are review inputs, not a substitute for checking the live Console wording.

Before uploading a build:

1. Run `./gradlew testDebugUnitTest lintDebug assembleDebug`.
2. Install the debug APK in place with the legacy debug key and verify the changed behavior on the phone.
3. Build a signed release bundle with the dedicated upload key and inspect its certificate and manifest.
4. Confirm that the Data safety and foreground-service answers still match the shipped code.
5. Upload the `.aab` to the applicable Play track and retain its review result with the release record.

The application id `com.noizey.app` and paid-first status become permanent when the Play Console app is created. Never publish this package as free and never use the legacy Android debug key as its production signing identity.

The public product, privacy, and support site is
<https://noizey.notimpossiblemike.chatgpt.site>. Use its `#privacy` section for
the Play privacy-policy field.
