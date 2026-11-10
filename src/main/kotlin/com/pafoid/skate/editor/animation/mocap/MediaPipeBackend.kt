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

/** What "Check setup" found. [ok] false comes with a [problem] and a [hint] for the user. */
data class SetupCheck(
    val ok: Boolean,
    val python: String? = null,
    val mediapipe: String? = null,
    val opencv: String? = null,
    val api: String? = null,
    val problem: String? = null,
    val hint: SetupHint? = null,
)

/** Which help text to show when setup fails (string keys in `editor/animation`). */
enum class SetupHint { INSTALL_PYTHON, INSTALL_MEDIAPIPE, MODEL_FILE, OTHER }

data class VideoInfo(val fps: Float, val frames: Int, val width: Int, val height: Int, val duration: Float, val thumbnail: File?)

/**
 * Drives `mediapipe_extract.py` (shipped in the editor's resources) with the user's Python (ANI-17, ANI-12 §3.3).
 * Local files only: the script reads the video from disk and nothing is downloaded.
 */
class MediaPipeBackend(
    private val runner: MocapProcessRunner,
    private val settings: () -> MocapSettings,
    private val script: () -> File = MocapScripts::mediaPipe,
) {
    fun checkSetup(): SetupCheck {
        val s = settings()
        val result = runner.run(argv("check", modelArgs(s)))
        if (result.ok) {
            val m = result.messages.lastOrNull() ?: JsonObject(emptyMap())
            fun str(k: String) = m[k]?.jsonPrimitive?.content
            return SetupCheck(true, str("python"), str("mediapipe"), str("opencv"), str("api"))
        }
        val problem = result.errorSummary()
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

    /** Runs the pose estimator over [video]; [output] receives the landmark JSON. */
    fun extract(video: File, output: File, cancel: AtomicBoolean, onProgress: (Float) -> Unit): LandmarkSequence {
        output.parentFile?.mkdirs()
        val s = settings()
        val result = runner.run(
            argv("extract", listOf("--input", video.absolutePath, "--output", output.absolutePath) + modelArgs(s)),
            cancel = cancel, onProgress = onProgress,
        )
        if (result.cancelled) throw MocapException("cancelled")
        if (!result.ok) throw MocapException(result.errorSummary())
        if (!output.isFile) throw MocapException("The pose estimator wrote no landmark file")
        return MediaPipeJson.parse(output.readText())
    }

    internal fun argv(command: String, args: List<String>): List<String> =
        listOf(settings().pythonPath, script().absolutePath, command) + args

    private fun modelArgs(s: MocapSettings) = s.modelPath?.let { listOf("--model", it) } ?: emptyList()

    companion object {
        fun hintFor(problem: String): SetupHint = when {
            // Windows' "python" can be a Microsoft Store shortcut that only prints this message.
            problem.contains("Microsoft Store", ignoreCase = true) || problem.contains("Python was not found", ignoreCase = true) ||
                problem.startsWith("Could not start") -> SetupHint.INSTALL_PYTHON
            problem.contains("not installed", ignoreCase = true) -> SetupHint.INSTALL_MEDIAPIPE
            problem.contains("model", ignoreCase = true) -> SetupHint.MODEL_FILE
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
