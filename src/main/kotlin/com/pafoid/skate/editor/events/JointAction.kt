package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.commands.objects.JointSettings
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.events.Event

sealed class JointAction(eventName: String) : Event(eventName) {
    data class Edit(val joint: JointComponent, val settings: JointSettings) : JointAction("joint.edit")
}
