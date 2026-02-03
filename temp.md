cleanup
- cube vertices in pose gizmo
- getcomponent in gameobject
- jobsystem.runio for file loading
- FileManager to save and load files, scene.loadFromFile
- fix max scroll
- cleanup assimploader, check if objloader is still neede
- profiler service
- trick analyzer vs trick detector

For the next phase of the project, here is what I would like to do :
- 

Create a todo list in a markdown format so I can copy and paste it for this next phase.

  Bugs
- Fix the scale translate arrows changing size when the camera moves.


Reports
- UI review to make it up to par with industry standards, compare features of Unity, Unreal, Godot and Stride
- Feature review to make sure everything is working as intended

# 🛠️ "Pose Master" Animation Authoring Tool

## 1. Foundation: Pose & Bone Manipulation
- [ ] **Bone Selection System**: Implement raycast-based bone selection in the 3D viewport to click and select joints directly on the Skater model.
- [ ] **Hierarchy Tree**: Create an ImGui window that displays the `SkeletalAnimation` joint hierarchy for manual bone selection.
- [ ] **TRS Gizmo Linking**: Bind the existing translation/rotation/scale gizmos to modify the `localTransform` of the selected bone.
- [ ] **Pose Mirroring**: Implement a utility to copy transformations from Left-side bones to Right-side bones (and vice-versa) across the X-axis.
- [ ] **Pose Presets**: Create a JSON-based system to save and load static poses (e.g., `ollie_crouch.json`, `kickflip_catch.json`).

## 2. Timeline & Keyframing Engine
- [ ] **Timeline UI Bar**: Add a draggable timeline at the bottom of the editor with play, pause, stop, and frame-step buttons.
- [ ] **Keyframe Data Structure**: Define a `KeyframeSequence` that stores a list of bone transforms indexed by timestamps (in seconds or frames).
- [ ] **Interpolation Logic**: Implement Slerp (Spherical Linear Interpolation) for bone rotations to ensure smooth movement between keyframes.
- [ ] **Keyframe Manipulation**:
    - [ ] Add/Delete keyframes at the current timeline position.
    - [ ] Drag existing keyframe markers to adjust animation timing.
- [ ] **Onion Skinning**: Render semi-transparent "ghost" meshes of the previous and next keyframes to visualize the motion arc.

## 3. Skate-Specific Rigging & Constraints
- [ ] **Board-Relative Locking**: Implement a constraint that locks the skater's feet bones to the skateboard's coordinate space.
- [ ] **Stance Auto-Flip**: Add a global toggle to mirror an entire animation sequence from **Regular** to **Goofy** stance.
- [ ] **Ground Alignment Tool**: Add a "Snap to Ground" feature that shifts the entire skeleton vertically so the lowest bone (wheels/feet) touches $Y=0$.
- [ ] **Root Motion Toggle**: Allow the option to extract the forward movement of the Hips bone and apply it as world-space velocity for the `GameObject`.

## 4. Export & Pipeline Integration
- [ ] **Native Format Exporter**: Create a serializer to export animations to the engine's internal `.skanim` format (binary or JSON).
- [ ] **Animation Metadata**: Support tagging specific frames with "Events" (e.g., a "Pop" sound effect trigger or "Collision Enabled" trigger).
- [ ] **Scale Validation**: Add an automated check to ensure the exported animation matches the standard 1.80m Skater scale.
- [ ] **Hot-Reloading**: Ensure the `AnimationComponent` can refresh and play a newly saved animation without a scene restart.

## 5. Advanced Refinement Tools
- [ ] **Dope Sheet View**: Expand the timeline into a spreadsheet-style view to edit individual bone tracks.
- [ ] **Easing Curves**: Implement Bezier curves for keyframe transitions to allow for "snappy" snaps or "slow-mo" catches.
- [ ] **Bone Weight Visualizer**: Add a debug overlay to see which vertices are influenced by the currently selected bone.

# 📥 Mixamo Asset Pipeline

- [ ] **Import Validation**: Verify `Assimp` reads the `mNumVertices` and `mWeights` from the FBX to confirm the mesh is correctly bound to the skeleton.
- [ ] **Bone Mapping (The Strip Utility)**:
    - Create a utility to map `mixamorig:LeftFoot` -> `LeftFoot`.
    - Ensure your `SkateboardPhysics` component can find these renamed bones for its "Foot Placement" logic.
- [ ] **Coordinate System Fix**:
    - Mixamo uses **Y-Up**, but FBX often exports in **Centimeters**.
    - Apply a `0.01` scale factor during the `aiImportFile` process to bring the 180cm model down to 1.8 units.
- [ ] **Animation Sampling**: Ensure the `AnimationComponent` samples the FBX at exactly 60fps to match the JBullet physics step.

# 🖼️ Asset Pipeline: Texture & Material Fixes

- [ ] **Path Stripping**: Implement a utility in `ShaderLoader` or `ModelLoader` to strip absolute directory paths from texture metadata (e.g., `C:/Path/To/Texture.png` -> `Texture.png`).
- [ ] **Fallback Texture**: Implement a "Pink Checkerboard" fallback texture. If a texture is missing, the engine should log a warning but still load the model so you don't get a crash.
- [ ] **Search Heuristic**: If a texture isn't in the model folder, have the engine check a global `textures/` folder as a secondary search location.
- [ ] **Y-Axis Flip**: FBX textures often have inverted UVs compared to OpenGL. Add a toggle in the loader to `aiProcess_FlipUVs`. Make sure to add the constant in the right place.

# 📸 Java Pose-to-Bone Tool: TODO

## 📂 Phase 1: Image Processing (BoofCV)
- [ ] **Image Loading**: Use BoofCV to load the reference image and convert it to a `Planar<GrayF32>` or `InterleavedU8`.
- [ ] **Pre-processing**: Use BoofCV to normalize brightness and resize the image to a square (e.g., 256x256) so the AI can read it easily.

## 🧠 Phase 2: Landmark Detection (ONNX Runtime Java)
- [ ] **Load MoveNet Model**: Use the **ONNX Runtime Java API** to load a `.onnx` version of MoveNet (Google’s lightning-fast pose model).
- [ ] **Inference**: Pass the BoofCV-processed image into the model.
- [ ] **Coordinate Output**: Extract the 17 key points (joints) provided by the model.

## 🦴 Phase 3: Bone Mapping (Engine Side)
- [ ] **Line Drawing**: In the "Skate Lab" UI, draw lines between the joints (e.g., Ankle to Knee) over the image to show the "Detected Skeleton."
- [ ] **Bone Orientation**: Calculate the 3D rotation (Quaternions) between joints (e.g., "Which way is the shin pointing?").
- [ ] **Skeleton Override**: Apply those rotations to your `Skater` model's bones via your existing `Bone Override` system.

## 🛹 Phase 4: Deck Mapping
- [ ] **Foot-to-Deck Projection**: Take the "Ankle" and "Toe" joints and find their intersection point on your 3D Skateboard model.
- [ ] **Save Pose**: Export the resulting configuration as a `.json` file for use in your "Pressure Map" trick system.

## 18.6. Interaction & Gizmos
- [ ] **Buttons**: Add buttons in the game view window for the gizmos (rotation, translation, scale)
- [ ] **Display**: Make sure the gizmos dont scale with the movement of the editor camera. 
- [ ] **Measure Tool**: Ensure the measure tool does not interfere with the gizmos and vice versa 