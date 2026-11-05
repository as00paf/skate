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
import com.pafoid.skate.engine.ui.GameSettingsStore
import com.pafoid.skate.game.ui.GameHudState
import com.pafoid.skate.game.ui.GameUiSystem
import org.lwjgl.glfw.GLFW
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.pack.PackedStrings
import com.pafoid.skate.engine.utils.Atlas
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.IntRect
import java.io.File
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
    val window = Window(title = project.name, icon = icon)

    window.windowController.setFullscreen(true)
    engine.start(window.glfwWindow, window.device)

    if (projectManager.openProject(project, binFile, header, headerSize)) {
        // The project's mixer (bus volumes and mutes set in the editor) applies to the exported game too (EDT-14).
        engine.audioEngine.applyConfig(project.gameplaySettings.audio.toConfig())
        engine.runtimePlaying = true
        installGameUi(window, pack.file.parentFile)
        window.show { dt ->
            // The framebuffer is blitted over the whole window, so UI pointer mapping uses the window's size.
            val w = IntArray(1)
            val h = IntArray(1)
            GLFW.glfwGetWindowSize(window.glfwWindow, w, h)
            engine.inputProvider.mouseListener.setViewRect(ViewRect(0f, 0f, w[0].toFloat(), h[0].toFloat()))
            engine.update(dt)
            testDraw(window)
        }

        engine.destroy()
    }
}

/**
 * HUD plus the engine's pause menu (GUI-09), which behaves as in editor Play; only Restart and Quit are the game's:
 * Restart reloads the scene as it was at launch, Quit closes the window.
 */
private fun installGameUi(window: Window, packDir: File) {
    val scene = engine.sceneManager.currentScene ?: return
    loadGameStrings()
    // The player's language choice is kept next to the game.bin that was loaded (GUI-10), wherever the game was started from.
    engine.gameLocale.attach(GameSettingsStore(GameSettings.fileIn(packDir), engine.logger))
    val launchState = ScenePlaySnapshot.capture(scene, engine.serializer)
    val hud = GameUiSystem(GameHudState())
    engine.systemManager.addSystem(hud)
    engine.pauseActions.restart = {
        engine.sceneManager.currentScene?.let { launchState.restore(engine.sceneManager, engine.serializer, it) }
        hud.resetState()
    }
    engine.pauseActions.quit = { GLFW.glfwSetWindowShouldClose(window.glfwWindow, true) }
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

/** Presents the scene framebuffer, stretched over the whole window. */
fun testDraw(window: Window) {// TODO : is working but needs to move
    if (presentRect.width != window.width || presentRect.height != window.height) {
        presentRect = IntRect(0, 0, window.width, window.height)
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