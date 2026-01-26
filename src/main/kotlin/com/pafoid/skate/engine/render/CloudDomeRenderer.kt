package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.Time
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.scenes.Scene
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

class CloudDomeRenderer(private val shader: Shader, loader: VAOLoader) {

    private val quad: RawModel
    private val noiseBase: Texture
    private val noiseDetail: Texture
    
    private val modelMatrix = Matrix4f()

    init {
        // Large horizontal quad for the sky
        val size = 5000f
        val vertices = floatArrayOf(
            -size, 0f, -size,
             size, 0f, -size,
             size, 0f,  size,
            -size, 0f,  size
        )
        val texCoords = floatArrayOf(
            0f, 0f,
            10f, 0f,
            10f, 10f,
            0f, 10f
        )
        val normals = floatArrayOf(
            0f, -1f, 0f,
            0f, -1f, 0f,
            0f, -1f, 0f,
            0f, -1f, 0f
        )
        val indices = intArrayOf(0, 1, 2, 2, 3, 0)
        
        quad = loader.loadToVAO(vertices, texCoords, normals, indices)
        
        noiseBase = AssetPool.getTexture("assets/textures/clouds/noise_base.png")
        noiseDetail = AssetPool.getTexture("assets/textures/clouds/noise_detail.png")
    }

    fun render(camera: Camera, scene: Scene) {
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_CULL_FACE)
        
        shader.start()
        
        // Position quad high in the sky and center it on camera (scrolling effect)
        modelMatrix.identity().translation(camera.position.x, 200f, camera.position.z)
        
        shader.uploadMat4f("transformationMatrix", modelMatrix)
        shader.uploadMat4f("viewMatrix", camera.createViewMatrix())
        shader.uploadMat4f("projectionMatrix", camera.createProjectionMatrix())
        
        shader.uploadVec3f("uSunDirection", scene.sun.direction)
        shader.uploadVec3f("uSunColor", scene.sun.color)
        shader.uploadVec3f("uSkyColor", scene.skyColor)
        shader.uploadVec3f("uFogColor", scene.fogColor)
        shader.uploadFloat("uFogDensity", scene.fogDensity)
        shader.uploadFloat("uFogGradient", scene.fogGradient)
        shader.uploadVec3f("uCameraPos", camera.position)
        
        // Scrolling offsets
        val time = Time.getTime()
        shader.uploadVec2f("uOffsetBase", Vector2f(time * 0.005f, time * 0.002f))
        shader.uploadVec2f("uOffsetDetail", Vector2f(time * -0.01f, time * 0.008f))

        glActiveTexture(GL_TEXTURE0)
        noiseBase.bind()
        shader.uploadInt("uNoiseBase", 0)
        
        glActiveTexture(GL_TEXTURE1)
        noiseDetail.bind()
        shader.uploadInt("uNoiseDetail", 1)

        glBindVertexArray(quad.vaoId)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        
        glDrawElements(GL_TRIANGLES, quad.vertexCount, GL_UNSIGNED_INT, 0)
        
        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
        glBindVertexArray(0)
        shader.stop()
    }
}
