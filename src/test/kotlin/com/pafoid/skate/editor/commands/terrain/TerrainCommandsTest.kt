package com.pafoid.skate.editor.commands.terrain

import com.pafoid.skate.editor.commands.CommandCategory
import com.pafoid.skate.editor.commands.objects.AddComponentCommand
import com.pafoid.skate.editor.terrain.TerrainBrush
import com.pafoid.skate.editor.terrain.TerrainEditRecorder
import com.pafoid.skate.editor.terrain.TerrainEditing
import com.pafoid.skate.editor.terrain.TerrainTool
import com.pafoid.skate.editor.ui.handlers.TerrainActionHandler
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.terrain.TerrainAssetSource
import com.pafoid.skate.engine.assets.terrain.TerrainAssets
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainStreaming
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.terrain.BiomeSource
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainStamp
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-92 commands: strokes, stamps, presets, painted biomes and asset creation, with undo. */
class TerrainCommandsTest {
    @TempDir
    lateinit var projectDir: File

    @AfterEach
    fun resetSource() {
        TerrainAssets.source = TerrainAssetSource.NONE
        TerrainAssets.clear()
    }

    private fun terrain(name: String = "Ground"): TerrainComponent {
        val t = TerrainComponent(sizeMetres = 32f, sampleSpacing = 1f, chunkQuads = 8, heightScaleMetres = 10f,
            recipe = TerrainPreset.ROLLING_HILLS.recipe(), streaming = TerrainStreaming.OFF, seedName = "terrain/cmd-test")
        GameObject(name).addComponent(Transform()).addComponent(t)
        assertTrue(t.runtime.ensureBuilt(t.key(2L), parallel = false))
        return t
    }

    private fun heights(t: TerrainComponent) = t.runtime.chunks.associate { (it.cx to it.cz) to it.heights.copyOf() }

    @Test
    fun Stroke_UndoAndRedo_RestoreTheChunks_AndSaveEachTime() {
        val t = terrain()
        val original = heights(t)
        val recorder = TerrainEditRecorder()
        TerrainBrush.sculpt(t.runtime, TerrainTool.RAISE, 0f, 0f, 5f, 2f, 1f, 0f, recorder)
        val edited = heights(t)
        val saves = mutableListOf<TerrainComponent>()
        val command = TerrainStrokeCommand(t, recorder.before, recorder.after(t.runtime), painted = false) { saves += it }

        command.undo()
        heights(t).forEach { (k, v) -> assertArrayEquals(original.getValue(k), v) }
        command.execute()
        heights(t).forEach { (k, v) -> assertArrayEquals(edited.getValue(k), v) }
        assertEquals(listOf(t, t), saves)
        assertEquals("command.TerrainStrokeCommand.sculpt", command.displayNameKey)
        assertEquals("command.TerrainStrokeCommand.paint", TerrainStrokeCommand(t, emptyMap(), emptyMap(), true) {}.displayNameKey)
        assertEquals("Ground", command.getTargetName())
    }

    @Test
    fun Stamps_ReplaceTheList_UndoRestoresIt() {
        val t = terrain()
        val first = TerrainStamp(x = 1f)
        t.stamps += first
        val second = TerrainStamp(x = 5f, width = 3f)
        val command = SetTerrainStampsCommand(t, listOf(first, second))
        assertFalse(command.isNoOp)
        command.execute()
        assertEquals(listOf(first, second), t.stamps)
        command.undo()
        assertEquals(listOf(first), t.stamps)
        assertTrue(SetTerrainStampsCommand(t, listOf(first)).isNoOp)
    }

    @Test
    fun Preset_ReplacesTheRecipe_UndoRestoresIt() {
        val t = terrain()
        val before = t.recipe
        val command = ApplyTerrainPresetCommand(t, TerrainPreset.MOUNTAINS)
        command.execute()
        assertEquals(TerrainPreset.MOUNTAINS.recipe(), t.recipe)
        command.undo()
        assertSame(before, t.recipe)
    }

