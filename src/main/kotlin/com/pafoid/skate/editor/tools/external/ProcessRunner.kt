package com.pafoid.skate.editor.tools.external

import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Cancels a running [ProcessRunner.run] from another thread. */
class CancelToken {
    private val cancelled = AtomicBoolean(false)
    val isCancelled: Boolean get() = cancelled.get()
    fun cancel() = cancelled.set(true)
}

/** How an external process ended. [log] is stdout and stderr merged, in order (last [ProcessRunner.MAX_LOG_LINES]). */
data class ProcessResult(
    val exitCode: Int,
    val log: List<String>,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut && !cancelled

    /** The last [n] log lines, for error messages. */
    fun tail(n: Int = 50): String = log.takeLast(n).joinToString("\n")
}

/**
 * Runs external tools (Blender for AST-19, the USD converter for AST-16e). Editor-only; behind an interface so tests
 * never start a process. Blocking: call it from a JobSystem IO worker.
 */
interface ProcessRunner {
    fun run(
        command: List<String>,
        workingDir: File? = null,
        timeout: Duration = Duration.ofMinutes(2),
        cancel: CancelToken = CancelToken(),
        onLine: (String) -> Unit = {},
    ): ProcessResult

    companion object {
        const val MAX_LOG_LINES = 2_000
    }
}

/** [ProcessBuilder] implementation: merged output, timeout and cancel kill the whole process tree. */
class JdkProcessRunner : ProcessRunner {
    override fun run(command: List<String>, workingDir: File?, timeout: Duration, cancel: CancelToken, onLine: (String) -> Unit): ProcessResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).apply { workingDir?.let { directory(it) } }.start()
        process.outputStream.close()
        val log = ArrayDeque<String>()
        val reader = Thread({
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    synchronized(log) {
                        log.addLast(line)
                        if (log.size > ProcessRunner.MAX_LOG_LINES) log.removeFirst()
                    }
                    onLine(line)
                }
            }
        }, "process-output").apply { isDaemon = true; start() }

        val deadline = System.nanoTime() + timeout.toNanos()
        var timedOut = false
        var cancelled = false
        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
            if (cancel.isCancelled) { cancelled = true; break }
            if (System.nanoTime() > deadline) { timedOut = true; break }
        }
        if (timedOut || cancelled) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
        reader.join(2_000)
        val lines = synchronized(log) { log.toList() }
        val exit = if (process.isAlive) -1 else process.exitValue()
        return ProcessResult(exit, lines, timedOut, cancelled)
    }
}
