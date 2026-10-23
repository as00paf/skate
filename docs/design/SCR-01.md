# SCR-01 — Scripting architecture

Status: **proposed, awaiting lead approval.** No code is written until approved.

## Goals

- One language-agnostic script API; Kotlin Script (`.kts`) first, TypeScript (SCR-04) and Lua (SCR-05) reuse it unchanged.
- Scripts only see the public API (`engine/scripting/api`). No GL, Bullet, ImGui or editor types leak.
- Script errors never crash the editor or game; they are reported with file and line.
- Engine stays independent from `editor.*`; physics is reached via `IPhysics3D`/`Physics3DFactory` only.

## Current state

`NativeScriptComponent` holds four `@Transient` Kotlin lambdas (`onInit/onUpdate/onReset/onDestroy`); `ScriptingSystem` calls `update(dt)` on each. It cannot be serialized or hot-reloaded, has no fixed update, and exposes the raw `GameObject`.

Decision: keep `NativeScriptComponent` unchanged (in-engine Kotlin code, used by prefabs). Add a new serializable `ScriptComponent` for source-based scripts; `ScriptingSystem` drives both.

## Package layout (`engine/scripting/**`)

```
scripting/
  api/        public script-facing API (the only thing scripts import)
  host/       ScriptHost, ScriptFactory, ScriptHostRegistry, ScriptError
  runtime/    ScriptRuntime, ScriptContextImpl, engine bindings (internal)
```

## Lifecycle

```kotlin
interface Script {
    fun onCreate(ctx: ScriptContext) {}
    fun onUpdate(ctx: ScriptContext, dt: Float) {}
    fun onFixedUpdate(ctx: ScriptContext, dt: Float) {}
    fun onDestroy(ctx: ScriptContext) {}
}
```

Per frame: `onCreate` for newly started scripts, then fixed steps (`onFixedUpdate`, 0..n), then `onUpdate`. `onDestroy` runs on play stop or entity removal, in reverse creation order. Every call is wrapped: a failing script is disabled and reported without affecting others.

## ScriptHost (one per language)

```kotlin
interface ScriptHost {
    val language: ScriptLanguage              // KOTLIN, TYPESCRIPT, LUA
    val fileExtensions: Set<String>           // "kts", "ts", "lua"
    fun load(source: ScriptSource): ScriptLoadResult    // Success(ScriptFactory) | Failure(errors)
    fun reload(source: ScriptSource): ScriptLoadResult  // SCR-03; keep state where possible
    fun close()
}
interface ScriptFactory { fun instantiate(): Script }   // foreign hosts adapt to Script
data class ScriptSource(val path: String, val text: String)
data class ScriptError(val path: String, val line: Int?, val column: Int?, val message: String, val cause: Throwable?)
```

`ScriptHostRegistry` maps extension to host. TS/Lua hosts adapt to the same `Script` interface, so `ScriptingSystem` is language-blind.

## Public API (`ScriptContext`)

```kotlin
interface ScriptContext {
    val entity: ScriptEntity
    val scene: ScriptScene      // find(name), findByTag, spawn(prefab), destroy(entity)
    val input: ScriptInput      // key/mouse/gamepad state, action(name) value
    val physics: ScriptPhysics  // raycast(from, to): RayHit?, gravity
    val events: ScriptEvents    // subscribe/publish; auto-unsubscribed on destroy
    val time: ScriptTime        // deltaTime, fixedDeltaTime, elapsed
    val log: ScriptLog          // routed to console tagged with script path
}
interface ScriptEntity {
    val id: Int; var name: String; val transform: ScriptTransform
    fun <T : Any> getComponent(type: KClass<T>): T?   // whitelisted public facades only
    fun hasComponent(type: KClass<*>): Boolean
}
```

- JOML `Vector3f`/`Quaternionf` are allowed in the API (copy adapters for Lua/JS).
- Component access returns facades (`ScriptRigidBody`, `ScriptLight`, ...) via a whitelist registry, so other domains can add facades without touching hosts.
- Events: engine events marked `@ScriptVisible`, plus string-keyed custom events (`emit("name", payload)`) for cross-language use.
- Inspector properties: scripts declare exposed values (name, type, default), stored in `ScriptComponent.properties`.

## Component and system

```kotlin
@Serializable class ScriptComponent(var path: String = "", var enabled: Boolean = true,
    val properties: MutableMap<String, ScriptValue> = mutableMapOf()) : Component()
```

Instance and error state are `@Transient`. `ScriptingSystem` owns a `ScriptRuntime` that resolves the host by extension, instantiates scripts, tracks created/failed state and dispatches lifecycle.

## Errors

`ScriptError` is published as an engine `ScriptErrorEvent`; the editor's `ConsoleWindow` subscribes and shows `path:line`. Compile errors leave the component unloaded; runtime exceptions disable that instance until reload. Nothing propagates to the engine loop.

## Sandbox

v1 runs Kotlin scripts in-process with the compile classpath limited to `scripting/api`, stdlib and JOML. Full isolation is a non-goal (documented limitation).

## Testing

Unit tests with a fake `ScriptHost`: lifecycle ordering, error isolation, host selection, bindings with fake scene/input/physics. Architecture guard: `scripting/api` imports no `org.lwjgl`, `com.jme3`, `imgui`, or `com.pafoid.skate.editor`.

## Cross-domain needs (reported to lead)

- ECS: fixed-update hook in `System`/`SystemManager`; register `ScriptComponent` in `ComponentType`/`Serializer`.
- PHY: hit-info raycast (point, normal, entity); `IPhysics3D.raycastClosest` returns only a distance.
- INP: engine-level read API for key/action state independent of editor.

## Task mapping

SCR-02 `KotlinScriptHost` + API bindings; SCR-03 `reload` + console reporting; SCR-04/05 JS/Lua hosts.
