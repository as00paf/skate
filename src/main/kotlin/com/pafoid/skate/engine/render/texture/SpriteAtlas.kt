package com.pafoid.skate.engine.render.texture

import com.pafoid.skate.engine.assets.data.Sprite
import com.pafoid.skate.engine.assets.data.Texture
import org.joml.Vector2f

/** Pixel rectangle inside an atlas texture; origin top-left, y down. */
data class AtlasRegion(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Named regions of one texture. Sprites share the atlas texture so they batch together.
 * UVs are inset by [halfTexelInset] texels to avoid bleeding from neighbours when filtering.
 */
class SpriteAtlas(val texture: Texture, private val halfTexelInset: Float = 0.5f) {
    private val regions = LinkedHashMap<String, AtlasRegion>()

    val names: Set<String> get() = regions.keys

    fun add(name: String, region: AtlasRegion): SpriteAtlas {
        require(region.w > 0 && region.h > 0) { "Region '$name' must be non-empty" }
        require(region.x >= 0 && region.y >= 0 && region.x + region.w <= texture.width && region.y + region.h <= texture.height) {
            "Region '$name' is outside the ${texture.width}x${texture.height} atlas"
        }
        regions[name] = region
        return this
    }

    fun region(name: String): AtlasRegion? = regions[name]

    /** UV rect (u0, v0, u1, v1) in texture space with v measured from the top. */
    fun uv(name: String): FloatArray {
        val r = regions[name] ?: throw NoSuchElementException("Unknown atlas region '$name'")
        val tw = texture.width.toFloat()
        val th = texture.height.toFloat()
        val inset = halfTexelInset
        return floatArrayOf((r.x + inset) / tw, (r.y + inset) / th, (r.x + r.w - inset) / tw, (r.y + r.h - inset) / th)
    }

    /** Sprite with corner order matching [Sprite]/RenderBatch2D (image is stored bottom-up, so v is flipped). */
    fun sprite(name: String): Sprite {
        val (u0, v0, u1, v1) = uv(name).let { listOf(it[0], it[1], it[2], it[3]) }
        val r = regions.getValue(name)
        val top = 1f - v0
        val bottom = 1f - v1
        val sprite = Sprite(texture, arrayOf(Vector2f(u1, top), Vector2f(u1, bottom), Vector2f(u0, bottom), Vector2f(u0, top)))
        sprite.width = r.w.toFloat()
        sprite.height = r.h.toFloat()
        return sprite
    }
}
