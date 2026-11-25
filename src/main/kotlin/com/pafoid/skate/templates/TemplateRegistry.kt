package com.pafoid.skate.templates

/**
 * The New Project templates in display order (EDT-59 §3.1). No reflection or plugin discovery: a new template
 * (TLG-13 demos) is a new entry here.
 */
object TemplateRegistry {
    const val DEFAULT_ID = SkateGameTemplate.ID

    private val templates: List<ProjectTemplate> = listOf(
        EmptyTemplate,
        SkateGameTemplate,
        FeatureShowcaseTemplate,
        TetrisTemplate,
        MarbleRunTemplate,
    )

    /**
     * The project name `skate sample <id>` and the MCP `create_sample` tool give each sample template (unchanged
     * from the pre-EDT-59 sample builders, so existing scripts and docs keep working).
     */
    val sampleNames: Map<String, String> = linkedMapOf(
        FeatureShowcaseTemplate.ID to "FeatureShowcase",
        MarbleRunTemplate.ID to "MarbleRun",
        TetrisTemplate.ID to "Tetris",
    )

    fun all(): List<ProjectTemplate> = templates

    fun ids(): List<String> = templates.map { it.id }

    fun get(id: String): ProjectTemplate? = templates.firstOrNull { it.id == id }

    fun require(id: String): ProjectTemplate =
        get(id) ?: throw IllegalArgumentException("Unknown template '$id' (available: ${ids().joinToString()})")
}
