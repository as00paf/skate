# Script API quick reference and snippets

Same API in every language; `ctx` is the first parameter of each lifecycle function.
The editor's completion popup (EDT-26) shows these entries, generated from `CompletionProvider.apiReference()`.

| Member | Meaning |
|---|---|
| `ctx.entity` (`id`, `name`, `transform.position/rotation/scale`, `getComponent`, `hasComponent`) | The entity the script is attached to |
| `ctx.scene.find(name)` / `findAll(name)` | Look up other entities |
| `ctx.input` (`isKeyDown`, `isKeyPressed`, `isKeyReleased`, `isMouseButtonDown`, `action`) | Input; key codes are GLFW codes, e.g. Space = 32 |
| `ctx.physics` (`gravity`, `raycast`) | Physics queries |
| `ctx.events` (`on`, `emit`) | Custom string-keyed events |
| `ctx.time` (`deltaTime`, `fixedDeltaTime`, `elapsed`) | Timing |
| `ctx.log` (`info`, `warn`, `error`) | Writes to the console Engine tab |
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
