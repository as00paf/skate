# Code Cleanup Report

## 1. Missing and Failing Unit Tests

The following key components lack sufficient unit test coverage:

* **Physics Core**:
    * `src/main/kotlin/com/pafoid/skate/engine/physics3d/Physics3D.kt`: Core physics space management, adding/removing
      bodies, fixed timestep logic.
    * `src/main/kotlin/com/pafoid/skate/engine/physics3d/components/RigidBody3D.kt`: Property synchronization with
      Bullet, force application methods.
* **Scene Management**:
    * `src/main/kotlin/com/pafoid/skate/engine/scenes/Scene.kt`: Scene lifecycle and object management.
    * `src/main/kotlin/com/pafoid/skate/engine/scenes/editor/LevelEditorSceneInitializer.kt`: Initialization logic.
* **Math Utilities**:
    * `src/main/kotlin/com/pafoid/skate/engine/utils/Ray.kt`: Ray casting logic.
    * `src/main/kotlin/com/pafoid/skate/engine/utils/MathExtensions.kt`: Extension methods.

### 3.2 Logging Violations (`println` / `System.err`)

Direct usage of `println` or `System.err` bypasses the `LoggerService`.

* `Window.kt`: `System.err.println`
* `TrickManager.kt`: `println("ERROR...")`
* `StringManager.kt`: `println("ERROR...")`
* `JobSystem.kt`: `println("JobSystem Error...")`
* `AssimpExtensions.kt`: Debug `println`s
* `Scene.kt`: `println("Error: Could not find $path")`
* `PlayerStateManager.kt`: `println("Transitioning...")`
* `ShaderLoader.kt`, `AssimpLoader.kt`, `Sound.kt`, `AnimationLoader.kt`: Error logging via `println`.

### 3.3 TODOs and FIXMEs

* `SceneManager.kt`: `// TODO: fix loading of saved scene`
* `Renderer2D.kt`: `// TODO: Handle z-index properly`
* `AssimpLoader.kt`: `// TODO: handle error`
* `AnimationSystem.kt`: `// TODO: fix nullability by caching animated gameObjects`

## 4. Architectural & Refactoring Opportunities

### 4.1 Long Methods & Classes

* **`PlayerController.kt`**: This class is ~480 lines and handles Input, Physics, Animation, State Management, and Debug
  UI.
    * *Recommendation*: Extract `WalkController`, `RideController`, `StanceManager` into separate components or
      delegates.
    * *Recommendation*: Move `bail()` logic to a `BailSystem` or `RagdollManager`.

### 4.4 Code Reuse & Extension Methods

* **Math Conversions**: `Math.toRadians` and `Math.toDegrees` are used frequently throughout the codebase (
  `Transform.kt`, `Camera.kt`, `Physics3D.kt`).
    * *Recommendation*: Consistently use `MathExtensions.kt` (`Float.toRadians()`, `Float.toDegrees()`).
* **Raycasting**: `PlayerController.handleGroundSnapping` duplicates logic found in `Camera`.
    * *Recommendation*: Generalize `RaycastUtils` or similar helper.


