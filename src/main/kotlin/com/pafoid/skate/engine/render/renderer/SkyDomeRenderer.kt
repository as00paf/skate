package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_LEQUAL
import org.lwjgl.opengl.GL11.GL_LESS
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glBindTexture
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
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SkyDomeRenderer(private val shader: Shader, loader: VAOLoader) {

    private val sphere: MeshPart
    private val modelMatrix = Matrix4f()

    init {
        sphere = generateUVSphere(loader, 50, 50, 500f)
    }

    private fun generateUVSphere(loader: VAOLoader, rings: Int, sectors: Int, radius: Float): MeshPart {
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

        return loader.loadToVAO(
            positions = vertices.toFloatArray(),
            textureCoords = texCoords.toFloatArray(),
            normals = normals.toFloatArray(),
            indices = indices.toIntArray()
        )
    }

    fun render(camera: CameraComponent, scene: Scene) {
        // Get environment component for sky/fog settings
        val environmentComponent = scene.getComponent<EnvironmentComponent>()
        val renderSky = environmentComponent?.renderSky ?: true
        val hdriTexture = environmentComponent?.skyTexture

        // Skip sky rendering if renderSky is false
        if (!renderSky) {
            return
        }

        glDisable(GL_CULL_FACE)
        glDepthFunc(GL_LEQUAL)
        glDepthMask(false)

        shader.start()

        // Get time component for time of day
        val dayNightComponent = scene.getComponent<DayNightCycleComponent>()
        val timeOfDay = dayNightComponent?.timeOfDay ?: 12.0f
        val sun = scene.getComponent<DirectionalLightComponent>()

        // Center on camera
        modelMatrix.identity().translation(camera.position)
        // Match rotation to sun direction + manual offset
        val angle = (timeOfDay / 24.0f - 0.5f) * 2.0f * PI.toFloat()
        modelMatrix.rotateY(-angle + Math.toRadians((environmentComponent?.skyRotation ?: 0f).toDouble()).toFloat())

        shader.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, modelMatrix)
        shader.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        shader.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        sun?.color?.let { shader.uploadVec3f(Uniforms.SUN_COLOR, it) }

        // Upload sky settings from EnvironmentComponent
        shader.uploadVec3f(Uniforms.SKY_TINT, environmentComponent?.skyTint ?: Vector3f(1f, 1f, 1f))
        shader.uploadFloat(Uniforms.SKY_EXPOSURE, environmentComponent?.skyExposure ?: 1.0f)

        // Upload fog settings from EnvironmentComponent
        shader.uploadVec3f(Uniforms.FOG_COLOR, environmentComponent?.fogColor ?: Vector3f(0.8f, 0.8f, 0.8f))
        shader.uploadFloat(Uniforms.FOG_DENSITY, environmentComponent?.fogDensity ?: 0.0f)
        shader.uploadFloat(Uniforms.FOG_GRADIENT, environmentComponent?.fogGradient ?: 1.5f)
        shader.uploadVec3f(Uniforms.CAMERA_POSITION, camera.position)

        // Draw sky texture
        hdriTexture?.texId?.let { texId ->
            if (texId < 0) return@let
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, texId)
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
        }

        shader.stop()
        
        glDepthMask(true)
        glDepthFunc(GL_LESS)
        glEnable(GL_CULL_FACE)
    }

    fun destroy() {
        if (sphere.vaoId != 0) {
            glDeleteVertexArrays(sphere.vaoId)
        }
    }
}
