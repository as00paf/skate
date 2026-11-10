package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-22: a glTF whose textures sit in a `textures/` subfolder loads them from next to the model, not the cwd. */
class AssimpLoaderGltfTexturesTest {
    @TempDir
    lateinit var dir: File

    private val previous = RhiContext.device

    @BeforeEach
    fun setUp() { RhiContext.device = FakeRhiDevice() }

    @AfterEach
    fun tearDown() { RhiContext.device = previous }

    private fun load(gltf: File) = AssimpLoader(TextureLoader(), VAOLoader()).loadModel(gltf.absolutePath)

    @Test
    fun Gltf_TexturesInASubfolder_LoadNextToTheModel() {
        val gltf = GltfFixture.write(File(dir, "Assets/Store/polyhaven/table"), "table", "textures/table_diff_1k.png")

        val texture = load(gltf).mesh.single().material.baseColorTexture
        assertNotNull(texture)

        assertEquals(File(gltf.parentFile, "textures/table_diff_1k.png").path, texture!!.filePath)
        assertEquals(GltfFixture.TEXTURE_SIZE, texture.width)
    }

    @Test
    fun Gltf_PercentEncodedTextureUri_IsDecoded() {
        val gltf = GltfFixture.write(File(dir, "model"), "rock", "textures/rock%20diff.png", textureFile = "textures/rock diff.png")

        val texture = load(gltf).mesh.single().material.baseColorTexture!!

        assertEquals(File(gltf.parentFile, "textures/rock diff.png").path, texture.filePath)
    }
}
