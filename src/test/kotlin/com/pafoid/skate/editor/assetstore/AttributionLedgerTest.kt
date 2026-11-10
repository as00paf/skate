package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.engine.assets.AssetRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AttributionLedgerTest {
    @TempDir
    lateinit var tmp: File

    private fun entry(id: String, ids: List<String>) = LedgerEntry(
        assetIds = ids, provider = "polyhaven", sourceId = id, title = id, sourceUrl = "https://polyhaven.com/a/$id",
        authors = listOf(LedgerAuthor("A")), license = LedgerLicense("CC0-1.0"), retrievedAt = "2026-11-06T00:00:00Z",
    )

    @Test
    fun Ledger_RoundTripsThroughProjectFile() {
        val ledger = AttributionLedger().upsert(entry("a", listOf("1")))
        AttributionLedger.save(tmp, ledger)
        assertEquals(File(tmp, ".skate/attribution.json"), AttributionLedger.file(tmp))
        assertEquals(ledger, AttributionLedger.load(tmp))
    }

    @Test
    fun Ledger_MissingFile_IsEmpty() {
        assertTrue(AttributionLedger.load(tmp).entries.isEmpty())
    }

    @Test
    fun Ledger_Decode_IgnoresUnknownKeysAndRejectsNewerSchema() {
        val text = """{"schema":1,"future":true,"entries":[{"assetIds":["1"],"provider":"p","sourceId":"s","title":"t",
            "sourceUrl":"u","license":{"spdx":"CC0-1.0","extra":1},"retrievedAt":"r","unknown":"x"}]}"""
        assertEquals("s", AttributionLedger.decode(text).entries.single().sourceId)
        assertThrows<IllegalArgumentException> { AttributionLedger.decode("""{"schema":99,"entries":[]}""") }
    }

    @Test
    fun Ledger_UpsertRemoveAndAssetQueries() {
        var ledger = AttributionLedger().upsert(entry("a", listOf("1", "2"))).upsert(entry("b", listOf("3")))
        ledger = ledger.upsert(entry("a", listOf("4")))
        assertEquals(listOf("b", "a"), ledger.entries.map { it.sourceId })
        assertEquals(listOf("a"), ledger.entriesFor(setOf("4", "9")).map { it.sourceId })
        assertEquals(listOf("a"), ledger.removeAssetIds(setOf("3")).entries.map { it.sourceId })
        assertNull(ledger.remove("polyhaven", "a").find("polyhaven", "a"))
    }

    @Test
    fun Ledger_IsNotSweptIntoAssetRegistryScan() {
        AttributionLedger.save(tmp, AttributionLedger().upsert(entry("a", listOf("1"))))
        File(tmp, ".skate/cache/store/polyhaven/a/gltf-1k/a.gltf").apply { parentFile.mkdirs(); writeText("{}") }
        File(tmp, "Assets/x.png").apply { parentFile.mkdirs(); writeText("png") }
        val registry = AssetRegistry(tmp).apply { scan() }
        assertEquals(1, registry.size)
        assertNull(registry.idOf(".skate/attribution.json"))
    }
}
