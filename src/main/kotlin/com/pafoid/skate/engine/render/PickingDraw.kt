package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.ShaderConst.Uniforms
import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.scenes.SceneManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import kotlin.getValue

private const val MAX_VERTICES = 10000

class PickingDraw: KoinComponent {
    private val resourceManager: ResourceManager by inject()
    private val logger: LoggerService by inject()
    private val sceneManager: SceneManager by inject()

    private val meshes = mutableListOf<PickingMesh>()
    private val vertexArray = FloatArray(MAX_VERTICES * 3)

    private lateinit var shader: Shader
    private var vaoId = -1
    private var vboId = -1
    private var started = false

    fun start() {
        shader = resourceManager.getShader(Assets.Shaders.PICKING)?: run {
            logger.logEngine("Could not load picking shader", LogLevel.ERROR)
            return
        }
        
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
        val camera = sceneManager.currentScene?.camera ?: return
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
