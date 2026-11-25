package com.pafoid.skate.editor.terrain

import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainStreaming
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainRuntime
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** EDT-92 brush maths on a small built terrain (no editor, no GPU). */
class TerrainBrushTest {

    /** 32 m terrain, 1 m spacing, 8-quad chunks: 4 × 4 chunks, samples -16..16 on each axis. */
    private fun built(preset: TerrainPreset = TerrainPreset.FLAT): TerrainComponent =
        TerrainComponent(sizeMetres = 32f, sampleSpacing = 1f, chunkQuads = 8, heightScaleMetres = 10f, recipe = preset.recipe(),
            streaming = TerrainStreaming.OFF, seedName = "terrain/brush-test").also {
            assertTrue(it.runtime.ensureBuilt(it.key(1L), parallel = false))
        }

    /** Every resident copy of every global sample (owner and aprons) holds the same height. */
    private fun assertSeamsClosed(runtime: TerrainRuntime) {
        val layout = runtime.layout!!
        val n = layout.chunksPerSide * layout.chunkQuads
        for (gz in 0..n) for (gx in 0..n) {
            val owner = TerrainBrush.heightAt(runtime, gx, gz)!!
            TerrainBrush.forEachCopy(runtime, gx, gz, ownOnly = false) { chunk, i, j ->
                assertEquals(owner, chunk.height(i, j), 0f, "sample ($gx, $gz) in chunk (${chunk.cx}, ${chunk.cz})")
            }
        }
    }

    @Test
    fun Falloff_IsOneAtTheCentreAndZeroAtTheEdge() {
        assertEquals(1f, TerrainBrush.falloff(0f, 5f))
        assertEquals(0f, TerrainBrush.falloff(5f, 5f))
        assertEquals(0f, TerrainBrush.falloff(7f, 5f))
        assertEquals(0.5625f, TerrainBrush.falloff(2.5f, 5f), 1e-6f)
        assertTrue(TerrainBrush.falloff(1f, 5f) > TerrainBrush.falloff(3f, 5f))
    }

    @Test
    fun Raycast_HitsTheGroundUnderTheCursor_AndMissesOutside() {
        val t = built(TerrainPreset.ROLLING_HILLS)
        val runtime = t.runtime
        val hit = TerrainBrush.raycast(runtime.heightField!!, runtime.layout!!, Vector3f(3f, 40f, -2f), Vector3f(0f, -1f, 0f))
        assertNotNull(hit)
        assertEquals(3f, hit!!.x, 1e-3f); assertEquals(-2f, hit.z, 1e-3f)
        assertEquals(runtime.heightField!!.heightAt(3f, -2f), hit.y, 1e-3f)
        // Slanted ray: the hit lies on the ray and on the ground
        val o = Vector3f(-30f, 20f, 0f); val d = Vector3f(1f, -0.6f, 0.1f)
        val slanted = TerrainBrush.raycast(runtime.heightField!!, runtime.layout!!, o, d)!!
        assertEquals(runtime.heightField!!.heightAt(slanted.x, slanted.z), slanted.y, 0.02f)
        assertNull(TerrainBrush.raycast(runtime.heightField!!, runtime.layout!!, Vector3f(100f, 40f, 0f), Vector3f(0f, -1f, 0f)), "outside the terrain")
        assertNull(TerrainBrush.raycast(runtime.heightField!!, runtime.layout!!, Vector3f(0f, 40f, 0f), Vector3f(0f, 1f, 0f)), "pointing up")
    }

    @Test
    fun Raise_OnAChunkCorner_WritesEveryCopy_SoSeamsStayClosed() {
        val t = built()
        val r = TerrainEditRecorder()
        // (0, 0) is the corner shared by four chunks
        val changed = TerrainBrush.sculpt(t.runtime, TerrainTool.RAISE, 0f, 0f, 3f, 2f, 0.5f, 0f, r)
        assertTrue(changed > 0)
        assertEquals(1f, t.runtime.heightField!!.heightAt(0f, 0f), 1e-5f, "strength × dt × falloff(0) = 1 m")
        assertTrue(t.runtime.heightField!!.heightAt(2f, 0f) in 0.01f..0.99f, "falls off towards the edge")
        assertEquals(0f, t.runtime.heightField!!.heightAt(5f, 0f), 0f, "nothing outside the radius")
        assertSeamsClosed(t.runtime)
        // The four corner chunks and the neighbours whose apron holds a brushed sample were recorded once each
        assertTrue(r.before.size >= 4)
        assertTrue(r.before.values.all { snap -> snap.heights.all { it == 0f } }, "snapshots are taken before the first write")
    }

