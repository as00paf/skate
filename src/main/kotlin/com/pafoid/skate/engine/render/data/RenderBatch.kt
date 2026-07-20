package com.pafoid.skate.engine.render.data

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.utils.EntityIdEncoder
import com.pafoid.skate.engine.utils.RenderConsts.COLOR_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts.COLOR_SIZE
import com.pafoid.skate.engine.utils.RenderConsts.ENTITY_ID_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts.ENTITY_ID_SIZE
import com.pafoid.skate.engine.utils.RenderConsts.POS_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts.POS_SIZE
import com.pafoid.skate.engine.utils.RenderConsts.TEX_COORDS_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts.TEX_COORDS_SIZE
import com.pafoid.skate.engine.utils.RenderConsts.TEX_ID_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts.TEX_ID_SIZE
import com.pafoid.skate.engine.utils.RenderConsts.VERTEX_SIZE
import com.pafoid.skate.engine.utils.RenderConsts.VERTEX_SIZE_BYTES
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Matrix4f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL30.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL30.GL_ELEMENT_ARRAY_BUFFER
import org.lwjgl.opengl.GL30.GL_FLOAT
import org.lwjgl.opengl.GL30.GL_STATIC_DRAW
import org.lwjgl.opengl.GL30.GL_TEXTURE0
import org.lwjgl.opengl.GL30.GL_TRIANGLES
import org.lwjgl.opengl.GL30.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL30.glActiveTexture
import org.lwjgl.opengl.GL30.glBindBuffer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glBufferData
import org.lwjgl.opengl.GL30.glBufferSubData
import org.lwjgl.opengl.GL30.glDisableVertexAttribArray
import org.lwjgl.opengl.GL30.glDrawElements
import org.lwjgl.opengl.GL30.glEnableVertexAttribArray
import org.lwjgl.opengl.GL30.glGenBuffers
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.opengl.GL30.glVertexAttribPointer

