package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Checks the skateboard collider against the bounds of the source glb, so visual and body stay aligned. */
class SkateboardAlignmentTest {

    private class Bounds(val min: FloatArray = floatArrayOf(1e9f, 1e9f, 1e9f), val max: FloatArray = floatArrayOf(-1e9f, -1e9f, -1e9f))

    /** World-space bounds of every mesh in the glb after applying node matrices (column-major, like glTF). */
    private fun glbBounds(file: File): Bounds {
        val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val jsonLength = buf.getInt(12)
        val json = Json.parseToJsonElement(String(file.readBytes(), 20, jsonLength)).jsonObject
        val nodes = json["nodes"]!!.jsonArray
        val meshes = json["meshes"]!!.jsonArray
        val accessors = json["accessors"]!!.jsonArray
        val bounds = Bounds()

        fun floats(a: JsonArray) = FloatArray(a.size) { a[it].jsonPrimitive.content.toFloat() }
        fun mul(a: FloatArray, b: FloatArray) = FloatArray(16) { i ->
            val c = i / 4; val r = i % 4
            (0 until 4).sumOf { k -> (a[k * 4 + r] * b[c * 4 + k]).toDouble() }.toFloat()
        }

        fun walk(index: Int, parent: FloatArray) {
            val node = nodes[index].jsonObject
            val m = mul(parent, node["matrix"]?.let { floats(it.jsonArray) } ?: floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
            node["mesh"]?.let { meshIndex ->
                meshes[meshIndex.jsonPrimitive.content.toInt()].jsonObject["primitives"]!!.jsonArray.forEach { prim ->
                    val acc = accessors[prim.jsonObject["attributes"]!!.jsonObject["POSITION"]!!.jsonPrimitive.content.toInt()].jsonObject
                    val lo = floats(acc["min"]!!.jsonArray); val hi = floats(acc["max"]!!.jsonArray)
                    for (corner in 0 until 8) {
                        val p = FloatArray(3) { k -> if (corner shr k and 1 == 1) hi[k] else lo[k] }
                        for (r in 0 until 3) {
                            val w = m[r] * p[0] + m[4 + r] * p[1] + m[8 + r] * p[2] + m[12 + r]
                            bounds.min[r] = minOf(bounds.min[r], w); bounds.max[r] = maxOf(bounds.max[r], w)
                        }
                    }
                }
            }
            (node["children"] as? JsonArray)?.forEach { walk(it.jsonPrimitive.content.toInt(), m) }
        }
        walk(0, floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        return bounds
    }

    @Test
    fun Skateboard_ColliderFitsInsideMeshBoundsAndTouchesGround() {
        val source = File("assets/obj/skateboard_free_model.glb")
        assertTrue(source.exists(), "source model missing: ${source.absolutePath}")
        val board = Skateboard(`3dModel`())
        val s = board.getComponent<Transform>()!!.scale
        val box = board.getComponent<BoxCollider3D>()!!
        val mesh = glbBounds(source).let { b -> Bounds(FloatArray(3) { b.min[it] * s.x }, FloatArray(3) { b.max[it] * s.x }) }

        val half = floatArrayOf(box.halfExtents.x * s.x, box.halfExtents.y * s.y, box.halfExtents.z * s.z)
        val center = floatArrayOf(box.offset.x * s.x, box.offset.y * s.y, box.offset.z * s.z)
        val tol = 0.01f
        for (axis in 0 until 3) {
            assertTrue(center[axis] - half[axis] >= mesh.min[axis] - tol, "collider below mesh on axis $axis")
            assertTrue(center[axis] + half[axis] <= mesh.max[axis] + tol, "collider above mesh on axis $axis")
        }
        // Model origin is the ground contact: mesh and collider both start at y = 0.
        assertEquals(0f, mesh.min[1], tol)
        assertEquals(0f, center[1] - half[1], tol)
        // Length axis is Z for both, and they are centred on the same point.
        assertTrue(mesh.max[2] - mesh.min[2] > mesh.max[0] - mesh.min[0])
        assertEquals((mesh.min[2] + mesh.max[2]) / 2, center[2], tol)
        assertEquals((mesh.min[0] + mesh.max[0]) / 2, center[0], tol)
    }
}
