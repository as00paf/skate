package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/** What happens to a [DestructibleComponent] prop when it breaks (`docs/design/PHY-29.md` §5.4). */
enum class DestructionMode {
    /** The object disappears; effects still play through `Fractured`. */
    REMOVE,

    /** The object is replaced by [DestructibleComponent.brokenPrefab] at the same place, with its velocities. */
    SWAP,

    /** The object shatters into physical pieces (slice 29c; until then it breaks like [REMOVE]). */
    SHATTER,
}

/** Where a [DestructionMode.SHATTER] prop's pieces come from (§4.1, §6.4). */
enum class FractureSource {
    /** [DestructibleComponent.fracture] when set, runtime fracture otherwise. */
    AUTO,
    PREFRACTURED,
    RUNTIME,
}

/**
 * A prop that takes damage and breaks (PHY-29, `docs/design/PHY-29.md` §4.1). Plain data: `DestructionSystem` applies
 * impact and API damage, queues the break and publishes `DestructibleDamaged` and `Fractured`.
 *
 * Impact damage is `max(0, J − impactThreshold) × impactDamagePerNs`, where `J` is the backend-neutral contact impulse
 * (`Collision.impulse`, reduced mass × closing speed). With the defaults a 70 kg skater at 6 m/s (≈ 420 N·s) breaks a
 * prop, a 20 kg crate thrown at 5 m/s (100 N·s) does not.
 */
@Serializable
data class DestructibleComponent(
    var mode: DestructionMode = DestructionMode.SHATTER,
    /** Own hit points, used while the object has no `HealthComponent` (§5.3). */
    var integrity: Float = 100f,
    /** Impacts below this impulse (N·s) do nothing. */
    var impactThreshold: Float = 150f,
    /** Damage per N·s above [impactThreshold]. */
    var impactDamagePerNs: Float = 0.5f,
    var fractureSource: FractureSource = FractureSource.AUTO,
    /** `.skfracture` asset for [DestructionMode.SHATTER] (slice 29b). */
    var fracture: String = "",
    /** Upper bound on runtime pieces, 4–16 (§6.4.3). */
    var maxRuntimePieces: Int = 12,
    /** Prefab path or GUID for [DestructionMode.SWAP]. */
    var brokenPrefab: String = "",
    /** Outward push of the pieces from the break point, in N·s per kg. */
    var breakImpulse: Float = 2f,
    /** Key for break sounds and dust (§8), a free string like damage types. */
    var surface: String = "wood",
    /** Small pieces don't trip characters unless this is on (§7.3). */
    var debrisCollidesWithCharacters: Boolean = false,
    /** The prop is already broken (saved and replicated, §9, §10). */
    var broken: Boolean = false,
    /** Runtime fracture seed (§6.4.5); saved with [broken]. */
    var fractureSeed: Long = 0L,
    /** Quantized impact point in the prop's frame that rebuilds the runtime base piece (§6.4.5). */
    @Contextual
    val fracturePoint: Vector3f = Vector3f(),
    /** 0 for an authored prop; a runtime piece is one deeper than the prop it came from (§6.4.6). */
    var fractureDepth: Int = 0,
) : Component() {

    override fun reset() {
        mode = DestructionMode.SHATTER
        integrity = 100f
        impactThreshold = 150f
        impactDamagePerNs = 0.5f
        fractureSource = FractureSource.AUTO
        fracture = ""
        maxRuntimePieces = 12
        brokenPrefab = ""
        breakImpulse = 2f
        surface = "wood"
        debrisCollidesWithCharacters = false
        broken = false
        fractureSeed = 0L
        fracturePoint.zero()
        fractureDepth = 0
    }

    /** Damage an impact of [impulse] N·s deals (0 below [impactThreshold]). */
    fun impactDamage(impulse: Float): Float =
        if (impulse.isFinite()) (impulse - impactThreshold).coerceAtLeast(0f) * impactDamagePerNs else 0f
}
