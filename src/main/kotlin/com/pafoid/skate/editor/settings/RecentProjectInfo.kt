package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.project.Project
import kotlinx.serialization.Serializable

@Serializable
data class RecentProjectInfo(
    val path: String,
    val name: String,
    val lastOpened: Long,
) {
    companion object {
        fun fromProjectSettings(project: Project): RecentProjectInfo {
            return RecentProjectInfo(
                path = project.projectPath,
                name = project.name,
                lastOpened = project.lastOpenedDate,
            )
        }
    }
}