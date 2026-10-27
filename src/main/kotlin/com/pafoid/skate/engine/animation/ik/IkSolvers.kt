package com.pafoid.skate.engine.animation.ik

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure IK solvers. They work on a skeleton described by a parent index array (-1 for roots) and
 * local-space matrices, and write corrected rotations back into the local matrices. All positions
 * are in model (skeleton root) space. No GL, scene or asset dependencies.
 */
object IkSolvers {
    private const val EPS = 1e-5f

    /** Model-space matrix of [bone], computed by walking up [parents]. */
    fun globalOf(parents: IntArray, locals: Array<Matrix4f>, bone: Int, out: Matrix4f = Matrix4f()): Matrix4f {
        var depth = 0
        var i = bone
        while (i >= 0) { depth++; i = parents[i] }
        val chain = IntArray(depth)
        i = bone
        var k = depth
        while (i >= 0) { chain[--k] = i; i = parents[i] }
        out.identity()
        for (b in chain) out.mul(locals[b])
        return out
    }

    /**
     * Two-bone IK (e.g. thigh-shin-foot or upper arm-forearm-hand). Rotates [root] and [mid] so [end] reaches
     * [target] (clamped to the chain's reach). [pole] picks the bend plane (knee/elbow direction); when null the
     * current bend is kept. [endRotation] (model space) optionally orients the end bone, scaled by [endRotationWeight].
     * [weight] 0..1 blends from the input pose to the solved pose. Returns false if the chain is degenerate.
     */
    fun solveTwoBone(
        parents: IntArray,
        locals: Array<Matrix4f>,
        root: Int,
        mid: Int,
        end: Int,
        target: Vector3f,
        pole: Vector3f? = null,
        weight: Float = 1f,
        endRotation: Quaternionf? = null,
        endRotationWeight: Float = 1f,
    ): Boolean {
        val w = weight.coerceIn(0f, 1f)
        if (w <= 0f) return false
        require(parents[mid] == root && parents[end] == mid) { "IK chain must be root -> mid -> end" }

        val gRoot = globalOf(parents, locals, root)
        val gMid = globalOf(parents, locals, mid)
        val gEnd = globalOf(parents, locals, end)
        val a = gRoot.getTranslation(Vector3f())
        val b = gMid.getTranslation(Vector3f())
        val c = gEnd.getTranslation(Vector3f())
        val l1 = a.distance(b)
        val l2 = b.distance(c)
        if (l1 < EPS || l2 < EPS) return false

        val toTarget = Vector3f(target).sub(a)
        val rawDist = toTarget.length()
        if (rawDist < EPS) return false
        val dirT = toTarget.div(rawDist)
        val dist = rawDist.coerceIn(abs(l1 - l2) + EPS, l1 + l2 - EPS)

        val bend = bendDirection(a, b, dirT, pole)
        val along = (l1 * l1 - l2 * l2 + dist * dist) / (2f * dist)
        val h = sqrt(max(0f, l1 * l1 - along * along))
        val newMid = Vector3f(dirT).mul(along).add(Vector3f(bend).mul(h)).add(a)
        val newEnd = Vector3f(dirT).mul(dist).add(a)

        val origRoot = rotationOf(locals[root])
        val origMid = rotationOf(locals[mid])
        val origEnd = rotationOf(locals[end])

        // Swing the root so the mid joint lands on newMid.
        val d1 = Quaternionf().rotationTo(Vector3f(b).sub(a), Vector3f(newMid).sub(a))
        applyGlobalDelta(parents, locals, root, d1, a)

        // Swing the mid so the end lands on newEnd.
        val m = globalOf(parents, locals, mid).getTranslation(Vector3f())
        val c2 = globalOf(parents, locals, end).getTranslation(Vector3f())
        val d2 = Quaternionf().rotationTo(Vector3f(c2).sub(m), Vector3f(newEnd).sub(m))
        applyGlobalDelta(parents, locals, mid, d2, m)

        if (endRotation != null && endRotationWeight > 0f) {
            val ge = globalOf(parents, locals, end)
            val current = ge.getUnnormalizedRotation(Quaternionf()).normalize()
            val wanted = Quaternionf(current).slerp(endRotation, endRotationWeight.coerceIn(0f, 1f))
            val delta = Quaternionf(wanted).mul(Quaternionf(current).invert())
            applyGlobalDelta(parents, locals, end, delta, ge.getTranslation(Vector3f()))
        }

        if (w < 1f) {
            blendRotation(locals[root], origRoot, w)
            blendRotation(locals[mid], origMid, w)
            blendRotation(locals[end], origEnd, w)
        }
        return true
    }

