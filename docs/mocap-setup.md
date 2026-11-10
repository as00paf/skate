# Video to Animation: setup

The editor's **Video to Animation** window (View > Windows) turns a video file on your computer into an animation
clip. It runs Google's MediaPipe pose estimator in a Python you install once. Nothing is uploaded or downloaded by
the editor: it reads the video from disk and writes a `.skanim` clip into your project.

## Install (once per machine)

1. Install **Python 3.10 or newer** from python.org. On Windows, tick "Add python.exe to PATH". The `python` that
   Windows offers from the Microsoft Store shortcut is not a real install. "Check setup" says so.
2. Optional but recommended: make a separate environment, for example
   `python -m venv C:\skate-mocap` and use `C:\skate-mocap\Scripts\python.exe` below.
3. Install the packages in that Python:
   `python -m pip install mediapipe opencv-python`
4. In the editor, open **Video to Animation > Setup**:
   - **Python**: the python executable from step 1 or 2 (default `python`).
   - **Model file** (optional): a MediaPipe `pose_landmarker_full.task` or `pose_landmarker_heavy.task` file,
     downloaded from the MediaPipe Pose Landmarker page. Without it the script uses the pose model built into
     MediaPipe's older "solutions" API, which newer MediaPipe releases may no longer include.
   - Click **Check setup**. It shows the Python, MediaPipe and OpenCV versions, or what is missing and how to fix it.

## Use

1. **Choose video...** and pick a local file (mp4, mov, avi, mkv, webm). A preview frame and the video's size,
   frame rate and length appear.
2. Optional: **Options** (character rig, smoothing, root motion, foot lock). The defaults suit most videos.
3. **Create animation**. The pose is detected in every frame (progress bar, Cancel), solved onto a humanoid,
   retargeted onto the character (rest-pose compensated) and saved as `Assets/Animations/<video name>.skanim`.
   The new clip is selected in the Project window; the detected pose can be scrubbed over the preview.
4. **Import mocap file...** brings in BVH, FBX or glTF animations made by any other tool (FreeMoCap, Rokoko,
   Blender, ...) the same way.

Tips for good results: one person, whole body in view, steady camera, good light, clothes that show the limbs.

## Limits

- One person per video (MediaPipe's default).
- Twist around a limb's own axis (forearm roll) and fingers are not captured.
- Without root motion the clip stays in place (the hips over the origin). Root motion follows the foot on the
  ground, so it drifts on long clips and does not capture jumps' height above the ground.
- **Unverified:** this pipeline was built and tested with a simulated MediaPipe (no MediaPipe install was available).
  The landmark axis conversion (MediaPipe x right, y down, z away from the camera -> engine Y-up, facing +Z) and
  the script's MediaPipe API calls still need a run on a real video.

## Landmark file format (version 1)

`mediapipe_extract.py extract` writes JSON:

```json
{ "version": 1, "source": "mediapipe 0.10.x", "fps": 30.0, "width": 1280, "height": 720,
  "frames": [ { "t": 0.0, "world": [[x, y, z, visibility] x 33], "image": [[u, v] x 33] },
              { "t": 0.033, "world": null } ] }
```

`world` is MediaPipe's world landmarks (metres, origin between the hips), `image` the normalized image positions,
and `null` means no person was detected in that frame. While running, the script prints `{"progress": 0..1}` lines
and, on failure, `{"error": "..."}` with exit code 1.
