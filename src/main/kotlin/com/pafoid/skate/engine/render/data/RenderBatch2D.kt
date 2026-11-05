package com.pafoid.skate.engine.render.data

import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.utils.EntityIdEncoder
import com.pafoid.skate.engine.utils.RenderConsts2D.COLOR_LOCATION
import com.pafoid.skate.engine.utils.RenderConsts2D.ENTITY_ID_LOCATION
import com.pafoid.skate.engine.utils.RenderConsts2D.POS_LOCATION
import com.pafoid.skate.engine.utils.RenderConsts2D.TEX_COORDS_LOCATION
import com.pafoid.skate.engine.utils.RenderConsts2D.TEX_ID_LOCATION
import com.pafoid.skate.engine.utils.RenderConsts2D.MAX_BATCH_TEXTURES
import com.pafoid.skate.engine.utils.RenderConsts2D.SAMPLER_INDICES
import com.pafoid.skate.engine.utils.RenderConsts2D.VERTEX_SIZE
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector2f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30.GL_TEXTURE0
import org.lwjgl.opengl.GL30.GL_TRIANGLES
import org.lwjgl.opengl.GL30.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL30.glActiveTexture
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDrawElements
import org.lwjgl.opengl.GL30.glEnableVertexAttribArray

class RenderBatch2D(
    private val maxBatchSize: Int,
    private val zIndex: Int,
    private val renderer: Renderer2D
) : Comparable<RenderBatch2D> {

    private val sprites = arrayOfNulls<Renderable2D>(maxBatchSize * 4)
    private var numSprites = 0
    private var hasRoom = true
    private var vertices: FloatArray = FloatArray(maxBatchSize * 4 * VERTEX_SIZE)
    private val textureSlots = mutableListOf<Texture>()

    private var vaoId = 0
    private var vbo: RhiBuffer? = null
    private var ebo: RhiBuffer? = null
    private var input: RhiVertexInput? = null
    // The shader samples a fixed uTextures[MAX_BATCH_TEXTURES] array, so never exceed it even if the GPU has more units
    private val maxTextureSlots = minOf(GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS), MAX_BATCH_TEXTURES)

    // Reusable objects to minimize allocations in hot loop
    private val currentPos = Vector4f()

    fun start() {
        val device = RhiContext.require()
        vbo = device.createBuffer(BufferDesc(BufferUsage.VERTEX, vertices.size.toLong() * Float.SIZE_BYTES, BufferUpdate.DYNAMIC))
        ebo = device.createBuffer(BufferUsage.INDEX, BufferUpdate.STATIC, generateIndices())
        input = device.createVertexInput(device.createVertexLayout(VertexLayouts.BATCH_2D), listOf(vbo!!), ebo)
        vaoId = input!!.nativeHandle.toInt()
    }

    fun addSprite(renderable2D: Renderable2D) {
        // Get index and add renderObject
        val index = numSprites
        sprites[index] = renderable2D
        numSprites++

        val texture = renderable2D.spriteRenderer.sprite.texture
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

    fun render(shader: Shader) {
        var rebufferData = false
        for (i in 0 until numSprites) {
            loadVertexProperties(i)
            rebufferData = true
        }

        if (rebufferData) {
            vbo?.update(0, vertices)
        }

        // Use shader
        shader.start()

        shader.uploadMat4f(Uniforms.PROJECTION, renderer.camera.projection)
        shader.uploadMat4f(Uniforms.VIEW, renderer.camera.view)

        // Bind textures to texture units starting at GL_TEXTURE0 so sampler indices match slot indices
        for (i in 0 until textureSlots.size) {
            glActiveTexture(GL_TEXTURE0 + i)
            glBindTexture(GL_TEXTURE_2D, textureSlots[i].texId)
        }
        // Upload sampler indices that map uTextures[0] -> texture unit 0, etc.
        shader.uploadIntArray(Uniforms.TEXTURES, SAMPLER_INDICES)

        glBindVertexArray(vaoId)
        glEnableVertexAttribArray(POS_LOCATION)
        glEnableVertexAttribArray(COLOR_LOCATION)
        glEnableVertexAttribArray(TEX_COORDS_LOCATION)
        glEnableVertexAttribArray(TEX_ID_LOCATION)
        glEnableVertexAttribArray(ENTITY_ID_LOCATION)

        glDrawElements(GL_TRIANGLES, numSprites * 6, GL_UNSIGNED_INT, 0)

        glBindVertexArray(0)

        shader.stop()
    }

    /**
     * Finds the texture slot ID for a given texture.
     * Returns 0 if no texture, or slot index + 1 if found.
     */
    private fun findTextureId(texture: Texture?): Int {
        // Return -1 when there is no texture to indicate 'no texture' sentinel
        if (texture == null) return -1
        for (i in textureSlots.indices) {
            if (textureSlots[i] == texture) {
                // Use zero-based texture slot indices that map directly to sampler indices
                return i
            }
        }
        return -1
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
        texCoord: Vector2f,
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
        val renderable = sprites[index] ?: return
        val go = renderable.spriteRenderer.gameObject ?: return

        // Find offset within array (4 vertices per sprite)
        var offset = index * 4 * VERTEX_SIZE

        val color = renderable.spriteRenderer.color
        val texCoords = renderable.spriteRenderer.sprite.texCoords
        val texId = findTextureId(renderable.spriteRenderer.sprite.texture)
        val entityId = EntityIdEncoder.encode(go.uId)

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

            currentPos.mul(renderable.position)

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

    override fun compareTo(other: RenderBatch2D): Int {
        return Integer.compare(this.zIndex, other.zIndex)
    }

    fun clear() {
        numSprites = 0
        textureSlots.clear()
        hasRoom = true
    }

    fun destroy() {
        input?.close()
        vbo?.close()
        ebo?.close()
        input = null; vbo = null; ebo = null
        vaoId = 0
    }
}

