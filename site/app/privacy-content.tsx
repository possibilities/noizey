export const privacyEffectiveDate = "August 29, 2026";

export function PrivacyPolicyContent() {
  return (
    <div className="policy-copy">
      <h3>Noizey does not collect your data.</h3>
      <p>
        Noizey has no analytics, advertising, accounts, tracking, or network
        access. No personal information, usage data, audio, or device
        identifiers are collected, transmitted, sold, or shared by Noizey
        Studio.
      </p>

      <h3>What stays on your device</h3>
      <p>
        Your current mix, preferences, and custom presets are stored locally.
        Noizey generates sound in real time and does not record microphone
        audio. When you choose Export settings, Android&apos;s system document
        picker saves a portable backup to the location you select. Noizey
        accesses an imported or exported file only in response to that action.
      </p>

      <h3>Android backup and device transfer</h3>
      <p>
        If Android backup is enabled for your device, Google&apos;s Android backup
        or device-transfer service may copy Noizey&apos;s locally stored settings
        and presets to your Google account or another device. Noizey Studio
        does not operate that service, receive those backups, or have access
        to them. You can manage Android backup in your device settings.
      </p>

      <h3>Children and policy changes</h3>
      <p>
        Noizey is not directed to children under 13 and does not knowingly
        collect information from anyone. If the app&apos;s data practices ever
        change, this page will be updated before the changed version is
        released.
      </p>
    </div>
  );
}
