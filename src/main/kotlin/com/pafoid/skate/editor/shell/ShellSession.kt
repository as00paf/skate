package com.pafoid.skate.editor.shell

import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/** State of the last command run by a [ShellSession]. */
sealed interface ShellStatus {
    data object Idle : ShellStatus
    data class Running(val command: String, val startedAtMs: Long) : ShellStatus
    data class Exited(val command: String, val exitCode: Int, val durationMs: Long) : ShellStatus
    data class Cancelled(val command: String, val durationMs: Long) : ShellStatus
    data class Failed(val command: String, val message: String) : ShellStatus
}

/** One output line; [kind] tells the panel how to colour it. */
data class ShellLine(val text: String, val kind: Kind = Kind.OUTPUT) {
    enum class Kind { COMMAND, OUTPUT, INFO, ERROR }
}

/**
 * Runs one shell command at a time without blocking the caller (the editor's UI thread): the process and its output
 * reader live on background threads, output lines are queued and the UI takes them with [drain] each frame.
 * [cancel] kills the whole process tree (Gradle and the CLI start child JVMs). stderr is merged into stdout so lines
 * stay in order. Commands go through the platform shell ([shellPrefix]) so `gradlew.bat test` or pipes work.
 */
class ShellSession(
    private val shellPrefix: List<String> = defaultShell(),
    private val charset: Charset = nativeCharset(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

        fun isWindows(os: String = System.getProperty("os.name")) = os.lowercase().contains("win")

        fun defaultShell(os: String = System.getProperty("os.name")): List<String> =
            if (isWindows(os)) listOf("cmd.exe", "/c") else listOf("sh", "-c")

        /** Console encoding of the OS (what cmd.exe and child processes print in), falling back to the JVM default. */
        fun nativeCharset(): Charset =
            System.getProperty("native.encoding")?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charset.defaultCharset()

        /** Removes ANSI colour/cursor escapes and a trailing CR, which a plain ImGui text view would show as junk. */
        fun clean(line: String): String = ANSI.replace(line, "").removeSuffix("\r")
    }

    private val pending = ConcurrentLinkedQueue<ShellLine>()

    @Volatile var status: ShellStatus = ShellStatus.Idle
        private set

    @Volatile private var process: Process? = null
    @Volatile private var cancelRequested = false
    private var worker: Thread? = null

    val isRunning: Boolean get() = status is ShellStatus.Running

    /**
     * Starts [command] in [workingDir]; returns false (and does nothing) if blank or one is already running.
     * Output, the echoed command and the final status line arrive through [drain].
     */
    @Synchronized
    fun run(command: String, workingDir: File): Boolean {
        val line = command.trim()
        if (line.isEmpty() || isRunning) return false
        cancelRequested = false
        val started = clock()
        status = ShellStatus.Running(line, started)
        pending.add(ShellLine("> $line", ShellLine.Kind.COMMAND))
        worker = Thread({ execute(line, workingDir, started) }, "shell-panel").apply { isDaemon = true; start() }
        return true
    }

    private fun execute(line: String, workingDir: File, started: Long) {
        try {
            val p = ProcessBuilder(shellPrefix + line).directory(workingDir).redirectErrorStream(true).start()
            process = p
            if (cancelRequested) killTree(p)
            p.inputStream.bufferedReader(charset).useLines { lines -> lines.forEach { pending.add(ShellLine(clean(it))) } }
            val code = p.waitFor()
            val duration = clock() - started
            status = if (cancelRequested) {
                pending.add(ShellLine("Cancelled after ${duration} ms", ShellLine.Kind.INFO))
                ShellStatus.Cancelled(line, duration)
            } else {
                pending.add(ShellLine("Exited with code $code in ${duration} ms", if (code == 0) ShellLine.Kind.INFO else ShellLine.Kind.ERROR))
                ShellStatus.Exited(line, code, duration)
            }
        } catch (e: Exception) {
            pending.add(ShellLine("Could not run: ${e.message}", ShellLine.Kind.ERROR))
            status = ShellStatus.Failed(line, e.message ?: e.javaClass.simpleName)
        } finally {
            process = null
        }
    }

    /** Stops the running command and every process it started; no-op when idle. */
    fun cancel() {
        if (!isRunning) return
        cancelRequested = true
        process?.let { killTree(it) }
    }

    private fun killTree(p: Process) {
        p.descendants().forEach { it.destroyForcibly() }
        p.destroyForcibly()
    }

    /** Lines produced since the last call, oldest first. Safe to call from any thread. */
    fun drain(max: Int = Int.MAX_VALUE): List<ShellLine> {
        val out = ArrayList<ShellLine>()
        while (out.size < max) out.add(pending.poll() ?: break)
        return out
    }

    /** Waits for the current command to finish (tests and shutdown); true if it finished in time. */
    fun await(timeoutMs: Long): Boolean {
        val w = worker ?: return true
        w.join(timeoutMs)
        return !w.isAlive
    }

    /** Cancels and waits briefly; call when the editor closes so no orphan Gradle daemon client is left. */
    fun shutdown() {
        cancel()
        await(TimeUnit.SECONDS.toMillis(2))
    }
}
