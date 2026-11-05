package com.pafoid.skate.engine.assets.pack

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PackedStringsTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun Inputs_PackEveryTableAndTranslationUnderPackDir() {
        File(dir, "hud.properties").writeText("a=A")
        File(dir, "hud_fr.properties").writeText("a=AA")
        File(dir, "notes.txt").writeText("ignored")
        val inputs = PackedStrings.inputs(dir)
        assertEquals(listOf("strings/game/hud.properties", "strings/game/hud_fr.properties"), inputs.map { it.relativePath })
        assertTrue(inputs.all { it.type == "TEXT" })
    }

    @Test
    fun Inputs_EmptyWhenDirectoryIsMissing() {
        assertTrue(PackedStrings.inputs(File(dir, "missing")).isEmpty())
    }

    @Test
    fun IsTable_OnlyMatchesPropertiesDirectlyUnderPackDir() {
        assertTrue(PackedStrings.isTable("strings/game/hud.properties"))
        assertFalse(PackedStrings.isTable("strings/game/sub/hud.properties"))
        assertFalse(PackedStrings.isTable("strings/game/readme.txt"))
        assertFalse(PackedStrings.isTable("C:/project/Scenes/Main.scene"))
    }

    @Test
    fun Source_ReadsBaseTablesAndTranslationsAsUtf8() {
        val source = PackedStrings.source(
            mapOf(
                "strings/game/hud.properties" to "pause.title=Paused".toByteArray(),
                "strings/game/hud_fr.properties" to "pause.title=Pausé".toByteArray(Charsets.UTF_8),
                "Scenes/Main.scene" to "{}".toByteArray(),
            )
        )
        assertEquals(listOf("hud.properties"), source.baseTables())
        assertEquals("Paused", source.read("hud.properties")?.getProperty("pause.title"))
        assertEquals("Pausé", source.read("hud_fr.properties")?.getProperty("pause.title"))
        assertNull(source.read("hud_de.properties"))
    }
}
