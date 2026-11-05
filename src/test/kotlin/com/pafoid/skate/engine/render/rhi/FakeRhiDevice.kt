package com.pafoid.skate.engine.render.rhi

import java.nio.ByteBuffer
import java.nio.FloatBuffer

/** GL-free [RhiDevice] for unit tests: records what was created and closed, in order. */
class FakeRhiDevice : RhiDevice {
    override val caps = RhiCaps("fake", "fake", "0", 4096, 256, 16f, false)
    val events = ArrayList<String>()
    private var nextHandle = 1L

    inner class Buffer(override val usage: BufferUsage, override val update: BufferUpdate, override var size: Long) : RhiBuffer {
        val name = "buf${nextHandle++}"
        var floats: FloatArray? = null
        var ints: IntArray? = null
        var closed = false
        override fun update(offset: Long, data: FloatArray) { events += "update $name" }
        override fun update(offset: Long, data: FloatBuffer) { events += "update $name" }
        override fun update(offset: Long, data: ByteBuffer) { events += "update $name" }
        override fun resize(size: Long) { this.size = size; events += "resize $name" }
        override fun replace(data: FloatArray) { floats = data; size = data.size * 4L; events += "replace $name" }
        override fun replace(data: IntArray) { ints = data; size = data.size * 4L; events += "replace $name" }
        override fun close() { closed = true; events += "close $name" }
    }

    inner class Layout(override val desc: VertexLayoutDesc) : RhiVertexLayout {
        override fun close() {}
    }

    inner class Input(override val layout: RhiVertexLayout, val buffers: List<RhiBuffer>, val indices: RhiBuffer?) : RhiVertexInput {
        val nativeHandle = 100 + nextHandle++
        var closed = false
        override fun close() { closed = true; events += "close vao$nativeHandle" }
    }

    inner class Texture(override val desc: TextureDesc) : RhiTexture {
        override val nativeHandle = 500 + nextHandle++
        override var sampler: SamplerDesc = desc.sampler
            private set
        val uploads = ArrayList<Pair<Int, Int>>()
        var mipsGenerated = false
        var closed = false
        override fun upload(level: Int, layer: Int, data: ByteBuffer?) { uploads += level to layer }
        override fun generateMips() { mipsGenerated = true }
        override fun setSampler(sampler: SamplerDesc) { this.sampler = sampler }
        override fun close() { closed = true; events += "close tex$nativeHandle" }
    }

    inner class Target(override var desc: RenderTargetDesc) : RhiRenderTarget {
        val name = "rt${nextHandle++}"
        private val colors = desc.colors.indices.map { Texture(desc.colorTexture(it)) }
        private val depth = desc.depthTexture()?.let { Texture(it) }
        override val width get() = desc.width
        override val height get() = desc.height
        var closed = false
        override fun resize(width: Int, height: Int) { desc = desc.copy(width = width, height = height); events += "resize $name ${width}x$height" }
        override fun colorTexture(index: Int): RhiTexture = colors[index]
        override fun depthTexture(): RhiTexture? = depth
        override fun close() { closed = true; events += "close $name" }
    }

    inner class Params(val name: String) : ShaderParams {
        override fun set(name: String, value: Float) { events += "${this.name}.$name=$value" }
        override fun set(name: String, value: Int) { events += "${this.name}.$name=$value" }
        override fun set(name: String, x: Float, y: Float) { events += "${this.name}.$name=$x,$y" }
        override fun set(name: String, x: Float, y: Float, z: Float) { events += "${this.name}.$name=$x,$y,$z" }
        override fun set(name: String, x: Float, y: Float, z: Float, w: Float) { events += "${this.name}.$name=$x,$y,$z,$w" }
        override fun set(name: String, values: IntArray) { events += "${this.name}.$name=${values.toList()}" }
        override fun setMatrix4(name: String, values: FloatArray) {
            events += "${this.name}.$name=mat4(12:${values[12]},13:${values[13]},14:${values[14]})"
        }
    }

    inner class FakeShader(override val desc: ShaderDesc) : RhiShader {
        val params = Params(desc.name)
        val blocks = HashMap<String, Int>()
        var closed = false
        override fun parameters(): ShaderParams = params
        override fun bindUniformBlock(blockName: String, binding: Int): Boolean { blocks[blockName] = binding; return true }
        override fun close() { closed = true; events += "close shader ${desc.name}" }
    }

