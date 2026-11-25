package com.pafoid.skate.templates

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import java.io.File

/**
 * Choices of the New Project wizard, `skate create` and the MCP `create_project` tool (EDT-59 §3.3).
 * [copyEngineDefaults], [includeAiFiles] and [versionControl] are creation-time actions; the project does not store
 * them (EDT-59 Q2). The others become [Project] fields.
 */
data class NewProjectSettings(
    val template: String = TemplateRegistry.DEFAULT_ID,
    /** Frame cap of the exported game: 0 is uncapped, else [Project.MIN_TARGET_FPS]..[Project.MAX_TARGET_FPS]. */
    val targetFps: Int = 0,
    val physicsBackend: PhysicsBackend = PhysicsBackend.BULLET,
    val graphicsBackend: GraphicsBackend = GraphicsBackend.DEFAULT,
    /** Copies `AGENTS.md`, a Claude skill and a Copilot pointer into the project (EDT-59 §5). */
    val includeAiFiles: Boolean = false,
    /** PNG or JPG copied to `Assets/Textures/splash.<ext>`; null keeps the engine's splash. */
    val splashImage: File? = null,
    /** EDT-70 / AST-26 §4.3: copy every engine default into the project, or reference them (basic shaders copied). */
    val copyEngineDefaults: Boolean = true,
    /** EDT-59 §6: `git init` with the Skate `.gitignore`/`.gitattributes` and a first commit (EDT-60 phase 1). */
    val versionControl: Boolean = false,
) {
    companion object {
        val SPLASH_EXTENSIONS = setOf("png", "jpg", "jpeg")

        fun isValidTargetFps(fps: Int): Boolean = fps == 0 || fps in Project.MIN_TARGET_FPS..Project.MAX_TARGET_FPS

        fun isValidSplash(file: File?): Boolean = file == null || (file.isFile && file.extension.lowercase() in SPLASH_EXTENSIONS)
    }

    /** Why these settings cannot create a project; empty when they can. */
    fun problems(): List<String> = buildList {
        if (TemplateRegistry.get(template) == null) {
            add("Unknown template '$template' (available: ${TemplateRegistry.ids().joinToString()})")
        }
        if (!isValidTargetFps(targetFps)) {
            add("Target FPS must be 0 (uncapped) or ${Project.MIN_TARGET_FPS}-${Project.MAX_TARGET_FPS}, not $targetFps")
        }
        if (!isValidSplash(splashImage)) add("Splash image must be an existing PNG or JPG file: ${splashImage?.path}")
    }
}
