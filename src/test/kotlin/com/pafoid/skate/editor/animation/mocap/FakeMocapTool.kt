package com.pafoid.skate.editor.animation.mocap

import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A process that prints [stdout] lines, [stderr] lines, and exits with [exit]; optionally blocks until destroyed. */
class FakeProcess(stdout: List<String>, stderr: List<String> = emptyList(), private val exit: Int = 0, private val blockUntilDestroyed: Boolean = false) : MocapProcess {
    private val released = CountDownLatch(1)
    var destroyed = false; private set
    override val stdout: BufferedReader = if (!blockUntilDestroyed) BufferedReader(StringReader(stdout.joinToString("\n")))
    else object : BufferedReader(StringReader("")) {
        private var sent = false
        override fun readLine(): String? {
            if (!sent) { sent = true; return stdout.firstOrNull() }
            released.await(5, TimeUnit.SECONDS); return null
        }
    }
    override val stderr: BufferedReader = BufferedReader(StringReader(stderr.joinToString("\n")))
    override fun waitFor(timeoutMs: Long): Int? = if (blockUntilDestroyed && !destroyed) null else if (destroyed) 137 else exit
    override fun destroy() { destroyed = true; released.countDown() }
}

/**
 * Plays the role of `python mediapipe_extract.py` (ANI-17, ANI-18): answers check/probe/extract from the argv it is
 * given and picks the pose API like the real script: the Tasks API when `--model` names a file, the legacy solutions
 * API only when [hasSolutions] (MediaPipe before 0.10.14), else the script's "No MediaPipe pose API works" error.
 * [checkLine] replaces the check answer outright.
 */
class FakeMocapTool(
    var landmarkJson: String = "",
    var checkLine: String? = null,
    var extractError: String? = null,
    var mediapipe: String = "0.10.21",
    var hasSolutions: Boolean = false,
) : ProcessLauncher {
    val calls = ArrayList<List<String>>()

    /** The API the real script would open for [argv], or its error. */
    fun api(argv: List<String>): Pair<String?, String?> {
        val i = argv.indexOf("--model")
        val model = if (i >= 0) argv[i + 1] else null
        val tasksProblem = when {
            model == null -> "no pose_landmarker model file was given"
            !File(model).isFile -> "model file not found: $model"
            else -> return "tasks" to null
        }
        if (hasSolutions) return "solutions" to null
        return null to "${MediaPipeBackend.NO_API}. Tasks API (pose model): $tasksProblem. Legacy solutions API: not in MediaPipe $mediapipe."
    }

    override fun launch(argv: List<String>, workDir: File?): MocapProcess {
        calls += argv
        fun arg(name: String) = argv[argv.indexOf(name) + 1]
        return when (argv[2]) {
            "check" -> checkLine?.let { FakeProcess(listOf(it), exit = if ("\"error\"" in it) 1 else 0) } ?: run {
                val (api, error) = api(argv)
                if (api == null) FakeProcess(listOf(errorLine(error!!)), exit = 1)
                else {
                    val model = if (api == "tasks") ""","model":${quote(arg("--model"))}""" else ""","fallback":"Tasks API unavailable""""
                    FakeProcess(listOf("""{"python":"3.11.9","mediapipe":"$mediapipe","opencv":"4.10.0","api":"$api"$model}"""))
                }
            }
            "probe" -> {
                File(arg("--thumbnail")).writeBytes(ByteArray(4))
                FakeProcess(listOf("""{"fps":30.0,"frames":90,"width":1280,"height":720,"duration":3.0}"""))
            }
            "extract" -> {
                val error = extractError ?: api(argv).second
                if (error != null) FakeProcess(listOf(errorLine(error)), exit = 1)
                else {
                    File(arg("--output")).writeText(landmarkJson)
                    FakeProcess(listOf("""{"progress":0.5}""", "not json", """{"progress":1.0}""", """{"done":true,"frames":3,"detected":3}"""))
                }
            }
            else -> FakeProcess(listOf("""{"error":"unknown command"}"""), exit = 2)
        }
    }

    private fun quote(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()
    private fun errorLine(message: String) = """{"error":${quote(message)}}"""
}

/** Stands in for the model download: writes [size] bytes in two progress steps, or throws [failure]. */
class FakeModelFetcher(var size: Int = 2_000, var failure: String? = null) : ModelFetcher {
    var fetches = 0; private set

    override fun fetch(url: String, target: File, cancel: java.util.concurrent.atomic.AtomicBoolean, onProgress: (Float) -> Unit) {
        fetches++
        failure?.let { throw java.io.IOException(it) }
        target.writeBytes(ByteArray(size / 2)); onProgress(0.5f)
        if (cancel.get()) throw java.io.IOException("cancelled")
        target.appendBytes(ByteArray(size - size / 2)); onProgress(1f)
    }
}

/** A model store in [dir] that "downloads" through [fetcher]; files of 1 kB and more count as complete. */
fun fakeModels(dir: File, fetcher: FakeModelFetcher = FakeModelFetcher()) = PoseModelStore({ File(dir, "models") }, fetcher, "https://example.invalid/pose.task", minBytes = 1_000)
