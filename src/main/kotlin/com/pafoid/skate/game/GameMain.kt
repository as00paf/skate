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

    //if(projectManager.openProject(project)){}

    val window = Window(title = "PAFSK8", windowIcon = Assets.Textures.APP_ICON)// TODO: add app icon to project

    engine.start(window.glfwWindow)

    window.show { dt ->
        engine.update(dt)
    }

    engine.destroy()
}

fun loadBinFile(): ByteArray? {
    val file = File(AssetsPacker.BIN_FILE)
    if (!file.exists()) {
        println("${file.absolutePath} does not exist")
        return null
    }
    val data = file.readBytes()
    return data
}

fun readFileHeader(binFile: ByteArray): Pair<Atlas?, Int> {
    val headerSize = ByteBuffer.wrap(binFile.copyOfRange(0, Long.SIZE_BYTES)).getLong().toInt()
    val rawData = binFile.copyOfRange(Long.SIZE_BYTES, headerSize)
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
                val data = binFile.copyOfRange(currentOffset, end)
                project = engine.serializer.decode<Project?>(data.toString(Charsets.UTF_8))
            }
            currentOffset += end
        }
    }

    return project
}
