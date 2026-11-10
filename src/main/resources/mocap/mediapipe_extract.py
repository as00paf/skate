"""Skate engine MediaPipe adapter (ANI-17, ANI-18). Runs outside the editor; talks JSON lines on stdout.

Commands:
  check   [--model M.task]                     -> {"python": ..., "mediapipe": ..., "opencv": ..., "api": ..., ...}
  probe   --input VIDEO --thumbnail OUT.png    -> {"fps": ..., "frames": ..., "width": ..., "height": ..., "duration": ...}
  extract --input VIDEO --output OUT.json [--model M.task]
          prints {"progress": 0..1} lines, then {"done": true, "frames": N, "detected": D, "api": ...}

Pose API: the Tasks API (PoseLandmarker, VIDEO mode) with the --model file; the legacy mp.solutions.pose only when
the Tasks API cannot run (MediaPipe 0.10.14+ dropped mp.solutions). The editor downloads the model, not this script.

Errors are printed as {"error": "..."} with exit code 1. Reads local files only; never downloads anything.
Output format (version 1) is documented in docs/mocap-setup.md.
"""
import argparse
import json
import os
import platform
import sys


def emit(obj):
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()


def fail(message):
    emit({"error": message})
    sys.exit(1)


def load_libs():
    try:
        import cv2  # noqa: F401
    except ImportError:
        fail("OpenCV (cv2) is not installed: pip install mediapipe opencv-python")
    try:
        import mediapipe  # noqa: F401
    except ImportError:
        fail("MediaPipe is not installed: pip install mediapipe")
    import cv2
    import mediapipe
    return cv2, mediapipe


def open_video(cv2, path):
    cap = cv2.VideoCapture(path)
    if not cap.isOpened():
        fail("Cannot open video: " + path)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    if fps <= 0 or fps > 1000:
        fps = 30.0
    frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    return cap, fps, frames, width, height


def cmd_check(args):
    cv2, mp = load_libs()
    detector, api, fallback = open_detector(mp, args.model)
    detector.close()
    out = {"python": platform.python_version(), "mediapipe": getattr(mp, "__version__", "?"),
           "opencv": cv2.__version__, "api": api}
    if api == "tasks":
        out["model"] = args.model
    if fallback:
        out["fallback"] = fallback
    emit(out)


def cmd_probe(args):
    cv2, _ = load_libs()
    cap, fps, frames, width, height = open_video(cv2, args.input)
    if frames > 2:
        cap.set(cv2.CAP_PROP_POS_FRAMES, frames // 3)
    ok, image = cap.read()
    cap.release()
    if not ok:
        fail("Cannot read a frame from: " + args.input)
    if not cv2.imwrite(args.thumbnail, image):
        fail("Cannot write thumbnail: " + args.thumbnail)
    emit({"fps": fps, "frames": frames, "width": width, "height": height, "duration": frames / fps if fps else 0.0})


class TasksDetector:
    def __init__(self, mp, model):
        from mediapipe.tasks import python as mp_python
        from mediapipe.tasks.python import vision
        options = vision.PoseLandmarkerOptions(
            base_options=mp_python.BaseOptions(model_asset_path=model),
            running_mode=vision.RunningMode.VIDEO, num_poses=1)
        self.mp = mp
        self.landmarker = vision.PoseLandmarker.create_from_options(options)

    def detect(self, rgb, timestamp_ms):
        image = self.mp.Image(image_format=self.mp.ImageFormat.SRGB, data=rgb)
        result = self.landmarker.detect_for_video(image, timestamp_ms)
        if not result.pose_world_landmarks:
            return None, None
        return result.pose_world_landmarks[0], result.pose_landmarks[0]

    def close(self):
        self.landmarker.close()


class SolutionsDetector:
    def __init__(self, mp):
        self.pose = mp.solutions.pose.Pose(static_image_mode=False, model_complexity=2, smooth_landmarks=True)

    def detect(self, rgb, timestamp_ms):
        result = self.pose.process(rgb)
        if result.pose_world_landmarks is None:
            return None, None
        return result.pose_world_landmarks.landmark, result.pose_landmarks.landmark

    def close(self):
        self.pose.close()


def has_solutions(mp):
    try:
        return hasattr(mp, "solutions") and hasattr(mp.solutions, "pose")
    except Exception:  # some builds raise on attribute access instead of lacking it
        return False


def open_detector(mp, model):
    """(detector, api, fallback reason): the Tasks API when it runs, else the legacy solution, else a clear error."""
    if not model:
        tasks_problem = "no pose_landmarker model file was given"
    elif not os.path.isfile(model):
        tasks_problem = "model file not found: " + model
    else:
        try:
            return TasksDetector(mp, model), "tasks", None
        except Exception as ex:
            tasks_problem = "%s: %s" % (type(ex).__name__, ex)
    if has_solutions(mp):
        try:
            return SolutionsDetector(mp), "solutions", "Tasks API unavailable (" + tasks_problem + ")"
        except Exception as ex:
            solutions_problem = "%s: %s" % (type(ex).__name__, ex)
    else:
        solutions_problem = "not in MediaPipe " + getattr(mp, "__version__", "?")
    fail("No MediaPipe pose API works. Tasks API (pose model): " + tasks_problem
         + ". Legacy solutions API: " + solutions_problem + ".")


def cmd_extract(args):
    cv2, mp = load_libs()
    cap, fps, frames, width, height = open_video(cv2, args.input)
    detector, api, _ = open_detector(mp, args.model)
    out_frames = []
    index = 0
    detected = 0
    last_progress = -1
    while True:
        ok, bgr = cap.read()
        if not ok:
            break
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
        world, image = detector.detect(rgb, int(index * 1000.0 / fps))
        frame = {"t": index / fps}
        if world is not None:
            detected += 1
            frame["world"] = [[p.x, p.y, p.z, getattr(p, "visibility", 1.0) or 0.0] for p in world]
            frame["image"] = [[p.x, p.y] for p in image]
        else:
            frame["world"] = None
        out_frames.append(frame)
        index += 1
        if frames > 0:
            progress = int(100 * index / frames)
            if progress != last_progress:
                last_progress = progress
                emit({"progress": min(1.0, index / frames)})
    cap.release()
    detector.close()
    if index == 0:
        fail("The video has no readable frames: " + args.input)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump({"version": 1, "source": "mediapipe " + getattr(mp, "__version__", "?"), "fps": fps,
                   "api": api, "width": width, "height": height, "frames": out_frames}, f)
    emit({"done": True, "frames": index, "detected": detected, "api": api})


def main():
    parser = argparse.ArgumentParser(description="Skate MediaPipe adapter")
    sub = parser.add_subparsers(dest="command", required=True)
    c = sub.add_parser("check")
    c.add_argument("--model")
    p = sub.add_parser("probe")
    p.add_argument("--input", required=True)
    p.add_argument("--thumbnail", required=True)
    e = sub.add_parser("extract")
    e.add_argument("--input", required=True)
    e.add_argument("--output", required=True)
    e.add_argument("--model")
    args = parser.parse_args()
    try:
        {"check": cmd_check, "probe": cmd_probe, "extract": cmd_extract}[args.command](args)
    except SystemExit:
        raise
    except Exception as ex:  # report anything unexpected in the protocol
        fail("%s: %s" % (type(ex).__name__, ex))


if __name__ == "__main__":
    main()
