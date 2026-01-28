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
- [ ] **Test Tool**: Add a "Mirror Pose" toggle (e.g., if you pose the left leg, it copies to the right) to speed up stance creation.

### 12. Trick Detection System
- [ ] **The Labeler**: Logic for monitoring local-space rotation accumulation in air.
- [ ] **Trick UI**: Viewport overlay to display identified tricks (e.g., "Kickflip", "360 Shove-it").
- [ ] **TDD Validation**: Unit tests for rotation-to-string trick identification.

### 13. Final Polish
- [ ] **App Icon**: Design and integrate native window icon.