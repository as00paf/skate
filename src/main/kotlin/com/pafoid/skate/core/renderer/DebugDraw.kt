package com.pafoid.skate.core.renderer

import com.pafoid.skate.core.Window
import marki.renderer.Line2D
import marki.renderer.Shader
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.ARBVertexArrayObject.glBindVertexArray
import org.lwjgl.opengl.ARBVertexArrayObject.glGenVertexArrays
import org.lwjgl.opengl.GL20.*
import com.pafoid.skate.util.AssetPool
import com.pafoid.skate.util.JMath
import java.util.*

object DebugDraw {

    private const val MAX_LINES = 3000

    private val defaultDebugColor = Vector3f(0f, 1f, 0f)
    private val lines = mutableListOf<Line2D>()
    private val vertexArray = FloatArray(MAX_LINES * 6 * 2)
    private val shader = AssetPool.getShader(Shader.Companion.DEBUG)
    private var vaoId = -1
    private var vboId = -1
    private var started = false


    fun start() {
        // Generate vao
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)

        // Create the vbo and buffer some memory
        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertexArray.size * Float.SIZE_BYTES.toLong(), GL_DYNAMIC_DRAW)

        // Enable the vertex array attributes
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
            return
        }

        // Remove dead lines
        lines.removeIf { it.beginFrame() < 0 }
    }

    fun draw() {
        if (lines.size < 1) return

        var index = 0
        lines.forEach { line ->
            for (i in 0 until 2) {
                val position = if (i == 0) line.from else line.to
                val color = line.color

                // Load the position
                vertexArray[index] = position.x
                vertexArray[index + 1] = position.y
                vertexArray[index + 2] = -10f


                // Load the color
                vertexArray[index + 3] = color.x
                vertexArray[index + 4] = color.y
                vertexArray[index + 5] = color.z

                index += 6
            }
        }

        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertexArray, GL_DYNAMIC_DRAW)

        shader.use()
        shader.uploadMat4f("uProjection", Window.currentScene.camera.getProjectionMatrix())
        shader.uploadMat4f("uView", Window.currentScene.camera.getViewMatrix())

        // Bind the vao
        glBindVertexArray(vaoId)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)

        //Draw the batch
        glDrawArrays(GL_LINES, 0, lines.size)

        // Disable Location
        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
        glBindVertexArray(0)

        shader.detach()
    }

    // Line2D
    fun addLine2D(from: Vector2f, to: Vector2f, color: Vector3f = defaultDebugColor, lifetime: Int = 2) {
        val camera = Window.currentScene.camera
        val cameraLeft = Vector2f(camera.position).add(Vector2f(-2f, -2f))
        val cameraRight = Vector2f(camera.position).add(Vector2f(camera.getProjectionSize()).mul(camera.zoom))

        val lineInView =
            from.x >= cameraLeft.x && from.x <= cameraRight.x && from.y >= cameraLeft.y && from.y <= cameraRight.y ||
                    to.x >= cameraLeft.x && to.x <= cameraRight.x && to.y >= cameraLeft.y && to.y <= cameraRight.y

        if (lines.size >= MAX_LINES || !lineInView) return

        lines.add(Line2D(from, to, color, lifetime))
    }

    fun addLine2D(line: Line2D) {
        if (lines.size >= MAX_LINES) return
        lines.add(line)
    }

    // Box2D
    fun addBox2D(center: Vector2f, dimensions: Vector2f, rotation: Double = 0.0, color: Vector3f = defaultDebugColor, lifetime:Int = 2) {
        val min = Vector2f(center).sub(Vector2f(dimensions).mul(0.5f))
        val max = Vector2f(center).add(Vector2f(dimensions).mul(0.5f))

        val vertices = arrayOf(
            Vector2f(min.x, min.y), Vector2f(min.x, max.y),
            Vector2f(max.x, max.y), Vector2f(max.x, min.y)
        )

        if(rotation != 0.0) {
            vertices.forEach { vert ->
                JMath.rotate(vert, center, rotation)
            }
        }

        addLine2D(vertices[0], vertices[1], color, lifetime)
        addLine2D(vertices[0], vertices[3], color, lifetime)
        addLine2D(vertices[1], vertices[2], color, lifetime)
        addLine2D(vertices[2], vertices[3], color, lifetime)
    }

    // Circle
    fun addCircle(center: Vector2f, radius: Float, color: Vector3f = defaultDebugColor, lifetime:Int = 2, pointCount:Int = 20) {
        val points = Array(pointCount) { Vector2f() }
        val increment = 360 / points.size
        var currentAngle = 0.0

        for(i in 0 until points.size) {
            val tmp = Vector2f(0f, radius)
            JMath.rotate(tmp, Vector2f(), currentAngle)
            points[i] = Vector2f(tmp).add(center)

            if(i>0) addLine2D(points[i-1], points[i], color, lifetime)

            currentAngle += increment
        }

        addLine2D(points[points.size - 1], points[0], color, lifetime)
    }
}