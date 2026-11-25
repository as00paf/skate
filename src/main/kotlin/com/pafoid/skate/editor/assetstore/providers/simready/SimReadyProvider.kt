package com.pafoid.skate.editor.assetstore.providers.simready

import com.pafoid.skate.editor.assetstore.AssetStoreProvider
import com.pafoid.skate.editor.assetstore.Author
import com.pafoid.skate.editor.assetstore.CancelToken
import com.pafoid.skate.editor.assetstore.DownloadItem
import com.pafoid.skate.editor.assetstore.DownloadPlan
import com.pafoid.skate.editor.assetstore.ImportEntry
import com.pafoid.skate.editor.assetstore.ImportPlan
import com.pafoid.skate.editor.assetstore.LicenseCatalog
import com.pafoid.skate.editor.assetstore.LicenseInfo
import com.pafoid.skate.editor.assetstore.PreparedFiles
import com.pafoid.skate.editor.assetstore.SourceFormat
import com.pafoid.skate.editor.assetstore.StoreAssetDetails
import com.pafoid.skate.editor.assetstore.StoreAssetKind
import com.pafoid.skate.editor.assetstore.StoreAssetRef
import com.pafoid.skate.editor.assetstore.StoreAssetSummary
import com.pafoid.skate.editor.assetstore.StoreCancelledException
import com.pafoid.skate.editor.assetstore.StoreFormats
import com.pafoid.skate.editor.assetstore.StoreHttp
import com.pafoid.skate.editor.assetstore.StoreHttpException
import com.pafoid.skate.editor.assetstore.StorePage
import com.pafoid.skate.editor.assetstore.StorePaths
import com.pafoid.skate.editor.assetstore.StoreQuery
import com.pafoid.skate.editor.assetstore.StoreVariant
import com.pafoid.skate.editor.assetstore.convert.BlenderUsdConverter
import com.pafoid.skate.editor.assetstore.convert.ConversionResult
import com.pafoid.skate.editor.assetstore.convert.UsdConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * NVIDIA SimReady assets on the Hugging Face Hub (AST-16 §5.6), converted from OpenUSD to glTF by [converter] at import
 * (§8). No login: public datasets through the standard Hub endpoints.
 *
 * Layout (checked on the live `nvidia/simready-assets` on 2026-11-25): one top-level folder per package, holding
 * `.metadata/com.nvidia.simready.root_usds.json` (the root USD files), `com.nvidia.simready.packaging.json` (with the
 * package `license`), `simready_usd/<root>.usd`, its `payloads/` layers and `Textures/`, and Omniverse `.thumbs/`.
 * Packages are self-contained, so the whole folder is downloaded, minus thumbnails, metadata and the duplicate
 * `source_assets/` images (materials reference `Textures/`). Datasets with another layout are not supported in v1.
 *
 * Search filters the cached package list of each repo (one Hub tree listing at the pinned commit). The commit SHA is
 * the item [StoreAssetRef.revision], so downloads are reproducible.
 */
