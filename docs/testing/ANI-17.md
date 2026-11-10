# ANI-17 — Video to Animation: how to test

Needs Python with the current MediaPipe from pip (`python -m pip install mediapipe opencv-python`, docs/mocap-setup.md),
an internet connection the first time (the pose model is downloaded once) and a short local video of one person,
whole body in view.

1. `gradlew.bat run`, open a project, then **View > Windows > Video to Animation**. The window has a `?` help popover.
2. Open **Setup**, set **Python** to your MediaPipe Python, leave **Model file** empty, click **Check setup**
   (ANI-18). Expected the first time: "Downloading the pose model… n%", then "Ready: Python …, MediaPipe …, Tasks API"
   and "Pose model: pose_landmarker_heavy.task". Clicking again is immediate (no new download). There is no
   "module mediapipe has no attribute solutions" error. With a wrong Python path you see the problem and a hint
   instead (for example the Microsoft Store shortcut message).
   - Offline check (optional): delete `%APPDATA%\Skate\mocap\pose_landmarker_heavy.task`, disconnect, Check setup:
     "Could not download the pose model … No MediaPipe pose API works …" and a hint to connect or choose a model file.
   - Override: set **Model file** to a downloaded `pose_landmarker_full.task`: Check setup shows that file instead.
     A path to a missing file says "Model file not found".
3. Click **Choose video...** and pick the video. Expected: a preview frame and "width x height, fps, seconds".
4. Click **Create animation**. Expected: a progress bar ("Detecting pose… n%"; "Downloading the pose model… n%"
   first if step 2 was skipped on this computer), then "Building the animation…",
   then "Created <video name>.skanim". The clip is selected in the Project window under `Assets/Animations`.
5. Drag the **Frame** slider under the preview: the detected skeleton (green lines) follows the person.
6. Add the new clip to the skater's Animator and press Play: the skater moves like the person (in place by default).
7. Turn on **Options > Root motion**, create again: a second clip (`<name> 2.skanim`); the skater now moves through
   the scene and its planted feet do not slide. Start another run and click **Cancel**: it stops without an error.
8. Click **Import mocap file...** and pick a BVH (for example from FreeMoCap or the CMU library): a new clip is
   created the same way.

ANI-18 checked the script against MediaPipe 1.2.2 from pip (Linux, Python 3.12): Check setup reported the Tasks API and
a 45-frame test video gave a `.skanim` on the skater rig. Still unverified: Windows, and whether the motion faces the
right way. If the skater faces away from the camera's direction, report it; the axis conversion has a `flipFacing`
switch in `MediaPipeJson`.
