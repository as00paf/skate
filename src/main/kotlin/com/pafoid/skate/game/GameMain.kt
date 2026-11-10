package com.pafoid.skate.game

import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.ScenePlaySnapshot
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.game.player.SkateGameplay
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.fileExtension
import com.pafoid.skate.engine.input.ViewRect
import com.pafoid.skate.engine.ui.GameSettings
import com.pafoid.skate.engine.ecs.random.RandomService
import com.pafoid.skate.engine.ui.GameSettingsStore
import com.pafoid.skate.game.ui.GameHudState
import com.pafoid.skate.game.ui.GameUiSystem
import org.lwjgl.glfw.GLFW
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.pack.PackedStrings
import com.pafoid.skate.engine.utils.Atlas
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.IntRect
import java.nio.ByteBuffer

private val engine = Engine()

fun main(args: Array<String>) {
    SkateGameplay.register()
    val pack = GameBin.read(engine.logger) ?: return
    val binFile = pack.bytes
    val (header, headerSize) = readFileHeader(binFile)
    if (header == null || headerSize <= 0) return

    val (project, icon) = loadProjectAndIcon(binFile, header, headerSize)
    if (project == null) return

    val projectManager = ProjectManager(engine)
    // game.settings.json next to the pack: the player's backend choice (AST-12) and locale (GUI-10).
    val settings = GameSettingsStore(GameSettings.fileIn(pack.file.parentFile), engine.logger)

    // Apply the project's startup language as the game's initial locale (GUI-15).
    if (project.startupLanguage.isNotEmpty() && project.startupLanguage in project.supportedLanguages) {
        settings.update { it.copy(startupLanguage = project.startupLanguage, supportedLanguages = project.supportedLanguages, locale = project.startupLanguage) }
    }

    val requested = GameGraphics.requested(project.graphicsBackend, settings)
    val window = GameGraphics.start(requested, engine.logger, settings) { backend ->
        Window(
            initialWidth = project.resolution.x,
            initialHeight = project.resolution.y,
            title = project.name,
            icon = icon,
            backend = backend,
            fullscreen = project.fullscreen,
            vsync = project.vsync,
        )
    }.window

    window.windowController.setFullscreen(project.fullscreen)
    engine.start(window.glfwWindow, window.device)
    SkateGameplay.install(engine)

    // Apply the project's physics rate to the engine's fixed time step (GUI-15).
    if (project.physicsFPS > 0) {
        engine.systemManager.fixedTimeStep = 1f / project.physicsFPS
    }
    SkateGameplay.install(engine)

    if (projectManager.openProject(project, binFile, header, headerSize)) {
        // The project's mixer (bus volumes and mutes set in the editor) applies to the exported game too (EDT-14).
        engine.audioEngine.applyConfig(project.gameplaySettings.audio.toConfig())
        // Apply game-side graphics settings from game.settings.json (overrides project defaults).
        applyGameGraphicsSettings(settings.settings, project)
        engine.runtimePlaying = true
        // ECS-14: -Dskate.seed, then game.settings.json "randomSeed", then the project's seed.
        engine.random.reseed(RandomService.resolve(project.randomSeed, settings.settings.randomSeed))
        installGameUi(window, settings, project)
        window.show(onClose = {
            // Engine first: on Vulkan the device and its window are released right after (VK-01 shutdown order).
            engine.destroy()
        }) { dt ->
            // The framebuffer is blitted over the whole window, so UI pointer mapping uses the window's size.
            val w = IntArray(1)
            val h = IntArray(1)
            GLFW.glfwGetWindowSize(window.glfwWindow, w, h)
            engine.inputProvider.mouseListener.setViewRect(ViewRect(0f, 0f, w[0].toFloat(), h[0].toFloat()))
            // Render (and lay out the UI) at the window's pixel size, as editor Play does for its viewport (GUI-12).
            GLFW.glfwGetFramebufferSize(window.glfwWindow, w, h)
            val fb = engine.renderer.frameBuffer
            GameView.resize(w[0] to h[0], fb.width to fb.height)?.let { (fw, fh) -> engine.resizeFrameBuffer(fw, fh) }
            engine.update(dt)
            testDraw(engine.renderer.frameBuffer.width, engine.renderer.frameBuffer.height)
        }
    }
}

/**
 * HUD plus the engine's pause menu (GUI-09), which behaves as in editor Play; only Restart and Quit are the game's:
 * Restart reloads the scene as it was at launch, Quit closes the window.
 */
private fun installGameUi(window: Window, settings: GameSettingsStore, project: Project) {
    val scene = engine.sceneManager.currentScene ?: return
    loadGameStrings()
    // The player's language choice is kept in the same game.settings.json (GUI-10).
    engine.gameLocale.attach(settings)
    val launchState = ScenePlaySnapshot.capture(scene, engine.serializer)
    val hud = GameUiSystem(GameHudState())
    engine.systemManager.addSystem(hud)
    engine.pauseActions.restart = {
        engine.sceneManager.currentScene?.let { launchState.restore(engine.sceneManager, engine.serializer, it) }
        // A restart replays the run: same random values as at launch.
        engine.random.restart()
        hud.resetState()
    }
    engine.pauseActions.quit = { GLFW.glfwSetWindowShouldClose(window.glfwWindow, true) }
    // Apply the post-processing quality preset from game settings (if any).
    applyPostProcessQuality(settings.settings.graphicsQuality)
}

