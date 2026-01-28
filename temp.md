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

## 🟢 Phase C: Advanced Simulation [COMPLETE]

### 7. Real-World Scaling (Unit System)
- [x] **Unit Infrastructure**: Base 1.0m = 1.0 Unit established.
- [x] **Physics Calibration**: Gravity at -9.81 m/s² and real-world board mass/inertia.
- [x] **Asset Scaling**: Assimp normalization and manual overrides for legacy assets.
- [x] **Metric Grid**: Camera-following "infinite" grid with 0.1m/1.0m hierarchy.
- [x] **Measure Tool**: Editor-mode ruler with Metric/Imperial toggling.


## 🚨 Priority 1: Global Metric Grid Fix
- [ ] **Task 1.1: World-Space Decoupling:** Unparent the grid from the Skateboard/Player. Ensure it is rendered at World $Y = 0$.
- [ ] **Task 1.2: Infinite Procedural Grid:** - Replace the current "Grid Mesh" with a single large plane (Minimum 500m x 500m).
    - Update the Shader to use **World Position** (passed from Vertex to Fragment) rather than UV coordinates.
- [ ] **Task 1.3: Metric Hierarchy:**
    - **Minor:** Line every $0.1$ units (10cm), Alpha = $0.2$.
    - **Major:** Line every $1.0$ units (1m), Alpha = $0.5$.
    - **Axes:** Highlight $X=0$ (Red) and $Z=0$ (Blue).
- [ ] **Task 1.4: Depth & Blend Fix:** - Set `glDepthFunc(GL_LEQUAL)`.
    - Ensure the grid is rendered *after* the skybox but *before* the player to prevent the "floating" appearance.
---

## 🔴 Phase D: Active Tasks [NEXT STEPS]

### 8. Skeletal Animation Pipeline
- [ ] **Animation Samplers**: Implement Skeleton hierarchy traversal and LINEAR/STEP/CUBICSPLINE interpolation.
- [ ] **GPU Skinning**: Update PBR Vertex Shader to compute final vertex positions using the Matrix Palette (4-bone influence).
- [ ] **Animation Debugger**: Build ImGui window with clip selection, timeline scrubbing, and bone visualizer overlay.

### 9. Trick Detection System
- [ ] **The Labeler**: Logic for monitoring local-space rotation accumulation in air.
- [ ] **Trick UI**: Viewport overlay to display identified tricks (e.g., "Kickflip", "360 Shove-it").
- [ ] **TDD Validation**: Unit tests for rotation-to-string trick identification.



## Riding Integration
### Phase A: Physics Locking
- [ ] **Task 10.1:** Parent Player Model transform to the `BoardRig` transform with a vertical offset. Make sure the skateboard and player model are rendered in a proportional way, ask for confirmation.
- [ ] **Task 10.2:** Implement "Snap to Board" logic to prevent the player from drifting off during high-speed turns.

### Phase B: Animation Posing
- [ ] **Task 10.3:** Load and apply a static "Ride" pose (knees bent, arms out).
- [ ] **Task 10.4:** Implement **Procedural Spine Lean** tied to LS steering input.

### Phase C: The Push Mechanic
- [ ] **Task 10.5:** Implement a `PlayerState` manager.
- [ ] **Task 10.6:** Create the "Push" animation logic (trigger physics impulse based on animation frame).

## Milestone 11: Character Controller & Camera
### 11.1 State Management
- [ ] Implement `PlayerState` (Walking vs. Riding).
- [ ] Map Gamepad **Y Button** to state toggle with position offsets.

### 11.2 On-Foot Locomotion
- [ ] Implement Left Joystick movement relative to Camera Forward.
- [ ] Implement **A Button** Jump logic for Walking state.
- [ ] Add `WALK` and `JUMP` animation triggers.

### 11.3 Advanced Camera System
- [ ] Implement Right Stick **Orbit Camera** (Yaw/Pitch control).
- [ ] Create `CameraSettings` data class (FOV, Offset, Distance).
- [ ] Implement **Smooth Transition** between 'Walking' and 'Riding' presets.

### 11.4 World Alignment
- [ ] Ensure Skater and Board maintain proper Y-offset (Centimeters to Meters conversion).
- [ ] Implement Raycast-based floor snapping for the Walking state.


- App icon