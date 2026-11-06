package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.render.fog.FogUniforms
import com.pafoid.skate.engine.render.lighting.EnvironmentProbe
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SkyDomeRenderer(private val shader: Shader, private val loader: VAOLoader) {

    private val sphere: MeshPart
    private val modelMatrix = Matrix4f()

    /** At the far plane after the scene, no depth writes, both faces ([PipelineStates.SKY]). */
    private val pipelines = PipelineCache(PipelineStates.SKY)

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

    fun render(camera: CameraComponent, cameraPosition: Vector3f, scene: Scene) {
        // Get environment component for sky/fog settings
        val environmentComponent = scene.getComponent<EnvironmentComponent>()?.takeIf { it.enabled }
        val renderSky = environmentComponent?.renderSky == true
        val hdriTexture = environmentComponent?.skyTexture

        // Skip sky rendering if renderSky is false
        if (!renderSky) {
            return
        }

        shader.start()

        val dayNightComponent = scene.getComponent<DayNightCycleComponent>()
        val sun = SceneSun.of(scene)

        // Centred on the camera, turned with the time of day plus the manual offset (the IBL probe uses the same angle)
        modelMatrix.identity().translation(cameraPosition)
        modelMatrix.rotateY(EnvironmentProbe.skyRotation(environmentComponent, dayNightComponent))

        shader.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, modelMatrix)
        shader.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        shader.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        sun?.color?.let { shader.uploadVec3f(Uniforms.SUN_COLOR, it) }

        // Upload sky settings from EnvironmentComponent
        shader.uploadVec3f(Uniforms.SKY_TINT, environmentComponent.skyTint)
        shader.uploadFloat(Uniforms.SKY_EXPOSURE, environmentComponent.skyExposure)

        // Same fog as the models (fog.glsl), evaluated at the far plane along each view ray
        FogUniforms.upload(shader, environmentComponent)
        shader.uploadFloat(Uniforms.FOG_SKY_DISTANCE, camera.farPlane)
        shader.uploadVec3f(Uniforms.CAMERA_POSITION, cameraPosition)

        // Draw sky texture: wraps around horizontally, clamps at the poles
        val sky = hdriTexture?.rhi
        val input = sphere.input
        if (sky != null && input != null) {
            sky.setSampler(sky.sampler.copy(wrap = Wrap.REPEAT, wrapV = Wrap.CLAMP_TO_EDGE))
            val cmd = RhiContext.require().commandList()
            cmd.setTexture(0, sky)
            shader.uploadInt(Uniforms.HDRI_TEXTURE, 0)
            cmd.setPipeline(pipelines.get(shader, input.layout) ?: return)
            cmd.setVertexInput(input)
            cmd.drawIndexed(sphere.vertexCount)
        }

        shader.stop()
    }

    fun destroy() {
        pipelines.close()
        loader.deleteVAO(sphere.vaoId)
    }
}
