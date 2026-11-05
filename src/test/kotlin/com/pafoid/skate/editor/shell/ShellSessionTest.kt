package com.pafoid.skate.editor.shell

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ShellSessionTest {

    private val win = ShellSession.isWindows()
    private val dir = File(".").absoluteFile
    private val session = ShellSession()

    /** A command that sleeps about [seconds] s in a child process of the shell. */
    private fun sleep(seconds: Int) = if (win) "ping -n ${seconds + 1} 127.0.0.1 > nul" else "sleep $seconds"

    private fun drainUntil(timeoutMs: Long, predicate: (List<ShellLine>) -> Boolean): List<ShellLine> {
        val seen = mutableListOf<ShellLine>()
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            seen += session.drain()
            if (predicate(seen)) return seen
            Thread.sleep(20)
        }
        return seen
    }

    @AfterEach
    fun stop() = session.shutdown()

    @Test
    fun `runs a command, echoes it and reports the exit code`() {
        assertTrue(session.run("echo hello shell", dir))
        assertTrue(session.await(10_000))
        val lines = session.drain()
        assertEquals(ShellLine("> echo hello shell", ShellLine.Kind.COMMAND), lines.first())
        assertTrue(lines.any { it.text.trim() == "hello shell" }, lines.toString())
        assertEquals(0, (session.status as ShellStatus.Exited).exitCode)
        assertEquals(ShellLine.Kind.INFO, lines.last().kind)
    }

    @Test
    fun `non-zero exit codes are reported as errors`() {
        session.run("exit 3", dir)
        assertTrue(session.await(10_000))
        assertEquals(3, (session.status as ShellStatus.Exited).exitCode)
        assertEquals(ShellLine.Kind.ERROR, session.drain().last().kind)
    }

    @Test
    fun `run returns at once and output streams while the command is still running`() {
        val started = System.currentTimeMillis()
        assertTrue(session.run("echo first&& ${sleep(3)}&& echo second".let { if (win) it else it.replace("&&", " &&") }, dir))
        assertTrue(System.currentTimeMillis() - started < 1000, "run blocked the caller")
        assertTrue(session.isRunning)
        val early = drainUntil(2500) { lines -> lines.any { it.text.trim() == "first" } }
        assertTrue(early.any { it.text.trim() == "first" }, "no live output: $early")
        assertTrue(session.isRunning, "should still be running when the first line arrives")
        assertFalse(session.run("echo again", dir), "only one command at a time")
        assertTrue(session.await(15_000))
    }

    @Test
    fun `cancel stops the command and its child processes`() {
        session.run(sleep(30), dir)
        Thread.sleep(500)
        val started = System.currentTimeMillis()
        session.cancel()
        // The output reader only ends once every process holding the pipe is gone, so this also proves the
        // child (ping/sleep) was killed, not just the shell.
        assertTrue(session.await(5_000), "process tree still alive after cancel")
        assertTrue(System.currentTimeMillis() - started < 5_000)
        assertTrue(session.status is ShellStatus.Cancelled, session.status.toString())
        assertFalse(session.isRunning)
        assertTrue(session.run("echo after", dir), "a new command can run after cancelling")
        assertTrue(session.await(10_000))
    }

    @Test
    fun `blank commands do nothing and a missing shell fails cleanly`() {
        assertFalse(session.run("   ", dir))
        assertEquals(ShellStatus.Idle, session.status)

        val broken = ShellSession(shellPrefix = listOf("definitely-not-a-shell-xyz"))
        assertTrue(broken.run("echo x", dir))
        assertTrue(broken.await(5_000))
        assertTrue(broken.status is ShellStatus.Failed)
        assertEquals(ShellLine.Kind.ERROR, broken.drain().last().kind)
    }

    @Test
    fun `ANSI escapes and carriage returns are stripped`() {
        assertEquals("BUILD SUCCESSFUL", ShellSession.clean("\u001B[1m\u001B[32mBUILD SUCCESSFUL\u001B[0m\r"))
        assertEquals(listOf("cmd.exe", "/c"), ShellSession.defaultShell("Windows 11"))
        assertEquals(listOf("sh", "-c"), ShellSession.defaultShell("Linux"))
    }
}
