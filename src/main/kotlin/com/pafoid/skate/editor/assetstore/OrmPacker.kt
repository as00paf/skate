package com.pafoid.skate.editor.assetstore

import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Packs separate grayscale maps into one glTF-style ORM texture: R = ambient occlusion, G = roughness, B = metalness.
 * The 3D shader reads metallic from B and roughness from G of `metallicRoughness`, and AO from R of `ao`, so one ORM
 * file fills both slots (same layout as Poly Haven's ARM maps). Missing maps use neutral values: AO 1, metal 0.
 */
object OrmPacker {
    /** Writes [output] as PNG. All given maps must have the same size; throws [IOException] otherwise. */
    fun pack(roughness: File, metalness: File?, ao: File?, output: File) {
        val rough = read(roughness)
        val metal = metalness?.let(::read)
        val occlusion = ao?.let(::read)
        val w = rough.width
        val h = rough.height
        listOfNotNull(metal, occlusion).forEach {
            if (it.width != w || it.height != h) throw IOException("Map sizes differ: ${it.width}x${it.height} vs ${w}x$h")
        }
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val row = IntArray(w)
        val r = IntArray(w)
        val m = IntArray(w)
        for (y in 0 until h) {
            rough.getRGB(0, y, w, 1, row, 0, w)
            occlusion?.getRGB(0, y, w, 1, r, 0, w)
            metal?.getRGB(0, y, w, 1, m, 0, w)
            for (x in 0 until w) {
                val g = green(row[x])
                val red = if (occlusion != null) green(r[x]) else 255
                val blue = if (metal != null) green(m[x]) else 0
                row[x] = (red shl 16) or (g shl 8) or blue
            }
            out.setRGB(0, y, w, 1, row, 0, w)
        }
        output.parentFile?.mkdirs()
        if (!ImageIO.write(out, "png", output)) throw IOException("No PNG writer")
    }

    private fun read(file: File): BufferedImage = ImageIO.read(file) ?: throw IOException("Cannot decode ${file.name}")

    private fun green(argb: Int) = (argb shr 8) and 0xFF
}
