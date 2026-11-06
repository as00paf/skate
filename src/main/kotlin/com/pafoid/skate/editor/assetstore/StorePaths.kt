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
    private const val RESERVED = "<>:\"/\\|?*"

    fun storeDir(projectDir: File): File = File(ProjectLayout.assetsDir(projectDir), STORE_DIR)

    fun importDir(projectDir: File, providerId: String, slug: String): File =
        File(File(storeDir(projectDir), requireSegment(providerId)), requireSegment(slug))

    fun stagingDir(projectDir: File, providerId: String, sourceId: String, variantId: String): File =
        File(projectDir, "$PROJECT_HIDDEN_DIR/$STAGING_DIR/${requireSegment(providerId)}/${slug(sourceId)}/${slug(variantId)}")

    /** True when [stagingDir] lies under this project's `.skate/cache/store/` (rejects a download for another project). */
    fun isProjectStaging(projectDir: File, stagingDir: File): Boolean {
        val root = File(projectDir, "$PROJECT_HIDDEN_DIR/$STAGING_DIR").canonicalFile
        return stagingDir.canonicalFile.path.startsWith(root.path + File.separator)
    }

    /**
     * `<project>/.skate/`, created with a `.gitignore` holding `cache/` on first use: the ledger next to it is
     * committed, staging and backups are not.
     */
    fun ensureHiddenDir(projectDir: File): File {
        val dir = File(projectDir, PROJECT_HIDDEN_DIR)
        dir.mkdirs()
        val ignore = File(dir, ".gitignore")
        if (!ignore.exists()) ignore.writeText("cache/\n")
        return dir
    }

    /** Per-user cache for search results and thumbnails (never inside a project). */
    fun userCacheDir(): File = File(EditorPaths.dir, CACHE_DIR)

    /** `[a-z0-9_-]` folder name from any provider id; never empty. */
    fun slug(sourceId: String): String {
        val cleaned = sourceId.lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')
        return cleaned.ifEmpty { "asset" }
    }

    /** Strict name for folders we create (provider ids, slugs): `[A-Za-z0-9_][A-Za-z0-9_.-]*`. */
    fun isSafeSegment(segment: String): Boolean = segment != ".." && SEGMENT.matches(segment)

    /**
     * One segment of a relative path from a provider or an archive: any name Windows accepts (spaces and brackets are
     * common in packs, e.g. `GLB format`), but not `.`/`..`, reserved characters, control characters, or a trailing
     * space or dot.
     */
    fun isSafePathSegment(segment: String): Boolean =
        segment.isNotEmpty() && segment != "." && segment != ".." && segment.length <= 255 &&
            segment.none { it < ' ' || it in RESERVED } && !segment.endsWith(' ') && !segment.endsWith('.') && !segment.startsWith(' ')

    /**
     * [path] as a `/`-separated relative path of safe segments ([isSafePathSegment]), or null when it is absolute, has
     * a drive letter, a `..`/empty segment or reserved characters (zip-slip guard; also applied to converter output).
     */
    fun safeRelative(path: String): String? {
        val normalized = path.replace('\\', '/')
        if (normalized.isEmpty() || normalized.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(normalized)) return null
        val segments = normalized.split('/').filter { it != "." }
        if (segments.isEmpty() || segments.any { !isSafePathSegment(it) }) return null
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
