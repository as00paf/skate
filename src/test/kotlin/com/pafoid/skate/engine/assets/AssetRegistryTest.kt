package com.pafoid.skate.engine.assets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AssetRegistryTest {
    @TempDir
    lateinit var dir: File

    private fun file(path: String, text: String = "x") = File(dir, path).also { it.parentFile.mkdirs(); it.writeText(text) }

    @Test
    fun AssetMeta_RoundTrips() {
        val meta = AssetMeta(guid = AssetId.generate(), importer = "texture", settings = mapOf("srgb" to "true"))
        assertEquals(meta, AssetMeta.decode(AssetMeta.encode(meta)))
    }

    @Test
    fun AssetId_RoundTripsAsJsonString() {
        val id = AssetId.generate()
        val json = Json.encodeToString(id)
        assertEquals("\"${id.value}\"", json)
        assertEquals(id, Json.decodeFromString<AssetId>(json))
        assertNull(AssetId.parseOrNull("not-a-guid"))
    }

    @Test
    fun AssetRegistry_ScanCreatesSidecarsAndIsStable() {
        val f = file("textures/a.png")
        val reg = AssetRegistry(dir)
        reg.scan()
        val id = reg.idOf("textures/a.png")!!
        assertTrue(AssetMeta.sidecarOf(f).exists())
        assertEquals("texture", AssetMeta.read(f)!!.importer)
        val reg2 = AssetRegistry(dir).also { it.scan() }
        assertEquals(id, reg2.idOf("textures/a.png"))
        assertEquals(1, reg2.size)
    }

    @Test
    fun AssetRegistry_MovePreservesGuid() {
        val f = file("a.png")
        val reg = AssetRegistry(dir).also { it.scan() }
        val id = reg.idOf("a.png")!!
        assertTrue(reg.move(f, File(dir, "sub/b.png")))
        assertEquals("sub/b.png", reg.pathOf(id))
        assertNull(reg.idOf("a.png"))
        assertFalse(AssetMeta.sidecarOf(f).exists())
        assertEquals(id, AssetRegistry(dir).also { it.scan() }.idOf("sub/b.png"))
    }

    @Test
    fun AssetRegistry_ExternalRenameWithSidecarKeepsGuid() {
        val f = file("a.png")
        val reg = AssetRegistry(dir).also { it.scan() }
        val id = reg.idOf("a.png")!!
        f.renameTo(File(dir, "c.png"))
        AssetMeta.sidecarOf(f).renameTo(File(dir, "c.png.meta"))
        reg.scan()
        assertEquals("c.png", reg.pathOf(id))
    }

    @Test
    fun AssetRegistry_DirectoryMoveKeepsGuids() {
        file("d/a.png")
        val reg = AssetRegistry(dir).also { it.scan() }
        val id = reg.idOf("d/a.png")!!
        assertTrue(reg.move(File(dir, "d"), File(dir, "e")))
        assertEquals("e/a.png", reg.pathOf(id))
    }

    @Test
    fun AssetRegistry_CopiedSidecarGetsFreshGuid() {
        val a = file("a.png")
        val reg = AssetRegistry(dir).also { it.scan() }
        val b = File(dir, "b.png").also { a.copyTo(it) }
        AssetMeta.sidecarOf(a).copyTo(AssetMeta.sidecarOf(b))
        reg.scan()
        assertEquals(2, reg.size)
        assertNotEquals(reg.idOf("a.png"), reg.idOf("b.png"))
    }

    @Test
    fun AssetRegistry_ResolveAcceptsGuidOrPath() {
        file("a.png")
        val reg = AssetRegistry(dir).also { it.scan() }
        val id = reg.idOf("a.png")!!
        assertEquals("a.png", reg.resolve(id.value))
        assertEquals("a.png", reg.resolve("a.png"))
        assertEquals(id.value, reg.toReference("a.png"))
        assertNull(reg.resolve("missing.png"))
    }

    @Test
    fun AssetRegistry_DeleteRemovesSidecar() {
        val f = file("a.png")
        val reg = AssetRegistry(dir).also { it.scan() }
        assertTrue(reg.delete(f))
        assertFalse(AssetMeta.sidecarOf(f).exists())
        assertEquals(0, reg.size)
    }
}
