# SkateSim Engine Changelog

This document tracks the development history and major milestones of the SkateSim skateboarding simulation engine.

---

## [v0.46.0.1.8] - 2026-03-25: Enhance Asset Browser Window

### Summary

Improved the Asset Browser Window with better usability, dynamic layout, and enhanced audio management.

### Added

- **Full-Width Toolbar**: Search bar and Refresh button now utilize the full width of the window for better accessibility.
- **Dynamic Grid Columns**: Asset items in the Animations, Textures, and Prefabs tabs now automatically adjust the number of columns based on the window width.
- **Enhanced Sounds Tab**:
    - **List View**: Refactored to a table-based list view for better information density.
    - **Audio Durations**: Displays the duration of audio files in seconds.
    - **Playback Controls**: Integrated Play/Stop icon buttons for quick audio preview.
- **Localized Tooltips**: Added descriptive tooltips to all buttons and search fields using `StringManager`.

---

## [v0.46.0.1.7] - 2026-03-25: Enhance Scene Hierarchy

### Summary

Significantly improved the Scene Hierarchy window with better organization, search capabilities, and essential scene management tools.

### Added

- **Visibility & Lock Toggles**: Added interactive icons to each GameObject in the hierarchy to quickly toggle visibility and locking.
- **Search Filtering**: Implemented a search bar to filter the hierarchy by GameObject name.
- **Inline Renaming**: Support for renaming GameObjects directly within the hierarchy.
- **F2 Shortcut**: Pressing F2 with a GameObject selected now triggers inline renaming.
- **"Add GameObject" Button**: Added a "+" button to the hierarchy toolbar for quick object creation.
- **Hierarchy Toolbar**: A dedicated toolbar at the top of the hierarchy window for common actions.

---

## [v0.46.0.1.6] - 2026-03-25: Refactor Properties Window

### Summary

Refactored the Properties Window to improve the layout and provide more robust tools for component and GameObject editing.

### Added

- **Dynamic Component Creation**: Support for adding new components to the selected GameObject directly from the UI.
- **Editable Name**: Ability to edit the GameObject's name directly in the properties panel.
- **isEnabled Toggle**: Added a toggle to easily enable or disable the GameObject.
- **Search Bar**: Included a search bar for filtering attached components.
- **Add Component Popup**: Introduced a popup menu for selecting and adding new components to the selected GameObject.

---

## [v0.46.0.1.5] - 2026-03-25: Scenes Tab Bar and Reviewer-Approved Refinements

### Summary

Implemented a multi-scene tab bar integrated into the Game Viewport, allowing users to seamlessly switch between open scenes. This update also incorporates several reviewer-approved refactorings to improve code clarity and robustness.

### Added

- **Scenes Tab Bar**:
  - A tab bar is now rendered at the top of the `GameViewWindow`.
  - Each open scene is represented by a tab, which can be selected to switch the active scene.
  - Tabs indicate their saved state (an asterisk `*` appears for unsaved scenes).
  - A permanent `+` button is included on the tab bar to allow for quick creation of new, empty scenes.
- **Multi-Scene Management**:
  - `SceneManager` was refactored to manage a list of `openScenes` instead of a single `currentScene`.

### Fixed

- **Redundant Docking Headers**: The native "Game Viewport" tab and header are now hidden using a combination of `ImGuiWindowFlags.NoTabItem` and `ImGuiDockNodeFlags.NoTabBar`, making the Scenes Tab Bar the sole navigation element.
- **Stale Scene References**: Refactored UI windows to fetch the `currentScene` directly from `SceneManager` to prevent one-frame lag and ensure all UI elements are always in sync.

### Changed

- **Code Clarity**:
  - Renamed `SceneManager.changeScene` to `openScene` to better reflect its new behavior.
  - Converted `SceneManager.currentScene` to a read-only computed property.
- **UI Robustness**:
  - Tab items in the Scenes Tab Bar now use unique IDs to prevent selection conflicts if multiple scenes have the same name.

---

## [v0.46.0.1.3] - 2026-03-25: Editor UI & Window Management Improvements

### Summary

Significant improvements to the custom editor window behavior and ImGui docking layout stability as part of the Engine UI revamp.

### Added