class SimReadyProvider(
    private val http: StoreHttp,
    private val converter: UsdConverter?,
    private val repos: List<String> = DEFAULT_REPOS,
    private val hub: String = HUB,
) : AssetStoreProvider {
    override val id = ID
    override val displayNameKey = "assetstore.provider.simready"
    override val kinds = setOf(StoreAssetKind.MODEL)
    override val poweredByKey = "assetstore.provider.simready.powered_by"

    /** A dataset at the commit we read it at, with its card license. */
    data class Repo(val name: String, val sha: String, val license: LicenseInfo)

    /** One file of a Hub tree listing. */
    data class HubFile(val path: String, val size: Long)

    private val repoInfo = ConcurrentHashMap<String, Repo>()
    private val packages = ConcurrentHashMap<String, List<String>>()

    override fun search(query: StoreQuery): StorePage {
        val tokens = query.text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val failures = ArrayList<Throwable>()
        val matches = repos.flatMap { name ->
            val repo = runCatching { repo(name) }.getOrElse { failures += it; return@flatMap emptyList() }
            if (!query.licenseFilter.accepts(repo.license)) return@flatMap emptyList()
            val list = runCatching { packages(repo) }.getOrElse { failures += it; return@flatMap emptyList() }
            list.filter { pkg -> tokens.all { it in searchText(pkg) } }.map { repo to it }
        }
        if (failures.size == repos.size && repos.isNotEmpty()) throw failures.first()
        val offset = query.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val end = minOf(matches.size, offset + query.pageSize)
        val items = if (offset >= end) emptyList() else matches.subList(offset, end).map { (repo, pkg) -> summary(repo, pkg, repo.license) }
        return StorePage(items, end.takeIf { it < matches.size }?.toString(), matches.size)
    }

    override fun details(ref: StoreAssetRef): StoreAssetDetails {
        val (repoName, pkg) = split(ref.sourceId)
        val repo = repo(repoName)
        val pinned = repo.copy(sha = ref.revision ?: repo.sha)
        val files = tree(pinned, pkg)
        val relative = files.map { it.path.removePrefix("$pkg/") }
        val roots = fetchText(resolveUrl(pinned, "$pkg/$ROOT_USDS"))
        val root = rootUsd(roots, relative) ?: throw IOException("No root USD file in $pkg")
        val license = fetchText(resolveUrl(pinned, "$pkg/$PACKAGING"))?.let(::packageLicense) ?: repo.license
        val size = files.filter { isPackageFile(it.path.removePrefix("$pkg/")) }.sumOf { it.size }
        val summary = summary(pinned, pkg, license).let { base ->
            thumbnail(root, relative)?.let { base.copy(thumbnailUrl = resolveUrl(pinned, "$pkg/$it")) } ?: base
        }
        return StoreAssetDetails(
            summary = summary,
            description = "${repo.name} · $root",
            sourceUrl = "$hub/datasets/${repo.name}/tree/${pinned.sha}/${encodePath(pkg)}",
            authors = listOf(Author(repo.name.substringBefore('/').uppercase(), "$hub/datasets/${repo.name}")),
            license = license,
            variants = listOf(StoreVariant(VARIANT_ID, SourceFormat.USD, null, size)),
            tags = listOf(repo.name),
            blocker = if (converter?.isAvailable() == true) null else BlenderUsdConverter.NEEDS_BLENDER,
        )
    }

    override fun resolveDownload(details: StoreAssetDetails, variant: StoreVariant): DownloadPlan {
        if (converter?.isAvailable() != true) throw IOException(BlenderUsdConverter.NEEDS_BLENDER)
        val ref = details.summary.ref
        val (repoName, pkg) = split(ref.sourceId)
        val repo = repo(repoName).let { it.copy(sha = ref.revision ?: it.sha) }
        val items = tree(repo, pkg).mapNotNull { file ->
            val relative = file.path.removePrefix("$pkg/")
            if (!isPackageFile(relative)) null else DownloadItem(resolveUrl(repo, file.path), relative, file.size)
        }
        if (items.none { UsdConverter.isUsd(it.relativePath) }) throw IOException("No USD files in $pkg")
        return DownloadPlan(items)
    }

    override fun prepare(details: StoreAssetDetails, variant: StoreVariant, stagingDir: File, staged: List<String>, cancel: CancelToken): PreparedFiles {
        val conv = converter ?: throw IOException(BlenderUsdConverter.NEEDS_BLENDER)
        val pkg = split(details.summary.ref.sourceId).second
        val roots = File(stagingDir, ROOT_USDS).takeIf { it.isFile }?.readText()
        val root = rootUsd(roots, staged) ?: throw IOException("No root USD file in $pkg")
        val input = StorePaths.resolveInside(stagingDir, root) ?: throw IOException("Unsafe root USD path: $root")
        val result = conv.run(input, File(stagingDir, OUT_DIR), StorePaths.slug(pkg), cancel)
        if (cancel.isCancelled) throw StoreCancelledException()
        return when (result) {
            is ConversionResult.Failed -> throw IOException(listOf(result.message, result.log.lines().takeLast(5).joinToString("\n")).filter { it.isNotBlank() }.joinToString("\n"))
            is ConversionResult.Converted -> {
                val skipped = result.outputs.filterNot { StoreFormats.isImportable(it) }
                PreparedFiles(
                    staged = result.outputs.map { "$OUT_DIR/$it" },
                    modifications = listOf("converted USD -> glTF via ${conv.id}"),
                    warnings = result.warnings + skipped.map { "$it is not a format Skate imports; it was skipped" },
                )
            }
        }
    }

    /** The converter output (glTF, `.bin`, textures) keeps its layout, so relative URIs resolve (AST-22). */
    override fun importPlan(details: StoreAssetDetails, variant: StoreVariant, staged: List<String>): ImportPlan =
        ImportPlan(staged.filter { it.startsWith("$OUT_DIR/") && StoreFormats.isImportable(it) }
            .map { ImportEntry(it, it.removePrefix("$OUT_DIR/")) })

    private fun repo(name: String): Repo = repoInfo.getOrPut(name) {
        val info = fetchJson("$hub/api/datasets/$name?$INFO_FIELDS")
        val sha = info.string("sha") ?: throw IOException("No commit for dataset $name")
        Repo(name, sha, datasetLicense(info))
    }

    /** Top-level package folders of [repo], at its commit; fetched once per session. */
    private fun packages(repo: Repo): List<String> = packages.getOrPut("${repo.name}@${repo.sha}") {
        pages("$hub/api/datasets/${repo.name}/tree/${repo.sha}")
            .filter { it.string("type") == "directory" }
            .mapNotNull { it.string("path") }
            .filter { !it.startsWith(".") && !it.contains('/') }
    }

    private fun tree(repo: Repo, pkg: String): List<HubFile> =
        pages("$hub/api/datasets/${repo.name}/tree/${repo.sha}/${encodePath(pkg)}?recursive=true")
            .filter { it.string("type") == "file" }
            .mapNotNull { entry -> entry.string("path")?.let { HubFile(it, entry.long("size") ?: 0L) } }
            .filter { it.path.startsWith("$pkg/") }

    /** Follows the Hub's `Link: <…>; rel="next"` pagination (1000 entries per page). */
    private fun pages(first: String): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        var url: String? = first
        var guard = 0
        while (url != null && guard++ < MAX_PAGES) {
            val result = http.get(url)
            if (!result.isSuccess) throw StoreHttpException(result.status, "Hugging Face HTTP ${result.status}: ${result.text.take(200)}")
            json.parseToJsonElement(result.text).jsonArray.forEach { (it as? JsonObject)?.let(out::add) }
            url = nextLink(result.headers)
        }
        return out
    }

    private fun fetchJson(url: String): JsonObject {
        val result = http.get(url)
        if (!result.isSuccess) throw StoreHttpException(result.status, "Hugging Face HTTP ${result.status}: ${result.text.take(200)}")
        return json.parseToJsonElement(result.text).jsonObject
    }

    /** Body of a small optional file, or null when it is missing. */
    private fun fetchText(url: String): String? = http.get(url).takeIf { it.isSuccess }?.text

    private fun summary(repo: Repo, pkg: String, license: LicenseInfo) = StoreAssetSummary(
        ref = StoreAssetRef(ID, "${repo.name}/$pkg", repo.sha),
        title = title(pkg),
        kind = StoreAssetKind.MODEL,
        thumbnailUrl = resolveUrl(repo, "$pkg/simready_usd/.thumbs/256x256/sm_${pkg}_01.usd.png"),
        license = license,
        authors = listOf(repo.name.substringBefore('/').uppercase()),
    )

    private fun resolveUrl(repo: Repo, path: String) = "$hub/datasets/${repo.name}/resolve/${repo.sha}/${encodePath(path)}"

    companion object {
        const val ID = "simready"
        const val HUB = "https://huggingface.co"
        /** Datasets following the SimReady packaging layout. `PhysicalAI-SimReady-Warehouse-01` uses another layout. */
        val DEFAULT_REPOS = listOf("nvidia/simready-assets")
        const val VARIANT_ID = "usd"
        const val ROOT_USDS = ".metadata/com.nvidia.simready.root_usds.json"
        const val PACKAGING = "com.nvidia.simready.packaging.json"
        /** Converter output folder inside staging. */
        const val OUT_DIR = "skate_gltf"
        private const val MAX_PAGES = 50
        /** `expand[]=…` (brackets encoded for `java.net.URI`): only these fields, not the 10 MB `siblings` list. */
        const val INFO_FIELDS = "expand%5B%5D=sha&expand%5B%5D=cardData&expand%5B%5D=tags"
        private val IMAGES = setOf("png", "jpg", "jpeg", "exr", "tga", "tif", "tiff", "hdr", "dds")

        private val json = Json { ignoreUnknownKeys = true }

        /** `nvidia/simready-assets/IV_pole_a01` → (`nvidia/simready-assets`, `IV_pole_a01`). */
        fun split(sourceId: String): Pair<String, String> {
            val cut = sourceId.lastIndexOf('/')
            require(cut > 0 && cut < sourceId.length - 1) { "Not a SimReady id: $sourceId" }
            return sourceId.substring(0, cut) to sourceId.substring(cut + 1)
        }

        /** `IV_pole_a01` → `IV pole a01`. */
        fun title(pkg: String): String = pkg.replace('_', ' ').trim()

        private fun searchText(pkg: String) = pkg.lowercase().replace('_', ' ')

        /**
         * Package files to download, relative to the package folder: everything except Omniverse thumbnails, the
         * metadata (but the root list, needed to convert) and the `source_assets/` images that duplicate `Textures/`.
         */
        fun isPackageFile(relative: String): Boolean {
            val segments = relative.split('/')
            if (".thumbs" in segments) return false
            if (segments.first() == ".metadata") return relative == ROOT_USDS
            if ("source_assets" in segments && StoreFormats.extension(relative) in IMAGES) return false
            return true
        }

        /**
         * The root USD among [files] (package-relative): the first entry of `root_usds.json` that was downloaded, else
         * a USD file directly in `simready_usd/`, else the least nested USD outside `payloads/`.
         */
        fun rootUsd(rootUsdsJson: String?, files: List<String>): String? {
            val listed = rootUsdsJson?.let { text ->
                runCatching { json.parseToJsonElement(text).jsonObject["entries"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()
            }.orEmpty()
            listed.firstOrNull { it in files && UsdConverter.isUsd(it) }?.let { return it }
            val usd = files.filter { UsdConverter.isUsd(it) && "payloads" !in it.split('/') }
            return usd.firstOrNull { it.startsWith("simready_usd/") && it.count { c -> c == '/' } == 1 }
                ?: usd.minWithOrNull(compareBy<String> { it.count { c -> c == '/' } }.thenBy { it.length })
        }

        /** Omniverse thumbnail of [root] (`<dir>/.thumbs/256x256/<file>.png`) when the package has one. */
        fun thumbnail(root: String, files: List<String>): String? {
            val dir = root.substringBeforeLast('/', "")
            val path = listOf(dir, ".thumbs/256x256/${root.substringAfterLast('/')}.png").filter { it.isNotEmpty() }.joinToString("/")
            return path.takeIf { it in files }
        }

        /** `license` of `com.nvidia.simready.packaging.json`, or null when absent or unknown. */
        fun packageLicense(text: String): LicenseInfo? = runCatching {
            json.parseToJsonElement(text).jsonObject.string("license")
        }.getOrNull()?.let(LicenseCatalog::lookup)?.takeIf { it.isKnown }

        /** Dataset card `license` (`cc-by-4.0`), else a `license:` tag; unknown blocks import (AST-16 §6.1). */
        fun datasetLicense(info: JsonObject): LicenseInfo {
            val card = (info["cardData"] as? JsonObject)?.string("license")
            val tag = (info["tags"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }?.firstOrNull { it.startsWith("license:") }?.removePrefix("license:")
            return LicenseCatalog.lookup(card ?: tag)
        }

        /** The `rel="next"` URL of a `Link` header, or null. */
        fun nextLink(headers: Map<String, List<String>>): String? =
            headers.entries.filter { it.key.equals("link", ignoreCase = true) }.flatMap { it.value }
                .flatMap { it.split(',') }
                .firstOrNull { Regex("""rel="?next"?""").containsMatchIn(it) }
                ?.let { Regex("<([^>]+)>").find(it)?.groupValues?.get(1) }

        /** Percent-encodes each segment of a repo path (spaces as `%20`, not `+`). */
        fun encodePath(path: String): String =
            path.split('/').joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") }

        private fun JsonObject.string(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        private fun JsonObject.long(key: String): Long? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
    }
}
