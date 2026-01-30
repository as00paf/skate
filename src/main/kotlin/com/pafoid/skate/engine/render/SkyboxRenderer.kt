package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.CubeMap
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.ShaderConst
import com.pafoid.skate.engine.assets.ShaderConst.Uniforms
import com.pafoid.skate.engine.models.RawModel
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.glDisableVertexAttribArray
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL30.glBindVertexArray

class SkyboxRenderer(private val shader: Shader, loader: VAOLoader) {

    private val cube: RawModel = loader.loadToVAO(VERTICES, 3)

    fun render(camera: Camera, cubeMap: CubeMap) {
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