/** Applies the post-processing quality preset to the game's post-process pass. */
private fun applyPostProcessQuality(qualityName: String?) {
    if (qualityName == null) return
    val quality = com.pafoid.skate.engine.render.renderer.passes.PostProcessQuality.fromGraphicsQuality(qualityName)
    val settings = engine.renderer.renderResources.renderPasses.postProcess.getSettings()
    com.pafoid.skate.engine.render.renderer.passes.PostProcessQualityPreset.apply(quality, settings)
}

/** Game UI text ships only inside game.bin (AST-07); nothing is read from the working directory. */
private fun loadGameStrings() {
    val packed = engine.assetsManager.assetsResolver.readAll(PackedStrings::isTable)
    if (packed.isEmpty()) {
        engine.logger.logGame("game.bin has no string tables; UI text shows its keys", LoggerService.LogLevel.WARN)
        return
    }
    engine.gameStrings.addStrings(PackedStrings.source(packed))
}

/**
 * Presents the scene framebuffer over the whole window. It has the window's framebuffer size ([GameView]); the
 * Window's own width/height are only its initial size and stay 1920x1080 after going fullscreen.
 */
fun testDraw(width: Int, height: Int) {// TODO : is working but needs to move
    if (presentRect.width != width || presentRect.height != height) {
        presentRect = IntRect(0, 0, width, height)
    }
    engine.device.commandList().blit(engine.renderer.frameBuffer.target, null, dstRect = presentRect, filter = Filter.LINEAR)
}

private var presentRect = IntRect(0, 0, 0, 0)

fun readFileHeader(binFile: ByteArray): Pair<Atlas?, Int> {
    val headerSizeData = ByteBuffer.wrap(binFile.copyOfRange(0, Int.SIZE_BYTES))
    val headerSize = headerSizeData.int + Int.SIZE_BYTES
    val rawData = binFile.copyOfRange(Int.SIZE_BYTES, headerSize)
    val data = rawData.toString(Charsets.UTF_8)
    try {
        val parsedData = engine.serializer.decode<Atlas?>(data)
        return Pair(parsedData, headerSize)
    } catch (ex: Exception) {
        engine.logger.logError("Could not read the game.bin header", ex, source = "game")
    }
    return null to 0
}

fun loadProjectAndIcon(binFile: ByteArray, atlas: Atlas, offset: Int): Pair<Project?, ByteArray?> {
    var currentOffset = offset
    var project: Project? = null
    atlas.keys.forEach { key ->
        atlas[key]?.forEach { info ->
            if (key in FileType.PROJECT_FILE.extensions) {
                val data = PackBuilder.extract(binFile, info.position + offset, info.size, info.compressed)
                    .toString(Charsets.UTF_8)
                try {
                    project = engine.serializer.decodeFile<Project?>(FileKind.PROJECT, data)
                } catch (e: Exception) {
                    engine.logger.logError("Could not load the project file from game.bin", e, source = "game")
                }
            }
            currentOffset += info.size
        }
    }

    val icon = project?.iconPath?.let { iconPath ->
        val extension = iconPath.fileExtension()
        val info = atlas.get(extension)?.firstOrNull {
            it.path == iconPath
        }
        info?.let {
            PackBuilder.extract(binFile, info.position + offset, info.size, info.compressed)
        }
    }

    return project to icon
}

/**
 * Applies graphics settings from [gameSettings] to the engine's static settings.
 *
 * This allows the player to override the project's graphics settings via game.settings.json.
 * When a setting is null, the project's value (or engine default) is kept.
 */
private fun applyGameGraphicsSettings(gameSettings: GameSettings, project: Project?) {
    // Project defaults (only when no game.settings.json override exists)
    if (gameSettings.graphicsQuality == null && project != null) {
        val quality = com.pafoid.skate.editor.settings.GraphicsQuality.valueOf(project.graphicsQuality.name)
        com.pafoid.skate.engine.render.shadow.ShadowSettings.quality = quality.shadowQualityLevel
        com.pafoid.skate.engine.render.renderer.passes.PostProcessQualityPreset.apply(
            com.pafoid.skate.engine.render.renderer.passes.PostProcessQuality.fromGraphicsQuality(quality.name),
            engine.renderer.renderResources.renderPasses.postProcess.getSettings()
        )
    }
    if (gameSettings.msaaSamples == null && project != null) {
        com.pafoid.skate.engine.render.MsaaSettings.samples = project.msaaSamples.coerceIn(0, 8)
    }
    if (gameSettings.textureQuality == null && project != null) {
        val level = com.pafoid.skate.engine.render.texture.TextureQualityLevel.fromName(project.textureQuality.name)
        com.pafoid.skate.engine.render.texture.TextureQualitySettings.apply(level, emptyList())
    }

    // Game settings overrides (when explicitly set in game.settings.json)
    gameSettings.msaaSamples?.let {
        com.pafoid.skate.engine.render.MsaaSettings.samples = it.coerceIn(0, 8)
    }
    gameSettings.textureQuality?.let {
        val level = com.pafoid.skate.engine.render.texture.TextureQualityLevel.fromName(it)
        com.pafoid.skate.engine.render.texture.TextureQualitySettings.apply(level, emptyList())
    }
    gameSettings.graphicsQuality?.let {
        val quality = com.pafoid.skate.editor.settings.GraphicsQuality.valueOf(it)
        com.pafoid.skate.engine.render.shadow.ShadowSettings.quality = quality.shadowQualityLevel
        applyPostProcessQuality(it)
    }
}

private val com.pafoid.skate.editor.settings.GraphicsQuality.shadowQualityLevel: com.pafoid.skate.engine.render.shadow.ShadowQualityLevel
    get() = com.pafoid.skate.engine.render.shadow.ShadowQualityLevel.fromName(name)