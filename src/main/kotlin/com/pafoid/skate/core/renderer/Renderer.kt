package marki.renderer

import com.pafoid.skate.components.SpriteRenderer
import marki.GameObject

const val MAX_BATCH_SIZE = 1000

class Renderer {
    private val batches = mutableListOf<RenderBatch>()
    lateinit var currentShader: Shader

    fun add(go: GameObject) {
        val spr = go.getComponent(SpriteRenderer::class.java)
        spr?.let { add(it) }
    }

    private fun add(sprite: SpriteRenderer) {
        var added = false
        batches.forEach { batch ->
            if(batch.hasRoom() && batch.zIndex() == sprite.gameObject.transform.zIndex) {
                val texture = sprite.getTexture()
                if(texture == null || (batch.hasTextureRoom() || batch.hasTexture(texture))){
                    batch.addSprite(sprite)
                    added = true
                }
            }
        }

        if(!added) {
            val newBatch = RenderBatch(MAX_BATCH_SIZE, sprite.gameObject.transform.zIndex, this)
            newBatch.start()
            batches.add(newBatch)
            newBatch.addSprite(sprite)
            batches.sort()
        }
    }

    fun render() {
        currentShader.use()
        for (i in batches.indices) {
            val batch = batches[i]
            batch.render()
        }
    }

    fun destroyGameObject(deadObject: GameObject) {
        if(deadObject.getComponent(SpriteRenderer::class.java) == null) return

        batches.forEach { batch ->
            if(batch.destroyIfExists(deadObject)) return
        }
    }

    fun destroyGameObjects(deadObjects: List<GameObject>) {
        deadObjects.forEach { destroyGameObject(it) }
    }

    fun getBoundShader():Shader {
        return currentShader
    }

    fun bindShader(shader: Shader) {
        currentShader = shader
    }
}