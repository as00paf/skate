package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.ColorTarget
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.utils.bindTexture
import com.pafoid.skate.engine.render.utils.bindVAO
import com.pafoid.skate.engine.render.utils.unbindVAO
import com.pafoid.skate.engine.render.utils.withBlendState
import com.pafoid.skate.engine.render.utils.withCullFace
import com.pafoid.skate.engine.render.utils.withDepthMask
import com.pafoid.skate.engine.render.utils.withDepthTest
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector2f
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_NEAREST
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBlitFramebuffer

/**
 * Post-processing render pass implementing the post-processing stack.
 *
 * The pass executes the enabled [PostProcessSettings] effects in the canonical
 * [PostEffect.DEFAULT_PIPELINE] order. Effects ping-pong between two scratch
 * colour targets as planned by [PostChain]: no effect samples the texture it
 * draws into (that read/write feedback loop is undefined in GL and produced
 * mirrored, tiled garbage). The final result is copied back into the scene
 * framebuffer's colour attachment, so the viewport keeps showing
 * [FrameBuffer.getTextureId] and the scene depth stays untouched for later
 * passes such as the debug pass.
 *
 * Post-processing needs the scene in a texture, so it is skipped when the scene
 * renders straight to the default framebuffer (`useFbo == false`).
 *
 * The pass can be toggled as a whole (runtime and editor) through the standard
 * render-graph enable/disable mechanism, or effect-by-effect through
 * [toggleEffect] and [updateSettings].
 *
 * @param frameBuffer The framebuffer holding the rendered color/depth buffers.
 * @param useFbo Whether the scene renders into the framebuffer instead of the default framebuffer.
 * @param bloomShader Shader applying the bloom effect.
 * @param tonemapShader Shader applying the ACES tonemapping effect.
 * @param gammaShader Shader applying the gamma correction effect.
 * @param fxaaShader Shader applying the FXAA anti-aliasing effect.
 * @param ssaoShader Shader applying the screen-space ambient occlusion effect.
 * @param vaoLoader Loader used to create the fullscreen quad used by effects.
 * @param settings The post-processing effect state for this pass.
 */
