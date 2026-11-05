# REN-13 test steps: Vulkan backend spike (opt-in)

The Vulkan backend is an offscreen spike (`docs/design/REN-13.md`). The editor and the game must be unaffected, and
the spike runs only when asked for.

1. `.\gradlew.bat run`, then create a NEW project → the editor opens and renders exactly as before (OpenGL).
2. Close the editor. Run `set SKATE_RHI=vulkan` then `.\gradlew.bat run` → the editor still uses OpenGL (the
   flag only drives the offscreen comparison).
3. In the same shell: `.\gradlew.bat :test --tests com.pafoid.skate.engine.render.rhi.vulkan.VulkanGeometryPassGpuTest`
   → the test passes and prints the comparison (mean difference near 0, max 1/255 on the reference machine).
4. Open `build/ren13/vulkan.png` and `build/ren13/gl.png` → both show the default MainScene (floor, ramps, box,
   skater, board, sky) upright and alike; `diff.png` is black.
5. `set SKATE_RHI=` (unset) and run the same test → it is skipped (opt-in).
