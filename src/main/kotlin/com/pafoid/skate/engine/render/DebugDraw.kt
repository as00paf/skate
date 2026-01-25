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
    private const val MAX_TRIANGLES = 1000
    
    private val lines = mutableListOf<Line3D>()
    private val triangles = mutableListOf<Triangle3D>()
    
    private val vertexArray = FloatArray(MAX_LINES * 6 * 2)
    private val triangleVertexArray = FloatArray(MAX_TRIANGLES * 6 * 3)
    
    private lateinit var shader: Shader
    private var vaoId = -1
    private var vboId = -1
    
    private var triangleVaoId = -1
    private var triangleVboId = -1
    
    private var started = false

    fun start() {
        shader = AssetPool.getShader(Shader.DEBUG)
        
        // Lines
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)
        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertexArray.size.toLong() * Float.SIZE_BYTES, GL_DYNAMIC_DRAW)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
        glEnableVertexAttribArray(1)

        // Triangles
        triangleVaoId = glGenVertexArrays()
        glBindVertexArray(triangleVaoId)
        triangleVboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, triangleVboId)
        glBufferData(GL_ARRAY_BUFFER, triangleVertexArray.size.toLong() * Float.SIZE_BYTES, GL_DYNAMIC_DRAW)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
        glEnableVertexAttribArray(1)

        glLineWidth(4.0f)
        started = true
    }

    fun beginFrame() {
        if (!started) start()
        lines.removeIf { it.beginFrame() < 0 }
        triangles.removeIf { it.beginFrame() < 0 }
    }

    fun draw() {
        shader.start()
        val camera = SceneManager.getCurrentScene()?.camera ?: return
        shader.uploadMat4f("uProjection", camera.createProjectionMatrix())
        shader.uploadMat4f("uView", camera.createViewMatrix())

        // 1. Draw Triangles
        if (triangles.isNotEmpty()) {
            var index = 0
            for (tri in triangles) {
                val pts = arrayOf(tri.v1, tri.v2, tri.v3)
                for (p in pts) {
                    triangleVertexArray[index++] = p.x
                    triangleVertexArray[index++] = p.y
                    triangleVertexArray[index++] = p.z
                    triangleVertexArray[index++] = tri.color.x
                    triangleVertexArray[index++] = tri.color.y
                    triangleVertexArray[index++] = tri.color.z
                }
            }
            glBindBuffer(GL_ARRAY_BUFFER, triangleVboId)
            glBufferSubData(GL_ARRAY_BUFFER, 0, triangleVertexArray)
            glBindVertexArray(triangleVaoId)
            glDrawArrays(GL_TRIANGLES, 0, triangles.size * 3)
        }

        // 2. Draw Lines
        if (lines.isNotEmpty()) {
            var index = 0
            for (line in lines) {
                val pts = arrayOf(line.from, line.to)
                for (p in pts) {
                    vertexArray[index++] = p.x
                    vertexArray[index++] = p.y
                    vertexArray[index++] = p.z
                    vertexArray[index++] = line.color.x
                    vertexArray[index++] = line.color.y
                    vertexArray[index++] = line.color.z
                }
            }
            glBindBuffer(GL_ARRAY_BUFFER, vboId)
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertexArray)
            glBindVertexArray(vaoId)
            glDrawArrays(GL_LINES, 0, lines.size * 2)
        }

        glBindVertexArray(0)
        shader.stop()
    }

    fun addLine3D(from: Vector3f, to: Vector3f, color: Vector3f = Vector3f(0f, 1f, 0f), lifetime: Int = 1) {
        if (lines.size >= MAX_LINES) return
        lines.add(Line3D(Vector3f(from), Vector3f(to), Vector3f(color), lifetime))
    }

    fun addTriangle3D(v1: Vector3f, v2: Vector3f, v3: Vector3f, color: Vector3f, lifetime: Int = 1) {
        if (triangles.size >= MAX_TRIANGLES) return
        triangles.add(Triangle3D(Vector3f(v1), Vector3f(v2), Vector3f(v3), Vector3f(color), lifetime))
    }

    fun addLine2D(from: Vector2f, to: Vector2f, color: Vector3f = Vector3f(0f, 1f, 0f), lifetime: Int = 1) {
        addLine3D(Vector3f(from.x, from.y, -10f), Vector3f(to.x, to.y, -10f), color, lifetime)
    }

    fun drawCircle(center: Vector3f, radius: Float, axis: Vector3f, color: Vector3f, segments: Int = 32) {
        val ortho1 = if (Math.abs(axis.x) > 0.9f) Vector3f(0f, 1f, 0f) else Vector3f(1f, 0f, 0f)
        val v1 = Vector3f(axis).cross(ortho1).normalize().mul(radius)
        val v2 = Vector3f(axis).cross(v1).normalize().mul(radius)

        var lastPt = Vector3f(center).add(v1)
        for (i in 1..segments) {
            val angle = (i.toFloat() / segments) * Math.PI.toFloat() * 2f
            val nextPt = Vector3f(center)
                .add(Vector3f(v1).mul(Math.cos(angle.toDouble()).toFloat()))
                .add(Vector3f(v2).mul(Math.sin(angle.toDouble()).toFloat()))
            
            addLine3D(lastPt, nextPt, color)
            lastPt = nextPt
        }
    }
}

class Line3D(val from: Vector3f, val to: Vector3f, val color: Vector3f, var lifetime: Int) {
    fun beginFrame(): Int = --lifetime
}

class Triangle3D(val v1: Vector3f, val v2: Vector3f, val v3: Vector3f, val color: Vector3f, var lifetime: Int) {
    fun beginFrame(): Int = --lifetime
}