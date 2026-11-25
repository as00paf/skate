package com.pafoid.skate.engine.platform.steampipe

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Cancels a running [SteamCmdRunner.run] from another thread. */
class SteamCmdCancel {
    private val flag = AtomicBoolean(false)
    val isCancelled: Boolean get() = flag.get()
    fun cancel() = flag.set(true)
}

/** How a steamcmd run ended. [exitCode] is -1 when it was cancelled. */
data class SteamCmdRun(val exitCode: Int, val cancelled: Boolean = false)

/**
 * Starts steamcmd (docs/design/NET-07.md §8). Behind an interface so tests never start a process. Blocking: call it
 * from a JobSystem IO worker. [onLine] receives raw output lines; callers pass them through [SteamLogFilter] before
 * showing or keeping them.
 */
fun interface SteamCmdRunner {
    fun run(command: List<String>, workingDir: File, cancel: SteamCmdCancel, onLine: (String) -> Unit): SteamCmdRun
}

/** [ProcessBuilder] runner: merged output, stdin closed (sign-in over stdin comes with NET-07d), cancel kills the tree. */
class JdkSteamCmdRunner : SteamCmdRunner {
    override fun run(command: List<String>, workingDir: File, cancel: SteamCmdCancel, onLine: (String) -> Unit): SteamCmdRun {
        val process = ProcessBuilder(command).directory(workingDir).redirectErrorStream(true).start()
        process.outputStream.close()
        val reader = Thread({
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
        }, "steamcmd-output").apply { isDaemon = true; start() }
        while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
            if (cancel.isCancelled) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
                reader.join(2_000)
                return SteamCmdRun(-1, cancelled = true)
            }
        }
        reader.join(2_000)
        return SteamCmdRun(process.exitValue())
    }
}
