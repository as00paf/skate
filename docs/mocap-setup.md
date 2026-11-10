# Video to Animation: setup

The editor's **Video to Animation** window (View > Windows) turns a video file on your computer into an animation
clip. It runs Google's MediaPipe pose estimator in a Python you install once. Nothing is uploaded: the editor reads
the video from disk and writes a `.skanim` clip into your project. The only download is MediaPipe's pose model, once
(see below).

## Install (once per machine)

1. Install **Python 3.10 or newer** from python.org. On Windows, tick "Add python.exe to PATH". The `python` that
   Windows offers from the Microsoft Store shortcut is not a real install. "Check setup" says so.
2. Optional but recommended: make a separate environment, for example
   `python -m venv C:\skate-mocap` and use `C:\skate-mocap\Scripts\python.exe` below.
3. Install the packages in that Python:
   `python -m pip install mediapipe opencv-python`
4. In the editor, open **Video to Animation > Setup**:
   - **Python**: the python executable from step 1 or 2 (default `python`).
   - **Model file** (optional): leave it empty. The editor then downloads Google's `pose_landmarker_heavy.task`
     (about 30 MB) the first time you click **Check setup** or **Create animation**, with a progress bar, and keeps it
     in your user data folder (`%APPDATA%\Skate\mocap` on Windows, `~/Library/Application Support/Skate/mocap` on
     macOS, `~/.config/skate/mocap` on Linux), shared by all projects. Set a path here only to use another model
     (for example `pose_landmarker_full.task`, faster but less precise) or on a computer without internet: download
     the file from the MediaPipe Pose Landmarker page and choose it. A path set here is never downloaded over.
   - Click **Check setup**. It shows the Python, MediaPipe and OpenCV versions, the pose API in use and the model
     file, or what is missing and how to fix it.

### Which MediaPipe API is used

The script uses MediaPipe's **Tasks API** (`PoseLandmarker` in video mode) with the model above. MediaPipe 0.10.14
and newer removed the old `mp.solutions` API, so the Tasks API is the only one current releases have. When the Tasks
API cannot run (no model because the download failed, or an old MediaPipe without it) and the installed MediaPipe
still has `mp.solutions.pose`, the script falls back to it; Check setup then says "legacy solutions API" and why.
When neither works, Check setup and Create animation fail with "No MediaPipe pose API works" and the reason for each.

On Linux the Tasks API needs the system's OpenGL ES library (`sudo apt install libgles2` on Ubuntu); without it
the error names `libGLESv2.so.2`. Windows and macOS need nothing extra.

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
- The script was checked against MediaPipe 1.2.2 and OpenCV 5.0 from pip (Python 3.12, Linux): Check setup reports
  the Tasks API and a test video gave a `.skanim` on the skater rig. Whether the motion faces the right way
  (MediaPipe x right, y down, z away from the camera -> engine Y-up, facing +Z) still needs a look in the editor.

## Landmark file format (version 1)

`mediapipe_extract.py extract` writes JSON:

```json
{ "version": 1, "source": "mediapipe 1.2.2", "api": "tasks", "fps": 30.0, "width": 1280, "height": 720,
  "frames": [ { "t": 0.0, "world": [[x, y, z, visibility] x 33], "image": [[u, v] x 33] },
              { "t": 0.033, "world": null } ] }
```

`world` is MediaPipe's world landmarks (metres, origin between the hips), `image` the normalized image positions,
and `null` means no person was detected in that frame. While running, the script prints `{"progress": 0..1}` lines
and, on failure, `{"error": "..."}` with exit code 1. `api` is the pose API that ran (`tasks` or `solutions`).
