package com.pafoid.skate.editor.animation.mocap

import com.pafoid.skate.editor.settings.MocapSettings
import com.pafoid.skate.engine.animation.mocap.LandmarkSequence
import com.pafoid.skate.engine.animation.mocap.MediaPipeJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class MocapException(message: String) : Exception(message)

/**
 * What "Check setup" found. [api] is the pose API the adapter actually opened (`tasks` or the legacy `solutions`),
 * [model] the Tasks model file, [fallback] why the legacy API is used. [ok] false comes with a [problem] and a [hint].
 */
data class SetupCheck(
    val ok: Boolean,
    val python: String? = null,
    val mediapipe: String? = null,
    val opencv: String? = null,
    val api: String? = null,
    val problem: String? = null,
    val hint: SetupHint? = null,
    val model: String? = null,
    val fallback: String? = null,
)

/** Which help text to show when setup fails (string keys in `editor/animation`). */
enum class SetupHint { INSTALL_PYTHON, INSTALL_MEDIAPIPE, MODEL_FILE, UPDATE_MEDIAPIPE, OTHER }

/** Preview images of a video (ANI-20): `f_<frame>.jpg` files in [dir] for the source frames in [frames]. */
data class PreviewFrames(val dir: File, val totalFrames: Int, val frames: IntArray) {
    fun file(frame: Int) = File(dir, "f_%06d.jpg".format(frame))
}

data class VideoInfo(val fps: Float, val frames: Int, val width: Int, val height: Int, val duration: Float, val thumbnail: File?)

/**
 * Drives `mediapipe_extract.py` (shipped in the editor's resources) with the user's Python (ANI-17, ANI-12 §3.3).
 * The script reads the video from disk; the pose model for MediaPipe's Tasks API comes from [models] (downloaded
 * once, ANI-18). When the model cannot be had the script still runs, so an old MediaPipe can use its legacy API.
 */
