package com.pafoid.skate.game

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.fileExtension
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
                val start = info.position + offset
                val end = start + info.size
                val data = binFile.copyOfRange(start, end).toString(Charsets.UTF_8)
                try {
                    project = engine.serializer.decode<Project?>(data)
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
            val start = info.position + offset
            val end = start + info.size
            binFile.copyOfRange(start, end)
        }
    }

    return project to icon
}