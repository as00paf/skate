# ARCH-017 DI/Layering Decision Checkpoint

- **Task:** ARCH-017
- **Owner:** tech-lead
- **Status:** done
- **Date:** 2026-05-17
- **Prepares:** ARCH-018

## 1) Decision Summary

**Decision:** ✅ **APPROVED with explicit constraints for ARCH-018 implementation.**

ARCH-018 may proceed only under the DI and layering rules defined in this checkpoint.

---

## 2) Target DI Pattern by Subsystem

### 2.1 Engine runtime/core (`engine/**`)

- **Required pattern:** constructor DI only.
- **Not allowed:** `KoinComponent`, `by inject()`, service-locator lookup, or direct manual construction of runtime services inside entities/scenes.
- **Applies to ARCH-018 scope:** `Scene.kt`, `BulletPhysics3D.kt`.

### 2.2 Editor orchestration (`editor/ui/handlers/**`, editor services)

- **Required pattern:** constructor DI only.
- **Allowed exception:** Koin bootstrap call for subscription lifecycle (e.g., `.also { it.init() }`) remains acceptable.
- **Not allowed:** field/property injection via `KoinComponent` in handlers.
- **Applies to ARCH-018 scope:** `ViewportActionHandler.kt`.

### 2.3 Data/event/value types

- Must remain DI-free.
- Manual creation allowed for immutable values, commands, and event payloads.

---

## 3) Engine/Editor Boundary Policy

1. `engine/**` **must not import** `editor/**` (hard rule).
2. Editor may depend on engine contracts and runtime types.
3. Event classes are split by ownership:
   - engine-owned events in `engine/events/**`
   - editor action events in `editor/events/**`
4. Event payloads crossing boundary must use engine-safe contracts or primitives:
   - no editor UI package types in engine-owned APIs.
5. `ViewportAction` remains editor-owned; payload types used by engine-facing command paths must be contract-safe (move/replace editor-UI-only enum usage).

---

## 4) ARCH-018 Phased Migration Strategy

### Phase A — DI normalization in editor handler

- Convert `ViewportActionHandler` to constructor DI.
- Update `KoinModule.kt` registration to pass dependencies explicitly.
- Remove `KoinComponent` / `inject()` usage from handler.

### Phase B — Scene/physics decoupling

- Remove direct `BulletPhysics3D()` construction from `Scene.kt`.
- Inject physics world creation through a dedicated factory contract (constructor-supplied), so each scene owns its own physics instance without hardcoding implementation.
- Register factory wiring in `KoinModule.kt`.

### Phase C — Physics backend DI cleanup

- Convert `BulletPhysics3D` to constructor DI for logger/debug/native loader dependencies.
- Replace inline `NativeLibraryLoader()` creation with injected dependency/factory.

### Phase D — Event boundary cleanup

- Update `ViewportAction` payload type usage to avoid editor-UI-only coupling for shared flows (notably prefab spawn payload).
- Apply required updates to related handler/command boundaries.

---

## 5) Migration Map (ARCH-018 File Guidance)

| File | Current issue | ARCH-018 target |
|---|---|---|
| `src/main/kotlin/com/pafoid/skate/engine/ecs/Scene.kt` | Manual `BulletPhysics3D()` construction | Constructor-injected physics factory/contract |
| `src/main/kotlin/com/pafoid/skate/engine/physics3d/BulletPhysics3D.kt` | `KoinComponent` + property injection + inline native loader construction | Constructor DI only; injected native loader dependency |
| `src/main/kotlin/com/pafoid/skate/editor/ui/handlers/ViewportActionHandler.kt` | `KoinComponent` + property injection | Constructor DI only; explicit Koin wiring |
| `src/main/kotlin/com/pafoid/skate/editor/events/ViewportAction.kt` | Prefab payload currently tied to editor UI enum location | Contract-safe prefab payload type for boundary-safe handling |
| `src/main/kotlin/com/pafoid/skate/app/KoinModule.kt` | Mixed implicit/manual wiring in ARCH-018 scope | Explicit constructor wiring + scene physics factory registration |

Likely adjacent updates during ARCH-018:
- prefab type contract location/usages
- scene creation call sites (e.g., scene manager/initializers) to pass factory dependency

---

## 6) Risks and Mitigations

1. **Risk:** Physics lifecycle ownership regressions (shared or leaked physics worlds).
   - **Mitigation:** enforce factory-per-scene creation and scene destroy cleanup verification.
2. **Risk:** Koin graph breakage from constructor signature changes.
   - **Mitigation:** apply phased wiring and compile after each phase.
3. **Risk:** Event payload refactor causes subscriber mismatch.
   - **Mitigation:** migrate payload type + all subscribers in the same commit slice.
4. **Risk:** Hidden reintroduction of service locator.
   - **Mitigation:** ban `KoinComponent` in ARCH-018 touched classes.

---

## 7) ARCH-018 Readiness Acceptance Criteria

ARCH-018 is ready to start when the implementer agrees to all of the following:

1. `Scene.kt`, `BulletPhysics3D.kt`, `ViewportActionHandler.kt` use constructor DI only.
2. No `KoinComponent` remains in those classes.
3. `Scene.kt` no longer directly constructs `BulletPhysics3D`.
4. Engine/editor layering remains clean (`engine/**` imports no `editor/**`).
5. `ViewportAction` boundary payloads are contract-safe (no editor-UI-only type leakage in shared flow contracts).
6. `KoinModule.kt` explicitly wires all new constructor deps/factories.
7. Compile succeeds: `cmd.exe /c gradlew.bat compileKotlin --no-daemon`.

---

## 8) Implementation Checklist for ARCH-018

- [ ] Convert `ViewportActionHandler` to constructor DI and update module wiring.
- [ ] Introduce and wire a scene physics factory contract; remove direct physics instantiation in `Scene`.
- [ ] Convert `BulletPhysics3D` to constructor DI; inject native loader dependency.
- [ ] Refactor prefab/event payload contract boundary and update consumers.
- [ ] Run compile and targeted impacted tests.
