package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.ecs.components.PlayerController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-32: bail-and-respawn bookkeeping for players entering water. */
class WaterRespawnsTest {

    private class Bailer(private val delay: Float) : CharacterBehavior {
        var bails = 0
        override val state = "IDLE"
        override fun update(input: CharacterInput, dt: Float) = CharacterMotion(state)
        override fun bail(): Float = delay.also { bails++ }
    }

    private fun player(response: WaterResponse, delay: Float = 1f): Pair<PlayerController, Bailer> {
        val behavior = Bailer(delay)
        val id = "test.bailer.$delay"
        CharacterBehaviors.register(id) { behavior }
        return PlayerController(id, "", response) to behavior
    }

    @Test
    fun WaterRespawn_NonePolicyIgnoresWater() {
        val (controller, behavior) = player(WaterResponse.NONE)
        val respawns = WaterRespawns()
        assertFalse(respawns.enter(controller))
        assertFalse(respawns.tick(controller, 10f))
        assertEquals(0, behavior.bails)
    }

    @Test
    fun WaterRespawn_BailPolicyBailsThenRespawnsOnceAfterTheDelay() {
        val (controller, behavior) = player(WaterResponse.BAIL, delay = 1f)
        val respawns = WaterRespawns()
        assertTrue(respawns.enter(controller))
        assertEquals(1, behavior.bails)
        assertFalse(respawns.tick(controller, 0.6f))
        assertTrue(respawns.isPending(controller))
        assertTrue(respawns.tick(controller, 0.6f))
        assertFalse(respawns.isPending(controller))
        assertFalse(respawns.tick(controller, 0.6f), "respawns only once")
    }

    @Test
    fun WaterRespawn_EnteringAgainWhileBailingDoesNotRestartTheBail() {
        val (controller, behavior) = player(WaterResponse.BAIL, delay = 1f)
        val respawns = WaterRespawns()
        respawns.enter(controller)
        respawns.tick(controller, 0.9f)
        assertFalse(respawns.enter(controller))
        assertEquals(1, behavior.bails)
        assertTrue(respawns.tick(controller, 0.2f))
    }

    @Test
    fun WaterRespawn_NoBailRespawnsOnTheNextTickAndClearForgets() {
        val (controller, _) = player(WaterResponse.BAIL, delay = 0f)
        val respawns = WaterRespawns()
        respawns.enter(controller)
        assertTrue(respawns.tick(controller, 0.016f))
        respawns.enter(controller)
        respawns.clear()
        assertFalse(respawns.tick(controller, 0.016f))
    }
}
