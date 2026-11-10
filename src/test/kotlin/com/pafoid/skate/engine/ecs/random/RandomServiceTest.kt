package com.pafoid.skate.engine.ecs.random

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RandomServiceTest {
    private fun draws(stream: RandomStream, n: Int = 20) = List(n) { stream.nextLong() }

    @Test
    fun RandomStream_MatchesSplitMix64Reference() {
        // Reference outputs of SplitMix64 seeded with 0: pins the algorithm, so sequences survive refactors and JVMs.
        val stream = RandomStream("ref", 0L)
        assertEquals(0xE220A8397B1DCDAFuL.toLong(), stream.nextLong())
        assertEquals(0x6E789E6AA1B965F4uL.toLong(), stream.nextLong())
        assertEquals(0x06C45D188009454FuL.toLong(), stream.nextLong())
    }

    @Test
    fun SameSeed_SameSequencePerStream() {
        val a = RandomService(1234)
        val b = RandomService(1234)
        assertEquals(draws(a.stream("enemies")), draws(b.stream("enemies")))
        assertEquals(List(50) { a.stream("loot").nextInt(1, 7) }, List(50) { b.stream("loot").nextInt(1, 7) })
        assertEquals(List(10) { a.stream("f").range(-1f, 1f) }, List(10) { b.stream("f").range(-1f, 1f) })
    }

    @Test
    fun Streams_AreIndependent_OfCreationOrderAndOtherDraws() {
        val alone = RandomService(99)
        val expected = draws(alone.stream("weather"))

        // Another stream created first and drained heavily (e.g. a newly added system) changes nothing.
        val busy = RandomService(99)
        repeat(1000) { busy.stream("particles").nextLong() }
        busy.stream("spawns").nextInt()
        assertEquals(expected, draws(busy.stream("weather")))

        // Interleaving draws between streams changes nothing either.
        val interleaved = RandomService(99)
        val weather = interleaved.stream("weather")
        val other = interleaved.stream("other")
        val got = List(20) { other.nextLong(); weather.nextLong() }
        assertEquals(expected, got)
    }

    @Test
    fun DifferentNamesOrSeeds_GiveDifferentSequences() {
        val service = RandomService(7)
        assertNotEquals(draws(service.stream("a")), draws(service.stream("b")))
        assertNotEquals(draws(RandomService(7).stream("a")), draws(RandomService(8).stream("a")))
        assertNotEquals(RandomService.streamSeed(1, "ab"), RandomService.streamSeed(1, "ba"))
    }

    @Test
    fun Reseed_RewindsStreamsAlreadyHandedOut() {
        val service = RandomService(1)
        val held = service.stream("held")
        val first = draws(held)
        service.restart()
        assertEquals(first, draws(held))
        assertEquals(0L + 20, held.draws)

        service.reseed(2)
        assertEquals(2L, service.seed)
        assertEquals(draws(RandomService(2).stream("held")), draws(held))
        assertSame(held, service.stream("held"))
        assertEquals(listOf("held"), service.streamNames)
    }

    @Test
    fun Stream_RejectsBlankNames() {
        assertThrows<IllegalArgumentException> { RandomService().stream(" ") }
    }

    @Test
    fun Helpers_StayInRange() {
        val s = RandomService(5).stream("h")
        repeat(1000) {
            assertTrue(s.nextInt(3, 9) in 3 until 9)
            assertTrue(s.range(2f, 4f).let { it >= 2f && it < 4f })
        }
        assertTrue((1..1000).none { s.chance(0f) })
        assertTrue((1..1000).all { s.chance(1f) })
        assertTrue(s.pick(listOf("x", "y")) in listOf("x", "y"))
        assertEquals(0, s.nextBits(0))
    }

    @Test
    fun Parse_AcceptsDecimalHexAndUnderscores() {
        assertEquals(42L, RandomService.parse(" 42 "))
        assertEquals(-42L, RandomService.parse("-42"))
        assertEquals(255L, RandomService.parse("0xFF"))
        assertEquals(1_000_000L, RandomService.parse("1_000_000"))
        assertEquals(-1L, RandomService.parse("0xFFFFFFFFFFFFFFFF"))
        assertNull(RandomService.parse("abc"))
        assertNull(RandomService.parse(""))
        assertNull(RandomService.parse(null))
    }

    @Test
    fun Resolve_PropertyThenSettingsThenProject() {
        assertEquals(3L, RandomService.resolve(project = 1, settings = 2, property = "3"))
        assertEquals(2L, RandomService.resolve(project = 1, settings = 2, property = "not a number"))
        assertEquals(1L, RandomService.resolve(project = 1, settings = null, property = null))
    }

    private class Dice : System() {
        fun roll() = random.nextInt(1, 7)
    }

    private class Noisy : System() {
        fun drain() = repeat(500) { random.nextLong() }
    }

    @Test
    fun Systems_HaveIndependentStreams_AndRewindWhenPlayStarts() {
        fun rolls(withNoisySystem: Boolean): List<Int> {
            val manager = SystemManager()
            manager.random.reseed(77)
            val noisy = Noisy()
            if (withNoisySystem) manager.addSystem(noisy)
            val dice = Dice().also { manager.addSystem(it) }
            if (withNoisySystem) noisy.drain()
            return List(10) { dice.roll() }
        }
        // Adding a system that draws a lot does not shift another system's values.
        assertEquals(rolls(false), rolls(true))

        val manager = SystemManager()
        val dice = Dice().also { manager.addSystem(it) }
        val scene = Scene()
        manager.loadScene(scene)
        scene.isRunning = true
        manager.update(0f)
        val firstRun = List(5) { dice.roll() }
        scene.isRunning = false
        manager.update(0f)
        scene.isRunning = true
        manager.update(0f) // a new Play run starts the streams again
        assertEquals(firstRun, List(5) { dice.roll() })
        assertEquals("system.Dice", dice.randomStreamName)
    }
}
