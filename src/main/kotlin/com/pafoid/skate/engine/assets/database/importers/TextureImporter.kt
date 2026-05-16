package com.pafoid.skate.engine.assets.database.importers

import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.database.AssetMeta
import com.pafoid.skate.engine.assets.database.AssetType
import java.io.File
import java.io.FileNotFoundException

/**
 * Importer for texture assets.
 *
 * Supported formats: png, jpg, jpeg, tga, bmp, psd, tif, tiff, hdr, exr, webp
 *
 * Currently copies source files to the project cache as-is.
 * Future: compression, resizing, mipmap generation, format conversion.
 */
class TextureImporter : AssetImporter {

    override val assetType = AssetType.TEXTURE
    override val supportedExtensions = setOf(
        "png", "jpg", "jpeg", "tga", "bmp", "psd", "tif", "tiff", "hdr", "exr", "webp"
    )

    override suspend fun import(
        sourceFile: File,
        meta: AssetMeta,
        projectRoot: File
    ): Result<List<String>> {
        val cacheDir = File(projectRoot, ".cache/textures/${meta.guid}")
        cacheDir.mkdirs()

        val outputName = "${sourceFile.nameWithoutExtension}.png"
        val outputPath = ".cache/textures/${meta.guid}/$outputName"
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
            Result.failure(FileNotFoundException("Texture source not found: ${meta.sourcePath}"))
        }
    }
}
