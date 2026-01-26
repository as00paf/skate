package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.Time
import com.pafoid.skate.engine.utils.NoiseGenerator
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

class VolumetricCloudRenderer(private val shader: Shader, loader: VAOLoader) {

    private val quad: RawModel
    private val noiseTexture: Texture

    init {
        val vertices = floatArrayOf(
            -1f,  1f, 0f,
            -1f, -1f, 0f,
             1f, -1f, 0f,
             1f,  1f, 0f
        )
        quad = loader.loadToVAO(vertices, 3)
        
        val noiseData = NoiseGenerator.generateCloudNoise(32)
        noiseTexture = Texture().init3D(32, 32, 32, noiseData)
    }

    fun render(camera: Camera, scene: Scene, depthTextureId: Int) {
        glDisable(GL_CULL_FACE)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        
        shader.start()
        
        // Matrices for ray direction reconstruction
        shader.uploadMat4f("projectionMatrix", camera.createProjectionMatrix())
        shader.uploadMat4f("viewMatrix", camera.createViewMatrix())
        
        shader.uploadVec3f("uCameraPos", camera.position)
        shader.uploadVec3f("uSunDirection", scene.sun.direction)
        shader.uploadVec3f("uSunColor", scene.sun.color)
        shader.uploadVec3f("uSkyColor", scene.skyColor)
        shader.uploadFloat("uTime", Time.getTime())
        
        shader.uploadFloat("uFogDensity", scene.fogDensity)
        shader.uploadFloat("uFogGradient", scene.fogGradient)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_3D, noiseTexture.getId())
        shader.uploadInt("uNoiseTexture", 0)
        
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, depthTextureId)
        shader.uploadInt("uDepthTexture", 1)

        glBindVertexArray(quad.vaoId)
        glEnableVertexAttribArray(0)
        
        glDrawArrays(GL_TRIANGLE_FAN, 0, quad.vertexCount)
        
        glDisableVertexAttribArray(0)
        glBindVertexArray(0)
        shader.stop()
    }
}
