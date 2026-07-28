package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.FileTypeResolver
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import java.io.ByteArrayOutputStream
import java.io.File

typealias AssetType = String
typealias AssetSize = Int
typealias AssetSizes = List<AssetSize>
typealias Atlas = LinkedHashMap<AssetType, AssetSizes>

private val serializer: Serializer = Serializer()

class AssetsPacker(private val logger: LoggerService) {
    var atlas = Atlas()

    fun pack(project: Project): Boolean {
        val outputBuffer = ByteArrayOutputStream()
        atlas = Atlas()

        val projectDir = File(project.projectPath).parentFile
        val sortedFiles = projectDir.walkTopDown().filter { it.isFile }.map {
            Pair(FileTypeResolver.resolve(it), it)
        }.sortedBy { it.first }

        sortedFiles.forEach {
            when (it.first) {
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
                FileType.TEXT -> {
                    val data = it.second.readBytes()
                    atlas[it.second.extension] = atlas[it.second.extension].orEmpty().plus(data.size)
                    outputBuffer.writeBytes(data)
                }

                else -> { /*Skip*/
                }
            }
        }

        val outputDir = File(project.projectPath).parentFile
        val output = File(outputDir, "builds\\${BIN_FILE}")
        val encodedAtlas = serializer.encode(atlas).toByteArray(Charsets.UTF_8)
        val encodedAtlasSize = encodedAtlas.size.toLong()

        output.writeBytes(byteArrayOf(encodedAtlasSize.toByte()))
        output.writeBytes(encodedAtlas)
        output.writeBytes(outputBuffer.toByteArray())

        return true
    }

    companion object {
        const val BIN_FILE = "game.bin"
    }
}