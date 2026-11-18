package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.FileTypeResolver
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.pack.PackInput
import com.pafoid.skate.engine.assets.pack.PackManifest
import com.pafoid.skate.engine.assets.pack.PackedGuids
import com.pafoid.skate.engine.assets.pack.PackedStrings
import com.pafoid.skate.engine.assets.pack.ReferenceCollector
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.ByteBuffer

typealias AssetType = String
typealias Atlas = LinkedHashMap<AssetType, List<AssetInfo>>

@Serializable
data class AssetInfo(
    val type: FileType,
    val path: String,
    var position: Int = 0,
    var size: Int = 0,
    val rawSize: Int = -1,
    val compressed: Boolean = false,
    val sha256: String = "",
)
private val serializer: Serializer = Serializer()

/**
 * The icon and splash an export ships (AST-29, `docs/design/AST-15.md` §4.1), as pack paths: an `engine:` reference or
 * a project-relative path. [iconPath] is blank only when even the engine icon cannot be read (a broken install);
 * [splashPath] is blank when the game shows no splash. [notices] say which fallbacks were used.
 */
data class ExportArt(val iconPath: String, val splashPath: String, val splashSeconds: Float, val notices: List<ExportNotice>)

/** How many files an export would pack and their size on disk, before compression (AST-29 summary). */
data class PackEstimate(val assetCount: Int, val bytes: Long)

/**
 * Packs a project into `builds/game.bin` + `manifest.json`. Besides the project's files, the engine's game string
 * tables ([gameStringsDir]) are always shipped under [PackedStrings.PACK_DIR] so the exported game shows its UI text.
 *
 * Engine defaults the project references (`engine:` references, AST-26 §4.6) are packed under the reference itself,
 * read from the project's override when it has one, else from the engine under [engineRoot]. Shaders are always
 * packed. Identical files are stored once ([PackBuilder]).
 */
