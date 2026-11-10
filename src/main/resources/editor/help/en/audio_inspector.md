# Audio Inspector

The Audio Inspector is the sound desk of the editor. It shows the sound of the selected object and the volume controls of the whole project.

## What it is

A sound in a scene comes from an object with an **Audio Source** component. The top part of the window edits the Audio Source of the selected object: which sound to play, how loud, whether it loops, and whether it is **3D**. A 3D sound gets quieter with distance and comes from the left or right depending on where it is.

The **Mixer** groups sounds into **buses**: **Master**, **Music**, **Sound Effects** and **UI**. Each sound plays on one bus, so you can turn all music down at once without touching every object.

## How to use it

1. Select an object in the [Scene Hierarchy](hierarchy.md).
2. If it has no sound yet, click **Add Audio Source**.
3. Choose a sound file, or drop one from the [Asset Browser](asset_browser.md). WAV, OGG and MP3 work.
4. Click **Play** to hear it in the editor and **Stop** to end the preview. The preview is not saved with the scene.
5. Set **Loop**, **Play on awake** and the bus. Turn on **3D (positional)** for sounds that come from a place in the world, and set the minimum and maximum distance.
6. Open **Mixer** to set each bus's **Volume**, **Mute**, **Low-pass** (a duller sound, as through a wall) and **Reverb send** (how much echo it gets in a reverb zone). Mixer values are saved with the project.
7. Click **Remove Audio Source** to remove the sound. It can be undone.

## Related windows

- [Properties](properties.md): the same Audio Source with the other components.
- [Asset Browser](asset_browser.md): the sounds of the project.
- [Scene Hierarchy](hierarchy.md): choose the object that plays the sound.
