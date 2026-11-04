package com.pafoid.skate.engine.assets.pack

import com.pafoid.skate.engine.assets.AssetRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PackBuilderTest {
    @TempDir
    lateinit var dir: File

    private fun input(name: String, bytes: ByteArray) = PackInput("/abs/$name", name, "TEXT", bytes)

    @Test
    fun PackBuilder_CompressesCompressibleDataAndExtractsOriginalBytes() {
        val big = "hello world ".repeat(500).toByteArray()
        val png = ByteArray(300) { (it * 7).toByte() }
        val result = PackBuilder.build("p", listOf(input("a.txt", big), input("b.png", png)))
        val (a, b) = result.manifest.entries
        assertTrue(a.compressed && a.storedSize < a.rawSize)
        assertFalse(b.compressed)
        assertArrayEquals(big, PackBuilder.extract(result.data, a.position, a.storedSize, a.compressed))
        assertArrayEquals(png, PackBuilder.extract(result.data, b.position, b.storedSize, b.compressed))
        assertTrue(PackBuilder.verify(result.data, result.manifest).isEmpty())
    }

    @Test
    fun PackBuilder_VerifyDetectsCorruption() {
        val result = PackBuilder.build("p", listOf(input("a.txt", "abc".toByteArray())), compress = false)
        val bad = result.data.copyOf().also { it[0] = 'x'.code.toByte() }
        val problems = PackBuilder.verify(bad, result.manifest)
        assertTrue(problems.any { it.contains("checksum") })
        assertTrue(problems.any { it.contains("a.txt") })
    }

    @Test
    fun PackBuilder_ManifestHasStableChecksums() {
        val a = PackBuilder.build("p", listOf(input("a.txt", "abc".toByteArray())), compress = false)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", a.manifest.entries.single().sha256)
    }

    @Test
    fun ReferenceCollector_FollowsPathsGuidsAndModelCompanionsAndSkipsUnreferenced() {
        val tex = File(dir, "tex/used.png").also { it.parentFile.mkdirs(); it.writeText("png") }
        File(dir, "tex/unused.png").writeText("png")
        val prefab = File(dir, "prefabs/p.prefab").also { it.parentFile.mkdirs() }
        val model = File(dir, "models/m.gltf").also { it.parentFile.mkdirs(); it.writeText("{}") }
        File(dir, "models/m.bin").writeText("b")
        val registry = AssetRegistry(dir).also { it.scan() }
        val guid = registry.toReference("tex/used.png")!!
        prefab.writeText("""{"texture":"$guid","model":"models/m.gltf"}""")
        val scene = File(dir, "a.scene").also {
            it.writeText("""{"prefab":${"\""}${prefab.absolutePath.replace("\\", "\\\\")}${"\""}}""")
        }

        val found = ReferenceCollector(dir, registry).collect(listOf(scene)).map { it.name }.toSet()
        assertEquals(setOf("a.scene", "p.prefab", "used.png", "m.gltf", "m.bin"), found)
    }
}
