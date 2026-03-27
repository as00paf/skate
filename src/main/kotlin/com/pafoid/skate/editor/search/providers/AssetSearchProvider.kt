package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.BaseSearchProvider
import com.pafoid.skate.editor.search.SearchCategory
import com.pafoid.skate.editor.search.SearchResult
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.utils.JobSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Search provider for all asset types in the project.
 *
 * This provider searches across textures, models, animations, sounds, and prefabs.
 * It performs case-insensitive substring matching on filenames and supports fuzzy matching
 * for abbreviated queries. Results include file paths and asset type information.
 *
 * Search features:
 * - Case-insensitive substring matching on filenames
 * - Fuzzy matching for abbreviated queries (e.g., "asph" matches "asphalt.png")
 * - File path in description for context
 * - Asset type in subcategory (Texture, Model, Animation, Sound, Prefab)
 * - Type-specific icons for visual identification
 *
 * Navigation:
 * - Opens AssetBrowserWindow
 * - Future enhancement: Switch to the appropriate tab and highlight the asset
 */
class AssetSearchProvider : BaseSearchProvider(), KoinComponent {

    private val resourceManager: ResourceManager by inject()
    private val imGuiLayer: ImGuiLayer by inject()
    private val logger: LoggerService by inject()

    override val category: SearchCategory = SearchCategory.ASSET_MODEL

