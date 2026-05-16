package com.pafoid.skate.engine.assets.database

/**
 * Categories of supported asset types.
 *
 * Each type defines the file extensions it maps from and the runtime extensions
 * it produces. This allows the import pipeline to select the correct importer
 * for any source file.
 */
enum class AssetType(
    val extensions: Set<String>,
    val metaExtensions: Set<String>
) {
    TEXTURE(
        extensions = setOf("png"),
        metaExtensions = setOf("png", "jpg", "jpeg", "tga", "bmp", "psd", "tif", "tiff", "hdr", "exr", "webp")
    ),
    MODEL(
        extensions = setOf("glb", "gltf"),
        metaExtensions = setOf("glb", "gltf", "obj", "fbx", "dae", "blend")
    ),
    AUDIO(
        extensions = setOf("wav", "ogg"),
        metaExtensions = setOf("wav", "ogg", "mp3", "flac", "aiff")
    ),
    SHADER(
        extensions = setOf("glsl"),
        metaExtensions = setOf("glsl", "vert", "frag", "comp")
    ),
    SCENE(
        extensions = setOf("scene", "skatescene"),
        metaExtensions = setOf("scene", "skatescene", "json")
    ),
    SCRIPT(
        extensions = setOf("kts"),
        metaExtensions = setOf("kts", "kt")
    ),
    FONT(
        extensions = setOf("ttf", "otf"),
        metaExtensions = setOf("ttf", "otf")
    ),
    UNKNOWN(
        extensions = emptySet(),
        metaExtensions = emptySet()
    );

    companion object {
        /**
         * Resolve an AssetType from a file extension.
         */
        fun fromExtension(ext: String): AssetType = entries.find {
            it.metaExtensions.contains(ext.lowercase())
        } ?: UNKNOWN
    }
}
