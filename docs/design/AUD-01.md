# AUD-01 — Audio review and target design

Scope: `engine/audio/AudioEngine`, `engine/ecs/systems/AudioSystem`, `engine/ecs/components/AudioComponent`, `engine/assets/loaders/SoundLoader`, `engine/assets/data/SoundSource`, `engine/events/AudioEvents`, `editor/settings/AudioSettings`.
Status: review + target design. No code changed. Follow-ups: AUD-02 (mixer/buses), AUD-03 (3D), AUD-04 (streaming/voices), AUD-05 (effects).

## 1. Current state

- `AudioEngine` opens the default device, creates one context, and exposes listener setters plus `setMasterVolume` (`AL_GAIN` on the listener). It is constructed in `Engine` and `init()`ed there; `AudioSystem.update` calls `init()` again (idempotent).
- `AudioSystem` (priority `LATE`) syncs the listener to the editor/game camera, then walks `scene.children` every frame, looking up `AudioComponent` + `Transform`, and keeps one `SoundSource` (one AL source) per entity in `activeSources`.
- `AudioComponent` holds `soundFilePath`, `is3D`, `loops`, `volume` plus transient play/pause/stop request flags.
- `SoundLoader` decodes a whole WAV (javax.sound) or OGG (`stb_vorbis_decode_filename`) into memory and uploads one `AL buffer`. `AssetsManager.getSound` caches it by absolute path forever.
- `AudioEvents` (`AudioPlay`, `AudioStop`, `AudioVolumeChange`, `AudioListenerMove`) are declared but nothing publishes or consumes them.
- `AudioSettings` (master/music/sfx volume, outputDevice) is persisted via `HardwareSettings` but **nothing reads it at runtime**; `AudioSystem.masterVolume` has no caller.

## 2. Defects

| # | Where | Defect | Sev |
|---|-------|--------|-----|
| D1 | `AudioSystem` / `AudioSettings` | Settings are never applied: master/music/SFX volumes have no effect; no buses, no mute. | High |
| D2 | `AudioSystem` | Every `AudioComponent` permanently owns an AL source (`activeSources`). No voice cap; OpenAL typically fails at ~256 sources (`alGenSources` error ignored, returns 0/-1) and the failure is unchecked. No priority or stealing. | High |
| D3 | `AudioSystem.updateListener` | Velocity hard-coded to 0 (no Doppler); up vector hard-coded `(0,1,0)` (wrong when the camera rolls); listener is always the camera, there is no `AudioListener` component. | Med |
| D4 | `AudioSystem.updateAudioSources` | Source with a path that fails to load throws out of `getSound` (re-thrown) every frame → system update exception each tick. Sources are created only when `soundFilePath` is non-blank at first sight; changing the path afterwards is ignored (stale buffer). | High |
| D5 | `AudioSystem.updateAudioSources` | `isPlaying` is overwritten from the source state every frame, so a paused source reads as "not playing" and `play()` after `pause()` is indistinguishable from stopped; no `isPaused` state. Setters (`volume`, `looping`, `relative`, position) are issued to OpenAL every frame for every source (redundant AL calls). | Low |
| D6 | `AudioSystem` | Direct `ALC`/`ALC10` calls and capability re-creation in the system; OpenAL cannot be mocked, so no logic is unit-testable. Context re-check every frame is a workaround, not a design. | Med |
| D7 | `SoundSource.stop` | `alSourcei(sourceId, AL_POSITION, 0)` – `AL_POSITION` is a 3-float property, so this raises `AL_INVALID_ENUM`. Intended to rewind; `alSourceStop` already rewinds to `AL_INITIAL`. `delete()` calls `stop()` which also hits it. | Med |
| D8 | `SoundSource` | No spatial parameters: `AL_REFERENCE_DISTANCE`, `AL_MAX_DISTANCE`, `AL_ROLLOFF_FACTOR`, `AL_VELOCITY`, `AL_PITCH`, direction/cone all unset (defaults: ref 1, rolloff 1, max `FLT_MAX`). Distance model never configured (`alDistanceModel` default inverse-clamped). No AL error checking anywhere. | Med |
| D9 | `SoundLoader` | Entire OGG is decoded to PCM and kept resident (a 5-minute stereo 44.1 kHz track ≈ 50 MB). Cache never evicts. WAV: only PCM; 24/32-bit conversion assumes little-endian and signed; float/ADPCM/big-endian unsupported; `printStackTrace` plus re-throw logs twice. OGG duration computed from `limit()` of a `ShortBuffer` (samples × channels): correct only by coincidence of the division by `channels`. Mono vs. stereo is not reflected anywhere (stereo buffers cannot be spatialised by OpenAL). | High |
| D10 | `AudioEngine` | Single hard-wired default device; `AudioSettings.outputDevice` unused; no device enumeration or hot-switch; no `isInitialized` guard in `makeContextCurrent`; `destroy()` skips cleanup if `init()` failed half-way (device leaked when `alcCreateContext` fails after catch calls `destroy()` with `isInitialized == false`). | Med |
| D11 | `AudioSystem.destroy` | Sources deleted but `audioEngine.destroy()` is never called from the system and `isInitialized = false` re-inits a second time on next `update`. Lifetime ownership is split between `Engine` and `AudioSystem`. | Low |
| D12 | `AudioComponent` | Single clip only; no bus, priority, pitch, min/max distance, rolloff, random container, one-shot, `playOnAwake`. `volume` stored in a data class → copy/equality semantics include volume. `destroy()` is a no-op. | Med |
| D13 | `AudioEvents` | Unused, and names (`sourceName`) have no meaning in the ECS. | Low |
| D14 | Architecture | `AudioSystem` already respects the editor/engine guard (no `editor.*` imports). `AudioSettings` lives in `editor/` so the engine must receive values through an engine-side config object (see 3.1). | Info |

