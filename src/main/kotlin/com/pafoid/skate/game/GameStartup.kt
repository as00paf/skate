package com.pafoid.skate.game

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.ui.FrameLimiter
import com.pafoid.skate.engine.ui.FrameRateOption
import com.pafoid.skate.engine.ui.GameOptions
import com.pafoid.skate.engine.ui.GameSettingsStore

/**
 * The exported game's start-up steps that shape its pause menu, kept out of [main] so the parity test (GUI-12,
 * GUI-20) runs the same code as the game.
 */
object GameStartup {

    /**
     * Sets the locale the game starts in (GUI-15, GUI-20): the player's own choice, else the startup language the
     * export took from the editor ([com.pafoid.skate.engine.ui.GameSettings.startLocale]).
     */
    fun applyLanguage(project: Project, settings: GameSettingsStore) {
        settings.update {
            it.copy(supportedLanguages = project.supportedLanguages, locale = it.startLocale(project.supportedLanguages))
        }
    }

    /**
     * The frame cap: the project's target FPS (AST-29) unless the player picked another rate in the pause menu
     * (GUI-17). Replaces the engine's unpaced Frame rate row (ECS-18) in place, so the row keeps its position.
     */
    fun installFrameRate(options: GameOptions, limiter: FrameLimiter, project: Project, settings: GameSettingsStore) {
        val option = FrameRateOption.option(limiter, Project.normalizedTargetFps(project.targetFps))
        option.apply(FrameRateOption.saved(settings.settings))
        options.register(option)
    }
}
