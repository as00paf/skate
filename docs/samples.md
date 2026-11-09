# Sample projects

## Default project (`New Project` in the editor)

`ProjectManager.createProject` copies the engine-bundled assets into `Assets/EngineDefaults` and calls `PrefabsGenerator.createDefaultScenes`, which writes two scenes: `MainScene` (the project's default scene, below) and `FeatureShowcase` (next section). Both scenes open with the project; `MainScene` is active. Keep them current with engine features; old projects are not migrated (GameEngine.md rule 8).

| Part | Contents |
|------|----------|
| Scene components | `ScenePhysicsComponent` (gravity, debug draw off: toggle it to see colliders), `GridLines`, `EnvironmentComponent` with the bundled HDRI sky, `AmbientLightComponent`, `DayNightCycleComponent`, `DirectionalLightComponent` (sun). |
| `Camera` | Default `CameraComponent` with a `FollowCameraComponent` (GPL-05) on `Skater`: it follows and orbits the skater, blends the walk/ride framing, pulls in when its spring arm hits an obstacle and shakes on hard impacts. Tune it in Properties (docs/camera.md). Its `CameraCycleComponent` (GPL-22) cycles `Camera` → `FirstPersonCamera` → `FixedCamera` from the HUD `CameraButton` or the `camera.cycle` action (C, gamepad Back). |
| `FirstPersonCamera`, `FixedCamera` | `FirstPersonCamera`: 70° camera at the skater's eyes (`FirstPersonCameraComponent`). `FixedCamera`: a static camera at (5, 3.5, 7) looking at the spawn. |
| `Skateboard`, `Skater` | Bundled board and character prefabs (`Skateboard`, `Skater` with the default animation set). |
| `Floor` | 20 x 20 m static asphalt slab, top surface at y = 0, box collider. |
| `Concrete_Ledge` | Static concrete ledge at (3, 0.25, -3) with a box collider, to grind. |
| `QuarterPipe`, `Funbox` | GPL-04 prefabs: a wooden quarter pipe 6 m behind the spawn (riding face toward the skater) and a concrete funbox at (-4, 0, 4). They also give the follow camera's spring arm something to collide with. |
| `HUD` | In-game UI canvas: `ScoreText` top-left (`hud.score`, "Score 0") and `CameraButton` top-right (`hud.camera`), which cycles the cameras. Both keys exist in the bundled game string tables. Restart is in the Esc pause menu. |

No 2D sprites or test cubes. Feature demos live in `FeatureShowcase`, not in `MainScene`.

## Feature showcase (`FeatureShowcase`)

A manual regression suite: one labelled area per engine feature in a single scene, built only from public engine components. Every new editor project contains it (switch to the `FeatureShowcase` scene tab), and the CLI writes it as a stand-alone sample:

```
skate sample --dir <folder>        # creates <folder>/FeatureShowcase (same as `skate sample feature-showcase`)
skate validate <folder>/FeatureShowcase
skate run <folder>/FeatureShowcase --frames 120
```

One builder serves both: `FeatureShowcase` in `src/main/kotlin/com/pafoid/skate/game/samples/`. The editor calls it from `PrefabsGenerator.createDefaultScenes` with assets loaded; the CLI's `ShowcaseProjectBuilder` calls it with a path-only `PrefabsGenerator(engine, loadAssets = false)` (headless). **When a feature lands, add an area there and a row below** (GameEngine.md rule 8).

`FeatureShowcaseSmokeTest` (QA-13, `src/test/kotlin/com/pafoid/skate/game/samples/`) creates a new project, loads this scene from disk and plays 5 s with the real systems, then checks each area's visible effect (particles alive, bodies settled, joints moving, car on its wheels, animation time advancing, audio sources started, every legend button flying the camera). **A new area also adds a `Showcase_<Area>_<Effect>` test there**, so a demo that ships inert fails `gradlew test`.

Layout: a 60 x 40 m grey floor. The default camera starts at an overview of every pad, from (0, 32, 55) looking down at the floor centre. Each area has a coloured ground pad named `[Area] Area`, and every object in it is named `[Area] ...`, so the Hierarchy groups them by name and selecting the pad highlights the area. Objects are top-level because physics and animation only drive top-level objects. The UI overlay legend (top-left) has one button per area plus **Overview**. In **Play** (editor or game), clicking one smoothly flies the camera to frame that area's pad from the +Z side, 35 degrees down; **Overview** returns to the start view. Press **Play** for anything that moves.

**Free-fly in Play** (`FreeFlyCameraComponent` on `Camera`), like the editor camera: W/S move along the view, A/D strafe, E/Q go up and down, Shift is 4x faster, and right-mouse drag over the game view looks around. Any fly or look input cancels a focus glide, so you can click an area and keep flying from there.

Both components are driven by the engine's `CameraControlSystem`, in editor Play and in the standalone game. `CameraFocusComponent` holds the named viewpoints. The system binds each one to its runtime UI button by name, looking the button up in the scene, and moves the camera. Motion uses `CameraMotion`, the EDT-34 math the editor camera also uses: units per second, exponential smoothing (sharpness 4/s) and frame delta clamped to 0.1 s. Views are built with `CameraFocusTarget.lookingAt`, which uses `Transform.lookAt`. The camera's rotation is the view rotation, so **positive X pitch looks down**. Never hand-write camera angles in samples.

| Area (position) | What to look for |
|------|------------------|
| `Lights` (back row, far left) | Red and blue point lights tint the sphere and pad from opposite sides; a green spot light makes a circular pool with a soft edge. No flicker or clipping as the camera moves. |
| `Shadows` (back row, centre left) | Sun (the `Sun` entity, linked to the day/night cycle) cascaded shadows from `Caster` and `TallPillar`, stable while the camera moves (no shimmer, acne or peter-panning). `NoCaster` (cast shadow off) casts none. The spot light above `SpotTarget` and the warm point light near `Caster` each cast their own shadow (REN-07). `Group` has no mesh; its child cube `GroupChild` and grandchild sphere `GroupGrandchild` cast shadows, and moving `Group` moves both shadows (REN-20). |
| `Materials` (back row, centre right) | A chrome sphere `Mirror` (back left) that mirrors the sky and clouds (image-based lighting, REN-27); five gold spheres from rough/dielectric to smooth/metallic, whose sky reflections sharpen as they get smoother; an emissive orange cube that glows (bloom); an asphalt-textured cube; a translucent blue cube (alpha blend); a jade sphere `Asset` whose material comes from `Assets/Materials/ShowcaseJade.skmat` (EDT-17: edit that file, reopen the scene, and the sphere follows). |
| `Particles` (back row, far right) | Play: `Fire` (additive, orange to red), `Smoke` (alpha, grows and fades), `Sparks` (a 60-particle burst every 1.5 s, falling under gravity), `Fountain` (blue jet that arcs back down). |
| `Physics` (middle row, far left) | Play: five stacked boxes settle without jitter or tunnelling, the ball drops and rolls, the `Crate` and `Barrel` prefabs stay upright and can be knocked over. Turn on debug draw in the scene's `ScenePhysicsComponent` to see colliders. |
| `Joints` (middle row, centre left) | Play: `HingePendulum` swings in its plane within about +/-69 degrees; `ConeTwistLamp` swings and circles under its pivot; `SliderBlock` drops 2 m along a vertical rail and stops. All are anchored to the world. |
| `Vehicle` (middle row, centre right) | Play: the blue `Car` (`VehicleComponent`, four raycast wheels, PHY-09) drops onto its suspension, bounces once or twice and rests level. It is not driven: scripts cannot set throttle/steering yet. |
| `Audio` (middle row, far right) | Play: `Emitter3D` (the cyan sphere) plays the bundled song `Assets/EngineDefaults/Sounds/Music/aquarela do brasil.mp3` on loop (AUD-10). It is positional: streamed on the music bus and downmixed to mono, so it gets louder from 25 m down to 1 m and pans as the camera moves around it. `playOnAwake` (the default) starts it on Play; it is silent while editing, and Stop silences it. |
| `Prefabs` (front row, left) | The prefab library (GPL-04): ledge, hubba, manual pad, grind box, funbox, rail, handrail, kinked rail, 3- and 5-stair sets, kicker, bank, quarter pipe, crate, barrel and bench, in concrete and two woods. Each is static with its hull colliders (debug draw shows them). |
| `Netcode` (front row, centre) | Play: client-side prediction (NET-03) with the kinematic skater step (GPL-17). A server and two clients run in-process over a simulated link (120 ms one way). The rider pushes, carves a circle and ollies every 2.5 s. **Green ghost** = server state; **blue** = the owning client's prediction, which moves on its own inputs at once and stays just ahead of the ghost; **orange** = the copy another client interpolates, visibly behind. Select `[Netcode] Session` to change `Latency` or `Packet Loss` in Play (the session restarts) and read `Prediction Error` / `Interpolation Lag`. Under loss an input can be dropped in all its redundant copies; the blue box is then corrected and eased back onto the server path over 0.1 s. |
| `Animation` (front row, right) | The bundled character looping the walking clip in place. |
| `Fog` (front row, far right) | Eight orange `Post_*` pillars recede along the right edge of the floor. In Play, the overlay's **Cycle fog** button (top right) steps the scene fog through off, linear, exp, exp² and exp² with height fog. Models, the floor, the sky and particles fog alike. The sky fogs like geometry at the far plane, and height fog keeps the sky clear overhead. In the editor, set the same options on the scene's `EnvironmentComponent` (`renderFog`, `fogMode`, `fogDensity`, `fogStart`/`fogEnd`, `fogHeight`, `fogHeightFalloff`) (REN-09). |
| `UI` (screen overlay) | In-game (non-ImGui) canvas: title and hint, the area legend (Overview plus one focus button per area), and bottom-right a tinted panel, formatted text ("Formatted text: 42"), a button with solid hover/press backgrounds (GUI-07), a 60% progress bar and a draggable slider. No `!!key!!` text; the layout keeps its corners when the window is resized. |

Overlay strings are `showcase.*` keys in the bundled `assets/strings/game/showcase` table (EN and FR), which the editor merges too; projects' own `Assets/strings.properties` are not loaded by the engine yet.

### Not covered yet

- **Ragdoll**: `RagdollComponent` needs bone bodies from `RagdollBuilder`, which nothing calls from a scene yet.
- **Driving the vehicle**: no script or input binding sets `VehicleComponent` throttle/steering.
- **Area labels in 3D**: there is no world-space text, so areas are labelled by name and by the overlay legend buttons.
- **CLI sample animation**: the headless CLI cannot load the character's skeleton, so in `skate sample` output the character stays in bind pose; the editor's default project animates.
- **Post-processing toggles** and **fog** have no dedicated area yet.

## Marble Run (`MarbleRun`)

A physics-heavy sample generated by `MarbleRunProjectBuilder` (`skate sample marble-run --dir <folder>`).
Open `MarbleRun.skateproject`, scene `Run`, and press Play.

What to look for:

- Eight marbles (CCD enabled) drop onto three zig-zag ramps; each ramp should drain onto the high end of the next without marbles tunnelling through the ramps or ground.
- `Pendulum` (hinge joint anchored to the world, limited to about +/-57 degrees) swings when marbles hit it and never leaves its limits.
- `Pusher` (slider joint anchored to the world, travel +/-2 on X) is shoved along its axis and stays on it; it does not rotate or fall.
- Marbles end up in the bin between `BinLeft` and `BinRight` or on the ground; none fall through the world.

Not covered yet: **triggers** (a goal/kill-zone volume), **camera follow** and **restitution** (bounciness) are not exposed as public components, so the scene uses a fixed camera and no goal detection. Revisit when those land.

## Tetris (`Tetris`)

A scripting, input, audio, UI and multi-scene sample generated by `TetrisProjectBuilder` (`skate sample tetris --dir <folder>`).
Open `Tetris.skateproject`, scene `Game` (both `Game` and `GameOver` must be open), and press Play. The rules are the Kotlin script `Assets/Scripts/tetris.kts` on `TetrisController`; it moves a pool of `Cell_*` cube entities (200 board cells and 4 for the active piece). `GameOver` has its own script, `gameover.kts`.

Controls: Left/Right move, Up rotate, Down soft drop, Space hard drop, R restart (also from the game-over screen).

Script API used (TLG-06): `ScriptAudioSource` (`SfxLock`, `SfxClear`, `SfxOver`), `ScriptMaterial` (per-piece colors), `ScriptUiText` (HUD `ScoreText`, string key `tetris.hud` in `Assets/strings.properties`) and `ctx.scene.load("GameOver")`.

What to look for:

- Pieces spawn, fall, rotate and lock inside the walls; each piece type has its own color and locked cells keep it.
- A blip plays on lock, a higher one on line clear; the HUD shows score, lines and level; speed rises with the level.
- When the stack reaches the top a low sound plays and the `GameOver` scene (title and hint) opens; R returns to `Game` and restarts.

## Moving and exporting samples (TLG-14)

Samples written by `skate sample` (and the MCP server) store every asset reference relative to the project folder,
as the editor does (AST-09). A sample folder can be moved, and `skate export <project> --out <folder>` produces a
`game.bin` that loads from any folder. The engine assets copied into a sample are read from the repository, so run the
CLI from the repository root (`gradlew :tools:cli:run` does); elsewhere `skate sample` stops with an error instead of
writing a sample with missing files. `SampleExportTest` builds, exports and reopens every sample from its pack alone.
