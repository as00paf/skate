# Script API quick reference and snippets

Same API in every language; `ctx` is the first parameter of each lifecycle function.
The editor's completion popup (EDT-26) shows these entries, generated from `CompletionProvider.apiReference()`.

| Member | Meaning |
|---|---|
| `ctx.entity` (`id`, `name`, `transform.position/rotation/scale`, `getComponent`, `hasComponent`) | The entity the script is attached to |
| `ctx.scene.find(name)` / `findAll(name)` | Look up other entities |
| `ctx.input` (`isKeyDown`, `isKeyPressed`, `isKeyReleased`, `isMouseButtonDown`, `action`, `isActionDown`, `wasActionPressed`, `pushContext`, `popContext`) | Input; key codes are GLFW codes, e.g. Space = 32. `pushContext(id, priority, blocking)` puts an input context on the stack (`blocking`: `"none"`, `"consume"` or `"all"`; Lua/TS default to `0, "consume"`) and returns a handle for `popContext`. The runtime pops a script's contexts when it is destroyed, hot reloaded or fails (INP-10). |
| `ctx.physics` (`gravity`, `raycast`) | Physics queries |
| `ctx.events` (`on`, `emit`) | Custom string-keyed events |
| `ctx.time` (`deltaTime`, `fixedDeltaTime`, `elapsed`) | Timing |
| `ctx.log` (`info`, `warn`, `error`) | Writes to the console Engine tab |
| `ctx.random` (`nextInt(until)`, `nextInt(from, until)`, `nextFloat`, `range(min, max)`, `chance(p)`, `seed`, `stream(name)`, `reseed(seed)`) | Seeded random (ECS-14). `ctx.random` itself is this script's own stream (named `script.<FileName>`); `stream(name)` is a named stream shared by every script and independent of all others. The same seed (Project Settings > Random, `-Dskate.seed`, or `randomSeed` in `game.settings.json`) replays the same values on every Play run |
| `ctx.properties` / `ctx.state` / `ctx.reloadCount` | Inspector values / values kept across hot reloads / reload counter |

## Snippets (also offered by completion: type `onUpdate`, `if`, `for`)

Kotlin (`.kts`):
```kotlin
onCreate { ctx -> ctx.log.info("hello ${ctx.entity.name}") }
onUpdate { ctx, dt -> ctx.entity.transform.position.x += dt }
```

TypeScript (`.ts`):
```ts
export function onCreate(ctx: any) { ctx.log.info("hello " + ctx.entity.name); }
export function onUpdate(ctx: any, dt: number) { ctx.entity.transform.position.x += dt; }
```

Lua (`.lua`):
```lua
function onCreate(ctx) ctx.log.info("hello " .. ctx.entity.name) end
function onUpdate(ctx, dt) ctx.entity.transform.position.x = ctx.entity.transform.position.x + dt end
```

Components: Kotlin `ctx.entity.getComponent<ScriptRigidBody>()`; TypeScript/Lua `ctx.entity.getComponent("RigidBody")`.
