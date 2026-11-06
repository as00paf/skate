package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.AttributeType
import com.pafoid.skate.engine.render.rhi.BlendFactor
import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.CullMode
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.Topology
import com.pafoid.skate.engine.render.rhi.Wrap
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_DST_ALPHA
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA
import org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_ALWAYS
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_EQUAL
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_GREATER
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_GREATER_OR_EQUAL
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_NEVER
import org.lwjgl.vulkan.VK10.VK_COMPARE_OP_NOT_EQUAL
import org.lwjgl.vulkan.VK10.VK_CULL_MODE_BACK_BIT
import org.lwjgl.vulkan.VK10.VK_CULL_MODE_FRONT_BIT
import org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE
import org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR
import org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST
import org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16B16A16_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SINT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SINT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM
import org.lwjgl.vulkan.VK10.VK_FORMAT_R8_UNORM
import org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST
import org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
import org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP
import org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
import org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT
import org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR
import org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST

/**
 * RHI value types -> Vulkan enums (REN-13 spike). Pure lookups on compile-time constants: unit tests need no Vulkan.
 */
object VkTranslate {
    fun compareOp(func: CompareFunc): Int = when (func) {
        CompareFunc.NEVER -> VK_COMPARE_OP_NEVER
        CompareFunc.LESS -> VK_COMPARE_OP_LESS
        CompareFunc.EQUAL -> VK_COMPARE_OP_EQUAL
        CompareFunc.LESS_EQUAL -> VK_COMPARE_OP_LESS_OR_EQUAL
        CompareFunc.GREATER -> VK_COMPARE_OP_GREATER
        CompareFunc.NOT_EQUAL -> VK_COMPARE_OP_NOT_EQUAL
        CompareFunc.GREATER_EQUAL -> VK_COMPARE_OP_GREATER_OR_EQUAL
        CompareFunc.ALWAYS -> VK_COMPARE_OP_ALWAYS
    }

    fun blendFactor(factor: BlendFactor): Int = when (factor) {
        BlendFactor.ZERO -> VK_BLEND_FACTOR_ZERO
        BlendFactor.ONE -> VK_BLEND_FACTOR_ONE
        BlendFactor.SRC_ALPHA -> VK_BLEND_FACTOR_SRC_ALPHA
        BlendFactor.ONE_MINUS_SRC_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
        BlendFactor.DST_ALPHA -> VK_BLEND_FACTOR_DST_ALPHA
        BlendFactor.ONE_MINUS_DST_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA
    }

    fun cullMode(cull: CullMode): Int = when (cull) {
        CullMode.NONE -> VK_CULL_MODE_NONE
        CullMode.BACK -> VK_CULL_MODE_BACK_BIT
        CullMode.FRONT -> VK_CULL_MODE_FRONT_BIT
    }

    fun topology(topology: Topology): Int = when (topology) {
        Topology.TRIANGLES -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
        Topology.TRIANGLE_STRIP -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP
        Topology.LINES -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST
    }

    fun attributeFormat(type: AttributeType): Int = when (type) {
        AttributeType.FLOAT -> VK_FORMAT_R32_SFLOAT
        AttributeType.VEC2 -> VK_FORMAT_R32G32_SFLOAT
        AttributeType.VEC3 -> VK_FORMAT_R32G32B32_SFLOAT
        AttributeType.VEC4 -> VK_FORMAT_R32G32B32A32_SFLOAT
        AttributeType.INT -> VK_FORMAT_R32_SINT
        AttributeType.IVEC4 -> VK_FORMAT_R32G32B32A32_SINT
    }

    /**
     * Image format of a [PixelFormat]. Three-channel formats are widened to four (optimal-tiling RGB8 and RGB32F are
     * rarely sampleable or renderable); [widenedFrom] says how to expand uploads.
     */
    fun imageFormat(format: PixelFormat): Int = when (format) {
        PixelFormat.R8 -> VK_FORMAT_R8_UNORM
        PixelFormat.RGB8, PixelFormat.RGBA8 -> VK_FORMAT_R8G8B8A8_UNORM
        PixelFormat.RGB32F -> VK_FORMAT_R32G32B32A32_SFLOAT
        PixelFormat.RGBA16F -> VK_FORMAT_R16G16B16A16_SFLOAT
        PixelFormat.DEPTH32F -> VK_FORMAT_D32_SFLOAT
    }

    /** Bytes per texel of the Vulkan image holding [format] (after widening). */
    fun imageTexelSize(format: PixelFormat): Int = when (format) {
        PixelFormat.RGB8 -> 4
        PixelFormat.RGB32F -> 16
        else -> format.bytesPerPixel
    }

    /**
     * Widens tightly packed 3-channel texels ([channelBytes] each) to 4 channels, filling alpha with [alpha]
     * (opaque: 0xFF for bytes, the bits of 1.0f for floats).
     */
    fun widenToFour(src: ByteArray, channelBytes: Int, alpha: ByteArray): ByteArray {
        val texels = src.size / (3 * channelBytes)
        val dst = ByteArray(texels * 4 * channelBytes)
        for (t in 0 until texels) {
            System.arraycopy(src, t * 3 * channelBytes, dst, t * 4 * channelBytes, 3 * channelBytes)
            System.arraycopy(alpha, 0, dst, t * 4 * channelBytes + 3 * channelBytes, channelBytes)
        }
        return dst
    }

    fun filter(filter: Filter): Int = when (filter) {
        Filter.NEAREST, Filter.NEAREST_MIPMAP_LINEAR -> VK_FILTER_NEAREST
        Filter.LINEAR, Filter.LINEAR_MIPMAP_LINEAR -> VK_FILTER_LINEAR
    }

    /** Mip mode of a minification filter; non-mipmapped filters clamp to level 0 through the sampler's max LOD. */
    fun mipmapMode(filter: Filter): Int = when (filter) {
        Filter.NEAREST_MIPMAP_LINEAR, Filter.LINEAR_MIPMAP_LINEAR -> VK_SAMPLER_MIPMAP_MODE_LINEAR
        else -> VK_SAMPLER_MIPMAP_MODE_NEAREST
    }

    fun usesMips(filter: Filter) = filter == Filter.NEAREST_MIPMAP_LINEAR || filter == Filter.LINEAR_MIPMAP_LINEAR

    fun addressMode(wrap: Wrap): Int = when (wrap) {
        Wrap.REPEAT -> VK_SAMPLER_ADDRESS_MODE_REPEAT
        Wrap.CLAMP_TO_EDGE -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
    }
}
