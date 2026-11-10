package com.pafoid.skate.engine.ecs.random

import kotlin.random.Random

/**
 * One deterministic random sequence (ECS-14), SplitMix64. The same seed gives the same values on every run, JVM and
 * platform. It is a [Random], so every Kotlin helper (`nextInt(from, until)`, `shuffled(random)`, ...) works with it.
 * Not thread-safe: use one stream per thread or system.
 */
class RandomStream internal constructor(val name: String, seed: Long) : Random() {
    private var state = seed

    /** Values drawn since the last reset; handy when checking that a system draws the same amount each run. */
    var draws: Long = 0
        private set

    internal fun reset(seed: Long) {
        state = seed
        draws = 0
    }

    override fun nextLong(): Long {
        draws++
        state += GOLDEN_GAMMA
        return mix64(state)
    }

    override fun nextBits(bitCount: Int): Int =
        if (bitCount <= 0) 0 else (nextLong() ushr (64 - bitCount)).toInt()

    override fun nextInt(): Int = (nextLong() ushr 32).toInt()

    /** Uniform in [[min], [max]). */
    fun range(min: Float, max: Float): Float = min + nextFloat() * (max - min)

    /** True with probability [p] (0 never, 1 always). */
    fun chance(p: Float): Boolean = nextFloat() < p

    /** A uniformly chosen element of [items]; throws when it is empty. */
    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    internal companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

        fun mix64(value: Long): Long {
            var z = value
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }
    }
}

/**
 * Seeded random numbers for the whole engine (ECS-14). Everything random that should replay the same way (gameplay,
 * spawns, procedural content) draws from a named [stream]. Each stream's sequence depends only on the [seed] and its
 * own name, so adding a stream (or a system that draws from one) never shifts the values of the others.
 *
 * The seed comes from the project (`Project.randomSeed`), can be overridden at launch ([resolve]: the
 * [SYSTEM_PROPERTY], then `game.settings.json`) and at runtime ([reseed], also from scripts). Every Play run starts
 * the streams again from the seed ([restart], called by `SystemManager` when the scene starts running).
 */
class RandomService(seed: Long = DEFAULT_SEED) {
    var seed: Long = seed
        private set

    private val streams = LinkedHashMap<String, RandomStream>()

    /** Names of the streams created so far, in creation order. */
    val streamNames: List<String> @Synchronized get() = streams.keys.toList()

    /** The stream called [name], created on first use. The same object is returned for the same name. */
    @Synchronized
    fun stream(name: String): RandomStream {
        require(name.isNotBlank()) { "Random stream name must not be blank" }
        return streams.getOrPut(name) { RandomStream(name, streamSeed(seed, name)) }
    }

    /** Switches to [newSeed] and rewinds every stream; streams already handed out keep working with the new seed. */
    @Synchronized
    fun reseed(newSeed: Long) {
        seed = newSeed
        streams.values.forEach { it.reset(streamSeed(newSeed, it.name)) }
    }

    /** Rewinds every stream to the start of the current seed's sequence. */
    fun restart() = reseed(seed)

    companion object {
        /** Seed of projects that never set one. */
        const val DEFAULT_SEED = 20_240_601L

        /** `-Dskate.seed=1234` overrides the seed everywhere (testing, bug reports). */
        const val SYSTEM_PROPERTY = "skate.seed"

        /** Seed of stream [name] under [seed]: a hash of both, so streams do not depend on each other. */
        fun streamSeed(seed: Long, name: String): Long = RandomStream.mix64(RandomStream.mix64(seed) xor fnv1a64(name))

        /** A seed typed by a person: a decimal or `0x` hex number; null when blank or not a number. */
        fun parse(value: String?): Long? {
            val text = value?.trim()?.replace("_", "") ?: return null
            if (text.isEmpty()) return null
            val negative = text.startsWith("-")
            val digits = text.removePrefix("-")
            val parsed = if (digits.startsWith("0x", ignoreCase = true)) digits.substring(2).toULongOrNull(16)?.toLong()
            else digits.toLongOrNull()
            return parsed?.let { if (negative) -it else it }
        }

        /** The seed a run uses: the [SYSTEM_PROPERTY] ([property]), then the player's settings, then the project's. */
        fun resolve(project: Long, settings: Long? = null, property: String? = System.getProperty(SYSTEM_PROPERTY)): Long =
            parse(property) ?: settings ?: project

        /** A fresh seed for "Randomize" buttons; not deterministic on purpose. */
        fun newSeed(): Long = Random.Default.nextLong(1, Int.MAX_VALUE.toLong())

        private fun fnv1a64(text: String): Long {
            var hash = -0x340d631b7bdddcdbL // 0xCBF29CE484222325
            for (b in text.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (b.toLong() and 0xff)
                hash *= 0x100000001b3L
            }
            return hash
        }
    }
}
