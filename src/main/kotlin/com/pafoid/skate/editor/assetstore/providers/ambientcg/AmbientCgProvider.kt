package com.pafoid.skate.editor.assetstore.providers.ambientcg

import com.pafoid.skate.editor.assetstore.AssetStoreProvider
import com.pafoid.skate.editor.assetstore.Author
import com.pafoid.skate.editor.assetstore.DownloadItem
import com.pafoid.skate.editor.assetstore.DownloadPlan
import com.pafoid.skate.editor.assetstore.ImportEntry
import com.pafoid.skate.editor.assetstore.ImportPlan
import com.pafoid.skate.editor.assetstore.LicenseCatalog
import com.pafoid.skate.editor.assetstore.MaterialSpec
import com.pafoid.skate.editor.assetstore.OrmPacker
import com.pafoid.skate.editor.assetstore.SourceFormat
import com.pafoid.skate.editor.assetstore.StoreAssetDetails
import com.pafoid.skate.editor.assetstore.StoreAssetKind
import com.pafoid.skate.editor.assetstore.StoreAssetRef
import com.pafoid.skate.editor.assetstore.StoreAssetSummary
import com.pafoid.skate.editor.assetstore.StoreHttp
import com.pafoid.skate.editor.assetstore.StoreHttpException
import com.pafoid.skate.editor.assetstore.StorePage
import com.pafoid.skate.editor.assetstore.StorePaths
import com.pafoid.skate.editor.assetstore.StoreQuery
import com.pafoid.skate.editor.assetstore.StoreVariant
import com.pafoid.skate.editor.assetstore.StoreZip
import com.pafoid.skate.engine.render.material.MaterialSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * ambientCG (AST-16 §5.2): CC0 PBR materials, API v3, no login. Texture sets download as one zip per
 * resolution/format (`1K-JPG`, …) holding `<id>_<attr>_Color`, `_NormalGL`, `_Roughness`, `_Metalness`,
 * `_AmbientOcclusion`, … images. [postProcess] unzips in staging and packs roughness/metal/AO into one ORM PNG.
 *
 * The API is documented as "not as reliable or stable as needed for an enterprise-level application", so the
 * provider fails soft: malformed assets or downloads are skipped instead of failing the page, and a non-JSON or
 * error response becomes a [StoreHttpException] the window shows inline.
 */
class AmbientCgProvider(private val http: StoreHttp, private val apiBase: String = API_BASE) : AssetStoreProvider {
    override val id = ID
    override val displayNameKey = "assetstore.provider.ambientcg"
    override val kinds = setOf(StoreAssetKind.TEXTURE_SET)
    override val poweredByKey = "assetstore.provider.ambientcg.powered_by"

    override fun search(query: StoreQuery): StorePage {
        if (!query.licenseFilter.accepts(LicenseCatalog.CC0)) return StorePage(emptyList(), null, 0)
        val offset = query.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val q = query.text.trim().takeIf { it.isNotEmpty() }?.let { "&q=" + URLEncoder.encode(it.take(100), Charsets.UTF_8) } ?: ""
        val body = fetch("$apiBase/assets?type=material&sort=popular&limit=${query.pageSize}&offset=$offset&include=$SEARCH_INCLUDE$q")
        val items = assets(body).mapNotNull { runCatching { summary(it) }.getOrNull() }
        val total = (body["totalResults"] as? JsonPrimitive)?.intOrNull
        val hasNext = body["nextPageHttp"].let { it != null && it !is JsonNull }
        return StorePage(items, (offset + query.pageSize).toString().takeIf { hasNext }, total)
    }

