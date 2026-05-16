package com.pafoid.skate.engine.assets.database.importers

import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.database.AssetMeta
import com.pafoid.skate.engine.assets.database.AssetType
import java.io.File
import java.io.FileNotFoundException

/**
 * Importer for 3D model assets.
 *
 * Supported formats: glb, gltf, obj, fbx, dae, blend
 *
 * For glb/gltf: copies as-is (already runtime-ready).
 * For other formats: copies to cache (future: convert to glb via Assimp export).
 */
class ModelImporter : AssetImporter {

    override val assetType = AssetType.MODEL
    override val supportedExtensions = setOf("glb", "gltf", "obj", "fbx", "dae", "blend")

    override suspend fun import(
        sourceFile: File,
        meta: AssetMeta,
        projectRoot: File
    ): Result<List<String>> {
        val cacheDir = File(projectRoot, ".cache/models/${meta.guid}")
        cacheDir.mkdirs()

        val outputName = when (sourceFile.extension.lowercase()) {
            "glb", "gltf" -> sourceFile.name
            else -> "${sourceFile.nameWithoutExtension}.glb"
        }
        val outputPath = ".cache/models/${meta.guid}/$outputName"
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
        val cacheFile = if (meta.generatedOutputPaths.isNotEmpty()) {
            File(projectRoot, meta.generatedOutputPaths.first())
        } else {
            File(projectRoot, meta.sourcePath)
        }
        return if (cacheFile.exists()) {
            Result.success(cacheFile.absolutePath)
        } else {
            Result.failure(FileNotFoundException("Model not found: ${cacheFile.path}"))
        }
    }
}
