package com.pafoid.skate.editor.project

import kotlinx.serialization.Serializable

@Serializable
data class ProjectMetadata(
    val name: String,
    val version: String = "1.0.0",
    val engineVersion: String,
    val createdDate: Long = System.currentTimeMillis(),
    val lastOpenedDate: Long = System.currentTimeMillis(),
    val projectPath: String,
    val description: String = ""
)