# 🛹 SkateSim MVP - Master TODO

## 🟢 Phase A: Core Infrastructure [COMPLETE]

### 1. Engine & File Management
- [x] **Async Boot**: Refactored `init()` with Kotlin Coroutines for non-blocking splash screen.
- [x] **Splash UX**: Implemented `EngineState` machine and full-screen splash shader (Y-flip fixed).
- [x] **Asset Extraction**: Automated unzip and organization of `player_model.zip`.
- [x] **GPU Sync**: Thread-safe `ConcurrentLinkedQueue` for uploading meshes/textures from background threads.

### 2. glTF 2.0 Integration
- [x] **Geometry**: Support for Standard Attributes (Tangent space), Index Accessors, and Primitives.
- [x] **PBR Material**: Metallic-Roughness model with sRGB-to-Linear pipeline and Alpha/Double-Sided support.
- [x] **Hierarchy**: Full TRS (Translation, Rotation, Scale) support with parent-child matrix multiplication.
- [x] **Skinning Data**: Support for `JOINTS_0` and `WEIGHTS_0` accessors and Inverse Bind Matrices.
- [x] **Binary/Embedded**: Support for `.glb` and Base64 Data URIs.

### 3. Professional Level Editor
- [x] **UI/UX Styling**: 'Pro Dark' theme with FontAwesome icon integration.
- [x] **Viewport Overlays**: Transparent overlays for Simulation Controls, FPS, and Gamepad HUD.
- [x] **Prefab System**: Grid-based thumbnail system with Search/Filter and Viewport Drag-and-Drop.
- [x] **Serialization**: JSON Scene Save/Load (GSON) and Persistent `settings.json`.
- [x] **Gizmo Suite**: Complete Translate/Rotate/Scale (QWER) with instant Bullet Physics scaling sync.

### 4. Testing & QA
- [x] **Unit Testing**: JUnit 5 + MockK integration for Physics and JOML math.
- [x] **Graphics Regression**: Offscreen rendering and "Gold Master" image comparison engine.
- [x] **Simulation Tools**: MockGamepad for input testing and DebugDraw for Bullet wireframes.

---

## 🟡 Phase B: Simulation & Atmosphere [COMPLETE]

### 5. Atmosphere & Environment
- [x] **Dynamic Sky (XL Style)**: UV Sphere Sky Dome with HDRI, Exposure, and Tint controls.
- [x] **Light Sync**: Directional Light (Sun) synced to Sky Dome rotation.
- [x] **Fog System**: Distance-based fading integrated into PBR shader.
- [x] **Obstacle Library**: Rail, Ledge, Kicker, and Skatelite-textured ramps.

### 6. The Board Rig (Physics)
- [x] **Assembly**: `btCompoundShape` for Deck/Trucks with mass-inertia calibration.
- [x] **Raycast Suspension**: 4-point Hooke’s Law ($F = kx + dv$) spring logic.
- [x] **Procedural Pop**: Localized tail impulses and center-of-mass leverage.
- [x] **Input Mapping**: Continuous Vectoring for local-space torques based on RS-flicks.
- [x] **Stance Engine**: Support for Regular/Goofy and Switch/Nollie/Fakie states.

### 7. Real-World Scaling (Unit System)
- [x] **Unit Infrastructure**: Base 1.0m = 1.0 Unit established.
- [x] **Physics Calibration**: Gravity at -9.81 m/s² and real-world board mass/inertia.
- [x] **Asset Scaling**: Assimp normalization and manual overrides for legacy assets.
- [x] **Metric Grid**: Camera-following "infinite"N grid with 0.1m/1.0m hierarchy.
- [x] **Measure Tool**: Editor-mode ruler with Metric/Imperial toggling.

---

## 🔴 Phase D: Next Tasks 

### 8. Skeletal Animation Pipeline
- [x] **Animation Samplers**: Implement Skeleton hierarchy traversal and LINEAR/STEP/CUBICSPLINE interpolation.
- [x] **GPU Skinning**: Updated PBR Vertex Shader to compute final positions using the Matrix Palette (4-bone influence).
- [x] **Animation Debugger**: Build ImGui window with clip selection, timeline scrubbing, and bone visualizer overlay.

### 9. Character Controller & Camera
- [x] **Task 9.1: State Toggle**: Map Gamepad **Y Button** to toggle Walking/Riding with teleport offsets.
- [x] **Task 9.2: On-Foot Locomotion**:
  - Implement LS movement relative to Camera Forward.
  - Implement **A Button** Jump logic for Walking state.
  - Trigger `WALK` and `JUMP` animation clips.