- **Custom Window Resizing**: Added manual resize grips in the editor UI for undecorated window management.
- **Custom Window Dragging**: Implemented smooth window movement across monitors via the editor menu bar and `WindowController`.

### Fixed

- **GLFW Window Issues**:
    - Enforced consistent undecorated state (`GLFW_DECORATED = GLFW_FALSE`) for the main application window to support custom UI themes.
    - Fixed window bounds calculation when maximizing an undecorated window on Windows OS.
    - Implemented minimum window size constraints (1024x768).
- **ImGui Docking**:
    - Resolved layout initialization issues where the dockspace could fail to set up on first launch.
    - Optimized `DockBuilder` submission order to ensure reliable and consistent panel placement (Viewport, Hierarchy, Properties, Console).
    - Improved layout persistence and initialization state.

---

## [v0.45.0.6] - 2026-03-24: Refactor Renderer to Render Graph System

### Summary

Refactored the monolithic Renderer into a modular, data-driven Render Graph system. This improves extensibility and allows for complex pass dependencies and resource sharing.

### Added

- **RenderGraph System**: New core architecture for managing rendering passes and resources.
  - `RenderGraph`: Orchestrates the execution of render passes.
  - `RenderPass`: Interface for individual rendering stages with lifecycle methods (`prepare`, `execute`, `cleanup`).
  - `RenderResource`: Generic container for textures, buffers, and values used in the graph.
  - `RenderContext`: Provides passes with access to resources and scene state.
  - `RenderGraphBuilder`: Fluent API for constructing the graph.

### Changed

- **Renderer.kt**: Now delegates all rendering work to the `RenderGraph`.
- **RenderPasses Refactored**: All existing passes converted to the new system:
  - `ShadowPass`: Defines output "ShadowMap".
  - `PickingPass`: Now uses `prepare()` for FBO setup.
  - `GeometryPass`: Dynamically samples "ShadowMap" from the graph context if available.
  - `DebugPass`: Now properly integrated into the graph lifecycle.
- **RenderResourcesFactory**: Now builds and configures the `RenderGraph` during initialization.

### Verified

- ✅ All unit tests passing
- ✅ Render graph logic verified with new unit tests
- ✅ Shadow map resource propagation through the graph confirmed

---

## [v0.45.0.5] - 2026-03-24: Set up Automated Testing Framework & Fix Failing Tests

### Summary

Set up the automated testing framework, fixed failing tests, and expanded test coverage for core systems.

### Completed

- Fixed currently failing tests including AudioEngineTest, BootManagerTest, and AudioComponentTest
- Expanded test coverage for core systems (ECS, asset loading, math)
- Ensured all tests pass consistently in the CI/CD pipeline
- Set up automated testing framework

---

## [v0.45.0.4] - 2026-03-23: Implement Ragdoll Physics

### Summary

Implemented Ragdoll Physics successfully according to the ECS architecture.

### Completed

- Implemented Ragdoll Component and Ragdoll System
- Implemented CapsuleCollider3D and Builder for Ragdoll creation
- Defined and created ragdoll skeletons from skeletal data
- Added ability to activate/deactivate ragdolls with animation blending
- Enabled ragdoll responses to physics forces (gravity, collisions)
- Integrated with physics system and component model
- Tests implemented successfully

---

## [v0.45.0.3] - 2026-03-23: Develop Basic Audio System

### Summary

Implemented basic audio system using OpenAL for 2D and 3D audio playback with spatialization.

### Completed

- Load and play audio files (WAV, OGG)
- Support for 2D audio playback (global sounds)
- Support for 3D audio playback with spatialization
- Basic controls for volume, looping, and playback status
- Refactor AudioComponent to be a pure data container (remove logic, load(), play(), stop(), and Sound instances)
- Move audio state evaluation and OpenAL interaction into AudioSystem
- Integrate audio loading with ResourceManager to prevent redundant file loading and manage shared SoundBuffers vs
  individual SoundSources
- Implement setPosition, setVolume, setLooping, and setRelative methods in Sound.kt
- Connect AudioSystem to update Sound instances based on Transform and AudioComponent data
- Fix hardcoded 0.3f volume gain and missing AL_SOURCE_RELATIVE flag for 2D audio
- Fix resource leaks in WAV loading (add .use blocks) and use proper LWJGL memory deallocation (MemoryUtil.memFree)
  instead of LibCStdlib.free()

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
