# Video to Animation

Video to Animation turns a video of a person into an animation for your character. Film yourself doing a move, and the character can repeat it. This is called **motion capture**, or **mocap**.

## What it is

The window watches the video frame by frame, finds the person's pose (head, arms, legs) and builds an **animation clip** for the character's skeleton. Everything happens on your computer: nothing is uploaded.

You can also import a mocap file made by another program (BVH, FBX or glTF).

The pose detection uses Python with the MediaPipe library. You set it up once in the **Setup** section; the steps are in `docs/mocap-setup.md`.

## How to use it

1. The first time, open **Setup**, choose your **Python** program and click **Check setup**. Wait until it says **Ready**.
2. Click **Choose video...** and pick a video (mp4, mov, avi, mkv or webm). A clear, full-body shot with one person works best.
3. Move the **Frame** slider to check that the detected pose follows the person.
4. Set the options:
   - **Character**: the skater, or the selected object if it has a skeleton.
   - **Smoothing**: removes jitter. Too much can soften fast moves.
   - **Root motion**: moves the character through the scene as the person moves. Off keeps the move in place.
   - **Lock feet**: keeps the feet planted while they touch the ground.
5. Click **Create animation** and wait. **Cancel** stops it.
6. The clip is saved in `Assets/Animations`. Click **Show in Project** to find it, then drag it onto an object with an **Animator**.

If only a small part of the motion matched the character's bones, the window warns you. Try a clearer video or another character.

## Related windows

- [Asset Browser](asset_browser.md): the animations of the project.
- [Project](project.md): the new clip in `Assets/Animations`.
- [Properties](properties.md): the Animator that plays the clip.