- [x] **Task 9.3: Orbital Camera**:
  - Implement RS **Orbit Control** (Yaw/Pitch).
  - Create presets for FOV, Offset, and Distance.
  - Implement smooth LERP transitions between presets.
- [x] **Task 9.4: World Alignment**:
  - Audit all Y-offsets for unit consistency.
  - Implement raycast floor snapping for the Walking state.

### 10. Riding Integration
- [x] **Task 10.1: Physics Locking**: Parent Player Model to `BoardRig` with vertical offset. **Verify scale proportions with User.**
- [x] **Task 10.2: Stability Logic**: Implement "Snap to Board" logic to prevent drift during high-speed turns.
- [x] **Task 10.3: Animation Posing**: Apply static "Ride" pose (knees bent, arms out) using the new `james.dae` model and orient the skater relative to the skateboard.
- [x] **Task 10.4: Procedural Lean**: Implement spine rotation tied to LS steering input.
- [x] **Task 10.5: State Manager**: Implement `PlayerState` manager (IDLE, RIDING, PUSHING).
- [x] **Task 10.6: Push Mechanic**: Trigger physics impulses based on specific animation frames.

### 11. Procedural Pose Editor 
- [x] **Bone Tree UI**: Implement an ImGui tree view showing the hierarchy of `aiNodes`.
- [x] **Local Override System**: Create a `BoneOverride` component that stores custom Quaternions for specific bone IDs.
- [x] **Pose Gizmos**: (Optional) Allow clicking a bone in the viewport to select it in the UI.
- [x] **JSON Pose Persistence**: Implement Save/Load for bone rotation maps (e.g., `assets/poses/ride_pose.json`).
- [x] **Test Tool**: Add a "Mirror Pose" toggle (e.g., if you pose the left leg, it copies to the right) to speed up stance creation.

### 12. Trick Detection System
- [x] **The Labeler**: Logic for monitoring local-space rotation accumulation in air.
- [x] **Trick UI**: Viewport overlay to display identified tricks (e.g., "Kickflip", "360 Shove-it").
- [x] **TDD Validation**: Unit tests for rotation-to-string trick identification.

### 13. Final Polish
- [x] **App Icon**: Design and integrate native window icon.

### 14. Maintenance & Integrity
- [x] **Lingering Branches**: List all the branches on the local and remote repository that are not main and master.
- [x] **Branch Audit**: Make sure no code was lost in any of these branches and confirm with the user if necessary.
- [x] **Branch cleanup**: Delete all the branches except for the master branch and the main branch. This is a risky operation so you must absolutely confirm with the user before proceeding.

### 15.1 Structural Integrity
- [x] **Task 15.1.1: File Atomization**: Ensure every class has its own dedicated file. No multi-class files. This is a big refactor so we must be careful and make sure the project still compiles after each class has been separated into its own file. Make sure to fix the import statements as we go. 
- [x] **Task 15.1.2: Hardcode Purge**: Move "Magic Numbers" (physics constants, shader paths, default FOV) into a centralized `Constants.kt` or relevant companion objects.

### 15.2 Readability & Documentation
- [x] **Task 15.2.1: Contextual Commenting**: Add KDoc/Javadoc comments to complex math functions, especially in the **Skeletal Animation** and **Raycast Suspension** modules.
- [x] **Task 15.2.2: Shader Documentation**: Add comments to `.vert` and `.frag` files explaining the coordinate spaces (World vs. View vs. Clip).

### 15.3 Formatting & Style
- [x] **Task 15.3.1: Whitespace Consolidation**: Remove all double/triple breaking spaces. Enforce a single-line break between methods and properties.
- [x] **Task 15.3.2: Linting Pass**: Apply standard Kotlin/JVM formatting rules (consistent indentation, curly brace placement, and trailing commas).
- [x] **Task 15.3.3: Import Optimization**: Remove unused imports and organize them alphabetically.

# 🧪 16. Skateboard Physics: Unit Test Suite

## 🏗️ 16.1: Static & Structural Integrity
- [x] **Test T1.1: Mass & Inertia Tensor**: Verify the `btCompoundShape` total mass equals the sum of parts (Deck + Trucks + Wheels).
- [x] **Test T1.2: Center of Mass (CoM)**: Confirm the CoM is slightly lower than the deck surface (simulating truck weight) and centered between axles.
- [x] **Test T1.3: Static Friction**: Place the board on a 15° slope; verify it does not move until an external force is applied.

