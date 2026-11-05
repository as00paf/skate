package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-11: the export's game jar comes from the editor's own location or a configured path, never the working directory. */
class GameJarLocatorTest {
    @TempDir
    lateinit var dir: File

    private fun jar(path: String, ageMs: Long = 0) = File(dir, path).also {
        it.parentFile.mkdirs(); it.writeText("jar")
        if (ageMs > 0) it.setLastModified(System.currentTimeMillis() - ageMs)
    }

    private fun locator(codeSource: File?, configured: String? = null, property: String? = null, built: Long? = null) =
        GameJarLocator(codeSource, { configured }, { property }, { built })

    @Test
    fun Locate_DevRunUsesTheRepoBuildLibsFromTheClassesFolder() {
        val classes = File(dir, "repo/build/classes/kotlin/main").also { it.mkdirs() }
        val expected = jar("repo/build/libs/skate-game.jar")
        val search = locator(classes).locate()
        assertEquals(expected.absoluteFile, search.jar?.absoluteFile)
        assertEquals(File(dir, "repo/build/libs").absoluteFile.normalize(), search.searched.first())
    }

    @Test
    fun Locate_InstalledEditorUsesTheFolderOfItsJar() {
        val editorJar = jar("install/skate.jar")
        val expected = jar("install/skate-game.jar")
        assertEquals(expected.absoluteFile, locator(editorJar).locate().jar?.absoluteFile)
    }

    @Test
    fun Locate_OrderIsEditorLocationThenSettingsThenSystemProperty() {
        val editorJar = jar("install/skate.jar")
        val beside = jar("install/skate-game.jar")
        val configured = jar("configured/my-game.jar")
        val property = jar("property/other-game.jar")

        val all = locator(editorJar, configured.path, File(dir, "property").path)
        assertEquals(beside.absoluteFile, all.locate().jar?.absoluteFile)
        assertEquals(
            listOf(File(dir, "install"), configured, File(dir, "property")).map { it.absoluteFile.normalize() },
            all.locate().searched,
        )

        beside.delete()
        assertEquals(configured.absoluteFile, all.locate().jar?.absoluteFile, "settings next")
        configured.delete()
        assertEquals(property.absoluteFile, all.locate().jar?.absoluteFile, "then -Dskate.gameJar")
        property.delete()
        assertNull(all.locate().jar)
    }

    @Test
    fun Locate_IgnoresTheWorkingDirectory() {
        // The old lookup scanned <working dir>/build/libs: a stray jar there must not be picked any more.
        val cwdLibs = File(System.getProperty("user.dir"), "build/libs").also { it.mkdirs() }
        val decoy = File(cwdLibs, "ast11-decoy-${System.nanoTime()}-game.jar")
        try {
            decoy.writeText("stale")
            val search = locator(jar("install/skate.jar")).locate()
            assertNull(search.jar)
            assertTrue(search.searched.none { it == cwdLibs.absoluteFile.normalize() }, search.searched.toString())
        } finally {
            decoy.delete()
        }
    }

    @Test
    fun Locate_InAFolderPrefersSkateGameJarElseTheNewestGameJar() {
        jar("libs/old-game.jar", ageMs = 60_000)
        val newest = jar("libs/new-game.jar")
        jar("libs/skate.jar")
        assertEquals(newest.absoluteFile, GameJarLocator.jarIn(File(dir, "libs"))?.absoluteFile)
        val preferred = jar("libs/skate-game.jar", ageMs = 120_000)
        assertEquals(preferred.absoluteFile, GameJarLocator.jarIn(File(dir, "libs"))?.absoluteFile)
        assertNull(GameJarLocator.jarIn(File(dir, "libs/readme.txt").also { it.writeText("x") }))
    }

    @Test
    fun Locate_FlagsAJarOlderThanTheEditorBuild() {
        val editorJar = jar("install/skate.jar")
        jar("install/skate-game.jar", ageMs = 3_600_000)
        assertTrue(locator(editorJar, built = System.currentTimeMillis()).locate().stale)
        assertFalse(locator(editorJar, built = System.currentTimeMillis() - 7_200_000).locate().stale)
        assertFalse(locator(editorJar, built = null).locate().stale, "unknown build time never warns")
    }

    @Test
    fun BesideEditor_ClassesFolderOutsideABuildFolderUsesItsLibs() {
        val classes = File(dir, "out/classes").also { it.mkdirs() }
        assertEquals(File(classes, "libs").absoluteFile, GameJarLocator.besideEditor(classes).absoluteFile)
    }

    @Test
    fun Export_MissingJarNamesEverySearchedPlace() {
        val scene = File(dir, "Game/Scenes/Main.scene").also { it.parentFile.mkdirs(); it.writeText("{}") }
        val projectFile = File(dir, "Game/Game.skateproject").also { it.writeText("{}") }
        val project = Project(name = "Game", projectPath = projectFile.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath))
        val search = locator(jar("install/skate.jar"), configured = File(dir, "nowhere").path)::locate

        val result = ProjectExporter(mockk<LoggerService>(relaxed = true), File(dir, "no-strings"), search).export(project)

        assertEquals(ExportFailure.GAME_JAR_MISSING, result.failure)
        assertEquals(ExportDetail.GAME_JAR_NOT_BUILT, result.detail)
        val named = result.detailArgs.single()
        assertTrue(File(dir, "install").absolutePath in named && File(dir, "nowhere").absolutePath in named, named)
    }
}
