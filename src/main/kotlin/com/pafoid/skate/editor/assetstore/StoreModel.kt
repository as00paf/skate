package com.pafoid.skate.editor.assetstore

/** What a store item becomes once imported (AST-16 §3). */
enum class StoreAssetKind { MODEL, TEXTURE_SET, HDRI }

/** Source file format of a variant. Only formats in [StoreFormats.IMPORTABLE] reach the project in v1 (§7.1). */
enum class SourceFormat { GLTF, GLB, FBX, OBJ, USD, BLEND, TEXTURE_SET, ZIP_PACK }

/** Stable reference to one provider item; [revision] pins a version where the provider has one (Hugging Face commit). */
data class StoreAssetRef(val providerId: String, val sourceId: String, val revision: String? = null)

data class StoreQuery(
    val text: String = "",
    val kind: StoreAssetKind = StoreAssetKind.MODEL,
    val licenseFilter: LicenseFilter = LicenseFilter.COMMERCIAL_OK,
    /** Opaque, from [StorePage.nextCursor]; null for the first page. */
    val cursor: String? = null,
    val pageSize: Int = 40,
)

data class StorePage(val items: List<StoreAssetSummary>, val nextCursor: String?, val total: Int?)

data class StoreAssetSummary(
    val ref: StoreAssetRef,
    val title: String,
    val kind: StoreAssetKind,
    val thumbnailUrl: String?,
    val license: LicenseInfo?,
    val authors: List<String> = emptyList(),
)

data class Author(val name: String, val url: String? = null)

data class StoreAssetDetails(
    val summary: StoreAssetSummary,
    val description: String?,
    val sourceUrl: String,
    val authors: List<Author>,
    val license: LicenseInfo,
    val variants: List<StoreVariant>,
    val tags: List<String> = emptyList(),
)

/** One downloadable form of an item, e.g. glTF at 2k. [sizeBytes] is the main file plus its includes when known. */
data class StoreVariant(val id: String, val format: SourceFormat, val resolution: String?, val sizeBytes: Long?)

/** One file to fetch into staging; [relativePath] is sanitised by [StorePaths.safeRelative] before use. */
data class DownloadItem(val url: String, val relativePath: String, val sizeBytes: Long? = null, val md5: String? = null)

/** Fresh download URLs for one variant; resolved right before downloading, never cached. */
data class DownloadPlan(val items: List<DownloadItem>, val headers: Map<String, String> = emptyMap())

/** Staged file at [stagedPath] (relative to the staging folder) to copy to [targetPath] (relative to the import folder). */
data class ImportEntry(val stagedPath: String, val targetPath: String)

/** A `.skmat` to generate next to the imported textures: material slot name to [ImportEntry.targetPath]. */
data class MaterialSpec(val name: String, val textures: Map<String, String>)

data class ImportPlan(val entries: List<ImportEntry>, val material: MaterialSpec? = null)

/**
 * Formats the v1 store may import (AST-16 §7.1): glTF/GLB (+ `.bin`), OBJ (+ `.mtl`, a model for `AssetMeta` and
 * `FileType`) and images. FBX is classed as an animation by the editor today, so it is not imported.
 */
object StoreFormats {
    val MODELS_GLTF = setOf("gltf", "glb")
    val MODELS_OBJ = setOf("obj")
    val IMPORTABLE = setOf("gltf", "glb", "bin", "obj", "mtl", "png", "jpg", "jpeg", "hdr")

    fun extension(path: String): String = path.substringAfterLast('.', "").lowercase()

    fun isImportable(path: String): Boolean = extension(path) in IMPORTABLE
}
