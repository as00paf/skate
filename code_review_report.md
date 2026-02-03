# Codebase Review Report

## 1. Architectural Principles Adherence

### SOLID & Clean Architecture
- **Adherence**: Generally good. Physics (JBullet) is largely encapsulated within `Physics3D` and `RigidBody3D`, and rendering within the `render` package. UI logic (ImGui) is separate.
- **Improvements**:
    - `Renderer2D` and `Renderer` rely on static `lateinit var shader` and `camera` respectively. This introduces tight coupling and makes testing harder. These dependencies should be injected via Koin or passed as parameters.
    - `InputBuffer.instance` is a manual singleton. It should be provided via Koin.

### Idiomatic Kotlin
- **Adherence**: Mostly good. Uses data classes, sealed classes (`PlayerState`), extension functions (`toRadians`, `toDegrees`, `Transform.toMatrix`, `Transform.toWorldMatrix`).
- **Improvements**:
    - `UnitType` is a sealed class with `data object`s, which is good. However, it has a `companion object` with `values()` and `valueOf(String)` methods that essentially replicate enum functionality. Since `UnitType` is effectively acting as an enum, it might be simpler to just make it an enum if no additional sealed class specific behavior is planned. If it is meant to be extensible, the `values()` and `valueOf()` methods need to be carefully maintained manually.

### Zero-Assertion Policy (`!!`)
- **Adherence**: Generally good, but several instances of `!!` were found, particularly in `AssimpLoader` for scene access and `RigidBody3D` when getting components.
- **Improvements**: Replace all `!!` with safe calls (`?.`), Elvis operator (`?:`), or explicit null checks (`if (x != null)`).

### Dependency Injection (Koin)
- **Adherence**: Koin is extensively used, which is excellent.
- **Improvements**:
    - `Renderer2D.shader` and `Renderer2D.camera` are static `lateinit var` and should be injected.
    - `VAOLoader` in `SplashScreenManager` is instantiated directly (`VAOLoader().loadToVAO(...)`) instead of being injected.

### Localization
- **Adherence**: Excellent. `StringManager` and `TrickManager` are well-implemented and used throughout the UI and trick detection. Hardcoded strings are generally avoided.

## 2. Code Improvements

### Code Duplicates
- **`AnimationSampler` Initialization**: The `sampleVector3f` and `sampleQuaternionf` methods in `AnimationSampler` have duplicated logic for handling `time <= times.first()` and `time >= times.last()`. This could be refactored into a common helper function.
- **Physics Body Setup**: In `Physics3D.add(go: GameObject)`, the logic for setting up `PhysicsRigidBody` properties (mass, friction, damping, location, rotation, scale) is somewhat duplicated between the initial creation and the update path. This could be extracted into a private helper function.
- **Raycast logic**: `handleClipping` in `Camera` and `handleGroundSnapping` in `PlayerController` have similar ray-test-and-find-closest-hit logic. This could be generalized into a utility function or extension function on `IPhysics3D`.
- **Collider Creation**: The logic in `Physics3D.add` for creating `BoxCollisionShape`, `CylinderCollisionShape`, and `HullCollisionShape` could be simplified using a builder pattern or a more generic factory if more collider types are added.

### Logic Flaws
- **`Animator.updateBlended`**: The `updateBlended` function calculates `previousTime` and `currentTime` by adding `dt` but `previousAnimation?.let { prev -> prev.update(previousTime, skeleton) }` will update `previousTime` again, which will lead to a double update of `previousTime`. It also updates `previousTime` and `currentTime` *before* the `blendTime` check, which means if `blendTime` is 0 initially, `currentTime` would still increment for one frame before the blending logic is skipped. It is more robust to manage `previousTime` and `currentTime` internally within the Animator and use the current `dt` for updates.
- **`Skater` constructor**: The `Skater` class has `GameObject("Skater")` inside its constructor, which is redundant as it already extends `GameObject(name)`. This likely results in the base `GameObject` constructor being called twice or only the outer `name` being used. The inner `GameObject("Skater")` is a function call that creates a new temporary `GameObject` instance that is not used.
- **`PlayerController.handleCatch`**: The `handleCatch` logic for `pitch` and `roll` (X and Z rotations) uses `angle % 180f`. While trying to snap to 0 or 180, `pAngle !in 20f..160f` means it acts when the angle is close to 0 or 180. The `applyTorqueImpulse` uses `(target - absPAngle) * catchStrength * dt`. If `absPAngle` is 10, `target` is 0, then `(0 - 10) * ...` results in negative torque. If `absPAngle` is 170, `target` is 180, then `(180 - 170) * ...` is positive torque. This logic seems inverted for `pitch` (X) and `roll` (Z) based on typical physical corrections (e.g., if rolling left, you want to apply torque right to correct). It should be consistent with the direction needed to return to a level orientation.
- **`MeasureTool` mouse position**: Uses `ImGui.getMousePos()` for `mousePos` then subtracts `viewportPos.x` and `viewportPos.y`. While `ImGui.getMousePos()` is typically screen-relative, it might be better to consistently use `mouseListener.getX()` and `mouseListener.getY()` which are already adjusted for the window.
- **`SplashScreenManager.destroy()`**: Sets `splashQuad = null` and `splashTexture = null`, but does not call `cleanUp()` on `VAOLoader` for `splashQuad`, leading to a potential resource leak.

