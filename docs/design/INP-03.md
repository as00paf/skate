# INP-03 — Action maps and rebinding

API in `engine/input/ActionMap.kt` (no editor/LWJGL imports; guarded by `InputReaderTest`).

- `ActionDefinition(id, context, category, displayKey, defaults)`; contexts `GAMEPLAY`, `UI`, `EDITOR`. `DefaultActions.register(map)` defines gameplay (`moveX`, `moveY`, `lookX`, `lookY`, `jump`, `sprint`, `crouch`) and UI navigation actions. The editor registers its own `EDITOR` actions with `map.define(...)`.
- `ActionBinding` (serializable): `Key`, `MouseButton`, `GamepadButton`, `GamepadAxis`; `scale` < 0 gives the negative half of an analog action. `sourceId()` is the conflict identity (axis direction included).
- `ActionMap`: `bindings`, `findConflicts`, `rebind(id, index|null, binding, ConflictResolution)` returning `RebindResult` (`Success`/`Conflict`/`Invalid`), `addBinding`, `removeBinding`, `clearBindings`, `reset`/`resetContext`/`resetAll`, `toProfile`/`loadProfile`, `revision`. Conflicts are checked within one context only. Resolutions: `REJECT`, `UNBIND_OTHERS`, `SWAP`.
- `BindingProfile` (name, version, overrides only) with `toJson`/`fromJson` for import/export.
- Persistence: `InputMappings.bindings` (per project, with `deadZones`). `InputSystem.mappings = x` loads it; after editing `inputSystem.actionMap` call `mappings.storeBindings(actionMap)` (inside an undoable command).
- Runtime: `InputSystem` reads gameplay input only through the action reader (keyboard now works); `InputSystem.reader` is the script-facing read API; `activeContexts` selects contexts (GAMEPLAY and UI by default).
