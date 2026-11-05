package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.render.rhi.AttributeType
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import org.joml.Matrix4f

/**
 * Uploads mesh data (positions, normals, texture coordinates, tangents, colors, skinning) to the GPU and tracks the
 * buffers it creates for cleanup.
 *
 * ## Vertex Attribute Layout
 *
 * The loader uses the following standard attribute locations:
 * - **Location 0**: Vertex positions (3 floats)
 * - **Location 1**: Texture coordinates (2 floats)
 * - **Location 2**: Vertex normals (3 floats)
 * - **Location 3**: Tangents (3 floats, optional)
 * - **Location 4**: Vertex colors (4 floats, optional)
 * - **Location 5**: Secondary texture coordinates (2 floats, optional)
 * - **Location 6**: Joint indices for skeletal animation (4 ints, optional)
 * - **Location 7**: Bone weights for skeletal animation (4 floats, optional)
 *
 * Each attribute gets its own tightly packed buffer ([VertexLayouts.mesh]). Buffers and the vertex input (the VAO
 * whose name is [MeshPart.vaoId]) come from the RHI device ([RhiContext], REN-12 step 2).
 *
 * ## Memory Management
 *
 * Every mesh is tracked by its VAO name. Call [cleanUp] to delete everything, or [deleteVAO] to free one mesh and
 * its buffers. Failure to call these methods leaks GPU memory.
 */
class VAOLoader(private val device: () -> RhiDevice = RhiContext::require) {

    private class GpuMeshBuffers(val input: RhiVertexInput, val buffers: List<RhiBuffer>)

    private val meshes = LinkedHashMap<Int, GpuMeshBuffers>()
    private val layouts = HashMap<List<Pair<Int, AttributeType>>, RhiVertexLayout>()

    /**
     * Uploads a mesh: one buffer per non-empty attribute plus the index buffer.
     *
     * @param positions Vertex position data (x, y, z triplets). **Required** - must not be empty.
     * @param textureCoords UV texture coordinates (u, v pairs). **Required** - must not be empty.
     * @param normals Vertex normal vectors (x, y, z triplets). **Required** - must not be empty.
     * @param indices Index buffer for indexed drawing. If empty, non-indexed rendering is used.
     * @param rawVertices Optional raw vertex data kept on the [MeshPart]. Default: empty.
     * @param tangents Optional tangent vectors for normal mapping (x, y, z triplets). Default: empty.
     * @param colors Optional vertex colors (r, g, b, a quads). Default: empty.
     * @param textureCoords1 Optional secondary UV coordinates. Default: empty.
     * @param joints Optional bone joint indices (4 per vertex, integer attribute). Default: empty.
     * @param weights Optional bone weights (4 per vertex). Default: empty.
     * @return A [MeshPart] with the VAO name, vertex count (index count when indexed) and enabled attribute locations.
     */
    fun loadToVAO(
        positions: FloatArray,
        textureCoords: FloatArray,
        normals: FloatArray,
        indices: IntArray,
        rawVertices: FloatArray = floatArrayOf(),
        tangents: FloatArray = floatArrayOf(),
        colors: FloatArray = floatArrayOf(),
        textureCoords1: FloatArray = floatArrayOf(),
        joints: IntArray = intArrayOf(),
        weights: FloatArray = floatArrayOf(),
        material: Material = Material(),
        inverseBindMatrices: List<Matrix4f> = emptyList(),
    ): MeshPart {
        val d = device()
        val attributes = ArrayList<Pair<Int, AttributeType>>(8)
        val buffers = ArrayList<RhiBuffer>(9)
        fun add(attribute: VertexLayouts.MeshAttribute, data: FloatArray) {
            attributes += attribute.location to attribute.type
            buffers += d.createBuffer(BufferUsage.VERTEX, BufferUpdate.STATIC, data)
        }
        val indexBuffer = if (indices.isNotEmpty()) d.createBuffer(BufferUsage.INDEX, BufferUpdate.STATIC, indices) else null
        add(VertexLayouts.MeshAttribute.POSITION, positions)
        add(VertexLayouts.MeshAttribute.UV, textureCoords)
        add(VertexLayouts.MeshAttribute.NORMAL, normals)
        if (tangents.isNotEmpty()) add(VertexLayouts.MeshAttribute.TANGENT, tangents)
        if (colors.isNotEmpty()) add(VertexLayouts.MeshAttribute.COLOR, colors)
        if (textureCoords1.isNotEmpty()) add(VertexLayouts.MeshAttribute.UV1, textureCoords1)
        if (joints.isNotEmpty()) {
            attributes += VertexLayouts.MeshAttribute.JOINTS.location to VertexLayouts.MeshAttribute.JOINTS.type
            buffers += d.createBuffer(BufferUsage.VERTEX, BufferUpdate.STATIC, joints)
        }
        if (weights.isNotEmpty()) add(VertexLayouts.MeshAttribute.WEIGHTS, weights)

        val vaoId = track(d, attributes, buffers, indexBuffer)
        val vertexCount = if (indices.isNotEmpty()) indices.size else positions.size / 3
        return MeshPart(
            vaoId = vaoId, vertexCount = vertexCount, vertices = rawVertices, texCoords = textureCoords,
            texCoords1 = textureCoords1,
            normals = normals,
            tangents = tangents,
            colors = colors,
            joints = joints,
            weights = weights,
            indices = indices,
            material = material,
            inverseBindMatrices = inverseBindMatrices,
            enabledAttributes = attributes.mapTo(mutableListOf()) { it.first },
        )
    }

