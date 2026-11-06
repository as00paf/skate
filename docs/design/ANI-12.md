# ANI-12 — Animation tool and video-to-animation (design note)

Status: **proposal, awaiting human approval**. No code has been written.

Scope:
1. An **animation editor window**: timeline, keyframes, curve editor, preview and events.
2. A **video → animation pipeline** for **local video files only** (human decision 2026-11-06). An existing pose-estimation tool runs as an external process. Its output becomes BVH, goes through the existing loader, and is mapped onto the project rig with the existing `Retargeter`.

Tool facts were checked on 2026-11-06 against each project's own README, LICENSE or model card (sources at the end). Anything I could not confirm from a primary source is marked **unverified**.

---

## 1. Tool evaluation

### 1.1 Summary

| Tool | Input | License (code / weights / body model) | Commercial use | Hardware | Install story | Verdict |
|---|---|---|---|---|---|---|
| **MediaPipe Pose Landmarker** (Google) | Single video, single person by default (`numPoses`) | Apache-2.0 / Apache-2.0 (BlazePose GHUM 3D model card) / none | Yes | CPU is enough; GPU delegate optional | `pip install mediapipe` + a `.task` model file (lite/full/heavy) | **Phase 1 default** |
| **NVIDIA GEM-X** (successor of GENMO/GEM) | Monocular video, moving camera OK, one tracked person | Apache-2.0 / NVIDIA Open Model License / SOMA (NVIDIA says NVIDIA-owned or permissive) | Yes, per the model card ("ready for commercial or non-commercial use") | NVIDIA GPU (Ampere listed, tested on A100 80 GB); CPU mode exists but speed **unverified** | Python ≥ 3.10, PyTorch 2.10, FFmpeg, manual download of checkpoints, YOLOX detector and SOMA assets | **Phase 2 "high quality" backend, after a spike** |
| **GVHMR** (ZJU 3DV) | Monocular video, world-grounded | Custom: "educational, research and non-profit purposes only" / same / SMPL + SMPL-X (non-commercial) | **No** | CUDA GPU; trained on 2× RTX 4090; CUDA 12.1 only for optional DPVO | conda, Python 3.10, SMPL/SMPL-X sign-up, 5 checkpoints from Google Drive incl. `yolov8x.pt` | **Rejected** (license) |
| **WHAM** | Monocular video, world-grounded | MIT / MIT (per repo) / SMPL (non-commercial) | **No in practice** (SMPL is required to get a skeleton) | CUDA 11.3 GPU | Ubuntu 20.04/22.04 only per INSTALL.md, Python 3.9, PyTorch 1.11, ViTPose + DPVO builds | **Rejected** (SMPL license, Linux-only, old stack) |
| **FreeMoCap** | Recordings from 1–N cameras; 3 cameras + ChArUco calibration recommended | AGPL-3.0 (maintainers offer other terms on request) | Yes, but copyleft | CPU, or NVIDIA GPU via `freemocap[cuda]` | `pip install freemocap[cpu|cuda]`, Python 3.9–3.11, own GUI | **Not a backend.** Users can export BVH/FBX/glTF from it and import the file (§3.6) |
| **NVIDIA MotionBricks** ("motion bricks") | **No video input.** Inputs are 4 context poses + 1–4 target poses | Apache-2.0 / NVIDIA Open Model Agreement / sample motions: evaluation-only license | Yes for code + models, **not** for the bundled sample motions | NVIDIA GPU required (TensorRT); tested on RTX 4090, Windows + Linux | `git lfs pull`, `uv sync`; TensorRT plan built on first run | **Out of scope for ANI-12** (§1.3) |

### 1.2 Notes per tool

**MediaPipe Pose Landmarker.** Outputs 33 landmarks per frame, both in image space and as "world landmarks" in metres with the hip midpoint as origin. `VIDEO` mode processes every decoded frame without dropping any. Models: lite, full, heavy.
- Strengths: permissive license end to end, CPU-only, one pip package, Windows wheels (the package is widely used on Windows; I did not re-check the current wheel matrix, so treat the supported Python versions as **unverified**).
- Weaknesses:
  - It gives positions, not joint rotations, so we must solve rotations ourselves (§3.3).
  - There is no world trajectory: the root is always at the hips origin.
  - Depth is ambiguous and the output jitters, so filtering is needed.
  - No finger detail, and only a weak twist (roll) signal for the forearm and shin.
