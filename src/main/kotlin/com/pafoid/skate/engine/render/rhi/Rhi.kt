package com.pafoid.skate.engine.render.rhi

import java.nio.ByteBuffer

/*
 * Render hardware interface (REN-12, docs/design/REN-12.md). Backend-neutral: nothing in this package may use a
 * graphics API (RhiGlIsolationGuardTest). The OpenGL backend lives in `rhi/opengl/`.
 *
 * Migration status: step 1 (skeleton). The device exposes what the GL backend implements today; resource creation
 * (`createBuffer`, `createTexture`, `createRenderTarget`, `createShader`, `createVertexLayout`, `createPipeline`,
 * `createTimer`, `commandList`, `readPixels`) is added to [RhiDevice] by the step that migrates its callers (2-7).
 * Everything runs on the main thread (§7.6).
 */

/** What the device supports; read once at creation. */
data class RhiCaps(
    val backend: String,
    val renderer: String,
    val version: String,
    val maxTextureSize: Int,
    val maxArrayLayers: Int,
    val maxAnisotropy: Float,
    val timerQueries: Boolean,
)

/** The graphics device of the window's context. */
interface RhiDevice : AutoCloseable {
    val caps: RhiCaps

    /** Start of a frame: forget cached state that code outside the RHI (ImGui) may have changed. */
    fun beginFrame()

    /** End of a frame: releases resources closed during it (deferred deletion, §6). */
    fun endFrame()
}

/** A backend object. [close] releases it once (deferred to [RhiDevice.endFrame]); further calls do nothing. */
interface RhiResource : AutoCloseable

interface RhiBuffer : RhiResource {
    val desc: BufferDesc
    fun update(offset: Long, data: ByteBuffer)
}

interface RhiTexture : RhiResource {
    val desc: TextureDesc
    fun upload(level: Int, layer: Int, data: ByteBuffer?)
    fun generateMips()

    /** Backend handle for ImGui only (GL: texture name), see §7.2. */
    val nativeHandle: Long
}

interface RhiRenderTarget : RhiResource {
    val desc: RenderTargetDesc
    val width: Int
    val height: Int
    fun resize(width: Int, height: Int)
    fun colorTexture(index: Int = 0): RhiTexture
    fun depthTexture(): RhiTexture?
}

/** Named shader parameters (today's `uName` uniforms); the backend uploads only changed values (§3). */
interface ShaderParams {
    fun set(name: String, value: Float)
    fun set(name: String, value: Int)
    fun set(name: String, x: Float, y: Float, z: Float)
    fun set(name: String, x: Float, y: Float, z: Float, w: Float)
    fun setMatrix4(name: String, values: FloatArray)
}

interface RhiShader : RhiResource {
    val desc: ShaderDesc
    fun parameters(): ShaderParams
}

interface RhiVertexLayout : RhiResource {
    val desc: VertexLayoutDesc
}

interface RhiPipeline : RhiResource {
    val desc: PipelineDesc
}

/** Non-blocking GPU timer: results arrive a few frames later; [latestNs] is -1 until then. */
interface RhiTimer : RhiResource {
    val latestNs: Long
}

/** Commands of a frame; immediate on GL, recorded on Vulkan. Passes bracket their draws with begin/endPass. */
interface RhiCommandList {
    /** Null [target] is the backbuffer. Clears per [clear] (load ops), then sets [viewport] (default: whole target). */
    fun beginPass(target: RhiRenderTarget?, layer: Int = 0, clear: ClearValues? = null, viewport: IntRect? = null)
    fun endPass()
    fun setPipeline(pipeline: RhiPipeline)
    fun setVertexBuffer(slot: Int, buffer: RhiBuffer, offset: Long = 0)
    fun setIndexBuffer(buffer: RhiBuffer)
    fun setTexture(slot: Int, texture: RhiTexture)
    fun setUniformBuffer(binding: Int, buffer: RhiBuffer, offset: Long = 0, size: Long = -1)
    fun setParams(params: ShaderParams)
    fun setScissor(rect: IntRect?)
    fun draw(vertexCount: Int, first: Int = 0, instances: Int = 1)
    fun drawIndexed(indexCount: Int, firstIndex: Int = 0, instances: Int = 1)
    fun blit(src: RhiRenderTarget, dst: RhiRenderTarget?, color: Boolean = true, depth: Boolean = false)
    fun beginTimer(timer: RhiTimer)
    fun endTimer(timer: RhiTimer)
}
