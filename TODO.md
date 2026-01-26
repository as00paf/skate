# SkateSim MVP - TODO List

## Phase 5: High-Level Execution Plan

### 1. File Management & Setup
- [x] **Asset Extraction**: Unzip the player model from the assets folder and organize the resulting files.
- [ ] **Diagnostics**:
    - [x] Implement a real-time FPS counter.
    - [x] **Custom Physics Debug**: Implement manual wireframe rendering for Bullet collision shapes using DebugDraw.

### 2. Atmosphere & Environment
- [x] **Sky & Fog**: Implement a solid color sky and fog system with distance fading.
- [x] **Dynamic Skybox (Skater XL Style)**:
    - [ ] **HDRI Generation**: Generate an equirectangular HDRI texture using nanobanana.
    - [x] **Sky Dome Rendering**: Implement a UV Sphere renderer for the Sky Dome.
    - [x] **Sky Shader**: Create a shader with u_skyTint, u_exposure, and fog blending.
    - [x] **Light Sync**: Sync Directional Light vector to Sky Dome rotation.
- [ ] **Obstacle Palette**: Add Rail, Ledge, and Kicker Ramp prefabs to the editor library.

### 3. Analog Control & Skater Integration
- [ ] **Skater Model**:
    - [ ] Add player model to the scene.
    - [ ] Link player model to physics rig.
- [ ] **Controller Support**: Full Gamepad Support (analog steering, flick mechanics).

### 4. Trick Detection
- [ ] **The Labeler**: Rotation monitoring and trick identification system.

### 5. Editor Improvements
- [ ] **Enhanced Gizmo**: Upgrade to include a Scale Gizmo.
- [ ] **Physics Sync**: Instant reflection of scaling in physical collision bounds.
- [ ] **Workflow**: Add hotkeys (QWER) for tool switching.