    @Test
    fun Lower_Flatten_And_Smooth() {
        val t = built()
        val rt = t.runtime
        val r = TerrainEditRecorder()
        TerrainBrush.sculpt(rt, TerrainTool.LOWER, 4f, 4f, 2f, 1f, 1f, 0f, r)
        assertEquals(-1f, rt.heightField!!.heightAt(4f, 4f), 1e-5f)

        // Flatten towards 0 from the dent: strong enough to reach the target at the centre in one step
        TerrainBrush.sculpt(rt, TerrainTool.FLATTEN, 4f, 4f, 2f, 10f, 1f, 0f, r)
        assertEquals(0f, rt.heightField!!.heightAt(4f, 4f), 1e-5f)

        // Smooth a single spike: it drops towards its neighbours' average and they rise a little
        TerrainBrush.sculpt(rt, TerrainTool.RAISE, -6f, -6f, 1.2f, 4f, 1f, 0f, r)
        val spike = rt.heightField!!.heightAt(-6f, -6f)
        TerrainBrush.sculpt(rt, TerrainTool.SMOOTH, -6f, -6f, 3f, 1f, 1f, 0f, r)
        assertTrue(rt.heightField!!.heightAt(-6f, -6f) < spike)
        assertSeamsClosed(rt)
    }

    @Test
    fun Paint_WritesSharedBorderSamplesInBothChunks_AndSeedsFromTheCurrentBiomes() {
        val t = built()
        val rt = t.runtime
        val r = TerrainEditRecorder()
        val changed = TerrainBrush.paint(rt, 0f, 0f, 1.5f, 2, r)
        assertTrue(changed > 0)
        // Global sample (16, 16) (local 0, 0) is own sample (8, 8) of chunk (1, 1) and (0, 0) of chunk (2, 2)
        val n = 9
        assertEquals(2.toByte(), rt.chunk(1, 1)!!.paintedBiomeIds!![8 * n + 8])
        assertEquals(2.toByte(), rt.chunk(2, 2)!!.paintedBiomeIds!![0])
        // Far samples keep the dominant biome they had
        val far = rt.chunk(0, 0)!!
        assertNull(far.paintedBiomeIds, "untouched chunks are not seeded")
        val c = rt.chunk(1, 1)!!
        val seeded = TerrainBrush.seedPainted(c.also { it.paintedBiomeIds = null })
        assertEquals(c.biomeIds?.size ?: (n * n), seeded.size)
    }

    @Test
    fun Restore_PutsBackBeforeAndAfter_BitExactly() {
        val t = built(TerrainPreset.ROLLING_HILLS)
        val rt = t.runtime
        val original = rt.chunks.associate { (it.cx to it.cz) to it.heights.copyOf() }
        val r = TerrainEditRecorder()
        TerrainBrush.sculpt(rt, TerrainTool.RAISE, 1f, -3f, 6f, 3f, 0.4f, 0f, r)
        TerrainBrush.paint(rt, 1f, -3f, 2f, 1, r)
        val after = r.after(rt)
        val edited = rt.chunks.associate { (it.cx to it.cz) to it.heights.copyOf() }
        val v = rt.version

        TerrainBrush.restore(rt, r.before)
        assertTrue(rt.version > v, "restore recomputes derived data (markEdited)")
        for (chunk in rt.chunks) assertArrayEquals(original.getValue(chunk.cx to chunk.cz), chunk.heights, "undo restores (${chunk.cx}, ${chunk.cz})")
        assertTrue(r.before.keys.all { rt.chunk(it.first, it.second)!!.paintedBiomeIds == null }, "painted ids undone")

        TerrainBrush.restore(rt, after)
        for (chunk in rt.chunks) assertArrayEquals(edited.getValue(chunk.cx to chunk.cz), chunk.heights, "redo restores (${chunk.cx}, ${chunk.cz})")
        assertTrue(after.values.any { it.painted != null })
        assertSeamsClosed(rt)
    }

    @Test
    fun ToolState_ClampsRadiusAndStrength() {
        val s = TerrainToolState()
        s.radius = 0f; s.strength = 1000f
        assertEquals(TerrainToolState.MIN_RADIUS, s.radius)
        assertEquals(TerrainToolState.MAX_STRENGTH, s.strength)
        s.tool = TerrainTool.RAISE; s.selectedStamp = 3
        s.reset()
        assertEquals(TerrainTool.NONE, s.tool); assertEquals(-1, s.selectedStamp)
    }

    @Test
    fun AssetName_IsFileSafeAndUnique() {
        assertEquals("My_Island", TerrainEditing.assetName("My Island!") { false })
        assertEquals("Terrain", TerrainEditing.assetName("  ") { false })
        val taken = setOf("Hills", "Hills_2")
        assertEquals("Hills_3", TerrainEditing.assetName("Hills") { it in taken })
        assertTrue(abs(1) == 1)
    }
}
