package com.pafoid.skate.game

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.Atlas
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import java.io.File
import java.nio.ByteBuffer

val engine = Engine()

fun main(args: Array<String>) {
    val binFile = loadBinFile() ?: return
    val (header, headerSize) = readFileHeader(binFile)
    if (header == null || headerSize <= 0) return

    val project = loadProject(binFile, header, headerSize) ?: return
    val stringManager = StringManager(engine.logger)
    val settingsManager = SettingsManager(engine.serializer, engine.logger, stringManager)
    val projectManager = ProjectManager(engine, settingsManager)

    val window = Window(title = project.name, windowIcon = Assets.Textures.APP_ICON)// TODO: add app icon to project
    window.windowController.setFullscreen(true)
    engine.start(window.glfwWindow)

    if (projectManager.openProject(project)) {
        engine.runtimePlaying = true
        window.show { dt ->
            engine.update(dt)
            testDraw(window)
        }

        engine.destroy()
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
    val file = File("C:\\workspace\\skate_workspace\\test\\Builds", AssetsPacker.BIN_FILE)// TODO: remove test
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
    val parsedData = engine.serializer.decode<Atlas?>(data)

    return Pair(parsedData, headerSize)
}

fun loadProject(binFile: ByteArray, atlas: Atlas, offset: Int): Project? {
    var currentOffset = offset
    var project: Project? = null
    atlas.keys.forEach { key ->
        atlas[key]?.forEach { end ->
            // Skip everything but project for now
            if (key == FileType.PROJECT_FILE.extensions[0]) {
                val data = binFile.copyOfRange(currentOffset, currentOffset + end).toString(Charsets.UTF_8)
                project = engine.serializer.decode<Project?>(data)
            }
            currentOffset += end
        }
    }

    return project
}
