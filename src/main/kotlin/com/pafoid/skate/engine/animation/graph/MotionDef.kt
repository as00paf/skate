package com.pafoid.skate.engine.animation.graph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable description of a [Motion]: a clip reference or a blend tree of other motions.
 * Immutable; edit with the `with*` helpers (each returns a modified copy) so editor commands can keep before/after snapshots.
 */
@Serializable
sealed interface MotionDef {
    /** Names of every clip this motion (transitively) plays. */
    fun clipNames(): Set<String>

    /** Builds the runtime motion; [clip] resolves a clip name, null means missing. */
    fun build(clip: (String) -> Motion?): Motion
}

@Serializable
@SerialName("clip")
data class ClipRef(val clip: String) : MotionDef {
    override fun clipNames() = setOf(clip)
    override fun build(clip: (String) -> Motion?): Motion =
        clip(this.clip) ?: throw IllegalArgumentException("Unknown clip '${this.clip}'")
}

@Serializable
data class Blend1DChild(val threshold: Float, val motion: MotionDef)

@Serializable
@SerialName("blend1d")
data class Blend1DDef(val parameter: String, val children: List<Blend1DChild>) : MotionDef {
    init { require(children.isNotEmpty()) { "Blend tree '$parameter' needs at least one child" } }

    override fun clipNames() = children.flatMapTo(mutableSetOf()) { it.motion.clipNames() }
    override fun build(clip: (String) -> Motion?): Motion =
        BlendTree1D(parameter, children.map { it.threshold to it.motion.build(clip) })

    fun withChild(threshold: Float, motion: MotionDef) = copy(children = children + Blend1DChild(threshold, motion))
    fun withoutChild(index: Int): Blend1DDef = copy(children = children.filterIndexed { i, _ -> i != index })
    fun withThreshold(index: Int, threshold: Float) =
        copy(children = children.mapIndexed { i, c -> if (i == index) c.copy(threshold = threshold) else c })
    fun withChildMotion(index: Int, motion: MotionDef) =
        copy(children = children.mapIndexed { i, c -> if (i == index) c.copy(motion = motion) else c })
}

@Serializable
data class Blend2DChild(val x: Float, val y: Float, val motion: MotionDef)

@Serializable
@SerialName("blend2d")
data class Blend2DDef(val parameterX: String, val parameterY: String, val children: List<Blend2DChild>) : MotionDef {
    init { require(children.isNotEmpty()) { "Blend tree '$parameterX/$parameterY' needs at least one child" } }

    override fun clipNames() = children.flatMapTo(mutableSetOf()) { it.motion.clipNames() }
    override fun build(clip: (String) -> Motion?): Motion =
        BlendTree2D(parameterX, parameterY, children.map { Triple(it.x, it.y, it.motion.build(clip)) })

    fun withChild(x: Float, y: Float, motion: MotionDef) = copy(children = children + Blend2DChild(x, y, motion))
    fun withoutChild(index: Int): Blend2DDef = copy(children = children.filterIndexed { i, _ -> i != index })
    fun withPosition(index: Int, x: Float, y: Float) =
        copy(children = children.mapIndexed { i, c -> if (i == index) c.copy(x = x, y = y) else c })
    fun withChildMotion(index: Int, motion: MotionDef) =
        copy(children = children.mapIndexed { i, c -> if (i == index) c.copy(motion = motion) else c })
}
