package com.pafoid.skate.build

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.utils.ProjectExporter
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarFile

/**
 * TLG-10: the standalone game jar is built with the editor, so exports never ship stale game code.
 * The first two tests guard the wiring; the last one is the end-to-end check run by tools/scripts/verify-game-jar.sh.
 */
class GameJarBuildTest {

    private val root = File(System.getProperty("user.dir"))

    @Test
    fun `gradle run and assemble build the game jar`() {
        val build = File(root, "build.gradle.kts").readText()
        assertTrue(Regex("""tasks\.named\("run"\)\s*\{\s*dependsOn\(gameJar\)""").containsMatchIn(build), "run must depend on gameJar")
        assertTrue(Regex("""tasks\.named\("assemble"\)\s*\{\s*dependsOn\(gameJar\)""").containsMatchIn(build), "assemble must depend on gameJar")
        assertTrue(build.contains("""archiveFile.set(layout.buildDirectory.file("libs/${ProjectExporter.GAME_JAR}"))"""),
            "gameJar must write the jar the exporter looks for")
    }

    @Test
    fun `the shared IntelliJ run configuration builds the game jar before launching the editor`() {
        val config = File(root, ".idea/runConfigurations/PAFSK8.xml").readText()
        assertTrue(config.contains("com.pafoid.skate.MainKt"))
        assertTrue(Regex("""<option name="Gradle\.BeforeRunTask" enabled="true" tasks="gameJar"""").containsMatchIn(config), config)
    }

    /**
     * Opt-in (`-Pskate.gameJarToken=<token>`): after game code containing [token] was built through the editor's
     * build path, a real export copies a jar that contains it.
     */
    @Test
    fun `an export ships the freshly built game code`(@TempDir dir: File) {
        val token = System.getProperty("skate.gameJarToken")
        assumeTrue(!token.isNullOrBlank(), "set -Pskate.gameJarToken to run the end-to-end check")

        val scene = File(dir, "Scenes/Main.scene").also { it.parentFile.mkdirs(); it.writeText("{}") }
        val file = File(dir, "Game.skateproject").also { it.writeText("{}") }
        val project = Project(name = "Game", projectPath = file.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath))
        val result = ProjectExporter(mockk<LoggerService>(relaxed = true)).export(project)
        assertTrue(result.success, result.toString())

        JarFile(File(dir, "builds/${ProjectExporter.GAME_JAR}")).use { jar ->
            val probe = jar.getEntry("com/pafoid/skate/game/GameJarProbe.class")
            assertTrue(probe != null, "exported jar has no probe class: stale game jar")
            val bytes = jar.getInputStream(probe).readBytes()
            assertTrue(String(bytes, Charsets.ISO_8859_1).contains(token), "exported jar has an old probe, not $token")
            assertEquals("com.pafoid.skate.game.GameMainKt", jar.manifest.mainAttributes.getValue("Main-Class"))
        }
    }
}
