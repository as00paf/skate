package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.RawModel
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_LEQUAL
import org.lwjgl.opengl.GL11.GL_LESS
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDepthFunc
import org.lwjgl.opengl.GL11.glDepthMask
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL13.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL20.glDisableVertexAttribArray
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL30.glBindVertexArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SkyDomeRenderer(private val shader: Shader, loader: VAOLoader, resourceManager: ResourceManager) {

    private val sphere: RawModel
    private val hdriTexture: Texture
    private val modelMatrix = Matrix4f()

    init {
        sphere = generateUVSphere(loader, 50, 50, 500f)
        hdriTexture = resourceManager.loadTextureSync(Assets.Textures.SKY_HDRI)
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
        // Match rotation to sun direction + manual offset
        val angle = (scene.sceneData.timeOfDay / 24.0f - 0.5f) * 2.0f * PI.toFloat()
        modelMatrix.rotateY(-angle + Math.toRadians(scene.sceneData.skyRotation.toDouble()).toFloat())

        shader.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, modelMatrix)
        shader.uploadMat4f(Uniforms.VIEW_MATRIX, camera.createViewMatrix())
        shader.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.createProjectionMatrix())
        shader.uploadVec3f(Uniforms.SUN_COLOR, scene.sceneData.sun.color)
        shader.uploadVec3f(Uniforms.SKY_TINT, scene.sceneData.skyTint)
        shader.uploadFloat(Uniforms.SKY_EXPOSURE, scene.sceneData.skyExposure)
        
        shader.uploadVec3f(Uniforms.FOG_COLOR, scene.sceneData.fogColor)
        shader.uploadFloat(Uniforms.FOG_DENSITY, scene.sceneData.fogDensity)
        shader.uploadFloat(Uniforms.FOG_GRADIENT, scene.sceneData.fogGradient)
        shader.uploadVec3f(Uniforms.CAMERA_POSITION, camera.position)

        glActiveTexture(GL_TEXTURE0)
        hdriTexture.bind()
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        shader.uploadInt(Uniforms.HDRI_TEXTURE, 0)

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
