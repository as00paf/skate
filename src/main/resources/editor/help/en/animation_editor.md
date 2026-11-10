# Animation Editor

The Animation Editor changes an animation clip: you can play it on a character, fix a pose, move keys in time, add events and change its length. Use it to touch up an animation instead of making it again from scratch.

## What it is

An **animation clip** is a short motion, such as a walk or a jump, stored as a `.skanim` file. It moves the bones of a character's **rig** (its skeleton) over time. A few words:

- **Frame**: one step of time. The **Frame rate** says how many frames make one second.
- **Key**: a saved value of one bone at one frame. Between keys, the engine fills in the motion.
- **Playhead**: the current frame, shown on the ruler of the timeline.
- **Event**: a named mark at a frame, such as `footstep`, that tells the game to do something at that moment.

The window shows a preview of the clip on a character, the bone list, the timeline with the keys, and the events.

## How to use it

1. Open it from **View > Windows > Assets**. Choose a clip in the **Clip** list, or click **Open...** to pick an animation file. An FBX or glTF clip opens as an editable `.skanim` copy.
2. Choose the **Rig** to preview on: the skater, or the selected object if it has a skeleton.
3. Click the ruler to move the playhead. Use **Play** (or **Space**), **Previous frame** and **Next frame** (**Left** and **Right**), and **To start** and **To end** (**Home** and **End**). **Loop**, **Speed** and **Snap** change how it plays.
4. Click a key to select it; **Ctrl**+click adds keys; drag a box to select several. Drag selected keys to move them in time. **Ctrl+A** selects all keys.
5. With keys selected, use **Scale** with a **Factor**, **Copy** (**Ctrl+C**), **Paste** (**Ctrl+V**) and **Delete keys** (**Delete**).
6. To change a pose, select a bone in the list (type in **Filter bones** to find it) or click it in the preview, then click **Key bones** (**K**) to key its pose at the playhead.
7. Type an **Event name** and click **Add event** to put an event at the playhead. **Rename** and **Delete event** change it.
8. Set **Length (s)**, then **Retime** to stretch the whole clip to it or **Trim** to cut it. Change the **Frame rate** if needed.
9. Every change can be undone with **Ctrl+Z**. Click **Save** to write the file.

To play the clip in the game, drag it onto an object with an **Animator**.

## Related windows

- [Video to Animation](video_to_animation.md): make a new clip from a video.
- [Asset Browser](asset_browser.md): the animations of the project.
- [Properties](properties.md): the Animator that plays the clip.
