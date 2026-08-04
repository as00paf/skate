package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.FileTypeResolver
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

typealias AssetType = String
typealias Atlas = LinkedHashMap<AssetType, List<AssetInfo>>

@Serializable
data class AssetInfo(val type: FileType, val path: String, var position: Int = 0, var size: Int = 0)
private val serializer: Serializer = Serializer()

class AssetsPacker(private val logger: LoggerService) {
    var atlas = Atlas()

    fun pack(project: Project): Boolean {
        val outputBuffer = ByteArrayOutputStream()
        atlas = Atlas()

        val projectDir = File(project.projectPath).parentFile
        val excluded = listOf("bin", "jar", "bat")
        val sortedFiles = projectDir.walkTopDown().filter { it.isFile && it.extension !in excluded }.map {
            Pair(AssetInfo(FileTypeResolver.resolve(it), it.absolutePath), it)
        }.sortedBy { it.first.type }

        var currentPosition = 0
        sortedFiles.forEach {
            when (it.first.type) {
                FileType.PROJECT_FILE,
                FileType.SCENE,
                FileType.SCRIPT_KOTLIN,
                FileType.SCRIPT_JAVA,
                FileType.TEXTURE,
                FileType.MODEL_3D,
                FileType.ANIMATION,
                FileType.SOUND,
                FileType.PREFAB,
                FileType.JSON,
                FileType.CONFIG,
                FileType.SHADER,
                FileType.MATERIAL,
                FileType.FONT,
                FileType.TEXT -> {
                    val fileData = it.second.readBytes()
                    val size = fileData.size
                    val updatedInfo = it.first.copy(size = size, position = currentPosition)
                    atlas[it.second.extension] = atlas[it.second.extension].orEmpty().minus(it.first).plus(updatedInfo)
                    outputBuffer.writeBytes(fileData)
                    currentPosition += size
                }
                else -> { /*Skip*/
                }
            }
        }

        val outputDir = File(project.projectPath).parentFile
        val output = File(outputDir, "builds\\${BIN_FILE}")
        val atlasData = serializer.encode(atlas)
        val encodedAtlas = atlasData.toByteArray(Charsets.UTF_8)
        val encodedAtlasSize = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(encodedAtlas.size).array()

        output.writeBytes(encodedAtlasSize)
        output.appendBytes(encodedAtlas)
        output.appendBytes(outputBuffer.toByteArray())

        val atlasTable = "Packed Assets Format: \n" +
                "Atlas size  :\t${encodedAtlas.size}\n" +
                "Atlas       :\t$atlasData\nWritten as bytes\n" +
                "Binary data :\t{${outputBuffer.size()} bytes written}"

        val atlasReport = File(outputDir, "builds\\atlas_report.txt")
        atlasReport.writeText(atlasTable)

        logger.logEngine(atlasTable, LoggerService.LogLevel.INFO)

        return true
    }

    companion object {
        const val BIN_FILE = "game.bin"
    }
}