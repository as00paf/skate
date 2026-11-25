package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.ecs.random.RandomService
import com.pafoid.skate.editor.settings.GraphicsQuality
import com.pafoid.skate.editor.settings.ShadowQuality
import com.pafoid.skate.editor.settings.TextureQuality
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.platform.steampipe.SteamPublishTarget
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2i
import java.io.File

@Serializable
data class Project(
    var name: String,
    var version: String = "1.0.0",
    var createdDate: Long = System.currentTimeMillis(),
    var lastOpenedDate: Long = System.currentTimeMillis(),
    var iconPath: String = "",
    var projectPath: String,
    var description: String = "",
    var defaultScene: String,
    val scenesPath: List<String>,
    var physicsFPS: Int = 60,
    @Contextual val resolution: Vector2i = Vector2i(1920, 1080),//TODO: Should be in gfx settings
    var useFbo: Boolean = true,
    var gameplaySettings: GameplaySettings = GameplaySettings(),
    /** Backend the exported game renders with (AST-12); the editor always uses OpenGL. */
    var graphicsBackend: GraphicsBackend = GraphicsBackend.DEFAULT,
    /** Seed of the engine's random streams in Play and in the exported game (ECS-14); see `RandomService.resolve`. */
    var randomSeed: Long = RandomService.DEFAULT_SEED,
    /** Graphics quality preset for the exported game (REN-30). */
    var graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    /** MSAA sample count for the exported game (REN-30). */
    var msaaSamples: Int = 4,
    /** Texture quality for the exported game (REN-30). */
    var textureQuality: TextureQuality = TextureQuality.HIGH,
    /** Shadow quality for the exported game (REN-30). */
    var shadowQuality: ShadowQuality = ShadowQuality.MEDIUM,
    /** Whether the exported game starts in fullscreen (EDT-57 / GUI-15). */
    var fullscreen: Boolean = false,
    /** Whether the exported game enables VSync (EDT-57 / GUI-15). */
    var vsync: Boolean = true,
    /** Supported UI locales for the exported game (EDT-57 / GUI-15); at least one is required. */
    var supportedLanguages: List<String> = listOf("en"),
    /** Startup locale for the exported game (EDT-57 / GUI-15); must be one of [supportedLanguages]. */
    var startupLanguage: String = "en",
    /**
     * Splash image the exported game shows at start (AST-29): a project file or an `engine:` reference; blank uses
     * the engine default (`engine:Textures/splash_screen.png`).
     */
    var splashPath: String = "",
    /** How long the splash shows, in seconds; 0 shows none (AST-29). */
    var splashSeconds: Float = DEFAULT_SPLASH_SECONDS,
    /** Frame cap of the exported game (AST-29): 0 is uncapped, else [MIN_TARGET_FPS]..[MAX_TARGET_FPS]. */
    var targetFps: Int = 0,
    /** Physics backend of the exported game and Play (AST-29); the game falls back to Bullet if Jolt fails to load. */
    var physicsBackend: PhysicsBackend = PhysicsBackend.BULLET,
    /** Steam app and depot this project uploads to (NET-07); null until set up. Holds no credentials. */
    var steamPublish: SteamPublishTarget? = null,
) {
    companion object {
        const val DEFAULT_SPLASH_SECONDS = 2f
        const val MAX_SPLASH_SECONDS = 10f
        const val MIN_TARGET_FPS = 15
        const val MAX_TARGET_FPS = 240

        /** [fps] as a valid cap: 0 (or less) stays uncapped, anything else is clamped to the supported range. */
        fun normalizedTargetFps(fps: Int): Int = if (fps <= 0) 0 else fps.coerceIn(MIN_TARGET_FPS, MAX_TARGET_FPS)

        /** [seconds] in 0..[MAX_SPLASH_SECONDS]; NaN shows no splash. */
        fun normalizedSplashSeconds(seconds: Float): Float = if (seconds.isNaN()) 0f else seconds.coerceIn(0f, MAX_SPLASH_SECONDS)
    }

    fun getProjectDirectory(): File {
        return File(projectPath).parentFile
    }

    fun getProjectFile(): File {
        return File(projectPath)
    }
}