## 🏁 16.2: Locomotion & Steering
- [x] **Test T2.1: Roll Resistance**: Measure velocity decay on a flat plane. (Verified FAILED - Fix deferred to 17.2)
- [x] **Test T2.2: Hooke's Law (Suspension)**: Apply a $75kg$ load; verify raycast springs compress. (PASSED)
- [x] **Test T2.3: Turning Radius**: Apply maximum local Z-torque (lean); verify the board traces a circular path. (PASSED - Refinement in 17.3)

## 🚀 16.3: The "Pop" Mechanics (Ollie Physics)
- [x] **Test T3.1: Tail Snap (Leverage)**: Apply downward impulse to the tail. Verify the **Nose** vertical velocity is positive. (PASSED)
- [x] **Test T3.2: Ground Impact Impulse**: Verify that when the tail hits the ground, a secondary upward impulse is generated. (PASSED)
- [x] **Test T3.3: Front Foot Leveling**: Simulate the "Slide" by applying downward force to the nose while airborne. Verify board pitch returns to $0$. (PASSED)

## 🔄 16.4: Trick Detection Logic
- [x] **Test T4.1: Rotation Accumulation**: Spin the board 360° on the local Y-axis in a vacuum; verify the `TrickTracker` returns "360 Shove-it".
- [x] **Test T4.2: Kickflip Detection**: Spin the board 360° on the local X-axis; verify the "Kickflip" state is triggered.
- [x] **Test T4.3: Stance Validation**: Perform a pop while moving backwards; verify the system identifies the trick as "Fakie Ollie" rather than "Nollie".

## 🛠️ 16.5: Boundary & Stress Testing
- [x] **Test T5.1: High-Speed Stability**: Run the simulation at $50 m/s$; verify raycast wheels do not "tunnel" (clip) through the floor. (PASSED)
- [x] **Test T5.2: Frame-Rate Independence**: Run the same "Push" test at $30fps$ and $120fps$; verify the final displacement is identical within a 1% margin. (PASSED)
- [x] **Test T5.3: Access Violation Regression**: Repeatedly instantiate and destroy 100 `PhysicsRigidBody` objects to ensure the `-Xverify:none` flag and JNI bindings are stable. (PASSED)

# 🏗️ Phase 17. Architecture & Infrastructure Optimization
- [x] **Dependency Migration**: Move all dependency versions from `build.gradle.kts` to `libs.versions.toml` for centralized version management.
- [x] **Resource Management System**: Implement a centralized system to handle efficient loading, caching, and unloading of engine assets (Textures, Shaders, Models).
- [x] **Dependency Injection**: Integrate **Koin** to manage component lifecycles, improve testability, and decouple engine systems.
- [x] **Standardized Serialization**: Implement a robust, project-wide serialization strategy (e.g., using kotlinx.serialization) for handling save states, configurations, and object properties.

# 🛠️ Phase 18. Editor UX & Workspace Overhaul
## 18.1. UI Navigation & Menu Cleanup
- [x] **Revamp 'View' Menu**:
  - [x] Remove the FPS counter from the menu (moving to Profiler).
  - [x] Add a "Windows" sub-menu with checkboxes to toggle all ImGui windows (Inspector, Console, Assets, etc.).
  - [x] Remove Physics Debug toggle (moving to Viewport Toolbar).
- [x] **Remove 'Create' Menu**: Deprecate the top-level Create menu in favor of the new Asset Browser workflow.
  
## 18.2. Integrated Console 
- [x] **Dual-Tab Console Window**:
  - [x] **Engine Tab**: Display system logs, shader compilation errors, and JNI/Physics warnings.
  - [x] **Editor Tab**: Log user actions (e.g., "Object Moved," "Prefab Spawned," "Scene Reset").
- [x] **Profiler Window**:
  - [x] Display **RAM Usage** (using `Runtime.getRuntime()`).
  - [x] Display **CPU/GPU Frame Times** (in ms).
  - [x] Display **Draw Call Count** (per frame).
  - [x] Display **Physics Step Duration** (timing the JBullet `stepSimulation`).

## 18.3. Unified Asset Browser
- [x] **Transform Prefabs Window**: Rename and expand the "Prefabs" window into a multi-tab **Asset Browser**.
  - [x] **Models Tab**: List all `.gltf`, `.fbx`, `.dae`, and `.obj` files in resources.
  - [x] **Textures Tab**: List all `.png` and `.jpg` files.
  - [x] **Prefabs Tab**: List saved `.json` entity templates.