    @Test
    fun PaintedBiomes_SeedFromTheCurrentBiomes_UndoGoesBack() {
        val t = terrain()
        val saves = mutableListOf<TerrainComponent>()
        val command = UsePaintedBiomesCommand(t) { saves += it }
        assertFalse(command.isNoOp)
        command.execute()
        assertEquals(BiomeSource.PAINTED, t.biomeSource)
        for (c in t.runtime.chunks) assertArrayEquals(c.biomeIds, c.paintedBiomeIds, "nothing changes on screen (${c.cx}, ${c.cz})")
        command.undo()
        assertEquals(BiomeSource.PROCEDURAL, t.biomeSource)
        assertTrue(t.runtime.chunks.all { it.paintedBiomeIds == null })
        assertEquals(2, saves.size)
    }

    @Test
    fun CreateAsset_WritesARegisteredSkterrain_AndIsExecuteOnly() {
        val registry = AssetRegistry(projectDir).also { it.scan() }
        TerrainAssets.source = TerrainAssetSource.forRegistry(registry)
        val ran = mutableListOf<String>()
        val created = mutableListOf<File>()
        val editing = TerrainEditing(
            dataDir = { File(projectDir, "Assets/Data") },
            register = { registry.register(it) },
            run = { ran += it.getDisplayName(); it.execute() },
            created = { _, f -> created += f },
        )
        val t = terrain("Big Island")
        assertTrue(editing.ensureAsset(t))
        val folder = File(projectDir, "Assets/Data/Big_Island.skterrain")
        assertTrue(File(folder, "terrain.json").isFile)
        assertEquals(listOf(folder), created)
        assertEquals(listOf("Create Terrain Asset"), ran)
        assertEquals(folder, TerrainAssets.source.folder(t.asset), "the new GUID resolves at once (registered)")
        assertTrue(editing.ensureAsset(t))
        assertEquals(1, ran.size, "an asset is created once")

        // A sculpt saved through the service lands in a region file; a second terrain gets its own name
        TerrainBrush.sculpt(t.runtime, TerrainTool.RAISE, 0f, 0f, 4f, 3f, 1f, 0f, TerrainEditRecorder())
        editing.save(t)
        assertTrue(File(folder, "regions").listFiles().orEmpty().isNotEmpty())
        val other = terrain("Big Island")
        assertTrue(editing.ensureAsset(other))
        assertTrue(File(projectDir, "Assets/Data/Big_Island_2.skterrain").isDirectory)

        val command = CreateTerrainAssetCommand(terrain(), editing::createAsset)
        assertEquals(CommandCategory.EXECUTE_ONLY, command.getCategory())
        val noProject = TerrainEditing(dataDir = { null }, register = {}, run = { it.execute() })
        assertFalse(noProject.ensureAsset(terrain()), "no project: no asset")
    }

    @Test
    fun AddComponent_NamesTheTerrainSeedAfterItsObject() {
        val go = GameObject("Hills").apply { addComponent(Transform()) }
        AddComponentCommand(go, ComponentType.TERRAIN, LoggerService().apply { consoleSink = { _, _ -> } }).execute()
        assertEquals("terrain/Hills", go.getComponent<TerrainComponent>()!!.seedName)
    }

    @Test
    fun FlattenUnder_FindsTheTerrainBeneath_AndFallsBackToAPoint() {
        val scene = Scene("s")
        val near = TerrainComponent(sizeMetres = 32f)
        val far = TerrainComponent(sizeMetres = 32f)
        scene.addChild(GameObject("Near").addComponent(Transform().apply { translation.set(0f, 0f, 0f) }).addComponent(near))
        scene.addChild(GameObject("Far").addComponent(Transform().apply { translation.set(200f, 0f, 0f); worldMatrix.translation(200f, 0f, 0f) }).addComponent(far))
        val terrains = TerrainEditing.terrainsIn(scene)
        assertEquals(listOf(near, far), terrains)
        assertSame(far, TerrainActionHandler.terrainUnder(terrains, Vector3f(205f, 0f, 3f)))
        assertSame(near, TerrainActionHandler.terrainUnder(terrains, Vector3f(500f, 0f, 0f)), "outside every terrain: the first")
        assertNull(TerrainActionHandler.terrainUnder(emptyList(), Vector3f()))

        val box = GameObject("Box").addComponent(Transform())
        val bounds = TerrainActionHandler.boundsOf(box, Vector3f(3f, 1f, 2f))
        assertEquals(1, bounds.size)
        assertEquals(Vector3f(3f, 1f, 2f), bounds[0].first)
        assertNotNull(bounds[0].second)
    }
}
