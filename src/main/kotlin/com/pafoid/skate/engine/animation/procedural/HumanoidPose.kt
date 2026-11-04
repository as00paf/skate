package com.pafoid.skate.engine.animation.procedural

import com.pafoid.skate.engine.animation.graph.AnimParameters
import com.pafoid.skate.engine.animation.graph.Motion
import com.pafoid.skate.engine.animation.graph.Pose
import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.animation.retarget.HumanoidBone
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import kotlinx.serialization.Serializable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/** A foot position in leg lengths: [along] the character's forward, toward the [side] the chest turns to, and [up]. */
@Serializable
data class FootTarget(val along: Float = 0f, val side: Float = 0f, val up: Float = 0f)

/**
 * One key of a procedural humanoid pose, authored relative to the character's forward (travel) direction.
 * Distances are fractions of the leg length and angles are degrees, so a pose fits any humanoid rig.
 * The "front" limbs are the ones on the leading side (left for a left-foot-forward stance).
 */
@Serializable
data class HumanoidPoseDef(
    /** Hips turned away from forward, toward the side the chest faces. 0 faces forward, 90 is a side stance. */
    val facingDeg: Float = 90f,
    val hipDrop: Float = 0.1f,
    /** Hips moved along forward (negative = toward the back foot). */
    val hipShift: Float = 0f,
    /** Upper body bent toward the chest side. */
    val leanDeg: Float = 10f,
    /** Upper chest turned back toward forward. */
    val shoulderOpenDeg: Float = 15f,
    /** Head turned back toward forward. */
    val headTurnDeg: Float = 60f,
    /** Upper arms below horizontal (0 = T-pose, 90 = hanging). */
    val armDownDeg: Float = 55f,
    /** Arms swung toward the chest side. */
    val armFrontDeg: Float = 15f,
    val elbowBendDeg: Float = 25f,
    val frontFoot: FootTarget = FootTarget(along = 0.35f),
    val backFoot: FootTarget = FootTarget(along = -0.35f),
    /** Toes turned from pointing at the chest side (0) toward forward (90). */
    val frontFootTurnDeg: Float = 0f,
    val backFootTurnDeg: Float = 0f,
)

@Serializable
data class PoseKeyDef(val time: Float, val pose: HumanoidPoseDef)

/**
 * A clip made of procedural pose keys (seconds), eased between keys. Use it as a stand-in until a real clip with the
 * same name is added to the animator: real clips win over procedural ones.
 */
@Serializable
data class ProceduralClipDef(
    val keys: List<PoseKeyDef>,
    /** Left foot leads (a "regular" stance). False mirrors the pose (right foot leads). */
    val leftFootForward: Boolean = true,
) {
    init {
        require(keys.isNotEmpty()) { "A procedural clip needs at least one key" }
        require(keys.zipWithNext().all { (a, b) -> b.time > a.time }) { "Procedural clip keys must have increasing times" }
    }

    fun build(name: String, builder: HumanoidPoseBuilder): ProceduralClipMotion {
        val poses = keys.map { key -> Pose(builder.boneCount).also { builder.build(key.pose, leftFootForward, it) } }
        return ProceduralClipMotion(name, keys.map { it.time }.toFloatArray(), poses)
    }
}

/** Plays baked key poses, eased with smoothstep. A single key is a static pose. */
class ProceduralClipMotion(
    val name: String,
    private val times: FloatArray,
    private val poses: List<Pose>,
) : Motion {
    init { require(times.size == poses.size && times.isNotEmpty()) { "Times and poses must match" } }

    private val clipDuration = if (times.size == 1) 1f else times.last().coerceAtLeast(MIN_DURATION)

    override fun duration(params: AnimParameters) = clipDuration

    override fun evaluate(params: AnimParameters, normalizedTime: Float, out: Pose) {
        val time = normalizedTime.coerceIn(0f, 1f) * clipDuration
        if (time <= times.first() || poses.size == 1) return out.set(poses.first())
        if (time >= times.last()) return out.set(poses.last())
        val hi = times.indexOfFirst { it >= time }
        val lo = hi - 1
        val w = (time - times[lo]) / (times[hi] - times[lo])
        Pose.blend(poses[lo], poses[hi], w * w * (3f - 2f * w), out)
    }

    private companion object { const val MIN_DURATION = 0.05f }
}

/**
 * Bakes [HumanoidPoseDef]s on a humanoid [skeleton] starting from its bind pose. Pure (no GL); the body frame (up,
 * right, forward) and the leg length are measured from the rig, so it works for any import axes and unit scale.
 * Legs are placed with two-bone IK, the other limbs are aimed.
 */
class HumanoidPoseBuilder(private val skeleton: Skeleton) {
    val boneCount = skeleton.boneCount
    private val parents = IntArray(boneCount) { -1 }
    private val bind = Array(boneCount) { Matrix4f() }
    private val slots = mutableMapOf<HumanoidBone, Int>()

