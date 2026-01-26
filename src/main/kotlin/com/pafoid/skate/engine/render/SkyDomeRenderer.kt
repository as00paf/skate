package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.scenes.Scene
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import kotlin.math.*

class SkyDomeRenderer(private val shader: Shader, loader: VAOLoader) {

    private val sphere: RawModel
    private val hdriTexture: Texture
    private val modelMatrix = Matrix4f()

    init {
        sphere = generateUVSphere(loader, 50, 50, 500f)
        hdriTexture = AssetPool.getTexture("assets/textures/sky_hdri.png")
    }

    private fun generateUVSphere(loader: VAOLoader, rings: Int, sectors: Int, radius: Float): RawModel {
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        val R = 1f / (rings - 1).toFloat()
        val S = 1f / (sectors - 1).toFloat()

        for (r in 0 until rings) {
            for (s in 0 until sectors) {
                val y = sin(-PI.toFloat() / 2f + PI.toFloat() * r * R)
                val x = cos(2f * PI.toFloat() * s * S) * sin(PI.toFloat() * r * R)
                val z = sin(2f * PI.toFloat() * s * S) * sin(PI.toFloat() * r * R)

                vertices.add(x * radius)
                vertices.add(y * radius)
                vertices.add(z * radius)

                texCoords.add(s * S)
                texCoords.add(r * R)

                normals.add(-x)
                normals.add(-y)
                normals.add(-z)
            }
        }

        for (r in 0 until rings - 1) {
            for (s in 0 until sectors - 1) {
                indices.add(r * sectors + s)
                indices.add(r * sectors + (s + 1))
                indices.add((r + 1) * sectors + (s + 1))
                indices.add((r + 1) * sectors + (s + 1))
                indices.add((r + 1) * sectors + s)
                indices.add(r * sectors + s)
            }
        }

        return loader.loadToVAO(vertices.toFloatArray(), texCoords.toFloatArray(), normals.toFloatArray(), indices.toIntArray())
    }

    fun render(camera: Camera, scene: Scene) {
        glDisable(GL_CULL_FACE)
        glDepthFunc(GL_LEQUAL)
        glDepthMask(false)

        shader.start()
        
        // Center on camera
        modelMatrix.identity().translation(camera.position)
        // Match rotation to sun direction (sync sun to texture light source)
        // The texture's light source is typically at some fixed position.
        // We'll rotate the dome so that 'sun' matches our directional light.
        // For now, let's just use rotation from time of day.
        val angle = (scene.timeOfDay / 24.0f - 0.5f) * 2.0f * PI.toFloat()
        modelMatrix.rotateY(-angle)

        shader.uploadMat4f("transformationMatrix", modelMatrix)
        shader.uploadMat4f("viewMatrix", camera.createViewMatrix())
        shader.uploadMat4f("projectionMatrix", camera.createProjectionMatrix())
        
        shader.uploadVec3f("u_skyTint", scene.skyTint)
        shader.uploadFloat("u_exposure", scene.skyExposure)
        
        shader.uploadVec3f("uFogColor", scene.fogColor)
        shader.uploadFloat("uFogDensity", scene.fogDensity)
        shader.uploadFloat("uFogGradient", scene.fogGradient)
        shader.uploadVec3f("uCameraPos", camera.position)

        glActiveTexture(GL_TEXTURE0)
        hdriTexture.bind()
        shader.uploadInt("u_hdriTexture", 0)

        glBindVertexArray(sphere.vaoId)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        
        glDrawElements(GL_TRIANGLES, sphere.vertexCount, GL_UNSIGNED_INT, 0)
        
        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
        glBindVertexArray(0)
        shader.stop()
        
        glDepthMask(true)
        glDepthFunc(GL_LESS)
        glEnable(GL_CULL_FACE)
    }
}
