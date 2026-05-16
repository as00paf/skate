package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.project.Project
import kotlinx.serialization.Serializable

@Serializable
data class RecentProjectInfo(
    val path: String,
    val name: String,
    val lastOpened: Long,
    val engineVersion: String
) {
    companion object {
        fun fromProjectSettings(project: Project): RecentProjectInfo {
            return RecentProjectInfo(
                path = project.metadata.projectPath,
                name = project.metadata.name,
                lastOpened = project.metadata.lastOpenedDate,
                engineVersion = project.metadata.engineVersion
            )
        }
    }
}