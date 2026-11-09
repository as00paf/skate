package com.pafoid.skate.engine.assets.loaders

import org.junit.jupiter.api.Test
import org.lwjgl.assimp.Assimp.aiGetErrorString
import org.lwjgl.assimp.Assimp.aiImportFileFromMemory
import org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals
import org.lwjgl.assimp.Assimp.aiReleaseImport
import org.lwjgl.assimp.AIMesh
import org.lwjgl.system.MemoryUtil
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MeshNormalsTest {

    /** Two triangles of a unit quad in the XY plane, counter-clockwise seen from +Z. */
    private val quad = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
    private val quadIndices = intArrayOf(0, 1, 2, 0, 2, 3)

    @Test
    fun NeedsRegeneration_AllZeroNormals_True() {
        assertTrue(MeshNormals.needsRegeneration(FloatArray(12)))
    }

    @Test
    fun NeedsRegeneration_OneRealNormal_False() {
        assertFalse(MeshNormals.needsRegeneration(floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f)))
    }

    @Test
    fun Smooth_CounterClockwiseQuad_PointsTowardsTheViewer() {
        assertNormals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f), MeshNormals.smooth(quad, quadIndices))
    }

    @Test
    fun Smooth_TwoFacesMeetingAtAnEdge_AverageByArea() {
        // A floor triangle (normal +Y, area 2) and a wall triangle (normal +Z, area 1) share the edge (0,0,0)-(2,0,0).
        val positions = floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f, -2f, 0f, 1f, 0f)
        val indices = intArrayOf(0, 1, 2, 0, 1, 3)
        val n = MeshNormals.smooth(positions, indices)
        val expected = floatArrayOf(0f, 4f, 2f).let { v -> val l = sqrt(20f); floatArrayOf(v[0] / l, v[1] / l, v[2] / l) }
        assertNormals(expected, n.copyOfRange(0, 3))
        assertNormals(floatArrayOf(0f, 1f, 0f), n.copyOfRange(6, 9))
        assertNormals(floatArrayOf(0f, 0f, 1f), n.copyOfRange(9, 12))
    }

    @Test
    fun Smooth_VerticesSplitByASeam_ShareOneNormal() {
        // Vertex 4 duplicates vertex 2 (another UV, as a seam would) and belongs to a tilted triangle.
        val positions = quad + floatArrayOf(1f, 1f, 0f, 2f, 1f, 1f)
        val indices = quadIndices + intArrayOf(1, 5, 4)
        val n = MeshNormals.smooth(positions, indices)
        assertNormals(n.copyOfRange(6, 9), n.copyOfRange(12, 15))
    }

    @Test
    fun Smooth_NegativeAndPositiveZeroPositions_AreTheSameVertex() {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, -0f, -0f, -0f)
        val n = MeshNormals.smooth(positions, intArrayOf(0, 1, 2))
        assertNormals(n.copyOfRange(0, 3), n.copyOfRange(9, 12))
    }

    @Test
    fun Smooth_DegenerateTriangle_FallsBackToUp() {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 2f, 0f, 0f)
        assertContentEquals(floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f), MeshNormals.smooth(positions, intArrayOf(0, 1, 2)))
    }

    @Test
    fun AssimpObj_WithoutNormals_GetsThemFromTheImportFlags() {
        // REN-27's sphere.obj: no `vn` lines. The file paths imported without GenSmoothNormals and got none.
        val obj = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1 2 3\nf 1 3 4\n"
        val withoutFlag = importObj(obj, AssimpLoader.IMPORT_FLAGS and aiProcess_GenSmoothNormals.inv())
        assertTrue(MeshNormals.needsRegeneration(withoutFlag.normals), "without the flag: ${withoutFlag.normals.toList()}")
        val imported = importObj(obj, AssimpLoader.IMPORT_FLAGS)
        assertFalse(MeshNormals.needsRegeneration(imported.normals))
        assertEachNormal(floatArrayOf(0f, 0f, 1f), imported.normals)
    }

    @Test
    fun AssimpObj_WithAllZeroNormals_IsDetectedAndRegenerated() {
        val obj = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvn 0 0 0\nf 1//1 2//1 3//1\nf 1//1 3//1 4//1\n"
        val imported = importObj(obj, AssimpLoader.IMPORT_FLAGS)
        assertTrue(MeshNormals.needsRegeneration(imported.normals), "Assimp kept: ${imported.normals.toList()}")
        assertEachNormal(floatArrayOf(0f, 0f, 1f), MeshNormals.smooth(imported.positions, imported.indices))
    }

    private class Imported(val positions: FloatArray, val normals: FloatArray, val indices: IntArray)

    /** The first mesh of [obj] as Assimp imports it with [flags]; missing normals read as zeros, as in AssimpLoader. */
    private fun importObj(obj: String, flags: Int): Imported {
        val bytes = obj.toByteArray()
        val buffer = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
        try {
            val scene = assertNotNull(aiImportFileFromMemory(buffer, flags, "obj"), aiGetErrorString())
            try {
                val mesh = AIMesh.create(scene.mMeshes()!!.get(0))
                val count = mesh.mNumVertices()
                val positions = FloatArray(count * 3)
                val normals = FloatArray(count * 3)
                for (v in 0 until count) {
                    val p = mesh.mVertices().get(v)
                    positions[v * 3] = p.x(); positions[v * 3 + 1] = p.y(); positions[v * 3 + 2] = p.z()
                    mesh.mNormals()?.get(v)?.let { normals[v * 3] = it.x(); normals[v * 3 + 1] = it.y(); normals[v * 3 + 2] = it.z() }
                }
                val indices = IntArray(mesh.mNumFaces() * 3)
                for (f in 0 until mesh.mNumFaces()) {
                    val face = mesh.mFaces().get(f)
                    for (i in 0 until 3) indices[f * 3 + i] = face.mIndices().get(i)
                }
                return Imported(positions, normals, indices)
            } finally {
                aiReleaseImport(scene)
            }
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun assertEachNormal(expected: FloatArray, normals: FloatArray) {
        for (v in 0 until normals.size / 3) assertNormals(expected, normals.copyOfRange(v * 3, v * 3 + 3))
    }

    private fun assertNormals(expected: FloatArray, actual: FloatArray) {
        assertTrue(expected.size == actual.size && expected.indices.all { abs(expected[it] - actual[it]) < 1e-5f },
            "expected ${expected.toList()} but was ${actual.toList()}")
    }
}
