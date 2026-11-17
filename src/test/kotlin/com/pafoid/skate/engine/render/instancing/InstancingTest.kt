package com.pafoid.skate.engine.render.instancing

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.shader.ShaderFeature
import com.pafoid.skate.engine.render.shader.ShaderPreprocessor
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/** REN-29 task 2: instanced mesh draws (packing, batches, instanced inputs, the INSTANCED shader path). */
class InstancingTest {
    private val device = FakeRhiDevice()
    private val loader = VAOLoader { device }

    @BeforeEach fun setUp() { RhiContext.device = device }
    @AfterEach fun tearDown() { RhiContext.device = null }

    private fun cube() = loader.loadToVAO(
        positions = FloatArray(24), textureCoords = FloatArray(16), normals = FloatArray(24), indices = IntArray(36) { it % 8 },
    )

    @Test
    fun Packing_StoresTheTopThreeRowsThenParams_AndRoundTrips() {
        val world = Matrix4f().translationRotateScale(Vector3f(1f, 2f, 3f), Quaternionf().rotateY(0.7f), Vector3f(2f, 2f, 2f))
        val data = FloatArray(32)
        InstancePacking.put(world, data, 16, tint = 0.1f, random = 0.2f, windPhase = 0.3f, lodFade = 0.4f)
        assertEquals(1f, data[16 + 3]); assertEquals(2f, data[16 + 7]); assertEquals(3f, data[16 + 11]) // translation column
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), data.copyOfRange(28, 32))
        assertTrue(InstancePacking.matrix(data, 1, Matrix4f()).equals(world, 1e-6f))
        assertEquals(64, VertexLayouts.INSTANCE_FLOATS * Float.SIZE_BYTES)
    }

    @Test
    fun Batch_GrowsItsStagingAndGpuBuffer_AndSkipsUnchangedUploads() {
        val batch = InstanceBatch(cube()) { device }
        repeat(100) { batch.add(Matrix4f().translation(it.toFloat(), 0f, 0f)) }
        val buffer = batch.upload() as FakeRhiDevice.Buffer
        assertEquals(100, batch.count)
        assertTrue(buffer.size >= 100L * 64)
        device.events.clear()
        batch.upload()
        assertTrue(device.events.none { it.startsWith("update") }, "unchanged records are not uploaded again")
        batch.clear(); batch.add(Matrix4f())
        assertSame(buffer, batch.upload())
        assertTrue(device.events.any { it.startsWith("update") })
        batch.close()
        assertTrue(buffer.closed)
        assertNull(batch.buffer)
    }

    @Test
    fun VaoLoader_InstancedInput_IsCachedPerBuffer_SharesMeshBuffers_AndIsReleased() {
        val part = cube()
        val a = device.createBuffer(com.pafoid.skate.engine.render.rhi.BufferUsage.INSTANCE, com.pafoid.skate.engine.render.rhi.BufferUpdate.DYNAMIC, FloatArray(0))
        val b = device.createBuffer(com.pafoid.skate.engine.render.rhi.BufferUsage.INSTANCE, com.pafoid.skate.engine.render.rhi.BufferUpdate.DYNAMIC, FloatArray(0))
        val inputA = loader.instancedInput(part, a) as FakeRhiDevice.Input
        assertSame(inputA, loader.instancedInput(part, a))
        val inputB = loader.instancedInput(part, b) as FakeRhiDevice.Input
        assertNotSame(inputA, inputB)
        assertSame(inputA.layout, inputB.layout, "one instanced layout per attribute set")
        val mesh = part.input as FakeRhiDevice.Input
        assertEquals(mesh.buffers + a, inputA.buffers)
        assertSame(mesh.indices, inputA.indices)
        assertTrue(inputA.layout.desc.bindings.last().perInstance)
        loader.releaseInstanced(a)
        assertTrue(inputA.closed)
        assertFalse(inputB.closed)
        loader.deleteVAO(part.vaoId)
        assertTrue(inputB.closed)
        assertFalse((a as FakeRhiDevice.Buffer).closed, "the batch owns its instance buffer")
    }

    @Test
    fun ModelRenderer_DrawInstanced_IsOneCallForEveryInstance() {
        val renderer = ModelRenderer(mockk(relaxed = true), { device }, loader)
        val batch = InstanceBatch(cube(), Material(alphaMode = AlphaMode.OPAQUE)) { device }
        repeat(5) { batch.add(Matrix4f().translation(it.toFloat(), 0f, 0f)) }
        EngineStats.resetDrawCalls()
        device.events.clear()

        assertTrue(renderer.drawInstanced(batch, mockk<Shader>(relaxed = true)))

        assertEquals(listOf("drawIndexed 36 from 0 x5"), device.events.filter { it.startsWith("draw") })
        assertEquals(1, EngineStats.instancedDraws.get())
        assertEquals(5, EngineStats.instances.get())
        batch.clear()
        assertFalse(renderer.drawInstanced(batch, mockk(relaxed = true)), "empty batches draw nothing")
        // Closing the batch drops the loader's instanced input over its buffer
        batch.add(Matrix4f()); renderer.drawInstanced(batch, mockk(relaxed = true))
        val input = device.inputs.last()
        batch.close()
        assertTrue(input.closed)
    }

    @Test
    fun ModelRenderer_WithoutALoader_CannotInstance() {
        val batch = InstanceBatch(cube()) { device }
        batch.add(Matrix4f())
        assertFalse(ModelRenderer(mockk(relaxed = true), { device }).drawInstanced(batch, mockk(relaxed = true)))
    }

    @Test
    fun InstancedFeature_ReplacesTheModelUniformByTheInstanceRows() {
        for (name in listOf("shader_3d_default.glsl", "shadow.glsl", "shader_3d_picking.glsl")) {
            val vertex = File("assets/Shaders/$name").readText().substringAfter("#type vertex").substringBefore("#type fragment")
            val out = ShaderPreprocessor.process(vertex, setOf(ShaderFeature.INSTANCED.name), name) { File("assets/Shaders/$it").readText() }
            assertTrue("#define INSTANCED" in out, name)
            assertTrue("worldPos = MODEL_MATRIX" in out || "worldMatrix = MODEL_MATRIX" in out, name)
            assertTrue("#define MODEL_MATRIX transpose(mat4(aInstanceRow0, aInstanceRow1, aInstanceRow2" in out, name)
        }
    }
}
