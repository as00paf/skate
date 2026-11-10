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

enum class MocapPhase { IDLE, PROBING, READY, EXTRACTING, SOLVING, DONE, FAILED }

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

    var options = VideoMocapOptions()

    private var cancel = AtomicBoolean(false)

    val busy: Boolean get() = phase == MocapPhase.PROBING || phase == MocapPhase.EXTRACTING || phase == MocapPhase.SOLVING
    val canCreate: Boolean get() = video != null && !busy && animationsDir() != null

    fun checkSetup() {
        if (checkingSetup) return
        checkingSetup = true
        background {
            val check = runCatching { backend.checkSetup() }.getOrElse { SetupCheck(false, problem = it.message, hint = SetupHint.OTHER) }
            main { setup = check; checkingSetup = false }
        }
    }

    /** Picks [file] and probes it for the preview. A failed probe (no Python yet) still lets the user try Create. */
    fun selectVideo(file: File) {
        if (busy) return
        video = file; info = null; landmarks = null; result = null; error = null; progress = 0f
        phase = MocapPhase.PROBING
        background {
            val probed = runCatching { backend.probe(file, File(workDir(), "thumb_${System.nanoTime()}.png")) }
            main {
                probed.onSuccess { info = it; phase = MocapPhase.READY }
                    .onFailure { error = it.message; phase = MocapPhase.READY }
            }
        }
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
        phase = MocapPhase.EXTRACTING
        background {
            val outcome = runCatching {
                val seq = backend.extract(file, File(workDir(), "landmarks_${System.nanoTime()}.json"), token) { progress = it }
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
