# Noizey in the terminal

Run from the mobile repository root:

```sh
go run .
```

Or build a standalone binary:

```sh
go build -o noizey .
./noizey
```

Go 1.24+ and a C compiler are required to build. macOS uses CoreAudio; Linux uses PulseAudio (including PipeWire's PulseAudio service), ALSA, or JACK. miniaudio is compiled into the binary through `malgo`. There is no audio-player subprocess, runtime Go dependency, network access, or downloaded recording. First-time Go dependency downloads need a network connection.

The app starts paused and restores the last mix. Audio continues while the terminal process runs, including when its window is in the background. Quitting, closing the terminal, or interrupting the process stops audio. To keep playback independent of an interactive screen, run it inside your usual terminal multiplexer:

```sh
./noizey --headless --preset deep_sleep --timer 60
```

Headless mode starts playing immediately and exits on a signal or when the timer finishes. It does not spawn or leave behind a daemon.

## Controls

The same controls work with arrow keys or the home row. Text fields own their keys; pasted text cannot trigger playback or deletion.

| Key | Action |
| --- | --- |
| Space | Play / pause |
| `x` | Stop and clear the sleep timer |
| `1`–`4`, Tab / Shift-Tab | Mix, sounds, presets, settings |
| Up / Down, `k` / `j` | Select a row |
| Home / End, `g` / `G`, Page Up / Down | Navigate longer lists |
| Left / Right, `h` / `l` | Change selected layer level by 5% |
| `[` / `]`, `-` / `+` | Change master level by 2% |
| Enter, `m` | Add or mute a sound; Enter loads a preset or changes a setting |
| `d`, Delete | Remove a layer; confirm deletion of a custom preset |
| `s` | Save the current layers as a named preset |
| `t` | Set a 15, 30, 45, 60, 90, or 120 minute timer; `0` turns it off |
| `/` | Search sounds by name, description, or category |
| `e` / `i` | Export / restore a settings backup |
| `?` | Help; scroll with Up / Down |
| Esc | Cancel a prompt or clear the sound filter |
| `q`, Ctrl-C | Quit; Ctrl-C also works inside a prompt |

Names and paths support arrows, Home / End, Backspace / Delete, Ctrl-U to clear, Ctrl-W to erase a word, and bracketed paste. Paths in TUI prompts support `~/`. The layout adapts down to 48 columns × 16 rows; smaller windows show a resize hint while retaining transport and quit controls.

## Mobile parity

All 19 procedural generators and eight factory presets are included. The generated catalog is extracted from Android's `SoundCatalog.kt` and `MixModels.kt`; `python3 scripts/sync-tui-catalog.py --check` detects drift. To update it after changing Android's catalog, run the script without `--check` and implement any new generator in `internal/audio/generator.go`.

The DSP ports the Android algorithms to 48 kHz stereo: matching filters, envelopes, seeded random sources, 160 ms layer smoothing, 80 ms master smoothing, and the same soft limiter. Floating-point precision differs from Kotlin, so samples are not promised to be bit-identical. A separate 50 ms transport ramp makes desktop play/pause transitions smooth.

Presets retain layer levels and mute states. Applying a preset preserves the master level, matching the mobile app. Edits clear the active preset identity. Deleting a preset leaves its current sound layers intact. The portable format supports up to 1,000 custom presets.

The timer pauses with playback and gently fades the final 30 seconds. Stop clears it. A timer is local to the playback session and is not restored on a new launch or from a settings backup.

Audio uses a shared output, so it can coexist with music, podcasts, and other apps. Noizey does not register for hardware media keys. The default output is checked every two seconds. An output change pauses playback unless **Continue after output changes** is on, in which case playback restarts on the new default. This preference shares the Android headphone-disconnect backup field. Desktop route detection is polling, so a default output can change before the next check; it is not an instantaneous headphone-disconnect guarantee.

Android foreground-service notifications, wake locks, and continued playback after closing the app have no direct TUI equivalent. Desktop playback lasts as long as this process and the operating system's audio session are available.

## Settings and backups

Settings are saved after every mix, preset, or preference change:

- macOS: `~/Library/Application Support/noizey/settings.noizey`
- Linux: `$XDG_CONFIG_HOME/noizey/settings.noizey`, or `~/.config/noizey/settings.noizey`
- Override either with `--state /path/to/settings.noizey`.

The app holds a file lock to prevent simultaneous sessions from overwriting one settings file. Use different `--state` paths for independent sessions. Writes use an atomic replacement and private file permissions. Corrupt settings are reported and left untouched.

Backups use Android's `NOIZEY_SETTINGS_V1` format: current mix, custom presets, and the playback preference. Export in either app and restore in the other. No generated audio is included. Restore previews the incoming mix before confirmation and saves the existing settings beside the state file as `settings.noizey.previous`. Export refuses to overwrite an existing destination.

Noninteractive operations are also available:

```sh
./noizey --list-presets
./noizey --preset ocean_night --play
./noizey --export ~/noizey-backup.noizey
./noizey --import ~/noizey-backup.noizey
./noizey --help
```

`--import` validates the file and replaces settings immediately, retaining `.previous`. It must be used by itself apart from `--state`. If the current settings file is corrupt, keep a copy, then use a new `--state` path to restore a known-good backup.

## Appearance and validation

The style contract is `~/code/fxnk/style/STYLE.md` and its `tokens.json`. The TUI uses fixed indexed gray roles, the terminal's default background, and the focus caret from the fxnk vocabulary. The light palette is selected by case-insensitive `FX_THEME=light|dark`, otherwise one 200 ms OSC 11 background query, then `COLORFGBG`, then dark. Mode 2031 notifications trigger a DA1-fenced resample and a complete palette swap; late startup replies and stale live replies are ignored.

```sh
FX_THEME=light ./noizey
go test ./...
go test -race ./...
go vet ./...
python3 scripts/sync-tui-catalog.py --check
```

An optional physical-output test plays quietly, verifies native PCM delivery, checks coexistence with a second stream, then pauses and restarts:

```sh
NOIZEY_AUDIO_TEST=1 go test ./internal/audio -run TestNativePlayback -v -count=1
```

Normal tests do not open an audio device. The native probe verifies device callbacks, not subjective listening quality. CI is configured to build and run unit/race tests on macOS and Linux; a working audio session is required for the physical-output probe.
