package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.render.fog.FogUniforms
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.getAllComponents
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.particles.ParticleSimulator
import com.pafoid.skate.engine.render.rhi.opengl.GLStateTracker
import com.pafoid.skate.engine.render.utils.withBlendState
import com.pafoid.skate.engine.render.utils.withCullFace
import com.pafoid.skate.engine.render.utils.withDepthMask
import com.pafoid.skate.engine.render.utils.withDepthTest
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL31.glDrawArraysInstanced
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

/**
 * Draws every live [ParticleEmitter] as camera-facing quads with one instanced draw per emitter, on top of the
 * geometry pass output (depth-tested against it, without writing depth). Runs before post-processing.
 *
 * @param textureId resolves an emitter's `texturePath` to a GL texture id (results are cached per path)
 */
class ParticlePass(
    private val shader: Shader,
    private val frameBuffer: FrameBuffer,
    private val useFbo: Boolean,
    private val cameraManager: CameraManager,
    private val textureId: (String) -> Int,
) : BaseRenderPass() {

    override val name: String = "ParticlePass"
    override val description: String = "Renders particle emitters as instanced billboards"
    override val inputs: Set<String> = setOf("SceneColor")
    override val outputs: Set<String> = setOf("SceneColor")

    private var input: RhiVertexInput? = null
    private var quadVbo: RhiBuffer? = null
    private var instanceVbo: RhiBuffer? = null
    private val vao: Int get() = input?.nativeHandle?.toInt() ?: 0
    private var instanceCapacity = 0
    private var instanceData: FloatBuffer? = null
    private val textureIds = HashMap<String, Int>()
    private val color = Vector4f()
    private val uv = FloatArray(4)

    private val cameraPosition = org.joml.Vector3f()

    override fun execute(scene: Scene) {
        val emitters = scene.getAllComponents<ParticleEmitter>()
            .filter { it.enabled && it.runtime.pool.count > 0 && it.gameObject?.isVisible == true }
        if (emitters.isEmpty()) return

        val cmd = RhiContext.require().commandList()
        cmd.beginPass(if (useFbo) frameBuffer.target else null, viewport = presentedViewport.of(frameBuffer, useFbo))
        if (vao == 0) createBuffers()

        val camera = cameraManager.camera
        shader.start()
        shader.uploadMat4f("uProjection", camera.projection)
        shader.uploadMat4f("uView", camera.view)
        shader.uploadInt("uTexture", 0)
        FogUniforms.upload(shader, scene.getComponent<EnvironmentComponent>()?.takeIf { it.enabled })
        shader.uploadVec3f(Uniforms.CAMERA_POSITION, cameraManager.transform.worldMatrix.getTranslation(cameraPosition))

        withDepthTest(true) {
            withDepthMask(false) {
                withBlendState(true) {
                    withCullFace(false) {
                        glBindVertexArray(vao)
                        emitters.forEach { draw(it) }
                        glBindVertexArray(0)
                        GLStateTracker.setBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
                    }
                }
            }
        }
        shader.stop()
        cmd.endPass()
    }

    private val presentedViewport = PresentedViewport()

    private fun draw(emitter: ParticleEmitter) {
        val pool = emitter.runtime.pool
        val count = pool.count
        val buffer = instanceBuffer(count)
        buffer.clear()
        for (i in 0 until count) {
            val t = pool.normalizedAge(i)
            ParticleSimulator.colorAt(emitter, t, color)
            ParticleSimulator.uvRect(emitter, ParticleSimulator.frameAt(emitter, t), uv)
            buffer.put(pool.posX[i]).put(pool.posY[i]).put(pool.posZ[i]).put(ParticleSimulator.sizeAt(emitter, t))
            buffer.put(color.x).put(color.y).put(color.z).put(color.w)
            buffer.put(uv)
        }
        buffer.flip()
        instanceVbo!!.update(0, buffer)

        val tex = emitter.texturePath.takeIf { it.isNotBlank() }
            ?.let { path -> textureIds.getOrPut(path) { textureId(path) } } ?: 0
        shader.uploadInt("uHasTexture", if (tex > 0) 1 else 0)
        shader.uploadInt("uAdditive", if (emitter.additive) 1 else 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, tex)

        // Alpha uses (ONE, ONE_MINUS_SRC_ALPHA) so the opaque scene target keeps alpha 1 (the editor composites it).
        GLStateTracker.setBlendFuncSeparate(
            GL_SRC_ALPHA, if (emitter.additive) GL_ONE else GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA,
        )
        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, count)
        EngineStats.drawCalls.incrementAndGet()
    }

    /** Grows the CPU staging buffer and the GPU instance buffer to hold at least [count] instances. */
    private fun instanceBuffer(count: Int): FloatBuffer {
        val current = instanceData
        if (current != null && count <= instanceCapacity) return current
        val capacity = maxOf(count, instanceCapacity * 2, 256)
        current?.let { MemoryUtil.memFree(it) }
        val buffer = MemoryUtil.memAllocFloat(capacity * FLOATS_PER_INSTANCE)
        instanceData = buffer
        instanceCapacity = capacity
        instanceVbo!!.resize(capacity.toLong() * FLOATS_PER_INSTANCE * Float.SIZE_BYTES)
        return buffer
    }

    private fun createBuffers() {
        val device = RhiContext.require()
        val quad = device.createBuffer(
            BufferUsage.VERTEX, BufferUpdate.STATIC, floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f),
        )
        // Sized by the first draw (instanceBuffer); the vertex input only needs the buffer object
        val instances = device.createBuffer(BufferUsage.INSTANCE, BufferUpdate.STREAM, FloatArray(0))
        quadVbo = quad
        instanceVbo = instances
        input = device.createVertexInput(device.createVertexLayout(VertexLayouts.PARTICLES), listOf(quad, instances))
    }

    fun destroy() {
        input?.close()
        quadVbo?.close()
        instanceVbo?.close()
        input = null; quadVbo = null; instanceVbo = null; instanceCapacity = 0
        instanceData?.let { MemoryUtil.memFree(it) }
        instanceData = null
        textureIds.clear()
    }

    private companion object {
        const val FLOATS_PER_INSTANCE = VertexLayouts.PARTICLE_FLOATS_PER_INSTANCE
    }
}
