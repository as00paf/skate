package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

object DebugDraw {
    private const val MAX_LINES = 3000
    private val lines = mutableListOf<Line2D>()
    private val vertexArray = FloatArray(MAX_LINES * 6 * 2)
    private lateinit var shader: Shader
    private var vaoId = -1
    private var vboId = -1
    private var started = false

    fun start() {
        shader = AssetPool.getShader(Shader.DEBUG)
        
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)

        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertexArray.size.toLong() * Float.SIZE_BYTES, GL_DYNAMIC_DRAW)

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)

        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
        glEnableVertexAttribArray(1)

        glLineWidth(2.0f)
        started = true
    }

    fun beginFrame() {
        if (!started) {
            start()
        }
        lines.removeIf { it.beginFrame() < 0 }
    }

    fun draw() {
        if (lines.isEmpty()) return

        var index = 0
        for (line in lines) {
            for (i in 0..1) {
                val position = if (i == 0) line.from else line.to
                val color = line.color

                vertexArray[index] = position.x
                vertexArray[index + 1] = position.y
                vertexArray[index + 2] = -10f // Fixed Z for 2D debug draw

                vertexArray[index + 3] = color.x
                vertexArray[index + 4] = color.y
                vertexArray[index + 5] = color.z
                index += 6
            }
        }

        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexArray)

        shader.start()
        val camera = SceneManager.getCurrentScene()?.camera ?: return
        shader.uploadMat4f("uProjection", camera.createProjectionMatrix())
        shader.uploadMat4f("uView", camera.createViewMatrix())

        glBindVertexArray(vaoId)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)

        glDrawArrays(GL_LINES, 0, lines.size * 2)

        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
        glBindVertexArray(0)
        shader.stop()
    }

    fun addLine2D(from: Vector2f, to: Vector2f, color: Vector3f = Vector3f(0f, 1f, 0f), lifetime: Int = 1) {
        if (lines.size >= MAX_LINES) return
        lines.add(Line2D(Vector2f(from), Vector2f(to), Vector3f(color), lifetime))
    }
}