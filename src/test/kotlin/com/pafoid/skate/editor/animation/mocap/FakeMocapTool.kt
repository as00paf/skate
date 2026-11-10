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

/** Plays the role of `python mediapipe_extract.py`: answers check/probe/extract from the argv it is given. */
class FakeMocapTool(
    var landmarkJson: String = "",
    var checkLine: String = """{"python":"3.11.9","mediapipe":"0.10.14","opencv":"4.10.0","api":"solutions"}""",
    var extractError: String? = null,
) : ProcessLauncher {
    val calls = ArrayList<List<String>>()

    override fun launch(argv: List<String>, workDir: File?): MocapProcess {
        calls += argv
        fun arg(name: String) = argv[argv.indexOf(name) + 1]
        return when (argv[2]) {
            "check" -> FakeProcess(listOf(checkLine))
            "probe" -> {
                File(arg("--thumbnail")).writeBytes(ByteArray(4))
                FakeProcess(listOf("""{"fps":30.0,"frames":90,"width":1280,"height":720,"duration":3.0}"""))
            }
            "extract" -> {
                val error = extractError
                if (error != null) FakeProcess(listOf("""{"error":"$error"}"""), exit = 1)
                else {
                    File(arg("--output")).writeText(landmarkJson)
                    FakeProcess(listOf("""{"progress":0.5}""", "not json", """{"progress":1.0}""", """{"done":true,"frames":3,"detected":3}"""))
                }
            }
            else -> FakeProcess(listOf("""{"error":"unknown command"}"""), exit = 2)
        }
    }
}
