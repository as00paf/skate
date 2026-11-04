package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f

/**
 * Server-side history of replicated states, one entry per tick, for lag compensation (NET-01 §7): hit tests for a
 * client are run against the world as that client saw it (its interpolation view tick), not the present.
 */
class StateHistory(private val schema: ReplicationSchema, val maxTicks: Int) {
    private val ticks = ArrayDeque<Pair<Int, Map<Int, IntArray>>>()

    val oldestTick: Int get() = ticks.firstOrNull()?.first ?: 0
    val newestTick: Int get() = ticks.lastOrNull()?.first ?: 0

    fun record(tick: Int, states: Map<Int, IntArray>) {
        if (ticks.isNotEmpty() && ticks.last().first >= tick) return
        ticks.addLast(tick to states)
        while (ticks.size > maxTicks) ticks.removeFirst()
    }

    /** State of [netId] at [tick] (blended between recorded ticks, clamped to the window); null if unknown. */
    fun stateAt(netId: Int, tick: Double): IntArray? {
        if (ticks.isEmpty()) return null
        val t = tick.coerceIn(oldestTick.toDouble(), newestTick.toDouble())
        var prev: Pair<Int, IntArray>? = null
        for ((k, states) in ticks) {
            val s = states[netId] ?: continue
            if (k.toDouble() == t) return s
            if (k > t) {
                val p = prev ?: return s
                val f = ((t - p.first) / (k - p.first)).toFloat()
                return schema.blend(p.second, s, f)
            }
            prev = k to s
        }
        return prev?.second
    }
}

/**
 * Temporarily moves objects back in time. Only Transforms are rewound; exact float values are saved and restored so
 * the authoritative simulation is not disturbed by quantization. Physics colliders are not moved: queries inside
 * [rewound] must read Transforms (or sync colliders themselves).
 */
internal class Rewinder(private val schema: ReplicationSchema) {
    private val transformOffset: Int = run {
        var off = 0
        for (r in schema.replicators) { if (r === TransformReplicator) return@run off; off += r.fieldCount }
        -1
    }

    private class Saved(val t: Transform, val pos: Vector3f, val rot: Vector3f, val scale: Vector3f)

    fun <T> rewound(objects: Map<Int, GameObject>, history: StateHistory, tick: Double, skip: (Int, GameObject) -> Boolean, block: () -> T): T {
        if (transformOffset < 0) return block()
        val saved = ArrayList<Saved>()
        try {
            for ((id, go) in objects) {
                if (skip(id, go)) continue
                val t = go.getComponent<Transform>() ?: continue
                val past = history.stateAt(id, tick) ?: continue
                saved += Saved(t, Vector3f(t.translation), Vector3f(t.rotation), Vector3f(t.scale))
                TransformReplicator.apply(go, past, transformOffset)
            }
            return block()
        } finally {
            for (s in saved) { s.t.translation.set(s.pos); s.t.rotation.set(s.rot); s.t.scale.set(s.scale) }
        }
    }
}
