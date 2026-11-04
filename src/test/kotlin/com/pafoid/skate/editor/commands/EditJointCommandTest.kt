package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.EditJointCommand
import com.pafoid.skate.editor.commands.objects.JointSettings
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditJointCommandTest {
    @Test
    fun EditJoint_ExecuteAndUndoRestoreEveryField() {
        val joint = JointComponent()
        val edited = JointSettings.of(joint).copy(
            jointType = JointType.HINGE, connectedBodyId = 7, anchor = Vector3f(1f, 2f, 3f), limitMax = 2.5f,
        )
        val command = EditJointCommand(joint, edited)
        command.execute()
        assertEquals(JointType.HINGE, joint.jointType)
        assertEquals(7, joint.connectedBodyId)
        assertEquals(Vector3f(1f, 2f, 3f), joint.anchor)
        assertEquals(2.5f, joint.limitMax)
        command.undo()
        assertEquals(JointType.FIXED, joint.jointType)
        assertEquals(-1, joint.connectedBodyId)
        assertEquals(Vector3f(), joint.anchor)
        assertEquals(1f, joint.limitMax)
    }

    @Test
    fun EditJoint_SnapshotIsDetachedFromLiveVectors() {
        val joint = JointComponent()
        val snapshot = JointSettings.of(joint)
        joint.anchor.set(9f, 9f, 9f)
        assertEquals(Vector3f(), snapshot.anchor)
    }

    @Test
    fun EditJoint_NoOpWhenUnchanged() {
        val joint = JointComponent()
        assertTrue(EditJointCommand(joint, JointSettings.of(joint)).isNoOp)
        assertFalse(EditJointCommand(joint, JointSettings.of(joint).copy(limitMin = -3f)).isNoOp)
    }
}