## 3. Target design

All new code lives under `engine/audio/` (+ the owned component/system files). OpenAL is hidden behind interfaces so mixer and voice logic are pure Kotlin and tested with mockk or fakes.

```
AudioSettings (editor) ──► AudioConfig (engine, data class) ──► AudioSystem
                                                                   │
 AudioComponent ─► AudioSystem ─► VoiceManager ─► AudioBackend (interface) ◄─ OpenAlBackend
 AudioListener  ─►              ─► Mixer(buses)                              ◄─ FakeBackend (tests)
 MusicStream ◄─ StreamingSource ◄─ OggStreamDecoder
```

### 3.1 Backend abstraction and config

- `interface AudioBackend`: `init(deviceName)`, `destroy()`, `createSource(): SourceHandle?` (null on exhaustion, errors checked via `alGetError`), `deleteSource`, `setSourceParams(handle, SourceParams)`, `play/pause/stop`, `state(handle)`, `queueBuffers/unqueueProcessed` (streaming), `setListener(ListenerState)`, `setDistanceModel`, `listDevices()`, `createBuffer/deleteBuffer`.
- `OpenAlBackend` is the only class that imports `org.lwjgl.openal.*` (except `SoundLoader`, which moves to the backend's `createBuffer` for uploading in AUD-04). `AudioEngine` becomes a thin owner of device/context and is folded into `OpenAlBackend`; `AudioEngine` remains as a facade so `Engine` keeps compiling.
- `data class AudioConfig(master, music, sfx, ui, muted flags, outputDevice, maxVoices)` in `engine/audio`. The editor maps `AudioSettings` → `AudioConfig` and calls `audioSystem.applyConfig(config)` (or `AudioConfig` is published on the existing engine event bus). Engine never imports `editor.*`. Settings changes take effect live (no restart) except `outputDevice`, which re-opens the device and re-creates sources.
- Ownership: `Engine` creates and destroys the backend; `AudioSystem` only uses it (fixes D11).

### 3.2 Mixer and buses (AUD-02)

- `enum BusId { MASTER, MUSIC, SFX, UI }` with MASTER as parent of the others. `class AudioBus(id, volume, muted, parent)`; `effectiveGain = volume × (muted ? 0 : 1) × parent.effectiveGain`.
- `Mixer` owns the buses, maps `AudioConfig` to them, and computes the final source gain: `clip.volume × component.volume × bus.effectiveGain`. Pure functions, unit-tested (clamping, mute, parent chain, volume=0 edge).
- `AudioSettings` gains `uiVolume` and `muted` flags (additive, defaults keep existing files valid via `validate()`); master gain is applied to each voice via the mixer rather than only `AL_GAIN` on the listener, so music/SFX/UI can differ. Listener `AL_GAIN` stays 1.
- Per-bus effect slots are reserved for AUD-05.

### 3.3 Voices (AUD-04)

- `AudioComponent` no longer owns an AL source. Playing a clip requests a `Voice` from `VoiceManager`:
  - `Voice(handle, component?, priority, bus, startedAt, audibility)`.
  - Cap `maxVoices` (default 32, bounded by backend limit). If full, steal the voice with the lowest `priority`, ties broken by lowest audibility (gain ÷ distance), then oldest; refuse the new request if every active voice outranks it. Looping non-audible voices may be virtualised (position advanced in time, no AL source) – optional later.
  - Finished one-shots are reaped each frame and the source returned to a pool (pool avoids `alGenSources` churn).
- `AudioComponent` additions (all `@Serializable`, defaulted for back-compat): `bus: BusId = SFX`, `priority: Int = 128`, `pitch`, `minDistance`, `maxDistance`, `rolloff`, `playOnAwake`, `mode: ONE_SHOT | LOOP | RANDOM_CONTAINER`, `clips: List<String>` (random container; `soundFilePath` kept as the single-clip shorthand), plus transient `state: STOPPED | PLAYING | PAUSED` (fixes D5). `isPlaying` derived from `state`.
- Change tracking: system applies parameters only when dirty versus the last applied `SourceParams` (fixes the per-frame AL spam).
- Failed loads are cached as "failed" for the path and logged once, never throw from `update` (D4); path changes re-bind the voice.

### 3.4 Streaming (AUD-04)

- Sounds above a size/duration threshold (or flagged `stream = true`, default for the MUSIC bus) are streamed: `OggStreamDecoder` wraps `stb_vorbis_open_filename` + `stb_vorbis_get_samples_short_interleaved`, producing fixed chunks (e.g. 4 × 64 KiB buffers).
- `StreamingSource` queues N buffers, and every frame `unqueueProcessed` → refill → requeue; on EOF it seeks to the start (loop) or marks the voice finished. Buffers are owned by the stream and freed on stop. No full decode, so memory is bounded by the chunk size.
- Decoding runs on a worker (shared `JobSystem`); only queue/unqueue happens on the AL thread. Underrun recovery: if the source stopped with queued buffers left, restart it.
- WAV remains fully buffered (short SFX); long WAV streaming is out of scope.
- `SoundLoader` gets a `probe(path)` returning metadata (channels, rate, duration) without decoding, used for the stream decision and for AssetsManager's cache (AST follow-up handles eviction and ref-counting).

### 3.5 3D audio (AUD-03)

- New `AudioListener` component (`engine/ecs/components/`): active flag, optional velocity override. `AudioSystem` picks the first enabled listener in the scene, falling back to the camera so existing scenes keep working. Up vector taken from the listener transform, not a constant.
- Attenuation: `enum AttenuationModel { NONE, INVERSE, INVERSE_CLAMPED, LINEAR, LINEAR_CLAMPED, EXPONENT, EXPONENT_CLAMPED }` with `minDistance`, `maxDistance`, `rolloff` per component. The gain function is implemented in pure Kotlin (`AttenuationCalculator`, mirrors OpenAL formulas) so it is unit-testable and is also used for voice audibility ranking (3.3). The backend sets the equivalent `alDistanceModel` and per-source params.
- Doppler: velocity = (position − previousPosition) / dt computed in the system from the transform, or taken from the entity's physics body when one exists (via a small read-only `VelocityProvider` the physics domain can supply; request `NEEDS PHY` at that point if required). `alDopplerFactor` / `alSpeedOfSound` exposed in `AudioConfig`.
- Stereo clips cannot be spatialised by OpenAL: 3D components with a stereo buffer log a one-time warning (and optionally downmix at load).
- Pan correctness tested via a pure helper (listener basis + source position → azimuth) in addition to attenuation tests.

### 3.6 Events

Drop the unused name-based events or replace them with entity-based ones (`AudioClipStarted(entityId)`, `AudioClipFinished(entityId)`) published by `VoiceManager`; decide in AUD-04 (kept until then).

### 3.7 Bug fixes folded into the tasks

- D7 (`AL_POSITION` rewind) – fix in AUD-02 (first change touching `SoundSource`).
- D10 (device leak on failed init, `outputDevice`) – AUD-02 (with `AudioConfig.outputDevice`).
- D9 loader cleanup (double logging, WAV format coverage) – AUD-04.

## 4. Task mapping

| Task | Delivers |
|------|----------|
| AUD-02 | `AudioBackend` + `OpenAlBackend`, `AudioConfig`, `Mixer`/`AudioBus`, settings → config wiring, live volume/mute, D1, D6, D7, D10, D11. Tests: mixer math, config mapping, system with a fake backend. |
| AUD-03 | `AudioListener`, attenuation models + calculator, Doppler/velocity, `SourceParams` spatial fields, D3, D8. Tests: attenuation formulas, pan helper. |
| AUD-04 | `VoiceManager` (cap, priority stealing, pooling), `AudioComponent` extensions (one-shot/loop/random container, state), `OggStreamDecoder`/`StreamingSource`, D2, D4, D5, D9, D12. Tests: stealing policy, random container, stream refill logic with a fake decoder. |
| AUD-05 | EFX reverb zones and low-pass per zone/bus via `AudioBackend` effect-slot methods. |

## 5. Assumptions and risks

- Serialisation: all new `AudioComponent`/`AudioSettings` fields are defaulted so existing scenes and settings files load unchanged.
- Windows-only natives: OpenAL Soft (bundled with LWJGL) supports EFX and `ALC_ENUMERATE_ALL_EXT`; guard with capability checks.
- Editor impact (inspector for the new component fields, audio settings panel for UI bus, mute) belongs to `skate-editor`: `NEEDS EDT` will be raised in AUD-02/04.
