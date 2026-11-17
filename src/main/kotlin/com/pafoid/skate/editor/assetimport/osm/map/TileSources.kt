package com.pafoid.skate.editor.assetimport.osm.map

import com.pafoid.skate.editor.assetstore.CancelToken
import com.pafoid.skate.editor.assetstore.StoreHttpException
import com.pafoid.skate.editor.assetimport.osm.OsmHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/** A slippy-map tile address. */
data class TileKey(val z: Int, val x: Int, val y: Int) {
    init {
        require(z in 0..22 && x in 0 until (1 shl z) && y in 0 until (1 shl z)) { "Invalid tile $z/$x/$y" }
    }

    /** The tile at [zoom] (≤ [z]) that contains this one: over-zooming draws part of it (§3.1, above z14). */
    fun ancestorAt(zoom: Int): TileKey = if (zoom >= z) this else TileKey(zoom, x shr (z - zoom), y shr (z - zoom))

    override fun toString(): String = "$z/$x/$y"
}

/** Where map tiles come from (AST-17 §3.1, Q-N2). [cacheId] names its cache folder. */
sealed interface TileSource {
    val attribution: String
    val maxZoom: Int
    val cacheId: String
    val isVector: Boolean
    fun url(key: TileKey): String

    /** OpenFreeMap vector tiles (`.pbf`, OpenMapTiles schema, z0–14) from a resolved TileJSON template. */
    data class Vector(val template: String, override val maxZoom: Int = 14) : TileSource {
        override val attribution: String get() = OPENFREEMAP_ATTRIBUTION
        override val cacheId: String get() = "openfreemap-" + hash(template)
        override val isVector: Boolean get() = true
        override fun url(key: TileKey): String = expand(template, key)
    }

    /** The user's raster template (`https://…/{z}/{x}/{y}.png`) with the attribution the user entered. */
    data class Raster(val template: String, override val attribution: String, override val maxZoom: Int = 19) : TileSource {
        override val cacheId: String get() = "raster-" + hash(template)
        override val isVector: Boolean get() = false
        override fun url(key: TileKey): String = expand(template, key)
    }

    companion object {
        const val OPENFREEMAP_TILEJSON = "https://tiles.openfreemap.org/planet"
        const val OPENFREEMAP_ATTRIBUTION = "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"
        val COPYRIGHT_URLS = listOf("https://openfreemap.org", "https://www.openmaptiles.org/", "https://www.openstreetmap.org/copyright")

        fun expand(template: String, key: TileKey): String =
            template.replace("{z}", key.z.toString()).replace("{x}", key.x.toString()).replace("{y}", key.y.toString())

        private fun hash(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
    }
}

/**
 * The editor's map settings (stored in `EditorSettings`, AST-17 §3.1): a raster template replaces OpenFreeMap only
 * with a non-blank attribution, which is mandatory.
 */
object MapSettings {
    /** Why [template]/[attribution] cannot be saved, or null. */
    fun validate(template: String?, attribution: String?): String? {
        val t = template?.trim().orEmpty()
        if (t.isEmpty()) return null
        if (!(t.startsWith("https://") || t.startsWith("http://"))) return "The tile URL must start with https://"
        if (listOf("{z}", "{x}", "{y}").any { it !in t }) return "The tile URL needs {z}, {x} and {y}"
        if (attribution.isNullOrBlank()) return "Enter the attribution your tile provider requires"
        return null
    }

    /** The user's raster source when it is valid, else null (OpenFreeMap). */
    fun rasterSource(template: String?, attribution: String?): TileSource.Raster? =
        if (!template.isNullOrBlank() && validate(template, attribution) == null) TileSource.Raster(template.trim(), attribution!!.trim()) else null
}

/**
 * Resolves the OpenFreeMap TileJSON into a [TileSource.Vector] (the tile URL names a dated planet build, so a new
 * build starts new cache keys). The TileJSON is cached for a day in [cacheFile]; offline, an older copy is used.
 */
class TileJsonResolver(
    private val http: OsmHttp,
    private val cacheFile: File,
    private val clock: () -> Instant = Instant::now,
    private val url: String = TileSource.OPENFREEMAP_TILEJSON,
) {
    fun resolve(cancel: CancelToken = CancelToken()): TileSource.Vector {
        val cached = cacheFile.takeIf { it.isFile }
        if (cached != null && Duration.between(Instant.ofEpochMilli(cached.lastModified()), clock()) < MAX_AGE) {
            parse(cached.readText())?.let { return it }
        }
        val fetched = runCatching {
            val r = http.get(url, OsmHttp.headers("Accept" to "application/json"), cancel)
            if (!r.isSuccess) throw StoreHttpException(r.status, "TileJSON: HTTP ${r.status}")
            r.text
        }
        fetched.getOrNull()?.let { text ->
            parse(text)?.let { source ->
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(text)
                cacheFile.setLastModified(clock().toEpochMilli())
                return source
            }
        }
        cached?.let { parse(it.readText()) }?.let { return it } // offline: an expired TileJSON is better than no map
        throw fetched.exceptionOrNull() ?: IllegalStateException("TileJSON at $url has no tiles")
    }

    companion object {
        val MAX_AGE: Duration = Duration.ofDays(1)
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): TileSource.Vector? = runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val template = root["tiles"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return null
            TileSource.Vector(template, root["maxzoom"]?.jsonPrimitive?.intOrNull ?: 14)
        }.getOrNull()
    }
}

/** What the cache knows about one tile file. */
@kotlinx.serialization.Serializable
data class TileEntryMeta(val expiresAt: Long, val etag: String? = null, val lastModified: String? = null)

/**
 * Disk cache of tiles: `<root>/<source>/<z>/<x>/<y>` plus a `.json` sidecar ([TileEntryMeta]). Capped at
 * [maxBytes]: the least recently written tiles are evicted first. [clear] empties it (Editor Settings).
 */
class TileDiskCache(val root: File, private val maxBytes: Long = DEFAULT_MAX_BYTES, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }

    fun file(source: TileSource, key: TileKey): File = File(root, "${source.cacheId}/${key.z}/${key.x}/${key.y}")
    private fun metaFile(file: File) = File(file.parentFile, file.name + ".json")

    fun read(source: TileSource, key: TileKey): Pair<ByteArray, TileEntryMeta>? {
        val f = file(source, key)
        if (!f.isFile) return null
        val meta = runCatching { json.decodeFromString(TileEntryMeta.serializer(), metaFile(f).readText()) }.getOrElse { TileEntryMeta(0) }
        return f.readBytes() to meta
    }

    fun write(source: TileSource, key: TileKey, bytes: ByteArray, meta: TileEntryMeta) {
        val f = file(source, key)
        f.parentFile.mkdirs()
        f.writeBytes(bytes)
        writeMeta(source, key, meta)
        evict()
    }

    fun writeMeta(source: TileSource, key: TileKey, meta: TileEntryMeta) {
        val f = file(source, key)
        metaFile(f).writeText(json.encodeToString(TileEntryMeta.serializer(), meta))
        f.setLastModified(now())
    }

    fun sizeBytes(): Long = tiles().sumOf { it.length() }

    fun clear() {
        root.listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    private fun tiles(): List<File> = if (!root.isDirectory) emptyList()
    else root.walkTopDown().filter { it.isFile && !it.name.endsWith(".json") }.toList()

    private fun evict() {
        val all = tiles()
        var total = all.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in all.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete(); metaFile(f).delete()
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 200L * 1024 * 1024
    }
}
