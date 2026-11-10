package com.pafoid.skate.editor.animation.mocap

import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.mocap.LandmarkSequence
import com.pafoid.skate.engine.animation.mocap.Smoothing
import com.pafoid.skate.engine.animation.mocap.SolveOptions
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Which rig the clip is made for. */
enum class TargetRig { SKATER, SELECTED }

/** [DOWNLOADING_MODEL]: the pose model is fetched once before the first detection (ANI-18). */
enum class MocapPhase { IDLE, PROBING, READY, DOWNLOADING_MODEL, EXTRACTING, SOLVING, DONE, FAILED }

/** Settings the window edits; defaults give a usable clip with one click. */
data class VideoMocapOptions(
    val smoothing: Smoothing = Smoothing.MEDIUM,
    val rootMotion: Boolean = false,
    val footLock: Boolean = true,
    val target: TargetRig = TargetRig.SKATER,
)

/**
 * State and actions of the Video to Animation window (ANI-17): pick a video -> probe (thumbnail, length) -> Create:
 * extract landmarks, solve, retarget, save a `.skanim` under `Assets/Animations` and reveal it. Also imports mocap
 * files (BVH/FBX/glTF) made by other tools. Work runs on [background]; results are applied on [main].
 * No ImGui here, so the flow is unit-tested with a fake process runner.
 */
