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
     * Runs the pose estimator over [video]; [output] receives the landmark JSON. [onModelProgress] reports the model
     * download, when one is needed, before detection starts.
     */
    fun extract(
        video: File, output: File, cancel: AtomicBoolean,
        onModelProgress: (Float) -> Unit = {}, onProgress: (Float) -> Unit,
    ): LandmarkSequence {
        output.parentFile?.mkdirs()
        val (model, modelError) = model(cancel, onModelProgress)
        if (cancel.get()) throw MocapException("cancelled")
        val result = runner.run(
            argv("extract", listOf("--input", video.absolutePath, "--output", output.absolutePath) + modelArgs(model)),
            cancel = cancel, onProgress = onProgress,
        )
        if (result.cancelled) throw MocapException("cancelled")
        if (!result.ok) throw MocapException(withModelError(result.errorSummary(), modelError))
        if (!output.isFile) throw MocapException("The pose estimator wrote no landmark file")
        return MediaPipeJson.parse(output.readText())
    }

    internal fun argv(command: String, args: List<String>): List<String> =
        listOf(settings().pythonPath, script().absolutePath, command) + args

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
