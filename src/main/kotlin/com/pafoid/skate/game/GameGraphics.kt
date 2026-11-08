package com.pafoid.skate.game

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.engine.ui.GameSettingsStore

/**
 * Picks and starts the exported game's graphics backend (GPL-19). [GraphicsBackend.resolve] chooses it from
 * `-Dskate.graphicsBackend`, then `game.settings.json`, then the packed project. Vulkan falls back to OpenGL
 * when it cannot start, with one log line and a note in the settings file.
 */
object GameGraphics {

    /** A started window and the backend it really runs on. */
    class Started<W>(val window: W, val backend: GraphicsBackend, val requested: GraphicsBackend) {
        val fellBack: Boolean get() = backend != requested
    }

    /** What the Vulkan backend draws so far; logged when a game starts on Vulkan. */
    const val VULKAN_STATE =
        "Vulkan backend is experimental: every render pass (shadows, geometry, post-processing, runtime UI, particles, " +
            "debug lines) matches OpenGL within documented tolerances (QA-16 parity suite), but it has not been run under " +
            "the Vulkan validation layer yet. Set \"graphicsBackend\": \"OPENGL\" in game.settings.json for the reference renderer."

    /** The backend to start with for [project]'s choice and the player's [store] (null when there is no settings file). */
    fun requested(project: GraphicsBackend, store: GameSettingsStore?, property: String? = System.getProperty(GraphicsBackend.SYSTEM_PROPERTY)) =
        GraphicsBackend.resolve(project, store?.settings?.graphicsBackend, property)

    /**
     * Opens the window for [requested] with [open]. If Vulkan throws (no loader, no device, init failure), logs one
     * error line, records it in [store] and opens an OpenGL window instead. OpenGL failures propagate: there is
     * nothing left to fall back to.
     */
    fun <W> start(requested: GraphicsBackend, logger: LoggerService, store: GameSettingsStore?, open: (GraphicsBackend) -> W): Started<W> {
        if (requested == GraphicsBackend.OPENGL) return Started(open(GraphicsBackend.OPENGL), GraphicsBackend.OPENGL, requested)
        val failure = try {
            val window = open(requested)
            logger.logGame("Graphics backend: ${requested.name}. $VULKAN_STATE", LoggerService.LogLevel.INFO)
            if (store?.settings?.graphicsNote != null) store.update { it.copy(graphicsNote = null) }
            return Started(window, requested, requested)
        } catch (e: Throwable) {
            e
        }
        val reason = failure.message ?: failure.javaClass.simpleName
        logger.logGame("Graphics backend ${requested.name} could not start ($reason); falling back to OPENGL.", LoggerService.LogLevel.ERROR)
        store?.update { it.copy(graphicsNote = "${requested.name} could not start: $reason. The game ran on OPENGL.") }
        return Started(open(GraphicsBackend.OPENGL), GraphicsBackend.OPENGL, requested)
    }
}
