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
    /**
     * ECS-14 stream name. Empty means `"terrain/<entity name>"`, read live (renaming such a terrain changes its ground);
     * the editor stores the name when it creates a terrain, so a renamed terrain keeps its world. A duplicate keeps it
     * too: a copy has the same ground until rerolled ([reroll]) or renamed here (REN-42).
     */
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

    /**
     * The stream name in use: [seedName], or `"terrain/<entity name>"` while it is empty. Pure: it never writes
     * [seedName], so loading then saving a scene leaves the file unchanged (EDT-60), and a call before the component is
     * attached does not pin it to "terrain/Terrain" (REN-42).
     */
    fun resolvedSeedName(): String = seedName.ifEmpty { "terrain/" + (gameObject?.name ?: "Terrain") }

    /** Size in metres actually built: finite, within [MIN_SIZE]..[MAX_SIZE] (NaN or 0 fall back to the default). */
    fun effectiveSize(): Float = finite(sizeMetres, DEFAULT_SIZE).coerceIn(MIN_SIZE, MAX_SIZE)

    /**
     * Sample spacing actually built: the nearest allowed spacing, coarsened until the terrain holds at most
     * [MAX_RESIDENT_SAMPLES] samples, since every chunk is resident until streaming (REN-28 phase D). 16384 m at
     * 0.5 m would be about 4.3 GB of heights; it builds at 4 m instead (REN-42).
     */
    fun effectiveSpacing(): Float {
        val requested = finite(sampleSpacing, 1f)
        val size = effectiveSize()
        val start = SPACINGS.indexOf(SPACINGS.minBy { kotlin.math.abs(it - requested) })
        for (i in start until SPACINGS.size) if (samplesFor(size, SPACINGS[i]) <= MAX_RESIDENT_SAMPLES) return SPACINGS[i]
        return SPACINGS.last()
    }

    /** True when [effectiveSpacing] is coarser than asked (the inspector can say why). */
    fun spacingCapped(): Boolean = effectiveSpacing() > SPACINGS.minBy { kotlin.math.abs(it - finite(sampleSpacing, 1f)) }

    /** The layers used: the first [MAX_LAYERS]; extra entries stay in the list (and the file) but are ignored. */
    fun effectiveLayers(): List<TerrainLayer> = layers.take(MAX_LAYERS)

    /** What the heights depend on, under [projectSeed]; every value sanitized, so building never throws on bad input. */
    fun key(projectSeed: Long): TerrainKey = TerrainKey(
        size = effectiveSize(),
        spacing = effectiveSpacing(),
        chunkQuads = Integer.highestOneBit(chunkQuads.coerceIn(MIN_CHUNK_QUADS, MAX_CHUNK_QUADS)),
        heightScale = finite(heightScaleMetres, DEFAULT_HEIGHT_SCALE),
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
        const val DEFAULT_SIZE = 256f
        const val DEFAULT_HEIGHT_SCALE = 64f

        /** Most samples one terrain keeps resident (4097², 64 MB of heights) until streaming (REN-28 phase D). */
        const val MAX_RESIDENT_SAMPLES = 4097L * 4097L

        fun samplesFor(size: Float, spacing: Float): Long {
            val perSide = kotlin.math.ceil(size / spacing).toLong() + 1
            return perSide * perSide
        }

        private fun finite(v: Float, fallback: Float): Float = if (v.isFinite() && v > 0f) v else fallback

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
