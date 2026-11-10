package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabCategory
import com.pafoid.skate.game.prefabs.PrefabType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PrefabThumbnailMaterialTest {

    @TempDir
    lateinit var dir: File

    private val project get() = File(dir, "Game")
    private val engine get() = File(dir, "engine")

    /** A project copy of [surface]'s engine texture (an override: its `.meta` names the engine source). */
    private fun projectCopy(surface: MaterialType): File {
        val logical = EngineDefaults.logicalPath(surface.texturePath)!!
        val file = File(File(project, "Assets"), logical).apply { parentFile.mkdirs(); writeText("x") }
        AssetMeta.write(file, AssetMeta(guid = AssetId.generate(), settings = mapOf(EngineDefaults.META_SOURCE to logical)))
        return file
    }

    @Test
    fun surfaceFor_ObstaclesAndTexturedGenericsHaveTheirDefault() {
        assertEquals(MaterialType.CONCRETE, PrefabThumbnailMaterial.surfaceFor(PrefabCatalog.entry(PrefabType.KICKER), null))
        assertEquals(MaterialType.METAL, PrefabThumbnailMaterial.surfaceFor(PrefabCatalog.entry(PrefabType.RAIL), MaterialType.WOOD_TAN))
        assertEquals(MaterialType.WOOD_BROWN, PrefabThumbnailMaterial.surfaceFor(PrefabCatalog.entry(PrefabType.CRATE), null))
        assertEquals(MaterialType.METAL, PrefabThumbnailMaterial.surfaceFor(PrefabCatalog.entry(PrefabType.BARREL), null))
        assertEquals(MaterialType.WOOD_DARK, PrefabThumbnailMaterial.surfaceFor(PrefabCatalog.entry(PrefabType.BENCH), null))
        assertEquals(MaterialType.WOOD_TAN, PrefabThumbnailMaterial.surfaceFor(PrefabCatalog.entry(PrefabType.BANK), MaterialType.WOOD_TAN))
        assertNull(PrefabThumbnailMaterial.surfaceFor(PrefabCatalog.entry(PrefabType.POINT_LIGHT), null))
    }

    @Test
    fun surfaceFor_EveryStreetAndTransitionObstacleIsTextured() {
        listOf(PrefabCategory.STREET, PrefabCategory.TRANSITION).flatMap(PrefabCatalog::inCategory).forEach {
            assertNotNull(PrefabThumbnailMaterial.surfaceFor(it, null), "${it.type} thumbnail has no material")
        }
    }

    @Test
    fun texturePath_PrefersTheProjectCopy() {
        val copy = projectCopy(MaterialType.CONCRETE)

        assertEquals(copy.absolutePath, PrefabThumbnailMaterial.texturePath(MaterialType.CONCRETE, project, engine))
    }

    @Test
    fun texturePath_FallsBackToTheEngineSource() {
        val logical = EngineDefaults.logicalPath(MaterialType.WOOD_DARK.texturePath)!!
        val file = File(File(engine, "assets"), logical).apply { parentFile.mkdirs(); writeText("x") }

        assertEquals(file.absolutePath, PrefabThumbnailMaterial.texturePath(MaterialType.WOOD_DARK, project, engine))
        assertEquals(file.absolutePath, PrefabThumbnailMaterial.texturePath(MaterialType.WOOD_DARK, null, engine))
    }

    @Test
    fun texturePath_NullWhenNoCopyExists() {
        assertNull(PrefabThumbnailMaterial.texturePath(MaterialType.METAL, project, engine))
    }

    @Test
    fun everySurfaceHasAnEngineSource() {
        MaterialType.entries.forEach { surface ->
            assertTrue(surface.texturePath in EngineDefaults.CATALOG, "$surface is not an engine default")
        }
    }

    @Test
    fun applyTexture_SetsEveryPartsBaseColor() {
        val model = `3dModel`("cube.obj", listOf(MeshPart(vaoId = 1), MeshPart(vaoId = 2)))
        val texture = Texture(filePath = "metal.png")

        val textured = PrefabThumbnailMaterial.applyTexture(model, texture)

        assertEquals("cube.obj", textured.path)
        assertEquals(listOf(1, 2), textured.mesh.map { it.vaoId })
        textured.mesh.forEach { assertSame(texture, it.material.baseColorTexture) }
        model.mesh.forEach { assertNull(it.material.baseColorTexture) }
    }

    @Test
    fun applyTexture_NoTextureKeepsTheModel() {
        val model = `3dModel`("sphere.obj")

        assertSame(model, PrefabThumbnailMaterial.applyTexture(model, null))
    }

    @Test
    fun cacheKey_DiffersPerSurface() {
        val ledge = PrefabCatalog.entry(PrefabType.LEDGE)

        assertNotEquals(
            PrefabThumbnailMaterial.cacheKey(ledge, MaterialType.CONCRETE),
            PrefabThumbnailMaterial.cacheKey(ledge, MaterialType.WOOD_DARK),
        )
    }
}
