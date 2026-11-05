package com.pafoid.skate.engine.render.rhi

/*
 * GL-free value types and descriptors of the render hardware interface (REN-12, docs/design/REN-12.md §3).
 * Descriptors are immutable data classes built once (per pass, material or asset), never per draw.
 */

/** Pixel rectangle; origin is the target's bottom-left on GL (the backend owns any flip, §7.4). */
data class IntRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    init {
        require(width >= 0 && height >= 0) { "IntRect needs a non-negative size, got ${width}x$height" }
    }

    val isEmpty: Boolean get() = width == 0 || height == 0
}

/** Load ops of [RhiCommandList.beginPass]: a null field keeps that attachment's contents. */
data class ClearValues(
    val color: FloatArray? = null,
    val depth: Float? = null,
) {
    init {
        require(color == null || color.size == 4) { "Clear color needs RGBA" }
        require(depth == null || depth in 0f..1f) { "Clear depth is in [0, 1], got $depth" }
    }

    override fun equals(other: Any?): Boolean =
        other is ClearValues && depth == other.depth && (color?.contentEquals(other.color) ?: (other.color == null))

    override fun hashCode(): Int = 31 * (color?.contentHashCode() ?: 0) + (depth?.hashCode() ?: 0)

    companion object {
        val COLOR_AND_DEPTH = ClearValues(floatArrayOf(0f, 0f, 0f, 1f), 1f)
        val DEPTH = ClearValues(depth = 1f)
    }
}

enum class Topology { TRIANGLES, TRIANGLE_STRIP, LINES }

enum class CompareFunc { NEVER, LESS, EQUAL, LESS_EQUAL, GREATER, NOT_EQUAL, GREATER_EQUAL, ALWAYS }

/** Depth test/write state; [clamp] keeps shadow casters behind the near plane (REN-07). */
data class DepthState(
    val test: Boolean = true,
    val write: Boolean = true,
    val func: CompareFunc = CompareFunc.LESS,
    val clamp: Boolean = false,
) {
    companion object {
        val READ_WRITE_LESS = DepthState()
        val READ_ONLY_LESS_EQUAL = DepthState(write = false, func = CompareFunc.LESS_EQUAL)
        val DISABLED = DepthState(test = false, write = false)
    }
}

enum class BlendFactor { ZERO, ONE, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, DST_ALPHA, ONE_MINUS_DST_ALPHA }

/** Separate RGB/alpha blend factors; [enabled] false ignores the factors. */
data class BlendState(
    val enabled: Boolean,
    val srcRgb: BlendFactor = BlendFactor.ONE,
    val dstRgb: BlendFactor = BlendFactor.ZERO,
    val srcAlpha: BlendFactor = BlendFactor.ONE,
    val dstAlpha: BlendFactor = BlendFactor.ZERO,
) {
    companion object {
        val OPAQUE = BlendState(enabled = false)

        /** Classic straight alpha on color and alpha (splash screen over the back buffer). */
        val ALPHA = BlendState(
            true, BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA,
        )

        /** Straight alpha whose target alpha only accumulates, so the target stays opaque (REN-19). */
        val ALPHA_KEEP_TARGET_OPAQUE = BlendState(
            true, BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA,
        )
        /** Additive color whose target alpha stays opaque, as [ALPHA_KEEP_TARGET_OPAQUE] (additive particles). */
        val ADDITIVE = BlendState(true, BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)
    }
}

enum class CullMode { NONE, BACK, FRONT }

data class PolygonOffset(val factor: Float, val units: Float)

enum class BufferUsage { VERTEX, INDEX, UNIFORM, INSTANCE }

/** Update frequency hint: STATIC (once), DYNAMIC (every few frames), STREAM (every frame, rewritten whole). */
enum class BufferUpdate { STATIC, DYNAMIC, STREAM }

data class BufferDesc(val usage: BufferUsage, val size: Long, val update: BufferUpdate = BufferUpdate.STATIC) {
    init {
        require(size > 0) { "Buffer size must be positive, got $size" }
    }
}

enum class TextureType { TEXTURE_2D, TEXTURE_2D_ARRAY, CUBE }

enum class PixelFormat(val bytesPerPixel: Int, val isDepth: Boolean = false) {
    R8(1), RGB8(3), RGBA8(4), RGB32F(12), RGBA16F(8), DEPTH32F(4, isDepth = true)
}

enum class Filter { NEAREST, LINEAR, NEAREST_MIPMAP_LINEAR, LINEAR_MIPMAP_LINEAR }

enum class Wrap { REPEAT, CLAMP_TO_EDGE }

/**
 * Sampler state lives with the texture description; [compare] makes a shadow (depth compare) sampler. [wrap] applies
 * to U (and W), [wrapV] to V (an equirectangular sky repeats around but clamps at the poles).
 */
data class SamplerDesc(
    val minFilter: Filter = Filter.LINEAR,
    val magFilter: Filter = Filter.LINEAR,
    val wrap: Wrap = Wrap.REPEAT,
    val anisotropy: Float = 1f,
    val compare: CompareFunc? = null,
    val wrapV: Wrap = wrap,
)

