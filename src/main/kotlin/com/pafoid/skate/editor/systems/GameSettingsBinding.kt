package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ui.GameLocale
import com.pafoid.skate.engine.ui.GameSettings
import com.pafoid.skate.engine.ui.GameSettingsStore

/**
 * Keeps the game's own locale (GUI-10) on the current project's `game.settings.json` (EDT-39).
 *
 * Opening **or creating** a project attaches its file (creating publishes only [ProjectEvent.Created]); closing
 * detaches, so the pause menu's Language never writes into another project's settings.
 */
object GameSettingsBinding {
    fun install(events: EventSystem, locale: GameLocale, logger: LoggerService) {
        events.subscribe<ProjectEvent.Opened> { attach(it.project, locale, logger) }
        events.subscribe<ProjectEvent.Created> { attach(it.project, locale, logger) }
        events.subscribe<ProjectEvent.Closed> { locale.detach() }
    }

    private fun attach(project: Project, locale: GameLocale, logger: LoggerService) {
        locale.attach(GameSettingsStore(GameSettings.fileIn(project.getProjectDirectory()), logger))
    }
}
