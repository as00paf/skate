# SkateSim Engine Changelog

This document tracks the development history and major milestones of the SkateSim skateboarding simulation engine.

---

## [v0.45.0.2] - 2026-03-23: Scene Serialization Refactored

### Summary

Refactored scene serialization to use existing LevelManager, removing duplicate SceneSerializer code.
All 15 ECS components remain serializable for level persistence and GameObject copy operations.

### Removed

- **SceneSerializer** - Duplicated LevelManager functionality, not integrated with UI
- **SceneDataWrapper** - Duplicated LevelData purpose
- **Scene.saveScene/loadScene()** - Not called anywhere, LevelManager handles persistence

### Changed

- **LevelManager remains single source of truth** (`game/level/LevelManager.kt`)
  - Handles level save/load with file dialogs
  - Integrated with editor menu bar (File > Save/Open Level)
  - Uses LevelData (gameObjects + SceneData) for persistence

- **Scene class simplified** (`engine/ecs/Scene.kt`)
  - Removed saveScene/loadScene methods
  - Scene initialization handled by SceneManager and SceneInitializer

- **GameObject.copy() for object-level operations** (`engine/ecs/GameObject.kt`)
  - Used by ClipboardService for copy/paste
  - Used for prefab operations
  - Uses Serializer directly for GameObject JSON encode/decode

- **Unit tests refocused** (`test/.../ecs/serialization/GameObjectSerializationTest.kt`)
  - 7 tests for GameObject serialization
  - Tests Transform, component polymorphism, file operations
  - Tests GameObject.copy() functionality

### Architecture Clarification

- **Level** = Persisted file format (LevelData: gameObjects + SceneData)
  - Saved/loaded via LevelManager
  - Accessed through editor menu (File > Save/Open Level)
  
- **Scene** = Runtime ECS container
  - Manages GameObjectManager, SystemManager, Physics, Camera
  - Not directly serialized
  
- **GameObject** = Serializable entity
  - Can be serialized individually for clipboard/prefabs
  - Uses Serializer.encode/decode directly

### Verified

- ✅ Build successful with no errors
- ✅ 7/7 GameObject serialization tests passing
- ✅ LevelManager integration with UI confirmed
- ✅ No duplicate serialization code
- ✅ Clear Level vs Scene distinction

---

## [v0.45.0.1] - 2026-03-23: Asset Management Pipeline Enhancement