    /**
     * Rotates [bone] so its local [axis] (its "forward") points at [target]. The turn is limited to
     * [maxAngleRad] and scaled by [weight].
     */
    fun solveLookAt(
        parents: IntArray,
        locals: Array<Matrix4f>,
        bone: Int,
        axis: Vector3f,
        target: Vector3f,
        weight: Float = 1f,
        maxAngleRad: Float = Math.PI.toFloat(),
    ): Boolean {
        val w = weight.coerceIn(0f, 1f)
        if (w <= 0f) return false
        val g = globalOf(parents, locals, bone)
        val pos = g.getTranslation(Vector3f())
        val forward = g.transformDirection(Vector3f(axis))
        val desired = Vector3f(target).sub(pos)
        if (forward.lengthSquared() < EPS || desired.lengthSquared() < EPS) return false
        forward.normalize()
        desired.normalize()

        val delta = Quaternionf().rotationTo(forward, desired)
        val angle = 2f * acos(delta.w.coerceIn(-1f, 1f))
        val limited = min(angle, maxAngleRad) * w
        if (angle > EPS && limited < angle) {
            delta.set(Quaternionf().identity().slerp(delta, limited / angle))
        }
        applyGlobalDelta(parents, locals, bone, delta, pos)
        return true
    }

    private fun abs(v: Float) = if (v < 0f) -v else v

    private fun bendDirection(a: Vector3f, b: Vector3f, dirT: Vector3f, pole: Vector3f?): Vector3f {
        val hint = if (pole != null) Vector3f(pole).sub(a) else Vector3f(b).sub(a)
        hint.sub(Vector3f(dirT).mul(hint.dot(dirT)))
        if (hint.lengthSquared() < EPS) {
            // Straight chain with no usable hint: pick any perpendicular.
            val ref = if (abs(dirT.y) < 0.9f) Vector3f(0f, 1f, 0f) else Vector3f(1f, 0f, 0f)
            val side = dirT.cross(ref, Vector3f())
            side.cross(dirT, hint)
        }
        return hint.normalize()
    }

    /** Applies a model-space rotation [delta] about [pivot] to [bone], re-deriving its local matrix. */
    private fun applyGlobalDelta(
        parents: IntArray,
        locals: Array<Matrix4f>,
        bone: Int,
        delta: Quaternionf,
        pivot: Vector3f,
    ) {
        val g = globalOf(parents, locals, bone)
        val rotated = Matrix4f()
            .translate(pivot).rotate(delta).translate(-pivot.x, -pivot.y, -pivot.z)
            .mul(g)
        val parent = parents[bone]
        val parentGlobal = if (parent >= 0) globalOf(parents, locals, parent) else Matrix4f()
        parentGlobal.invert().mul(rotated, locals[bone])
    }

    private fun rotationOf(m: Matrix4f): Quaternionf = m.getUnnormalizedRotation(Quaternionf()).normalize()

    private fun blendRotation(local: Matrix4f, original: Quaternionf, w: Float) {
        val t = local.getTranslation(Vector3f())
        val s = local.getScale(Vector3f())
        val q = Quaternionf(original).slerp(rotationOf(local), w)
        local.translationRotateScale(t, q, s)
    }
}
