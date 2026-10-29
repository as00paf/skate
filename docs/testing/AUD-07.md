# AUD-07 test steps: per-bus low-pass and reverb-send in AudioConfig / AudioSettings

Scope: engine/settings layer only. The editor does not yet call `AudioEngine.applyConfig(settings.audio.toConfig())`
(pending EDT task), so there is NO editor UI to exercise this yet. Verification is through automated tests and the settings file.

## A. Automated check (clean state)
1. Open a terminal in the repo root (Windows: PowerShell; WSL: use `cmd.exe /c` prefix).
2. Run: `.\gradlew.bat test --tests "com.pafoid.skate.engine.audio.BusEffectsConfigTest"`
   Expected: BUILD SUCCESSFUL, 3 tests pass:
   - `Mixer_ConfigAppliesBusEffectsLive` (MUSIC low-pass 0.4 x MASTER 0.5 = 0.2; UI reverb send 0.7; SFX send 0)
   - `Mixer_DefaultConfigKeepsDefaultEffects` (defaults: all low-pass 1, SFX send 1, MUSIC/UI send 0)
   - `Mixer_AudioSettingsEffectsAreValidatedAndMapped` (-1 -> 0, 3 -> 1, 0.3 kept)
   (Note: Gradle may report "no tests found" for the `tools:cli` module when filtering; use `:test` for the root project: `.\gradlew.bat :test --tests "..."`.)
3. Run the full suite: `.\gradlew.bat :test`. Expected: BUILD SUCCESSFUL, no FAILED lines.

## B. Backward compatibility of saved settings
4. Launch the editor: `.\gradlew.bat run`. Expected: editor window opens, no audio errors in the console.
5. Close the editor, then open your existing hardware settings file (the JSON holding `"audio": {...}`; found via `HardwareSettings` in `editor/settings`).
   Expected: it loads without errors in step 4 even if it lacks the new keys (defaults apply).
6. Edit the `audio` object to add `"musicLowPass": 0.4, "sfxReverbSend": 0.5`, save, relaunch with `.\gradlew.bat run`.
   Expected: editor starts normally and the values are preserved after the editor rewrites the file (and out-of-range values such as `3` are clamped to 1 by `validate()` when mapped).
7. Audible effect is NOT expected yet; it appears once EDT wires `applyConfig`.

## C. After the EDT wiring lands (future)
8. Set `musicLowPass` to 0.2, play a music-bus AudioComponent in a scene (Play button). Expected: music sounds muffled; restoring 1.0 removes it.
