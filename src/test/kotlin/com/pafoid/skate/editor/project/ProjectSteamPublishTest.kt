package com.pafoid.skate.editor.project

import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.platform.steampipe.SteamPublishTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** NET-07: the Steam target is saved with the project; credentials never are. */
class ProjectSteamPublishTest {
    @TempDir
    lateinit var dir: File

    private val serializer = Serializer()

    private fun project(file: File) = Project(name = "P", projectPath = file.absolutePath, defaultScene = File(dir, "Main.scene").absolutePath, scenesPath = emptyList())

    @Test
    fun ProjectSteamPublish_RoundTripsThroughTheProjectFile() {
        val file = File(dir, "p.skateproject")
        val target = SteamPublishTarget(appId = 1234560, depotId = 1234561, descriptionTemplate = "{project} {date}")
        file.writeText(serializer.encodeFile(FileKind.PROJECT, project(file).apply { steamPublish = target }))
        assertEquals(target, ProjectFileReader.read(serializer, file).steamPublish)
    }

    @Test
    fun ProjectSteamPublish_OldProjectsHaveNoTarget() {
        val file = File(dir, "old.skateproject")
        file.writeText(serializer.encodeFile(FileKind.PROJECT, project(file)).replace(Regex(",\\s*\"steamPublish\": null"), ""))
        assertNull(ProjectFileReader.read(serializer, file).steamPublish)
    }

    @Test
    fun ProjectSteamPublish_EditorSettingsKeepOnlyPathAndUsername() {
        val settings = EditorSettings(steamCmdPath = "C:\\steamcmd", steamUsername = "builder_bot")
        val decoded = serializer.json.decodeFromString(EditorSettings.serializer(), serializer.json.encodeToString(EditorSettings.serializer(), settings))
        assertEquals("C:\\steamcmd", decoded.steamCmdPath)
        assertEquals("builder_bot", decoded.steamUsername)
        assertEquals(emptyList<String>(), EditorSettings::class.java.declaredFields.map { it.name }.filter { it.contains("password", ignoreCase = true) })
    }
}
