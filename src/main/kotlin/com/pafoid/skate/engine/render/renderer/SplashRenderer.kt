package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.RawModel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.ShaderConst
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Specialized renderer for the splash screen.
 * Handles the rendering of a full-screen quad with a splash shader and texture.
 */
class SplashRenderer(private val vaoLoader: VAOLoader) {

    private var quad: RawModel? = null

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

        shader.start()
        shader.uploadFloat(ShaderConst.Uniforms.PROGRESS, progress)
        shader.uploadFloat(ShaderConst.Uniforms.ALPHA, alpha)

        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        texture.bind()
        shader.uploadInt(ShaderConst.Uniforms.TEXTURE, 0)

        GL30.glBindVertexArray(q.vaoId)
        GL20.glEnableVertexAttribArray(0)
        GL20.glEnableVertexAttribArray(1)

        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        GL11.glDrawElements(GL11.GL_TRIANGLES, q.vertexCount, GL11.GL_UNSIGNED_INT, 0)

        GL20.glDisableVertexAttribArray(0)
        GL20.glDisableVertexAttribArray(1)
        GL30.glBindVertexArray(0)

        texture.unbind()
        shader.stop()
        GL11.glDisable(GL11.GL_BLEND)
    }

    /**
     * Cleans up the splash renderer resources.
     */
    fun destroy() {
        quad?.let {
            vaoLoader.deleteVAO(it.vaoId)
        }
        quad = null
    }
}
