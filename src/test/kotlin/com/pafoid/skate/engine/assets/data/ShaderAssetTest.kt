package com.pafoid.skate.engine.assets.data

import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The GL-free shader asset (REN-12 step 5) on a recording device. */
class ShaderAssetTest {
    private val device = FakeRhiDevice()

    @BeforeEach
    fun setUp() { RhiContext.device = device }

    @AfterEach
    fun tearDown() { RhiContext.device = null }

    private fun shader(name: String) = Shader(device.createShader(ShaderDesc(name, "v", "f")))

    @Test
    fun Uploads_MakeTheShaderCurrentThenSetNamedParams() {
        val s = shader("lit")
        s.uploadFloat("uA", 0.5f)
        s.uploadBoolean("uOn", true)
        s.uploadVec3f("uColor", Vector3f(1f, 2f, 3f))
        s.uploadMat4f("uM", Matrix4f().translation(4f, 5f, 6f))

        assertEquals(
            listOf(
                "use lit", "lit.uA=0.5", "use lit", "lit.uOn=1", "use lit", "lit.uColor=1.0,2.0,3.0",
                "use lit", "lit.uM=mat4(12:4.0,13:5.0,14:6.0)",
            ),
            device.events,
        )
    }

    @Test
    fun ReplaceWith_AdoptsTheFreshProgramAndEmptiesIt() {
        val old = shader("a")
        val fresh = shader("b")
        val freshRhi = fresh.rhi
        old.replaceWith(fresh)

        assertSame(freshRhi, old.rhi)
        assertTrue((device.shaders[0]).closed)
        assertFalse((device.shaders[1]).closed)
        assertNull(fresh.rhi)
        old.uploadInt("uX", 2)
        assertEquals("b.uX=2", device.events.last())
    }

    @Test
    fun Destroy_ClosesTheProgram_LaterUploadsDoNothing() {
        val s = shader("gone")
        s.destroy()
        s.destroy()
        assertTrue(device.shaders.single().closed)
        device.events.clear()
        s.uploadFloat("uA", 1f)
        s.start()
        assertEquals(emptyList<String>(), device.events)
        assertFalse(s.bindUniformBlock("Lights", 1))
    }
}
