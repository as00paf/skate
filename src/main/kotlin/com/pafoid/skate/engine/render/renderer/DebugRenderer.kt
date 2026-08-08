package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms.PROJECTION
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms.VIEW
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_LINES
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glLineWidth
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glBufferSubData
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glGenVertexArrays
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val MAX_LINES = 3000
private const val MAX_TRIANGLES = 1000

class DebugRenderer(
    private val shader: Shader,
    private val cameraManager: CameraManager,
) {

    private val lines = mutableListOf<Line3D>()
    private val triangles = mutableListOf<Triangle3D>()
    
    private val lineVertexData = FloatArray(MAX_LINES * 6 * 2)
    private val triangleVertexData = FloatArray(MAX_TRIANGLES * 6 * 3)

    private var vaoId = -1
    private var vboId = -1
    
    private var triangleVaoId = -1
    private var triangleVboId = -1
    
    private var started = false

    fun start() {
        started = true

        if (vaoId != -1) return
        
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)
        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, lineVertexData.size.toLong() * Float.SIZE_BYTES, GL_DYNAMIC_DRAW)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
        glEnableVertexAttribArray(1)

        triangleVaoId = glGenVertexArrays()
        glBindVertexArray(triangleVaoId)
        triangleVboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, triangleVboId)
        glBufferData(GL_ARRAY_BUFFER, triangleVertexData.size.toLong() * Float.SIZE_BYTES, GL_DYNAMIC_DRAW)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES.toLong())
        glEnableVertexAttribArray(1)

        glLineWidth(4.0f)
    }

    fun beginFrame() {
        if (!started) start()
        lines.removeIf { it.beginFrame() < 0 }
        triangles.removeIf { it.beginFrame() < 0 }
    }

    fun draw() {
        if (!started) {
            start()
        }
        
        shader.start()
        val camera = cameraManager.camera

        shader.uploadMat4f(PROJECTION, camera.projection)
        shader.uploadMat4f(VIEW, camera.view)

        if (triangles.isNotEmpty()) {
            var index = 0
            for (tri in triangles) {
                val pts = arrayOf(tri.v1, tri.v2, tri.v3)
                for (p in pts) {
                    triangleVertexData[index++] = p.x
                    triangleVertexData[index++] = p.y
                    triangleVertexData[index++] = p.z
                    triangleVertexData[index++] = tri.color.x
                    triangleVertexData[index++] = tri.color.y
                    triangleVertexData[index++] = tri.color.z
                }
            }
            glBindBuffer(GL_ARRAY_BUFFER, triangleVboId)
            glBufferSubData(GL_ARRAY_BUFFER, 0, triangleVertexData)
            glBindVertexArray(triangleVaoId)
            glDrawArrays(GL_TRIANGLES, 0, triangles.size * 3)
        }

        if (lines.isNotEmpty()) {
            var index = 0
            for (line in lines) {
                val pts = arrayOf(line.from, line.to)
                for (p in pts) {
                    lineVertexData[index++] = p.x
                    lineVertexData[index++] = p.y
                    lineVertexData[index++] = p.z
                    lineVertexData[index++] = line.color.x
                    lineVertexData[index++] = line.color.y
                    lineVertexData[index++] = line.color.z
                }
            }
            glBindBuffer(GL_ARRAY_BUFFER, vboId)
            glBufferSubData(GL_ARRAY_BUFFER, 0, lineVertexData)
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

    fun addThickLineQuad3D(
        from: Vector3f,
        to: Vector3f,
        color: Vector3f = Vector3f(0f, 1f, 0f),
        thickness: Float = 0.08f,
        lifetime: Int = 1
    ) {
        if (triangles.size >= MAX_TRIANGLES - 2) return
        
        val direction = Vector3f(to).sub(from)
        val lineLength = direction.length()

        if (lineLength < 0.001f) return
        
        val lineDir = direction.normalize()

        val cameraPos = cameraManager.camera.position
        val cameraToLine = Vector3f(from).sub(cameraPos).normalize()
        
        var perpendicular = Vector3f(lineDir).cross(cameraToLine).normalize()
        
        if (perpendicular.length() < 0.01f) {
            val up = if (abs(lineDir.y) > 0.9f) Vector3f(0f, 0f, 1f) else Vector3f(0f, 1f, 0f)
            perpendicular = Vector3f(lineDir).cross(up).normalize()
        }
        
        perpendicular.mul(thickness * 0.5f)

        val v1 = Vector3f(from).add(perpendicular)
        val v2 = Vector3f(from).sub(perpendicular)
        val v3 = Vector3f(to).add(perpendicular)
        val v4 = Vector3f(to).sub(perpendicular)
        
        triangles.add(Triangle3D(v1, v2, v3, Vector3f(color), lifetime))
        triangles.add(Triangle3D(v2, v4, v3, Vector3f(color), lifetime))
    }

    fun addTriangle3D(v1: Vector3f, v2: Vector3f, v3: Vector3f, color: Vector3f, lifetime: Int = 1) {
        if (triangles.size >= MAX_TRIANGLES) return
        triangles.add(Triangle3D(Vector3f(v1), Vector3f(v2), Vector3f(v3), Vector3f(color), lifetime))
    }

    fun drawCircle(center: Vector3f, radius: Float, axis: Vector3f, color: Vector3f, segments: Int = 32) {
        val ortho1 = if (abs(axis.x) > 0.9f) Vector3f(0f, 1f, 0f) else Vector3f(1f, 0f, 0f)
        val v1 = Vector3f(axis).cross(ortho1).normalize().mul(radius)
        val v2 = Vector3f(axis).cross(v1).normalize().mul(radius)

        var lastPt = Vector3f(center).add(v1)
        for (i in 1..segments) {
            val angle = (i.toFloat() / segments) * Math.PI.toFloat() * 2f
            val nextPt = Vector3f(center)
                .add(Vector3f(v1).mul(cos(angle.toDouble()).toFloat()))
                .add(Vector3f(v2).mul(sin(angle.toDouble()).toFloat()))

            addLine3D(lastPt, nextPt, color)
            lastPt = nextPt
        }
    }

    fun addCylinder3D(
        center: Vector3f,
        rotation: Quaternionf,
        radius: Float,
        height: Float,
        axis: Int,
        color: Vector3f
    ) {
        val halfHeight = height / 2f
        val segments = 16
        val axisVec = when(axis) {
            0 -> Vector3f(1f, 0f, 0f)
            1 -> Vector3f(0f, 1f, 0f)
            else -> Vector3f(0f, 0f, 1f)
        }
        val ortho1 = if (abs(axisVec.y) > 0.9f) Vector3f(1f, 0f, 0f) else Vector3f(0f, 1f, 0f)
        val v1 = Vector3f(axisVec).cross(ortho1).normalize().mul(radius)
        val v2 = Vector3f(axisVec).cross(v1).normalize().mul(radius)

        for (i in 0 until segments) {
            val a1 = (i.toFloat() / segments) * Math.PI.toFloat() * 2f
            val a2 = ((i + 1).toFloat() / segments) * Math.PI.toFloat() * 2f
            val p1 = Vector3f(v1).mul(cos(a1.toDouble()).toFloat()).add(Vector3f(v2).mul(sin(a1.toDouble()).toFloat()))
            val p2 = Vector3f(v1).mul(cos(a2.toDouble()).toFloat()).add(Vector3f(v2).mul(sin(a2.toDouble()).toFloat()))
            val bottom1 = Vector3f(p1).add(Vector3f(axisVec).mul(-halfHeight))
            val bottom2 = Vector3f(p2).add(Vector3f(axisVec).mul(-halfHeight))
            val top1 = Vector3f(p1).add(Vector3f(axisVec).mul(halfHeight))
            val top2 = Vector3f(p2).add(Vector3f(axisVec).mul(halfHeight))
            rotation.transform(bottom1).add(center)
            rotation.transform(bottom2).add(center)
            rotation.transform(top1).add(center)
            rotation.transform(top2).add(center)
            addLine3D(bottom1, bottom2, color)
            addLine3D(top1, top2, color)
            addLine3D(bottom1, top1, color)
        }
    }

    fun addBox3D(center: Vector3f, rotation: Quaternionf, halfExtents: Vector3f, color: Vector3f) {
        val h = halfExtents
        val corners = arrayOf(
            Vector3f(-h.x, -h.y, -h.z), Vector3f(h.x, -h.y, -h.z),
            Vector3f(h.x, h.y, -h.z), Vector3f(-h.x, h.y, -h.z),
            Vector3f(-h.x, -h.y, h.z), Vector3f(h.x, -h.y, h.z),
            Vector3f(h.x, h.y, h.z), Vector3f(-h.x, h.y, h.z)
        )
        corners.forEach { c -> rotation.transform(c).add(center) }
        addLine3D(corners[0], corners[1], color)
        addLine3D(corners[1], corners[2], color)
        addLine3D(corners[2], corners[3], color)
        addLine3D(corners[3], corners[0], color)
        addLine3D(corners[4], corners[5], color)
        addLine3D(corners[5], corners[6], color)
        addLine3D(corners[6], corners[7], color)
        addLine3D(corners[7], corners[4], color)
        addLine3D(corners[0], corners[4], color)
        addLine3D(corners[1], corners[5], color)
        addLine3D(corners[2], corners[6], color)
        addLine3D(corners[3], corners[7], color)
    }
}

// TODO: extract
class Line3D(val from: Vector3f, val to: Vector3f, val color: Vector3f, var lifetime: Int) {
    fun beginFrame(): Int = --lifetime
}

class Triangle3D(val v1: Vector3f, val v2: Vector3f, val v3: Vector3f, val color: Vector3f, var lifetime: Int) {
    fun beginFrame(): Int = --lifetime
}
