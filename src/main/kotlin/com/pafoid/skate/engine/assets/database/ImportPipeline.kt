package com.pafoid.skate.engine.assets.database

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.database.importers.AssetImporter
import com.pafoid.skate.engine.assets.database.importers.AudioImporter
import com.pafoid.skate.engine.assets.database.importers.ModelImporter
import com.pafoid.skate.engine.assets.database.importers.ShaderImporter
import com.pafoid.skate.engine.assets.database.importers.TextureImporter
import java.io.File

/**
 * Orchestrates the asset import pipeline.
 *
 * Maintains a registry of importers by asset type and routes import
 * requests to the correct importer.
 */
class ImportPipeline(
    private val logger: LoggerService
) {
    private val importers = mutableMapOf<AssetType, AssetImporter>()

    /**
     * Register an importer for a specific asset type.
     */
    fun registerImporter(importer: AssetImporter) {
        importers[importer.assetType] = importer
    }

    /**
     * Get the importer for a specific asset type.
     */
    fun getImporter(type: AssetType): AssetImporter? = importers[type]

    /**
     * Get the importer that supports a given file extension.
     */
    fun getImporterForExtension(ext: String): AssetImporter? =
        importers.values.find { importer ->
            importer.supportedExtensions.contains(ext.lowercase())
        }

    /**
     * Import a source file using the appropriate importer.
     *
     * @param sourceFile The source file to import
     * @param meta The asset metadata
     * @param projectRoot The project root directory
     * @return Result with list of generated output paths
     */
    suspend fun import(
        sourceFile: File,
        meta: AssetMeta,
        projectRoot: File
    ): Result<List<String>> {
        val type = AssetType.fromExtension(sourceFile.extension)
        val importer = importers[type]
            ?: return Result.failure(IllegalStateException("No importer registered for type: $type"))

        return importer.import(sourceFile, meta, projectRoot)
            .onSuccess { outputPaths ->
                logger.logEngine("Imported ${sourceFile.name} → $outputPaths", LogLevel.INFO)
            }
            .onFailure { err ->
                logger.logEngine("Import failed for ${sourceFile.name}: ${err.message}", LogLevel.ERROR)
            }
    }

    /**
     * Register all standard importers.
     * Convenience method for Koin initialization.
     */
    fun registerStandardImporters() {
        registerImporter(TextureImporter())
        registerImporter(ModelImporter())
        registerImporter(AudioImporter())
        registerImporter(ShaderImporter())
    }
}
