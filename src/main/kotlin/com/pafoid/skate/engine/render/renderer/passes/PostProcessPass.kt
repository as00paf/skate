package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.utils.GLStateTracker
import com.pafoid.skate.engine.render.utils.bindTexture
import com.pafoid.skate.engine.render.utils.bindVAO
import com.pafoid.skate.engine.render.utils.unbindVAO
import com.pafoid.skate.engine.render.utils.withBlendState
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector2f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER

/**
 * Post-processing render pass implementing the post-processing stack.
 *
 * The pass executes the enabled [PostProcessSettings] effects in the canonical
 * [PostEffect.DEFAULT_PIPELINE] order. Each enabled effect renders a fullscreen
 * quad over the rendered color buffer (the scene framebuffer, or the default
 * framebuffer when FBO rendering is disabled).
 *
 * The pass can be toggled as a whole (runtime and editor) through the standard
 * render-graph enable/disable mechanism, or effect-by-effect through
 * [toggleEffect] and [updateSettings].
 *
 * @param frameBuffer The framebuffer holding the rendered color/depth buffers.
 * @param useFbo Whether the scene renders into the framebuffer (HDR target)
 * instead of the default framebuffer.
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

    override fun execute(scene: Scene) {
        if (!isEnabled) return

        val effects = resolvedEffects()
        if (effects.isEmpty()) return

        for (effect in effects) {
            when (effect) {
                PostEffect.SSAO -> applySsaO()
                PostEffect.BLOOM -> applyBloom()
                PostEffect.TONEMAPPING -> applyTonemapping()
                PostEffect.GAMMA_CORRECTION -> applyGammaCorrection()
                PostEffect.FXAA -> applyFxaa()
            }
        }
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

    private fun bindQuad(): Boolean {
        val quad = getFullscreenQuad() ?: return false
        quad.vaoId.bindVAO(quad.enabledAttributes)
        glDisable(GL11.GL_DEPTH_TEST)
        return true
    }

    private fun unbindQuad() {
        val quad = getFullscreenQuad() ?: return
        quad.vaoId.unbindVAO(quad.enabledAttributes)
        glEnable(GL11.GL_DEPTH_TEST)
    }

    private fun drawQuad() {
        val quad = getFullscreenQuad() ?: return
        GL11.glDrawElements(GL11.GL_TRIANGLES, quad.vertexCount, GL11.GL_UNSIGNED_INT, 0)
        EngineStats.drawCalls.incrementAndGet()
    }

    private fun bindTarget() {
        if (useFbo) frameBuffer.bind() else GL30.glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    private fun unbindTarget() {
        if (useFbo) frameBuffer.unbind() else GL30.glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    private fun applyBloom() {
        if (!settings.bloomEnabled) return
        val shader = bloomShader ?: return
        bindTarget()
        if (!bindQuad()) {
            unbindTarget()
            return
        }
        withBlendState(true) {
            GLStateTracker.setBlendFunc(GL11.GL_ONE, GL11.GL_ZERO)
            shader.start()
            shader.uploadFloat(Uniforms.BLOOM_INTENSITY, settings.bloomIntensity)
            shader.uploadFloat(Uniforms.BLOOM_THRESHOLD, settings.bloomThreshold)
            shader.uploadTexture(Uniforms.TEXTURE, 0)
            bindTexture(0, frameBuffer.getTextureId())
            drawQuad()
        }
        shader.stop()
        unbindQuad()
        unbindTarget()
    }

    private fun applyTonemapping() {
        if (!settings.tonemappingEnabled) return
        val shader = tonemapShader ?: return
        bindTarget()
        if (!bindQuad()) {
            unbindTarget()
            return
        }
        withBlendState(false) {
            shader.start()
            shader.uploadFloat(Uniforms.ACES_EXPOSURE, settings.acesExposure)
            shader.uploadTexture(Uniforms.TEXTURE, 0)
            bindTexture(0, frameBuffer.getTextureId())
            drawQuad()
        }
        shader.stop()
        unbindQuad()
        unbindTarget()
    }

    private fun applyGammaCorrection() {
        if (!settings.gammaCorrectionEnabled) return
        val shader = gammaShader ?: return
        bindTarget()
        if (!bindQuad()) {
            unbindTarget()
            return
        }
        withBlendState(false) {
            shader.start()
            shader.uploadFloat(Uniforms.GAMMA_VALUE, settings.gammaValue)
            shader.uploadTexture(Uniforms.TEXTURE, 0)
            bindTexture(0, frameBuffer.getTextureId())
            drawQuad()
        }
        shader.stop()
        unbindQuad()
        unbindTarget()
    }

    private fun applyFxaa() {
        if (!settings.fxaaEnabled) return
        val shader = fxaaShader ?: return
        bindTarget()
        if (!bindQuad()) {
            unbindTarget()
            return
        }
        withBlendState(false) {
            shader.start()
            shader.uploadFloat(Uniforms.FXAA_STRENGTH, settings.fxaaStrength)
            shader.uploadVec2f(Uniforms.FXAA_TEXEL_SIZE, Vector2f(1f / frameBuffer.width, 1f / frameBuffer.height))
            shader.uploadTexture(Uniforms.TEXTURE, 0)
            bindTexture(0, frameBuffer.getTextureId())
            drawQuad()
        }
        shader.stop()
        unbindQuad()
        unbindTarget()
    }

    private fun applySsaO() {
        if (!settings.ssaoEnabled) return
        val shader = ssaoShader ?: return
        bindTarget()
        if (!bindQuad()) {
            unbindTarget()
            return
        }
        withBlendState(false) {
            shader.start()
            shader.uploadFloat(Uniforms.SSAO_RADIUS, settings.ssaoRadius)
            shader.uploadFloat(Uniforms.SSAO_INTENSITY, settings.ssaoIntensity)
            shader.uploadInt(Uniforms.SSAO_DEPTH_TEXTURE, 1)
            shader.uploadTexture(Uniforms.TEXTURE, 0)
            bindTexture(0, frameBuffer.getTextureId())
            bindTexture(1, frameBuffer.getDepthTextureId())
            drawQuad()
        }
        shader.stop()
        unbindQuad()
        unbindTarget()
    }
}
