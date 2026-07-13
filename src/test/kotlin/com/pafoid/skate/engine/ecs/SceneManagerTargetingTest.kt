package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SceneManagerTargetingTest {

    @Test
    fun `closeOtherScenes keeps referenced scene under reordered list`() {
        val sceneManager = createSceneManager()
        val sceneA = mockScene(1, "A")
        val sceneB = mockScene(2, "B")
        val sceneC = mockScene(3, "C")
        sceneManager.openScenes.addAll(listOf(sceneC, sceneA, sceneB))
        sceneManager.activeSceneIndex = 0

        sceneManager.closeOtherScenes(sceneB)

        assertEquals(1, sceneManager.openScenes.size)
        assertSame(sceneB, sceneManager.openScenes.first())
        assertEquals(0, sceneManager.activeSceneIndex)
    }

    @Test
    fun `openScene resolves RenderComponent and Animator references`() {
        val assetsManager = mockk<AssetsManager>(relaxed = true)
        val assetDatabase = mockk<AssetDatabase>(relaxed = true)
        val sceneManager = SceneManager(
            assetsManager = assetsManager,
            eventSystem = mockk(relaxed = true),
            serializer = mockk(relaxed = true),
            systemManager = mockk(relaxed = true),
            logger = mockk(relaxed = true),
            assetDatabase = assetDatabase
        )

        val scene = Scene("TestScene")
        val go = GameObject("TestObject")

        val rc = com.pafoid.skate.engine.ecs.components.RenderComponent(modelGuid = "test-model-guid")
        val animator = com.pafoid.skate.engine.ecs.components.Animator()
        animator.animationPaths.add("path/to/animation.json")

        go.components.add(rc)
        go.components.add(animator)
        scene.gameObjects.add(go)

        val assetInfo = mockk<com.pafoid.skate.engine.assets.database.AssetInfo>(relaxed = true)
        val tempFile = java.io.File.createTempFile("dummy_model", ".glb")
        tempFile.deleteOnExit()
        every { assetDatabase.getByGuid(any()) } returns assetInfo
        every { assetInfo.absoluteSourcePath } returns tempFile.absolutePath

        val expectedModel = mockk<com.pafoid.skate.engine.assets.data.models.BaseModel>()
        every { assetsManager.loadModelSync(tempFile.absolutePath) } returns expectedModel

        sceneManager.openScene(scene)

        assertSame(expectedModel, rc.model)
    }

    @Test
    fun `prepareSceneForSaving resolves model and texture GUIDs`() {
        val assetDatabase = mockk<AssetDatabase>(relaxed = true)
        val sceneManager = SceneManager(
            assetsManager = mockk(relaxed = true),
            eventSystem = mockk(relaxed = true),
            serializer = mockk(relaxed = true),
            systemManager = mockk(relaxed = true),
            logger = mockk(relaxed = true),
            assetDatabase = assetDatabase
        )

        val scene = Scene("TestScene")
        val go = GameObject("TestObject")

        val texture = mockk<com.pafoid.skate.engine.assets.data.Texture>()
        every { texture.filePath } returns "path/to/texture.png"

        val material = com.pafoid.skate.engine.assets.data.models.Material(baseColorTexture = texture)
        val meshPart = com.pafoid.skate.engine.assets.data.models.MeshPart(mockk(), material)
        val model = com.pafoid.skate.engine.assets.data.models.TexturedModel(listOf(meshPart))
        model.sourcePath = "path/to/model.glb"

        val rc = com.pafoid.skate.engine.ecs.components.RenderComponent(model = model)
        go.components.add(rc)
        scene.gameObjects.add(go)

        val modelAsset = mockk<com.pafoid.skate.engine.assets.database.AssetInfo>()
        val modelGuid = com.pafoid.skate.engine.assets.database.AssetGuid("resolved-model-guid")
        every { modelAsset.guid } returns modelGuid
        every { assetDatabase.getByAbsolutePath(java.io.File("path/to/model.glb").absolutePath) } returns modelAsset

        val textureAsset = mockk<com.pafoid.skate.engine.assets.database.AssetInfo>()
        val textureGuid = com.pafoid.skate.engine.assets.database.AssetGuid("resolved-texture-guid")
        every { textureAsset.guid } returns textureGuid
        every { assetDatabase.getByAbsolutePath(java.io.File("path/to/texture.png").absolutePath) } returns textureAsset

        sceneManager.prepareSceneForSaving(scene)

        assertEquals("resolved-model-guid", rc.modelGuid)
        assertEquals("resolved-texture-guid", rc.albedoTextureGuid)
    }

    private fun createSceneManager(): SceneManager {
        return SceneManager(
            assetsManager = mockk(relaxed = true),
            eventSystem = mockk(relaxed = true),
            serializer = mockk(relaxed = true),
            systemManager = mockk(relaxed = true),
            logger = mockk(relaxed = true),
            assetDatabase = mockk(relaxed = true)
        )
    }

    private fun mockScene(uid: Int, name: String): Scene {
        val scene = mockk<Scene>(relaxed = true)
        every { scene.getUid() } returns uid
        every { scene.name } returns name
        return scene
    }
}
