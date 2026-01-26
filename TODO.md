# SkateSim MVP - TODO List

## Phase 5: High-Level Execution Plan

### 1. File Management & Setup
- [x] **Asset Extraction**: Unzip the player model from the assets folder and organize the resulting files.
    - [x] **Diagnostics**:
        - [x] Implement a real-time FPS counter.
        - [x] **Custom Physics Debug**: Implement manual wireframe rendering for Bullet collision shapes using DebugDraw.

### 2. Atmosphere & Environment
- [x] **Sky & Fog**: Implement a solid color sky and fog system with distance fading.
- [x] **Dynamic Skybox (Skater XL Style)**:
    - [x] **HDRI Generation**: Generate an equirectangular HDRI texture using nanobanana.
    - [x] **Sky Dome Rendering**: Implement a UV Sphere renderer for the Sky Dome.
    - [x] **Sky Shader**: Create a shader with u_skyTint, u_exposure, and fog blending.
    - [x] **Light Sync**: Sync Directional Light vector to Sky Dome rotation.
- [x] **Obstacle Palette**: Add Rail, Ledge, and Kicker Ramp prefabs to the editor library.

### 3. Analog Control & Skater Integration
- [x] **Skater Model**:
    - [x] Add player model to the scene.
    - [x] Link player model to physics rig.
- [x] **Controller Support**: Full Gamepad Support (analog steering, flick mechanics).

### 4. glTF Core Features Implementation
- **Mesh & Geometry**
    - [x] **Primitive Support**: Handle GL_TRIANGLES, GL_TRIANGLE_STRIP, and GL_TRIANGLE_FAN.
    - [x] **Standard Attributes**: Support Position (VEC3), Normal (VEC3), and Tangent (VEC4).
    - [x] **Texture Coordinates**: Support at least two UV sets (TEXCOORD_0, TEXCOORD_1).
    - [x] **Vertex Coloring**: Support COLOR_0 (RGB/RGBA) multiplication in the fragment shader.
    - [x] **Index Buffer Accessors**: Support for UNSIGNED_BYTE, UNSIGNED_SHORT, and UNSIGNED_INT.
- **PBR Material Model (Metallic-Roughness)**
    - [x] **Base Color**: Factor (RGBA) and Texture (RGBA) with sRGB-to-Linear conversion.
    - [x] **Metallic-Roughness**: Blue channel = Metallic, Green channel = Roughness.
    - [x] **Normal Map**: Support for tangent-space normals with scale influence.
    - [x] **Ambient Occlusion**: Support for the occlusionTexture (Red channel).
    - [x] **Emissive Map**: Support for emissiveTexture and emissiveFactor.
    - [x] **Alpha Pipeline**: Handle OPAQUE, MASK (alphaCutoff), and BLEND modes.
    - [x] **Double Sided**: Implement the doubleSided flag by disabling backface culling per-material.
- **Transformation & Hierarchy**
    - [x] **Node Hierarchy**: Proper parent-child matrix multiplication to maintain local-to-world transforms.
    - [x] **TRS Support**: Handle nodes defined by Translation, Rotation (Quaternions), and Scale.
    - [x] **Coordinate System**: Convert glTF Right-Handed (Y-Up) to your engine's internal coordinate system if different.
- **Skinning & Animation**
    - [x] **Joints & Weights**: Support for vertex skinning with up to 4 joint influences (JOINTS_0, WEIGHTS_0).
    - [ ] **Inverse Bind Matrices**: Proper calculation of the skin's global transform.
    - [ ] **Animation Samplers**: Implementation of LINEAR, STEP, and CUBICSPLINE interpolation.
- **Data & Buffers**
    - [x] **Binary Format**: Support for .glb (Binary glTF) container parsing.
    - [x] **External Buffers**: Loading of external .bin files and URI-based image assets.
    - [x] **Embedded Data**: Decoding of Base64 Data URIs within the JSON.

### 5. Editor Improvements
- [ ] **Enhanced Gizmo**: Upgrade to include a Scale Gizmo.
- [ ] **Physics Sync**: Instant reflection of scaling in physical collision bounds.
- [ ] **Workflow**: Add hotkeys (QWER) for tool switching.

### 6. Testing & Quality Assurance
- **Unit Testing Framework**
    - [ ] **Framework Setup**: Integrate JUnit 5 and MockK.
    - [ ] **Physics Unit Tests**: Create tests for the BoardRig and ContinuousVectoring logic.
    - [ ] **Math Validation**: Unit tests for JOML transformations.
- **Graphics Regression Testing**
    - [ ] **Frame Capture Utility**: Implement utility to capture framebuffer and save as .png.
    - [ ] **Offscreen Rendering**: Configure a test mode for rendering without a physical window.
    - [ ] **Visual Assertion Engine**: Compare new renders against "Gold Master" screenshots.
    - [ ] **Shader Validation**: Automated tests to ensure shaders compile successfully.
- **Architecture & Mocking**
    - [ ] **Interface Extraction**: Refactor Renderer and PhysicsWorld into interfaces.
    - [ ] **Input Simulation**: Create MockGamepad class for controller simulation.

### 7. 🛹 The Board Rig Technical Checklist

#### 1. Physics Assembly (Bullet Physics)
- [x] **Compound Shape**: Create a btCompoundShape with Deck and Trucks.
- [x] **Rigid Body Config**: Initialize with specific mass and calculated inertia.
- [x] **Material Properties**: Set appropriate friction for Deck (high) and Trucks (low).

#### 2. Raycast Suspension (The "Wheels")
- [x] **Suspension Points**: Define 4 local vectors for wheel positions.
- [x] **Spring Logic**: Implement Hooke’s Law ($F = k \cdot x + d \cdot v$) for each point.
- [x] **Impulse Application**: Apply forces at raycast origins for realistic leaning.

#### 3. Procedural "Pop" & "Flick" Logic
- [x] **Pop Point**: Localized upward impulse at tail with center downward force.
- [x] **Torque Mapping**: Local-space torques ($X, Y, Z$) based on analog stick flick velocity.

#### 4. State Management
- [x] **Grounded Check**: Consider grounded if at least 3 of 4 rays hit the floor.
- [ ] **Stance Logic**: Support Regular vs Goofy by mirroring input-to-torque mapping.

### 8. Trick Detection
- [ ] **The Labeler**: Rotation monitoring and trick identification system.
- [ ] **Unit tests**: Add unit tests for trick detection.