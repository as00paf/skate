package marki.renderer

import com.pafoid.skate.components.SpriteRenderer
import com.pafoid.skate.core.renderer.Texture
import marki.GameObject
import com.pafoid.skate.core.Window
import org.joml.Matrix4f
import org.joml.Vector4f
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.glDisableVertexAttribArray
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20C.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glGenVertexArrays


class RenderBatch(private var maxBatchSize: Int, private var zIndex: Int, private val renderer: Renderer) :
    Comparable<RenderBatch> {
    // Vertex
    // ======
    // Pos                  Color                                   Text coords         texId       entityId
    // float, float,        float, float, float, float, float,      float, float,       float       float
    private val sprites = arrayOfNulls<SpriteRenderer>(maxBatchSize)
    private var spriteCount = 0
    private var hasRoom = true
    private var vertices = FloatArray(maxBatchSize * 4 * VERTEX_SIZE)
    private var vaoId = -1
    private var vboId = -1
    private val textures = mutableListOf<Texture>()
    private val texSlots = intArrayOf(0, 1, 3, 4, 5, 6, 7)

    fun start() {
        // Generate and bind VAO
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)

        // Allocate space for vertices
        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertices.size * Float.SIZE_BYTES.toLong(), GL_DYNAMIC_DRAW)

        // Create and upload indices buffer
        val eboId = glGenBuffers()
        val indices = generateIndices()
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW)

        // Enable buffer attribute pointers
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

    fun addSprite(sprite: SpriteRenderer) {
        // Get index and add renderObject
        val index = spriteCount
        sprites[index] = sprite
        spriteCount++

        sprite.getTexture()?.let { spriteTexture ->
            if (!textures.contains(spriteTexture)) {
                textures.add(spriteTexture)
            }
        }

        // Add properties to local vertices array
        loadVertexProperties(index)

        if (spriteCount >= maxBatchSize) {
            hasRoom = false
        }
    }

    fun render() {
        var rebufferData = false

        var i = 0
        while (i < spriteCount) {
            val spr = sprites[i]!!
            if (spr.isDirty()) {
                if (!hasTexture(spr.getTexture())) {
                    renderer.destroyGameObject(spr.gameObject)
                    renderer.add(spr.gameObject)
                } else {
                    loadVertexProperties(i)
                    spr.setClean()
                    rebufferData = true
                }
            }

            // TODO: get better solution for this
            if (spr.gameObject.transform.zIndex != zIndex) {
                destroyIfExists(spr.gameObject)
                renderer.add(spr.gameObject)
                i--
            }
            i++
        }

        if (rebufferData) {
            glBindBuffer(GL_ARRAY_BUFFER, vboId)
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertices)
        }

        // Use shader
        val shader = renderer.getBoundShader()
        shader.use()
        shader.uploadMat4f("uProjection", Window.currentScene.camera.getProjectionMatrix())
        shader.uploadMat4f("uView", Window.currentScene.camera.getViewMatrix())

        textures.forEachIndexed { index, texture ->
            glActiveTexture(GL_TEXTURE0 + index + 1)
            texture.bind()
        }
        shader.uploadIntArray("uTextures", texSlots)

        glBindVertexArray(vaoId)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)

        glDrawElements(GL_TRIANGLES, spriteCount * 6, GL_UNSIGNED_INT, 0)

        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
        glBindVertexArray(0)

        textures.forEach { texture ->
            texture.unbind()
        }

        shader.detach()
    }

    private fun loadVertexProperties(index: Int) {
        val sprite = sprites[index] ?: return

        // Find offset within array (4 vertices per sprite)
        var offset = index * 4 * VERTEX_SIZE
        val color = sprite.getColor()

        val texId = sprite.getTexture().let { sprTex ->
            if (textures.contains(sprTex)) textures.indexOf(sprTex) + 1
            else 0
        }

        val texCoords = sprite.getTextCoords()

        val transform = sprite.gameObject.transform
        val isRotated = transform.rotation != 0.0
        val transformMatrix = Matrix4f().identity()
        if (isRotated) {
            transformMatrix.translate(
                transform.position.x,
                transform.position.y,
                0f
            )
            transformMatrix.rotate(Math.toRadians(transform.rotation).toFloat(), 0f, 0f, 1f)
            transformMatrix.scale(transform.scale.x, transform.scale.y, 1f)
        }

        // Add vertices with the appropriate properties
        var xAdd = 0.5f
        var yAdd = 0.5f
        for (i in 0 until 4) {
            when (i) {
                1 -> yAdd = -0.5f
                2 -> xAdd = -0.5f
                3 -> yAdd = 0.5f
            }

            // Load position
            val go = sprite.gameObject
            var currentPos = Vector4f(
                go.transform.position.x + (xAdd * go.transform.scale.x),
                go.transform.position.y + (yAdd * go.transform.scale.y),
                0f, 1f
            )

            if (isRotated) {
                currentPos = Vector4f(xAdd, yAdd, 0f, 1f).mul(transformMatrix)
            }

            vertices[offset] = currentPos.x
            vertices[offset + 1] = currentPos.y

            // Load color
            vertices[offset + 2] = color.x
            vertices[offset + 3] = color.y
            vertices[offset + 4] = color.z
            vertices[offset + 5] = color.w

            // Load tex coords
            vertices[offset + 6] = texCoords[i].x
            vertices[offset + 7] = texCoords[i].y

            // Load tex id
            vertices[offset + 8] = texId.toFloat()

            // Load entity id
            vertices[offset + 9] = go.getUid().toFloat() + 1

            offset += VERTEX_SIZE
        }
    }

    private fun generateIndices(): IntArray {
        val elements = IntArray(6 * maxBatchSize)
        for (index in 0 until maxBatchSize) {
            loadElementIndices(elements, index)
        }

        return elements
    }

    private fun loadElementIndices(elements: IntArray, index: Int) {
        val offsetArrayIndex = 6 * index
        val offset = 4 * index

        // 3, 2, 0, 0, 2 ,1       7, 6, 4, 4, 6, 5
        // Triangle 1
        elements[offsetArrayIndex] = offset + 3
        elements[offsetArrayIndex + 1] = offset + 2
        elements[offsetArrayIndex + 2] = offset + 0

        // Triangle 2
        elements[offsetArrayIndex + 3] = offset + 0
        elements[offsetArrayIndex + 4] = offset + 2
        elements[offsetArrayIndex + 5] = offset + 1
    }

    fun hasRoom() = hasRoom

    fun hasTextureRoom() = textures.size < 8

    fun hasTexture(texture: Texture?) = texture != null && textures.contains(texture)

    fun zIndex() = zIndex

    fun destroyIfExists(go: GameObject): Boolean {
        val sprite = go.getComponent(SpriteRenderer::class.java)
        for (i in 0 until spriteCount) {
            if (sprites[i] === sprite) {
                for (j in i until spriteCount - 1) {
                    sprites[j] = sprites[j + 1]
                    sprites[j]?.setDirty()
                }
                spriteCount--
                return true
            }
        }

        return false
    }

    override fun compareTo(other: RenderBatch): Int {
        return zIndex.compareTo(other.zIndex)
    }

}

const val POS_SIZE = 2
const val COLOR_SIZE = 4
const val TEX_COORDS_SIZE = 2
const val TEX_ID_SIZE = 1
const val ENTITY_ID_SIZE = 1
const val POS_OFFSET = 0
const val COLOR_OFFSET: Int = POS_OFFSET + POS_SIZE * Float.SIZE_BYTES
const val TEX_COORDS_OFFSET = COLOR_OFFSET + COLOR_SIZE * Float.SIZE_BYTES
const val TEX_ID_OFFSET = TEX_COORDS_OFFSET + TEX_COORDS_SIZE * Float.SIZE_BYTES
const val ENTITY_ID_OFFSET = TEX_ID_OFFSET + TEX_ID_SIZE * Float.SIZE_BYTES
const val VERTEX_SIZE = 10
const val VERTEX_SIZE_BYTES: Int = VERTEX_SIZE * Float.SIZE_BYTES