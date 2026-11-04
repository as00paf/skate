# Sample projects

## Default project (`New Project` in the editor)

`ProjectManager.createProject` copies the engine-bundled assets into `Assets/EngineDefaults` and calls `PrefabsGenerator.createDefaultScenes`, which writes two scenes: `MainScene` (the project's default scene, below) and `FeatureShowcase` (next section), plus `Assets/tone.wav` for the showcase's audio demo. Both scenes open with the project; `MainScene` is active. Keep them current with engine features; old projects are not migrated (GameEngine.md rule 8).

| Part | Contents |
|------|----------|
| Scene components | `ScenePhysicsComponent` (gravity, debug draw off: toggle it to see colliders), `GridLines`, `EnvironmentComponent` with the bundled HDRI sky, `AmbientLightComponent`, `DayNightCycleComponent`, `DirectionalLightComponent` (sun). |
| `Camera` | Default `CameraComponent` at (0, 3, 10), behind the skater. |
| `Skateboard`, `Skater` | Bundled board and character prefabs (`Skateboard`, `Skater` with the default animation set). |
| `Floor` | 20 x 20 m static asphalt slab, top surface at y = 0, box collider. |
| `Concrete_Ledge` | Static concrete ledge at (3, 0.25, -3) with a box collider, to grind. |
| `HUD` | In-game UI canvas: `ScoreText` top-left (`hud.score`, "Score 0") and `RestartButton` top-right (`pause.restart`). Both keys exist in the bundled game and editor string tables. |

No 2D sprites or test cubes. Feature demos live in `FeatureShowcase`, not in `MainScene`.

## Feature showcase (`FeatureShowcase`)

A manual regression suite: one labelled area per engine feature in a single scene, built only from public engine components. Every new editor project contains it (switch to the `FeatureShowcase` scene tab), and the CLI writes it as a stand-alone sample:

```
skate sample --dir <folder>        # creates <folder>/FeatureShowcase (same as `skate sample feature-showcase`)
skate validate <folder>/FeatureShowcase
skate run <folder>/FeatureShowcase --frames 120
```

One builder serves both: `FeatureShowcase` in `src/main/kotlin/com/pafoid/skate/game/samples/`. The editor calls it from `PrefabsGenerator.createDefaultScenes` with assets loaded; the CLI's `ShowcaseProjectBuilder` calls it with a path-only `PrefabsGenerator(engine, loadAssets = false)` (headless). **When a feature lands, add an area there and a row below** (GameEngine.md rule 8).

Layout: a 60 x 40 m grey floor. The default camera starts at an overview of every pad, from (0, 32, 55) looking down at the floor centre. Each area has a coloured ground pad named `[Area] Area`, and every object in it is named `[Area] ...`, so the Hierarchy groups them by name and selecting the pad highlights the area. Objects are top-level because physics and animation only drive top-level objects. The UI overlay legend (top-left) has one button per area plus **Overview**. In **Play** (editor or game), clicking one smoothly flies the camera to frame that area's pad from the +Z side, 35 degrees down; **Overview** returns to the start view. Press **Play** for anything that moves.

**Free-fly in Play** (`FreeFlyCameraComponent` on `Camera`), like the editor camera: W/S move along the view, A/D strafe, E/Q go up and down, Shift is 4x faster, and right-mouse drag over the game view looks around. Any fly or look input cancels a focus glide, so you can click an area and keep flying from there.

Both components are driven by the engine's `CameraControlSystem`, in editor Play and in the standalone game. `CameraFocusComponent` holds the named viewpoints. The system binds each one to its runtime UI button by name, looking the button up in the scene, and moves the camera. Motion uses `CameraMotion`, the EDT-34 math the editor camera also uses: units per second, exponential smoothing (sharpness 4/s) and frame delta clamped to 0.1 s. Views are built with `CameraFocusTarget.lookingAt`, which uses `Transform.lookAt`. The camera's rotation is the view rotation, so **positive X pitch looks down**. Never hand-write camera angles in samples.

| Area (position) | What to look for |
|------|------------------|
| `Lights` (back row, far left) | Red and blue point lights tint the sphere and pad from opposite sides; a green spot light makes a circular pool with a soft edge. No flicker or clipping as the camera moves. |
| `Shadows` (back row, centre left) | Sun (scene `DirectionalLight`) cascaded shadows from `Caster` and `TallPillar`, stable while the camera moves (no shimmer, acne or peter-panning). `NoCaster` (cast shadow off) casts none. The spot light above `SpotTarget` and the warm point light near `Caster` each cast their own shadow (REN-07). |
| `Materials` (back row, centre right) | Five gold spheres from rough/dielectric to smooth/metallic; an emissive orange cube that glows (bloom); an asphalt-textured cube; a translucent blue cube (alpha blend). |
| `Particles` (back row, far right) | Play: `Fire` (additive, orange to red), `Smoke` (alpha, grows and fades), `Sparks` (a 60-particle burst every 1.5 s, falling under gravity), `Fountain` (blue jet that arcs back down). |
| `Physics` (middle row, far left) | Play: five stacked boxes settle without jitter or tunnelling, the ball drops and rolls, the `Crate` and `Barrel` prefabs stay upright and can be knocked over. Turn on debug draw in the scene's `ScenePhysicsComponent` to see colliders. |
| `Joints` (middle row, centre left) | Play: `HingePendulum` swings in its plane within about +/-69 degrees; `ConeTwistLamp` swings and circles under its pivot; `SliderBlock` drops 2 m along a vertical rail and stops. All are anchored to the world. |
| `Vehicle` (middle row, centre right) | Play: the blue `Car` (`VehicleComponent`, four raycast wheels, PHY-09) drops onto its suspension, bounces once or twice and rests level. It is not driven: scripts cannot set throttle/steering yet. |
| `Audio` (middle row, far right) | Play: a 440 Hz tone (`Assets/tone.wav`). `Emitter3D` is positional (louder and panned as the camera approaches); `Ambient2D` is a quiet non-positional layer. Stopping play silences both. |
| `Prefabs` (front row, left) | The prefab library (GPL-04): ledge, hubba, manual pad, grind box, funbox, rail, handrail, kinked rail, 3- and 5-stair sets, kicker, bank, quarter pipe, crate, barrel and bench, in concrete and two woods. Each is static with its hull colliders (debug draw shows them). |
| `Animation` (front row, right) | The bundled character looping the walking clip in place. |
| `UI` (screen overlay) | In-game (non-ImGui) canvas: title and hint, the area legend (Overview plus one focus button per area), and bottom-right a tinted panel, formatted text ("Formatted text: 42"), a button with solid hover/press backgrounds (GUI-07), a 60% progress bar and a draggable slider. No `!!key!!` text; the layout keeps its corners when the window is resized. |

Overlay strings are `showcase.*` keys in the bundled `game_strings` and `editor_strings` tables (EN and FR); projects' own `Assets/strings.properties` are not loaded by the engine yet.

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
