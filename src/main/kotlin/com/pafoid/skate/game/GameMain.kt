package com.pafoid.skate.game

import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.ScenePlaySnapshot
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.game.player.SkateGameplay
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.fileExtension
import com.pafoid.skate.engine.input.ViewRect
import com.pafoid.skate.game.ui.GameHudState
import com.pafoid.skate.game.ui.GameUiSystem
import org.lwjgl.glfw.GLFW
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.pack.PackedStrings
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.Atlas
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import java.io.File
import java.nio.ByteBuffer

private val engine = Engine()

fun main(args: Array<String>) {
    SkateGameplay.register()
    val binFile = loadBinFile() ?: return
    val (header, headerSize) = readFileHeader(binFile)
    if (header == null || headerSize <= 0) return

    val (project, icon) = loadProjectAndIcon(binFile, header, headerSize)
    if (project == null) return

    val projectManager = ProjectManager(engine)
    val window = Window(title = project.name, icon = icon)

    window.windowController.setFullscreen(true)
    engine.start(window.glfwWindow)

    if (projectManager.openProject(project, binFile, header, headerSize)) {
        engine.runtimePlaying = true
        installGameUi(window)
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
private fun installGameUi(window: Window) {
    val scene = engine.sceneManager.currentScene ?: return
    loadGameStrings()
    val launchState = ScenePlaySnapshot.capture(scene, engine.serializer)
    val hud = GameUiSystem(GameHudState())
    engine.systemManager.addSystem(hud)
    engine.pauseActions.restart = {
        engine.sceneManager.currentScene?.let { launchState.restore(engine.sceneManager, engine.serializer, it) }
        hud.resetState()
    }
    engine.pauseActions.quit = { GLFW.glfwSetWindowShouldClose(window.glfwWindow, true) }
}

/** Game UI text ships inside game.bin (AST-07); packs exported before that fall back to the tables on disk. */
private fun loadGameStrings() {
    val packed = engine.assetsManager.assetsResolver.readAll(PackedStrings::isTable)
    if (packed.isNotEmpty()) {
        engine.stringManager.addStrings(PackedStrings.source(packed))
    } else {
        engine.logger.logEngine("game.bin has no string tables, reading ${Assets.Strings.GAME_STRINGS}")
        engine.stringManager.addStrings(Assets.Strings.GAME_STRINGS)
    }
}

fun testDraw(window: Window) {// TODO : is working but needs to move
    GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, engine.renderer.frameBuffer.fboId)
    GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0)
    GL30.glBlitFramebuffer(
        0, 0, engine.renderer.frameBuffer.width, engine.renderer.frameBuffer.height,
        0, 0, window.width, window.height,
        GL11.GL_COLOR_BUFFER_BIT,
        GL11.GL_LINEAR
    )
}

fun loadBinFile(): ByteArray? {
    // Exported games ship game.bin next to the jar; -Dskate.bin=<path> overrides it.
    val file = System.getProperty("skate.bin")?.let { File(it) } ?: File(AssetsPacker.BIN_FILE)
    if (!file.exists()) {
        println("${file.absolutePath} does not exist")
        return null
    }
    val data = file.readBytes()
    return data
}

fun readFileHeader(binFile: ByteArray): Pair<Atlas?, Int> {
    val headerSizeData = ByteBuffer.wrap(binFile.copyOfRange(0, Int.SIZE_BYTES))
    val headerSize = headerSizeData.int + Int.SIZE_BYTES
    val rawData = binFile.copyOfRange(Int.SIZE_BYTES, headerSize)
    val data = rawData.toString(Charsets.UTF_8)
    try {
        val parsedData = engine.serializer.decode<Atlas?>(data)
        return Pair(parsedData, headerSize)
    } catch (ex: Exception) {
        engine.logger.logEngine("Could not load header file: $ex")
        ex.printStackTrace()
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
                    engine.logger.logEngine("Error loading project file: $e")
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