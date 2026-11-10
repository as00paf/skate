package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.BaseSearchProvider
import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.editor.systems.FileSystemScanner
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds asset files of the open project by file name (EDT-61): models, textures, animations, sounds, prefabs and
 * scripts anywhere under [projectDirectory], skipping the folders the Project window skips. Also lists the built-in
 * prefab catalog. Opening a file result asks [reveal] to show it in the Project window.
 *
 * @param projectDirectory Root of the open project; null when no project is open (only the catalog is searched).
 * @param reveal Shows an absolute file path in the Project window.
 */
class AssetSearchProvider(
    private val logger: LoggerService,
    private val projectDirectory: () -> File? = { null },
    private val reveal: (String) -> Unit = {},
) : BaseSearchProvider() {

    override val category: SearchCategory = SearchCategory.ASSET_MODEL

    override val categories: Set<SearchCategory> = setOf(
        SearchCategory.ASSET_MODEL, SearchCategory.ASSET_TEXTURE, SearchCategory.ASSET_ANIMATION,
        SearchCategory.ASSET_SOUND, SearchCategory.ASSET_PREFAB, SearchCategory.ASSET_SCRIPT,
    )

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val files = withContext(Dispatchers.IO) { searchProjectFiles(query) }
        return (files + searchPrefabCatalog(query)).sortedByDescending { it.relevanceScore }
    }

    override fun navigate(result: SearchResult) {
        val path = result.metadata["path"] as? String ?: return
        if (result.metadata["inProject"] == true) {
            reveal(path)
        } else {
            logger.logEditor("Asset selected: $path (${result.metadata["type"]})")
        }
    }

    /** Matches on the file name with and without its extension, so "james" ranks james.glb as a prefix match. */
    internal fun searchProjectFiles(query: String): List<SearchResult> {
        val root = projectDirectory() ?: return emptyList()
        if (!root.isDirectory) return emptyList()
        val results = ArrayList<SearchResult>()
        root.walkTopDown()
            .onEnter { it == root || it.name !in FileSystemScanner.SKIP_NAMES }
            .filter { it.isFile }
            .forEach { file ->
                val kind = kindOf(file) ?: return@forEach
                val score = maxOf(calculateRelevance(file.name, query), calculateRelevance(file.nameWithoutExtension, query))
                if (score > 0f) results += fileResult(file, kind, score)
            }
        return results
    }

    private fun searchPrefabCatalog(query: String): List<SearchResult> = PrefabCatalog.entries.mapNotNull { prefab ->
        val score = calculateRelevance(prefabName(prefab), query)
        if (score > 0f) createPrefabResult(prefab, score) else null
    }

    /** Asset kinds the search finds, by extension (as the Project window resolves them). */
    internal enum class Kind(val type: String, val category: SearchCategory, val subcategory: String, val icon: String, val extensions: List<String>) {
        MODEL("model", SearchCategory.ASSET_MODEL, "Model", Icons.CUBE, FileType.MODEL_3D.extensions),
        TEXTURE("texture", SearchCategory.ASSET_TEXTURE, "Texture", Icons.CAMERA, FileType.TEXTURE.extensions),
        ANIMATION("animation", SearchCategory.ASSET_ANIMATION, "Animation", Icons.PLAY, FileType.ANIMATION.extensions),
        SOUND("sound", SearchCategory.ASSET_SOUND, "Sound", Icons.MUSIC, FileType.SOUND.extensions),
        PREFAB("prefab", SearchCategory.ASSET_PREFAB, "Prefab", Icons.GEAR, FileType.PREFAB.extensions),
        SCRIPT("script", SearchCategory.ASSET_SCRIPT, "Script", Icons.FILE_CODE, FileType.SCRIPT_KOTLIN.extensions + FileType.SCRIPT_JAVA.extensions),
    }

    private fun kindOf(file: File): Kind? {
        val ext = file.extension.lowercase()
        // FBX files can be models or animations depending on content; use the resolver for content-aware classification.
        if (ext == "fbx") {
            val resolved = com.pafoid.skate.editor.systems.FileTypeResolver.resolve(file)
            return when (resolved) {
                FileType.MODEL_3D -> Kind.MODEL
                FileType.ANIMATION -> Kind.ANIMATION
                else -> null
            }
        }
        return Kind.entries.firstOrNull { ext in it.extensions }
    }

    private fun fileResult(file: File, kind: Kind, score: Float): SearchResult {
        val root = projectDirectory()
        val relative = root?.let { file.relativeTo(it).invariantSeparatorsPath } ?: file.path
        return SearchResult(
            id = "asset_${kind.type}_${file.absolutePath}",
            displayName = file.name,
            category = kind.category,
            subcategory = kind.subcategory,
            description = relative,
            icon = kind.icon,
            relevanceScore = score,
            metadata = mapOf(
                "path" to file.absolutePath,
                "type" to kind.type,
                "extension" to file.extension,
                "inProject" to true,
            ),
        )
    }

    /** English name from the type (this provider has no string table): QUARTER_PIPE -> "Quarter Pipe". */
    private fun prefabName(prefab: PrefabEntry) =
        prefab.type.name.split('_').joinToString(" ") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }

    private fun createPrefabResult(prefab: PrefabEntry, score: Float): SearchResult {
        return SearchResult(
            id = "asset_prefab_${prefab.type.name}",
            displayName = prefabName(prefab),
            category = SearchCategory.ASSET_PREFAB,
            subcategory = "Prefab",
            description = prefab.modelPath,
            icon = Icons.GEAR,
            relevanceScore = score,
            metadata = mapOf(
                "path" to prefab.modelPath,
                "type" to "prefab",
                "prefabType" to prefab.type.name
            )
        )
    }
}