class MediaPipeBackend(
    private val runner: MocapProcessRunner,
    private val settings: () -> MocapSettings,
    private val models: PoseModelStore,
    private val script: () -> File = MocapScripts::mediaPipe,
) {
    /** [onModelProgress] reports the model download (0..1) when it has to be downloaded first. */
    fun checkSetup(cancel: AtomicBoolean = AtomicBoolean(false), onModelProgress: (Float) -> Unit = {}): SetupCheck {
        val (model, modelError) = model(cancel, onModelProgress)
        val result = runner.run(argv("check", modelArgs(model)), cancel = cancel)
        if (result.ok) {
            val m = result.messages.lastOrNull() ?: JsonObject(emptyMap())
            fun str(k: String) = m[k]?.jsonPrimitive?.content
            val fallback = listOfNotNull(modelError, str("fallback")).joinToString(" ").ifEmpty { null }
            return SetupCheck(true, str("python"), str("mediapipe"), str("opencv"), str("api"), model = str("model"), fallback = fallback)
        }
        val problem = withModelError(result.errorSummary(), modelError)
        return SetupCheck(false, problem = problem, hint = hintFor(problem))
    }

    fun probe(video: File, thumbnail: File): VideoInfo {
        thumbnail.parentFile?.mkdirs()
        val result = runner.run(argv("probe", listOf("--input", video.absolutePath, "--thumbnail", thumbnail.absolutePath)))
        if (!result.ok) throw MocapException(result.errorSummary())
        val m = result.messages.lastOrNull() ?: throw MocapException("The video probe returned nothing")
        val fps = m["fps"]?.jsonPrimitive?.floatOrNull ?: 30f
        return VideoInfo(
            fps = fps,
            frames = m["frames"]?.jsonPrimitive?.intOrNull ?: 0,
            width = m["width"]?.jsonPrimitive?.intOrNull ?: 0,
            height = m["height"]?.jsonPrimitive?.intOrNull ?: 0,
            duration = m["duration"]?.jsonPrimitive?.floatOrNull ?: 0f,
            thumbnail = thumbnail.takeIf { it.isFile },
        )
    }

    /**
     * Writes small preview images of [video] into [dir] for playback in the window (ANI-20): at most [maxFrames]
     * images, every k-th frame of long videos. Images already there from an earlier run are reused.
     */
    fun previewFrames(video: File, dir: File, cancel: AtomicBoolean, maxFrames: Int = 900, onProgress: (Float) -> Unit = {}): PreviewFrames {
        val done = File(dir, DONE_MARKER)
        if (!done.isFile) {
            dir.deleteRecursively()
            dir.mkdirs()
            val result = runner.run(
                argv("frames", listOf("--input", video.absolutePath, "--dir", dir.absolutePath, "--max-frames", maxFrames.toString())),
                cancel = cancel, onProgress = onProgress,
            )
            if (result.cancelled) throw MocapException("cancelled")
            if (!result.ok) throw MocapException(result.errorSummary())
            val total = result.messages.lastOrNull()?.get("frames")?.jsonPrimitive?.intOrNull ?: 0
            done.writeText(total.toString())
        }
        val frames = dir.listFiles().orEmpty().mapNotNull { FRAME_FILE.matchEntire(it.name)?.groupValues?.get(1)?.toInt() }.sorted().toIntArray()
        val total = done.readText().trim().toIntOrNull()?.takeIf { it > 0 } ?: ((frames.lastOrNull() ?: 0) + 1)
        return PreviewFrames(dir, total, frames)
    }

    /**
     * Runs the pose estimator over [video]; [output] receives the landmark JSON. [range] (source frames, both ends
     * included) limits it to part of the video (ANI-20); landmark times then start at 0 on the range's first frame.
     * [onModelProgress] reports the model download, when one is needed, before detection starts.
     */
    fun extract(
        video: File, output: File, cancel: AtomicBoolean,
        onModelProgress: (Float) -> Unit = {}, range: IntRange? = null, onProgress: (Float) -> Unit,
    ): LandmarkSequence {
        output.parentFile?.mkdirs()
        val (model, modelError) = model(cancel, onModelProgress)
        if (cancel.get()) throw MocapException("cancelled")
        val result = runner.run(
            argv("extract", listOf("--input", video.absolutePath, "--output", output.absolutePath) + modelArgs(model) + rangeArgs(range)),
            cancel = cancel, onProgress = onProgress,
        )
        if (result.cancelled) throw MocapException("cancelled")
        if (!result.ok) throw MocapException(withModelError(result.errorSummary(), modelError))
        if (!output.isFile) throw MocapException("The pose estimator wrote no landmark file")
        return MediaPipeJson.parse(output.readText())
    }

    internal fun argv(command: String, args: List<String>): List<String> =
        listOf(settings().pythonPath, script().absolutePath, command) + args

    private fun rangeArgs(range: IntRange?) = range?.let {
        require(it.first >= 0 && it.last >= it.first) { "Invalid trim range $it" }
        listOf("--start-frame", it.first.toString(), "--end-frame", it.last.toString())
    } ?: emptyList()

    private fun modelArgs(model: File?) = model?.let { listOf("--model", it.absolutePath) } ?: emptyList()

    /** The Tasks model, or why there is none (the script then tries the legacy API). Cancellation propagates. */
    private fun model(cancel: AtomicBoolean, onProgress: (Float) -> Unit): Pair<File?, String?> = try {
        models.resolve(settings().modelPath, cancel, onProgress) to null
    } catch (e: MocapException) {
        if (cancel.get()) throw e
        null to e.message
    }

    /** The download problem first: it is the root cause when the script then finds no usable API. */
    private fun withModelError(problem: String, modelError: String?) =
        if (modelError == null || problem.contains(modelError)) problem else "$modelError $problem"

    companion object {
        /** Start of the adapter's error when neither the Tasks nor the legacy pose API works. */
        const val NO_API = "No MediaPipe pose API works"
        private const val DONE_MARKER = "done.txt"
        private val FRAME_FILE = Regex("""f_(\d+)\.jpg""")
        private val MODEL_PROBLEMS = listOf("download the pose model", "model file not found", "no pose_landmarker model")

        fun hintFor(problem: String): SetupHint = when {
            // Windows' "python" can be a Microsoft Store shortcut that only prints this message.
            problem.contains("Microsoft Store", ignoreCase = true) || problem.contains("Python was not found", ignoreCase = true) ||
                problem.contains("Could not start") -> SetupHint.INSTALL_PYTHON
            problem.contains("not installed", ignoreCase = true) -> SetupHint.INSTALL_MEDIAPIPE
            MODEL_PROBLEMS.any { problem.contains(it, ignoreCase = true) } -> SetupHint.MODEL_FILE
            // The model was there but neither pose API could open it: the MediaPipe install itself is the problem.
            problem.contains(NO_API) -> SetupHint.UPDATE_MEDIAPIPE
            else -> SetupHint.OTHER
        }
    }
}

/** The adapter scripts live in the editor jar; they are copied out next to the system temp files to run. */
object MocapScripts {
    private const val RESOURCE = "/mocap/mediapipe_extract.py"

    fun mediaPipe(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "skate-mocap").apply { mkdirs() }
        val file = File(dir, "mediapipe_extract.py")
        val bytes = MocapScripts::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }
            ?: throw MocapException("The editor is missing $RESOURCE")
        if (!file.isFile || !file.readBytes().contentEquals(bytes)) file.writeBytes(bytes)
        return file
    }
}
