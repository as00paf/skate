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
        override val nativeHandle = 100 + nextHandle++
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

    val textures = ArrayList<Texture>()
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

    override fun commandList(): RhiCommandList = object : RhiCommandList {
        override fun setUniformBuffer(binding: Int, buffer: RhiBuffer, offset: Long, size: Long) {
            uniformBindings += binding to buffer
        }
    }
}
