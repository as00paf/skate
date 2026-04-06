# SkateSim Engine - QWEN.md

## Project Overview

**SkateSim** is a sophisticated skateboarding simulation engine built in Kotlin using the LWJGL3 framework. It combines realistic physics simulation with advanced rendering capabilities to create an authentic skateboarding experience. The engine follows Entity-Component-System (ECS) architecture with a strong emphasis on clean code, dependency injection (Koin), and test-driven development.

### Core Technologies
- **Language**: Kotlin (JVM 17)
- **Graphics**: LWJGL 3, OpenGL 3.3+, Dear ImGui for UI
- **Physics**: JBullet (Bullet Physics), libbulletjme
- **Math**: JOML (Java OpenGL Math Library)
- **Dependencies**: Koin (DI), Coroutines, kotlinx.serialization
- **Assets**: Assimp for 3D model loading (glTF, FBX, OBJ, DAE)

### Architecture
The engine follows a modular architecture with clear separation between:
- **Rendering Engine**: Forward rendering with PBR (Metallic-Roughness workflow)
- **Physics Engine**: JBullet-based with specialized skateboard physics
- **Animation System**: Skeletal animation with GPU skinning
- **Editor Tools**: "Skate Lab" with gizmos, inspectors, and prefab system
- **Asset Management**: Centralized resource loading and caching

## Building and Running

### Prerequisites
- Java 17+
- Gradle (wrapper included)

### Build Commands
```bash
# Build the project
.\gradlew.bat build

# Run the application
.\gradlew.bat run

# Run tests
.\gradlew.bat test

# Run with increased heap size (recommended for physics simulation)
.\gradlew.bat run --info
```

### Key Configuration
- Main class: `com.pafoid.skate.MainKt`
- JVM args include: `-Xverify:none` (for JNI compatibility)
- Max heap size for tests: 2GB

## Development Conventions

### Coding Standards
- **Null Safety**: Strict no `!!` policy; use safe calls (`?.`) and Elvis operator (`?:`)
- **Dependency Injection**: Use Koin for all dependencies
- **Idiomatic Kotlin**: Leverage coroutines, sealed classes, extension functions
- **SOLID Principles**: Clear separation of concerns between physics, rendering, and UI
- **Localization**: All UI strings must be in `strings.properties`

### Project Structure
```
src/
├── main/
│   └── kotlin/com/pafoid/skate/
│       ├── engine/           # Core engine systems
│       │   ├── animation/    # Skeletal animation system
│       │   ├── assets/       # Asset loading and management
│       │   ├── di/          # Dependency injection modules
│       │   ├── render/      # Rendering pipeline
│       │   ├── physics3d/   # Physics engine integration
│       │   ├── scenes/      # ECS implementation
│       │   └── ...          # Other engine components
│       └── skateboard/      # Skate-specific logic
└── test/                    # Unit tests with JUnit5 + MockK
```

### Key Systems

#### Animation System
- **GPU Skinning**: Up to 4 bone influences per vertex
- **Interpolation**: LINEAR (SLERP for rotations), STEP, CUBIC_SPLINE
- **Formats**: glTF 2.0, FBX, DAE via Assimp
- **Blending**: Cross-fading between animation clips
- **Procedural**: Bone override system for real-time adjustments

#### Physics System
- **Skateboard Physics**: Raycast suspension with Hooke's Law (F = kx + dv)
- **Real-world Scaling**: 1.0m = 1.0 unit with proper gravity (-9.81 m/s²)
- **Stance Engine**: Support for Regular/Goofy and Switch/Nollie/Fakie states
- **Pop Mechanics**: Leveraged tail/nose impulses for ollie physics
- **Fixed Timestep**: Deterministic physics regardless of render framerate

#### Rendering Pipeline
- **PBR Workflow**: Metallic-Roughness with full texture map support
- **Lighting**: Directional light with sky dome synchronization
- **Fog System**: Distance-based atmospheric effects
- **2D Rendering**: Sprite batching for UI elements
- **Selection**: Pixel-perfect picking via dedicated framebuffer

#### Editor ("Skate Lab")
- **Gizmos**: Translation, rotation, scale tools
- **Asset Browser**: Multi-tab browser for models, textures, prefabs
- **Scene Management**: JSON serialization for save/load
- **Console System**: Dual-tab logging (Engine/Editor)
- **Profiler**: Real-time performance metrics

## Testing Strategy

### Test Categories
- **Unit Tests**: Physics constants, collision detection, math operations
- **Integration Tests**: Asset loading, component interactions
- **Regression Tests**: Graphics rendering, physics stability

## Key Features

### Skateboard Physics
- **Raycast Suspension**: 4-point spring system with damping
- **Procedural Pop**: Localized tail impulses with center-of-mass leverage
- **Input Mapping**: Continuous vectoring for local-space torques
- **Stability Logic**: Snap-to-board mechanics for high-speed turns

### Animation Pipeline
- **GPU Skinning**: Hardware-accelerated vertex deformation
- **Blending System**: Smooth transitions between animation clips
- **Procedural Overrides**: Real-time bone manipulation
- **Mixamo Integration**: Automated bone mapping and scaling

### Editor Tools
- **Prefab System**: Grid-based thumbnail organization
- **Undo/Redo**: Full command pattern implementation
- **Gizmo System**: Interactive 3D manipulation tools
- **Asset Management**: Centralized resource caching

## Known Issues & Improvements

### Critical Areas
- **Null Safety**: Remaining `!!` operators need replacement with proper null handling
- **Resource Management**: Potential memory leaks in asset loading/unloading
- **Animation Blending**: Timing issues in crossfade transitions

### Performance Considerations
- **Object Allocation**: Minimize garbage collection in hot loops
- **Batching**: Optimize rendering and physics updates
- **Threading**: Proper separation of physics and rendering threads

### Architecture Goals
- **Modularity**: Continued separation of concerns
- **Testability**: Increase test coverage for complex systems
- **Documentation**: Expand KDoc coverage for complex algorithms

## Git Workflow

### Branching Strategy
- **Feature Branches**: `feature/description-of-task`
- **Bug Fixes**: `bug/description-of-bug`
- **Atomic Commits**: Small, focused changes with descriptive messages

## Project Status

The project maintains high code quality standards with extensive testing, proper documentation, and continuous refactoring efforts.

## Development Environment Protocol
- **OS**: Windows 10 (Native PowerShell / Windows Terminal).
- **Shell**: PowerShell 7+ (Avoid Bash/Sh/Zsh).
- **Build Tool**: `gradlew.bat` (Not `./gradlew`).
- **File System**: Windows-style paths (`C:\path\to\file`).
- **Commands**: Use PowerShell cmdlets (`Remove-Item`, `Copy-Item`, `Expand-Archive`) or standard Windows binaries.
