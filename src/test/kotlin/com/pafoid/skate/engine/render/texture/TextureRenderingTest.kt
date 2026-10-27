package com.pafoid.skate.engine.render.texture

import com.pafoid.skate.engine.assets.data.Texture
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextureRenderingTest {
    @Test
    fun TextureQuality_HighClampsAnisotropyToHardware() {
        assertEquals(8f, TextureSampling.forQuality(TextureQualityLevel.HIGH, 8f).maxAnisotropy)
        assertEquals(16f, TextureSampling.forQuality(TextureQualityLevel.HIGH, 32f).maxAnisotropy)
        assertEquals(1f, TextureSampling.forQuality(TextureQualityLevel.HIGH, 0f).maxAnisotropy)
    }

    @Test
    fun TextureQuality_LowUsesNoAnisotropyAndNearestMip() {
        val s = TextureSampling.forQuality(TextureQualityLevel.LOW, 16f)
        assertEquals(1f, s.maxAnisotropy)
        assertEquals(MinFilter.NEAREST_MIPMAP_LINEAR, s.minFilter)
        assertEquals(4f, TextureSampling.forQuality(TextureQualityLevel.MEDIUM, 16f).maxAnisotropy)
    }

    @Test
    fun SpriteAtlas_RegionUvsAreInsetAndValidated() {
        val atlas = SpriteAtlas(Texture(64, 64, 1, 4, false, "a.png")).add("a", AtlasRegion(0, 0, 32, 32))
        val uv = atlas.uv("a")
        assertEquals(0.5f / 64f, uv[0]); assertEquals(31.5f / 64f, uv[2])
        assertFailsWith<IllegalArgumentException> { atlas.add("bad", AtlasRegion(40, 0, 32, 8)) }
        assertFailsWith<NoSuchElementException> { atlas.uv("nope") }
    }

    @Test
    fun SpriteAtlas_SpritesShareTextureSoTheyBatch() {
        val tex = Texture(64, 64, 1, 4, false, "a.png")
        val atlas = SpriteAtlas(tex).add("a", AtlasRegion(0, 0, 32, 32)).add("b", AtlasRegion(32, 0, 32, 32))
        assertEquals(atlas.sprite("a").texture, atlas.sprite("b").texture)
        assertEquals(32f, atlas.sprite("b").width)
    }

    private fun corner(m: Matrix4f, x: Float, y: Float) = Vector4f(x, y, 1f, 1f).mul(m).let { Vector3f(it.x, it.y, it.z) }

    @Test
    fun Billboard_SphericalQuadIsCenteredAndFacesCamera() {
        val m = Billboard.matrix(BillboardMode.SPHERICAL, Vector3f(0f, 0f, 0f), 2f, 2f, Vector3f(0f, 0f, 5f), Matrix4f())
        val c = corner(m, 0.5f, 0.5f)
        assertEquals(0f, c.x, 1e-5f); assertEquals(0f, c.y, 1e-5f); assertEquals(0f, c.z, 1e-5f)
        assertEquals(2f, corner(m, 1f, 0.5f).distance(c) * 2f / 1f, 1e-4f) // width 2 across the quad
        assertEquals(0f, corner(m, 1f, 1f).z, 1e-5f) // quad plane is perpendicular to the view direction
    }

    @Test
    fun Billboard_CylindricalKeepsQuadUpright() {
        val m = Billboard.matrix(BillboardMode.CYLINDRICAL, Vector3f(), 1f, 1f, Vector3f(3f, 10f, 0f), Matrix4f())
        assertEquals(0f, corner(m, 0.5f, 1f).x, 1e-5f)
        assertEquals(0.5f, corner(m, 0.5f, 1f).y, 1e-5f)
    }
}
