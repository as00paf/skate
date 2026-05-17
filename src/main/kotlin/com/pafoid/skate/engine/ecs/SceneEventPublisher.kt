package com.pafoid.skate.engine.ecs

interface SceneEventPublisher {
    fun publishOpened(scene: Scene)
    fun publishChanged()
    fun publishClosing(scene: Scene)
    fun publishClosed(scene: Scene)
}
