package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneEventPublisher

class SceneActionEventPublisher(
    private val eventSystem: EventSystem,
) : SceneEventPublisher {
    override fun publishOpened(scene: Scene) {
        eventSystem.publish(SceneAction.Opened(scene))
    }

    override fun publishChanged() {
        eventSystem.publish(SceneAction.Changed)
    }

    override fun publishClosing(scene: Scene) {
        eventSystem.publish(SceneAction.Closing(scene))
    }

    override fun publishClosed(scene: Scene) {
        eventSystem.publish(SceneAction.Closed(scene))
    }
}