    override fun details(ref: StoreAssetRef): StoreAssetDetails {
        val asset = asset(ref.sourceId)
        val summary = summary(asset)
        return StoreAssetDetails(
            summary = summary,
            description = asset.string("shortDescription")?.takeIf { it.isNotBlank() },
            sourceUrl = "$SITE/view?id=${ref.sourceId}",
            authors = listOf(Author(AUTHOR, SITE)),
            license = LicenseCatalog.CC0,
            variants = variants(asset),
            tags = (asset["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        )
    }

    override fun resolveDownload(details: StoreAssetDetails, variant: StoreVariant): DownloadPlan {
        val sourceId = details.summary.ref.sourceId
        val download = downloads(asset(sourceId)).firstOrNull { it.attributes == variant.id }
            ?: throw IOException("ambientCG no longer offers ${variant.id} for $sourceId")
        return DownloadPlan(listOf(DownloadItem(download.url, "${sourceId}_${variant.id}.zip", download.size)))
    }

    override fun postProcess(details: StoreAssetDetails, variant: StoreVariant, stagingDir: File, staged: List<String>): List<String> {
        val files = staged.flatMap { rel ->
            val file = File(stagingDir, rel)
            if (rel.endsWith(".zip", ignoreCase = true)) StoreZip.extract(file, stagingDir) else listOf(rel)
        }
        val rough = find(files, MAP_ROUGHNESS) ?: return files
        val orm = "${details.summary.ref.sourceId}_${variant.id}$ORM_SUFFIX.png"
        OrmPacker.pack(
            File(stagingDir, rough),
            find(files, MAP_METALNESS)?.let { File(stagingDir, it) },
            find(files, MAP_AO)?.let { File(stagingDir, it) },
            File(stagingDir, orm),
        )
        return files + orm
    }

    override fun importPlan(details: StoreAssetDetails, variant: StoreVariant, staged: List<String>): ImportPlan {
        val color = find(staged, MAP_COLOR)
        val normal = find(staged, MAP_NORMAL_GL)
        val orm = staged.firstOrNull { name(it).endsWith("$ORM_SUFFIX.png", ignoreCase = true) }
        val hasAo = find(staged, MAP_AO) != null
        val picked = listOfNotNull(color, normal, orm)
        val textures = buildMap {
            color?.let { put(MaterialSchema.Companion.Tex.BASE_COLOR, flat(it)) }
            normal?.let { put(MaterialSchema.Companion.Tex.NORMAL, flat(it)) }
            orm?.let {
                put(MaterialSchema.Companion.Tex.METALLIC_ROUGHNESS, flat(it))
                if (hasAo) put(MaterialSchema.Companion.Tex.AO, flat(it))
            }
        }
        val spec = MaterialSpec(details.summary.ref.sourceId, textures).takeIf { textures.isNotEmpty() }
        return ImportPlan(picked.map { ImportEntry(it, flat(it)) }, spec)
    }

    private fun asset(sourceId: String): JsonObject {
        require(StorePaths.isSafeSegment(sourceId)) { "Invalid ambientCG id: $sourceId" }
        return assets(fetch("$apiBase/assets?id=$sourceId&include=$DETAILS_INCLUDE"))
            .firstOrNull { it.string("id").equals(sourceId, ignoreCase = true) }
            ?: throw IOException("ambientCG has no asset $sourceId")
    }

    private fun summary(asset: JsonObject): StoreAssetSummary {
        val sourceId = asset.string("id") ?: throw IOException("Asset without id")
        val thumbs = asset["thumbnails"] as? JsonObject
        return StoreAssetSummary(
            ref = StoreAssetRef(ID, sourceId),
            title = asset.string("title") ?: sourceId,
            kind = StoreAssetKind.TEXTURE_SET,
            thumbnailUrl = thumbs?.string(THUMBNAIL) ?: thumbs?.values?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull },
            license = LicenseCatalog.CC0,
            authors = listOf(AUTHOR),
        )
    }

    private fun variants(asset: JsonObject): List<StoreVariant> = downloads(asset)
        .mapNotNull { d ->
            val match = VARIANT.matchEntire(d.attributes) ?: return@mapNotNull null
            StoreVariant(d.attributes, SourceFormat.TEXTURE_SET, match.groupValues[1].uppercase(), d.size)
        }
        .sortedWith(compareBy({ it.resolution?.removeSuffix("K")?.toIntOrNull() ?: Int.MAX_VALUE }, { if (it.id.endsWith("JPG")) 0 else 1 }))

    private class Download(val attributes: String, val url: String, val size: Long?)

    private fun downloads(asset: JsonObject): List<Download> = (asset["downloads"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val attributes = o.string("attributes") ?: return@mapNotNull null
        val url = o.string("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
        if (o.string("extension")?.lowercase() != "zip") return@mapNotNull null
        Download(attributes, url, (o["size"] as? JsonPrimitive)?.longOrNull)
    }

    private fun fetch(url: String): JsonObject {
        val result = http.get(url)
        if (!result.isSuccess) throw StoreHttpException(result.status, "ambientCG HTTP ${result.status}")
        val element = runCatching { json.parseToJsonElement(result.text) }.getOrNull()
        return element as? JsonObject ?: throw StoreHttpException(result.status, "ambientCG returned an unexpected response")
    }

    companion object {
        const val ID = "ambientcg"
        const val API_BASE = "https://ambientCG.com/api/v3"
        const val SITE = "https://ambientcg.com"
        const val AUTHOR = "ambientCG"
        const val THUMBNAIL = "256-PNG"
        const val SEARCH_INCLUDE = "title,thumbnails,tags"
        const val DETAILS_INCLUDE = "title,downloads,thumbnails,tags,maps,shortDescription"
        const val ORM_SUFFIX = "_ORM"

        const val MAP_COLOR = "Color"
        const val MAP_NORMAL_GL = "NormalGL"
        const val MAP_ROUGHNESS = "Roughness"
        const val MAP_METALNESS = "Metalness"
        const val MAP_AO = "AmbientOcclusion"

        /** Texture-set zips we import: `<n>K-JPG` / `<n>K-PNG` (not `-LQ-`/model variants). */
        private val VARIANT = Regex("""(\d+K)-(JPG|PNG)""", RegexOption.IGNORE_CASE)
        private val IMAGE = setOf("jpg", "jpeg", "png")

        private val json = Json { ignoreUnknownKeys = true }

        private fun assets(body: JsonObject): List<JsonObject> = (body["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

        /** First top-level image whose name ends with `_<map>` (case-insensitive). */
        private fun find(files: List<String>, map: String): String? = files.firstOrNull { rel ->
            val n = name(rel)
            n.substringAfterLast('.').lowercase() in IMAGE && n.substringBeforeLast('.').endsWith("_$map", ignoreCase = true)
        }

        private fun name(rel: String) = rel.substringAfterLast('/')

        private fun flat(rel: String) = name(rel)

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
    }
}
