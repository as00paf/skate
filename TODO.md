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

---

## 🔴 Phase C: Active Tasks [NEXT STEPS]

### 7. Real-World Scaling (Unit System)
# 📏 Unit System & Scaling Implementation

## 🎯 Objective
Establish **1.0 Engine Unit = 1.0 Meter** as the global standard. All physics, assets, and UI measurements must derive from this base to ensure simulation accuracy.

---

#### 7.1. Core Unit Utility (TDD Required)
- [x] **Task 1.1:** Create a `Units` utility class/object in Kotlin.
  - Define constants: `M_TO_CM = 100.0`, `IN_TO_M = 0.0254`, `FT_TO_M = 0.3048`.
  - Implement conversion functions: `toMeters(value, unit)`, `fromMeters(value, unit)`.
- [x] **Task 1.2 (TDD):** Write unit tests to verify conversion accuracy (e.g., 12 inches must equal 0.3048m within a 0.0001 epsilon).

#### 7.2. Physics Calibration (Bullet Physics)
- [x] **Task 2.1:** Set global gravity to exactly `-9.81` m/s².
- [x] **Task 2.2:** Update `BoardRig` dimensions using real-world metric data:
  - Deck: ~0.8m x 0.2m.
  - Wheelbase: ~0.35m.
  - Mass: ~1.8kg.
- [x] **Task 2.3 (TDD):** Implement a "Freefall Test" case. Assert that a rigid body dropped from 1.0m hits the floor at approx 0.45s ($t = \sqrt{2h/g}$).

#### 7.3. Asset Pipeline Scaling
- [x] **Task 3.1:** Update `ModelLoader` (Assimp) to handle unit normalization.
  - Check for "Unit" metadata in glTF/FBX files.
  - Apply a global `u_modelScale` multiplier to the root node to ensure the model matches the 1.0m engine scale.
- [x] **Task 3.2:** Recalibrate the Editor Grid.
  - Set major grid lines to 1.0m.
  - Set minor grid lines to 0.1m (10cm).

#### 7.4. UI & Interaction
- [x] **Task 4.1:** Implement **ImGui Unit Toggle** in the settings (Metric vs. Imperial).
- [x] **Task 4.2:** Update the Speedometer to calculate velocity in `m/s` and convert to `km/h` or `mph` for the display.
- [x] **Task 4.3:** Add a "Measure Tool" to the editor to verify distances between obstacles in meters/feet.

---

### 8. Skeletal Animation Pipeline
- [ ] **Animation Samplers**: Implement Skeleton hierarchy traversal and LINEAR/STEP/CUBICSPLINE interpolation.
- [ ] **GPU Skinning**: Update PBR Vertex Shader to compute final vertex positions using the Matrix Palette (4-bone influence).
- [ ] **Animation Debugger**: Build ImGui window with clip selection, timeline scrubbing, and bone visualizer overlay.

### 9. Trick Detection System
- [ ] **The Labeler**: Logic for monitoring local-space rotation accumulation in air.
- [ ] **Trick UI**: Viewport overlay to display identified tricks (e.g., "Kickflip", "360 Shove-it").
- [ ] **TDD Validation**: Unit tests for rotation-to-string trick identification.
