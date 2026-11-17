package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.TerrainSystem
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.lod.ScreenErrorLod
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.passes.SceneFrame
import com.pafoid.skate.engine.render.renderer.passes.TerrainPass
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.terrain.TerrainChunkData
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** REN-28 task 4: chunk geometry, LOD errors and selection, skirts, pooled streams and the terrain pass. */
class TerrainMeshTest {
    private val q = 8
    private val mesh = TerrainMeshBuilder(q)

    private fun chunk(height: (Int, Int) -> Float): TerrainChunkData {
        val stride = q + 3
        return TerrainChunkData(0, 0, q, 1f, 0f, 0f, FloatArray(stride * stride) { k -> height(k % stride - 1, k / stride - 1) })
    }

    @Test
    fun Grid_HasEverySampleThenFourSkirtRows() {
        val v = mesh.gridVertices()
        assertEquals((q + 1) * (q + 1) + 4 * (q + 1), mesh.vertexCount)
        assertEquals(mesh.vertexCount * 3, v.size)
        val skirts = (0 until mesh.vertexCount).count { v[it * 3 + 2] == 1f }
        assertEquals(4 * (q + 1), skirts)
        assertEquals(4, mesh.lodCount)
        assertEquals(2, TerrainMeshBuilder(2).lodCount)
    }

    @Test
    fun Lods_StepByPowersOfTwo_WithSkirts_AndSplitOnTheColliderDiagonal() {
        for (lod in 0 until mesh.lodCount) {
            val cells = q shr lod
            assertEquals((cells * cells * 2 + 4 * cells * 2) * 3, mesh.lodIndices(lod).size, "lod $lod")
        }
        val idx = mesh.lodIndices(0)
        // First quad: triangles (00, 01, 11) and (00, 11, 10), sharing the (i, j)-(i+1, j+1) diagonal
        assertEquals(listOf(mesh.gridIndex(0, 0), mesh.gridIndex(0, 1), mesh.gridIndex(1, 1)), idx.take(3))
        assertEquals(listOf(mesh.gridIndex(0, 0), mesh.gridIndex(1, 1), mesh.gridIndex(1, 0)), idx.slice(3..5))
    }

    @Test
    fun LodErrors_ZeroOnPlanes_AndGrowWithDetail() {
        val plane = chunk { i, j -> 0.3f * i + 0.1f * j }
        mesh.lodErrors(plane).forEach { assertEquals(0f, it, 1e-5f) }
        val bumpy = chunk { i, j -> if ((i + j) % 2 == 0) 1f else 0f }
        val e = mesh.lodErrors(bumpy)
        assertEquals(0f, e[0])
        assertTrue(e[1] > 0.4f)
        assertTrue(e.toList().zipWithNext().all { (a, b) -> b >= a - 1e-6f })
    }

    @Test
    fun Stream_CarriesHeightsAndNormals_SkirtsCopyTheirEdge() {
        val c = chunk { i, j -> i.toFloat() + 10f * j }
        val s = mesh.stream(c)
        val f = TerrainMeshBuilder.STREAM_FLOATS
        assertEquals(c.height(3, 2), s[mesh.gridIndex(3, 2) * f])
        val bottomSkirt = (q + 1) * (q + 1) + 3
        assertEquals(c.height(3, 0), s[bottomSkirt * f])
        val n = c.normal(3, 2, Vector3f())
        assertEquals(n.x, s[mesh.gridIndex(3, 2) * f + 1]); assertEquals(n.z, s[mesh.gridIndex(3, 2) * f + 2])
        c.biomeIds = ByteArray((q + 1) * (q + 1)) { 2 }
        assertEquals(2f, mesh.stream(c)[mesh.gridIndex(3, 2) * f + 3])
    }

    @Test
    fun LodSelection_HasHysteresis_AndSkirtsDropByTheError() {
        val errors = floatArrayOf(0f, 0.1f, 0.4f, 1.2f)
        val scale = ScreenErrorLod.projectionScale(1080, 60f)
        // Distance where LOD 1's error is exactly 2 px
        val d = 0.1f * scale / 2f
        assertEquals(1, TerrainLod.select(errors, d * 1.01f, scale))
        assertEquals(0, TerrainLod.select(errors, d * 1.01f, scale, previous = 0), "not clearly under the threshold yet")
        assertEquals(1, TerrainLod.select(errors, d * 1.4f, scale, previous = 0))
        assertEquals(1, TerrainLod.select(errors, d * 0.9f, scale, previous = 1), "kept within the margin")
        assertEquals(0, TerrainLod.select(errors, d * 0.5f, scale, previous = 1))
        assertEquals(1f, TerrainLod.skirtDrop(errors, 1))
        assertEquals(2.4f, TerrainLod.skirtDrop(errors, 3), 1e-6f)
    }

    // --- GPU side, on the fake device ---

