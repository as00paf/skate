package com.pafoid.skate.engine.physics3d.destruction

import com.pafoid.skate.engine.ecs.GameObject
import org.joml.Vector3f

/**
 * Damage and breaks for props with a `DestructibleComponent` (PHY-29 §5.1), for scripts, weapons and gameplay. The
 * scene's `DestructionSystem` implements it. Explosions (`explode`) come with slice 29d.
 */
interface DestructionService {
    /**
     * Deals [amount] damage of [type] to [target] at the world [point]. Returns false when [target] is not a live,
     * unbroken destructible or [amount] is not positive. A prop brought to 0 integrity breaks within a few fixed steps
     * (at most 4 breaks run per fixed step, the rest wait).
     */
    fun damage(target: GameObject, amount: Float, point: Vector3f, type: String = DAMAGE_TYPE_SCRIPT): Boolean

    /** Breaks [target] at the world [point] whatever its integrity; false when it is not a live, unbroken destructible. */
    fun breakNow(target: GameObject, point: Vector3f): Boolean

    companion object {
        const val DAMAGE_TYPE_IMPACT = "impact"
        const val DAMAGE_TYPE_SCRIPT = "script"
    }
}
