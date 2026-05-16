package com.pafoid.skate.engine.ecs.scene

import com.pafoid.skate.engine.ecs.Scene

abstract class SceneInitializer {
    // Callback for progress reporting: (Progress [0..1], Message)
    var onProgress: ((Float, String) -> Unit)? = null

    abstract suspend fun init(scene: Scene)
    abstract suspend fun loadResources(scene: Scene)

    protected fun reportProgress(progress: Float, message: String) {
        onProgress?.invoke(progress, message)
    }
}