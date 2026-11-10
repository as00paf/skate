# Export Project

Export Project builds a copy of your game that other people can play without the editor. This is the step between "it works on my machine" and "send it to a friend".

## What it is

Exporting collects the scenes and assets your game needs, packs them into one file (`game.bin` with a `manifest.json`) and, for a Windows build, adds the game program and a `run-game.bat` launcher. The result goes in an output folder you choose.

There are two targets:

- **Windows build**: a complete game folder you can zip and share. It needs the game program (the **game jar**). If the window cannot find it, run `gradlew gameJar` (for example from the [Shell](shell.md)) or choose a jar yourself.
- **Asset pack only**: only `game.bin` and `manifest.json`, for an existing game program.

## How to use it

1. Save your work and choose **File > Export Project**.
2. Choose the **Target**.
3. Tick the **Scenes** to include. The startup scene is always included.
4. Set the options:
   - **Compress assets** makes the build smaller but a little slower to load.
   - **Only referenced assets** leaves out files no scene, prefab or setting uses.
5. Choose the **Output folder**, or click **Default**. Do not choose the project folder itself.
6. Click **Export** and wait while the steps run: saving, collecting, packing, verifying and copying.
7. When it is done, click **Open Folder** to see the result. Double-click `run-game.bat` to play.

The game starts with the graphics backend chosen in [Project Settings](project_settings.md). Vulkan is experimental: test the exported game before you share it.

If the export fails, the window explains why and what to do. **Back to Options** lets you change a setting and try again.

## Related windows

- [Project Settings](project_settings.md): startup scene, graphics backend and quality.
- [Shell](shell.md): run `gradlew gameJar` or the Skate CLI.
- [Console](console.md): details of a failed export.
