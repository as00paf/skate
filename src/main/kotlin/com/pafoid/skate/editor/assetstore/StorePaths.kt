package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.editor.systems.EditorPaths
import com.pafoid.skate.engine.assets.ProjectLayout
import java.io.File

/** Folder names and path sanitising for the asset store (AST-16 §7). */
object StorePaths {
    /** Under the assets folder: `Assets/Store/<provider>/<slug>/`. */
    const val STORE_DIR = "Store"
    /** Project-local, hidden from `AssetRegistry.scan` (dot folder) and from exports. */
    const val PROJECT_HIDDEN_DIR = ".skate"
    const val STAGING_DIR = "cache/store"
    const val CACHE_DIR = "assetstore-cache"

    private val SEGMENT = Regex("""[A-Za-z0-9_][A-Za-z0-9_.-]*""")

    fun storeDir(projectDir: File): File = File(ProjectLayout.assetsDir(projectDir), STORE_DIR)

    fun importDir(projectDir: File, providerId: String, slug: String): File =
        File(File(storeDir(projectDir), requireSegment(providerId)), requireSegment(slug))

    fun stagingDir(projectDir: File, providerId: String, sourceId: String, variantId: String): File =
        File(projectDir, "$PROJECT_HIDDEN_DIR/$STAGING_DIR/${requireSegment(providerId)}/${slug(sourceId)}/${slug(variantId)}")

    /** Per-user cache for search results and thumbnails (never inside a project). */
    fun userCacheDir(): File = File(EditorPaths.dir, CACHE_DIR)

    /** `[a-z0-9_-]` folder name from any provider id; never empty. */
    fun slug(sourceId: String): String {
        val cleaned = sourceId.lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')
        return cleaned.ifEmpty { "asset" }
    }

    fun isSafeSegment(segment: String): Boolean = segment != ".." && SEGMENT.matches(segment)

    /**
     * [path] as a `/`-separated relative path made only of safe segments, or null when it is absolute, has a drive
     * letter, a `..`/empty segment or other characters (zip-slip guard; also applied to converter output).
     */
    fun safeRelative(path: String): String? {
        val normalized = path.replace('\\', '/')
        if (normalized.isEmpty() || normalized.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(normalized)) return null
        val segments = normalized.split('/').filter { it != "." }
        if (segments.isEmpty() || segments.any { !isSafeSegment(it) }) return null
        return segments.joinToString("/")
    }

    /** [relative] resolved under [root], or null if it would escape it. */
    fun resolveInside(root: File, relative: String): File? {
        val safe = safeRelative(relative) ?: return null
        val base = root.canonicalFile
        val file = File(base, safe).canonicalFile
        return file.takeIf { it.path.startsWith(base.path + File.separator) }
    }

    private fun requireSegment(segment: String): String =
        segment.also { require(isSafeSegment(it)) { "Unsafe folder name: $it" } }
}
