package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.CubeMap
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.render.CameraComponent
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.lwjgl.opengl.GL11.GL_LEQUAL
import org.lwjgl.opengl.GL11.GL_LESS
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.glDepthFunc
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL20.glDisableVertexAttribArray
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays

class SkyboxRenderer(private val shader: Shader, loader: VAOLoader) {

    private val cube: MeshPart = loader.loadToVAO(VERTICES, 3)

    fun render(camera: CameraComponent, cubeMap: CubeMap) {
        shader.start()
        shader.uploadMat4f(Uniforms.VIEW_MATRIX, camera.createViewMatrix())
        shader.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.createProjectionMatrix())

        glBindVertexArray(cube.vaoId)
        glEnableVertexAttribArray(0)

        cubeMap.bind()

        // Depth test should be less or equal to pass since skybox is at max depth
        glDepthFunc(GL_LEQUAL)
        glDrawArrays(GL_TRIANGLES, 0, cube.vertexCount)
        glDepthFunc(GL_LESS) // Reset to default

        glDisableVertexAttribArray(0)
        glBindVertexArray(0)
        shader.stop()
    }

    fun destroy() {
        if (cube.vaoId != 0) {
            glDeleteVertexArrays(cube.vaoId)
        }
    }
}

private const val SIZE = 500f
private val VERTICES = floatArrayOf(
    -SIZE,  SIZE, -SIZE,
    -SIZE, -SIZE, -SIZE,
    SIZE, -SIZE, -SIZE,
    SIZE, -SIZE, -SIZE,
    SIZE,  SIZE, -SIZE,
    -SIZE,  SIZE, -SIZE,

    -SIZE, -SIZE,  SIZE,
    -SIZE, -SIZE, -SIZE,
    -SIZE,  SIZE, -SIZE,
    -SIZE,  SIZE, -SIZE,
    -SIZE,  SIZE,  SIZE,
    -SIZE, -SIZE,  SIZE,

    SIZE, -SIZE, -SIZE,
    SIZE, -SIZE,  SIZE,
    SIZE,  SIZE,  SIZE,
    SIZE,  SIZE,  SIZE,
    SIZE,  SIZE, -SIZE,
    SIZE, -SIZE, -SIZE,

    -SIZE, -SIZE,  SIZE,
    -SIZE,  SIZE,  SIZE,
    SIZE,  SIZE,  SIZE,
    SIZE,  SIZE,  SIZE,
    SIZE, -SIZE,  SIZE,
    -SIZE, -SIZE,  SIZE,

    -SIZE,  SIZE, -SIZE,
    SIZE,  SIZE, -SIZE,
    SIZE,  SIZE,  SIZE,
    SIZE,  SIZE,  SIZE,
    -SIZE,  SIZE,  SIZE,
    -SIZE,  SIZE, -SIZE,

    -SIZE, -SIZE, -SIZE,
    -SIZE, -SIZE,  SIZE,
    SIZE, -SIZE, -SIZE,
    SIZE, -SIZE, -SIZE,
    -SIZE, -SIZE,  SIZE,
    SIZE, -SIZE,  SIZE
)