package com.pafoid.skate.editor.tools.external

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Duration

/** A [ProcessRunner] that answers from a table instead of starting processes. */
class FakeRunner(private val answer: (List<String>) -> ProcessResult) : ProcessRunner {
    val commands = mutableListOf<List<String>>()
    override fun run(command: List<String>, workingDir: File?, timeout: Duration, cancel: CancelToken, onLine: (String) -> Unit): ProcessResult {
        commands += command
        return answer(command)
    }
}

class BlenderToolsTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun Version_ParsesBlenderOutput() {
        assertEquals(BlenderVersion(5, 2, 3), BlenderVersion.parse("Blender 5.2.3 LTS\n\tbuild date: 2026-10-22"))
        assertEquals(BlenderVersion(4, 2, 0), BlenderVersion.parse("Blender 4.2"))
        assertNull(BlenderVersion.parse("Python 3.11"))
        assertTrue(BlenderVersion(4, 1, 9) < BlenderVersion.MINIMUM)
        assertTrue(BlenderVersion(10, 0) > BlenderVersion(4, 2, 5))
    }

    @Test
    fun Command_RunsBackgroundFactoryWithPythonExitCode_NeverAutoexec() {
        val cmd = BlenderCommand.build(File("C:/B/blender.exe"), File("C:/p/Assets/cap blend.blend"), File("C:/t/s.py"), listOf("{\"a\":1}"))

        assertEquals(listOf("C:/B/blender.exe".replace('/', File.separatorChar), "-b", "--factory-startup", "--python-exit-code", "1"), cmd.take(5))
        assertEquals(listOf("--python", File("C:/t/s.py").path, "--", "{\"a\":1}"), cmd.takeLast(4))
        assertTrue(cmd.none { it in BlenderCommand.FORBIDDEN }, "a .blend's embedded Python must never auto-run")
        assertTrue(File("C:/p/Assets/cap blend.blend").path in cmd, "the path is one argument, spaces included")
    }

    @Test
    fun ExecutableFromCommand_MapsTheLauncherToBlender() {
        val exe = BlenderLocator.executableFromCommand("\"C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender-launcher.exe\" \"%1\"")
        assertEquals("blender.exe", exe?.name)
        assertEquals("Blender 5.2", exe?.parentFile?.name)
        assertEquals("blender.exe", BlenderLocator.executableFromCommand("C:\\B\\blender.exe %1")?.name)
    }

    private fun exe(name: String) = File(dir, "$name/blender.exe").apply { parentFile.mkdirs(); writeText("") }

    private fun locator(versions: Map<String, String>, setting: String? = null, path: List<File> = emptyList(), registry: String? = null): Pair<BlenderLocator, FakeRunner> {
        val runner = FakeRunner { cmd ->
            when {
                cmd[0] == "reg" && cmd[2] == """HKCR\.blend""" -> registry?.let { ProcessResult(0, listOf("    (Default)    REG_SZ    blender.x")) } ?: ProcessResult(1, emptyList())
                cmd[0] == "reg" -> ProcessResult(0, listOf("    (Default)    REG_SZ    \"$registry\" \"%1\""))
                else -> versions[File(cmd[0]).parentFile.name]?.let { ProcessResult(0, listOf(it)) } ?: ProcessResult(1, emptyList())
            }
        }
        return BlenderLocator(runner, setting = { setting }, pathDirs = { path }, programDirs = { listOf(dir) }, isWindows = true) to runner
    }

    @Test
    fun Locator_PrefersTheSetting_ThenTheNewestSupported() {
        File(dir, "Blender Foundation").mkdirs()
        val old = File(dir, "Blender Foundation/Blender 3.6/blender.exe").apply { parentFile.mkdirs(); writeText("") }
        val lts = File(dir, "Blender Foundation/Blender 4.2/blender.exe").apply { parentFile.mkdirs(); writeText("") }
        val new = File(dir, "Blender Foundation/Blender 5.2/blender.exe").apply { parentFile.mkdirs(); writeText("") }
        val custom = exe("custom")
        val (loc, _) = locator(mapOf("Blender 3.6" to "Blender 3.6.0", "Blender 4.2" to "Blender 4.2.1 LTS", "Blender 5.2" to "Blender 5.2.3 LTS", "custom" to "Blender 4.2.0"), setting = custom.path)

        val found = loc.find()

        assertEquals(listOf(custom, new, lts), found.map { it.executable })
        assertEquals(BlenderSource.SETTING, found.first().source)
        assertFalse(found.any { it.executable == old }, "3.6 is below the 4.2 minimum")
    }

    @Test
    fun Locator_FindsTheFileAssociation() {
        val viaRegistry = exe("Assoc")
        val launcher = File(viaRegistry.parentFile, "blender-launcher.exe").path
        val (loc, _) = locator(mapOf("Assoc" to "Blender 5.0.1"), registry = launcher)

        val best = loc.best()

        assertEquals(viaRegistry, best?.executable)
        assertEquals(BlenderSource.REGISTRY, best?.source)
    }

    @Test
    fun Locator_CachesUntilInvalidated() {
        exe("OnPath")
        val (loc, runner) = locator(mapOf("OnPath" to "Blender 4.2.0"), path = listOf(File(dir, "OnPath")))

        loc.find(); loc.find()
        val probes = runner.commands.count { it.getOrNull(1) == "--version" }
        loc.invalidate(); loc.find()

        assertEquals(1, probes)
        assertEquals(2, runner.commands.count { it.getOrNull(1) == "--version" })
    }

    @Test
    fun Locator_NothingFound() {
        val (loc, _) = locator(emptyMap())
        assertNull(loc.best())
    }

    @Test
    fun JdkRunner_CapturesOutputAndExitCode() {
        val java = ProcessHandle.current().info().command().orElse("java")
        val result = JdkProcessRunner().run(listOf(java, "-version"))

        assertTrue(result.isSuccess, result.tail())
        assertTrue(result.log.any { "version" in it }, result.tail())
    }
}
