# Skate Project Context

## Project Overview

**Skate** is a Kotlin-based software project set up for graphics or game development. It utilizes the **Gradle** build system and depends on the **LWJGL (Lightweight Java Game Library)** ecosystem, including bindings for GLFW, OpenGL, OpenAL, and Assimp.

**Current Status:** The project structure is initialized, but the source directories (`src/main/kotlin`, `src/test/kotlin`) are currently empty.

## Tech Stack

*   **Language:** Kotlin (JVM Target 17)
*   **Build System:** Gradle (Wrapper provided)
*   **Key Libraries:**
    *   LWJGL (Core, Assimp, GLFW, OpenAL, OpenGL, STB)
    *   JOML (Java OpenGL Math Library)
    *   LWJGL3-AWT (AWT integration)
*   **Testing:** JUnit Platform, Kotlin Test

## Building and Running

Since this is a Gradle project, use the provided wrapper scripts (`gradlew` or `gradlew.bat`).

### Build
To compile the project and run checks:
```bash
./gradlew build
```

### Run
*Note: No main application entry point has been defined yet.*
Once a main class is created, you can typically run it using a Gradle task (e.g., `run` if the `application` plugin is applied, which is not yet present in `build.gradle`).

### Testing
To execute unit tests:
```bash
./gradlew test
```

## Development Conventions

*   **Code Style:** The project is configured to use the official Kotlin code style (`kotlin.code.style=official` in `gradle.properties`).
*   **Directory Structure:** Follows the standard Gradle/Maven directory layout:
    *   `src/main/kotlin`: Application source code.
    *   `src/main/resources`: Non-code assets (shaders, textures, configuration).
    *   `src/test/kotlin`: Unit tests.
