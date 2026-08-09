package com.pafoid.skate.editor.project

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2i
import java.io.File

@Serializable
data class Project(
    var name: String,
    var version: String = "1.0.0",
    var createdDate: Long = System.currentTimeMillis(),
    var lastOpenedDate: Long = System.currentTimeMillis(),
    var iconPath: String = "",
    var projectPath: String,
    var description: String = "",
    var defaultScene: String,
    val scenesPath: List<String>,
    var physicsFPS: Int = 60,
    @Contextual val resolution: Vector2i = Vector2i(1920, 1080),//TODO: Should be in gfx settings
    var useFbo: Boolean = true,
    var gameplaySettings: GameplaySettings = GameplaySettings(),
) {
    fun getProjectDirectory(): File {
        return File(projectPath).parentFile
    }

    fun getProjectFile(): File {
        return File(projectPath)
    }
}