package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import java.io.File

/**
 * Copies engine defaults into a new project (AST-26 §4.3, §5.2). Copy mode copies the whole [EngineDefaults.CATALOG];
 * reference mode only the basic shaders, which every project gets. A copy lands at `Assets/<logical path>` with
 * `engine.source` / `engine.sha256` in its `.meta`, which makes it the override of its `engine:` reference.
 *
 * A file already at the target that is not the override of that reference keeps its name; the copy becomes
 * `<name> (engine).<ext>` (decision 9). A source missing from the engine makes the result a failure listing it (the
 * other defaults are still copied), so a broken entry is an error instead of a silently missing texture (BUG-020).
 */
class EngineAssetCopier(
    private val engineRoot: File = File(System.getProperty("user.dir")),
) {
    private val resolver = EngineDefaultResolver(engineRoot)

    /** What a copy run did. */
    data class Report(val copied: List<File>, val missing: List<String>)

    /** Catalog references whose engine source does not exist (neither in the repo nor on the classpath). */
    fun missingSources(entries: List<String> = EngineDefaults.CATALOG): List<String> =
        entries.filter { ref -> EngineDefaults.logicalPath(ref)?.let(resolver::engineFile) == null }

    /** Copies the catalog ([copyAll]) or only the basic shaders into [projectDir]; failure when sources are missing. */
    fun copyEngineDefaults(projectDir: File, copyAll: Boolean = true): Result<Report> = runCatching {
        val entries = if (copyAll) EngineDefaults.CATALOG else EngineDefaults.Shaders.BASIC
        val projectResolver = EngineDefaultResolver(engineRoot) { projectDir }
        val copied = entries.mapNotNull { ref -> copy(projectDir, ref, projectResolver) }
        val missing = missingSources(entries)
        if (missing.isNotEmpty()) throw IllegalStateException("Missing engine defaults (copied the other ${copied.size}): $missing")
        Report(copied, missing)
    }

    /**
     * Copies one engine default into [projectDir] (also "Copy into project" later): returns the copy, or null when the
     * project already overrides it or the engine has no source.
     */
    fun copy(projectDir: File, reference: String, projectResolver: EngineDefaultResolver = EngineDefaultResolver(engineRoot) { projectDir }): File? {
        val logical = EngineDefaults.logicalPath(reference) ?: return null
        val source = resolver.engineFile(logical) ?: return null
        if (projectResolver.overrideFile(logical) != null) return null
        val target = freeTarget(File(ProjectLayout.assetsDir(projectDir), logical))
        target.parentFile.mkdirs()
        val bytes = source.readBytes()
        target.writeBytes(bytes)
        AssetMeta.write(target, AssetMeta(
            guid = AssetId.generate(),
            importer = AssetMeta.importerFor(target),
            settings = mapOf(EngineDefaults.META_SOURCE to logical, EngineDefaults.META_SHA256 to EngineDefaultResolver.sha256(bytes)),
        ))
        projectResolver.invalidate()
        return target
    }

    /**
     * Refreshes the project's **unchanged** basic shader copies whose engine shader changed (AST-26 §5.2, NQ7): they
     * follow the engine silently on project open. Modified copies are never touched. Returns the refreshed files.
     */
    fun refreshUnchangedShaders(projectDir: File): List<File> {
        val projectResolver = EngineDefaultResolver(engineRoot) { projectDir }
        return EngineDefaults.Shaders.BASIC.mapNotNull { ref ->
            val logical = EngineDefaults.logicalPath(ref) ?: return@mapNotNull null
            val copy = projectResolver.overrideFile(logical) ?: return@mapNotNull null
            val meta = AssetMeta.read(copy) ?: return@mapNotNull null
            val copiedHash = meta.settings[EngineDefaults.META_SHA256] ?: return@mapNotNull null
            if (EngineDefaultResolver.sha256(copy) != copiedHash) return@mapNotNull null // modified: an override
            val engineBytes = resolver.engineFile(logical)?.readBytes() ?: return@mapNotNull null
            val engineHash = EngineDefaultResolver.sha256(engineBytes)
            if (engineHash == copiedHash) return@mapNotNull null
            copy.writeBytes(engineBytes)
            AssetMeta.write(copy, meta.copy(settings = meta.settings + (EngineDefaults.META_SHA256 to engineHash)))
            copy
        }
    }

    /** [target], or `<name> (engine).<ext>` (then ` (engine 2)`...) when a file of the user's is already there. */
    private fun freeTarget(target: File): File {
        if (!target.exists()) return target
        val ext = target.extension.let { if (it.isEmpty()) "" else ".$it" }
        val stem = target.nameWithoutExtension
        return generateSequence(1) { it + 1 }
            .map { n -> File(target.parentFile, if (n == 1) "$stem (engine)$ext" else "$stem (engine $n)$ext") }
            .first { !it.exists() }
    }
}
