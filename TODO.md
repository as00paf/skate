# SkateSim MVP - TODO List

## Phase 5: High-Level Execution Plan

### 1. File Management & Setup
- [x] **Asset Extraction**: Unzip the player model from the assets folder and organize the resulting files.
- [ ] **Diagnostics**:
    - [x] Implement a real-time FPS counter.
    - [x] **Custom Physics Debug**: Implement manual wireframe rendering for Bullet collision shapes using DebugDraw.

### 2. Atmosphere & Environment
- [x] **Sky & Fog**: Implement a solid color sky and fog system with distance fading.
- [x] **Cloud System**: Added procedurally spawned drifting cloud sprites high in the sky.
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
