package com.pafoid.skate.engine.assets.database.importers

import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.database.AssetMeta
import com.pafoid.skate.engine.assets.database.AssetType
import java.io.File
import java.io.FileNotFoundException

/**
 * Importer for audio assets.
 *
 * Supported formats: wav, ogg, mp3, flac, aiff
 *
 * Currently copies source files to the project cache as-is.
 * Future: sample rate conversion, channel mixing, compression.
 */
class AudioImporter : AssetImporter {

    override val assetType = AssetType.AUDIO
    override val supportedExtensions = setOf("wav", "ogg", "mp3", "flac", "aiff")

    override suspend fun import(
        sourceFile: File,
        meta: AssetMeta,
        projectRoot: File
    ): Result<List<String>> {
        val cacheDir = File(projectRoot, ".cache/audio/${meta.guid}")
        cacheDir.mkdirs()
        val outputPath = ".cache/audio/${meta.guid}/${sourceFile.name}"
        val outputFile = File(projectRoot, outputPath)

        if (!outputFile.exists() || sourceFile.lastModified() > outputFile.lastModified()) {
            sourceFile.copyTo(outputFile, overwrite = true)
        }

        return Result.success(listOf(outputPath))
    }

    override fun loadRuntime(
        guid: AssetGuid,
        meta: AssetMeta,
        projectRoot: File
    ): Result<Any> {
        val sourcePath = File(projectRoot, meta.sourcePath)
        return if (sourcePath.exists()) {
            Result.success(sourcePath.absolutePath)
        } else {
            Result.failure(FileNotFoundException("Audio not found: ${meta.sourcePath}"))
        }
    }
}
