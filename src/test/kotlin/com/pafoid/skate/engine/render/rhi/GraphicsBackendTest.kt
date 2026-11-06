package com.pafoid.skate.engine.render.rhi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GraphicsBackendTest {
    @Test
    fun GraphicsBackend_DefaultsToOpenGlAndOnlyVulkanIsExperimental() {
        assertEquals(GraphicsBackend.OPENGL, GraphicsBackend.DEFAULT)
        assertFalse(GraphicsBackend.OPENGL.experimental)
        assertTrue(GraphicsBackend.VULKAN.experimental)
        assertEquals("lbl.graphics_backend.vulkan", GraphicsBackend.VULKAN.labelKey)
    }

    @Test
    fun GraphicsBackend_ParsesCaseInsensitivelyAndRejectsUnknown() {
        assertEquals(GraphicsBackend.VULKAN, GraphicsBackend.parse(" vulkan "))
        assertEquals(GraphicsBackend.OPENGL, GraphicsBackend.parse("OpenGL"))
        assertNull(GraphicsBackend.parse("metal"))
        assertNull(GraphicsBackend.parse(""))
        assertNull(GraphicsBackend.parse(null))
    }

    @Test
    fun GraphicsBackend_ResolveOrderIsPropertyThenSettingsThenProject() {
        val p = GraphicsBackend.OPENGL
        assertEquals(GraphicsBackend.OPENGL, GraphicsBackend.resolve(p, settings = null, property = null))
        assertEquals(GraphicsBackend.VULKAN, GraphicsBackend.resolve(p, settings = GraphicsBackend.VULKAN, property = null))
        assertEquals(GraphicsBackend.OPENGL, GraphicsBackend.resolve(GraphicsBackend.VULKAN, GraphicsBackend.VULKAN, property = "opengl"))
        assertEquals(GraphicsBackend.VULKAN, GraphicsBackend.resolve(GraphicsBackend.VULKAN, null, property = "bogus"), "unknown property ignored")
    }
}
