package com.pafoid.skate.engine.assets.loaders

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.imageio.ImageIO

/** A one-triangle `.gltf` + `.bin` with a base color texture under [textureUri], like a Poly Haven download (AST-22). */
object GltfFixture {
    const val TEXTURE_SIZE = 4

    fun png(size: Int = TEXTURE_SIZE): ByteArray {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
            for (x in 0 until size) for (y in 0 until size) setRGB(x, y, 0xFF3366CC.toInt())
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    /** Writes `<dir>/<name>.gltf`, `<dir>/<name>.bin` and the texture at [textureFile] (relative to [dir]). */
    fun write(dir: File, name: String, textureUri: String, textureFile: String = textureUri, embedBuffer: Boolean = false): File {
        dir.mkdirs()
        val bin = ByteBuffer.allocate(36 + 24).order(ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f).forEach { bin.putFloat(it) }
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f).forEach { bin.putFloat(it) }
        val bytes = bin.array()
        val bufferUri = if (embedBuffer) "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bytes)
        else "$name.bin".also { File(dir, it).writeBytes(bytes) }
        File(dir, textureFile).apply { parentFile.mkdirs() }.writeBytes(png())
        val gltf = File(dir, "$name.gltf")
        gltf.writeText(
            """
            {
              "asset": {"version": "2.0"},
              "scene": 0, "scenes": [{"nodes": [0]}], "nodes": [{"mesh": 0}],
              "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "TEXCOORD_0": 1}, "material": 0}]}],
              "materials": [{"pbrMetallicRoughness": {"baseColorTexture": {"index": 0}}}],
              "textures": [{"source": 0}],
              "images": [{"uri": "$textureUri"}],
              "buffers": [{"byteLength": ${bytes.size}, "uri": "$bufferUri"}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                {"buffer": 0, "byteOffset": 36, "byteLength": 24}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
                {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC2"}
              ]
            }
            """.trimIndent()
        )
        return gltf
    }
}
