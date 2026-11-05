package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.objects.MaterialState
import com.pafoid.skate.editor.events.MaterialAction
import com.pafoid.skate.editor.events.UndoRedoAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.material.MaterialAsset
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MaterialActionHandlerTest {

    @TempDir
    lateinit var dir: File

    private val events = EventSystem()
    private val logger = LoggerService()
    private val undo = UndoRedoManager(events, logger)

    private class FakeDialogs : MaterialFileDialogs {
        var savePath: String? = null
        var openPath: String? = null
        var suggested: File? = null
        override fun saveAs(suggested: File): String? = savePath.also { this.suggested = suggested }
        override fun open(startDir: File?): String? = openPath
    }

    private val dialogs = FakeDialogs()

    init {
        MaterialActionHandler(events, undo, logger, { dir }, { Texture(filePath = it) }, dialogs)
    }

    private fun renderObject(partMaterial: Material = Material(metallicFactor = 0.7f)): RenderComponent {
        val rc = RenderComponent(model = `3dModel`("cube.gltf", mesh = listOf(MeshPart(material = partMaterial))))
        GameObject("Gold Ball").addComponent(Transform()).addComponent(rc)
        return rc
    }

    @Test
    fun CreateOverride_CopiesThePartMaterialAndUndoRemovesIt() {
        val part = Material(metallicFactor = 0.7f)
        val rc = renderObject(part)
        events.publish(MaterialAction.CreateOverride(rc))
        val override = rc.material!!
        assertNotSame(part, override)
        assertEquals(MaterialState.of(part), MaterialState.of(override))
        override.baseColor.set(0f)
        assertEquals(Vector4f(1f), part.baseColor, "The shared model material is never edited")

        events.publish(UndoRedoAction.Undo)
        assertNull(rc.material)
        events.publish(UndoRedoAction.Redo)
        assertSame(override, rc.material)
    }

    @Test
    fun Edit_IsOneUndoStep() {
        val rc = renderObject().apply { material = Material() }
        val m = rc.material!!
        val before = MaterialState.of(m)
        val after = before.copy(roughness = 0.9f)
        after.applyTo(m) // the inspector previewed it already
        events.publish(MaterialAction.Edit(m, before, after, "Gold Ball"))
        assertEquals(1, undo.getUndoCount())
        events.publish(UndoRedoAction.Undo)
        assertEquals(0.5f, m.roughnessFactor)
        events.publish(UndoRedoAction.Redo)
        assertEquals(0.9f, m.roughnessFactor)
    }

    @Test
    fun SaveAs_SuggestsProjectMaterialsFolderWritesAndLinks() {
        val rc = renderObject().apply { material = Material(metallicFactor = 1f) }
        val target = File(dir, "Assets/Materials/Gold.skmat")
        dialogs.savePath = target.path
        events.publish(MaterialAction.SaveAs(rc))

        assertEquals(File(dir, "Assets/Materials/Gold_Ball.skmat"), dialogs.suggested)
        assertTrue(target.isFile)
        assertEquals(target.absolutePath, rc.materialPath)
        assertEquals(1f, MaterialAsset.load(target).toMaterial { null }.metallicFactor)

        events.publish(UndoRedoAction.Undo)
        assertNull(rc.materialPath, "Undo unlinks")
        assertTrue(target.isFile, "Undo never deletes the file")
    }

    @Test
    fun SaveAs_CancelledChangesNothing() {
        val rc = renderObject().apply { material = Material() }
        events.publish(MaterialAction.SaveAs(rc))
        assertNull(rc.materialPath)
        assertEquals(0, undo.getUndoCount())
    }

    @Test
    fun Load_UsesTheAssetAndUndoRestoresThePreviousMaterial() {
        val file = File(dir, "Red.skmat")
        MaterialAsset.from(Material(baseColor = Vector4f(1f, 0f, 0f, 1f)), "Red").save(file)
        val rc = renderObject()
        dialogs.openPath = file.path
        events.publish(MaterialAction.Load(rc))
        assertEquals(Vector4f(1f, 0f, 0f, 1f), rc.material!!.baseColor)
        assertEquals(file.absolutePath, rc.materialPath)
        events.publish(UndoRedoAction.Undo)
        assertNull(rc.material)
        assertNull(rc.materialPath)
    }

    @Test
    fun Load_InvalidFileIsLoggedAndChangesNothing() {
        val file = File(dir, "Broken.skmat").apply { writeText("{ not json") }
        val rc = renderObject()
        dialogs.openPath = file.path
        events.publish(MaterialAction.Load(rc))
        assertNull(rc.material)
        assertEquals(0, undo.getUndoCount())
    }

    @Test
    fun Unlink_And_RemoveOverride_AreUndoable() {
        val m = Material()
        val rc = renderObject().apply { material = m; materialPath = "x.skmat" }
        events.publish(MaterialAction.Unlink(rc))
        assertSame(m, rc.material)
        assertNull(rc.materialPath)
        events.publish(MaterialAction.RemoveOverride(rc))
        assertNull(rc.material)
        events.publish(UndoRedoAction.Undo)
        events.publish(UndoRedoAction.Undo)
        assertSame(m, rc.material)
        assertEquals("x.skmat", rc.materialPath)
    }

    /** Real path: save as asset, edit, save, write the scene, reopen it from disk. The .skmat wins over the scene copy. */
    @Test
    fun SavedAsset_SceneSavedAndReopened_LoadsTheMaterialFromTheSkmat() {
        val rc = renderObject().apply { material = Material() }
        val go = rc.gameObject!!
        val scene = Scene("Mat").apply { addChild(go) }
        val skmat = File(dir, "Assets/Materials/Gold.skmat")
        dialogs.savePath = skmat.path
        events.publish(MaterialAction.SaveAs(rc))
        val m = rc.material!!
        val before = MaterialState.of(m)
        events.publish(MaterialAction.Edit(m, before, before.copy(metallic = 0.9f, roughness = 0.15f), go.name))
        events.publish(MaterialAction.Save(rc))

        val serializer = Serializer()
        val sceneFile = File(dir, "Mat.scene").apply { writeText(serializer.encodeFile(FileKind.SCENE, scene)) }
        // Changed outside the scene (e.g. by another scene using the same asset): reopening picks it up
        val asset = MaterialAsset.load(skmat)
        asset.copy(params = asset.params + ("baseColor" to listOf(0.2f, 0.4f, 0.6f, 1f))).save(skmat)

        val assets = mockk<AssetsManager>(relaxed = true).also {
            every { it.loadModel(any()) } answers { `3dModel`(firstArg()) }
            every { it.getTexture(any()) } answers { Texture(filePath = firstArg()) }
        }
        val sceneManager = SceneManager(assets, events, serializer, SystemManager(), logger)
        val reopened = serializer.decodeFile<Scene?>(FileKind.SCENE, sceneFile.readText())!!
        sceneManager.openScene(reopened)

        val loaded = reopened.children.single { it.name == go.name }.getComponent<RenderComponent>()!!
        assertEquals(skmat.absolutePath, loaded.materialPath)
        val lm = loaded.material!!
        assertEquals(0.9f, lm.metallicFactor)
        assertEquals(0.15f, lm.roughnessFactor)
        assertEquals(Vector4f(0.2f, 0.4f, 0.6f, 1f), lm.baseColor)
    }

    @Test
    fun MissingSkmat_KeepsTheSceneCopy() {
        val rc = RenderComponent(material = Material(metallicFactor = 0.4f), materialPath = File(dir, "gone.skmat").path)
        val error = rc.resolveMaterialAsset({ p -> File(p).takeIf(File::isFile)?.readText() }, { null })
        assertFalse(error == null)
        assertEquals(0.4f, rc.material!!.metallicFactor)
    }
}
