package com.pafoid.skate.editor.assetimport.osm.map

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Draws a decoded OpenMapTiles vector tile into an image with Java2D (AST-17 §3.1) on a `JobSystem` worker; the
 * image is uploaded as a texture on the main thread. Layers in order: background, landcover/landuse/park fills,
 * water, transportation lines (width by `class`), buildings (from zoom 14), place labels at low zoom. Fixed colours;
 * no MapLibre style in v1.
 */
object TileRasterizer {
    const val SIZE = 512

    val BACKGROUND = Color(0xF2EFE9)
    val WATER = Color(0xA9CCE3)
    val PARK = Color(0xC8E6B0)
    val LANDUSE = Color(0xE6E1D8)
    val BUILDING = Color(0xD2CBC2)
    val ROAD = Color(0xFFFFFF)
    val MAJOR_ROAD = Color(0xF7D88B)
    val ROAD_CASING = Color(0xBDB6AA)
    val LABEL = Color(0x40403C)

    private val GREEN_CLASSES = setOf("park", "grass", "wood", "forest", "meadow", "garden", "recreation_ground", "cemetery", "pitch")
    private val MAJOR = setOf("motorway", "trunk", "primary", "secondary")

    /**
     * [layers] drawn at [zoom] into a [size] px image. [sub] over-zooms: the part of the tile covered by a descendant
     * `dz` levels deeper at offset (`sx`, `sy`) in descendant tiles, scaled up (used above the source's max zoom).
     */
    fun render(layers: List<MvtLayer>, zoom: Int, size: Int = SIZE, sub: Triple<Int, Int, Int> = Triple(0, 0, 0)): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = BACKGROUND
            g.fillRect(0, 0, size, size)
            val (dz, sx, sy) = sub
            val scale = (1 shl dz).toDouble()
            fun transform(extent: Int) = AffineTransform().apply {
                scale(size * scale / extent, size * scale / extent)
                translate(-sx * extent / scale, -sy * extent / scale)
            }
            val byName = layers.associateBy { it.name }
            fun fill(layer: String, color: (MvtFeature) -> Color?) {
                val l = byName[layer] ?: return
                val t = transform(l.extent)
                l.features.filter { it.type == MvtType.POLYGON }.forEach { f ->
                    g.color = color(f) ?: return@forEach
                    g.fill(t.createTransformedShape(path(f, closed = true)))
                }
            }
            fill("landuse") { LANDUSE }
            fill("landcover") { if ((it.tags["class"] as? String) in GREEN_CLASSES) PARK else null }
            fill("park") { PARK }
            fill("water") { WATER }
            byName["transportation"]?.let { l ->
                val t = transform(l.extent)
                val lines = l.features.filter { it.type == MvtType.LINESTRING }
                for (casing in listOf(true, false)) lines.forEach { f ->
                    val cls = f.tags["class"] as? String ?: ""
                    val w = roadWidth(cls, zoom) * scale.toFloat().coerceAtMost(4f)
                    g.stroke = BasicStroke(if (casing) w + 1.5f else w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g.color = if (casing) ROAD_CASING else if (cls in MAJOR) MAJOR_ROAD else ROAD
                    g.draw(t.createTransformedShape(path(f, closed = false)))
                }
            }
            if (zoom + dz >= 14) fill("building") { BUILDING }
            if (zoom <= 12) byName["place"]?.let { l ->
                val t = transform(l.extent)
                g.color = LABEL
                g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                l.features.filter { it.type == MvtType.POINT }.forEach { f ->
                    val name = f.tags["name:latin"] as? String ?: f.tags["name"] as? String ?: return@forEach
                    val p = f.parts.firstOrNull() ?: return@forEach
                    val pt = t.transform(java.awt.geom.Point2D.Float(p[0], p[1]), null)
                    g.drawString(name, pt.x.toFloat(), pt.y.toFloat())
                }
            }
        } finally {
            g.dispose()
        }
        return image
    }

    /** Line width in pixels per road class (wider roads and at higher zoom). */
    fun roadWidth(cls: String, zoom: Int): Float {
        val base = when (cls) {
            "motorway", "trunk" -> 4f
            "primary" -> 3.5f
            "secondary", "tertiary" -> 3f
            "minor", "service" -> 2f
            "path", "track" -> 1f
            else -> 1.5f
        }
        return base * (1f + (zoom - 12).coerceIn(0, 4) * 0.5f)
    }

    private fun path(f: MvtFeature, closed: Boolean): Path2D.Float {
        val p = Path2D.Float(Path2D.WIND_EVEN_ODD)
        f.parts.forEach { part ->
            if (part.size < 2) return@forEach
            p.moveTo(part[0], part[1])
            for (i in 2 until part.size step 2) p.lineTo(part[i], part[i + 1])
            if (closed) p.closePath()
        }
        return p
    }

    /** A raster source tile (PNG/JPEG bytes) decoded on the worker, or null when unreadable. */
    fun decodeRaster(bytes: ByteArray): BufferedImage? = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()

    /** For an over-zoomed [key] drawn from its ancestor at [sourceZoom]: (dz, offset x, offset y) for [render]. */
    fun subOf(key: TileKey, sourceZoom: Int): Triple<Int, Int, Int> {
        val dz = (key.z - sourceZoom).coerceAtLeast(0)
        val mask = (1 shl dz) - 1
        return Triple(dz, key.x and mask, key.y and mask)
    }
}
