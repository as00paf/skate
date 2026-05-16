# SkateSim MVP (PAFSK8) - Project Context

## Project Overview

**PAFSK8** is a high-fidelity skateboarding simulation engine built in Kotlin. It aims to combine 3D graphics, strict physics simulation, and robust editor tools to create a realistic skateboarding experience.

## Tech Stack & Architecture

*   **Language:** Kotlin (JVM Target 17)
*   **Graphics:** OpenGL 3.3+ (Forward Rendering)
*   **Math:** JOML (Java OpenGL Math Library)
*   **Physics:** Bullet Physics (via JBullet or LWJGL-Bullet)
*   **UI/Tweak Tool:** Dear ImGui
*   **Input:** LWJGL (GLFW)
*   **Data:** JSON for Level/Config persistence
* **Architecture:** Hybrid ECS (Entity-Component-System). See `@docs/ECS_ARCHITECTURE.md`.

## Core Architecture & State

### Dual-Mode System
*   **Edit Mode:** Physics paused, Free-fly camera, ImGui Gizmos active, Object picking/placement enabled.
*   **Play Mode:** Physics active, Spring-arm camera, Continuous Vectoring input, Session markers enabled.

---

## AI Instructions & Mandates

The following mandates must be strictly adhered to. They establish the role, conventions, workflows, and testing
protocol for this workspace.

@AI_INSTRUCTIONS.md

---

## Project Status & Tracking

Current goals, known issues, and general project status. Ensure `docs/TODO.md` is maintained.

@docs/TODO.md
@docs/obstacles.md
