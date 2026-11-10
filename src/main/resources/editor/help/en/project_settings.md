# Project Settings

Project Settings holds the settings of your game. They are saved inside the project, so everyone who opens the project, and the exported game, uses the same values.

## What it is

Compare it with [Editor Settings](editor_settings.md): Editor Settings is about **you** and your editor; Project Settings is about **the game**. The categories are:

- **Project Metadata**: the name, version, description and folder of the project, and your recent projects.
- **Game**: the **Startup scene** that opens with the project and starts the exported game, and the language the game's text starts in.
- **Graphics**: how the exported game renders: the graphics backend (OpenGL or Vulkan), overall quality, shadows, textures, MSAA, fullscreen and V-Sync. The editor itself always renders with OpenGL.
- **Physics**: how many physics steps run per second.
- **Random**: the **seed** of the random numbers. The same seed gives the same "random" results every time you play, which helps when you hunt a bug. **Randomize** picks a new one.
- **Input**: settings of the game's controls.

## How to use it

1. Open a project, then choose **Settings > Project Settings...**.
2. Click a category on the left and change values.
3. Click **OK** or **Apply** to save the project. **Cancel** throws your changes away.
4. Before you export, check the **Startup scene** and the **Graphics** category: the [Export Project](export_wizard.md) window uses them.

## Related windows

- [Editor Settings](editor_settings.md): your personal preferences.
- [Export Project](export_wizard.md): build the game with these settings.
- [Key Bindings](keybindings.md): the game's controls.
