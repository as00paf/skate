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

### 4. glTF Core Features Implementation [COMPLETE]
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
    - [x] **Inverse Bind Matrices**: Proper calculation of the skin's global transform.
    - [ ] **Animation Samplers**: Implementation of LINEAR, STEP, and CUBICSPLINE interpolation.
- **Data & Buffers**
    - [x] **Binary Format**: Support for .glb (Binary glTF) container parsing.
    - [x] **External Buffers**: Loading of external .bin files and URI-based image assets.
    - [x] **Embedded Data**: Decoding of Base64 Data URIs within t3he JSON.

### 5. Testing & Quality Assurance
- **Unit Testing Framework**
    - [x] **Framework Setup**: Integrate JUnit 5 and MockK.
    - [x] **Physics Unit Tests**: Create tests for the BoardRig and ContinuousVectoring logic.
    - [x] **Math Validation**: Unit tests for JOML transformations.
- **Graphics Regression Testing**
    - [x] **Frame Capture Utility**: Implement utility to capture framebuffer and save as .png.
    - [x] **Offscreen Rendering**: Configure a test mode for rendering without a physical window.
    - [x] **Visual Assertion Engine**: Compare new renders against "Gold Master" screenshots.
    - [x] **Shader Validation**: Automated tests to ensure shaders compile successfully.
- **Architecture & Mocking**
    - [x] **Interface Extraction**: Refactor Renderer and PhysicsWorld into interfaces.
    - [x] **Input Simulation**: Create MockGamepad class for controller simulation.

### 6. Editor Improvements
- [x] **Enhanced Gizmo**: Upgrade to include a Scale Gizmo.
- [x] **Physics Sync**: Instant reflection of scaling in physical collision bounds.
- [x] **Workflow**: Add hotkeys (QWER) for tool switching.

### 7. Performance & Boot Sequence
- [x] **Boot Strapping:** Refactor the `init()` sequence to use Kotlin Coroutines.
  - The Main Thread must immediately open the window and show the Splash Screen.
  - Heavy assets must load on `Dispatchers.IO`.
- [x] **Splash Screen:** - Generate a splash screen named `splash_screen.png` based on this prompt : A professional game splash screen for a skateboarding simulation titled 'PAFSK8' that looks like a Thrasher magazine cover
- [x] **Splash Screen:** - Implement a full-screen quad shader to display the `splash_screen.png`.
  - Integrate a "Loading Progress" variable that updates based on completed tasks.
- [x] **OpenGL Context Sync:** Use a thread-safe queue to "upload" loaded textures and meshes to the GPU once the background worker finishes parsing them.
- [x] **Thread Monitoring:** Add a debug view in ImGui showing CPU usage per thread (Main, Physics, Asset-IO).
  Async Boot & Splash UX :
- [ ] **Task 0.1: Window Lifecycle & Focus:** - Set `GLFW_VISIBLE` to false initially.
  - Create the window, then call `glfwShowWindow` and `glfwFocusWindow` once the Splash Shader is ready.
- [ ] **Task 0.2: Loading State Machine:** - Implement `EngineState { BOOTING, LOADING, RUNNING }`.
  - Create a `LoadingProgress` atomic float (0.0 to 1.0) shared between threads.
- [ ] **Task 0.3: Background Asset Thread:** - Use `Dispatchers.IO` to unzip assets and run Assimp parsing.
  - **Crucial:** Background thread must *not* call OpenGL functions. It must store raw data in a `ConcurrentLinkedQueue`.
- [ ] **Task 0.4: Main Thread Splash Loop:** - Render a full-screen quad with the splash image.
  - Render an ImGui progress bar synced to `LoadingProgress`.
- [ ] **Task 0.5: GPU Upload & Handoff:** - On the Main Thread, poll the `ConcurrentLinkedQueue`.
  - Call `glBufferData` only when the Main Thread sees data is ready.
  - Transition to `RUNNING` with a 1-second alpha fade-out.

### 8. 🛹 The Board Rig Technical Checklist

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
- [x] **Preferred Stance Logic**: Support Regular vs Goofy by mirroring input-to-torque mapping.
- [x] **Current Stance Logic**: Support Regular, Switch, Nollie and Fakie to define how the player is standing on the board.    

### 9. Professional Level Editor
- [x] **Task 7.1: UI Styling & Icons:** - Apply a custom 'Pro Dark' theme (like a Slate or Charcoal gray).
  - Merge FontAwesome .ttf into the ImGui font atlas for buttons.
- [x] **Task 7.2: Scene Serialization (JSON):** - Integrate a JSON library (e.g., Jackson or kotlinx.serialization).
  - Implement `SceneSerializer` to save/load all objects in the Hierarchy. (Using GSON)
- [x] **Task 7.3: Viewport Overlays:** - Move 'Play/Stop' and 'FPS' to a transparent `ImGuiWindowFlags_NoDecoration` overlay inside the 3D viewport.
- [x] **Task 7.4: Drag-and-Drop:** - Allow dragging items from the 'Prefabs' window directly into the 3D Viewport.
- [x] **Task 7.5: Modern Property Widgets:** - Implement 'Color Pickers' for materials and 'Draggable Floats' for all transform values.
- [x] **Task 7.6: File Dialog Integration:** - Add 'Save As...' and 'Open...' buttons using a native file picker (like TinyFileDialogs).
- [x] **Task 7.7: Splash Fix:** Correct the UV coordinates in the Splash Shader to fix the vertical flip (Y-axis inversion).
- [x] **Task 7.8:** **System Settings Persistence:**
  - Create a `SettingsManager` to save/load `settings.json`.
  - Implement ImGui toggles for Windowed vs. Borderless Fullscreen and other available options
  - Persist resolution and V-Sync settings.
- [x] **Task 7.9:** **Input Visualization (Gamepad Overlay):**
  - Render a semi-transparent HUD element showing an Xbox/PlayStation controller.
  - Use dynamic stick and button highlights.
- [x] **Task 7.10:** Scene Serialization: Implement JSON Save/Load for all level GameObjects.
- [x] **Task 7.11:** Viewport Overlays: Move simulation controls and FPS to a transparent viewport layer.


### 10. Trick Detection
- [ ] **The Labeler**: Rotation monitoring and trick identification system.
- [ ] **ImGui Debug Window**: ImGui Window to display the tricks identified
- [ ] **Unit tests**: Add unit tests for trick detection.

### 11. Skeletal Animation & UI Integration
- [ ] **Animation Pipeline:** Implement Skeleton hierarchy, SLERP interpolation, and Global Transform calculation.
- [ ] **GPU Skinning:** Update Vertex Shader for 4-bone influence and pass the Matrix Palette.
- [ ] **ImGui Debugger Window:** Build a playback UI with clip selection, timeline scrubbing, and a bone visualizer overlay.