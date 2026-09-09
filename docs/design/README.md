# Noizey visual identity

Noizey is a quiet instrument for blending sound. The Android interface keeps its
monochrome night palette, static ring mark, and native controls. Its primary job
is to make choosing a mix, balancing its layers, and pausing playback immediate.

## Brand

Use the existing ring-and-core symbol with a bold lowercase `noizey` wordmark.
The product name in prose remains Noizey. “Make your own quiet.” is the brand
line; “An offline sound mixer for Android” explains the product in store artwork.
The symbol is an identity, not a live meter. It never pulses to imply audio activity.

The launcher retains the familiar symbol. Store artwork sources live in
`play/listing/graphics/source/`; render the feature graphic with:

```sh
rsvg-convert play/listing/graphics/source/feature-graphic.svg \
  -o play/listing/graphics/feature-graphic-1024x500.png
```

## Android design system

- Night `#0B0B0C`: continuous mixer canvas.
- Surface `#101011`: sheets, selectable presets, and transport.
- Raised `#1C1C1E`: native control surfaces.
- Silver `#D7D7D8`: primary actions and selection.
- Text `#E9E9EA`: names and headings.
- Secondary `#9E9EA2`: readable descriptions and values.

Use the Android system sans family: bold, tightly spaced for the wordmark;
34/40 sp semibold for the current mix and sheet titles; 16/22 sp for sound names;
14/20 sp for ordinary explanations. Respect system text scaling. Controls keep
native touch targets, focus, slider semantics, and state descriptions.

The layout is left aligned: identity and utility controls, current mix and master
level, compact preset strip, continuous sound rows, then a pinned transport.
Surfaces distinguish selectable presets from editable sound rows. Sound rows use
spacing and a quiet divider instead of a card and icon tile around every layer.
Slider ticks are visually suppressed while their stepped adjustment remains.
Selection has a checkmark and accessible state; mute has explicit text and a switch.

## Review decisions

The previous screen spent substantial vertical space on preset cards and boxed
sound layers. Small uppercase section labels and dotted slider tracks competed
with the content. The revised hierarchy puts larger readable names on a calmer
canvas, allows two-line sound and preset names, and retains readable muted levels.
Sheets open expanded so controls and explanatory text are not initially below
the screen. The sound picker has a persistent Done action so closing it does not
depend on knowing a bottom-sheet gesture. “Save preset” names the object created by saving.

Keep audio generation, signing, persistence, and the desktop TUI's fxnk language
independent from visual changes. Website deployment and Play publication are
separate delivery actions; the checked-in store graphic is preparation for them.

Design references: the current Vercel design baseline, the wiki's *Vercel design
guidance for native fleet apps* and *Design studio playbook for Android, web and
native apps*, and the local design-with-ai core stance. These inform restraint,
hierarchy, and actual-renderer verification; they do not replace Noizey's identity.

## Verification

The September 9, 2026 makeover passed `testDebugUnitTest lintDebug assembleDebug`
(9 unit tests) and was installed in place on the Samsung Galaxy S22 with the
legacy debug key. Device checks covered the mixer, volume adjustment, play/pause,
adding a sound, Done dismissal, expanded timer sheet, and preferences. Mixer and
picker layouts were also inspected at 1.5 system font scale. Full TalkBack and
all-device-size qualification were not part of this bounded pass.

The final four store screenshots are actual device captures. The phone's original
Noizey preferences were compared byte for byte after installation and restored
following interaction checks. System text size was restored, and the phone was
returned to AgentVoice. No emulator was created. The Gradle daemon was stopped
and the disposable root cache removed after checking for other Gradle processes.
