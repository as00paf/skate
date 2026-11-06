# REN-27: Environment reflections (image-based lighting) and a chrome showcase sphere

1. Create a new project and open the **FeatureShowcase** scene; click **Materials** in the area list (or fly to the back row, centre right).
2. The big chrome sphere **[Materials] Mirror** (back left) clearly mirrors the blue sky and clouds, with the pale horizon in its lower half. Orbit the camera: the reflection moves with the view, like a real mirror ball.
3. The five gold spheres go from rough (left: soft, blurred sheen) to smooth (right: a sharp, mirror-like sky reflection).
4. Select the scene and open **Environment**: set **Reflection Intensity** to 0, then back to 1; the reflections disappear and come back. **Sky Light Intensity** brightens or darkens shaded sides with sky-blue light.
5. In the same component, change **Sky Exposure** or **Sky Tint**: the reflections follow the new sky colour.
6. In the scene's **Day Night Cycle**, move **Time Of Day** to 23: reflections and sky light dim with the sun. Back to 12: full again.
7. Open MainScene: the asphalt, ramps and skater get a light blue fill from the sky; nothing looks black or noisy.
8. Optional: export or run the game with the Vulkan backend: the chrome sphere looks the same as in the editor.
