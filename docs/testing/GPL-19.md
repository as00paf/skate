# GPL-19: Exported game starts the chosen graphics backend, manual test

The game picks its backend from `-Dskate.graphicsBackend`, then `game.settings.json` (`graphicsBackend`), then the project setting. Vulkan opens a window with no GL context and a Vulkan device. If Vulkan cannot start, the game logs one error line, writes `graphicsNote` to `game.settings.json` and runs on OpenGL. Do not enable validation (the nightly layer is not used; wait for the LunarG SDK).

1. `.\gradlew.bat gameJar`, then `.\gradlew.bat run`. Create a **new project**, set its graphics backend to **Vulkan** in Project Settings, then **File > Export Project** (Windows target). Run `run-game.bat` from a console. The game shows MainScene. The console shows one WARN line: `Graphics backend: VULKAN. Vulkan backend is experimental: …`.
2. Quit. In `builds\game.settings.json` set `"graphicsBackend": "OPENGL"` and run again. The game starts on OpenGL with no backend line, and post-processing and shadows look like the editor's.
3. Set it back to `"VULKAN"`. Run `java -Dskate.graphicsBackend=opengl -jar skate-game.jar` in `builds`. The property wins and the game starts on OpenGL.
4. Simulate a machine without Vulkan: run `java -Dorg.lwjgl.vulkan.libname=missing-vulkan.dll -jar skate-game.jar`. The game starts on OpenGL. The console shows one ERROR line `Graphics backend VULKAN could not start (…); falling back to OPENGL.`, and `game.settings.json` now has a `graphicsNote` ending in "The game ran on OPENGL." (`graphicsBackend` is still `VULKAN`).
5. Run `run-game.bat` again normally. The game starts on Vulkan and `graphicsNote` is gone.

Automated: `.\gradlew.bat :test --tests "*GameGraphicsTest"` (selection and fallback, no GPU). On a GPU: `set SKATE_RHI=vulkan`, `set SKATE_VK_VALIDATION=0`, then `.\gradlew.bat :test --tests "*GameVulkanStartGpuTest"`. It opens the game's Vulkan window and plays MainScene for 60 frames with every pass, presenting each frame.
