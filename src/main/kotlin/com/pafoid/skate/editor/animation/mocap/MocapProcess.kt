package com.pafoid.skate.editor.animation.mocap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A started external process (real or faked in tests). */
interface MocapProcess {
    val stdout: BufferedReader
    val stderr: BufferedReader
    /** Exit code, or null if it did not exit within [timeoutMs]. */
    fun waitFor(timeoutMs: Long): Int?
    fun destroy()
}

/** Starts processes from an argv list. Never through a shell, so paths with spaces or quotes are safe. */
fun interface ProcessLauncher {
    fun launch(argv: List<String>, workDir: File?): MocapProcess

    companion object {
        val SYSTEM = ProcessLauncher { argv, dir ->
            val p = ProcessBuilder(argv).apply { if (dir != null) directory(dir) }.start()
            object : MocapProcess {
                override val stdout = p.inputStream.bufferedReader()
                override val stderr = p.errorStream.bufferedReader()
                override fun waitFor(timeoutMs: Long): Int? = if (p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) p.exitValue() else null
                override fun destroy() { p.descendants().forEach { it.destroyForcibly() }; p.destroyForcibly() }
            }
        }
    }
}

/** Result of one run: [messages] are the JSON objects the script printed (progress lines excluded). */
class RunResult(val exitCode: Int?, val messages: List<JsonObject>, val stderrTail: List<String>, val cancelled: Boolean, val timedOut: Boolean) {
    val ok: Boolean get() = exitCode == 0 && !cancelled && !timedOut

    /** One line for the UI: the script's own error message, else the last stderr line. */
    fun errorSummary(): String = messages.lastOrNull { "error" in it }?.get("error")?.jsonPrimitive?.content
        ?: stderrTail.lastOrNull { it.isNotBlank() }
        ?: when { cancelled -> "cancelled"; timedOut -> "timed out"; else -> "exit code $exitCode" }
}

/**
 * Runs a mocap tool and follows its protocol: one JSON object per stdout line, `{"progress": 0..1}` for progress,
 * anything else kept as a message (`{"error": "..."}` for failures). Non-JSON lines are ignored. Call from a worker.
 */
class MocapProcessRunner(private val launcher: ProcessLauncher = ProcessLauncher.SYSTEM, private val timeoutMs: Long = 60 * 60 * 1000L) {
    private val json = Json { ignoreUnknownKeys = true }

    fun run(argv: List<String>, workDir: File? = null, cancel: AtomicBoolean = AtomicBoolean(false), onProgress: (Float) -> Unit = {}): RunResult {
        val process = try {
            launcher.launch(argv, workDir)
        } catch (e: Exception) {
            return RunResult(null, listOf(JsonObject(mapOf("error" to kotlinx.serialization.json.JsonPrimitive("Could not start ${argv.firstOrNull()}: ${e.message}")))), emptyList(), false, false)
        }
        val stderr = ArrayDeque<String>()
        val errReader = Thread {
            runCatching { process.stderr.forEachLine { synchronized(stderr) { stderr.addLast(it); if (stderr.size > 40) stderr.removeFirst() } } }
        }.apply { isDaemon = true; start() }

        val messages = ArrayList<JsonObject>()
        val watchdog = Thread {
            while (!Thread.currentThread().isInterrupted) {
                if (cancel.get()) { process.destroy(); return@Thread }
                try { Thread.sleep(100) } catch (_: InterruptedException) { return@Thread }
            }
        }.apply { isDaemon = true; start() }
        try {
            runCatching {
                process.stdout.forEachLine { line ->
                    val obj = runCatching { json.parseToJsonElement(line.trim()).jsonObject }.getOrNull() ?: return@forEachLine
                    val progress = obj["progress"]?.jsonPrimitive?.floatOrNull
                    if (progress != null && obj.size == 1) onProgress(progress.coerceIn(0f, 1f)) else messages += obj
                }
            }
            val code = process.waitFor(timeoutMs)
            if (code == null) process.destroy()
            errReader.join(1000)
            return RunResult(code, messages, synchronized(stderr) { stderr.toList() }, cancel.get(), code == null)
        } finally {
            watchdog.interrupt()
        }
    }
}