class VideoToAnimationController(
    private val backend: MediaPipeBackend,
    private val workDir: () -> File,
    private val animationsDir: () -> File?,
    private val targetSkeleton: (TargetRig) -> Skeleton?,
    private val loadAnimationFile: (File) -> Pair<Animation, Skeleton?>,
    private val saveClip: (SkClip, File) -> Unit,
    private val onCreated: (File) -> Unit,
    private val background: (() -> Unit) -> Unit,
    private val main: (() -> Unit) -> Unit,
) {
    @Volatile var phase = MocapPhase.IDLE; private set
    @Volatile var progress = 0f; private set
    @Volatile var video: File? = null; private set
    @Volatile var info: VideoInfo? = null; private set
    @Volatile var landmarks: LandmarkSequence? = null; private set
    /** Last failure, for the UI (the tool's own message when it gave one). */
    @Volatile var error: String? = null; private set
    @Volatile var result: File? = null; private set
    @Volatile var coverage = 0f; private set
    @Volatile var setup: SetupCheck? = null; private set
    @Volatile var checkingSetup = false; private set
    /** Model download progress (0..1) while Check setup fetches it, else null. */
    @Volatile var setupDownload: Float? = null; private set

    var options = VideoMocapOptions()

    /** Playback and trim range of the chosen video (ANI-20); null until the probe read its frame rate. */
    @Volatile var player: VideoPlayer? = null; private set
    /** Preview images for playback; null while they are being made (or when Python is not set up). */
    @Volatile var preview: PreviewFrames? = null; private set
    /** Progress (0..1) of making the preview images, null when not running. */
    @Volatile var previewProgress: Float? = null; private set
    /** First source frame of the range [landmarks] were extracted from (their frame 0). */
    @Volatile var landmarksStart = 0; private set
    private var previewCancel = AtomicBoolean(false)

    private var cancel = AtomicBoolean(false)

    val busy: Boolean get() = phase == MocapPhase.PROBING || phase == MocapPhase.DOWNLOADING_MODEL ||
        phase == MocapPhase.EXTRACTING || phase == MocapPhase.SOLVING
    val canCreate: Boolean get() = video != null && !busy && animationsDir() != null

    fun checkSetup() {
        if (checkingSetup) return
        checkingSetup = true
        background {
            val check = runCatching { backend.checkSetup { setupDownload = it.takeIf { p -> p < 1f } } }
                .getOrElse { SetupCheck(false, problem = it.message, hint = SetupHint.OTHER) }
            main { setup = check; checkingSetup = false; setupDownload = null }
        }
    }

    /** Picks [file] and probes it for the preview. A failed probe (no Python yet) still lets the user try Create. */
    fun selectVideo(file: File) {
        if (busy) return
        video = file; info = null; landmarks = null; result = null; error = null; progress = 0f
        player = null; preview = null; previewProgress = null; landmarksStart = 0
        previewCancel.set(true)
        phase = MocapPhase.PROBING
        background {
            val probed = runCatching { backend.probe(file, File(workDir(), "thumb_${System.nanoTime()}.png")) }
            main {
                probed.onSuccess { info = it; player = VideoPlayer(it.fps, it.frames); phase = MocapPhase.READY; loadPreview(file) }
                    .onFailure { error = it.message; phase = MocapPhase.READY }
            }
        }
    }

    /** Makes the playback images in the background; they are cached per video file and its last change. */
    private fun loadPreview(file: File) {
        val token = AtomicBoolean(false).also { previewCancel = it }
        previewProgress = 0f
        val dir = File(workDir(), "preview_" + Integer.toHexString("${file.absolutePath}|${file.length()}|${file.lastModified()}".hashCode()))
        background {
            val frames = runCatching { backend.previewFrames(file, dir, token) { if (!token.get()) previewProgress = it } }
            main {
                if (token.get() || video != file) return@main
                previewProgress = null
                frames.onSuccess { p -> preview = p; player?.updateFrameCount(p.totalFrames) }
                // No preview is not an error worth blocking on: the thumbnail stays, Create still works.
            }
        }
    }

    /** Advances playback by [dt] seconds (called every frame by the window). */
    fun tick(dt: Float) { player?.tick(dt) }

    /** The preview image for the player's current frame, when extracted. */
    fun previewImage(): File? {
        val p = preview ?: return null
        val frame = player?.previewFrameFor(p.frames) ?: return null
        return p.file(frame)
    }

    /** Index into [landmarks] for source video [frame], or null when that frame is outside the detected range. */
    fun landmarkIndexFor(frame: Int): Int? {
        val seq = landmarks ?: return null
        val i = frame - landmarksStart
        return i.takeIf { it in seq.frames.indices }
    }

    fun create() {
        val file = video ?: return
        val dir = animationsDir() ?: return
        if (busy) return
        val opts = options
        error = null; result = null; progress = 0f
        // Rig lookups may load a model (GL), so they run here on the main thread.
        val target = targetSkeleton(opts.target) ?: return fail(MocapException("No rig: select an object with a skeleton"), null)
        val token = AtomicBoolean(false).also { cancel = it }
        // Only the marked range becomes the clip (ANI-20); the whole video when nothing is marked.
        val range = player?.takeIf { it.trimmed }?.let { it.inFrame..it.outFrame }
        player?.pause()
        phase = MocapPhase.EXTRACTING
        background {
            val outcome = runCatching {
                val seq = backend.extract(file, File(workDir(), "landmarks_${System.nanoTime()}.json"), token,
                    onModelProgress = { p ->
                        phase = if (p < 1f) MocapPhase.DOWNLOADING_MODEL else MocapPhase.EXTRACTING
                        progress = if (p < 1f) p else 0f
                    },
                    range = range,
                ) { progress = it }
                landmarksStart = range?.first ?: 0
                landmarks = seq
                phase = MocapPhase.SOLVING
                val made = MocapImport.fromLandmarks(seq, file.nameWithoutExtension, target,
                    SolveOptions(opts.smoothing, opts.rootMotion, opts.footLock))
                made
            }
            main {
                outcome.onSuccess { made -> save(made, dir, file.nameWithoutExtension) }
                    .onFailure { fail(it, token) }
            }
        }
    }

    /** Imports a BVH, FBX or glTF made by another tool onto the target rig. */
    fun importFile(file: File) {
        val dir = animationsDir() ?: return
        if (busy) return
        error = null; result = null; progress = 0f
        // Model loads may touch GL: rig and source file are read here on the main thread, the solve runs in background.
        val rig = targetSkeleton(options.target) ?: return fail(MocapException("No rig: select an object with a skeleton"), null)
        val isBvh = file.extension.equals("bvh", ignoreCase = true)
        val loaded = if (isBvh) null else runCatching { loadAnimationFile(file) }.getOrElse { return fail(it, null) }
        phase = MocapPhase.SOLVING
        background {
            val outcome = runCatching {
                if (loaded == null) MocapImport.fromBvh(file.readText(), file.nameWithoutExtension, rig)
                else MocapImport.fromAnimationFile(loaded.first, loaded.second, rig)
            }
            main {
                outcome.onSuccess { save(it, dir, file.nameWithoutExtension) }.onFailure { fail(it, null) }
            }
        }
    }

    fun cancel() = cancel.set(true)

    private fun save(made: MocapClip, dir: File, base: String) {
        try {
            val file = com.pafoid.skate.editor.commands.animation.CreateAnimationClipCommand.freeFile(dir, base)
            saveClip(made.clip.copy(name = file.nameWithoutExtension), file)
            coverage = made.coverage
            result = file
            progress = 1f
            phase = MocapPhase.DONE
            onCreated(file)
        } catch (e: Exception) {
            fail(e, null)
        }
    }

    private fun fail(e: Throwable, token: AtomicBoolean?) {
        error = if (token?.get() == true) null else (e.message ?: e.javaClass.simpleName)
        phase = if (token?.get() == true) MocapPhase.READY else MocapPhase.FAILED
    }
}
