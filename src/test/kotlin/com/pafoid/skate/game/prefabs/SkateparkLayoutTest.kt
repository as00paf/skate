package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** GPL-23: the MainScene skatepark fits its floor, obstacles do not intersect, and the street line connects. */
class SkateparkLayoutTest {

    private val engine = mockk<Engine>(relaxed = true).also { every { it.logger } returns LoggerService() }
    private val park = PrefabsGenerator(engine, loadAssets = false).spawnSkatepark()
    /** The repo file of an `engine:` model reference. */
    private fun engineFile(reference: String) = File("assets/" + EngineDefaults.logicalPath(reference)!!)

    /** World XZ footprint and top height of an obstacle, from its committed model and its transform (yaw 0 or 180). */
    private data class Footprint(val minX: Float, val maxX: Float, val minZ: Float, val maxZ: Float, val top: Float) {
        fun overlap(o: Footprint): Float =
            maxOf(0f, minOf(maxX, o.maxX) - maxOf(minX, o.minX)) * maxOf(0f, minOf(maxZ, o.maxZ) - maxOf(minZ, o.minZ))
    }

    private fun footprint(go: GameObject): Footprint {
        val obj = engineFile(go.getComponent<RenderComponent>()!!.model!!.path)
        val vs = obj.readLines().filter { it.startsWith("v ") }.map { l -> l.trim().split(Regex("\\s+")).let { Vector3f(it[1].toFloat(), it[2].toFloat(), it[3].toFloat()) } }
        val t = go.getComponent<Transform>()!!
        val turned = t.rotation.y == 180f
        assertTrue(t.rotation.y == 0f || turned, "${go.name}: only 0 or 180 degree turns are used")
        val world = vs.map { v -> Vector3f(if (turned) -v.x else v.x, v.y, if (turned) -v.z else v.z).mul(t.scale).add(t.translation) }
        return Footprint(world.minOf { it.x }, world.maxOf { it.x }, world.minOf { it.z }, world.maxOf { it.z }, world.maxOf { it.y })
    }

    private val obstacles = park.filter { it.name != "Floor" }.associate { it.name to footprint(it) }

    @Test
    fun everyObstacle_FitsOnThe40MetreFloor() {
        val half = PrefabsGenerator.PARK_SIZE / 2f
        val floor = park.single { it.name == "Floor" }.getComponent<Transform>()!!
        assertEquals(half, floor.scale.x, 1e-4f)
        assertEquals(half, floor.scale.z, 1e-4f)
        obstacles.forEach { (name, f) ->
            assertTrue(f.minX >= -half && f.maxX <= half && f.minZ >= -half && f.maxZ <= half, "$name $f is off the floor")
        }
        assertTrue(obstacles.size >= 18, "four zones and the plaza: ${obstacles.keys}")
    }

    @Test
    fun obstacles_DoNotIntersect_ExceptTheStairGroups() {
        // Hubba and handrail sit along the 5-stair's edges, the deck and the bank butt against the stairs' landings.
        val groups = listOf(
            setOf("Street_EuroGap", "Street_Stairs5", "Street_Hubba", "Street_Handrail"),
            setOf("Flat_Bank", "Flat_Stairs3"),
        )
        val names = obstacles.keys.toList()
        for (i in names.indices) for (j in i + 1 until names.size) {
            val (a, b) = names[i] to names[j]
            if (groups.any { a in it && b in it }) continue
            assertTrue(obstacles.getValue(a).overlap(obstacles.getValue(b)) < 0.01f, "$a and $b intersect")
        }
    }

    @Test
    fun theEuroGapDeck_LeadsFlushOntoThe5StairLanding() {
        val deck = obstacles.getValue("Street_EuroGap")
        val stairs = obstacles.getValue("Street_Stairs5")
        assertEquals(stairs.minZ, deck.maxZ, 0.01f, "the deck ends where the landing starts")
        assertEquals(stairs.top, deck.top, 0.01f, "same height, so the rider rolls onto the landing")
        assertEquals(stairs.minX, deck.minX, 0.01f)
        assertEquals(stairs.maxX, deck.maxX, 0.01f)
    }

    @Test
    fun rampsFaceTheWayTheyAreRidden() {
        // Ridden going north (+Z): the deck and the turned bank rise toward +Z, the turned kicker launches north.
        fun highEndIsNorth(name: String): Boolean {
            val go = park.single { it.name == name }
            val vs = engineFile(go.getComponent<RenderComponent>()!!.model!!.path).readLines().filter { it.startsWith("v ") }
                .map { l -> l.trim().split(Regex("\\s+")).let { Vector3f(it[1].toFloat(), it[2].toFloat(), it[3].toFloat()) } }
            val sign = if (go.getComponent<Transform>()!!.rotation.y == 180f) -1f else 1f
            val top = vs.maxOf { it.y }
            val highZ = vs.filter { it.y > top - 1e-3f }.map { it.z * sign }.average()
            return highZ > 0
        }
        listOf("Street_EuroGap", "Flat_Bank", "Flat_Kicker", "Transition_Bank").forEach { assertTrue(highEndIsNorth(it), "$it rises toward +Z") }
    }
}
