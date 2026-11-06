package com.pafoid.skate.editor.assetstore.providers.polyhaven

import com.pafoid.skate.editor.assetstore.AssetStoreProvider
import com.pafoid.skate.editor.assetstore.Author
import com.pafoid.skate.editor.assetstore.DownloadItem
import com.pafoid.skate.editor.assetstore.DownloadPlan
import com.pafoid.skate.editor.assetstore.ImportEntry
import com.pafoid.skate.editor.assetstore.ImportPlan
import com.pafoid.skate.editor.assetstore.LicenseCatalog
import com.pafoid.skate.editor.assetstore.MaterialSpec
import com.pafoid.skate.editor.assetstore.SourceFormat
import com.pafoid.skate.editor.assetstore.StoreAssetDetails
import com.pafoid.skate.editor.assetstore.StoreAssetKind
import com.pafoid.skate.editor.assetstore.StoreAssetRef
import com.pafoid.skate.editor.assetstore.StoreAssetSummary
import com.pafoid.skate.editor.assetstore.StoreHttp
import com.pafoid.skate.editor.assetstore.StoreHttpException
import com.pafoid.skate.editor.assetstore.StorePage
import com.pafoid.skate.editor.assetstore.StoreQuery
import com.pafoid.skate.editor.assetstore.StoreVariant
import com.pafoid.skate.engine.render.material.MaterialSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Poly Haven (AST-16 §5.1): CC0 models and textures, public API, no login. The API asks for a unique User-Agent
 * (sent by [StoreHttp]) and for users to be told assets come from Poly Haven ([poweredByKey]).
 *
 * Search joins `/search` (ranked slugs) with the `/assets?type=…` catalog (names, authors), cached per kind for the
 * session. Models import as glTF plus its `include` files; textures as the diffuse, OpenGL normal and ARM maps plus a
 * `.skmat` (ARM = AO/roughness/metal, the glTF ORM layout, so it fills both metallicRoughness and ao).
 */
class PolyHavenProvider(private val http: StoreHttp, private val apiBase: String = API_BASE) : AssetStoreProvider {
    override val id = ID
    override val displayNameKey = "assetstore.provider.polyhaven"
    override val kinds = setOf(StoreAssetKind.MODEL, StoreAssetKind.TEXTURE_SET)
    override val poweredByKey = "assetstore.provider.polyhaven.powered_by"

    private val catalogs = ConcurrentHashMap<StoreAssetKind, Map<String, JsonObject>>()

    override fun search(query: StoreQuery): StorePage {
        val catalog = catalog(query.kind)
        val slugs = if (query.text.isBlank()) {
            catalog.entries.sortedByDescending { it.value.long("download_count") ?: 0L }.map { it.key }
        } else {
            val q = URLEncoder.encode(query.text.trim().take(100), Charsets.UTF_8)
            fetch("$apiBase/search?q=$q&t=${apiType(query.kind)}&limit=$SEARCH_LIMIT")["results"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject.string("slug") }.filter { it in catalog }
        }
        val license = LicenseCatalog.CC0
        val visible = if (query.licenseFilter.accepts(license)) slugs else emptyList()
        val offset = query.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val end = minOf(visible.size, offset + query.pageSize)
        val items = if (offset >= end) emptyList() else visible.subList(offset, end).map { summary(it, catalog.getValue(it), query.kind) }
        return StorePage(items, end.takeIf { it < visible.size }?.toString(), visible.size)
    }

