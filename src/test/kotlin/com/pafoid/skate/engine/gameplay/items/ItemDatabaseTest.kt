package com.pafoid.skate.engine.gameplay.items

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** GPL-24a §3.4: duplicate ids, unknown ids and lenient decoding of `*.item.json`. */
class ItemDatabaseTest {

    @Test
    fun ItemDatabase_PartialFileDecodesWithDefaultsAndIgnoresUnknownKeys() {
        val db = ItemDatabase()
        val def = db.load("cap.item.json", """{ "id": "cap.red", "equip": { "slot": "HEAD", "offset": { "y": 0.1 } }, "future": 1 }""")!!
        assertEquals(1, def.maxStack)
        assertEquals("HEAD", def.equip!!.slot)
        assertEquals(Vector3f(0f, 0.1f, 0f), def.equip!!.offset)
        assertEquals(Vector3f(1f, 1f, 1f), def.equip!!.scale)
        assertEquals(def, db["cap.red"])
    }

    @Test
    fun ItemDatabase_UnknownIdIsNullAndInvalidFilesAreReported() {
        val db = ItemDatabase()
        assertNull(db["nope"])
        assertNull(db.load("bad.item.json", "{ not json"))
        assertNull(db.load("noid.item.json", """{ "maxStack": 3 }"""), "id is required")
        assertEquals(2, db.warnings.size)
    }

    @Test
    fun ItemDatabase_DuplicateIdWarnsAndTheLastDefinitionWins() {
        val db = ItemDatabase()
        db.register(ItemDef("wax", maxStack = 5))
        db.register(ItemDef("wax", maxStack = 9))
        assertEquals(9, db["wax"]!!.maxStack)
        assertTrue(db.warnings.single().contains("wax"))
    }

    @Test
    fun ItemDatabase_LoadsEveryItemFileInAFolderAndSkipsOthers(@TempDir dir: File) {
        File(dir, "Hats").mkdirs()
        File(dir, "Hats/cap.item.json").writeText("""{ "id": "cap" }""")
        File(dir, "glasses.item.json").writeText("""{ "id": "glasses" }""")
        File(dir, "notes.json").writeText("""{ "id": "ignored" }""")
        val db = ItemDatabase()
        assertEquals(2, db.loadFolder(dir))
        assertEquals(setOf("cap", "glasses"), db.all.map { it.id }.toSet())
        assertEquals(0, ItemDatabase().loadFolder(File(dir, "missing")))
    }

    @Test
    fun ItemDatabase_LoadAllReportsMissingPaths() {
        val db = ItemDatabase()
        val loaded = db.loadAll(listOf("a.item.json", "b.item.json")) { if (it == "a.item.json") """{ "id": "a" }""" else null }
        assertEquals(1, loaded)
        assertTrue(db.warnings.single().contains("b.item.json"))
    }
}
