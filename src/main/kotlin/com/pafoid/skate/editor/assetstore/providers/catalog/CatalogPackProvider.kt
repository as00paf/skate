package com.pafoid.skate.editor.assetstore.providers.catalog

import com.pafoid.skate.editor.assetstore.AssetStoreProvider
import com.pafoid.skate.editor.assetstore.Author
import com.pafoid.skate.editor.assetstore.DownloadPlan
import com.pafoid.skate.editor.assetstore.ImportEntry
import com.pafoid.skate.editor.assetstore.ImportPlan
import com.pafoid.skate.editor.assetstore.LicenseCatalog
import com.pafoid.skate.editor.assetstore.SourceFormat
import com.pafoid.skate.editor.assetstore.StoreAssetDetails
import com.pafoid.skate.editor.assetstore.StoreAssetKind
import com.pafoid.skate.editor.assetstore.StoreAssetRef
import com.pafoid.skate.editor.assetstore.StoreAssetSummary
import com.pafoid.skate.editor.assetstore.StoreFormats
import com.pafoid.skate.editor.assetstore.StorePage
import com.pafoid.skate.editor.assetstore.StorePaths
import com.pafoid.skate.editor.assetstore.StoreQuery
import com.pafoid.skate.editor.assetstore.StoreVariant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A curated pack: one page on the creator's site, downloaded by hand. */
@Serializable
data class CatalogPack(
    val id: String,
    val title: String,
    val pageUrl: String,
    val tags: List<String> = emptyList(),
    /** Formats the pack page lists (curation note only; the import decides from the zip's contents). */
    val formats: List<String> = emptyList(),
    /** Extra zip file name stems that identify this pack. */
    val zipNames: List<String> = emptyList(),
)

/** `src/main/resources/assetstore/<provider>.catalog.json`. */
@Serializable
data class PackCatalog(
    val schema: Int = 1,
    val provider: String,
    val author: String,
    val authorUrl: String,
    val license: String,
    /** When a human last checked the pages (no runtime scraping, AST-16 §1). */
    val curated: String,
    val packs: List<CatalogPack>,
)

/**
 * Kenney / Quaternius (AST-16 §5.4–5.5): libraries without an API, so no download or scraping. The editor ships a
 * curated catalog of pack pages; "Open on website" leads to the page, the user downloads the zip, and
 * "Import downloaded pack…" imports it. [importPlan] picks the glTF/GLB models of the pack (with their `.bin` and
 * images, keeping the layout so relative URIs resolve), else its OBJ models (with `.mtl` and images). FBX/Blend-only
 * packs produce an empty plan.
 */
