package com.pafoid.skate.engine.platform.steampipe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SteamCmdInstallerTest {
    @TempDir
    lateinit var dir: File

    private val managed get() = File(dir, "steamcmd")

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
        return out.toByteArray()
    }

    private class FakeRunner(private val exits: MutableList<Int>) : SteamCmdRunner {
        val commands = mutableListOf<List<String>>()
        override fun run(command: List<String>, workingDir: File, cancel: SteamCmdCancel, onLine: (String) -> Unit): SteamCmdRun {
            commands += command
            onLine("Loading Steam API...OK")
            return SteamCmdRun(exits.removeAt(0))
        }
    }

    private fun downloader(bytes: ByteArray, urls: MutableList<String> = mutableListOf()) = SteamCmdDownloader { url, target, progress ->
        urls += url
        target.writeBytes(bytes)
        progress(bytes.size.toLong(), bytes.size.toLong())
    }

    @Test
    fun SteamCmdInstaller_DownloadsFromValveExtractsAndSelfUpdates() {
        val urls = mutableListOf<String>()
        val runner = FakeRunner(mutableListOf(0))
        val steps = mutableListOf<SteamCmdInstallStep>()
        val result = SteamCmdInstaller(managed, downloader(zipBytes("steamcmd.exe" to "exe"), urls), runner)
            .install(onStep = { step, _ -> if (steps.lastOrNull() != step) steps += step })

        assertNull(result.failure)
        val exe = File(managed, "steamcmd.exe")
        assertEquals(SteamCmdInstall(exe, SteamCmdSource.MANAGED), result.install)
        assertEquals(listOf(SteamCmdInstaller.DOWNLOAD_URL), urls)
        assertTrue(SteamCmdInstaller.DOWNLOAD_URL.startsWith("https://"))
        assertEquals(listOf(listOf(exe.absolutePath, "+quit")), runner.commands)
        assertEquals(listOf(SteamCmdInstallStep.DOWNLOADING, SteamCmdInstallStep.EXTRACTING, SteamCmdInstallStep.UPDATING, SteamCmdInstallStep.DONE), steps)
        assertFalse(File(managed, "steamcmd.zip.part").exists())
    }

    @Test
    fun SteamCmdInstaller_RerunsOnceAfterTheFirstRunUpdate() {
        val runner = FakeRunner(mutableListOf(7, 0))
        val ok = SteamCmdInstaller(managed, downloader(zipBytes("steamcmd.exe" to "exe")), runner).install()
        assertNull(ok.failure)
        assertEquals(2, runner.commands.size)

        val failing = SteamCmdInstaller(managed, downloader(zipBytes("steamcmd.exe" to "exe")), FakeRunner(mutableListOf(7, 7))).install()
        assertEquals(SteamCmdInstallFailure.SELF_UPDATE_FAILED, failing.failure)
    }

    @Test
    fun SteamCmdInstaller_ReportsDownloadAndArchiveFailures() {
        val offline = SteamCmdInstaller(managed, { _, _, _ -> throw IOException("offline") }, FakeRunner(mutableListOf())).install()
        assertEquals(SteamCmdInstallFailure.DOWNLOAD_FAILED, offline.failure)
        assertEquals("offline", offline.detail)

        val garbage = SteamCmdInstaller(managed, downloader("not a zip".toByteArray()), FakeRunner(mutableListOf())).install()
        assertEquals(SteamCmdInstallFailure.ARCHIVE_INVALID, garbage.failure)

        val noExe = SteamCmdInstaller(managed, downloader(zipBytes("readme.txt" to "hi")), FakeRunner(mutableListOf())).install()
        assertEquals(SteamCmdInstallFailure.EXECUTABLE_MISSING, noExe.failure)
    }

    @Test
    fun SteamCmdInstaller_RefusesEntriesOutsideTheInstallFolder() {
        val evil = SteamCmdInstaller(managed, downloader(zipBytes("../evil.exe" to "x", "steamcmd.exe" to "exe")), FakeRunner(mutableListOf())).install()
        assertEquals(SteamCmdInstallFailure.ARCHIVE_INVALID, evil.failure)
        assertFalse(File(dir, "evil.exe").exists())
    }

    @Test
    fun SteamCmdInstaller_CancelStopsTheDownload() {
        val cancel = SteamCmdCancel().apply { cancel() }
        val result = SteamCmdInstaller(managed, downloader(zipBytes("steamcmd.exe" to "exe")), FakeRunner(mutableListOf())).install(cancel)
        assertEquals(SteamCmdInstallFailure.CANCELLED, result.failure)
    }

    @Test
    fun SteamCmdInstaller_UninstallRemovesTheManagedFolder() {
        File(managed, "config").mkdirs()
        File(managed, "config/config.vdf").writeText("cached")
        assertTrue(SteamCmdInstaller(managed, downloader(ByteArray(0)), FakeRunner(mutableListOf())).uninstall())
        assertFalse(managed.exists())
    }
}
