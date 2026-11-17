package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.ecs.components.PlayerController
import java.util.IdentityHashMap

/**
 * Pending bail-and-respawn per player (GPL-32). [enter] reacts to `WaterEntered` according to the player's
 * [PlayerController.waterResponse]; [tick] counts the bail down and says when to respawn. Pure: the motion system
 * moves the body.
 */
class WaterRespawns {
    private val remaining = IdentityHashMap<PlayerController, Float>()

    /** Bails [controller] if its policy says so and it is not bailing already; true when a respawn is now pending. */
    fun enter(controller: PlayerController): Boolean {
        if (controller.waterResponse != WaterResponse.BAIL || controller in remaining) return false
        remaining[controller] = controller.behavior.bail().coerceAtLeast(0f)
        return true
    }

    fun isPending(controller: PlayerController) = controller in remaining

    /** Advances [controller]'s countdown by [dt]; true (once) when it must respawn now. */
    fun tick(controller: PlayerController, dt: Float): Boolean {
        val left = (remaining[controller] ?: return false) - dt
        if (left > 0f) {
            remaining[controller] = left
            return false
        }
        remaining.remove(controller)
        return true
    }

    /** Forgets every pending respawn (scene change, Play start). */
    fun clear() = remaining.clear()
}
