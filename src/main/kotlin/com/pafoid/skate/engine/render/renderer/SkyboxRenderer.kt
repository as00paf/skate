package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.CubeMap
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms

class SkyboxRenderer(private val shader: Shader, private val loader: VAOLoader) {

    private val cube: MeshPart = loader.loadToVAO(VERTICES, 3)

    /** At the far plane (LEQUAL), no depth writes ([PipelineStates.SKY]). */
    private val pipelines = PipelineCache(PipelineStates.SKY)

    fun render(camera: CameraComponent, cubeMap: CubeMap) {
        val input = cube.input ?: return
        shader.start()
        shader.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        shader.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)

        val cmd = RhiContext.require().commandList()
        cmd.setTexture(0, cubeMap.texture)
        cmd.setPipeline(pipelines.get(shader, input.layout) ?: return)
        cmd.setVertexInput(input)
        cmd.draw(cube.vertexCount)
        shader.stop()
    }

    fun destroy() {
        pipelines.close()
        loader.deleteVAO(cube.vaoId)
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