package com.pafoid.skate.engine.assets.database.importers

import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.database.AssetMeta
import com.pafoid.skate.engine.assets.database.AssetType
import java.io.File
import java.io.FileNotFoundException

/**
 * Importer for shader assets.
 *
 * Supported formats: glsl, vert, frag, comp
 *
 * Shaders are loaded directly from source — no pre-processing needed.
 * Future: shader compilation, optimization, variant generation.
 */
class ShaderImporter : AssetImporter {

    override val assetType = AssetType.SHADER
    override val supportedExtensions = setOf("glsl", "vert", "frag", "comp")

    override suspend fun import(
        sourceFile: File,
        meta: AssetMeta,
        projectRoot: File
    ): Result<List<String>> {
        // Shaders are loaded directly from source — no pre-processing needed
        return Result.success(listOf(meta.sourcePath))
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
            Result.failure(FileNotFoundException("Shader not found: ${meta.sourcePath}"))
        }
    }
}