open class PostProcessPass(
    private val frameBuffer: FrameBuffer,
    private val useFbo: Boolean,
    private val bloomShader: Shader,
    private val tonemapShader: Shader,
    private val gammaShader: Shader,
    private val fxaaShader: Shader,
    private val ssaoShader: Shader,
    private val vaoLoader: VAOLoader,
    private var settings: PostProcessSettings = PostProcessSettings(),
) : BaseRenderPass() {

    override val name: String = "PostProcessPass"

    override val description: String = "Applies the post-processing effect stack (bloom, tonemapping, gamma, FXAA, SSAO)"

    override val canDisable: Boolean = true

    private var fullscreenQuad: MeshPart? = null
    private val ping = ColorTarget()
    private val pong = ColorTarget()
    private val texelSize = Vector2f()

    override fun execute(scene: Scene) {
        if (!isEnabled || !useFbo) return

        val effects = resolvedEffects()
        if (effects.isEmpty()) return
        val quad = getFullscreenQuad() ?: return

        val width = frameBuffer.width
        val height = frameBuffer.height
        ping.ensure(width, height)
        pong.ensure(width, height)
        val steps = PostChain.plan(effects.size)

        quad.vaoId.bindVAO(quad.enabledAttributes)
        withDepthTest(false) {
            withDepthMask(false) {
                withBlendState(false) {
                    withCullFace(false) {
                        for (i in effects.indices) {
                            targetOf(steps[i].destination).bind()
                            apply(effects[i], textureOf(steps[i].source), quad)
                        }
                    }
                }
            }
        }
        quad.vaoId.unbindVAO(quad.enabledAttributes)

        // Copy the result back into the scene colour buffer (depth is left as the geometry pass wrote it).
        val result = targetOf(PostChain.resultTarget(effects.size))
        glBindFramebuffer(GL_READ_FRAMEBUFFER, result.fboId)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, frameBuffer.fboId)
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST)
        frameBuffer.bind()
        glViewport(0, 0, width, height)
    }

    private fun targetOf(target: PostChain.Target): ColorTarget = when (target) {
        PostChain.Target.PING -> ping
        PostChain.Target.PONG -> pong
        PostChain.Target.SCENE -> error("post effects never write the scene target")
    }

    private fun textureOf(target: PostChain.Target): Int = when (target) {
        PostChain.Target.SCENE -> frameBuffer.getTextureId()
        PostChain.Target.PING -> ping.textureId
        PostChain.Target.PONG -> pong.textureId
    }

    private fun apply(effect: PostEffect, source: Int, quad: MeshPart) {
        val shader = when (effect) {
            PostEffect.SSAO -> ssaoShader
            PostEffect.BLOOM -> bloomShader
            PostEffect.TONEMAPPING -> tonemapShader
            PostEffect.GAMMA_CORRECTION -> gammaShader
            PostEffect.FXAA -> fxaaShader
        }
        shader.start()
        when (effect) {
            PostEffect.SSAO -> {
                shader.uploadFloat(Uniforms.SSAO_RADIUS, settings.ssaoRadius)
                shader.uploadFloat(Uniforms.SSAO_INTENSITY, settings.ssaoIntensity)
                shader.uploadInt(Uniforms.SSAO_DEPTH_TEXTURE, 1)
                bindTexture(1, frameBuffer.getDepthTextureId())
            }
            PostEffect.BLOOM -> {
                shader.uploadFloat(Uniforms.BLOOM_INTENSITY, settings.bloomIntensity)
                shader.uploadFloat(Uniforms.BLOOM_THRESHOLD, settings.bloomThreshold)
            }
            PostEffect.TONEMAPPING -> shader.uploadFloat(Uniforms.ACES_EXPOSURE, settings.acesExposure)
            PostEffect.GAMMA_CORRECTION -> shader.uploadFloat(Uniforms.GAMMA_VALUE, settings.gammaValue)
            PostEffect.FXAA -> {
                shader.uploadFloat(Uniforms.FXAA_STRENGTH, settings.fxaaStrength)
                shader.uploadVec2f(Uniforms.FXAA_TEXEL_SIZE, texelSize.set(1f / frameBuffer.width, 1f / frameBuffer.height))
            }
        }
        shader.uploadTexture(Uniforms.TEXTURE, 0)
        bindTexture(0, source)
        glDrawElements(GL_TRIANGLES, quad.vertexCount, GL_UNSIGNED_INT, 0)
        EngineStats.drawCalls.incrementAndGet()
        shader.stop()
    }

    /**
     * Returns the effects that should run during the current frame, in
     * canonical pipeline order. Returns an empty list when the pass is disabled.
     */
    fun resolvedEffects(): List<PostEffect> = if (isEnabled) settings.enabledEffects() else emptyList()

    /** Returns the post-processing settings currently used by this pass. */
    fun getSettings(): PostProcessSettings = settings

    /** Replaces the post-processing settings used by this pass. */
    fun updateSettings(next: PostProcessSettings) {
        settings = next
    }

    /** Toggles the given effect on/off and returns the new enabled state. */
    fun toggleEffect(effect: PostEffect): Boolean = settings.toggle(effect)

    /** Returns whether the given effect is currently enabled. */
    fun isEffectEnabled(effect: PostEffect): Boolean = settings.isEnabled(effect)

    fun destroy() {
        fullscreenQuad?.let { vaoLoader.deleteVAO(it.vaoId) }
        fullscreenQuad = null
        ping.destroy()
        pong.destroy()
        bloomShader.destroy()
        tonemapShader.destroy()
        gammaShader.destroy()
        fxaaShader.destroy()
        ssaoShader.destroy()
    }

    private fun getFullscreenQuad(): MeshPart? {
        if (fullscreenQuad == null) {
            fullscreenQuad = vaoLoader.loadToVAO(
                positions = floatArrayOf(
                    -1f, -1f, 0f,
                    1f, -1f, 0f,
                    1f, 1f, 0f,
                    -1f, 1f, 0f
                ),
                textureCoords = floatArrayOf(
                    0f, 0f,
                    1f, 0f,
                    1f, 1f,
                    0f, 1f
                ),
                normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
                indices = intArrayOf(0, 1, 2, 2, 3, 0)
            )
        }
        return fullscreenQuad
    }
}
