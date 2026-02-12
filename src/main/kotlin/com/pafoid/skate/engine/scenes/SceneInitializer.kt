package com.pafoid.skate.engine.scenes

abstract class SceneInitializer {
    // Callback for progress reporting: (Progress [0..1], Message)
    var onProgress: ((Float, String) -> Unit)? = null

    abstract suspend fun init(scene:Scene)
    abstract suspend fun loadResources(scene: Scene)
    abstract fun imgui()

    protected fun reportProgress(progress: Float, message: String) {
        onProgress?.invoke(progress, message)
    }
}