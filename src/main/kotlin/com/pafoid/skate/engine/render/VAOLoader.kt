package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.data.models.RawModel
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL30.GL_ELEMENT_ARRAY_BUFFER
import org.lwjgl.opengl.GL30.GL_FLOAT
import org.lwjgl.opengl.GL30.GL_INT
import org.lwjgl.opengl.GL30.GL_STATIC_DRAW
import org.lwjgl.opengl.GL30.GL_TRIANGLES
import org.lwjgl.opengl.GL30.glBindBuffer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glBufferData
import org.lwjgl.opengl.GL30.glDeleteBuffers
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenBuffers
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.opengl.GL30.glVertexAttribIPointer
import org.lwjgl.opengl.GL30.glVertexAttribPointer
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Vertex Array Object (VAO) loader for OpenGL mesh data.
 *
 * This class is responsible for creating and managing OpenGL vertex buffer objects (VBOs)
 * and vertex array objects (VAOs). It handles the upload of vertex data (positions, normals,
 * texture coordinates, tangents, colors, etc.) to the GPU and tracks all created resources
 * for proper cleanup.
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
 * ## Memory Management
 *
 * This class tracks all created VAOs and VBOs in internal maps:
 * - [vaos]: List of all created VAO IDs
 * - [vbos]: List of all created VBO IDs
 * - [vaoVboMap]: Mapping of VAO ID to its associated VBO IDs
 *
 * Call [cleanUp()] to delete all tracked resources, or [deleteVAO()] to remove a specific VAO
 * and its associated VBOs. Failure to call these methods will result in OpenGL resource leaks.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val loader = VAOLoader()
 * val model = loader.loadToVAO(
 *     positions = floatArrayOf(...),
 *     textureCoords = floatArrayOf(...),
 *     normals = floatArrayOf(...),
 *     indices = intArrayOf(...)
 * )
 *
 * // Later, when done with the model:
 * loader.deleteVAO(model.vaoId)
 *
 * // Or cleanup all resources:
 * loader.cleanUp()
 * ```
 */
class VAOLoader {

    private val vaos = mutableListOf<Int>()
    private val vbos = mutableListOf<Int>()
    private val vaoVboMap = mutableMapOf<Int, MutableList<Int>>()

    /**
     * Loads vertex data into a new VAO with full attribute support.
     *
     * This method creates a VAO and uploads all provided vertex attribute arrays to the GPU.
     * It automatically enables the appropriate vertex attribute arrays based on which data
     * is provided (non-empty arrays only).
     *
     * @param positions Vertex position data (x, y, z triplets). **Required** - must not be empty.
     * @param textureCoords UV texture coordinates (u, v pairs). **Required** - must not be empty.
     * @param normals Vertex normal vectors (x, y, z triplets). **Required** - must not be empty.
     * @param indices Index buffer for indexed drawing. If empty, non-indexed rendering is used.
     * @param rawVertices Optional raw vertex data for custom processing. Default: empty.
     * @param tangents Optional tangent vectors for normal mapping (x, y, z triplets). Default: empty.
     * @param colors Optional vertex colors (r, g, b, a quads). Default: empty.
     * @param drawMode OpenGL primitive type (e.g., [GL_TRIANGLES]). Default: [GL_TRIANGLES].
     * @param textureCoords1 Optional secondary UV coordinates for lightmapping, etc. Default: empty.
     * @param joints Optional bone joint indices for skeletal animation (4 joints per vertex). Default: empty.
     * @param weights Optional bone weights for skeletal animation (4 weights per vertex). Default: empty.
     * @return A [RawModel] containing the VAO ID, vertex count, and metadata for rendering.
     *
     * ## Vertex Count Calculation
     * - If [indices] is provided: vertex count = indices.size
     * - Otherwise: vertex count = positions.size / 3
     *
     * ## Enabled Attributes
     * The returned [RawModel] includes a list of enabled attribute locations, which should be
     * passed to VAO binding functions to minimize redundant OpenGL calls.
     */
    fun loadToVAO(
        positions: FloatArray, 
        textureCoords: FloatArray, 
        normals: FloatArray, 
        indices: IntArray, 
        rawVertices: FloatArray = floatArrayOf(),
        tangents: FloatArray = floatArrayOf(),
        colors: FloatArray = floatArrayOf(),
        drawMode: Int = GL_TRIANGLES,
        textureCoords1: FloatArray = floatArrayOf(),
        joints: IntArray = intArrayOf(),
        weights: FloatArray = floatArrayOf()
    ): RawModel {
        val vaoId = createVAO()
        val currentVbos = mutableListOf<Int>()
        val enabledAttribs = mutableListOf<Int>()
        
        if (indices.isNotEmpty()) {
            currentVbos.add(bindIndicesBuffer(indices))
        }
        currentVbos.add(storeDataInAttribList(0, 3, positions))
        enabledAttribs.add(0)
        
        currentVbos.add(storeDataInAttribList(1, 2, textureCoords))
        enabledAttribs.add(1)
        
        currentVbos.add(storeDataInAttribList(2, 3, normals))
        enabledAttribs.add(2)
        
        if (tangents.isNotEmpty()) {
            currentVbos.add(storeDataInAttribList(3, 3, tangents))
            enabledAttribs.add(3)
        }
        if (colors.isNotEmpty()) {
            currentVbos.add(storeDataInAttribList(4, 4, colors))
            enabledAttribs.add(4)
        }
        if (textureCoords1.isNotEmpty()) {
            currentVbos.add(storeDataInAttribList(5, 2, textureCoords1))
            enabledAttribs.add(5)
        }
        if (joints.isNotEmpty()) {
            currentVbos.add(storeDataInAttribListInt(6, 4, joints))
            enabledAttribs.add(6)
        }
        if (weights.isNotEmpty()) {
            currentVbos.add(storeDataInAttribList(7, 4, weights))
            enabledAttribs.add(7)
        }
        
        unbindVAO()
        
        vaoVboMap[vaoId] = currentVbos

        val vertexCount = if (indices.isNotEmpty()) indices.size else positions.size / 3
        return RawModel(vaoId, vertexCount, rawVertices, drawMode, enabledAttribs)
    }

    /**
     * Stores integer data in a vertex attribute buffer.
     *
     * Creates a VBO and uploads integer data (typically joint indices for skeletal animation)
     * to the GPU using [glVertexAttribIPointer] for integer attribute handling.
     *
     * @param attributeNumber The vertex attribute location (typically 6 for joint indices).
     * @param coordinateSize Number of components per vertex (e.g., 4 for vec4/int4).
     * @param data The integer array to upload.
     * @return The generated VBO ID for tracking.
     */
    private fun storeDataInAttribListInt(attributeNumber: Int, coordinateSize: Int, data: IntArray): Int {
        val vboId = glGenBuffers()
        vbos.add(vboId)
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        val buffer = storeDataInIntBuffer(data)
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        glVertexAttribIPointer(attributeNumber, coordinateSize, GL_INT, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        return vboId
    }

    /**
     * Loads a simple vertex buffer with only position data.
     *
     * This is a simplified overload for cases where only vertex positions are needed
     * (e.g., debug rendering, simple geometry). Creates a VAO with a single vertex
     * attribute at location 0.
     *
     * @param positions Vertex position data. The [coordinateSize] determines how many
     *        components per vertex (typically 2 for 2D or 3 for 3D).
     * @param coordinateSize Number of float components per vertex (2, 3, or 4).
     * @param rawVertices Optional raw vertex data for custom processing. Default: empty.
     * @return A [RawModel] containing the VAO ID and vertex count.
     */
    fun loadToVAO(positions: FloatArray, coordinateSize: Int, rawVertices: FloatArray = floatArrayOf()): RawModel {
        val vaoId = createVAO()
        val vboId = storeDataInAttribList(0, coordinateSize, positions)
        unbindVAO()
        
        vaoVboMap[vaoId] = mutableListOf(vboId)
        
        return RawModel(vaoId, positions.size / coordinateSize, rawVertices, enabledAttributes = listOf(0))
    }

    /**
     * Creates a new Vertex Array Object (VAO).
     *
     * Generates a VAO using [glGenVertexArrays] and binds it for subsequent vertex attribute setup.
     * The VAO ID is tracked in [vaos] for cleanup.
     *
     * @return The generated VAO ID.
     */
    private fun createVAO(): Int {
        val vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)
        vaos.add(vaoId)
        return vaoId
    }

    /**
     * Stores float data in a vertex attribute buffer.
     *
     * Creates a VBO and uploads float data to the GPU using [glVertexAttribPointer].
     * The VBO ID is tracked in [vbos] for cleanup.
     *
     * @param attributeNumber The vertex attribute location (0-7).
     * @param coordinateSize Number of components per vertex (2, 3, or 4).
     * @param data The float array to upload.
     * @return The generated VBO ID for tracking.
     */
    private fun storeDataInAttribList(attributeNumber: Int, coordinateSize: Int, data: FloatArray): Int {
        val vboId = glGenBuffers()
        vbos.add(vboId)
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        val buffer = storeDataInFloatBuffer(data)
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        glVertexAttribPointer(attributeNumber, coordinateSize, GL_FLOAT, false, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        return vboId
    }

    /**
     * Unbinds the currently bound VAO.
     *
     * Binds VAO 0 to prevent accidental modifications to the VAO state.
     * Should be called after setting up all vertex attributes for a VAO.
     */
    private fun unbindVAO() {
        glBindVertexArray(0)
    }

    /**
     * Creates and binds an index buffer (EBO) for indexed rendering.
     *
     * Uploads the index array to the GPU and binds it to [GL_ELEMENT_ARRAY_BUFFER].
     * The VBO ID is tracked in [vbos] for cleanup.
     *
     * @param indices The index array defining the draw order of vertices.
     * @return The generated VBO ID for tracking.
     */
    private fun bindIndicesBuffer(indices: IntArray): Int {
        val vboId = glGenBuffers()
        vbos.add(vboId)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboId)
        val buffer = storeDataInIntBuffer(indices)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        return vboId
    }

    /**
     * Creates a FloatBuffer and uploads float array data to it.
     *
     * This helper method allocates a direct NIO buffer, copies the float array data,
     * and flips the buffer for reading by OpenGL.
     *
     * @param data The float array to convert.
     * @return A flipped FloatBuffer ready for OpenGL upload.
     */
    private fun storeDataInFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = BufferUtils.createFloatBuffer(data.size)
        buffer.put(data)
        buffer.flip()
        return buffer
    }

    /**
     * Creates an IntBuffer and uploads integer array data to it.
     *
     * This helper method allocates a direct NIO buffer, copies the integer array data,
     * and flips the buffer for reading by OpenGL. Used for index buffers and joint data.
     *
     * @param data The integer array to convert.
     * @return A flipped IntBuffer ready for OpenGL upload.
     */
    private fun storeDataInIntBuffer(data: IntArray): IntBuffer {
        val buffer = BufferUtils.createIntBuffer(data.size)
        buffer.put(data)
        buffer.flip()
        return buffer
    }

    /**
     * Deletes all tracked OpenGL resources (VAOs and VBOs).
     *
     * This method should be called when shutting down the renderer or when all
     * mesh data should be released from GPU memory. After calling this method,
     * all previously created [RawModel] instances will have invalid VAO/VBO IDs
     * and must not be used for rendering.
     *
     * **Warning**: This deletes ALL resources created by this VAOLoader instance.
     * If you need to delete individual models, use [deleteVAO] instead.
     */
    fun cleanUp() {
        vaos.forEach { glDeleteVertexArrays(it) }
        vbos.forEach { glDeleteBuffers(it) }
        vaos.clear()
        vbos.clear()
        vaoVboMap.clear()
    }

    /**
     * Deletes a specific VAO and its associated VBOs.
     *
     * This method removes a single VAO and all VBOs that were created with it,
     * freeing GPU memory for that specific mesh. The VAO ID is removed from
     * internal tracking lists to prevent double-deletion.
     *
     * @param vaoId The VAO ID to delete, typically from [RawModel.vaoId].
     *
     * ## Usage
     * ```kotlin
     * val model = loader.loadToVAO(...)
     * // ... use model for rendering ...
     * loader.deleteVAO(model.vaoId)
     * ```
     *
     * **Note**: If the [vaoId] is not found in the tracked list, this method does nothing.
     */
    fun deleteVAO(vaoId: Int) {
        if (vaos.contains(vaoId)) {
            glDeleteVertexArrays(vaoId)
            vaos.remove(vaoId)
            
            vaoVboMap[vaoId]?.let { associatedVbos ->
                associatedVbos.forEach { vboId ->
                    glDeleteBuffers(vboId)
                    vbos.remove(vboId)
                }
                vaoVboMap.remove(vaoId)
            }
        }
    }

}