package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms.PROJECTION
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms.VIEW
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_LINES
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glLineWidth
import org.lwjgl.opengl.GL30.glBindVertexArray
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val MAX_LINES = 3000
private const val MAX_TRIANGLES = 1000
private const val CAMERA_POS_UNIFORM = "uCameraPos"
private const val PIXEL_ANGLE_UNIFORM = "uPixelAngle"
private const val GROUND_UNIFORM = "uGround"

class DebugRenderer(
    private val shader: Shader,
    private val cameraManager: CameraManager,
) {

    private val lines = mutableListOf<Line3D>()
    private val groundLines = mutableListOf<Line3D>()
    private val groundTriangles = mutableListOf<Triangle3D>()
    private val triangles = mutableListOf<Triangle3D>()
    
    private val lineVertexData = FloatArray(MAX_LINES * 6 * 2)
    private val triangleVertexData = FloatArray(MAX_TRIANGLES * 6 * 3)

    private var vao: RhiVertexInput? = null
    private var vbo: RhiBuffer? = null
    private var triangleVao: RhiVertexInput? = null
    private var triangleVbo: RhiBuffer? = null
    private val vaoId get() = vao?.nativeHandle?.toInt() ?: -1
    private val triangleVaoId get() = triangleVao?.nativeHandle?.toInt() ?: -1
    
    private var started = false

    /** Frees all GL objects; [start] may be called again afterwards. */
    fun destroy() {
        listOf(vao, vbo, triangleVao, triangleVbo).forEach { it?.close() }
        vao = null; vbo = null; triangleVao = null; triangleVbo = null
        started = false
    }

    fun start() {
        started = true

        if (vaoId != -1) return

        val device = RhiContext.require()
        val layout = device.createVertexLayout(VertexLayouts.DEBUG)
        val lineBuffer = device.createBuffer(BufferDesc(BufferUsage.VERTEX, lineVertexData.size.toLong() * Float.SIZE_BYTES, BufferUpdate.DYNAMIC))
        vbo = lineBuffer
        vao = device.createVertexInput(layout, listOf(lineBuffer))
        val triangleBuffer = device.createBuffer(BufferDesc(BufferUsage.VERTEX, triangleVertexData.size.toLong() * Float.SIZE_BYTES, BufferUpdate.DYNAMIC))
        triangleVbo = triangleBuffer
        triangleVao = device.createVertexInput(layout, listOf(triangleBuffer))

        glLineWidth(4.0f)
    }

    fun beginFrame() {
        if (!started) start()
        lines.removeIf { it.beginFrame() < 0 }
        groundLines.removeIf { it.beginFrame() < 0 }
        groundTriangles.removeIf { it.beginFrame() < 0 }
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

        shader.uploadVec3f(CAMERA_POS_UNIFORM, cameraManager.transform.worldMatrix.getTranslation(cameraPosition))
        shader.uploadFloat(PIXEL_ANGLE_UNIFORM, pixelAngle)
        drawTriangles()
        drawLines()

        glBindVertexArray(0)
        shader.stop()
    }

    /** Radians per pixel at the view centre ([GroundDepthPull.pixelAngle]); set by the debug pass every frame. */
    var pixelAngle: Float = GroundDepthPull.pixelAngle(Math.toRadians(45.0).toFloat(), 1080)

    private val cameraPosition = Vector3f()

    /** Ground triangles then the others, uploaded once and drawn as two ranges (ground pull on, then off). */
    private fun drawTriangles() {
        if (groundTriangles.isEmpty() && triangles.isEmpty()) return
        var index = 0
        for (batch in arrayOf(groundTriangles, triangles)) for (tri in batch) {
            for (p in arrayOf(tri.v1, tri.v2, tri.v3)) {
                triangleVertexData[index++] = p.x
                triangleVertexData[index++] = p.y
                triangleVertexData[index++] = p.z
                triangleVertexData[index++] = tri.color.x
                triangleVertexData[index++] = tri.color.y
                triangleVertexData[index++] = tri.color.z
            }
        }
        triangleVbo?.update(0, triangleVertexData)
        glBindVertexArray(triangleVaoId)
        if (groundTriangles.isNotEmpty()) {
            shader.uploadInt(GROUND_UNIFORM, 1)
            glDrawArrays(GL_TRIANGLES, 0, groundTriangles.size * 3)
        }
        if (triangles.isNotEmpty()) {
            shader.uploadInt(GROUND_UNIFORM, 0)
            glDrawArrays(GL_TRIANGLES, groundTriangles.size * 3, triangles.size * 3)
        }
    }

    /** Ground lines then the others, uploaded once into one buffer and drawn as two ranges (ground pull on, then off). */
    private fun drawLines() {
        if (groundLines.isEmpty() && lines.isEmpty()) return
        var index = 0
        for (batch in arrayOf(groundLines, lines)) for (line in batch) {
            for (p in arrayOf(line.from, line.to)) {
                lineVertexData[index++] = p.x
                lineVertexData[index++] = p.y
                lineVertexData[index++] = p.z
                lineVertexData[index++] = line.color.x
                lineVertexData[index++] = line.color.y
                lineVertexData[index++] = line.color.z
            }
        }
        vbo?.update(0, lineVertexData)
        glBindVertexArray(vaoId)
        if (groundLines.isNotEmpty()) {
            shader.uploadInt(GROUND_UNIFORM, 1)
            glDrawArrays(GL_LINES, 0, groundLines.size * 2)
        }
        if (lines.isNotEmpty()) {
            shader.uploadInt(GROUND_UNIFORM, 0)
            glDrawArrays(GL_LINES, groundLines.size * 2, lines.size * 2)
        }
    }

    /** Vertices of the ground ribbons queued this frame (tests: they must lie on the floor plane). */
    internal fun groundTriangleVertices(): List<Vector3f> = groundTriangles.flatMap { listOf(it.v1, it.v2, it.v3) }

    /**
     * A line lying on a horizontal floor, e.g. the editor grid (REN-19): it gets the grazing-angle depth pull
     * ([GroundDepthPull]) so it does not z-fight with the floor far away.
     */
    fun addGroundLine3D(from: Vector3f, to: Vector3f, color: Vector3f, lifetime: Int = 1) {
        if (lines.size + groundLines.size >= MAX_LINES) return
        groundLines.add(Line3D(Vector3f(from), Vector3f(to), Vector3f(color), lifetime))
    }

    fun addLine3D(from: Vector3f, to: Vector3f, color: Vector3f = Vector3f(0f, 1f, 0f), lifetime: Int = 1) {
        if (lines.size + groundLines.size >= MAX_LINES) return
        lines.add(Line3D(Vector3f(from), Vector3f(to), Vector3f(color), lifetime))
    }

    fun addThickLineQuad3D(
        from: Vector3f,
        to: Vector3f,
        color: Vector3f = Vector3f(0f, 1f, 0f),
        thickness: Float = 0.08f,
        lifetime: Int = 1,
        /** True for a ribbon lying on a horizontal floor (the grid's X/Z axes): it gets the ground depth pull. */
        ground: Boolean = false,
    ) {
        if (triangles.size + groundTriangles.size >= MAX_TRIANGLES - 2) return
        
        val direction = Vector3f(to).sub(from)
        val lineLength = direction.length()

        if (lineLength < 0.001f) return
        
        val lineDir = direction.normalize()
        val cameraToLine =
            Vector3f(from).sub(cameraManager.transform.worldMatrix.getTranslation(Vector3f())).normalize()
        
        // Ground ribbons lie flat in the floor plane (a camera-facing one would dip half its width below the floor)
        var perpendicular = if (ground) Vector3f(lineDir).cross(0f, 1f, 0f).normalize() else Vector3f(lineDir).cross(cameraToLine).normalize()
        
        if (perpendicular.length() < 0.01f || !perpendicular.isFinite) {
            val up = if (abs(lineDir.y) > 0.9f) Vector3f(0f, 0f, 1f) else Vector3f(0f, 1f, 0f)
            perpendicular = Vector3f(lineDir).cross(up).normalize()
        }
        
        perpendicular.mul(thickness * 0.5f)

        val v1 = Vector3f(from).add(perpendicular)
        val v2 = Vector3f(from).sub(perpendicular)
        val v3 = Vector3f(to).add(perpendicular)
        val v4 = Vector3f(to).sub(perpendicular)
        
        val target = if (ground) groundTriangles else triangles
        target.add(Triangle3D(v1, v2, v3, Vector3f(color), lifetime))
        target.add(Triangle3D(v2, v4, v3, Vector3f(color), lifetime))
    }

    fun addTriangle3D(v1: Vector3f, v2: Vector3f, v3: Vector3f, color: Vector3f, lifetime: Int = 1) {
        if (triangles.size + groundTriangles.size >= MAX_TRIANGLES) return
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
