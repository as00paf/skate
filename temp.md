cleanup
- cube vertices in pose gizmo
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


---

# 🛠️ 🔵 Phase I: "Pose Master" Animation Authoring Tool

## 1. Foundation: Pose & Bone Manipulation

- [ ] **Bone Selection System**: Implement raycast-based bone selection in the 3D viewport to click and select joints
  directly on the Skater model.
- [ ] **Hierarchy Tree**: Create an ImGui window that displays the `SkeletalAnimation` joint hierarchy for manual bone
  selection.
- [ ] **TRS Gizmo Linking**: Bind the existing translation/rotation/scale gizmos to modify the `localTransform` of the
  selected bone.
- [ ] **Pose Mirroring**: Implement a utility to copy transformations from Left-side bones to Right-side bones (and
  vice-versa) across the X-axis.
- [ ] **Pose Presets**: Create a JSON-based system to save and load static poses (e.g., `ollie_crouch.json`,
  `kickflip_catch.json`).

## 2. Timeline & Keyframing Engine

- [ ] **Timeline UI Bar**: Add a draggable timeline at the bottom of the editor with play, pause, stop, and frame-step
  buttons.
- [ ] **Keyframe Data Structure**: Define a `KeyframeSequence` that stores a list of bone transforms indexed by
  timestamps (in seconds or frames).
- [ ] **Interpolation Logic**: Implement Slerp (Spherical Linear Interpolation) for bone rotations to ensure smooth
  movement between keyframes.
- [ ] **Keyframe Manipulation**:
  - [ ] Add/Delete keyframes at the current timeline position.
  - [ ] Drag existing keyframe markers to adjust animation timing.
- [ ] **Onion Skinning**: Render semi-transparent "ghost" meshes of the previous and next keyframes to visualize the
  motion arc.

## 3. Skate-Specific Rigging & Constraints

- [ ] **Board-Relative Locking**: Implement a constraint that locks the skater's feet bones to the skateboard's
  coordinate space.
- [ ] **Stance Auto-Flip**: Add a global toggle to mirror an entire animation sequence from **Regular** to **Goofy**
  stance.
- [ ] **Ground Alignment Tool**: Add a "Snap to Ground" feature that shifts the entire skeleton vertically so the lowest
  bone (wheels/feet) touches $Y=0$.
- [ ] **Root Motion Toggle**: Allow the option to extract the forward movement of the Hips bone and apply it as
  world-space velocity for the `GameObject`.

## 4. Export & Pipeline Integration

- [ ] **Native Format Exporter**: Create a serializer to export animations to the engine's internal `.skanim` format (
  binary or JSON).
- [ ] **Animation Metadata**: Support tagging specific frames with "Events" (e.g., a "Pop" sound effect trigger or "
  Collision Enabled" trigger).
- [ ] **Scale Validation**: Add an automated check to ensure the exported animation matches the standard 1.80m Skater
  scale.
- [ ] **Hot-Reloading**: Ensure the `AnimationComponent` can refresh and play a newly saved animation without a scene
  restart.

## 5. Advanced Refinement Tools

- [ ] **Dope Sheet View**: Expand the timeline into a spreadsheet-style view to edit individual bone tracks.
- [ ] **Easing Curves**: Implement Bezier curves for keyframe transitions to allow for "snappy" snaps or "slow-mo"
  catches.
- [ ] **Bone Weight Visualizer**: Add a debug overlay to see which vertices are influenced by the currently selected
  bone.

---

# 📸️ 🔵 Phase J: Pose-to-Bone Tool

## 📂 Phase 1: Image Processing (BoofCV)

- [ ] **Image Loading**: Use BoofCV to load the reference image and convert it to a `Planar<GrayF32>` or
  `InterleavedU8`.
- [ ] **Pre-processing**: Use BoofCV to normalize brightness and resize the image to a square (e.g., 256x256) so the AI
  can read it easily.

## 🧠 Phase 2: Landmark Detection (ONNX Runtime Java)

- [ ] **Load MoveNet Model**: Use the **ONNX Runtime Java API** to load a `.onnx` version of MoveNet (Google’s
  lightning-fast pose model).
- [ ] **Inference**: Pass the BoofCV-processed image into the model.
- [ ] **Coordinate Output**: Extract the 17 key points (joints) provided by the model.

## 🦴 Phase 3: Bone Mapping (Engine Side)

- [ ] **Line Drawing**: In the "Skate Lab" UI, draw lines between the joints (e.g., Ankle to Knee) over the image to
  show the "Detected Skeleton."
- [ ] **Bone Orientation**: Calculate the 3D rotation (Quaternions) between joints (e.g., "Which way is the shin
  pointing?").
- [ ] **Skeleton Override**: Apply those rotations to your `Skater` model's bones via your existing `Bone Override`
  system.

## 🛹 Phase 4: Deck Mapping

- [ ] **Foot-to-Deck Projection**: Take the "Ankle" and "Toe" joints and find their intersection point on your 3D
  Skateboard model.
- [ ] **Save Pose**: Export the resulting configuration as a `.json` file for use in your "Pressure Map" trick system.

Analyze the project and create a report to help me clean up the code that will include but not necessarily limited to :

Key bindings for editor camera movement
Change default binding for tools to 1,2,3,4 ...

Make PlayerStateManager a component ? Should be able to pair animation with state
Verify animation blending
Allow Rendering of RigidBody3D and BoxCollider3D

UI Review :
Move BoneTreeWindow to Skeleton component
Add option for debug render with lines for models
Fix dockspace
EditorCamera ImGui, presets
Make console text selectable/copyable
Button to reset to bind pose
