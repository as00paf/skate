# ANI-17 — Video to Animation: how to test

Needs Python with MediaPipe (docs/mocap-setup.md) and a short local video of one person, whole body in view.

1. `gradlew.bat run`, open a project, then **View > Windows > Video to Animation**. The window has a `?` help popover.
2. Open **Setup**, set **Python** to your MediaPipe Python, click **Check setup**. Expected: "Ready: Python …, MediaPipe …".
   With a wrong path you see the problem and a hint instead (for example the Microsoft Store shortcut message).
3. Click **Choose video...** and pick the video. Expected: a preview frame and "width x height, fps, seconds".
4. Click **Create animation**. Expected: a progress bar ("Detecting pose… n%"), then "Building the animation…",
   then "Created <video name>.skanim". The clip is selected in the Project window under `Assets/Animations`.
5. Drag the **Frame** slider under the preview: the detected skeleton (green lines) follows the person.
6. Add the new clip to the skater's Animator and press Play: the skater moves like the person (in place by default).
7. Turn on **Options > Root motion**, create again: a second clip (`<name> 2.skanim`); the skater now moves through
   the scene and its planted feet do not slide. Start another run and click **Cancel**: it stops without an error.
8. Click **Import mocap file...** and pick a BVH (for example from FreeMoCap or the CMU library): a new clip is
   created the same way.

Unverified (no MediaPipe was installed when this was built): the real script run (steps 2–5) and whether the motion
faces the right way. If the skater faces away from the camera's direction, report it; the axis conversion has a
`flipFacing` switch in `MediaPipeJson`.
