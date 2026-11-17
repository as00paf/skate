package com.pafoid.skate.editor.animation

import com.pafoid.skate.engine.animation.graph.AnimationStateMachine
import java.io.File

/**
 * Controller assets offered by the Animator inspector (EDT-16): the engine's bundled controllers plus every
 * `*.controller.json` under the open project. The project folder is set by AnimatorActionHandler on project open.
 */
class AnimatorControllerCatalog(private val bundled: List<String> = BUNDLED) {
    var projectRoot: File? = null
        set(value) {
            field = value
            scanned = null
        }

    private var scanned: List<String>? = null

    /** Picker entries: bundled, then project files (sorted), then [current] if it is none of those. */
    fun options(current: String?): List<String> {
        val project = scanned ?: scan().also { scanned = it }
        return (bundled + project + listOfNotNull(current)).distinct()
    }

    /** Forgets the project scan so newly added controller files show up. */
    fun refresh() { scanned = null }

    private fun scan(): List<String> {
        val root = projectRoot?.takeIf { it.isDirectory } ?: return emptyList()
        return root.walkTopDown()
            .onEnter { it.name !in SKIPPED_DIRS }
            .filter { it.isFile && isController(it.path) }
            .map { it.absolutePath }
            .sorted()
            .toList()
    }

    companion object {
        /** The editor's catalog, read by the Animator inspector. */
        val shared by lazy { AnimatorControllerCatalog() }

        const val SKATER = com.pafoid.skate.engine.assets.EngineDefaults.Animations.SKATER_CONTROLLER
        val BUNDLED = listOf(SKATER)
        private val SKIPPED_DIRS = setOf("Builds", ".git", "build")

        /** Files the picker lists and the drop target accepts. */
        fun isController(path: String) = path.endsWith(".controller.json", ignoreCase = true)

        /** Short label for a controller path: its file name without `.controller.json`. */
        fun displayName(path: String): String =
            path.substringAfterLast('/').substringAfterLast('\\').removeSuffix(".controller.json").removeSuffix(".json")

        /** "current", or "current -> next" while a crossfade runs. */
        fun stateReadout(machine: AnimationStateMachine): String {
            val next = machine.nextState ?: return machine.currentState
            return "${machine.currentState} -> $next"
        }
    }
}