class CatalogPackProvider(
    catalogJson: String,
    override val displayNameKey: String,
    override val poweredByKey: String?,
) : AssetStoreProvider {
    val catalog: PackCatalog = json.decodeFromString(PackCatalog.serializer(), catalogJson)

    override val id: String = catalog.provider
    override val kinds = setOf(StoreAssetKind.MODEL)
    override val manualDownload = true

    private val license = LicenseCatalog.lookup(catalog.license)

    init {
        require(StorePaths.isSafeSegment(id)) { "Unsafe provider id ${catalog.provider}" }
        catalog.packs.forEach { require(StorePaths.isSafeSegment(it.id)) { "Unsafe pack id ${it.id}" } }
        require(catalog.packs.map { it.id }.toSet().size == catalog.packs.size) { "Duplicate pack id in $id catalog" }
    }

    override fun search(query: StoreQuery): StorePage {
        if (query.kind !in kinds || !query.licenseFilter.accepts(license)) return StorePage(emptyList(), null, 0)
        val words = query.text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val hits = catalog.packs.filter { pack ->
            val haystack = (listOf(pack.id, pack.title) + pack.tags).joinToString(" ").lowercase()
            words.all { it in haystack }
        }
        val offset = query.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val end = minOf(hits.size, offset + query.pageSize)
        val items = if (offset >= end) emptyList() else hits.subList(offset, end).map(::summary)
        return StorePage(items, end.takeIf { it < hits.size }?.toString(), hits.size)
    }

    override fun details(ref: StoreAssetRef): StoreAssetDetails {
        val pack = catalog.packs.firstOrNull { it.id == ref.sourceId } ?: throw NoSuchElementException("No pack ${ref.sourceId} in $id catalog")
        return StoreAssetDetails(
            summary = summary(pack),
            description = null,
            sourceUrl = pack.pageUrl,
            authors = listOf(Author(catalog.author, catalog.authorUrl)),
            license = license,
            variants = listOf(StoreVariant(PACK_VARIANT, SourceFormat.ZIP_PACK, null, null)),
            tags = pack.tags + pack.formats.map { "format:$it" },
        )
    }

    override fun resolveDownload(details: StoreAssetDetails, variant: StoreVariant): DownloadPlan =
        throw UnsupportedOperationException("$id packs are downloaded by hand from ${details.sourceUrl}")

    /**
     * The pack whose id, title or [CatalogPack.zipNames] best matches [fileName] (letters and digits only, version
     * suffixes ignored); the longest match wins so `city-kit` does not shadow `city-kit-roads`.
     */
    override fun matchPack(fileName: String): StoreAssetRef? {
        val stem = normalize(fileName.substringAfterLast('/').substringAfterLast('\\').removeSuffix(".zip").removeSuffix(".ZIP"))
        if (stem.isEmpty()) return null
        return catalog.packs
            .flatMap { pack -> (listOf(pack.id, pack.title) + pack.zipNames).map { pack to normalize(it) } }
            .filter { (_, key) -> key.length >= 3 && key in stem }
            .maxByOrNull { (_, key) -> key.length }
            ?.let { (pack, _) -> StoreAssetRef(id, pack.id) }
    }

    override fun importPlan(details: StoreAssetDetails, variant: StoreVariant, staged: List<String>): ImportPlan {
        val models = staged.filter { StoreFormats.extension(it) in StoreFormats.MODELS_GLTF }
            .ifEmpty { staged.filter { StoreFormats.extension(it) in StoreFormats.MODELS_OBJ } }
        if (models.isEmpty()) return ImportPlan(emptyList())
        val companions = if (StoreFormats.extension(models.first()) in StoreFormats.MODELS_GLTF) GLTF_COMPANIONS else OBJ_COMPANIONS
        val modelDirs = models.map { dirOf(it) }.toSet()
        val picked = staged.filter { path ->
            path in models || (StoreFormats.extension(path) in companions && modelDirs.any { dir -> dir.isEmpty() || path.startsWith("$dir/") })
        }
        val root = commonDir(picked)
        return ImportPlan(picked.map { ImportEntry(it, if (root.isEmpty()) it else it.removePrefix("$root/")) })
    }

    private fun summary(pack: CatalogPack) = StoreAssetSummary(
        ref = StoreAssetRef(id, pack.id),
        title = pack.title,
        kind = StoreAssetKind.MODEL,
        thumbnailUrl = null,
        license = license,
        authors = listOf(catalog.author),
    )

    companion object {
        const val PACK_VARIANT = "pack"
        /** Entries per pack zip: Kenney kits ship several formats of 100+ models each. */
        const val PACK_MAX_ENTRIES = 20_000

        private val GLTF_COMPANIONS = setOf("bin", "png", "jpg", "jpeg")
        private val OBJ_COMPANIONS = setOf("mtl", "png", "jpg", "jpeg")
        private val json = Json { ignoreUnknownKeys = true }

        /** Lowercase letters and digits, with a trailing version (`_2.1`, ` v1.0`, `(1)`) removed. */
        fun normalize(name: String): String =
            name.lowercase()
                .replace(Regex("""[\s_-]*(v?\d+(\.\d+)+|\(\d+\))$"""), "")
                .replace(Regex("[^a-z0-9]"), "")

        fun load(resource: String, displayNameKey: String, poweredByKey: String?): CatalogPackProvider {
            val text = CatalogPackProvider::class.java.getResource(resource)?.readText()
                ?: throw IllegalStateException("Missing catalog resource $resource")
            return CatalogPackProvider(text, displayNameKey, poweredByKey)
        }

        private fun dirOf(path: String) = path.substringBeforeLast('/', "")

        /** Longest folder prefix shared by every path ("" when they differ at the top). */
        private fun commonDir(paths: List<String>): String {
            if (paths.isEmpty()) return ""
            var dirs = dirOf(paths.first()).split('/').filter { it.isNotEmpty() }
            for (p in paths.drop(1)) {
                val other = dirOf(p).split('/')
                dirs = dirs.zip(other).takeWhile { (a, b) -> a == b }.map { it.first }
            }
            return dirs.joinToString("/")
        }
    }
}