class AssetsPacker(
    private val logger: LoggerService,
    private val gameStringsDir: File = File(Assets.Strings.GAME_STRINGS),
    private val engineRoot: File = File(System.getProperty("user.dir")),
) {
    var atlas = Atlas()
    var lastManifest: PackManifest? = null
        private set

    /** GUIDs (from `.meta` sidecars) of the project files in the last pack: what export credits are matched against. */
    var lastPackedIds: Set<String> = emptySet()
        private set

    /** Types that are always packed; everything else is packed only when referenced from scenes, prefabs or the project. */
    private val alwaysPacked = setOf(FileType.PROJECT_FILE, FileType.SCENE, FileType.SCRIPT_KOTLIN, FileType.SCRIPT_JAVA, FileType.CONFIG, FileType.SHADER)
    private val packable = alwaysPacked + setOf(
        FileType.TEXTURE, FileType.MODEL_3D, FileType.ANIMATION, FileType.SOUND, FileType.PREFAB, FileType.JSON,
        FileType.SHADER, FileType.MATERIAL, FileType.FONT, FileType.TEXT
    )

    /** Scene files of [project] that an export can include or leave out (output and hidden folders skipped). */
    fun sceneFiles(project: Project, outputDir: File = defaultOutputDir(project)): List<File> =
        projectFiles(project, outputDir).filter { FileTypeResolver.resolve(it) == FileType.SCENE }.sortedBy { it.path }

    /**
     * Returns the files that [pack] would include, without writing anything. Scenes in [excludedScenes] (absolute
     * paths) are left out, except the project's default scene.
     */
    fun selectFiles(
        project: Project,
        onlyReferenced: Boolean = true,
        outputDir: File = defaultOutputDir(project),
        excludedScenes: Set<String> = emptySet(),
    ): List<File> = select(project, onlyReferenced, outputDir, excludedScenes).files

    /** The project files and the `engine:` references a pack would hold. */
    class Selection(val files: List<File>, val engineReferences: List<String>)

    fun select(
        project: Project,
        onlyReferenced: Boolean = true,
        outputDir: File = defaultOutputDir(project),
        excludedScenes: Set<String> = emptySet(),
    ): Selection {
        val projectDir = File(project.projectPath).parentFile
        val excluded = excludedScenes.map { File(it).canonicalFile }.toSet() - File(project.defaultScene).canonicalFile
        val all = projectFiles(project, outputDir).filter { it.canonicalFile !in excluded }

        val roots = (if (onlyReferenced) all.filter { FileTypeResolver.resolve(it) in alwaysPacked } else all).toMutableList()
        // The icon and splash ship even with "only referenced" (AST-29); engine ones are packed under their reference.
        val art = art(project)
        val artPaths = listOf(art.iconPath, art.splashPath).filter { it.isNotBlank() }
        artPaths.filterNot(EngineDefaults::isReference).forEach { roots += File(projectDir, it) }
        val registry = AssetRegistry(projectDir).also { it.scan() }
        val resolver = EngineDefaultResolver(engineRoot) { projectDir }
        val collector = ReferenceCollector(projectDir, registry, engineDefaults = resolver)
        val referenced = collector.collect(roots)
        val engineRefs = LinkedHashSet(collector.engineReferences)
        engineRefs += artPaths.filter(EngineDefaults::isReference)
        val files = if (onlyReferenced) {
            val allowed = all.map { it.canonicalFile }.toSet()
            (referenced + roots.map { it.canonicalFile }).filter { it in allowed }.distinct().sortedBy { it.path }
        } else {
            all.sortedBy { it.path }
        }
        return Selection(files, engineRefs.filter { resolver.resolve(it) != null }.sorted())
    }

    /**
     * Resolves [Project.iconPath] and [Project.splashPath] for an export (AST-29). The icon is mandatory: a blank or
     * unreadable one falls back to [EngineDefaults.Textures.APP_ICON]. A blank splash is the engine's
     * [EngineDefaults.Textures.SPLASH_SCREEN]; an unreadable one falls back to it too; [Project.splashSeconds] 0 means
     * none.
     */
    fun art(project: Project): ExportArt {
        val projectDir = File(project.projectPath).parentFile.canonicalFile
        val resolver = EngineDefaultResolver(engineRoot) { projectDir }
        val notices = mutableListOf<ExportNotice>()
        fun default(reference: String, unavailable: ExportWarning): String =
            if (resolver.resolve(reference) != null) reference else "".also { notices += ExportNotice(unavailable) }

        val icon = when {
            project.iconPath.isBlank() -> { notices += ExportNotice(ExportWarning.ICON_NOT_SET); null }
            else -> packPath(project.iconPath, projectDir, resolver)
                ?: null.also { notices += ExportNotice(ExportWarning.ICON_NOT_FOUND, listOf(project.iconPath)) }
        } ?: default(EngineDefaults.Textures.APP_ICON, ExportWarning.ICON_DEFAULT_MISSING)

        val seconds = Project.normalizedSplashSeconds(project.splashSeconds)
        val splash = when {
            seconds <= 0f -> ""
            project.splashPath.isBlank() -> default(EngineDefaults.Textures.SPLASH_SCREEN, ExportWarning.SPLASH_DEFAULT_MISSING)
            else -> packPath(project.splashPath, projectDir, resolver) ?: run {
                notices += ExportNotice(ExportWarning.SPLASH_NOT_FOUND, listOf(project.splashPath))
                default(EngineDefaults.Textures.SPLASH_SCREEN, ExportWarning.SPLASH_DEFAULT_MISSING)
            }
        }
        return ExportArt(icon, splash, if (splash.isBlank()) 0f else seconds, notices)
    }

    /**
     * The pack path of [path] (absolute, project-relative or `engine:`), or null when it is not a readable file the
     * pack can hold. A project copy of an engine default keeps its `engine:` reference, as saved projects do.
     */
    private fun packPath(path: String, projectDir: File, resolver: EngineDefaultResolver): String? {
        if (EngineDefaults.isReference(path)) return path.takeIf { resolver.resolve(it) != null }
        val file = File(path).let { if (it.isAbsolute) it else File(projectDir, path) }.canonicalFile
        if (!file.isFile) return null
        resolver.referenceFor(file)?.let { return it }
        return file.takeIf { it.startsWith(projectDir) }?.relativeTo(projectDir)?.invariantSeparatorsPath
    }

    /** The files [pack] would hold and their size on disk, for the export summary; reads no file contents. */
    fun estimate(
        project: Project,
        onlyReferenced: Boolean = true,
        outputDir: File = defaultOutputDir(project),
        excludedScenes: Set<String> = emptySet(),
    ): PackEstimate {
        val selection = select(project, onlyReferenced, outputDir, excludedScenes)
        val resolver = EngineDefaultResolver(engineRoot) { File(project.projectPath).parentFile.canonicalFile }
        val engineFiles = selection.engineReferences.mapNotNull(resolver::resolve)
        val files = selection.files + engineFiles
        return PackEstimate(files.size, files.sumOf { it.length() })
    }

    private fun projectFiles(project: Project, outputDir: File): List<File> {
        val projectDir = File(project.projectPath).parentFile
        val skipped = outputDir.canonicalFile
        // The project folder itself is never hidden, even when its path ends in `..` (QA-15).
        return projectDir.walkTopDown()
            .onEnter { (it == projectDir || !it.name.startsWith(".")) && it.canonicalFile != skipped }
            .filter { it.isFile && !it.name.startsWith(".") && !AssetMeta.isSidecar(it) && !isRawOsmSnapshot(it) }
            .filter { FileTypeResolver.resolve(it) in packable || isModelCompanion(it) }
            .toList()
    }

    /** `.mtl` / glTF `.bin` files load with their model; they have no [FileType] of their own. */
    private fun isModelCompanion(file: File): Boolean =
        file.extension.lowercase() in MODEL_COMPANIONS && file.name != BIN_FILE

    /**
     * Writes [BIN_FILE], [MANIFEST_FILE] and, when [writeReport] (debug builds, AST-29), [REPORT_FILE] to [outputDir];
     * the report always goes to the log. [onProgress] gets the current step, its completed fraction (0..1) and the
     * file being read, if any. The packed project file carries the export's resolved icon, splash and frame cap
     * ([art], [packedProject]), so the game needs no fallback logic of its own.
     */
    fun pack(
        project: Project,
        compress: Boolean = true,
        onlyReferenced: Boolean = true,
        outputDir: File = defaultOutputDir(project),
        excludedScenes: Set<String> = emptySet(),
        writeReport: Boolean = true,
        onProgress: (ExportStep, Float, String) -> Unit = { _, _, _ -> },
    ): Boolean {
        atlas = Atlas()
        // Both sides canonical: a project path in another form than the files (an 8.3 short name such as RUNNER~1 on
        // CI, a `..` segment, a junction) otherwise turns every pack entry into a `../../..` path (QA-15).
        val projectDir = File(project.projectPath).parentFile.canonicalFile
        onProgress(ExportStep.COLLECTING, 0f, "")
        val selection = select(project, onlyReferenced, outputDir, excludedScenes)
        val files = selection.files.sortedBy { FileTypeResolver.resolve(it) }
        val art = art(project)
        val projectFile = File(project.projectPath).canonicalFile
        val total = (files.size + selection.engineReferences.size).coerceAtLeast(1)
        val projectInputs = files.mapIndexed { index, file ->
            val relative = file.canonicalFile.relativeTo(projectDir).invariantSeparatorsPath
            onProgress(ExportStep.PACKING, index.toFloat() / total, relative)
            val bytes = file.readBytes().let { if (file.canonicalFile == projectFile) packedProject(it, project, art) else it }
            PackInput(file.absolutePath, relative, FileTypeResolver.resolve(file).name, bytes)
        }
        val resolver = EngineDefaultResolver(engineRoot) { projectDir }
        val engineInputs = selection.engineReferences.mapIndexedNotNull { index, reference ->
            val file = resolver.resolve(reference) ?: return@mapIndexedNotNull null
            onProgress(ExportStep.PACKING, (files.size + index).toFloat() / total, reference)
            // Stored under the reference itself, so the game's readBytes / localPath find `engine:` paths as saved.
            PackInput(file.absolutePath, reference, FileTypeResolver.resolve(file).name, file.readBytes())
        }
        // AST-35: GUIDs saved in scenes (MeshCollider3D.meshRef) resolve in the game through this table.
        val guids = files.mapNotNull { file ->
            AssetMeta.read(file)?.guid?.let { it.value to file.canonicalFile.relativeTo(projectDir).invariantSeparatorsPath }
        }.toMap()
        lastPackedIds = guids.keys
        val inputs = projectInputs + engineInputs + gameStringInputs(projectInputs) + PackedGuids.input(guids)
        val result = PackBuilder.build(project.name, inputs, compress, project.graphicsBackend)
        inputs.zip(result.manifest.entries).forEach { (input, entry) ->
            val type = FileType.valueOf(input.type)
            // Entries are addressed by their project-relative path, like the references in saved scenes (AST-09).
            val extension = File(input.relativePath).extension
            val info = AssetInfo(type, input.relativePath, entry.position, entry.storedSize, entry.rawSize, entry.compressed, entry.sha256)
            atlas[extension] = atlas[extension].orEmpty() + info
        }

        outputDir.mkdirs()
        val atlasData = serializer.encode(atlas)
        val encodedAtlas = atlasData.toByteArray(Charsets.UTF_8)
        val encodedAtlasSize = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(encodedAtlas.size).array()

        File(outputDir, BIN_FILE).writeBytes(encodedAtlasSize + encodedAtlas + result.data)
        File(outputDir, MANIFEST_FILE).writeText(serializer.json.encodeToString(PackManifest.serializer(), result.manifest))
        lastManifest = result.manifest
        onProgress(ExportStep.PACKING, 1f, "")

        val raw = result.manifest.entries.sumOf { it.rawSize }
        val firstAt = HashMap<Int, String>()
        val report = "Packed ${inputs.size} assets: $raw bytes -> ${result.data.size} bytes (atlas ${encodedAtlas.size} bytes)\n" +
            result.manifest.entries.joinToString("\n") { e ->
                val shared = firstAt.putIfAbsent(e.position, e.path)?.let { " (shared with $it)" }.orEmpty()
                "  ${e.path} ${e.rawSize}->${e.storedSize} ${e.sha256.take(12)}$shared"
            }
        val reportFile = File(outputDir, REPORT_FILE)
        if (writeReport) reportFile.writeText(report) else reportFile.delete() // a release build lists no packed paths
        logger.logEngine(report, LoggerService.LogLevel.INFO)
        return true
    }

    /** The engine's game string tables; a project file at the same pack path wins. */
    private fun gameStringInputs(projectInputs: List<PackInput>): List<PackInput> {
        val strings = PackedStrings.inputs(gameStringsDir)
        if (strings.isEmpty()) {
            logger.logEngine("No game string tables in ${gameStringsDir.absolutePath}; the exported game will show raw keys", LoggerService.LogLevel.WARN)
        }
        val taken = projectInputs.map { it.relativePath }.toSet()
        return strings.filter { it.relativePath !in taken }
    }

    companion object {
        const val BIN_FILE = "game.bin"
        const val REPORT_FILE = "atlas_report.txt"
        const val MANIFEST_FILE = "manifest.json"
        const val BUILDS_DIR = ProjectLayout.BUILDS_DIR
        private val MODEL_COMPANIONS = setOf("mtl", "bin")

        /** Raw OpenStreetMap snapshots stay in the project (ODbL, AST-17 §7): never packed, referenced or not. */
        const val OSM_SNAPSHOT_SUFFIX = ".osm.json"

        fun isRawOsmSnapshot(file: File): Boolean = file.name.lowercase().endsWith(OSM_SNAPSHOT_SUFFIX)

        private val projectJson = Json { prettyPrint = true }

        /**
         * The saved project file [bytes] with the export's resolved [art] and normalized frame cap (AST-29). A file
         * that is not a JSON object is packed unchanged.
         */
        fun packedProject(bytes: ByteArray, project: Project, art: ExportArt): ByteArray {
            val root = runCatching { Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }.getOrNull() ?: return bytes
            val patched = JsonObject(root + mapOf(
                "iconPath" to JsonPrimitive(art.iconPath),
                "splashPath" to JsonPrimitive(art.splashPath),
                "splashSeconds" to JsonPrimitive(art.splashSeconds),
                "targetFps" to JsonPrimitive(Project.normalizedTargetFps(project.targetFps)),
            ))
            return projectJson.encodeToString(JsonObject.serializer(), patched).toByteArray(Charsets.UTF_8)
        }

        /** `<project folder>/builds`. */
        fun defaultOutputDir(project: Project): File = File(File(project.projectPath).parentFile, BUILDS_DIR)
    }
}
