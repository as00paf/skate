package com.pafoid.skate.templates

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateRegistryTest {

    private fun strings(file: String) = Properties().apply {
        File("src/main/resources/editor/strings/$file").reader(Charsets.UTF_8).use { load(it) }
    }

    @Test
    fun TemplateRegistry_ListsTheFiveTemplatesInDisplayOrder() {
        assertEquals(listOf("empty", "skate-game", "feature-showcase", "tetris", "marble-run"), TemplateRegistry.ids())
        assertEquals(TemplateRegistry.ids().toSet().size, TemplateRegistry.ids().size, "ids are unique")
    }

    @Test
    fun TemplateRegistry_DefaultIsTheSkateGame() {
        assertEquals("skate-game", TemplateRegistry.DEFAULT_ID)
        assertEquals("skate-game", NewProjectSettings().template)
    }

    @Test
    fun TemplateRegistry_GetFindsKnownIdsOnly() {
        assertEquals(TetrisTemplate, TemplateRegistry.get("tetris"))
        assertNull(TemplateRegistry.get("nope"))
    }

    @Test
    fun TemplateRegistry_SamplesAreSampleTemplates() {
        assertEquals(listOf("feature-showcase", "marble-run", "tetris"), TemplateRegistry.sampleNames.keys.toList())
        TemplateRegistry.sampleNames.keys.forEach { assertEquals(TemplateCategory.SAMPLE, TemplateRegistry.require(it).category) }
    }

    @Test
    fun TemplateRegistry_EveryTemplateHasItsStringsInEnglishAndFrench() {
        val en = strings("tooling.properties")
        val fr = strings("tooling_fr.properties")
        TemplateRegistry.all().forEach { t ->
            listOf(t.nameKey, t.descriptionKey).forEach { key ->
                assertNotNull(en.getProperty(key), "en $key")
                assertNotNull(fr.getProperty(key), "fr $key")
            }
            assertTrue(t.icon.isNotEmpty(), t.id)
            assertTrue(t.sceneNames.isNotEmpty(), t.id)
        }
    }

    @Test
    fun TemplateRegistry_StructurePreviewListsTheTemplatesScenesAndFiles() {
        val names = TetrisTemplate.structure("Blocks").map { it.name.trim() }
        assertTrue("Game.scene" in names && "GameOver.scene" in names, names.toString())
        assertTrue("Scripts/tetris.kts" in names, names.toString())
        assertEquals("Blocks.skateproject", names.last())
        assertTrue(SkateGameTemplate.structure("X").none { it.name.contains("FeatureShowcase") })
    }
}
