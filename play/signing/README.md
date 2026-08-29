# Release signing

Noizey uses Google Play App Signing. Google generates and protects the production app-signing key; the local RSA key is a replaceable upload credential only.

- Alias: `noizey-upload`
- Primary keystore: `/Users/arthack/.android/noizey-upload.jks`
- Cold-storage copy: `/Volumes/Scratch/coldstorage/mike/.android/noizey-upload.jks`
- Password source: macOS Keychain service `noizey-google-play-upload-key`, account `noizey-upload`
- Public certificate: `upload-certificate.pem`

Never commit a keystore or password. If the upload key is lost or compromised, request an upload-key reset in Play Console; this does not rotate the Google-held app-signing key installed on user devices.
