package com.pafoid.skate.engine.platform.steampipe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SteamCmdLocatorTest {
    @TempDir
    lateinit var dir: File

    private fun exeIn(name: String): File = File(dir, name).apply { mkdirs() }.let { File(it, SteamCmdLocator.EXECUTABLE).apply { writeText("x") } }

    @Test
    fun SteamCmdLocator_SettingWinsAndAcceptsAFolder() {
        val sdk = exeIn("sdk")
        exeIn("managed")
        val locator = SteamCmdLocator(setting = { sdk.parentFile.path }, managedDir = File(dir, "managed"), pathDirs = { emptyList() })
        assertEquals(SteamCmdInstall(sdk, SteamCmdSource.SETTING), locator.locate())
    }

    @Test
    fun SteamCmdLocator_FallsBackToManagedThenPath() {
        val onPath = exeIn("bin")
        val noManaged = SteamCmdLocator(setting = { File(dir, "missing.exe").path }, managedDir = File(dir, "managed"), pathDirs = { listOf(File(dir, "nope"), onPath.parentFile) })
        assertEquals(SteamCmdInstall(onPath, SteamCmdSource.PATH), noManaged.locate())

        val managed = exeIn("managed")
        assertEquals(SteamCmdInstall(managed, SteamCmdSource.MANAGED), noManaged.locate())
    }

    @Test
    fun SteamCmdLocator_NullWhenNothingIsInstalled() {
        assertNull(SteamCmdLocator(setting = { " " }, managedDir = File(dir, "managed"), pathDirs = { listOf(dir) }).locate())
    }

    @Test
    fun SteamCmdLocator_ManagedDirUsesLocalAppData() {
        assertEquals(File("C:\\Users\\me\\AppData\\Local", "Skate${File.separator}steamcmd"),
            SteamCmdLocator.defaultManagedDir { if (it == "LOCALAPPDATA") "C:\\Users\\me\\AppData\\Local" else null })
    }
}
