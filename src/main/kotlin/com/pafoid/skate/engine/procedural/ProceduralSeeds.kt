package com.pafoid.skate.engine.procedural

import com.pafoid.skate.engine.ecs.random.RandomService

/**
 * Seeds for procedural content from the ECS-14 project seed (REN-28 §5.1). Procedural generation never draws from a
 * sequential `RandomStream`: every seed is a hash of the project seed and a name, so a result depends neither on
 * generation order, nor on thread count, nor on what other systems draw. A terrain chunk regenerates identically on
 * its own, which is what streaming needs.
 *
 * Names follow the stream naming of REN-28: `"terrain/<entity>"` for a terrain, `"<base>/<layer>"` for one of its
 * noise layers, `"<base>/scatter/<rule>/<cx>,<cz>"` for per-chunk placement.
 */
object ProceduralSeeds {

    /**
     * 64-bit seed of [name] under [projectSeed]; [offset] is a designer's per-object reroll (0 keeps the plain name, so
     * adding the field later changes nothing for existing content).
     */
    fun seed(projectSeed: Long, name: String, offset: Int = 0): Long =
        RandomService.streamSeed(projectSeed, if (offset == 0) name else "$name#$offset")

    /** The 32-bit seed FastNoiseLite takes, folded from [seed] (both halves count). */
    fun noiseSeed(projectSeed: Long, name: String, offset: Int = 0): Int = fold(seed(projectSeed, name, offset))

    /** Seed of chunk ([cx], [cz]) of [name]: independent per chunk, so chunks can be generated in any order. */
    fun chunkSeed(projectSeed: Long, name: String, cx: Int, cz: Int, offset: Int = 0): Long =
        seed(projectSeed, "$name/$cx,$cz", offset)

    /** Folds a 64-bit seed to 32 bits: `(s xor (s ushr 32)).toInt()`. */
    fun fold(seed: Long): Int = (seed xor (seed ushr 32)).toInt()

    /** A [NoiseField] for [settings], seeded from [projectSeed] and the stream [name]. */
    fun field(settings: NoiseSettings, projectSeed: Long, name: String, offset: Int = 0): NoiseField =
        NoiseField(settings, noiseSeed(projectSeed, name, offset))
}
