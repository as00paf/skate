package com.pafoid.skate.engine.assets.loaders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GltfReferencesTest {

    @Test
    fun uris_ListsBuffersAndImagesDecodedSkippingDataUris() {
        val gltf = """{"buffers":[{"uri":"table.bin"},{"uri":"data:application/octet-stream;base64,AAAA"}],
            "images":[{"uri":"textures/table%20diff_1k.jpg"},{"bufferView":3},{"uri":"textures/table_nor_gl_1k.jpg"}]}"""

        assertEquals(listOf("table.bin", "textures/table diff_1k.jpg", "textures/table_nor_gl_1k.jpg"), GltfReferences.uris(gltf))
    }

    @Test
    fun uris_BadJsonGivesNothing() {
        assertTrue(GltfReferences.uris("not json").isEmpty())
        assertTrue(GltfReferences.uris("""{"images": 3}""").isEmpty())
    }

    @Test
    fun resolve_JoinsToTheModelFolder() {
        assertEquals("Assets/Store/polyhaven/table/textures/a.jpg", GltfReferences.resolve("Assets/Store/polyhaven/table/table.gltf", "textures/a.jpg"))
        assertEquals("C:/proj/Assets/x/textures/a b.jpg", GltfReferences.resolve("C:\\proj\\Assets\\x\\m.gltf", "textures/a%20b.jpg"))
        assertEquals("/home/p/Assets/shared/a.jpg", GltfReferences.resolve("/home/p/Assets/x/m.gltf", "../shared/./a.jpg"))
        assertEquals("a.jpg", GltfReferences.resolve("m.gltf", "a.jpg"))
    }

    @Test
    fun resolve_RejectsAbsoluteSchemesAndEscapes() {
        assertNull(GltfReferences.resolve("Assets/x/m.gltf", "/etc/a.jpg"))
        assertNull(GltfReferences.resolve("Assets/x/m.gltf", "https://example.com/a.jpg"))
        assertNull(GltfReferences.resolve("Assets/x/m.gltf", "C:/a.jpg"))
        assertNull(GltfReferences.resolve("Assets/m.gltf", "../../a.jpg"))
    }

    @Test
    fun texturePath_PrefersTheFileNextToTheModel() {
        val existing = setOf("C:/p/Assets/x/textures/a.jpg".replace('/', java.io.File.separatorChar))
        assertEquals(existing.single(), GltfReferences.texturePath("C:\\p\\Assets\\x\\m.gltf", "textures/a.jpg") { it in existing })
    }

    @Test
    fun texturePath_FallsBackToTheUriAsWritten() {
        assertEquals("assets/Textures/a.png", GltfReferences.texturePath("C:\\p\\m.obj", "assets/Textures/a.png") { false })
    }

    @Test
    fun decode_KeepsPlusAndMalformedEscapes() {
        assertEquals("a+b c%zz%2", GltfReferences.decode("a+b%20c%zz%2"))
        assertEquals("é", GltfReferences.decode("%C3%A9"))
    }
}