    private val up = Vector3f()
    private val forward = Vector3f()
    private val restRight = Vector3f()
    private val restAnkles = Vector3f()
    private val legLength: Float

    init {
        fun walk(bone: Bone, parent: Int) {
            if (bone.index in 0 until boneCount) {
                parents[bone.index] = parent
                bind[bone.index].set(bone.bindLocalTransform)
                HumanoidBone.fromName(bone.name)?.let { slots.putIfAbsent(it, bone.index) }
            }
            val next = if (bone.index in 0 until boneCount) bone.index else parent
            bone.children.forEach { walk(it, next) }
        }
        walk(skeleton.rootBone, -1)
        val missing = REQUIRED - slots.keys
        require(missing.isEmpty()) { "Not a humanoid rig, missing bones: ${missing.joinToString()}" }

        val hips = restPos(HumanoidBone.HIPS)
        restPos(HumanoidBone.HEAD).sub(hips, up).normalize()
        restPos(HumanoidBone.RIGHT_UPPER_LEG).sub(restPos(HumanoidBone.LEFT_UPPER_LEG), restRight)
        restRight.sub(Vector3f(up).mul(restRight.dot(up))).normalize()
        up.cross(restRight, forward).normalize()
        restAnkles.set(restPos(HumanoidBone.LEFT_FOOT)).add(restPos(HumanoidBone.RIGHT_FOOT)).mul(0.5f)
        legLength = restPos(HumanoidBone.LEFT_UPPER_LEG).distance(restPos(HumanoidBone.LEFT_LOWER_LEG)) +
            restPos(HumanoidBone.LEFT_LOWER_LEG).distance(restPos(HumanoidBone.LEFT_FOOT))
    }

    /** Writes the local pose for [def] into [out]. */
    fun build(def: HumanoidPoseDef, leftFootForward: Boolean, out: Pose) {
        val locals = Array(boneCount) { Matrix4f(bind[it]) }
        // The chest turns to the side away from the leading foot.
        val chestSide = if (leftFootForward) Vector3f(restRight) else Vector3f(restRight).negate()
        val facing = blend(forward, chestSide, def.facingDeg.coerceIn(-170f, 170f))

        // Hips: drop, shift and turn the whole body.
        val hips = slot(HumanoidBone.HIPS)
        val offset = Vector3f(up).mul(-def.hipDrop * legLength).add(Vector3f(forward).mul(def.hipShift * legLength))
        setGlobal(locals, hips, Matrix4f().translation(offset).mul(global(locals, hips)))
        val turn = Quaternionf().rotationTo(forward, facing)
        rotate(locals, hips, turn)

        // Upper body: lean toward the chest side, open the shoulders, turn the head toward travel.
        val spine = SPINE.mapNotNull { slots[it] }
        val leanAxis = Vector3f(up).cross(facing).normalize()
        val lean = Math.toRadians(def.leanDeg.toDouble()).toFloat() / spine.size
        spine.forEach { rotate(locals, it, Quaternionf().fromAxisAngleRad(leanAxis, lean)) }
        spine.lastOrNull()?.let { rotate(locals, it, yawToward(facing, forward, def.shoulderOpenDeg)) }
        val headTurn = yawToward(facing, forward, def.headTurnDeg - def.shoulderOpenDeg)
        val neck = slots[HumanoidBone.NECK]
        if (neck != null) {
            rotate(locals, neck, Quaternionf().slerp(headTurn, 0.5f))
            rotate(locals, slot(HumanoidBone.HEAD), Quaternionf().slerp(headTurn, 0.5f))
        } else {
            rotate(locals, slot(HumanoidBone.HEAD), headTurn)
        }

        // Arms: out along the shoulder line (which the hip turn swung toward travel), then down and forward.
        for (left in listOf(true, false)) {
            val upper = slots[if (left) HumanoidBone.LEFT_UPPER_ARM else HumanoidBone.RIGHT_UPPER_ARM] ?: continue
            val lower = slots[if (left) HumanoidBone.LEFT_LOWER_ARM else HumanoidBone.RIGHT_LOWER_ARM] ?: continue
            val hand = slots[if (left) HumanoidBone.LEFT_HAND else HumanoidBone.RIGHT_HAND]
            val out0 = turn.transform(if (left) Vector3f(restRight).negate() else Vector3f(restRight))
            val horizontal = blend(out0, facing, def.armFrontDeg)
            val upperDir = blend(horizontal, Vector3f(up).negate(), def.armDownDeg)
            aim(locals, upper, lower, upperDir)
            if (hand != null) aim(locals, lower, hand, blend(upperDir, facing, def.elbowBendDeg))
        }

        // Legs: two-bone IK to the foot targets, knees toward the chest side, then turn the toes.
        for (front in listOf(true, false)) {
            val left = front == leftFootForward
            val target = if (front) def.frontFoot else def.backFoot
            val upLeg = slot(if (left) HumanoidBone.LEFT_UPPER_LEG else HumanoidBone.RIGHT_UPPER_LEG)
            val leg = slot(if (left) HumanoidBone.LEFT_LOWER_LEG else HumanoidBone.RIGHT_LOWER_LEG)
            val foot = slot(if (left) HumanoidBone.LEFT_FOOT else HumanoidBone.RIGHT_FOOT)
            val goal = Vector3f(restAnkles)
                .add(Vector3f(forward).mul(target.along * legLength))
                .add(Vector3f(chestSide).mul(target.side * legLength))
                .add(Vector3f(up).mul(target.up * legLength))
            val pole = position(locals, upLeg).add(goal).mul(0.5f)
                .add(Vector3f(facing).mul(legLength))
                .add(Vector3f(forward).mul((if (front) 0.2f else -0.2f) * legLength))
            IkSolvers.solveTwoBone(parents, locals, upLeg, leg, foot, goal, pole)

            val toes = slots[if (left) HumanoidBone.LEFT_TOES else HumanoidBone.RIGHT_TOES] ?: continue
            val turnDeg = if (front) def.frontFootTurnDeg else def.backFootTurnDeg
            // Keep the rest pitch so the sole stays flat on the deck whatever the knee bend.
            val rest = position(bind, toes).sub(position(bind, foot))
            val rise = rest.dot(up)
            val flat = Vector3f(rest).sub(Vector3f(up).mul(rise)).length()
            val toward = blend(chestSide, forward, turnDeg).mul(flat).add(Vector3f(up).mul(rise))
            aim(locals, foot, toes, toward)
        }

        for (i in 0 until boneCount) out.setBone(i, locals[i])
    }

