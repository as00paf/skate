package com.pafoid.skate.engine.render.lighting

import org.joml.Vector3f

/** Per-object light selection (GL-free): keeps only lights whose sphere of influence touches the object's bounds. */
object LightCulling {
    const val MAX_LIGHTS_PER_OBJECT = 16

    /**
     * Writes into [out] the indices (into [lights]) of the lights affecting a sphere at [center] with [radius],
     * strongest first, at most [out].size. Returns the count. Spot lights are also rejected when the sphere is
     * entirely behind the light's cone apex plane.
     */
    fun select(lights: List<LightData>, center: Vector3f, radius: Float, out: IntArray, scores: FloatArray = FloatArray(out.size)): Int {
        var count = 0
        for (i in lights.indices) {
            val l = lights[i]
            val dx = l.position.x - center.x
            val dy = l.position.y - center.y
            val dz = l.position.z - center.z
            val dist2 = dx * dx + dy * dy + dz * dz
            val reach = l.range + radius
            if (dist2 > reach * reach) continue
            val peak = maxOf(l.color.x, l.color.y, l.color.z)
            val score = peak / (1f + dist2)
            // insertion into a descending top-N list
            var pos = count
            if (count == out.size) {
                if (score <= scores[count - 1]) continue
                pos = count - 1
            } else count++
            while (pos > 0 && scores[pos - 1] < score) {
                scores[pos] = scores[pos - 1]; out[pos] = out[pos - 1]; pos--
            }
            scores[pos] = score; out[pos] = i
        }
        return count
    }
}
