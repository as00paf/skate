package com.pafoid.skate.engine.render.rhi.vulkan

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR
import org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB
import org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_UNORM
import org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16B16A16_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM
import org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED

/** VK-01 swapchain choices, without a device. */
class SwapchainPolicyTest {
    private val all = intArrayOf(VK_PRESENT_MODE_IMMEDIATE_KHR, VK_PRESENT_MODE_MAILBOX_KHR, VK_PRESENT_MODE_FIFO_KHR)

    @Test
    fun PresentMode_VsyncIsFifo() {
        assertEquals(VK_PRESENT_MODE_FIFO_KHR, SwapchainPolicy.presentMode(all, vsync = true))
    }

    @Test
    fun PresentMode_NoVsyncPrefersMailboxThenImmediate() {
        assertEquals(VK_PRESENT_MODE_MAILBOX_KHR, SwapchainPolicy.presentMode(all, vsync = false))
        assertEquals(VK_PRESENT_MODE_IMMEDIATE_KHR,
            SwapchainPolicy.presentMode(intArrayOf(VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_IMMEDIATE_KHR), vsync = false))
        assertEquals(VK_PRESENT_MODE_FIFO_KHR,
            SwapchainPolicy.presentMode(intArrayOf(VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_FIFO_RELAXED_KHR), vsync = false))
    }

    @Test
    fun SurfaceFormat_PrefersLinearBgra() {
        val srgb = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
        val formats = listOf(VK_FORMAT_B8G8R8A8_SRGB to srgb, VK_FORMAT_R8G8B8A8_UNORM to srgb, VK_FORMAT_B8G8R8A8_UNORM to srgb)
        assertEquals(VK_FORMAT_B8G8R8A8_UNORM to srgb, SwapchainPolicy.surfaceFormat(formats))
        assertEquals(VK_FORMAT_R8G8B8A8_UNORM to srgb, SwapchainPolicy.surfaceFormat(formats.take(2)))
    }

    @Test
    fun SurfaceFormat_FallsBackToTheFirst() {
        val only = listOf(VK_FORMAT_R16G16B16A16_SFLOAT to 1000104002)
        assertEquals(only[0], SwapchainPolicy.surfaceFormat(only))
        assertEquals(VK_FORMAT_B8G8R8A8_UNORM to VK_COLOR_SPACE_SRGB_NONLINEAR_KHR,
            SwapchainPolicy.surfaceFormat(listOf(VK_FORMAT_UNDEFINED to VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)))
    }

    @Test
    fun Extent_UsesTheSurfaceExtentWhenFixed() {
        val e = SwapchainPolicy.extent(intArrayOf(800, 600), intArrayOf(1, 1), intArrayOf(4096, 4096), intArrayOf(1024, 768))
        assertArrayEquals(intArrayOf(800, 600), e)
    }

    @Test
    fun Extent_ClampsTheWindowSizeWhenTheSurfaceLetsItChoose() {
        val any = intArrayOf(SwapchainPolicy.EXTENT_FROM_WINDOW, SwapchainPolicy.EXTENT_FROM_WINDOW)
        assertArrayEquals(intArrayOf(1024, 768), SwapchainPolicy.extent(any, intArrayOf(1, 1), intArrayOf(4096, 4096), intArrayOf(1024, 768)))
        assertArrayEquals(intArrayOf(4096, 1), SwapchainPolicy.extent(any, intArrayOf(1, 1), intArrayOf(4096, 4096), intArrayOf(9000, 0)))
    }

    @Test
    fun Extent_MinimizedWindowIsZero() {
        val e = SwapchainPolicy.extent(intArrayOf(0, 0), intArrayOf(0, 0), intArrayOf(4096, 4096), intArrayOf(0, 0))
        assertTrue(SwapchainPolicy.isMinimized(e[0], e[1]))
        assertFalse(SwapchainPolicy.isMinimized(1, 1))
    }

    @Test
    fun ImageCount_OneAboveMinimumWithinMaximum() {
        assertEquals(3, SwapchainPolicy.imageCount(2, 0))
        assertEquals(3, SwapchainPolicy.imageCount(2, 8))
        assertEquals(2, SwapchainPolicy.imageCount(2, 2))
    }
}
