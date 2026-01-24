package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector4f
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30.*

class RenderBatch(
    private val maxBatchSize: Int,
    private val zIndex: Int,
    private val renderer: Renderer2D // Callback reference for texture slots? Or just pass logic here
) : Comparable<RenderBatch> {

    private val POS_SIZE = 2
    private val COLOR_SIZE = 4
    private val TEX_COORDS_SIZE = 2
    private val TEX_ID_SIZE = 1
    private val ENTITY_ID_SIZE = 1

    private val POS_OFFSET = 0
    private val COLOR_OFFSET = POS_OFFSET + POS_SIZE * Float.SIZE_BYTES
    private val TEX_COORDS_OFFSET = COLOR_OFFSET + COLOR_SIZE * Float.SIZE_BYTES
    private val TEX_ID_OFFSET = TEX_COORDS_OFFSET + TEX_COORDS_SIZE * Float.SIZE_BYTES
    private val ENTITY_ID_OFFSET = TEX_ID_OFFSET + TEX_ID_SIZE * Float.SIZE_BYTES
    private val VERTEX_SIZE = 10
    private val VERTEX_SIZE_BYTES = VERTEX_SIZE * Float.SIZE_BYTES

    private val sprites = arrayOfNulls<SpriteRenderer>(maxBatchSize * 4)
    private var numSprites = 0
    private var hasRoom = true
    private var vertices: FloatArray = FloatArray(maxBatchSize * 4 * VERTEX_SIZE)
    private val texSlots = mutableListOf<Texture>()

    private var vaoId = 0
    private var vboId = 0
    private var maxTextureSlots = 8 // Default, should be queried

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

        if (spr.getTexture() != null) {
            if (!texSlots.contains(spr.getTexture())) {
                texSlots.add(spr.getTexture()!!)
            }
        }

        // Add properties to local vertices array
        loadVertexProperties(index)

        if (numSprites >= maxBatchSize) {
            hasRoom = false
        }
    }

    fun render(shader: Shader = Renderer2D.shader) {
        var rebufferData = false
        for (i in 0 until numSprites) {
            val spr = sprites[i]
            if (spr?.isDirty() == true) {
                loadVertexProperties(i)
                spr.setClean()
                rebufferData = true
            }
        }

        if (rebufferData) {
            glBindBuffer(GL_ARRAY_BUFFER, vboId)
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertices)
        }

        // Use shader
        shader.start()

        val viewMatrix = Renderer2D.camera.createViewMatrix()
        val projectionMatrix = Renderer2D.camera.createProjectionMatrix()
        shader.uploadMat4f("uProjection", projectionMatrix)
        shader.uploadMat4f("uView", viewMatrix)

        for (i in 0 until texSlots.size) {
            glActiveTexture(GL_TEXTURE0 + i + 1)
            texSlots[i].bind()
        }
        shader.uploadIntArray("uTextures", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7))

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

        for (i in 0 until texSlots.size) {
            texSlots[i].unbind()
        }
        shader.stop()
    }

    private fun loadVertexProperties(index: Int) {
        val sprite = sprites[index] ?: return

        // Find offset within array (4 vertices per sprite)
        var offset = index * 4 * VERTEX_SIZE

        val color = sprite.getColor()
        val texCoords = sprite.getTexCoords()

        var texId = 0
        if (sprite.getTexture() != null) {
            for (i in 0 until texSlots.size) {
                if (texSlots[i] == sprite.getTexture()) {
                    texId = i + 1
                    break
                }
            }
        }
        
        val isRotated = sprite.gameObject.transform.rotation.z != 0f
        var transformMatrix = Matrix4f().identity()
        if (isRotated) {
            transformMatrix.translate(sprite.gameObject.transform.translation.x, sprite.gameObject.transform.translation.y, 0f)
            transformMatrix.rotate(Math.toRadians(sprite.gameObject.transform.rotation.z.toDouble()).toFloat(), 0f, 0f, 1f)
            transformMatrix.scale(sprite.gameObject.transform.scale.x, sprite.gameObject.transform.scale.y, 1f)
        }


        // Add vertices with the appropriate properties
        val xAdd = 1.0f
        val yAdd = 1.0f
        
        for (i in 0..3) {
            var currentPos = Vector4f(0f, 0f, 0f, 1f)
            if (i == 1) {
                currentPos = Vector4f(xAdd, 0f, 0f, 1f)
            } else if (i == 2) {
                currentPos = Vector4f(xAdd, yAdd, 0f, 1f)
            } else if (i == 3) {
                currentPos = Vector4f(0f, yAdd, 0f, 1f)
            }
            
            if (isRotated) {
                 currentPos.mul(transformMatrix)
            } else {
                 currentPos.x = currentPos.x * sprite.gameObject.transform.scale.x + sprite.gameObject.transform.translation.x
                 currentPos.y = currentPos.y * sprite.gameObject.transform.scale.y + sprite.gameObject.transform.translation.y
            }
            

            // Load position
            vertices[offset] = currentPos.x
            vertices[offset + 1] = currentPos.y

            // Load color
            vertices[offset + 2] = color.x
            vertices[offset + 3] = color.y
            vertices[offset + 4] = color.z
            vertices[offset + 5] = color.w

            // Load texture coordinates
            vertices[offset + 6] = texCoords[i].x
            vertices[offset + 7] = texCoords[i].y

            // Load texture id
            vertices[offset + 8] = texId.toFloat()
            
            // Load entity id
            vertices[offset + 9] = sprite.gameObject.getUid().toFloat() + 1 // +1 because 0 is reserved for "nothing"

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
        return texSlots.size < 8
    }

    fun hasTexture(tex: Texture): Boolean {
        return texSlots.contains(tex)
    }

    fun zIndex(): Int {
        return zIndex
    }

    override fun compareTo(other: RenderBatch): Int {
        return Integer.compare(this.zIndex, other.zIndex)
    }
}