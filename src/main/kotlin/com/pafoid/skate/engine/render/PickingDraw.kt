package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.ShaderConst.Uniforms
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

data class PickingMesh(
    val vertices: List<Vector3f>,
    val transform: Matrix4f,
    val objectId: Int
)

object PickingDraw {
    private const val MAX_VERTICES = 10000
    private val meshes = mutableListOf<PickingMesh>()
    private val vertexArray = FloatArray(MAX_VERTICES * 3)

    private lateinit var shader: Shader
    private var vaoId = -1
    private var vboId = -1
    private var started = false

    fun start() {
        shader = AssetPool.getShader(Assets.Shaders.PICKING)
        
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)
        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertexArray.size.toLong() * Float.SIZE_BYTES, GL_DYNAMIC_DRAW)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)
        
        started = true
    }

    fun beginFrame() {
        if (!started) start()
        meshes.clear()
    }

    fun draw() {
        shader.start()
        val camera = SceneManager.getCurrentScene()?.camera ?: return
        shader.uploadMat4f(Uniforms.PROJECTION, camera.createProjectionMatrix())
        shader.uploadMat4f(Uniforms.VIEW, camera.createViewMatrix())

        for (mesh in meshes) {
            shader.uploadMat4f(Uniforms.MODEL, mesh.transform)
            shader.uploadInt(Uniforms.OBJECT_ID, mesh.objectId)

            var index = 0
            for(vertex in mesh.vertices) {
                vertexArray[index++] = vertex.x
                vertexArray[index++] = vertex.y
                vertexArray[index++] = vertex.z
            }
            
            glBindBuffer(GL_ARRAY_BUFFER, vboId)
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertexArray)
            glBindVertexArray(vaoId)
            glDrawArrays(GL_TRIANGLES, 0, mesh.vertices.size)
        }

        glBindVertexArray(0)
        shader.stop()
    }

    fun addMesh(mesh: PickingMesh) {
        if (meshes.size * mesh.vertices.size > MAX_VERTICES) return
        meshes.add(mesh)
    }
}
