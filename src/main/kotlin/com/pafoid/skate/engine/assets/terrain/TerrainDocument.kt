package com.pafoid.skate.engine.assets.terrain

import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainLayer
import com.pafoid.skate.engine.ecs.components.TerrainSource
import com.pafoid.skate.engine.ecs.components.TerrainStreaming
import com.pafoid.skate.engine.terrain.TerrainRecipe
import com.pafoid.skate.engine.terrain.TerrainStamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `terrain.json` of a `.skterrain` asset (REN-28 §10): everything a terrain's heights and look depend on, i.e. the
 * recipe, stamps and layers plus the grid settings. Chunk heights live in the region files ([TerrainRegionFile]).
 * Versioned by [schema]; no migrations while the engine is a work in progress (rule 8): a newer or older schema is
 * refused with a clear message.
 */
@Serializable
data class TerrainDocument(
    val schema: Int = CURRENT_SCHEMA,
    val sizeMetres: Float = 256f,
    val sampleSpacing: Float = 1f,
    val heightScaleMetres: Float = 64f,
    val chunkQuads: Int = 64,
    val source: TerrainSource = TerrainSource.PROCEDURAL,
    val recipe: TerrainRecipe = TerrainRecipe(),
    val stamps: List<TerrainStamp> = emptyList(),
    val layers: List<TerrainLayer> = TerrainLayer.defaults(),
    val streaming: TerrainStreaming = TerrainStreaming.AUTO,
    val seedName: String = "",
    val seedOffset: Int = 0,
) {
    /** Copies the document into [terrain] (hot reload, scene load). Runtime-only flags (shadows, collider) stay. */
    fun applyTo(terrain: TerrainComponent) {
        terrain.sizeMetres = sizeMetres
        terrain.sampleSpacing = sampleSpacing
        terrain.heightScaleMetres = heightScaleMetres
        terrain.chunkQuads = chunkQuads
        terrain.source = source
        terrain.recipe = recipe
        terrain.stamps.clear(); terrain.stamps.addAll(stamps)
        terrain.layers.clear(); terrain.layers.addAll(layers)
        terrain.streaming = streaming
        if (seedName.isNotEmpty()) terrain.seedName = seedName
        terrain.seedOffset = seedOffset
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_SCHEMA = 1

        private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

        fun of(terrain: TerrainComponent): TerrainDocument = TerrainDocument(
            sizeMetres = terrain.sizeMetres,
            sampleSpacing = terrain.sampleSpacing,
            heightScaleMetres = terrain.heightScaleMetres,
            chunkQuads = terrain.chunkQuads,
            source = terrain.source,
            recipe = terrain.recipe,
            stamps = terrain.stamps.toList(),
            layers = terrain.layers.toList(),
            streaming = terrain.streaming,
            seedName = terrain.resolvedSeedName(),
            seedOffset = terrain.seedOffset,
        )

        fun decode(text: String): TerrainDocument {
            val doc = json.decodeFromString(serializer(), text)
            require(doc.schema == CURRENT_SCHEMA) {
                "This terrain was saved with format ${doc.schema} (current $CURRENT_SCHEMA) and can't be opened; recreate it."
            }
            return doc
        }
    }
}
