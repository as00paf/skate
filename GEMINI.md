# SkateSim MVP - Project Context

## Project Overview

**SkateSim MVP** is a high-fidelity skateboarding simulation engine built in Kotlin. It aims to combine 3D graphics, strict physics simulation, and robust editor tools to create a realistic skateboarding experience.

**Current Status:** Core engine infrastructure (GLFW, OpenGL, ImGui) is initialized. Basic 2D/3D rendering and JBox2D physics were implemented, but the project is now pivoting to a **3D Bullet Physics** backend for strict collision and grind simulation.

## Tech Stack

*   **Language:** Kotlin (JVM Target 17)
*   **Graphics:** OpenGL 3.3+ (Forward Rendering)
*   **Math:** JOML (Java OpenGL Math Library)
*   **Physics:** Bullet Physics (via JBullet or LWJGL-Bullet)
*   **UI/Tweak Tool:** Dear ImGui
*   **Input:** LWJGL (GLFW)
*   **Data:** JSON for Level/Config persistence

## Core Architecture & State

### Dual-Mode System
*   **Edit Mode:** Physics paused, Free-fly camera, ImGui Gizmos active, Object picking/placement enabled.
*   **Play Mode:** Physics active, Spring-arm camera, Continuous Vectoring input, Session markers enabled.

### Systems
*   **The "Anchor" System:** The skater is a visual model parented to the board. On "Bail," the anchor breaks, and the skater becomes a "Tumble Cube" (Rigid Body) inheriting the board's momentum.
*   **Session Markers:** Keybind to "Drop Marker" (saves transform) and "Reset" (teleports board/skater back with zero velocity).

## Roadmap / Todo List

### Phase 1: Engine Foundation [Completed]
- [x] **Physics Migration:** Replace JBox2D with Bullet Physics.
- [x] **Main Loop:** Implement a fixed physics timestep (60Hz) with variable rendering.
- [x] **JOML Camera:** Build a 3D camera system with Raycast Clipping (using `btCollisionWorld.rayTest`) to prevent geometry clipping.
- [x] **Modular Tile System:** Create a system to spawn/render floor tiles with **Vertex Snapping** logic to ensure flush surfaces.

### Phase 2: Simulation Physics (The Board) [Completed]
- [x] **Raycast Vehicle:** Implement 4 raycasts from deck corners for suspension.
- [x] **Suspension Logic:** Calculate forces using Hooke's Law ($F = k \cdot x$).
- [x] **Continuous Vectoring Input:**
    - [x] Map high-frequency GLFW callbacks to an InputBuffer.
    - [x] Calculate "Flick Velocity" from stick/mouse movement.
    - [x] Apply Direct Torque based on flick direction (Local X/Y/Z).
- [x] **Semi-Auto Catch:** Magnetic angular impulse logic to snap board to 180° increments when within $\pm 20^\circ$ of level.

### Phase 3: Strict Collision & Grinds [Completed]
- [x] **Truck Geometry:** Model board as a compound body (Deck Box + Truck Cylinders) for physical "hooking".
- [x] **Testing Lab Obstacles:**
    - [x] **Rail:** Low-friction cylinder.
    - [x] **Ledge:** Box with 90° edges.
    - [x] **Kicker:** Simple wedge.
- [x] **Bail Logic:** Transition to "Tumble Cube" on high impact or bad landing orientation ($> 90^\circ$ from up).

### Phase 4: Sandbox & Editor Tools [Completed]
- [x] **ImGui Physics Tuner:** Sliders for Gravity, PopForce, Friction, SuspensionStiffness, and CatchStrength.
- [x] **Transform Gizmos:** 3D arrows in Edit Mode for modular tiles and obstacles.
- [x] **Trick Labeler:** Analyzer for Pitch/Yaw/Roll degrees to string together names (e.g., "Kickflip 180").
- [x] **Persistence:** Save/Load level layouts and physics configs to JSON.

### Additional Features [Completed]
- [x] **Game Controller Support:** Implement GLFW joystick/gamepad listeners.
- [x] **Assimp Enhancements:** Support for GLB and FBX model loading.
- [x] **Threading Support:** Coroutines integration via `JobSystem`.
- [x] **UI Layout:** Programmatic docking setup for editor windows.
- [ ] **Particle System:** 2D/3D visual effects.

## Mathematical Constraints
*   **Local Space Conversion:** Use `JOML Matrix4f.transformDirection()` for flick torques.
*   **Friction Tuning:** Rails ($< 0.1$), Deck ($> 0.5$).
*   **Stability:** Fixed delta time for `stepSimulation`.