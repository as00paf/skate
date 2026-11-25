package com.pafoid.skate.templates

import java.io.File

/**
 * EDT-59 §5: the AI agent/skill files a new project can include (human answer 5: `AGENTS.md`, a Claude skill and a
 * Copilot pointer; no `.mcp.json` yet). Classpath resources under `/templates/ai/`, rendered with `{{projectName}}`
 * and `{{template}}`. A file that already exists is kept (the target folder may be non-empty, decision 3).
 */
object AiFiles {
    private const val RESOURCES = "/templates/ai/"

    /** Project-relative target to resource name (resources avoid dot-folders, which packaging tools may skip). */
    val FILES: Map<String, String> = linkedMapOf(
        "AGENTS.md" to "AGENTS.md",
        ".claude/skills/skate-project/SKILL.md" to "SKILL.md",
        ".github/copilot-instructions.md" to "copilot-instructions.md",
    )

    fun render(text: String, projectName: String, templateId: String): String =
        text.replace("{{projectName}}", projectName).replace("{{template}}", templateId)

    /** Writes the files that do not exist yet; returns the ones written. */
    fun write(projectDir: File, projectName: String, templateId: String): List<File> =
        FILES.mapNotNull { (target, resource) ->
            val file = File(projectDir, target)
            if (file.exists()) return@mapNotNull null
            val text = AiFiles::class.java.getResourceAsStream(RESOURCES + resource)?.use { it.readBytes().decodeToString() }
                ?: throw ProjectCreationException("Missing resource $RESOURCES$resource")
            file.parentFile.mkdirs()
            file.writeText(render(text, projectName, templateId))
            file
        }
}
