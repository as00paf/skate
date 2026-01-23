# Skate Project Context

## Project Overview

**Skate** is a Kotlin-based game engine project aiming to combine the best features of a 2D engine (**MinePaf**) and a 3D engine (**PafCraft**) into a unified, high-performance engine capable of supporting both dimensions.

**Current Status:** The project structure is initialized and populated with core engine classes. We are currently in the process of porting and refactoring features from the legacy projects into this new unified architecture.

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
*   **Workflow:** The user will explicitly confirm when a milestone is complete before proceeding to the next item on the Todo list.

## Tech Stack

*   **Language:** Kotlin (JVM Target 17)
*   **Build System:** Gradle (Kotlin DSL)
*   **Key Libraries:**
    *   LWJGL (Core, Assimp, GLFW, OpenAL, OpenGL, STB)
    *   JOML (Java OpenGL Math Library)
    *   LWJGL3-AWT (AWT integration)
*   **Testing:** JUnit Platform, Kotlin Test

## Roadmap / Todo List

### Milestone 1: Core Infrastructure [In Progress]
- [ ] **Windowing System:** Abstraction over GLFW window creation and management.
- [ ] **Input Handling:** Robust Keyboard and Mouse listeners with event polling.
- [ ] **Game Loop:** Fixed time-step loop (`Time`, `delta_time`).
- [ ] **Math Library Integration:** standardizing on JOML for Vectors/Matrices.

### Milestone 2: Asset Management
- [ ] **Resource Management System:** Centralized `AssetPool` or Manager.
- [ ] **Shader System:** Loading, compiling, and linking GLSL shaders.
- [ ] **Texture System:** Loading images (STB) and creating OpenGL textures.
- [ ] **Audio System:** Integration of OpenAL for sound playback (from MinePaf/PafCraft).

### Milestone 3: Scene & Entity Architecture
- [ ] **Scene Management:** Abstract `Scene` class and `SceneManager` for switching states.
- [ ] **Entity Component System (ECS) / GameObject:**
    - [ ] Decide on strict ECS vs GameObject/Component pattern (referencing `MinePaf` structure).
    - [ ] Base `Component` and `GameObject`/`Entity` classes.
    - [ ] Serialization/Deserialization support.

### Milestone 4: 2D Rendering (from MinePaf)
- [ ] **Sprite System:** `Sprite`, `SpriteSheet` components.
- [ ] **Batch Rendering:** Efficient `SpriteRenderer` and batching logic to minimize draw calls.
- [ ] **2D Camera:** Orthographic camera implementation.
- [ ] **2D Physics:** Integration (Box2D or custom AABB).

### Milestone 5: 3D Rendering (from PafCraft)
- [ ] **3D Model Loading:** `ObjLoader` (and eventually Assimp for others).
- [ ] **Mesh & VAO Abstraction:** `VAOLoader`, `RawModel`, `TexturedModel`.
- [ ] **3D Camera:** Perspective camera implementation (First/Third person).
- [ ] **Lighting:** Ambient, Diffuse, Specular lighting shader implementation.
- [ ] **Skybox:** Cube map rendering.

### Milestone 6: Editor Tools (Optional/Later)
- [ ] **Level Editor:** Scene serialization, Gizmos (Translate, Scale), and object picking.
- [ ] **ImGui Integration:** Debug UI for inspecting entities.

### Milestone 7: Polish & Expansion
- [ ] **Advanced 3D Formats:** Support for FBX/GLTF (using Assimp).
- [ ] **Particle System:** 2D/3D particles.
- [ ] **Skateboarding Game Logic:** Specific gameplay mechanics.

## Building and Running

### Build
```bash
./gradlew build
```

### Testing
```bash
./gradlew test
```

## Development Conventions

*   **Code Style:** Official Kotlin code style.
*   **Directory Structure:** Standard Gradle/Maven layout.