    override fun details(ref: StoreAssetRef): StoreAssetDetails {
        val info = fetch("$apiBase/info/${ref.sourceId}")
        val files = fetch("$apiBase/files/${ref.sourceId}")
        val kind = kindOf(info)
        val variants = when (kind) {
            StoreAssetKind.MODEL -> modelVariants(files)
            else -> textureVariants(files)
        }
        val summary = summary(ref.sourceId, info, kind)
        return StoreAssetDetails(
            summary = summary,
            description = info.string("description"),
            sourceUrl = "$SITE/a/${ref.sourceId}",
            authors = authors(info).map { Author(it) },
            license = LicenseCatalog.CC0,
            variants = variants,
            tags = info["tags"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
        )
    }

    override fun resolveDownload(details: StoreAssetDetails, variant: StoreVariant): DownloadPlan {
        val files = fetch("$apiBase/files/${details.summary.ref.sourceId}")
        val res = variant.resolution ?: throw IllegalArgumentException("Variant ${variant.id} has no resolution")
        return when (variant.format) {
            SourceFormat.GLTF -> {
                val gltf = files.obj("gltf")?.obj(res)?.obj("gltf") ?: throw IllegalArgumentException("No glTF at $res")
                val main = gltf.fileItem(fileName(gltf.string("url")!!))
                val includes = gltf.obj("include").orEmpty().map { (path, value) -> value.jsonObject.fileItem(path) }
                DownloadPlan(listOf(main) + includes)
            }
            SourceFormat.TEXTURE_SET -> DownloadPlan(TEXTURE_MAPS.mapNotNull { map ->
                files.obj(map)?.obj(res)?.obj("jpg")?.let { it.fileItem(fileName(it.string("url")!!)) }
            })
            else -> throw IllegalArgumentException("Unsupported variant ${variant.id}")
        }
    }

    override fun importPlan(details: StoreAssetDetails, variant: StoreVariant, staged: List<String>): ImportPlan {
        val entries = staged.map { ImportEntry(it, it) }
        if (variant.format != SourceFormat.TEXTURE_SET) return ImportPlan(entries)
        fun find(tag: String) = staged.firstOrNull { it.substringAfterLast('/').contains("_${tag}_") }
        val textures = buildMap {
            find("diff")?.let { put(MaterialSchema.Companion.Tex.BASE_COLOR, it) }
            find("nor_gl")?.let { put(MaterialSchema.Companion.Tex.NORMAL, it) }
            find("arm")?.let {
                put(MaterialSchema.Companion.Tex.METALLIC_ROUGHNESS, it)
                put(MaterialSchema.Companion.Tex.AO, it)
            }
        }
        return ImportPlan(entries, MaterialSpec(details.summary.ref.sourceId, textures).takeIf { textures.isNotEmpty() })
    }

    /** `/assets?type=…` for [kind], fetched once per session. */
    private fun catalog(kind: StoreAssetKind): Map<String, JsonObject> = catalogs.getOrPut(kind) {
        fetch("$apiBase/assets?type=${apiType(kind)}").mapValues { it.value.jsonObject }
    }

    private fun summary(slug: String, obj: JsonObject, kind: StoreAssetKind) = StoreAssetSummary(
        ref = StoreAssetRef(ID, slug),
        title = obj.string("name") ?: slug,
        kind = kind,
        thumbnailUrl = "$THUMBS/$slug.png?width=256&height=256",
        license = LicenseCatalog.CC0,
        authors = authors(obj),
    )

    private fun modelVariants(files: JsonObject): List<StoreVariant> =
        files.obj("gltf").orEmpty().mapNotNull { (res, value) ->
            val gltf = value.jsonObject.obj("gltf") ?: return@mapNotNull null
            val size = (gltf.long("size") ?: 0L) + gltf.obj("include").orEmpty().values.sumOf { it.jsonObject.long("size") ?: 0L }
            StoreVariant("gltf-$res", SourceFormat.GLTF, res, size)
        }.sortedBy { resolutionOrder(it.resolution) }

    private fun textureVariants(files: JsonObject): List<StoreVariant> =
        files.obj("Diffuse").orEmpty().keys.map { res ->
            val size = TEXTURE_MAPS.sumOf { files.obj(it)?.obj(res)?.obj("jpg")?.long("size") ?: 0L }
            StoreVariant("jpg-$res", SourceFormat.TEXTURE_SET, res, size)
        }.sortedBy { resolutionOrder(it.resolution) }

    private fun fetch(url: String): JsonObject {
        val result = http.get(url)
        if (!result.isSuccess) throw StoreHttpException(result.status, "Poly Haven HTTP ${result.status}: ${result.text.take(200)}")
        return json.parseToJsonElement(result.text).jsonObject
    }

    private fun JsonObject.fileItem(path: String) = DownloadItem(string("url")!!, path, long("size"), string("md5"))

    companion object {
        const val ID = "polyhaven"
        const val API_BASE = "https://api.polyhaven.com"
        const val SITE = "https://polyhaven.com"
        const val THUMBS = "https://cdn.polyhaven.com/asset_img/thumbs"
        private const val SEARCH_LIMIT = 200

        /** Maps downloaded for a texture set: base colour, OpenGL normal, packed AO/rough/metal. */
        val TEXTURE_MAPS = listOf("Diffuse", "nor_gl", "arm")

        private val json = Json { ignoreUnknownKeys = true }

        fun apiType(kind: StoreAssetKind): String = when (kind) {
            StoreAssetKind.MODEL -> "models"
            StoreAssetKind.TEXTURE_SET -> "textures"
            StoreAssetKind.HDRI -> "hdris"
        }

        /** `type` in `/info`: 0 HDRI, 1 texture, 2 model. */
        fun kindOf(info: JsonObject): StoreAssetKind = when (info["type"]?.jsonPrimitive?.intOrNull) {
            0 -> StoreAssetKind.HDRI
            1 -> StoreAssetKind.TEXTURE_SET
            else -> StoreAssetKind.MODEL
        }

        /** `1k` < `2k` < … by numeric value. */
        fun resolutionOrder(res: String?): Int = res?.removeSuffix("k")?.toIntOrNull() ?: Int.MAX_VALUE

        private fun authors(obj: JsonObject): List<String> = obj.obj("authors")?.keys?.toList().orEmpty()

        private fun fileName(url: String): String = url.substringBefore('?').substringAfterLast('/')

        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
        private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
        private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
        private fun JsonObject?.orEmpty(): Map<String, kotlinx.serialization.json.JsonElement> = this ?: emptyMap()
        private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
    }
}
