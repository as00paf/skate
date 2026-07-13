package com.pafoid.skate.editor.project

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Project(
    val metadata: ProjectMetadata,
    val defaultScene: String = "main",
    val assetPaths: List<String> = listOf("Assets"),
    val scenePaths: List<String> = listOf("Scenes"),
    val buildPaths: List<String> = listOf("Builds"),
    val gameplaySettings: GameplaySettings = GameplaySettings(),
) {
    fun getProjectDirectory(): File {
        return File(metadata.projectPath).parentFile
    }

    fun getProjectFile(): File {
        return File(metadata.projectPath)
    }
}