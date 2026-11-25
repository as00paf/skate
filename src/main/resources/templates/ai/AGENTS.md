# {{projectName}}: notes for AI coding agents

{{projectName}} is a game project for the Skate engine, created from the `{{template}}` template. A Skate project is
data: scenes, assets and scripts. There is no Gradle build and no `src/` folder in it.

## Layout

| Path | What it is |
|---|---|
| `{{projectName}}.skateproject` | Project settings (JSON): name, default scene, scene list, graphics and physics backend, target FPS, splash image, languages, input mappings. |
| `Scenes/*.scene` | Scenes (JSON). The editor writes them; the first one in `scenesPath` is not necessarily the default, `defaultScene` is. |
| `Assets/<Type>/` | Project assets in one folder per type: `Models`, `Animations`, `Textures`, `Materials`, `Sounds`, `Scripts`, `Shaders`, `Fonts`, `Data`, `Prefabs`, `Items`. |
| `Assets/strings.properties` | The game's translated strings (`key=value`, plus `strings_<lang>.properties`). |
| `Builds/` | Export output. Never edit or commit it. |

Asset references in project files are project-relative (`Assets/Textures/wall.png`) or engine references
(`engine:Models/cube.obj`, the engine's bundled defaults). Never write absolute paths into a scene or project file.

## Rules

- Do not edit `.scene`, `.prefab` or `.skateproject` files by hand while the Skate editor has the project open: the
  editor overwrites them on save. Close the project first, or make the change in the editor.
- Every `.meta` file belongs to its asset (it holds the asset's id). Move, rename or delete it together with the asset.
- Game logic lives in scripts under `Assets/Scripts/` (Kotlin `.kts`, TypeScript `.ts` or Lua `.lua`), attached to an
  entity with a `ScriptComponent`. The editor hot-reloads them in Play.

## Scripts

Each lifecycle function receives `ctx`:

```kotlin
onCreate { ctx -> ctx.log.info("hello ${ctx.entity.name}") }
onUpdate { ctx, dt -> ctx.entity.transform.position.x += dt }
```

| Member | Meaning |
|---|---|
| `ctx.entity` | The entity the script is on: `name`, `transform.position/rotation/scale`, `getComponent`, `hasComponent`. |
| `ctx.scene.find(name)` / `findAll(name)` | Other entities. |
| `ctx.input` | `isKeyDown`, `isKeyPressed`, `action`, `isActionDown`, `wasActionPressed` (GLFW key codes, Space = 32). |
| `ctx.physics` | `gravity`, `raycast`. |
| `ctx.events` | `on`, `emit`: string-keyed events between scripts. |
| `ctx.time` | `deltaTime`, `fixedDeltaTime`, `elapsed`. |
| `ctx.random` | Seeded random: the same project seed replays the same values. |
| `ctx.log` | `info`, `warn`, `error` (the editor console). |
| `ctx.properties`, `ctx.state` | Inspector values; values kept across hot reloads. |

## Headless tools

The Skate CLI works on this folder without opening a window:

```
skate validate .                 # checks the project file, every scene and the assets folder
skate run . --frames 120         # runs the default scene headless and prints engine stats
skate pack .                     # packs the assets into Builds/
```

Run `skate validate .` after changing project files.
