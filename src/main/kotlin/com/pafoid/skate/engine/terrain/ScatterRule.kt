package com.pafoid.skate.engine.terrain

import kotlinx.serialization.Serializable

/**
 * What to scatter on a biome (REN-28 §7): a model (instanced, no behaviour) or a prefab (spawned as entities, capped),
 * how dense, where (height, slope), how far apart, and how each item is jittered. Placement is per chunk, seeded
 * `"<seedName>/scatter/<id>/<cx>,<cz>"`, so it is deterministic and independent of other chunks and rules.
 */
@Serializable
data class ScatterRule(
    /** Stable id: names the seed stream and, later, remembered state (REN-28 §15.10). */
    val id: String = "rule",
    /** A model path (`.obj`, `.glb`...) drawn as instance batches. */
    val model: String = "",
    /** Optional `.skmat` override for every part of [model]. */
    val material: String = "",
    /** A prefab GUID or path: items are spawned as entities under the terrain (scripts, lights...), at most [maxEntities]. */
    val prefab: String = "",
    val densityPer100m2: Float = 1f,
    val minHeight: Float = -1e6f,
    val maxHeight: Float = 1e6f,
    val minSlope: Float = 0f,
    val maxSlope: Float = 35f,
    /** Minimum distance between items of this rule (the jittered grid's cell size). */
    val minSpacing: Float = 2f,
    val scaleMin: Float = 0.8f,
    val scaleMax: Float = 1.2f,
    /** Random yaw range in degrees (360 = any). */
    val yawJitter: Float = 360f,
    /** 0 = upright, 1 = aligned to the ground normal. */
    val alignToNormal: Float = 0f,
    /** Static colliders from the item (PHY). */
    val collider: Boolean = false,
    /** Items beyond this distance from the camera are not drawn (m). */
    val streamRadius: Float = 150f,
    /** Beyond this distance items thin out until [streamRadius] (m). */
    val fadeStart: Float = 100f,
    /** Skip flattened (stamped) ground: no rocks on the half-pipe. */
    val avoidStamps: Boolean = true,
    val maxEntities: Int = 64,
    /** Optional respawn of destroyed or taken items, in game seconds (REN-28 §17); 0 = never. */
    val respawnAfterSeconds: Float = 0f,
)

/**
 * One placed item (terrain-local position, yaw radians, uniform scale, ground normal), [random] in 0..1 seeded with the
 * item: it orders thinning by distance and feeds instance tint. [key] is stable per chunk and rule.
 */
data class ScatterInstance(
    val rule: Int,
    val key: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
    val scale: Float,
    val nx: Float,
    val ny: Float,
    val nz: Float,
    val random: Float,
)
