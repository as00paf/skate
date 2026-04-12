package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.database.AssetRegistryData
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Project(
    val metadata: ProjectMetadata,
    val defaultScene: String = "",
    val assetPaths: List<String> = listOf("Assets"),
    val scenePaths: List<String> = listOf("Scenes"),
    val buildPaths: List<String> = listOf("Builds"),
    val gameplaySettings: GameplaySettings = GameplaySettings(),
    val assetRegistry: AssetRegistryData? = null
) {
    fun getProjectDirectory(): File {
        return File(metadata.projectPath).parentFile
    }

    fun getProjectFile(): File {
        return File(metadata.projectPath)
    }
}