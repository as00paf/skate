package com.pafoid.skate.editor.assetimport.osm.map

import com.pafoid.skate.editor.assetimport.osm.JdkOsmHttp
import com.pafoid.skate.editor.assetimport.osm.OsmHttp
import com.pafoid.skate.editor.assetimport.osm.OverpassClient
import com.pafoid.skate.editor.assetstore.CancelToken
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.systems.EditorPaths
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant

/**
 * The map back end for the city dialog (EDT-71) in one place: the tile source from [EditorSettings] (OpenFreeMap or
 * the user's raster template), the cached [TileClient], and [image] (fetch + decode + rasterise, on a worker). Tile
 * cache under `<user data>/cache/tiles`; Editor Settings' "Clear map cache" calls [clearCache].
 */
class OsmMapServices(
    private val settings: () -> EditorSettings,
    private val http: OsmHttp = JdkOsmHttp(),
    cacheRoot: File = File(EditorPaths.userDataDir(), "cache/tiles"),
    clock: () -> Instant = Instant::now,
) {
    val cache = TileDiskCache(cacheRoot, now = { clock().toEpochMilli() })
    val tiles = TileClient(http, cache, clock)
    private val tileJson = TileJsonResolver(http, File(cacheRoot, "openfreemap.tilejson"), clock)

    @Volatile
    private var vector: TileSource.Vector? = null

    fun overpass(): OverpassClient = OverpassClient(http, settings().overpassUrl.ifBlank { OverpassClient.DEFAULT_ENDPOINT })

    /** The active source: the user's raster template when valid, else OpenFreeMap (resolved once per session). */
    fun source(cancel: CancelToken = CancelToken()): TileSource =
        settings().let { MapSettings.rasterSource(it.osmTileUrl, it.osmTileAttribution) }
            ?: vector ?: tileJson.resolve(cancel).also { vector = it }

    /** The attribution the map must show, bottom-right, always (§3.1). */
    fun attribution(): String = settings().let { MapSettings.rasterSource(it.osmTileUrl, it.osmTileAttribution)?.attribution }
        ?: TileSource.OPENFREEMAP_ATTRIBUTION

    /**
     * The image for [key] (over-zoomed from the source's max zoom when deeper), or null when unavailable. Throws
     * `StoreCancelledException` when [cancel] fires (the tile left the view).
     */
    fun image(key: TileKey, cancel: CancelToken): BufferedImage? {
        val source = source(cancel)
        val fetchKey = key.ancestorAt(source.maxZoom)
        val data = tiles.fetch(source, fetchKey, cancel) ?: return null
        return if (source.isVector) {
            val layers = runCatching { MvtDecoder.decode(data.bytes) }.getOrNull() ?: return null
            TileRasterizer.render(layers, fetchKey.z, sub = TileRasterizer.subOf(key, fetchKey.z))
        } else {
            TileRasterizer.decodeRaster(data.bytes)?.let { if (fetchKey == key) it else overzoom(it, key, fetchKey.z) }
        }
    }

    fun clearCache() {
        cache.clear()
        vector = null
    }

    private fun overzoom(image: BufferedImage, key: TileKey, sourceZoom: Int): BufferedImage {
        val (dz, sx, sy) = TileRasterizer.subOf(key, sourceZoom)
        val part = image.width shr dz
        val out = BufferedImage(TileRasterizer.SIZE, TileRasterizer.SIZE, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            drawImage(image, 0, 0, out.width, out.height, sx * part, sy * part, (sx + 1) * part, (sy + 1) * part, null)
            dispose()
        }
        return out
    }
}