    inner class Pipeline(override val desc: PipelineDesc) : RhiPipeline {
        var closed = false
        override fun close() { closed = true }
    }

    val pipelines = ArrayList<Pipeline>()
    val textures = ArrayList<Texture>()
    val targets = ArrayList<Target>()
    val shaders = ArrayList<FakeShader>()

    /** Thrown by the next [createShader] when set. */
    var shaderError: ShaderCompileError? = null

    /** Value [readPixels] writes into every byte (or float, for float formats). */
    var readbackValue = 0f
    val layoutsCreated = ArrayList<Layout>()
    val inputs = ArrayList<Input>()
    val uniformBindings = ArrayList<Pair<Int, RhiBuffer>>()

    override fun beginFrame() {}
    override fun endFrame() {}
    override fun close() {}

    override fun createBuffer(desc: BufferDesc): RhiBuffer = Buffer(desc.usage, desc.update, desc.size)
    override fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: FloatArray): RhiBuffer =
        Buffer(usage, update, data.size * 4L).also { it.floats = data }
    override fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: IntArray): RhiBuffer =
        Buffer(usage, update, data.size * 4L).also { it.ints = data }
    override fun createVertexLayout(desc: VertexLayoutDesc): RhiVertexLayout = Layout(desc).also { layoutsCreated += it }
    override fun createVertexInput(layout: RhiVertexLayout, vertexBuffers: List<RhiBuffer>, indexBuffer: RhiBuffer?): RhiVertexInput =
        Input(layout, vertexBuffers, indexBuffer).also { inputs += it }

    override fun createTexture(desc: TextureDesc): RhiTexture = Texture(desc).also { textures += it }

    override fun createShader(desc: ShaderDesc): RhiShader {
        shaderError?.let { shaderError = null; throw it }
        return FakeShader(desc).also { shaders += it }
    }

    override fun createPipeline(desc: PipelineDesc): RhiPipeline = Pipeline(desc).also { pipelines += it }

    override fun createRenderTarget(desc: RenderTargetDesc): RhiRenderTarget = Target(desc).also { targets += it }

    override fun readPixels(target: RhiRenderTarget?, attachment: Int, rect: IntRect, format: PixelFormat, out: ByteBuffer) {
        events += "read ${(target as Target?)?.name ?: "backbuffer"} $rect $format"
        if (format == PixelFormat.RGB32F) repeat(rect.width * rect.height * 3) { out.putFloat(it * 4, readbackValue) }
    }

    override fun commandList(): RhiCommandList = object : RhiCommandList {
        override fun setParams(params: ShaderParams) { events += "use ${(params as? Params)?.name}" }
        override fun setPipeline(pipeline: RhiPipeline) { events += "pipeline ${pipeline.desc.state.topology}" }
        override fun setVertexInput(input: RhiVertexInput) { events += "input" }
        override fun setTexture(slot: Int, texture: RhiTexture?) { events += "texture $slot ${texture?.let { (it as Texture).nativeHandle }}" }
        override fun setScissor(rect: IntRect?) { events += "scissor $rect" }
        override fun draw(vertexCount: Int, firstVertex: Int, instances: Int) { events += "draw $vertexCount from $firstVertex x$instances" }
        override fun drawIndexed(indexCount: Int, firstIndex: Int, instances: Int) { events += "drawIndexed $indexCount from $firstIndex x$instances" }

        override fun setUniformBuffer(binding: Int, buffer: RhiBuffer, offset: Long, size: Long) {
            uniformBindings += binding to buffer
        }

        override fun beginPass(target: RhiRenderTarget?, layer: Int, clear: ClearValues?, viewport: IntRect?) {
            events += "begin ${(target as Target?)?.name ?: "backbuffer"} layer=$layer clear=${clear != null} viewport=$viewport"
        }

        override fun endPass() { events += "end" }

        override fun blit(src: RhiRenderTarget, dst: RhiRenderTarget?, srcRect: IntRect?, dstRect: IntRect?, filter: Filter) {
            events += "blit ${(src as Target).name} -> ${(dst as Target?)?.name ?: "backbuffer"} $filter"
        }
    }
}