## 18.4. Game Viewport & Toolbar
- [x] **External Toolbar**: Move buttons from inside the viewport to a dedicated toolbar strip in the top of the game viewport with the buttons centered. Don't change the dockspace if you don't need to.
- [x] **Icon-Only Buttons**:
  - [x] Replace text labels with icons (using FontAwesome or high-res textures).
  - [x] Add tooltips for every button (on hover).
- [x] **New Features & Keybinds**:
  - [x] **Reset Scene**: Full scene reload/re-initialization.
  - [x] **Pause**: Toggle engine time scale ($0.0$ vs $1.0$).
  - [x] **Screenshot**: Capture frame buffer to PNG. Save to screenshots folder and show a popup asking the user if he wants to open the screenshot or its containing folder.
  - [x] **Physics Toggle**: Move the debug wireframe toggle here and make it a toggle button.
  - [x] **Maximize Viewport (F12)**: Toggle between "Editor Layout" and "Fullscreen Viewport" mode. Make sure to use the right method so its not triggered multiple times.

## 18.5. Interaction & Shortcuts
- [x] **Middle-Mouse Pan**: Implement `View Rotation` in the editor camera. Pressing **MMB** + Mouse move shifts the camera on its local yaw/pitch axes. Ensure mouse capture works as expected.
- [x] **Mouse Wheel Zoom**: Implement `Zooming` in the editor camera. Scrolling the mouse wheel zooms in and out.
- [x] **Standard Editor Shortcuts**:
  - [x] **Ctrl + C / V / X**: Copy, Paste, and Cut selected `GameObject` (handling deep clones).
  - [x] **Ctrl + Z / Y**: Fully implement a Undo/Redo stack for Transform changes and object deletions.

# 🔧 19. Physics Implementation Fixes
- [x] **Task 19.1: Friction Propagation**: Fix `RigidBody3D` to ensure the `friction` property is correctly applied to the Bullet `rawBody` during initialization and runtime updates.
- [x] **Task 19.2: Rolling Resistance**: Implement `linearDamping` logic in `RigidBody3D` to satisfy the `rollResistance` test.
- [x] **Task 19.3: Steering Geometry**: Implement local-space steering forces in `SkateboardPhysics` to translate board lean (roll) into actual turning (yaw).
- [x] **Task 19.4: Fixed Timestep Integration**: Implement an accumulator-based fixed timestep loop in `Engine` or `Physics3D` to ensure deterministic physics regardless of render framerate.
- [x] **Task 19.5: Unit Tests**: Fix and add missing physics unit tests.

# 🌍 20. Localization & String Management System (i18n)



## 🏗️ Phase 20.1: Infrastructure Setup
- [x] **Define String Schema**: Create a centralized directory `src/main/resources/values/` to hold string data.
- [x] **Initial String Resource**: Create `strings.properties` (the "Default" language) to host all current UI labels.
- [x] **String Manager Singleton**: Implement `StringManager` (or `R`) class to handle the loading and retrieval of strings via keys (e.g., `R.string("lbl_reset_board")`).
- [x] **Koin Integration**: Register the `StringManager` in your Koin module for easy injection into UI components.

## 🛠️ Phase 20.2: Code Refactoring (The "Hardcoded Hunt")
- [x] **Refactor "Skate Lab" UI**: Replace all `ImGui.text("...")` and button labels in `LevelEditorSceneInitializer.kt` with localized lookups.
- [x] **Refactor Inspector Labels**: Move component field names (e.g., "Mass", "Friction") from `SkateboardPhysics.kt` into the properties file.
- [x] **Refactor Trick Analyzer**: Move trick names ("Kickflip", "Ollie") to `tricks.properties` to allow for "Trick Name" variations in the future.

## 🚀 Phase 20.3: Advanced Features (Android-Inspired)
- [x] **String Formatting**: Implement support for placeholders (e.g., `lbl_speed=Speed: %f m/s`) using `String.format()`.
- [x] **Pluralization Support**: Add a logic handler for quantities (e.g., "1 Trick" vs "5 Tricks").
- [x] **Language Switching Logic**: Add a setting in the Editor to toggle between `strings_en.properties` and `strings_fr.properties` (or others) at runtime without restarting the engine.