class RenderBatch(
    private val maxBatchSize: Int,
    private val zIndex: Int,
    private val renderer: Renderer2D
) : Comparable<RenderBatch> {

    private val sprites = arrayOfNulls<SpriteRenderer>(maxBatchSize * 4)
    private var numSprites = 0
    private var hasRoom = true
    private var vertices: FloatArray = FloatArray(maxBatchSize * 4 * VERTEX_SIZE)
    private val textureSlots = mutableListOf<Texture>()

    private var vaoId = 0
    private var vboId = 0
    private val maxTextureSlots = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS)

    // Reusable objects to minimize allocations in hot loop
    private val transformMatrix = Matrix4f()
    private val currentPos = Vector4f()

    fun start() {
        // Generate and bind a Vertex Array Object
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)

        // Allocate space for vertices
        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertices.size.toLong() * Float.SIZE_BYTES, GL_DYNAMIC_DRAW)

        // Create and upload indices buffer
        val eboId = glGenBuffers()
        val indices = generateIndices()
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW)

        // Enable the buffer attribute pointers
        glVertexAttribPointer(0, POS_SIZE, GL_FLOAT, false, VERTEX_SIZE_BYTES, POS_OFFSET.toLong())
        glEnableVertexAttribArray(0)

        glVertexAttribPointer(1, COLOR_SIZE, GL_FLOAT, false, VERTEX_SIZE_BYTES, COLOR_OFFSET.toLong())
        glEnableVertexAttribArray(1)

        glVertexAttribPointer(2, TEX_COORDS_SIZE, GL_FLOAT, false, VERTEX_SIZE_BYTES, TEX_COORDS_OFFSET.toLong())
        glEnableVertexAttribArray(2)

        glVertexAttribPointer(3, TEX_ID_SIZE, GL_FLOAT, false, VERTEX_SIZE_BYTES, TEX_ID_OFFSET.toLong())
        glEnableVertexAttribArray(3)
        
        glVertexAttribPointer(4, ENTITY_ID_SIZE, GL_FLOAT, false, VERTEX_SIZE_BYTES, ENTITY_ID_OFFSET.toLong())
        glEnableVertexAttribArray(4)
    }

    fun addSprite(spr: SpriteRenderer) {
        // Get index and add renderObject
        val index = numSprites
        sprites[index] = spr
        numSprites++

        val texture = spr.sprite.texture
        if (texture != null) {
            if (!textureSlots.contains(texture)) {
                textureSlots.add(texture)
            }
        }

        // Add properties to local vertices array
        loadVertexProperties(index)

        if (numSprites >= maxBatchSize) {
            hasRoom = false
        }
    }

    fun render(shader: Shader = renderer.shader) {
        var rebufferData = false
        for (i in 0 until numSprites) {
            loadVertexProperties(i)
            rebufferData = true
        }

        if (rebufferData) {
            glBindBuffer(GL_ARRAY_BUFFER, vboId)
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertices)
        }

        // Use shader
        shader.start()

        val viewMatrix = renderer.camera.createViewMatrix()
        val projectionMatrix = renderer.camera.createProjectionMatrix()
        shader.uploadMat4f(Uniforms.PROJECTION, projectionMatrix)
        shader.uploadMat4f(Uniforms.VIEW, viewMatrix)

        for (i in 0 until textureSlots.size) {
            glActiveTexture(GL_TEXTURE0 + i + 1)
            glBindTexture(GL_TEXTURE_2D, textureSlots[i].texId)
        }
        shader.uploadIntArray(Uniforms.TEXTURES, intArrayOf(0, 1, 2, 3, 4, 5, 6, 7))

        glBindVertexArray(vaoId)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        glEnableVertexAttribArray(2)
        glEnableVertexAttribArray(3)
        glEnableVertexAttribArray(4)

        glDrawElements(GL_TRIANGLES, numSprites * 6, GL_UNSIGNED_INT, 0)

        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
        glDisableVertexAttribArray(2)
        glDisableVertexAttribArray(3)
        glDisableVertexAttribArray(4)
        glBindVertexArray(0)

        shader.stop()
    }

    /**
     * Finds the texture slot ID for a given texture.
     * Returns 0 if no texture, or slot index + 1 if found.
     */
    private fun findTextureId(texture: Texture?): Int {
        if (texture == null) return 0
        for (i in textureSlots.indices) {
            if (textureSlots[i] == texture) {
                return i + 1
            }
        }
        return 0
    }

    /**
     * Loads a single vertex's properties into the vertices array.
     * 
     * @param offset The starting offset in the vertices array
     * @param position The vertex position (already transformed)
     * @param color The vertex color
     * @param texCoord The texture coordinate
     * @param texId The texture slot ID
     * @param entityId The entity ID
     */
    private fun loadVertex(
        offset: Int,
        position: Vector4f,
        color: Vector4f,
        texCoord: org.joml.Vector2f,
        texId: Int,
        entityId: Float
    ) {
        // Position
        vertices[offset] = position.x
        vertices[offset + 1] = position.y

        // Color
        vertices[offset + 2] = color.x
        vertices[offset + 3] = color.y
        vertices[offset + 4] = color.z
        vertices[offset + 5] = color.w

        // Texture coordinates
        vertices[offset + 6] = texCoord.x
        vertices[offset + 7] = texCoord.y

        // Texture ID
        vertices[offset + 8] = texId.toFloat()

        // Entity ID
        vertices[offset + 9] = entityId
    }

    /**
     * Loads vertex properties for a sprite into the vertices array.
     * Handles both rotated and non-rotated sprites.
     */
    private fun loadVertexProperties(index: Int) {
        val sprite = sprites[index] ?: return
        val transform = sprite.gameObject.getComponent<Transform>() ?: return

        // Find offset within array (4 vertices per sprite)
        var offset = index * 4 * VERTEX_SIZE

        val color = sprite.color
        val texCoords = sprite.sprite.texCoords
        val texId = findTextureId(sprite.sprite.texture)
        val entityId = EntityIdEncoder.encode(sprite.gameObject.uId)

        val isRotated = transform.rotation.z != 0f
        if (isRotated) {
            transformMatrix.identity()
            transformMatrix.translate(transform.translation.x, transform.translation.y, 0f)
            transformMatrix.rotate(Math.toRadians(transform.rotation.z.toDouble()).toFloat(), 0f, 0f, 1f)
            transformMatrix.scale(transform.scale.x, transform.scale.y, 1f)
        }

        // Load 4 vertices per sprite (quad corners)
        for (i in 0..3) {
            currentPos.set(0f, 0f, 0f, 1f)
            when (i) {
                1 -> currentPos.x = 1f
                2 -> {
                    currentPos.x = 1f
                    currentPos.y = 1f
                }

                3 -> currentPos.y = 1f
            }

            // Transform position
            if (isRotated) {
                currentPos.mul(transformMatrix)
            } else {
                currentPos.x = currentPos.x * transform.scale.x + transform.translation.x
                currentPos.y = currentPos.y * transform.scale.y + transform.translation.y
            }

            loadVertex(offset, currentPos, color, texCoords[i], texId, entityId)
            offset += VERTEX_SIZE
        }
    }

    private fun generateIndices(): IntArray {
        // 6 indices per quad (3 per triangle)
        val elements = IntArray(6 * maxBatchSize)
        for (i in 0 until maxBatchSize) {
            loadElementIndices(elements, i)
        }
        return elements
    }

    private fun loadElementIndices(elements: IntArray, index: Int) {
        val offsetArrayIndex = 6 * index
        val offset = 4 * index

        // 3, 2, 0, 0, 2, 1        7, 6, 4, 4, 6, 5
        // Triangle 1
        elements[offsetArrayIndex] = offset + 3
        elements[offsetArrayIndex + 1] = offset + 2
        elements[offsetArrayIndex + 2] = offset + 0

        // Triangle 2
        elements[offsetArrayIndex + 3] = offset + 0
        elements[offsetArrayIndex + 4] = offset + 2
        elements[offsetArrayIndex + 5] = offset + 1
    }

    fun hasRoom(): Boolean {
        return hasRoom
    }

    fun hasTextureRoom(): Boolean {
        return textureSlots.size < maxTextureSlots
    }

    fun hasTexture(tex: Texture): Boolean {
        return textureSlots.contains(tex)
    }

    fun zIndex(): Int {
        return zIndex
    }

    override fun compareTo(other: RenderBatch): Int {
        return Integer.compare(this.zIndex, other.zIndex)
    }

    fun clear() {
        numSprites = 0
        textureSlots.clear()
        hasRoom = true
    }

    fun destroy() {
        if (vaoId != 0) {
            org.lwjgl.opengl.GL30.glDeleteVertexArrays(vaoId)
            vaoId = 0
        }
        if (vboId != 0) {
            org.lwjgl.opengl.GL30.glDeleteBuffers(vboId)
            vboId = 0
        }
    }
}

