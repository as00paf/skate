# Skate Project Context

## Project Overview

**Skate** is a Kotlin-based game engine project aiming to combine the best features of a 2D engine (**MinePaf**) and a 3D engine (**PafCraft**) into a unified, high-performance engine capable of supporting both dimensions.

**Current Status:** The project structure is initialized and populated with core engine classes. We are currently in the process of porting and refactoring features from the legacy projects into this new unified architecture. Milestone 4 (2D Rendering basics) is largely complete.

## Project History & Goals

*   **Origins:**
    *   **MinePaf:** A 2D game engine (previous project).
    *   **PafCraft:** A 3D game engine (previous project).
*   **Objective:** Combine MinePaf and PafCraft to create **Skate**, a versatile engine for a skateboarding game (and potentially others).
*   **Strategy:** Implement features one by one, ensuring high quality and stability before moving to the next.
*   **Future:** Once parity is reached, extend capabilities (e.g., support for more 3D file types).

## Architecture & Principles

*   **SOLID Principles:** Rigorous adherence to Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, and Dependency Inversion principles.
*   **Clean Architecture:** Separation of concerns. Isolate core business logic/game logic from external frameworks (rendering library, input handling) where possible.
*   **Kotlin Idioms:** Leverage Kotlin's specific features (Extensions, Coroutines, Sealed Classes, Delegates, Null Safety) for concise and safe code.

## Workflow & Guidelines

1.  **Feature Branches:** Before starting a task, create a branch named `feature/description` (e.g., `feature/orthographic-camera`).
2.  **Small Commits:** Commit often with clear messages to track changes easily.
3.  **Project Cleanliness:** Ensure the project is clean (unused files removed, code refactored) before starting a new task.
4.  **Completion & Merging:**
    *   Push code to the feature branch when an item is done.
    *   Explicitly request user confirmation before merging to `master` and updating the Todo list.
5.  **UI Strategy:** Use **ImGui** for the Level Editor and Debug UI.

## Tech Stack

*   **Language:** Kotlin (JVM Target 17)
*   **Build System:** Gradle (Kotlin DSL)
*   **Key Libraries:**
    *   LWJGL (Core, Assimp, GLFW, OpenAL, OpenGL, STB)
    *   JOML (Java OpenGL Math Library)
    *   LWJGL3-AWT (AWT integration)
    *   **JBox2D:** For Physics.
    *   **ImGui:** For UI/Editor.
*   **Testing:** JUnit Platform, Kotlin Test

## Roadmap / Todo List

### Milestone 1: Core Infrastructure [Completed]
- [x] **Windowing System:** Abstraction over GLFW window creation and management.
- [x] **Input Handling:** Robust Keyboard and Mouse listeners with event polling.
- [x] **Game Loop:** Fixed time-step loop (`Time`, `delta_time`).
- [x] **Math Library Integration:** Standardizing on JOML.
- [x] **Refactoring:** Decoupled Renderer from SceneManager.

### Milestone 2: Asset Management [Completed]
- [x] **Resource Management System:** `AssetPool`.
- [x] **Shader System:** Basic loading and compilation.
- [x] **Texture System:** Basic loading.
- [x] **Audio System:** Integration of OpenAL (Basic support present).

### Milestone 3: Scene & Entity Architecture [Completed]
- [x] **Scene Management:** `Scene`, `SceneManager`.
- [x] **Entity Component System (ECS):** `GameObject`, `Component`, `Entity` structure.

### Milestone 4: 2D Rendering [Completed]
- [x] **Sprite System:** `Sprite`, `SpriteSheet` components.
- [x] **Batch Rendering:** `RenderBatch`, `Renderer2D`.
- [x] **Integration:** `Renderer` delegates to `Renderer2D`.

### Milestone 5: Rendering & Camera Enhancements [Completed]
- [x] **Cleanup Assets:** Remove unused models (dragon.obj) and shaders; standardize shader files.
- [x] **Orthographic Camera:** Implement `Camera` subclass or mode for 2D.
- [x] **3D Camera Improvements:** Refine perspective camera (First/Third person controls).
- [x] **Shader Refactoring:** Create dedicated shaders for 2D (batch) and 3D (lit).

### Milestone 6: Physics [Completed]
- [x] **JBox2D Integration:** Add library dependency.
- [x] **Physics Components:** `Rigidbody2D`, `BoxCollider2D`, etc.
- [x] **Physics System:** Integration into the Game Loop.

### Milestone 7: Editor & UI [Completed]
- [x] **ImGui Integration:** Setup ImGui context and render loop.
- [x] **Level Editor Scene:** Gizmos, Object Picking, Hierarchy view.
- [x] **Scene Serialization:** Loading/Saving scenes with JSON.

### Milestone 8: 3D Rendering & Gameplay [Completed]
- [x] **Advanced 3D:** Lighting (Ambient, Diffuse, Specular), Skybox.
- [x] **Skateboarding Mechanics:** Player controller, physics tuning.

### Milestone 9: Input & Advanced Assets [Current Focus]
- [x] **Game Controller Support:** Implement GLFW joystick/gamepad listeners.
- [ ] **Assimp Enhancements:** Support for GLB and FBX model loading.
- [ ] **Particle System:** 2D/3D visual effects.