    private val textureExtensions = listOf("png", "jpg", "jpeg")
    private val modelExtensions = listOf("obj", "glb", "gltf", "fbx")
    private val animationExtensions = listOf("fbx")
    private val soundExtensions = listOf("wav", "ogg")

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        return withContext(Dispatchers.Default) {
            val results = mutableListOf<SearchResult>()

            results.addAll(searchTextures(query))
            results.addAll(searchModels(query))
            results.addAll(searchAnimations(query))
            results.addAll(searchSounds(query))
            results.addAll(searchPrefabs(query))

            results.sortedByDescending { it.relevanceScore }
        }
    }

    override fun navigate(result: SearchResult) {
        val assetPath = result.metadata["path"] as? String ?: return
        val assetType = result.metadata["type"] as? String ?: return

        // AssetBrowser is always rendered in ImGuiLayer
        // Future enhancement: switch to appropriate tab and highlight the asset
        logger.logEditor("Asset selected: $assetPath ($assetType)")
    }

    private suspend fun searchTextures(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        val texturesDir = File(Assets.Folders.TEXTURES)

        if (texturesDir.exists()) {
            texturesDir.walkTopDown()
                .filter { it.isFile && textureExtensions.contains(it.extension.lowercase()) }
                .forEach { file ->
                    val score = calculateRelevance(file.name, query)
                    if (score > 0.0f) {
                        results.add(createTextureResult(file, score))
                    }
                }
        }

        results
    }

    private suspend fun searchModels(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        val modelsDir = File("assets/obj")

        if (modelsDir.exists()) {
            modelsDir.walkTopDown()
                .filter { it.isFile && modelExtensions.contains(it.extension.lowercase()) }
                .forEach { file ->
                    val score = calculateRelevance(file.name, query)
                    if (score > 0.0f) {
                        results.add(createModelResult(file, score))
                    }
                }
        }

        results
    }

    private suspend fun searchAnimations(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        val animationsDir = File(Assets.Folders.ANIMATIONS)

        if (animationsDir.exists()) {
            animationsDir.walkTopDown()
                .filter { it.isFile && animationExtensions.contains(it.extension.lowercase()) }
                .forEach { file ->
                    val score = calculateRelevance(file.name, query)
                    if (score > 0.0f) {
                        results.add(createAnimationResult(file, score))
                    }
                }
        }

        results
    }

    private suspend fun searchSounds(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        val soundsDir = File("assets/sounds")

        if (soundsDir.exists()) {
            soundsDir.walkTopDown()
                .filter { it.isFile && soundExtensions.contains(it.extension.lowercase()) }
                .forEach { file ->
                    val score = calculateRelevance(file.name, query)
                    if (score > 0.0f) {
                        results.add(createSoundResult(file, score))
                    }
                }
        }

        results
    }

    private suspend fun searchPrefabs(query: String): List<SearchResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<SearchResult>()

        val prefabConfigs = listOf(
            PrefabInfo("Skateboard", PrefabType.SKATEBOARD, Assets.Models.SKATEBOARD_GLB),
            PrefabInfo("Rail", PrefabType.RAIL, Assets.Models.RAIL),
            PrefabInfo("Ledge", PrefabType.LEDGE, Assets.Models.LEDGE),
            PrefabInfo("Kicker", PrefabType.KICKER, Assets.Models.KICKER),
            PrefabInfo("Manual Pad", PrefabType.MANUAL_PAD, Assets.Models.MANUAL_PAD),
            PrefabInfo("Bank", PrefabType.BANK, Assets.Models.BANK),
            PrefabInfo("Quarter Pipe", PrefabType.QUARTER_PIPE, Assets.Models.QUARTER_PIPE),
        )

        prefabConfigs.forEach { config ->
            val score = calculateRelevance(config.name, query)
            if (score > 0.0f) {
                results.add(createPrefabResult(config, score))
            }
        }

        results
    }

    private fun createTextureResult(file: File, score: Float): SearchResult {
        return SearchResult(
            id = "asset_texture_${file.absolutePath}",
            displayName = file.name,
            category = SearchCategory.ASSET_TEXTURE,
            subcategory = "Texture",
            description = file.absolutePath,
            icon = Icons.CAMERA,
            relevanceScore = score,
            metadata = mapOf(
                "path" to file.absolutePath,
                "type" to "texture",
                "extension" to file.extension
            )
        )
    }

    private fun createModelResult(file: File, score: Float): SearchResult {
        return SearchResult(
            id = "asset_model_${file.absolutePath}",
            displayName = file.name,
            category = SearchCategory.ASSET_MODEL,
            subcategory = "Model",
            description = file.absolutePath,
            icon = Icons.CUBE,
            relevanceScore = score,
            metadata = mapOf(
                "path" to file.absolutePath,
                "type" to "model",
                "extension" to file.extension
            )
        )
    }

    private fun createAnimationResult(file: File, score: Float): SearchResult {
        return SearchResult(
            id = "asset_animation_${file.absolutePath}",
            displayName = file.name,
            category = SearchCategory.ASSET_ANIMATION,
            subcategory = "Animation",
            description = file.absolutePath,
            icon = Icons.PLAY,
            relevanceScore = score,
            metadata = mapOf(
                "path" to file.absolutePath,
                "type" to "animation",
                "extension" to file.extension
            )
        )
    }

    private fun createSoundResult(file: File, score: Float): SearchResult {
        return SearchResult(
            id = "asset_sound_${file.absolutePath}",
            displayName = file.name,
            category = SearchCategory.ASSET_SOUND,
            subcategory = "Sound",
            description = file.absolutePath,
            icon = Icons.MUSIC,
            relevanceScore = score,
            metadata = mapOf(
                "path" to file.absolutePath,
                "type" to "sound",
                "extension" to file.extension
            )
        )
    }

    private fun createPrefabResult(config: PrefabInfo, score: Float): SearchResult {
        return SearchResult(
            id = "asset_prefab_${config.name}",
            displayName = config.name,
            category = SearchCategory.ASSET_PREFAB,
            subcategory = "Prefab",
            description = config.modelPath,
            icon = Icons.GEAR,
            relevanceScore = score,
            metadata = mapOf(
                "path" to config.modelPath,
                "type" to "prefab",
                "prefabType" to config.type.name
            )
        )
    }

    private data class PrefabInfo(
        val name: String,
        val type: PrefabType,
        val modelPath: String
    )

    private enum class PrefabType {
        SKATEBOARD,
        SKATER,
        RAIL,
        LEDGE,
        KICKER,
        MANUAL_PAD,
        BANK,
        QUARTER_PIPE
    }
}
