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
- [ ] **Test T5.2: Frame-Rate Independence**: Run the same "Push" test at $30fps$ and $120fps$; verify the final displacement is identical within a 1% margin. (FAILED - See Task 17.4)
- [x] **Test T5.3: Access Violation Regression**: Repeatedly instantiate and destroy 100 `PhysicsRigidBody` objects to ensure the `-Xverify:none` flag and JNI bindings are stable. (PASSED)

# 🏗️ Phase 17. Architecture & Infrastructure Optimization
- [x] **Dependency Migration**: Move all dependency versions from `build.gradle.kts` to `libs.versions.toml` for centralized version management.
- [x] **Resource Management System**: Implement a centralized system to handle efficient loading, caching, and unloading of engine assets (Textures, Shaders, Models).
- [x] **Dependency Injection**: Integrate **Koin** to manage component lifecycles, improve testability, and decouple engine systems.
- [ ] **Standardized Serialization**: Implement a robust, project-wide serialization strategy (e.g., using kotlinx.serialization) for handling save states, configurations, and object properties.

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
- [ ] **Middle-Mouse Pan**: Implement `View Panning` in the editor camera. Pressing **MMB** + Mouse move shifts the camera on its local Up/Right axes. Ensure mouse capture works as expected.
- [ ] **Standard Editor Shortcuts**:
  - [ ] **Ctrl + C / V / X**: Copy, Paste, and Cut selected `GameObject` (handling deep clones).
  - [ ] **Ctrl + Z / Y**: Fully implement a Undo/Redo stack for Transform changes and object deletions.

# 🔧 19. Physics Implementation Fixes (Deferred)
- [ ] **Task 19.1: Friction Propagation**: Fix `RigidBody3D` to ensure the `friction` property is correctly applied to the Bullet `rawBody` during initialization and runtime updates.
- [ ] **Task 19.2: Rolling Resistance**: Implement `linearDamping` logic in `RigidBody3D` to satisfy the `rollResistance` test.
- [ ] **Task 19.3: Steering Geometry**: Implement local-space steering forces in `SkateboardPhysics` to translate board lean (roll) into actual turning (yaw).
- [ ] **Task 19.4: Fixed Timestep Integration**: Implement an accumulator-based fixed timestep loop in `Engine` or `Physics3D` to ensure deterministic physics regardless of render framerate.
- [ ] **Task 19.5: Unit Tests**: Fix and add missing physics unit tests.

# 🌍 20. Localization & String Management System (i18n)

## 🏗️ Phase 20.1: Infrastructure Setup
- [ ] **Define String Schema**: Create a centralized directory `src/main/resources/values/` to hold string data.
- [ ] **Initial String Resource**: Create `strings.properties` (the "Default" language) to host all current UI labels.
- [ ] **String Manager Singleton**: Implement `StringManager` (or `R`) class to handle the loading and retrieval of strings via keys (e.g., `R.string("lbl_reset_board")`).
- [ ] **Koin Integration**: Register the `StringManager` in your Koin module for easy injection into UI components.

## 🛠️ Phase 20.2: Code Refactoring (The "Hardcoded Hunt")
- [ ] **Refactor "Skate Lab" UI**: Replace all `ImGui.text("...")` and button labels in `LevelEditorSceneInitializer.kt` with localized lookups.
- [ ] **Refactor Inspector Labels**: Move component field names (e.g., "Mass", "Friction") from `SkateboardPhysics.kt` into the properties file.
- [ ] **Refactor Trick Analyzer**: Move trick names ("Kickflip", "Ollie") to `tricks.properties` to allow for "Trick Name" variations in the future.

## 🚀 Phase 20.3: Advanced Features (Android-Inspired)
- [ ] **String Formatting**: Implement support for placeholders (e.g., `lbl_speed=Speed: %f m/s`) using `String.format()`.
- [ ] **Pluralization Support**: Add a logic handler for quantities (e.g., "1 Trick" vs "5 Tricks").
- [ ] **Language Switching Logic**: Add a setting in the Editor to toggle between `strings_en.properties` and `strings_fr.properties` (or others) at runtime without restarting the engine.

## 🧪 Phase 20.4: Validation & Quality
- [ ] **Missing Key Fallback**: Ensure that if a key is missing, the engine returns the key name (e.g., `!!MISSING_KEY!!`) instead of crashing.
- [ ] **Unit Test - String Retrieval**: Verify that the `StringManager` correctly pulls values from the resources folder.
- [ ] **Unit Test - Format Validation**: Verify that string placeholders are filled correctly with float and integer values.