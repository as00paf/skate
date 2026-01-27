package com.pafoid.skate.engine.scenes

abstract class SceneInitializer {
    abstract suspend fun init(scene:Scene)
    abstract suspend fun loadResources(scene: Scene)
    abstract fun imgui()
}