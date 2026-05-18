# Development Guardrails

## Mandatory Architecture Contracts

1. **Editor mutation pipeline:** `UI -> Event -> ActionHandler -> Command -> UndoRedoManager`
2. **Play-mode mutation gate:** editor mutations are blocked during play mode except explicit runtime-allowed controls.
3. **Command semantics:** use correct command category (`UNDOABLE`, `EXECUTE_ONLY`, `ASYNC`) and history behavior.
4. **ECS invalidation:** system eligibility/cache invalidation must react to object-set and component-composition changes.
5. **Layering:** `engine/**` must not import `editor/**`.
6. **Localization:** no hardcoded user-facing UI strings; use string keys/resources.

## Allowed / Forbidden Patterns

### Allowed

- UI publishes typed events/actions; handlers perform mutations via commands.
- Constructor DI (Koin wiring in module/bootstrap).
- Engine-owned boundary interfaces for engine/editor integration.
- Command execution through `UndoRedoManager`.

### Forbidden

- Direct scene/runtime mutation in UI entrypoints.
- Direct `UndoRedoManager.executeCommand(...)` calls from UI windows/menus/search providers.
- `engine/**` imports from `editor/**`.
- Service-locator style dependency access in engine runtime paths.
- Hardcoded user-facing literals in UI rendering paths.

## Test Guardrails by Change Type

| Change type | Minimum guard tests |
|---|---|
| UI mutation entrypoints (`editor/ui/**`, menus, search, toolbar, project window) | `UiMutationPipelineGuardTest` + targeted handler/command tests |
| Engine/editor boundary changes (`engine/**`, adapters) | `EngineLayeringGuardTest` + affected subsystem tests |
| Command lifecycle (`UndoRedoManager`, async/sync commands) | `UndoRedoManagerTest` + targeted command tests (`CreateSceneCommandTest`, `OpenSceneCommandTest` when relevant) |
| ECS mutation/invalidation (`GameObject`, `Scene`, `SystemManager`, eligible systems) | `SystemManagerInvalidationTest` + adjacent system tests |
| Play-mode gating behavior | targeted mutation-gate tests in `UndoRedoManagerTest` and affected handlers |

## Documentation Update Triggers

Update docs when any of the following changes:

1. Mutation pipeline contract or command semantics.
2. Engine/editor boundary interfaces or ownership.
3. Guard-test scope or mandatory quality gates.
4. ECS invalidation behavior/contracts.
5. Canonical development workflow for active contributors.

Required updates:

- `docs/ECS_ARCHITECTURE.md` for architecture behavior/flow changes.
- `docs/DOCS_INDEX.md` for documentation navigation/source-of-truth changes.
- `QWEN.md` for high-level project status/navigation references.

## Canonical Links

- Documentation index: `docs/DOCS_INDEX.md`
- Core contracts ADR: `docs/ADR-ARCH-002-core-contracts.md`
- ECS architecture: `docs/ECS_ARCHITECTURE.md`
- Historical milestones: `docs/CHANGELOG.md`
