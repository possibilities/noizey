# Noizey Play Console answers

These answers describe release `1.0.0` and must be revisited whenever the app or its dependencies change.

## App setup

- App name: **Noizey**
- App or game: **App**
- Default language: **English (United States)**
- Application ID: **com.noizey.app**
- Pricing: **Paid from first publication**, with a United States price of **$1.99 USD**
- Initial availability: **United States**
- Category: **Music & Audio**
- App signing: **Google Play App Signing**, using the dedicated Noizey upload certificate

## Merchant setup

- Payments profile type: **Individual**
- Public business name: **Noizey Studio**
- Product type: **Computer Software**
- Website and support email: use the Store contact values below
- Buyer card-statement text: **Google*Noizey**
- Account group: **Noizey Studio**; enrolled in the 15% service-fee tier
- Configure and verify a United States payout bank account in Play Console. Never
  copy its routing or account number into this repository.

## Closed testing and production access

- This personal developer account must run a closed test with at least **12
  testers continuously opted in for 14 days** before applying for production
  access. Recruit 18–20 where possible so one dropout does not reset the clock.
- Keep the application paid from creation. Closed-test users normally must buy a
  paid app, so create paid-app promo codes to comp tester copies if Play Console
  enables them for the closed release. If it does not, pause before asking anyone
  to pay; never switch this package to free as a workaround.
- Recruit real Android users and collect candid feedback. Do not request public
  ratings, positive reviews, fake usage, multiple accounts controlled by one
  person, or any other engagement manipulation.
- Use a Google Group or private Play tester list and share the opt-in link
  directly. Never publish tester Google-account addresses or commit them here.

## Store contact

- Privacy policy: **https://noizey.notimpossiblemike.chatgpt.site/privacy**
- Website: **https://noizey.notimpossiblemike.chatgpt.site**
- Support email: **mikebannister@gmail.com**
- The privacy policy accurately discloses optional Android system backup and device transfer of locally stored preferences.
- On August 31, 2026, the dedicated `/privacy` route was deployed and the
  Console privacy-policy URL change was submitted. Play shows it under
  **Changes in review**.
- The four opaque RGB phone screenshots were recropped to `1080 × 2160`,
  uploaded in listing order, and are also under **Changes in review**.

## Ads

- Contains ads: **No**

## App access

- All functionality is available without special access.
- No account, login, membership, location, or access code is required.

## Target audience and content

- Intended ages: **13–15, 16–17, and 18+**
- Designed for children: **No**
- News app: **No**
- Government app: **No**
- Financial features: **None**
- User-generated content or social features: **None**

## Data safety

- Data collected: **No data collected**
- Data shared: **No data shared**
- Accounts: **No account creation**
- Data transmitted off device: **None**
- The system document picker reads or writes a settings backup only after an explicit user action. Noizey does not receive the file outside the app process or transmit it anywhere.

### Release 1.0.0 audit (August 31, 2026)

- Audited bundle: `app/build/outputs/bundle/release/app-release.aab`, version code
  `1`, version name `1.0.0`, SHA-256
  `19ce42a0fc3b2b1ee47de31c8ded7b1718904383adf54d1999c518d2cbf6c552`.
- The merged release manifest has no `INTERNET`, advertising ID, location,
  camera, microphone, contacts, accounts, or storage permission. Its only
  platform permissions are foreground service, foreground media playback,
  notifications, wake lock, and `ACCESS_NETWORK_STATE`. Media3 contributes
  `ACCESS_NETWORK_STATE`; without `INTERNET`, it cannot transmit app data.
- The packaged dependency closure contains AndroidX/Media3, Kotlin,
  coroutines, and Guava support libraries. It contains no advertising,
  analytics, crash-reporting, attribution, telemetry, billing, or HTTP-client
  SDK.
- App source stores mixes, presets, and preferences in private
  `SharedPreferences`. Import and export use Android's system document picker
  only after a user action. Android system backup and device transfer may copy
  those preferences, as disclosed by the privacy policy; Noizey Studio does
  not receive them.
- The Privacy & support button opens the public Noizey URL in the user's
  external URI handler. Noizey has no WebView, embedded browser, socket, or
  HTTP request code. A release DEX string scan found the Noizey support URL and
  library diagnostic/documentation URLs, but no app data endpoint.
- Live Console review showed **No** for collecting or sharing required user
  data. Its preview says **No data collection declared** and **No data shared
  with third parties**, with the documented Noizey privacy-policy URL. This
  matches the bundle, so no Console change was saved or submitted.
- Re-run this audit before any release that adds a permission, SDK, server,
  WebView, account, telemetry, advertising, billing, or other off-device data
  path.

## Health apps declaration

- Noizey does not diagnose, treat, monitor, or manage a medical or health condition.
- It does not collect health data or connect to Health Connect, sensors, or medical devices.
- Its timer only stops audio playback; it does not measure or analyze sleep.
- Select the Console answer equivalent to **This app does not provide health features**.

## Content rating

- Complete the IARC questionnaire as an app/utility.
- Violence, sexuality, language, controlled substances, gambling, fear, and user interaction: **None**.
- Location sharing, unrestricted web access, and digital purchases inside the app: **None**.

## Foreground service

- Type: **Media playback**
- User-visible feature: a chosen mix continues playing when the user locks the phone, opens another app, or dismisses Noizey's activity.
- User initiation: the user presses Play in Noizey.
- User control: pause and stop are available in the app and the persistent media notification.
- Interruption impact: Android would stop the user's active mix and sleep timer early.
- Evidence: upload `evidence/foreground-playback.mp4`, which shows user-initiated
  playback continuing outside Noizey and pause/resume control from the compact
  media notification. Unrelated notifications are masked for privacy.