    /**
     * Uploads positions only, at attribute location 0 (debug geometry, sky box, full-screen quads).
     *
     * @param coordinateSize Number of float components per vertex (1 to 4).
     * @param rawVertices Optional raw vertex data kept on the [MeshPart]. Default: empty.
     */
    fun loadToVAO(positions: FloatArray, coordinateSize: Int, rawVertices: FloatArray = floatArrayOf()): MeshPart {
        val d = device()
        val type = when (coordinateSize) {
            1 -> AttributeType.FLOAT
            2 -> AttributeType.VEC2
            3 -> AttributeType.VEC3
            4 -> AttributeType.VEC4
            else -> throw IllegalArgumentException("coordinateSize must be 1 to 4, got $coordinateSize")
        }
        val buffer = d.createBuffer(BufferUsage.VERTEX, BufferUpdate.STATIC, positions)
        val vaoId = track(d, listOf(0 to type), listOf(buffer), null)
        return MeshPart(
            vaoId = vaoId,
            vertexCount = positions.size / coordinateSize,
            vertices = rawVertices,
            enabledAttributes = mutableListOf(0)
        )
    }

    private fun track(d: RhiDevice, attributes: List<Pair<Int, AttributeType>>, buffers: List<RhiBuffer>, indices: RhiBuffer?): Int {
        val layout = layouts.getOrPut(attributes) { d.createVertexLayout(VertexLayouts.mesh(attributes)) }
        val input = d.createVertexInput(layout, buffers, indices)
        val vaoId = input.nativeHandle.toInt()
        meshes[vaoId] = GpuMeshBuffers(input, listOfNotNull(indices) + buffers)
        return vaoId
    }

    /**
     * Deletes every mesh this loader created. Their [MeshPart]s must not be drawn afterwards.
     */
    fun cleanUp() {
        meshes.values.forEach(::release)
        meshes.clear()
    }

    /**
     * Deletes the mesh whose VAO name is [vaoId] ([MeshPart.vaoId]) and its buffers; unknown names are ignored.
     */
    fun deleteVAO(vaoId: Int) {
        meshes.remove(vaoId)?.let(::release)
    }

    private fun release(mesh: GpuMeshBuffers) {
        mesh.input.close()
        mesh.buffers.forEach { it.close() }
    }
}