    private fun slot(bone: HumanoidBone) = slots.getValue(bone)

    private fun restPos(bone: HumanoidBone) = position(bind, slot(bone))

    private fun global(locals: Array<Matrix4f>, bone: Int) = IkSolvers.globalOf(parents, locals, bone)

    private fun position(locals: Array<Matrix4f>, bone: Int) = global(locals, bone).getTranslation(Vector3f())

    private fun setGlobal(locals: Array<Matrix4f>, bone: Int, g: Matrix4f) {
        val parent = parents[bone]
        val parentGlobal = if (parent >= 0) global(locals, parent) else Matrix4f()
        parentGlobal.invert().mul(g, locals[bone])
    }

    /** Applies a model-space rotation [delta] to [bone] about its own position. */
    private fun rotate(locals: Array<Matrix4f>, bone: Int, delta: Quaternionf) {
        val g = global(locals, bone)
        val p = g.getTranslation(Vector3f())
        setGlobal(locals, bone, Matrix4f().translate(p).rotate(delta).translate(-p.x, -p.y, -p.z).mul(g))
    }

    /** Rotates [bone] so the segment to [child] points along [dir]. */
    private fun aim(locals: Array<Matrix4f>, bone: Int, child: Int, dir: Vector3f) {
        val segment = position(locals, child).sub(position(locals, bone))
        if (segment.lengthSquared() < 1e-10f || dir.lengthSquared() < 1e-10f) return
        rotate(locals, bone, Quaternionf().rotationTo(segment, dir))
    }

    /** Rotation about [up] that turns [from] by [deg] toward [to]. */
    private fun yawToward(from: Vector3f, to: Vector3f, deg: Float): Quaternionf {
        val sign = if (Vector3f(from).cross(to).dot(up) >= 0f) 1f else -1f
        return Quaternionf().fromAxisAngleRad(up, sign * Math.toRadians(deg.toDouble()).toFloat())
    }

    private companion object {
        val REQUIRED = setOf(
            HumanoidBone.HIPS, HumanoidBone.HEAD,
            HumanoidBone.LEFT_UPPER_LEG, HumanoidBone.LEFT_LOWER_LEG, HumanoidBone.LEFT_FOOT,
            HumanoidBone.RIGHT_UPPER_LEG, HumanoidBone.RIGHT_LOWER_LEG, HumanoidBone.RIGHT_FOOT,
        )
        val SPINE = listOf(HumanoidBone.SPINE, HumanoidBone.CHEST, HumanoidBone.UPPER_CHEST)

        /** Unit vector [deg] of the way from [a] toward [b] (an arc in their plane when they are perpendicular). */
        fun blend(a: Vector3f, b: Vector3f, deg: Float): Vector3f {
            val r = Math.toRadians(deg.toDouble())
            val v = Vector3f(a).normalize().mul(cos(r).toFloat()).add(Vector3f(b).normalize().mul(sin(r).toFloat()))
            return if (v.lengthSquared() < 1e-10f) Vector3f(a).normalize() else v.normalize()
        }
    }
}
