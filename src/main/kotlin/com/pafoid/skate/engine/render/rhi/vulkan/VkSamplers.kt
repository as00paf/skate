package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.SamplerDesc
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkSamplerCreateInfo

/**
 * `VkSampler` objects from RHI [SamplerDesc]s (VK-02), shared by every texture with the same state: a scene has a
 * handful of distinct samplers, so textures never own one. Destroyed with the device.
 */
internal class VkSamplerCache(private val core: VkCore) : AutoCloseable {
    private val samplers = HashMap<SamplerDesc, Long>()

    /** Distinct samplers created so far. */
    val size: Int get() = samplers.size

    fun sampler(desc: SamplerDesc): Long = samplers.getOrPut(desc) { create(desc) }

    private fun create(s: SamplerDesc): Long = stackPush().use { st ->
        val info = VkSamplerCreateInfo.calloc(st).`sType$Default`()
        SamplerState.of(s, core.anisotropy, core.maxAnisotropy).apply(info)
        val p = st.mallocLong(1)
        vk(vkCreateSampler(core.device, info, null, p), "vkCreateSampler")
        p[0]
    }

    override fun close() {
        samplers.values.forEach { vkDestroySampler(core.device, it, null) }
        samplers.clear()
    }
}

/**
 * The Vulkan sampler fields of a [SamplerDesc] (pure, unit-tested). Non-mipmapped filters clamp the LOD to level 0
 * (GL's `GL_LINEAR`/`GL_NEAREST` minification ignores mips); anisotropy applies only when the device supports it.
 */
data class SamplerState(
    val magFilter: Int,
    val minFilter: Int,
    val mipmapMode: Int,
    val addressU: Int,
    val addressV: Int,
    val addressW: Int,
    val anisotropy: Float?,
    val compareOp: Int?,
    val maxLod: Float,
) {
    fun apply(info: VkSamplerCreateInfo) {
        info.magFilter(magFilter).minFilter(minFilter).mipmapMode(mipmapMode)
            .addressModeU(addressU).addressModeV(addressV).addressModeW(addressW)
            .anisotropyEnable(anisotropy != null).maxAnisotropy(anisotropy ?: 1f)
            .compareEnable(compareOp != null).compareOp(compareOp ?: VK_COMPARE_OP_ALWAYS)
            .minLod(0f).maxLod(maxLod).borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK)
    }

    companion object {
        /** No mip clamp: every level the image has. */
        const val ALL_LEVELS = VK_LOD_CLAMP_NONE

        fun of(s: SamplerDesc, anisotropySupported: Boolean, maxAnisotropy: Float) = SamplerState(
            magFilter = VkTranslate.filter(s.magFilter),
            minFilter = VkTranslate.filter(s.minFilter),
            mipmapMode = VkTranslate.mipmapMode(s.minFilter),
            addressU = VkTranslate.addressMode(s.wrap),
            addressV = VkTranslate.addressMode(s.wrapV),
            addressW = VkTranslate.addressMode(s.wrap),
            anisotropy = if (anisotropySupported && s.anisotropy > 1f) minOf(s.anisotropy, maxAnisotropy) else null,
            compareOp = s.compare?.let { VkTranslate.compareOp(it) },
            maxLod = if (VkTranslate.usesMips(s.minFilter)) ALL_LEVELS else 0f,
        )
    }
}
