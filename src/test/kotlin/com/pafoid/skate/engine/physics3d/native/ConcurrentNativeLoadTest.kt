package com.pafoid.skate.engine.physics3d.native

import com.github.stephengold.joltjni.Jolt
import com.jme3.bullet.util.NativeLibrary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * PHY-21: several JVMs that share one natives folder (parallel worktree test runs, an editor next to an exported game)
 * all load Bullet and Jolt. They start together on an empty folder, and some start while others hold the DLLs loaded.
 */
class ConcurrentNativeLoadTest {

    @TempDir
    lateinit var work: File

    @Test
    fun NativeLoader_FourJvmsSharingOneFolder_AllLoadBulletAndJolt() {
        val natives = File(work, "natives")
        val argFile = File(work, "java.args").apply {
            // An @argfile keeps long test classpaths under the Windows command-line limit
            writeText("-cp\n\"${System.getProperty("java.class.path").replace("\\", "\\\\")}\"\n")
        }
        val java = File(System.getProperty("java.home"), "bin/java").path
        val startAt = System.currentTimeMillis() + 2_000
        val delays = listOf(0L, 0L, 300L, 600L)

        val children = delays.mapIndexed { i, delay ->
            ProcessBuilder(
                java, "-D${NativeLibraryLoader.NATIVES_DIR_PROPERTY}=${natives.path}", "@${argFile.path}",
                ChildMain::class.java.name, (startAt + delay).toString(),
            ).redirectErrorStream(true).redirectOutput(File(work, "child$i.log")).start()
        }

        children.forEachIndexed { i, child ->
            assertTrue(child.waitFor(60, TimeUnit.SECONDS), "child $i timed out")
            val log = File(work, "child$i.log").readText()
            assertEquals(0, child.exitValue(), "child $i failed:\n$log")
            assertTrue(log.contains("LOADED bullet=") && log.contains("jolt="), "child $i:\n$log")
        }
        // One versioned folder per library, each holding just the DLL (no temp files left behind)
        val dirs = natives.listFiles()!!.filter { it.isDirectory }.map { it.name }.sorted()
        assertEquals(2, dirs.size, "$dirs")
        assertTrue(dirs[0].startsWith("bulletjme-") && dirs[1].startsWith("joltjni-"), "$dirs")
        natives.listFiles()!!.filter { it.isDirectory }.forEach { assertEquals(1, it.list()!!.size, it.name) }
    }

    /** Child process: waits for the shared start time, loads both natives, calls into each, holds them a moment. */
    object ChildMain {
        @JvmStatic
        fun main(args: Array<String>) {
            val startAt = args[0].toLong()
            while (System.currentTimeMillis() < startAt) Thread.onSpinWait()
            val bullet = NativeLibraryLoader().loadNativeLibrary()
            val jolt = NativeLibraryLoader(spec = NativeLibrarySpec.JOLT).loadNativeLibrary()
            if (!bullet || !jolt) {
                println("FAILED bullet=$bullet jolt=$jolt")
                exitProcess(1)
            }
            println("LOADED bullet=${NativeLibrary.versionNumber()} jolt=${Jolt.buildType()}")
            Thread.sleep(1_000) // keep the DLLs loaded (locked on Windows) while later children install
            exitProcess(0)
        }
    }
}