## 🧪 Phase 20.4: Validation & Quality
- [x] **Missing Key Fallback**: Ensure that if a key is missing, the engine returns the key name (e.g., `!!MISSING_KEY!!`) instead of crashing.
- [x] **Unit Test - String Retrieval**: Verify that the `StringManager` correctly pulls values from the resources folder.
- [x] **Unit Test - Format Validation**: Verify that string placeholders are filled correctly with float and integer values.

---

## 🔵 Phase E: Code Quality & Refactoring

### E1. Dependency Injection & Static Removal
- [x] E1.1: Inject `Renderer2D.shader` and `Renderer2D.camera` via Koin.
- [x] E1.2: Provide `InputBuffer.instance` via Koin.
- [x] E1.3: Inject `VAOLoader` into `SplashScreenManager`.

### E2. Null Safety (`!!` Removal )
- [x] E2.1: Remove `!!` in `AssimpLoader` for scene access.
- [x] E2.2: Remove `!!` in `RigidBody3D` when getting components.
- [x] E2.3: Remove `!!` in `Window.getImGuiLayer()` and `getFrameBuffer()`.
- [x] E2.4: Review and remove `!!` in `Animator.update` and `editorUpdate`.
- [x] E2.5: Review and remove `!!` in `TrickDetector.start`.

### E3. Code Duplication Refactoring
- [x] E3.1: Refactor `AnimationSampler` `sampleVector3f` and `sampleQuaternionf` initialization logic.
- [x] E3.2: Extract `PhysicsRigidBody` property setup into a helper in `Physics3D.add`.
- [x] E3.3: Generalize raycast logic from `Camera.handleClipping` and `PlayerController.handleGroundSnapping`.
- [x] E3.4: Simplify collider creation in `Physics3D.add` (e.g., using builder/factory pattern).

### E4. Logic Flaws & Bug Fixes
- [x] E4.1: Fix `Animator.updateBlended` to correctly manage `previousTime` and `currentTime` updates.
- [x] E4.2: Remove redundant `GameObject("Skater")` call in `Skater` constructor.
- [x] E4.3: Correct `PlayerController.handleCatch` logic for pitch and roll to apply correct torque.
- [x] E4.4: Use consistent mouse position (`mouseListener.getX/Y()`) in `MeasureTool`.
- [x] E4.5: Ensure `VAOLoader.cleanUp()` is called for `splashQuad` in `SplashScreenManager.destroy()`.

### E5. Performance & Efficiency
- [x] E5.1: Optimize `Animator.visualizeJoint` to minimize object allocation.
- [x] E5.2: Optimize `RenderBatch.loadVertexProperties` by reusing `Matrix4f`.
- [x] E5.3: Optimize `PickingDraw.draw` by batching picking meshes.
- [x] E5.4: Investigate and optimize `ScreenshotUtils.takeScreenshot` for large screenshots.
- [x] E5.5: Refactor `Physics3D.add` to separate adding and updating `PhysicsRigidBody` properties.

### E6. Code Structure & Organization
- [x] E6.1: Move `Line3D` and `Triangle3D` to their own files.
- [x] E6.2: Move `TransformCommand`, `CreateGameObjectCommand`, `DeleteGameObjectCommand` to their own files.
- [x] E6.3: Move `Ray` to its own file.

### E7. Import Statements & Code Style
- [x] E7.1: Replace all FQNs with top-level import statements for JOML and JBullet classes and use import aliases when pertinent.

### E8. Naming & Constants
- [x] E8.1: Improve naming of `AnimationSampler` interpolation variables (`t0`, `t1`, `t`).
- [x] E8.2: Rename `DebugDraw` `vertexArray` to `lineVertexData` and `triangleVertexArray` to `triangleVertexData`.
- [x] E8.3: Rename `RenderBatch` `texSlots` to `textureSlots`.

### E9. Documentation (KDoc)
- [x] E9.1: Add KDoc to `MathExtensions.kt` functions.
- [x] E9.2: Add KDoc to `Interpolation.kt` functions.
- [x] E9.3: Add KDoc to `MImGui.kt` functions.
- [x] E9.4: Add KDoc to private helper methods in `Physics3D.kt`.
- [x] E9.5: Add KDoc to `PlayerController.kt` methods.
- [x] E9.6: Add KDoc to `TrickDetector.detectTrick()` method.

---

## 🔵 Phase F: Polishing 

