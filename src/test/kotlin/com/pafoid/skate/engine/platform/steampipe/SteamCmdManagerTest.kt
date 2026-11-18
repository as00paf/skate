package com.pafoid.skate.engine.platform.steampipe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SteamCmdManagerTest {
    @TempDir
    lateinit var dir: File

    private val target = SteamPublishTarget(appId = 1234560, depotId = 1234561, descriptionTemplate = "{project} {version} ({date})")
    private val values = BuildDescriptionValues("Skate", "1.0", "2026-11-18")
    private val content get() = File(dir, "content").apply { mkdirs(); File(this, "game.exe").writeText("x") }
    private val staging get() = File(dir, "staging")
    private val install get() = SteamCmdInstall(File(dir, "steamcmd/steamcmd.exe").apply { parentFile.mkdirs(); writeText("x") }, SteamCmdSource.MANAGED)

    private class RecordingRunner(private val output: List<String> = emptyList(), private val exit: Int = 0) : SteamCmdRunner {
        val commands = mutableListOf<List<String>>()
        val dirs = mutableListOf<File>()
        override fun run(command: List<String>, workingDir: File, cancel: SteamCmdCancel, onLine: (String) -> Unit): SteamCmdRun {
            commands += command
            dirs += workingDir
            output.forEach(onLine)
            return SteamCmdRun(exit)
        }
    }

    @Test
    fun SteamCmdManager_PreviewStagesADryRunScript() {
        val staged = SteamCmdManager(RecordingRunner()).stage(target, content, staging, values, preview = true)
        val script = staged.script!!
        assertEquals(File(staging, "scripts/app_build_1234560.vdf").absolutePath, script.absolutePath)
        val text = script.readText()
        assertTrue(text.contains("\"Preview\" \"1\""), text)
        assertTrue(text.contains("\"Desc\" \"Skate 1.0 (2026-11-18)\""), text)
        assertTrue(text.contains("\"ContentRoot\" \"${content.absolutePath}"), text)
        assertTrue(text.contains("\"BuildOutput\" \"${File(staging, "output").absolutePath}"), text)
        assertTrue(text.contains("\"FileExclusion\" \"steam_appid.txt\""), text)
        assertFalse(text.contains("SetLive"))
        assertTrue(File(staging, "output").isDirectory)
    }

    @Test
    fun SteamCmdManager_RealUploadScriptIsNotPreview() {
        val text = SteamCmdManager(RecordingRunner()).stage(target, content, staging, values, preview = false).script!!.readText()
        assertTrue(text.contains("\"Preview\" \"0\""))
    }

    @Test
    fun SteamCmdManager_StageRefusesInvalidTargetsAndContent() {
        val manager = SteamCmdManager(RecordingRunner())
        val bad = manager.stage(SteamPublishTarget(), content, staging, values, preview = true)
        assertNull(bad.script)
        assertEquals(listOf(SteamUploadIssue.TARGET_INVALID), bad.issues)
        assertEquals(listOf(SteamPublishIssue.APP_ID_INVALID, SteamPublishIssue.DEPOT_ID_INVALID), bad.targetIssues)

        assertEquals(listOf(SteamUploadIssue.CONTENT_MISSING), manager.stage(target, File(dir, "none"), staging, values, true).issues)
        val empty = File(dir, "empty").apply { mkdirs() }
        assertEquals(listOf(SteamUploadIssue.CONTENT_EMPTY), manager.stage(target, empty, staging, values, true).issues)
        assertFalse(File(staging, "scripts").exists())
    }

    @Test
    fun SteamCmdManager_UploadRunsCachedLoginWithoutPassword() {
        val runner = RecordingRunner()
        val exe = install
        val run = SteamCmdManager(runner).upload(exe, "builder_bot", target, content, staging, values, preview = true)
        val script = run.staged.script!!
        assertEquals(listOf(listOf(exe.executable.absolutePath, "+login", "builder_bot", "+run_app_build", script.absolutePath, "+quit")), runner.commands)
        assertEquals(exe.executable.absoluteFile.parentFile, runner.dirs.single())
        assertEquals(SteamCmdRun(0), run.run)
    }

    @Test
    fun SteamCmdManager_UploadOutputIsRedacted() {
        val lines = mutableListOf<String>()
        val runner = RecordingRunner(listOf("Logging in user 'builder_bot' to Steam Public...OK"))
        SteamCmdManager(runner).upload(install, "builder_bot", target, content, staging, values, preview = true, onLine = { lines += it })
        assertEquals(listOf("Logging in user 'bu***' to Steam Public...OK"), lines)
    }

    @Test
    fun SteamCmdManager_RefusesUsernamesThatCouldInjectArguments() {
        val runner = RecordingRunner()
        listOf("", "ab", "bot +quit", "bot\"x", "+login", "évite").forEach { name ->
            val run = SteamCmdManager(runner).upload(install, name, target, content, staging, values, preview = true)
            assertEquals(listOf(SteamUploadIssue.USERNAME_INVALID), run.staged.issues, name)
            assertNull(run.run)
        }
        assertTrue(runner.commands.isEmpty())
        assertThrows<IllegalArgumentException> { SteamCmdManager.uploadCommand(File("s.exe"), "a b", File("x.vdf")) }
    }
}
