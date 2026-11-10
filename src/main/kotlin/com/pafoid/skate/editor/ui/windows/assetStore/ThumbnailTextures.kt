package com.pafoid.skate.editor.ui.windows.assetStore

/**
 * Small LRU of thumbnail textures for the Asset Store window (AST-16 review item 9): infinite scroll must not keep
 * every thumbnail on the GPU. [load] uploads a file and returns its texture id; [unload] frees it
 * (`AssetsManager.unloadTexture`) on eviction and on [clear] (window closed, project changed).
 */
class ThumbnailTextures(
    private val capacity: Int,
    private val load: (String) -> Long,
    private val unload: (String) -> Unit,
) {
    private val entries = object : LinkedHashMap<String, Long>(16, 0.75f, true) {}

    val size: Int get() = entries.size

    fun texId(path: String): Long {
        entries[path]?.let { return it }
        val id = load(path)
        entries[path] = id
        while (entries.size > capacity) {
            val eldest = entries.keys.first()
            entries.remove(eldest)
            unload(eldest)
        }
        return id
    }

    fun clear() {
        entries.keys.toList().forEach(unload)
        entries.clear()
    }
}
