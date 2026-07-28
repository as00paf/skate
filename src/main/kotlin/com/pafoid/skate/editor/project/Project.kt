package com.pafoid.skate.editor.project

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Project(
    var name: String,
    var version: String = "1.0.0",
    var createdDate: Long = System.currentTimeMillis(),
    var lastOpenedDate: Long = System.currentTimeMillis(),
    var projectPath: String,// TODO: make sure its not the dir anywhere
    var description: String = "",
    var defaultScene: String = "MainScene",
    var physicsFPS: Int = 60,
    var gameplaySettings: GameplaySettings = GameplaySettings(),
) {
    fun getProjectDirectory(): File {
        return File(projectPath).parentFile
    }

    fun getProjectFile(): File {
        return File(projectPath)
    }
}