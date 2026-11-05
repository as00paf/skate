package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.utils.ShaderConst

/**
 * Specialized renderer for the splash screen.
 * Handles the rendering of a full-screen quad with a splash shader and texture.
 */
class SplashRenderer(private val vaoLoader: VAOLoader) {

    private var quad: MeshPart? = null

    /**
     * Initializes the splash screen quad.
     */
    fun initialize() {
        if (quad != null) return

        quad = vaoLoader.loadToVAO(
            positions = floatArrayOf(
                -1f, -1f, 0f,
                1f, -1f, 0f,
                1f,  1f, 0f,
                -1f,  1f, 0f
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

    /**
     * Renders the splash screen quad.
     *
     * @param shader The splash shader
     * @param texture The splash texture
     * @param progress The loading progress (0.0 to 1.0)
     * @param alpha The transparency of the splash screen (0.0 to 1.0)
     */
    fun render(shader: Shader, texture: Texture, progress: Float, alpha: Float) {
        val q = quad ?: return
        val input = q.input ?: return

        shader.start()
        shader.uploadFloat(ShaderConst.Uniforms.PROGRESS, progress)
        shader.uploadFloat(ShaderConst.Uniforms.ALPHA, alpha)

        val cmd = RhiContext.require().commandList()
        cmd.setTexture(0, texture.rhi)
        shader.uploadInt(ShaderConst.Uniforms.TEXTURE, 0)

        // No depth, straight alpha over whatever is on screen ([PipelineStates.SPLASH])
        cmd.setPipeline(pipelines.get(shader, input.layout) ?: return)
        cmd.setVertexInput(input)
        cmd.drawIndexed(q.vertexCount)
        shader.stop()
    }

    private val pipelines = PipelineCache(PipelineStates.SPLASH)

    /**
     * Cleans up the splash renderer resources.
     */
    fun destroy() {
        quad?.let {
            vaoLoader.deleteVAO(it.vaoId)
        }
        quad = null
        pipelines.close()
    }
}