data class TextureDesc(
    val type: TextureType,
    val format: PixelFormat,
    val width: Int,
    val height: Int,
    val layers: Int = 1,
    val mipLevels: Int = 1,
    val sampler: SamplerDesc = SamplerDesc(),
) {
    init {
        require(width > 0 && height > 0) { "Texture size must be positive, got ${width}x$height" }
        require(layers >= 1 && mipLevels >= 1) { "Texture needs at least one layer and one mip level" }
        require(type == TextureType.TEXTURE_2D_ARRAY || layers == 1 || (type == TextureType.CUBE && layers == 6)) {
            "Only 2D arrays (or cubes with 6 faces) have several layers"
        }
        require(mipLevels <= fullMipChain(width, height)) { "$mipLevels mip levels exceed the ${width}x$height chain" }
    }

    companion object {
        /** Levels of a complete mip chain down to 1x1. */
        fun fullMipChain(width: Int, height: Int): Int = 32 - Integer.numberOfLeadingZeros(maxOf(width, height, 1))
    }
}

/**
 * Color attachments plus an optional depth attachment. A [layered] target's attachments are 2D arrays of [layers]
 * layers and [RhiCommandList.beginPass] picks the layer to render (shadow cascades, spot lights, cube faces).
 * [colorSampler] and [depthSampler] are how the attachments are sampled when read as textures.
 */
data class RenderTargetDesc(
    val width: Int,
    val height: Int,
    val colors: List<PixelFormat> = listOf(PixelFormat.RGBA8),
    val depth: PixelFormat? = PixelFormat.DEPTH32F,
    val layers: Int = 1,
    val layered: Boolean = layers > 1,
    val colorSampler: SamplerDesc = SamplerDesc(),
    val depthSampler: SamplerDesc = SamplerDesc(Filter.NEAREST, Filter.NEAREST),
) {
    init {
        require(width > 0 && height > 0) { "Render target size must be positive, got ${width}x$height" }
        require(colors.none { it.isDepth }) { "Color attachments can't use a depth format" }
        require(depth == null || depth.isDepth) { "Depth attachment needs a depth format" }
        require(colors.isNotEmpty() || depth != null) { "A render target needs at least one attachment" }
        require(layers >= 1 && (layers == 1 || layered)) { "Only a layered target has several layers, got $layers" }
    }

    /** Type of the attachment textures. */
    val textureType: TextureType get() = if (layered) TextureType.TEXTURE_2D_ARRAY else TextureType.TEXTURE_2D

    fun colorTexture(index: Int) = TextureDesc(textureType, colors[index], width, height, layers, sampler = colorSampler)

    fun depthTexture(): TextureDesc? = depth?.let { TextureDesc(textureType, it, width, height, layers, sampler = depthSampler) }
}

/** Preprocessed (REN-05 includes and variant defines applied) stage sources. */
data class ShaderDesc(val name: String, val vertexSource: String, val fragmentSource: String)

enum class ShaderStage { VERTEX, FRAGMENT, LINK }

/** A shader stage failed to compile, or the program to link; [log] is the driver's message. */
class ShaderCompileError(val stage: ShaderStage, val log: String) : RuntimeException("$stage failed: $log")

enum class AttributeType(val components: Int, val isInteger: Boolean = false) {
    FLOAT(1), VEC2(2), VEC3(3), VEC4(4), INT(1, isInteger = true), IVEC4(4, isInteger = true)
}

/** One shader input: [location] matches `layout(location = N)` in the shader. */
data class VertexAttribute(val location: Int, val type: AttributeType, val offset: Int)

/** One bound buffer's layout; [perInstance] advances once per instance (particles). */
data class VertexBinding(val stride: Int, val attributes: List<VertexAttribute>, val perInstance: Boolean = false)

data class VertexLayoutDesc(val bindings: List<VertexBinding>) {
    init {
        val locations = bindings.flatMap { b -> b.attributes.map { it.location } }
        require(locations.size == locations.toSet().size) { "Duplicate attribute locations: $locations" }
    }
}

/** Fixed-function state of a pipeline, without its shader and vertex layout (one per draw kind). */
data class PipelineState(
    val topology: Topology = Topology.TRIANGLES,
    val depth: DepthState = DepthState.READ_WRITE_LESS,
    val blend: BlendState = BlendState.OPAQUE,
    val cull: CullMode = CullMode.BACK,
    val polygonOffset: PolygonOffset? = null,
    /** Width of [Topology.LINES] in pixels (Vulkan: the wideLines feature). */
    val lineWidth: Float = 1f,
) {
    fun desc(shader: RhiShader, layout: RhiVertexLayout) = PipelineDesc(shader, layout, this)
}

/** Immutable pipeline: shader, vertex layout and fixed-function state; created once and reused every frame. */
data class PipelineDesc(
    val shader: RhiShader,
    val layout: RhiVertexLayout,
    val state: PipelineState = PipelineState(),
)
