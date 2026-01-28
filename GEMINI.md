# SkateSim MVP - Project Context

This is the main instructions :
@./AI_INSTRUCTIONS.md

This is the to-do list.

@./TODO.md

## Project Overview

**PAFSK8** is a high-fidelity skateboarding simulation engine built in Kotlin. It aims to combine 3D graphics, strict physics simulation, and robust editor tools to create a realistic skateboarding experience.

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


This is the obstacle list :

@./obstacles.md