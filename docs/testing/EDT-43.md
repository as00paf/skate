# EDT-43: Graphics backend in New Project and Project Settings

1. Start the editor and open **New Project**. Type a name.
   **Expected:**
   - The name you type stays (it used to reset to "test").
   - "Graphics backend" shows **OpenGL**. Its tooltip says only exported games use it and the editor stays on OpenGL.
2. Pick **Vulkan (experimental)**, then create the project.
   **Expected:**
   - An experimental warning appears under the picker.
   - The new `<name>.skateproject` contains `"graphicsBackend": "VULKAN"`.
   - The editor viewport still renders normally with OpenGL.
3. Open **Project Settings > Graphics** and switch back to **OpenGL**. Press Ctrl+Z.
   **Expected:**
   - The picker shows Vulkan again, and Ctrl+Y returns it to OpenGL.
   - Choose OpenGL and press **OK**; the project file now says `"OPENGL"`.
4. Open **File > Export Project**.
   **Expected:** the dialog shows "Graphics backend: OpenGL". The tooltip says it can be changed in Project Settings > Graphics. With Vulkan chosen, the line and its warning say Vulkan instead.
5. Switch the editor to French (Settings).
   **Expected:**
   - The picker reads "Backend graphique", and Vulkan reads "Vulkan (expérimental)".
   - The Project Settings category reads "Graphismes".
