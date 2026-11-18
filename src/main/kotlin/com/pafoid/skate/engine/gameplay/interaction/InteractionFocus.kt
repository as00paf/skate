package com.pafoid.skate.engine.gameplay.interaction

import org.joml.Vector3f

/** One interactable as the focus sees it: where it is, its reach and whether this interactor may use it. */
data class FocusCandidate(val position: Vector3f, val radius: Float, val offered: Boolean = true)

/**
 * Which interactable an interactor focuses (GPL-24 §5.2), pure. Only offered candidates within their radius count;
 * among those, the one most in front (largest dot product between [forward] and the direction to it) wins, and the
 * nearer one breaks a tie. Distances are horizontal plus vertical (plain 3D), as interaction ranges are small.
 */
object InteractionFocus {
    private const val TIE = 1e-4f

    /** Index of the focused candidate, or -1. [forward] need not be normalized. */
    fun choose(from: Vector3f, forward: Vector3f, candidates: List<FocusCandidate>): Int {
        val facing = Vector3f(forward).let { if (it.lengthSquared() > 0f) it.normalize() else it }
        val to = Vector3f()
        var best = -1
        var bestDot = 0f
        var bestDistance = 0f
        candidates.forEachIndexed { i, c ->
            if (!c.offered) return@forEachIndexed
            val distance = from.distance(c.position)
            if (distance > c.radius) return@forEachIndexed
            // Standing on it counts as straight ahead
            val dot = if (distance < 1e-5f) 1f else c.position.sub(from, to).normalize().dot(facing)
            val better = best < 0 || dot > bestDot + TIE || (dot > bestDot - TIE && distance < bestDistance)
            if (better) {
                best = i
                bestDot = dot
                bestDistance = distance
            }
        }
        return best
    }
}
