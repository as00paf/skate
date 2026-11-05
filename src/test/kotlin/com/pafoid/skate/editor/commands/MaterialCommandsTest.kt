package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.AssignMaterialCommand
import com.pafoid.skate.editor.commands.objects.EditMaterialCommand
import com.pafoid.skate.editor.commands.objects.MaterialState
import com.pafoid.skate.editor.commands.objects.SaveMaterialAssetCommand
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.render.material.MaterialAsset
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MaterialCommandsTest {

    private val edited = MaterialState(
        baseColor = Vector4f(0.2f, 0.3f, 0.4f, 0.5f), baseColorFactor = 2f, metallic = 0.8f, roughness = 0.1f,
        emissive = Vector3f(3f, 2f, 1f), doubleSided = true, alphaMode = AlphaMode.MASK, alphaCutoff = 0.3f,
        baseColorTexture = Texture(filePath = "a.png"), normalMap = Texture(filePath = "n.png"),
    )

    @Test
    fun MaterialState_SnapshotIsDetachedFromTheLiveMaterial() {
        val m = Material()
        val snapshot = MaterialState.of(m)
        m.baseColor.set(0f); m.emissiveFactor.set(5f)
        assertEquals(Vector4f(1f), snapshot.baseColor)
        assertEquals(Vector3f(0f), snapshot.emissive)

        edited.applyTo(m)
        m.baseColor.set(9f)
        assertEquals(Vector4f(0.2f, 0.3f, 0.4f, 0.5f), edited.baseColor)
    }

    @Test
    fun MaterialState_ValidatedClampsToShaderRanges() {
        val v = MaterialState(
            baseColor = Vector4f(-1f, 2f, 0.5f, 3f), baseColorFactor = -1f, metallic = 2f, roughness = -0.5f,
            emissive = Vector3f(-1f, 4f, 0f), alphaCutoff = 1.5f,
        ).validated()
        assertEquals(Vector4f(0f, 2f, 0.5f, 1f), v.baseColor)
        assertEquals(0f, v.baseColorFactor)
        assertEquals(1f, v.metallic)
        assertEquals(0f, v.roughness)
        assertEquals(Vector3f(0f, 4f, 0f), v.emissive)
        assertEquals(1f, v.alphaCutoff)
    }

    @Test
    fun EditMaterial_ExecuteAndUndoRestoreEveryField() {
        val m = Material()
        val before = MaterialState.of(m)
        val command = EditMaterialCommand(m, before, edited)
        command.execute()
        assertEquals(edited, MaterialState.of(m))
        assertSame(edited.normalMap, m.normalMap)
        command.undo()
        assertEquals(before, MaterialState.of(m))
        assertNull(m.baseColorTexture)
        command.execute()
        assertEquals(AlphaMode.MASK, m.alphaMode)
    }

    @Test
    fun EditMaterial_SameValuesIsNoOp() {
        val m = Material()
        assertTrue(EditMaterialCommand(m, MaterialState.of(m), MaterialState.of(m)).isNoOp)
        assertFalse(EditMaterialCommand(m, MaterialState.of(m), edited).isNoOp)
    }

    @Test
    fun AssignMaterial_UndoRestoresPreviousMaterialAndLink() {
        val original = Material()
        val rc = RenderComponent(material = original, materialPath = "old.skmat")
        val next = Material(metallicFactor = 1f)
        val command = AssignMaterialCommand(rc, next, "new.skmat")
        command.execute()
        assertSame(next, rc.material)
        assertEquals("new.skmat", rc.materialPath)
        command.undo()
        assertSame(original, rc.material)
        assertEquals("old.skmat", rc.materialPath)
        assertTrue(AssignMaterialCommand(rc, original, "old.skmat").isNoOp)
    }

    @Test
    fun SaveMaterialAsset_WritesASkmatThatLoadsBackTheSameValues(@TempDir dir: File) {
        val m = Material().also(edited.copy(normalMap = null)::applyTo)
        val file = File(dir, "Sub/Gold.skmat")
        SaveMaterialAssetCommand(m, file).execute()
        val asset = MaterialAsset.load(file)
        assertEquals("Gold", asset.name)
        val loaded = asset.toMaterial { Texture(filePath = it) }
        val state = MaterialState.of(loaded)
        assertEquals(MaterialState.of(m).copy(baseColorTexture = null), state.copy(baseColorTexture = null))
        assertEquals("a.png", loaded.baseColorTexture?.filePath)
    }
}