    private val device = FakeRhiDevice()
    @BeforeEach fun setUp() { RhiContext.device = device }
    @AfterEach fun tearDown() { RhiContext.device = null }

    @Test
    fun Pool_ReusesReleasedSlots_InsteadOfCreatingBuffers() {
        val layout = device.createVertexLayout(com.pafoid.skate.engine.render.data.VertexLayouts.TERRAIN)
        val pool = ChunkBufferPool(mesh, device, layout)
        val a = pool.acquire()
        assertEquals(mesh.lodCount, a.inputs.size)
        pool.release(a)
        val buffersBefore = device.events.size
        val b = pool.acquire()
        assertTrue(a === b)
        pool.upload(b, chunk { _, _ -> 1f })
        assertTrue(device.events.drop(buffersBefore).all { it.startsWith("update") })
        pool.close()
    }

    private val camera = CameraComponent().apply {
        viewportWidth = 640; viewportHeight = 360
        projection.setPerspective(Math.toRadians(60.0).toFloat(), 16f / 9f, 0.1f, 2000f)
        view.setLookAt(0f, 40f, 90f, 0f, 0f, 0f, 0f, 1f, 0f)
    }
    private val cameraManager = mockk<CameraManager>(relaxed = true) {
        every { this@mockk.camera } returns this@TerrainMeshTest.camera
        every { transform } returns Transform().apply { worldMatrix.set(Matrix4f().translation(0f, 40f, 90f)) }
    }

    private fun sceneWithTerrain(): Pair<Scene, TerrainComponent> {
        val terrain = TerrainComponent(sizeMetres = 128f, chunkQuads = 32, heightScaleMetres = 20f, recipe = TerrainPreset.ROLLING_HILLS.recipe())
        val scene = Scene("S")
        scene.addChild(GameObject("Ground").addComponent(Transform()).addComponent(terrain))
        TerrainSystem().apply { init(scene) }.update(0f)
        return scene to terrain
    }

    @Test
    fun Pass_DrawsVisibleChunks_UploadsOnce_AndReuploadsEditedChunks() {
        val (scene, terrain) = sceneWithTerrain()
        val frame = SceneFrame(LightingUniformsLoader())
        val shader = mockk<Shader>(relaxed = true)
        val renderer = TerrainRenderer(device = { device })
        val fb = FrameBuffer(640, 360, msaaSamples = 0) { device }.apply { initialize() }
        val pass = TerrainPass({ shader }, { shader }, { shader }, renderer, fb, frame, cameraManager)
        frame.begin(scene, camera, Vector3f(0f, 40f, 90f), null)

        pass.execute(scene)
        val draws = device.events.count { it.startsWith("drawIndexed") }
        assertTrue(draws in 1..16, "visible chunks only: $draws")
        assertEquals(16, renderer.chunksDrawn + renderer.chunksCulled)
        verify { shader.uploadFloat(Uniforms.TERRAIN_SPACING, 1f) }

        device.events.clear()
        pass.execute(scene)
        assertTrue(device.events.none { it.startsWith("update") }, "unchanged chunks are not uploaded again")

        terrain.runtime.chunk(1, 1)!!.setHeight(3, 3, 50f)
        device.events.clear()
        pass.execute(scene)
        assertEquals(1, device.events.count { it.startsWith("update") }, "only the edited chunk")
    }

    @Test
    fun Pass_FeedsPickingAndShadows() {
        val (scene, _) = sceneWithTerrain()
        val shader = mockk<Shader>(relaxed = true)
        val renderer = TerrainRenderer(device = { device })
        val pass = TerrainPass({ shader }, { shader }, { shader }, renderer, mockk(relaxed = true), SceneFrame(LightingUniformsLoader()), cameraManager)
        pass.drawPicking(scene)
        verify(exactly = 1) { shader.uploadFloat(Uniforms.ENTITY_ID, any()) }

        val caster = pass.shadowCaster
        caster.collect(scene)
        val before = caster.signature(1L)
        assertEquals(before, caster.signature(1L))
        device.events.clear()
        caster.draw(Matrix4f().ortho(-100f, 100f, -100f, 100f, -200f, 200f).lookAt(0f, 50f, 0f, 0f, 0f, 0f, 0f, 0f, -1f))
        assertTrue(device.events.count { it.startsWith("drawIndexed") } >= 1)
    }

    @Test
    fun Pass_NoTerrain_DoesNothing() {
        val renderer = TerrainRenderer(device = { device })
        val frame = SceneFrame(LightingUniformsLoader())
        frame.begin(Scene(), camera, Vector3f(), null)
        device.events.clear()
        TerrainPass({ mockk(relaxed = true) }, { null }, { null }, renderer, mockk(relaxed = true), frame, cameraManager).execute(Scene("Empty"))
        assertTrue(device.events.isEmpty(), device.events.toString())
    }
}
