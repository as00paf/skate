package com.pafoid.skate.engine.render.rhi

import kotlinx.serialization.Serializable

/**
 * Which RHI backend an exported game renders with (AST-12). Chosen per project and saved in the `.skateproject`
 * (`Project.graphicsBackend`). The editor ignores it and always uses OpenGL.
 *
 * Where the value travels in an export (see docs/testing/AST-12.md):
 * - the packed project file (inside `game.bin`): the project's choice;
 * - `game.settings.json` next to `game.bin` (`GameSettings.graphicsBackend`): written by the export, and a player can
 *   edit it, e.g. to fall back to OpenGL;
 * - `manifest.json` (`PackManifest.graphicsBackend`): build metadata only, nothing reads it at startup.
 *
 * Game startup (GPL-19) picks the backend with [resolve] before creating the window.
 */
@Serializable
enum class GraphicsBackend(val experimental: Boolean) {
    OPENGL(experimental = false),
    VULKAN(experimental = true);

    /** Editor string key of the backend's display name (`lbl.graphics_backend.opengl`, ...). */
    val labelKey: String get() = "lbl.graphics_backend.${name.lowercase()}"

    companion object {
        val DEFAULT = OPENGL

        /** `-Dskate.graphicsBackend=vulkan` overrides everything else (testing, support). */
        const val SYSTEM_PROPERTY = "skate.graphicsBackend"

        /** [value] as a backend, case-insensitive; null when blank or unknown. */
        fun parse(value: String?): GraphicsBackend? = value?.trim()?.let { v -> entries.firstOrNull { it.name.equals(v, ignoreCase = true) } }

        /**
         * The backend an exported game should start with: the [SYSTEM_PROPERTY] ([property]), then the player's
         * `game.settings.json` ([settings]), then the project's choice ([project]).
         */
        fun resolve(
            project: GraphicsBackend,
            settings: GraphicsBackend?,
            property: String? = System.getProperty(SYSTEM_PROPERTY),
        ): GraphicsBackend = parse(property) ?: settings ?: project
    }
}
