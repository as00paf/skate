package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import org.joml.Vector3f

/** Snapshot of every editable [JointComponent] field. */
data class JointSettings(
    val jointType: JointType,
    val connectedBodyId: Int,
    val anchor: Vector3f,
    val connectedAnchor: Vector3f,
    val axis: Vector3f,
    val connectedAxis: Vector3f,
    val limitMin: Float,
    val limitMax: Float,
    val swingSpan1: Float,
    val swingSpan2: Float,
    val twistSpan: Float,
    val linearLower: Vector3f,
    val linearUpper: Vector3f,
    val angularLower: Vector3f,
    val angularUpper: Vector3f,
) {
    fun deepCopy() = copy(
        anchor = Vector3f(anchor), connectedAnchor = Vector3f(connectedAnchor), axis = Vector3f(axis),
        connectedAxis = Vector3f(connectedAxis), linearLower = Vector3f(linearLower),
        linearUpper = Vector3f(linearUpper), angularLower = Vector3f(angularLower), angularUpper = Vector3f(angularUpper),
    )

    fun applyTo(c: JointComponent) {
        c.jointType = jointType
        c.connectedBodyId = connectedBodyId
        c.anchor.set(anchor); c.connectedAnchor.set(connectedAnchor)
        c.axis.set(axis); c.connectedAxis.set(connectedAxis)
        c.limitMin = limitMin; c.limitMax = limitMax
        c.swingSpan1 = swingSpan1; c.swingSpan2 = swingSpan2; c.twistSpan = twistSpan
        c.linearLower.set(linearLower); c.linearUpper.set(linearUpper)
        c.angularLower.set(angularLower); c.angularUpper.set(angularUpper)
    }

    companion object {
        fun of(c: JointComponent) = JointSettings(
            c.jointType, c.connectedBodyId, c.anchor, c.connectedAnchor, c.axis, c.connectedAxis, c.limitMin, c.limitMax,
            c.swingSpan1, c.swingSpan2, c.twistSpan, c.linearLower, c.linearUpper, c.angularLower, c.angularUpper,
        ).deepCopy()
    }
}

/** Replaces all joint settings at once so one inspector edit is one undo step. */
class EditJointCommand(private val joint: JointComponent, settings: JointSettings) : Command {
    private val before = JointSettings.of(joint)
    private val after = settings.deepCopy()

    val isNoOp: Boolean get() = before == after

    override fun execute() = after.applyTo(joint)
    override fun undo() = before.applyTo(joint)
    override fun getDisplayName(): String = "Edit Joint"
    override fun getTargetName(): String = joint.name
}
