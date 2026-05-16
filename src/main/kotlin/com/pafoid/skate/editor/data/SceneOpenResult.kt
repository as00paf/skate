package com.pafoid.skate.editor.data

sealed class SceneOpenResult {
    object Cancelled : SceneOpenResult()
    data class Loaded(val path: String) : SceneOpenResult()
    data class Failed(val path: String, val reason: String) : SceneOpenResult()
}