- Expected quality: good enough for blocking out and reference motion. It needs cleanup in the animation editor before it can be used in a shipped game. This is my assessment, not a measured result. §5 adds a measurement step.

**GEM-X (NVIDIA).** This is the NVIDIA video→motion offering. I believe it is what the human meant by "motion bricks" (§1.3).
- Recovers full-body pose (77 joints, body + hands) and the root path in world space, from video shot with a moving camera.
- `gem-infer` writes PyTorch `.pt` files (`body.pt`, `motion.pt`). The page shows no BVH/glTF export, so we need our own exporter (an adapter script).
- **Unverified:**
  - the exact fields in `motion.pt` (documented in DEMO.md, not read yet);
  - whether inference runs on Windows (the model card lists Windows, but the README's commands are bash and rendering needs Linux x86-64);
  - VRAM needs on consumer GPUs;
  - the licenses of the YOLOX-HumanArt detector weights and of the ViTPose keypoint weights it uses;
  - the SOMA asset license (we only have NVIDIA's statement).
- Released 2026-10-30, one week before this note. Expect churn.
- The original NVlabs/GENMO repo (SMPL-based) uses the NVIDIA OneWay **Noncommercial** license. Only GEM-X is usable for us.

**GVHMR.** Probably the best quality of the academic options, but the LICENSE allows only "educational, research and non-profit purposes", requires modifications to be open source and non-commercial, and depends on SMPL/SMPL-X. Its default detector is Ultralytics YOLOv8. I believe Ultralytics is AGPL-3.0 but did not re-check it today. Rejected.

**WHAM.** The MIT code is fine, but a usable skeleton needs the SMPL body model, and the SMPL model license says "any use for commercial purposes is prohibited" (commercial licenses go through Meshcapade). Setup is documented for Ubuntu only, on CUDA 11.3 / PyTorch 1.11. Rejected.

**FreeMoCap.** Its strength is calibrated multi-camera capture, not "turn this one clip into an animation". The docs present single-camera recording as a simple pipeline check. Its accuracy in single-camera mode is **unverified**. Its documented BVH/FBX/glTF export means any user who has FreeMoCap can already bring results in through the BVH/glTF import path. We do not need to drive it as a process. AGPL-3.0 would also be a concern if we ever bundled it, which we won't.

### 1.3 About "motion bricks"

MotionBricks (NVlabs, SIGGRAPH 2026) is a **real-time motion generator**. It produces motion that reaches target poses (in-betweening) and drives locomotion from velocity, heading and style commands, on the SOMA skeleton at 30 Hz. It does **not** read video or estimate pose. It can't serve as the video→animation backend.

Two possible later uses, each of which would need its own task and design note:
- **Editor in-betweening:** fill the gap between two keyed poses in the animation editor.
- **Runtime neural locomotion.**

Both need an NVIDIA GPU with TensorRT, which conflicts with "must work for any 3D game" at runtime. An editor-only, optional in-betweening helper is the realistic version. Its sibling Kimodo (text-to-motion) was not evaluated; its license is **unverified**.

The NVIDIA tool that does turn video into motion is **GEM-X**, evaluated above.

**Question for the human:** did "motion bricks" mean GEM-X (video), or do you also want MotionBricks in-betweening as a follow-up task?

### 1.4 Recommendation

1. **Phase 1:** MediaPipe backend, CPU, permissive license, easy install.
2. **Phase 2:** GEM-X backend for users with an NVIDIA GPU, gated on the spike in §5 confirming Windows inference, the output fields and the third-party weight licenses.
3. Always support **importing a BVH/glTF/FBX file** produced by any external tool (FreeMoCap, Rokoko, Blender, …). The pipeline's second half (retarget + clean up) is shared, so this costs little.
4. Do not integrate GVHMR, WHAM or the original GENMO (non-commercial).

---

## 2. Animation editor window

An editor window ("Animation", `editor/animation` string area, which ANI owns) that edits a **clip asset**. It is not a scene tool: the selected GameObject only supplies the preview rig.

### 2.1 Layout

```
┌ toolbar: clip ▾ | rig ▾ | ◀◀ ◀ ▶ ▶▶ | loop | fps 30 | speed | snap | auto-key ┐
├───────────────┬───────────────────────────────────────────────────────────┤
│ track tree    │ time ruler / scrubber                                     │
│  Hips   T R   │ ◆────◆──────◆      dope sheet (keys per track)            │
│  Spine    R   │   ◆──────◆                                                │
│  …            │ ───────────────── or curve editor (tab) ───────────────── │
│ Events        │  ▼ footstep   ▼ land                                      │
├───────────────┴───────────────────────────────────────────────────────────┤
│ Preview (offscreen viewport): orbit camera, ground grid, bone overlay      │
└───────────────────────────────────────────────────────────────────────────┘
```

- **Timeline:** scrub, play/pause, loop, frame stepping, playback speed, set range, snap to frames at the clip's fps.
- **Dope sheet:** keys per bone and per channel (T/R/S). Select, box-select, move, scale in time, copy/paste, delete. "Key selected bones" writes the current preview pose.
- **Curve editor:** one curve per scalar component. Rotations are shown as Euler degrees (the project convention) but stored as quaternions. Editing an Euler curve rewrites the quaternion keys and keeps the hemisphere continuous. Tangent modes: auto, linear, flat, step, broken. Tangents map onto the existing `InterpolationType.CUBIC_SPLINE` (glTF Hermite in/out tangents), so the runtime sampler does not change.
- **Preview:** pose-in → pose-out evaluation of the edited clip on the chosen rig, through the same `Pose`/sampler code as the runtime. It is rendered in an offscreen viewport. Selecting a bone in the viewport selects its tracks.
- **Events track:** named markers at a time, saved with the clip (§2.2). At runtime they are merged with the per-state `AnimEventDef`s that already exist.
- **Cleanup tools,** needed for mocap: resample, keyframe reduction (error tolerance per channel), smoothing filter, foot-lock over a selected range, mirror (with the existing `BoneMirrorUtil`), trim, set loop seam (blend the last N frames into the first).

### 2.2 Data

- **New editable clip asset `*.skanim` (JSON):**
  - fields: name, fps, duration, loop flag, rig hint (humanoid or not), tracks (`bone`, `path`, keys with time, value, in/out tangents), events (`time` in seconds, `name`);
  - format version field, like the other assets (AST versioning);
  - JSON because scenes, prefabs and settings stay git-diffable (EDT-60, AST-18).
- **Loader:** a `*.skanim` loader produces the existing `Animation`/`AnimationChannel`/`AnimationSampler`. Runtime evaluation, controllers, masks, IK and the GPU palette do not change. Packing for exports goes through the normal asset pack (AST), with no special case.
- **Imported clips** (FBX/glTF/BVH through Assimp) stay read-only sources. "Edit" creates a `*.skanim` copy next to them. Overwriting an imported file is out of scope.
- **Every edit is a `Command`** (`editor/commands`) for undo/redo and the UI mutation pipeline guard: `MoveKeysCommand`, `SetKeyCommand`, `DeleteKeysCommand`, `SetTangentCommand`, `AddAnimEventCommand`, … A scrub gesture is coalesced into one command.
- **Pure, unit-testable core** under `engine/animation/edit/`: key insert/remove, tangent computation, Euler↔quat curve conversion, keyframe reduction, filtering, loop-seam blend, foot-lock. The window is a thin view over it.

### 2.3 Out of scope (separate tasks if wanted)

- Full rigging (no skin weight painting, no bone creation).
- Blend-shape (morph) tracks.
- Animating non-bone properties (cameras, lights, materials).
- Writing back to FBX/glTF. A glTF export of `*.skanim` is possible later.

---

## 3. Video → animation pipeline

### 3.1 Flow

```
local video file ──► Backend process (Python, user-installed) ──► raw output (JSON / .pt)
                         │ progress on stdout (JSON lines)
                         ▼
               adapter → MotionSequence (per-frame joint positions or rotations, fps, joint set)
                         ▼
     solve (positions → rotations on a reference humanoid), filter, foot contacts, root
                         ▼
                   BVH file  (+ optional .skanim)
                         ▼
     AnimationLoader (Assimp BVH import) ──► Retargeter.retarget(…, project rig) ──► clip asset
                         ▼
                 opens in the animation editor for cleanup
```

### 3.2 Local files only

- The UI picks a file from disk (mp4/mov/avi/mkv, depending on what the backend's decoder accepts).
- No URL field, no download, no webcam capture.
- The editor never fetches models or packages on its own. Setup is a documented, copyable command list, run by the user (§3.5).
- The process gets the absolute input path and a job folder under the project's `Library/mocap/<job-id>/` (temp, not in source control). Only the final clip goes into `Assets/`.

### 3.3 Backend interface

```kotlin
// shape only: not implementation
interface MocapBackend {
    val id: String                      // "mediapipe", "gemx"
    fun checkSetup(settings: MocapSettings): SetupStatus   // finds python, imports the package, model file present
    fun command(job: MocapJob): List<String>               // argv for ProcessBuilder, no shell
    fun readResult(job: MocapJob): MotionSequence          // parse the raw output into the common format
}
```

- **Process runner:** `ProcessBuilder` with an argv list, never a shell string. It parses JSON-line progress from stdout, keeps stderr for the error panel, can cancel (kill the process tree), applies a timeout, and runs on the `JobSystem` so the editor never blocks.
- **Adapter scripts** ship with the editor (`tools/mocap/mediapipe_extract.py`, `tools/mocap/gemx_export.py`). They are small and readable, and write one documented JSON format. The engine never imports Python.
- **MediaPipe adapter:** writes the 33 world landmarks plus visibility per frame, and the video fps. The Kotlin solver then:
  - builds a reference humanoid with `HumanoidBone` slots and bone lengths averaged over the clip;
  - computes per-bone rotations by aligning bone directions (twist from the hip/shoulder planes and hand/foot landmarks);
  - converts to Y-up, right-handed, metres (`conventions.md`). MediaPipe's world axis orientation is **unverified** and gets a fixture test;
  - filters with a One-Euro or Savitzky-Golay filter;
  - detects foot contacts (low velocity + low height) and locks feet;
  - estimates the root path from contacts. MediaPipe gives none, so the default is in-place, with an "estimate root motion" option.
- **GEM-X adapter:** reads `motion.pt` and writes world-space root translation plus per-joint local rotations for the SOMA body joints. A SOMA joint-name table maps them onto `HumanoidBone`. Hands are optional, only when the target rig has finger bones. The adapter's exact fields depend on the spike (§5).
- **BVH writer:** a small pure Kotlin writer (hierarchy + motion, ZYX Euler channels). Unit-tested with a round trip through our parser of the same file. BVH is the hand-off format because:
  - Assimp imports it, so `AnimationLoader` already covers the read side (to confirm with a fixture);
  - external tools read and write it;
  - it lets users bring results in from tools we don't drive.

### 3.4 Retargeting and integration

- Use `Retargeter.buildMap` / `retarget` with the project rig. Check `coverage()` and warn below a threshold (proposed 0.8).
- `retarget` only renames channels and scales translations. It does not compensate for **rest pose** differences (for example a T-pose source on an A-pose target). Mocap from a generic reference skeleton will hit this. That fix is a separate ANI change to `Retargeter` (rest-pose delta per bone, applied in local space). I will list it as a follow-up task; it is inside ANI's domain.
- The result is saved as a `*.skanim` (editable) and opened in the animation editor.

### 3.5 Settings and install story

- **Editor settings (per machine, not per project), section "Motion capture":**
  - path to a Python executable (or venv) for each backend;
  - MediaPipe model file path (lite/full/heavy);
  - GEM-X checkpoint folder;
  - a "Check setup" button that runs `checkSetup` and shows a clear pass/fail per item.
- **Install guidance:** one page in `docs/` with copyable steps for each backend, the license summary from §1, and a reminder that GEM-X needs an NVIDIA GPU.
- **Exports never contain any of this.** The pipeline is editor-only.

### 3.6 Import of external mocap files

"Import mocap file…" accepts BVH/FBX/glTF and runs the same retarget → `*.skanim` → editor steps. This is how FreeMoCap and any other tool are supported without integrating them.

---

## 4. Ownership and cross-domain needs

| Piece | Owner |
|---|---|
| `engine/animation/edit/*` (pure clip editing, filters, solver, BVH writer), `*.skanim` loader, Retargeter rest-pose fix, adapter scripts | ANI |
| Animation editor window, mocap job panel, settings section, commands | Window and commands are in EDT paths. Proposed: ANI writes them under the UI mutation pipeline rules, with EDT reviewing; or EDT builds the shell and ANI the panels. Lead decides. |
| Offscreen preview viewport (render to texture with its own camera) | REN / EDT; reuse the existing viewport code if possible |
| `*.skanim` asset type registration, packing, GUID | AST |
| `tools/mocap/*` placement and `Library/` temp folder convention | lead / AST |

---

## 5. Proposed task split (after approval)

1. **ANI-13** `*.skanim` format, loader, and pure editing core (keys, tangents, Euler↔quat curves, reduction, filters) with unit tests.
2. **ANI-14** Animation editor window: timeline, dope sheet, events, preview (needs EDT/REN as in §4).
3. **ANI-15** Curve editor and cleanup tools (foot-lock, loop seam, mirror, trim).
4. **ANI-16** Retargeter rest-pose compensation.
5. **ANI-17** Mocap process runner, BVH writer, import of external mocap files (§3.6).
6. **ANI-18** MediaPipe backend: adapter script, position→rotation solver, filters, root estimation. Done when a sample video produces a clip on the default skater rig. The quality check is a visual comparison against a reference, plus solver unit tests on synthetic landmarks.
7. **ANI-19** GEM-X spike (timebox one day). Confirm Windows inference, VRAM on a consumer RTX, `motion.pt` fields, and the YOLOX/ViTPose/SOMA asset licenses. Report go/no-go before writing the backend.
8. (Optional) MotionBricks in-betweening in the editor: only if the human wants it (§1.3).

Order: 1 → 2 → 5 → 6 can deliver video→editable clip early. 3, 4 and 7 follow.

---

## 6. Risks

- **MediaPipe quality** may disappoint for fast, rotation-heavy motion such as skate tricks: the board occludes the feet and the body spins. Mitigations: the editor cleanup tools, and GEM-X as the higher-quality path.
- **GEM-X is one week old.** Its API and output fields may change; pin a version in the setup doc.
- **License drift:** recheck the licenses when bumping a backend version. The adapter scripts print the backend version into the job log.
- **Python environment fragility:** isolated per-backend venv paths, plus "Check setup" with actionable errors.

## Sources (accessed 2026-11-06)

- MediaPipe Pose Landmarker: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker
- BlazePose GHUM 3D model card (Apache-2.0): https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf
- GVHMR repo, LICENSE and INSTALL: https://github.com/zju3dv/GVHMR
- WHAM repo and INSTALL: https://github.com/yohanshin/WHAM
- SMPL model license: https://smpl.is.tue.mpg.de/modellicense.html
- FreeMoCap repo and docs: https://github.com/freemocap/freemocap, https://docs.freemocap.org/documentation/
- MotionBricks repo: https://github.com/NVlabs/motionbricks
- GENMO / GEM (non-commercial): https://github.com/NVlabs/GENMO
- GEM-X repo and model card: https://github.com/NVlabs/GEM-X, https://huggingface.co/nvidia/GEM-X