### Inefficient Implementations
- **`Animator.visualizeJoint`**: In `visualizeJoint`, `joint.worldTransform.getTranslation(jointPos)` and `child.worldTransform.getTranslation(childPos)` followed by `modelMatrix.transformPosition` on new `Vector3f` instances can create a lot of temporary objects in a loop, impacting performance during editor updates. JOML matrices have `transform` methods that can operate in place or with a destination vector to minimize object allocation.
- **`RenderBatch.loadVertexProperties`**: `Matrix4f().identity()` and subsequent `translate`, `rotate`, `scale` calls create a new `Matrix4f` every time for every sprite, which is very inefficient. A single `Matrix4f` should be reused and reset for each sprite.
- **`PickingDraw.draw`**: The `vertexArray` is recreated and copied to the VBO for *every mesh*, instead of batching all picking meshes into one buffer update. This is highly inefficient if there are many pickable meshes.
- **`ScreenshotUtils.takeScreenshot`**: The `flippedPixels` ByteBuffer is created and populated row by row in a loop. While necessary for flipping, for very large screenshots, this could be slow. For performance, it might be better to do the flip directly in a shader if possible or optimize the CPU-side loop (though it's usually not a performance bottleneck for single screenshots).
- **`Physics3D.add` updates for existing rigid bodies**: When `rb.rawBody != null`, it checks `currentMass != desiredMass` to decide whether to remove and re-add the body. If the mass is the same, it updates physics location, rotation, scale, friction, and damping. However, scale and rotation are also handled by `RigidBody3D.update` and `editorUpdate`. This means some updates might be applied redundantly or potentially lead to conflicting states if not carefully managed. A more explicit separation of concerns where `Physics3D.add` only *adds* a new body and `RigidBody3D` itself is responsible for syncing its `rawBody` with its `gameObject.transform` would be cleaner.

### Objects that should be classes
- No obvious instances of objects that should be classes were found.

### Files with Multiple Classes
- **`DebugDraw.kt`**: Contains `DebugDraw`, `Line3D`, `Triangle3D`. `Line3D` and `Triangle3D` are small data classes, but separating them into their own files would adhere to the strict "one class per file" rule.
- **`EditorCommands.kt`**: Contains `TransformCommand`, `CreateGameObjectCommand`, `DeleteGameObjectCommand`. These are command objects related to the editor's undo/redo system. They could be in separate files.
- **`Ray.kt`**: Contains `Ray`. It could be a top-level class.

### Missing Top-Level Import Statements
- Many files use fully qualified names (FQNs) for JOML and JBullet classes instead of top-level imports, violating the "Code Style" architectural principle. For example:
    - `org.joml.Vector3f` appears frequently as `com.pafoid.skate.engine.utils.JomlVector3f`.
    - `com.jme3.math.Vector3f` appears frequently as `com.pafoid.skate.engine.utils.JmeVector3f`.
    - `org.joml.Matrix4f` is often used with FQN.
    - `org.joml.Quaternionf` is often used with FQN.

### Components that should use injection but are not
- As identified in "SOLID & Clean Architecture" and "Dependency Injection":
    - `Renderer2D.shader` and `Renderer2D.camera` are static `lateinit var` and should be injected.
    - `VAOLoader` in `SplashScreenManager` is directly instantiated.

### Threading Issues
- **`JobSystem.update()`**: This method is responsible for executing `mainThreadTasks`. It needs to be called from the main loop to ensure main-thread-bound coroutines are executed. This is currently handled correctly in `Window.loop()`.
- **`ResourceManager.loadModel(filePath: String)` and `loadTexture(filePath: String)`**: These functions are `suspend` functions and are called within `JobSystem.runAsync` or `JobSystem.runIO`. The results are then marshaled back to the main thread using `JobSystem.runOnMain`. This is a good pattern for offloading heavy asset loading.
- **`ThumbnailCache.renderThumbnail`**: This method performs OpenGL rendering to an FBO, which *must* happen on the main thread. While the `getThumbnail` call itself might be on a background thread, the actual `renderThumbnail` is implied to be called on the main thread through `JobSystem.runOnMain` in `AssetBrowserTab`. This seems correctly handled.

### Code Conflicts
- No immediate code conflicts were identified.

### Instances where the code would benefit from using sealed classes, extension method, inline classes, coroutines
- **Sealed Classes**: `PlayerState` is already a good example of a sealed class. `UnitType` could also benefit from being a sealed interface with data objects if extensibility is desired without the overhead of an enum.
- **Extension Functions**: Used effectively for `toRadians`, `toDegrees`, `Transform.toMatrix`, `Transform.toWorldMatrix`.
- **Inline Classes**: Could be used for primitive wrappers like `TextureId(val id: Int)` or `ShaderId(val id: Int)` to provide type safety without runtime overhead, preventing accidental misuse of raw integers.
- **Coroutines**: Already extensively used in `JobSystem`, `ResourceManager`, `SceneManager`, `SplashScreenManager` for async operations and thread management, which is excellent.

### Fixes and Todos that are in the code
- **`AssimpLoader`**: `// TODO: handle error` and `// TODO: fix nullability` are present.
- **`AssimpLoader.processAnimation`**: `//TODO: extract` for translation, rotation, scale processing. This suggests further refactoring is intended.
- **`SplashScreenManager`**: `// TODO: inject loader ?` for `VAOLoader`.

### Variables that should have more descriptive names
- **`AnimationSampler.sample` and `sampleVector3f`/`sampleQuaternionf`**: `t0`, `t1`, `t` are common in interpolation but could be `keyframeTime0`, `keyframeTime1`, `interpolationFactor` for extreme clarity, though their current usage is standard in graphics.
- **`DebugDraw` `vertexArray` / `triangleVertexArray`**: These are generic names. Could be `lineVertexData` and `triangleVertexData`.
- **`GameViewWindow.windowSize`, `windowPos`**: These refer to the ImGui window size/position, but `imageSizeX`, `imageSizeY`, `imageScreenPosX`, `imageScreenPosY` are more specific to the *rendered image within* the window. The names are generally clear in context.
- **`ImGuiLayer` `glslVersion`**: Could be `glslVersionString`.
- **`RenderBatch` `sprites`**: It's an `Array<SpriteRenderer?>`. `spriteRenderers` would be more descriptive.
- **`RenderBatch` `texSlots`**: `textureSlots` would be clearer.

### Usage of `!!` instead of handling nullability
- **`Window.getImGuiLayer()` and `getFrameBuffer()`**: Use `instance!!.imGuiLayer` and `instance!!.frameBuffer`. This is unsafe if `instance` can be null. While `init` sets `instance`, runtime access needs to be null-safe.
- **`Animation.update()` and `updateBlended()`**: `skeleton.getJointByName(channel.targetNodeName) ?: continue` correctly handles nullability, but the return from `getJointByName` is then used with `joint.localTransform.translation(tempVec3)` etc. This is generally safe.
- **`Animator.update` and `editorUpdate`**: Multiple `?.let { ... } ?: return` are used, which is good. However, some `!!` still exist inside the `let` blocks. For example, `gameObject.getComponent<Entity>()?.model?.skeleton ?: return`.
- **`AssimpLoader.preLoadModel`**: `scene.mMeshes()!!`, `scene.mRootNode()!!`, `aiAnim.mChannels()!!`, `aiChannel.mPositionKeys()!!`, etc. are used extensively. While Assimp typically guarantees these are present if parsing is successful, it's a potential point of failure.
- **`Physics3D.add`**: `rb.rawBody!!` is used in several places.
- **`TrickDetector.start`**: `rigidBody = gameObject.getComponent(RigidBody3D::class.java)!!` and `skateboardPhysics = gameObject.getComponent(SkateboardPhysics::class.java)!!`. These are guarded by the expectation that these components are always present. If they are truly mandatory, consider making them constructor dependencies of `TrickDetector` and injecting `TrickDetector` through Koin, where Koin can guarantee the dependencies are met or fail fast at startup.

### Missing KDoc/Javadoc comments
- Many functions, especially in utility classes and components, could benefit from KDoc comments explaining their purpose, parameters, and return values. This is explicitly requested in the `AI_INSTRUCTIONS.md` under "Readability & Documentation".
    - `MathExtensions.kt`: Functions like `toRadians` and `toDegrees` could have comments.
    - `Interpolation.kt`: Each interpolation function could have detailed KDoc.
    - `MImGui.kt`: Each `draw...Control` function could have KDoc.
    - `Physics3D.kt`: Private helper methods like `debugDrawShape`, `drawComplexShapes`, `drawCompoundCollisionShape`, `drawCylinderCollisionShape`, `drawBoxCollisionShape` could use KDoc.
    - `PlayerController.kt`: Many methods like `handleStability`, `updateRidingAnimation`, `updateProceduralLean`, `handleWalking`, `handleGroundSnapping`, `handleStateToggle`, `updateCurrentStance`, `checkBail`, `bail`, `handleCatch`, `handleFlicks`, `handleSteering`, `handlePushing`, `handleJumping` would benefit greatly from KDoc.
    - `TrickDetector.kt`: `detectTrick()` especially.

## 3. Shader Documentation
- The `.vert` and `.frag` files themselves were not inspected, but the `AI_INSTRUCTIONS.md` mentions "Shader Documentation: Add comments to `.vert` and `.frag` files explaining the coordinate spaces (World vs. View vs. Clip)." This is a good practice to follow during development.
