package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.terrain.Biome
import com.pafoid.skate.engine.terrain.BiomeClimate
import com.pafoid.skate.engine.terrain.BiomePresets
import com.pafoid.skate.engine.terrain.BiomeSource
import com.pafoid.skate.engine.terrain.TerrainKey
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainRecipe
import com.pafoid.skate.engine.terrain.TerrainRuntime
import com.pafoid.skate.engine.terrain.TerrainStamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A heightmap terrain (REN-28 §3.1): square, axis-aligned and centred on the Transform position (rotation ignored).
 * Procedural terrains are generated from [recipe] and [stamps], seeded from the project seed and [seedName]
 * (ECS-14), so the same seed gives the same ground everywhere. The collider is a separate `HeightfieldCollider3D`
 * (PHY-15) reading [runtime]'s chunks.
 */
@Serializable
data class TerrainComponent(
    /** Square side in metres, up to [MAX_SIZE]; rounded up to whole chunks. */
    var sizeMetres: Float = 256f,
    /** Metres between height samples: 0.5, 1, 2 or 4. */
    var sampleSpacing: Float = 1f,
    /** A recipe height of 1.0 maps to this many metres. */
    var heightScaleMetres: Float = 64f,
    /** Quads per chunk side (power of two); also the streaming unit. */
    var chunkQuads: Int = 64,
    var source: TerrainSource = TerrainSource.PROCEDURAL,
    var recipe: TerrainRecipe = TerrainPreset.ISLAND.recipe(),
    /** Flattened areas applied after the recipe (REN-28 §5.4). */
    val stamps: MutableList<TerrainStamp> = mutableListOf(),
    /** Up to [MAX_LAYERS] material layers (REN-28 §4.2, task 5). */
    val layers: MutableList<TerrainLayer> = TerrainLayer.defaults(),
    /** Biomes (REN-28 §6): material layer rules and scatter rules; empty shades by height and slope only. */
    val biomes: MutableList<Biome> = BiomePresets.defaults(),
    var biomeSource: BiomeSource = BiomeSource.PROCEDURAL,
    var climate: BiomeClimate = BiomeClimate(),
    var streaming: TerrainStreaming = TerrainStreaming.AUTO,
    /** ECS-14 stream name; empty means `"terrain/<entity name>"` at first use. Kept on rename so the world stays. */
    var seedName: String = "",
    /** Rerolls this terrain alone (0 keeps the plain seed). */
    var seedOffset: Int = 0,
    var castShadows: Boolean = true,
    var collider: Boolean = true,
    /**
     * GUID of the `.skterrain` asset holding this terrain's document and chunk data (AST-36); empty keeps everything
     * in the scene (procedural only, edits are not saved). See `TerrainAssets`.
     */
    var asset: String = "",
) : Component() {

    @Transient
    val runtime: TerrainRuntime = TerrainRuntime()

    /** The stream name, assigned from the entity name the first time it is needed. */
    fun resolvedSeedName(): String {
        if (seedName.isEmpty()) seedName = "terrain/" + (gameObject?.name ?: "Terrain")
        return seedName
    }

    /** What the heights depend on, under [projectSeed]. */
    fun key(projectSeed: Long): TerrainKey = TerrainKey(
        size = sizeMetres.coerceIn(MIN_SIZE, MAX_SIZE),
        spacing = SPACINGS.minBy { kotlin.math.abs(it - sampleSpacing) },
        chunkQuads = Integer.highestOneBit(chunkQuads.coerceIn(MIN_CHUNK_QUADS, MAX_CHUNK_QUADS)),
        heightScale = heightScaleMetres,
        recipe = if (source == TerrainSource.PROCEDURAL) recipe else TerrainPreset.FLAT.recipe(),
        stamps = stamps.toList(),
        projectSeed = projectSeed,
        seedName = resolvedSeedName(),
        seedOffset = seedOffset,
        biomes = biomes.toList(),
        climate = climate,
        biomeSource = biomeSource,
    )

    fun applyPreset(preset: TerrainPreset) {
        recipe = preset.recipe()
    }

    /** Rerolls this terrain alone (same project seed, new offset): the showcase "Reroll seed" button (REN-28 §11). */
    fun reroll() { seedOffset++ }

    override fun reset() {
        sizeMetres = 256f; sampleSpacing = 1f; heightScaleMetres = 64f; chunkQuads = 64
        source = TerrainSource.PROCEDURAL
        recipe = TerrainPreset.ISLAND.recipe()
        stamps.clear()
        layers.clear(); layers.addAll(TerrainLayer.defaults())
        biomes.clear(); biomes.addAll(BiomePresets.defaults())
        biomeSource = BiomeSource.PROCEDURAL
        climate = BiomeClimate()
        streaming = TerrainStreaming.AUTO
        seedOffset = 0
        castShadows = true; collider = true
        asset = ""
        runtime.clear()
    }

    companion object {
        const val MIN_SIZE = 8f
        const val MAX_SIZE = 16384f
        const val MAX_LAYERS = 8
        const val MIN_CHUNK_QUADS = 8
        const val MAX_CHUNK_QUADS = 256
        val SPACINGS = listOf(0.5f, 1f, 2f, 4f)

        /**
         * The Feature Showcase terrain (REN-28 §11): a 96 m procedural island with the 3 default biomes (beach,
         * grass with rocks, rocky peak) and one flattened pad at local ([padX], [padZ]) for a ramp. TLG places it and
         * wires a "Reroll seed" button to [reroll].
         */
        fun showcaseIsland(padX: Float = -18f, padZ: Float = 10f, padHeight: Float = 3f): TerrainComponent = TerrainComponent(
            sizeMetres = 96f,
            sampleSpacing = 0.5f,
            heightScaleMetres = 20f,
            chunkQuads = 64,
            recipe = TerrainPreset.SHOWCASE_ISLAND.recipe(),
            stamps = mutableListOf(TerrainStamp(x = padX, y = padHeight, z = padZ, width = 8f, depth = 12f, falloff = 4f)),
            streaming = TerrainStreaming.OFF,
            seedName = "terrain/showcase-island",
        )
    }
}

@Serializable
enum class TerrainSource { PROCEDURAL, DATA }

@Serializable
enum class TerrainStreaming { OFF, ON, AUTO }

/** A material layer (REN-28 §4.2): a `.skmat` reference, its tiling in metres, triplanar on steep slopes. */
@Serializable
data class TerrainLayer(
    val material: String = "",
    val tilingMetres: Float = 4f,
    val triplanar: Boolean = false,
) {
    companion object {
        /** Sand, grass, rock, snow (materials picked in the editor or by the biome presets, task 6). */
        fun defaults(): MutableList<TerrainLayer> = mutableListOf(
            TerrainLayer(tilingMetres = 4f),
            TerrainLayer(tilingMetres = 6f),
            TerrainLayer(tilingMetres = 8f, triplanar = true),
            TerrainLayer(tilingMetres = 8f),
        )
    }
}
