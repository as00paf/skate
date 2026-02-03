package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.ShaderConst.Uniforms
import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import kotlin.getValue

private const val MAX_VERTICES = 50000
private const val VERTEX_SIZE = 4 // x, y, z, id

class PickingDraw: KoinComponent {
    private val resourceManager: ResourceManager by inject()
    private val logger: LoggerService by inject()
    private val sceneManager: SceneManager by inject()

    private val meshes = mutableListOf<PickingMesh>()
    private val vertexArray = FloatArray(MAX_VERTICES * VERTEX_SIZE)
    private var totalVertices = 0

    private lateinit var shader: Shader
    private var vaoId = -1
    private var vboId = -1
    private var started = false

    private val tempVec = Vector4f()
    private val identityMatrix = Matrix4f()

    fun start() {
        shader = resourceManager.getShader(Assets.Shaders.PICKING_3D)?: run {
            logger.logEngine("Could not load picking shader", LogLevel.ERROR)
            return
        }
        
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)
        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertexArray.size.toLong() * Float.SIZE_BYTES, GL_DYNAMIC_DRAW)
        
        // Position
        glVertexAttribPointer(0, 3, GL_FLOAT, false, VERTEX_SIZE * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)
        
        // Entity ID (mapped to location 10 in shader)
        glVertexAttribPointer(10, 1, GL_FLOAT, false, VERTEX_SIZE * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
        glEnableVertexAttribArray(10)
        
        started = true
    }

    fun beginFrame() {
        if (!started) start()
        meshes.clear()
        totalVertices = 0
    }

    fun draw() {
        if (meshes.isEmpty()) return

        shader.start()
        val camera = sceneManager.currentScene?.camera ?: return
        shader.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.createProjectionMatrix())
        shader.uploadMat4f(Uniforms.VIEW_MATRIX, camera.createViewMatrix())
        
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
        
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexArray)
        
        glBindVertexArray(vaoId)
        glDrawArrays(GL_TRIANGLES, 0, totalVertices)

        glBindVertexArray(0)
        shader.stop()
    }

    fun addMesh(mesh: PickingMesh) {
        if (totalVertices + mesh.vertices.size > MAX_VERTICES) return
        meshes.add(mesh)
        totalVertices += mesh.vertices.size
    }
}
