package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.render.rhi.AttributeType
import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.VertexAttribute
import com.pafoid.skate.engine.render.rhi.VertexBinding
import com.pafoid.skate.engine.render.rhi.VertexLayoutDesc
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.render.data.PickingMesh
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Matrix4f
import org.joml.Vector4f

private const val MAX_VERTICES = 50000
private const val VERTEX_SIZE = 4 // x, y, z, id

/** World-space position plus the entity id at `aEntityId` (location 9) of the 3D picking shader. */
private val LAYOUT = VertexLayoutDesc(listOf(
    VertexBinding(VERTEX_SIZE * Float.SIZE_BYTES, listOf(
        VertexAttribute(0, AttributeType.VEC3, 0),
        VertexAttribute(9, AttributeType.FLOAT, 3 * Float.SIZE_BYTES),
    )),
))

class PickingRenderer(
    private val assetsManager: AssetsManager,
    private val logger: LoggerService,
    private val camera: CameraComponent
) {

    private val meshes = mutableListOf<PickingMesh>()
    private val vertexArray = FloatArray(MAX_VERTICES * VERTEX_SIZE)
    private var totalVertices = 0

    private lateinit var shader: Shader
    private var input: RhiVertexInput? = null
    private var vbo: RhiBuffer? = null
    private var started = false
    private val pipelines = PipelineCache(PipelineStates.SPRITES)

    private val tempVec = Vector4f()
    private val identityMatrix = Matrix4f()

    /** Frees the GPU objects; [start] may be called again afterwards. */
    fun destroy() {
        input?.close(); vbo?.close()
        input = null; vbo = null
        pipelines.close()
        started = false
    }

    fun start() {
        shader = assetsManager.getShader(Assets.Shaders.PICKING_3D)
        val device = RhiContext.require()
        val buffer = device.createBuffer(BufferDesc(BufferUsage.VERTEX, vertexArray.size.toLong() * Float.SIZE_BYTES, BufferUpdate.DYNAMIC))
        vbo = buffer
        input = device.createVertexInput(device.createVertexLayout(LAYOUT), listOf(buffer))
        started = true
    }

    fun beginFrame() {
        if (!started) start()
        meshes.clear()
        totalVertices = 0
    }

    fun draw() {
        if (meshes.isEmpty()) return
        val vertexInput = input ?: return

        shader.start()

        shader.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        shader.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        
        // Transformation is handled on CPU for batching
        shader.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, identityMatrix)
        shader.uploadInt(Uniforms.HAS_SKIN, 0) // Disable skinning for these CPU-transformed meshes
        shader.uploadBoolean(Uniforms.USE_BATCH, true)

        var index = 0
        for (mesh in meshes) {
            for (vertex in mesh.vertices) {
                // Transform to world space on CPU
                tempVec.set(vertex.x, vertex.y, vertex.z, 1.0f)
                tempVec.mul(mesh.transform)
                
                vertexArray[index++] = tempVec.x
                vertexArray[index++] = tempVec.y
                vertexArray[index++] = tempVec.z
                vertexArray[index++] = mesh.objectId.toFloat()
            }
        }
        vbo?.update(0, vertexArray)

        val cmd = RhiContext.require().commandList()
        cmd.setPipeline(pipelines.get(shader, vertexInput.layout) ?: return)
        cmd.setVertexInput(vertexInput)
        cmd.draw(totalVertices)
        shader.stop()
    }

    fun addMesh(mesh: PickingMesh) {
        if (totalVertices + mesh.vertices.size > MAX_VERTICES) return
        meshes.add(mesh)
        totalVertices += mesh.vertices.size
    }
}