## F1. Interaction & Gizmos
- [x] **F1.1 Selection Gizmo**: Refactor items hover and picking into a SelectionGizmo in the GizmoSystem
- [x] **F1.2 Buttons**: Add toggle buttons in the game view window toolbar for each gizmos (rotation, translation, scale, select)
- [x] **F1.3 Selection**: Handle object hover & picking only if the selection tool is selected and the simulation is not running.
- [x] **F1.4 Display**: Make sure the gizmos don't scale with the movement of the editor camera. They must always remain an appropriate size.
- [x] **F1.5 Measure Tool**: Refactor the measure tool into a gizmo and add it to the gizmo system
- [x] **F1.6 Deselect**: Deselect game object when the user presses Esc
- [x] **F1.7 KeyBindings**: Implement a key bindings settings menu.

---

# 📥 🔵 Phase G: Mixamo Asset Pipeline

- [x] **ObjLoader**: Confirm ObjLoader is not needed anymore since AssimpLoader should be able to do the job.
- [x] **Import Validation**: Verify `Assimp` reads the `mNumVertices` and `mWeights` from the FBX to confirm the mesh is correctly bound to the skeleton.
- [x] **Bone Mapping (The Strip Utility)**:
  - Create a utility to map `mixamorig:LeftFoot` -> `LeftFoot`.
  - Ensure your `SkateboardPhysics` component can find these renamed bones for its "Foot Placement" logic.
- [x] **Coordinate System Fix**:
  - Mixamo uses **Y-Up**, but FBX often exports in **Centimeters**.
  - Apply a `0.01` scale factor during the `aiImportFile` process to bring the 180cm model down to 1.8 units and confirm the scale with the user.
- [x] **Animation Sampling**: Ensure the `AnimationComponent` samples the FBX at exactly 60fps to match the JBullet physics step.

---

# 📥 🔵 Phase H: Walking Around

- [x] **H1. Animations**: Add a method to load animation files in AssimpLoader and ResourceManager.
- [x] **H2. AnimationList**: Finish implementing AnimationsTab.kt so it renders the file items in a list. The items of the list should have readable names.
- [x] **H3. DragAndDrop**: Allow animation items from the AnimationsTab to be drag and dropped from the AnimationsTab to the Animator imgui panel so animations can be added to an entity's texturedModel animations list.
- [x] **H4. Create TransformComponent**: Strip the `transform` from `GameObject` and `Entity`. Create a standalone component.
- [x] **H5. Create RenderComponent**: Move `TexturedModel`, `shininess`, `reflectivity`, and `textureScale` into this component.
- [x] **H6. Create SkeletonComponent**: Extract the `Skeleton` from the `Entity` class. This component will be updated by the `Animator`.
- [x] **H7. Deprecate the 'Entity' Class**: If `GameObject` already supports components, move the logic there and delete the `Entity` class to avoid having two "base" objects.
- [x] **H8. Update Serializer**: Ensure `@Serializable` is applied to the new small components so the level can still be saved as JSON.
- [x] **H9. RendererSystem**: Update the renderer to look for GameObjects that have **BOTH** a `TransformComponent` AND a `RenderComponent`.
- [x] **H10. AnimationSystem**: Update this to look for GameObjects with a `SkeletonComponent`. It should apply the current frame of animation to the *component's* bones, not the model's bones.
- [x] **H11. Data Strip-down**: Remove any "heavy" logic (like matrix math) from `AnimatorComponent`. It should only contain variables (`time`, `speed`, `looping`).
- [x] **H12. System Registration**: Create `AnimationSystem.kt` and register it in your main engine loop or Koin module.
- [x] **H13. The "Query" Loop**: In `AnimationSystem.update()`, find all objects that have both an `AnimatorComponent` AND a `SkeletonComponent`.
- [x] **H14. Blending Logic**: Move the "Cross-fade" math from your old `Entity` class into the `AnimationSystem`.
- [x] **H15. Root Motion (Optional)**: If the animation moves the "Hips" bone, have the `AnimationSystem` apply that delta movement back to the `TransformComponent` so the physics body follows the feet.
- [x] **H16. CharacterModel**: Create a class for character models. The difference between a CharacterModel and a Textured Model is that the Character Model has Skeleton, and the TexturedModel does not.
- [ ] **H17. KDoc**: Add and fix KDoc :
  - [x] Add missing kdoc in Physics3D.kt, RigidBody3D.kt
  - [x] `computeGlobalTransformsRecursive()` in `SkeletonMath.kt` could use more detailed documentation
  - [ ] Animation sampling methods in `AnimationSampler.kt` need enhanced documentation
  - [ ] Various interpolation methods in `Interpolation.kt` need more detailed KDoc