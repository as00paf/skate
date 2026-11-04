package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.FileTypeResolver
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.pack.PackInput
import com.pafoid.skate.engine.assets.pack.PackManifest
import com.pafoid.skate.engine.assets.pack.PackedStrings
import com.pafoid.skate.engine.assets.pack.ReferenceCollector
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import kotlinx.serialization.Serializable
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
 * Packs a project into `builds/game.bin` + `manifest.json`. Besides the project's files, the engine's game string
 * tables ([gameStringsDir]) are always shipped under [PackedStrings.PACK_DIR] so the exported game shows its UI text.
 */
class AssetsPacker(
    private val logger: LoggerService,
    private val gameStringsDir: File = File(Assets.Strings.GAME_STRINGS),
) {
    var atlas = Atlas()
    var lastManifest: PackManifest? = null
        private set

    /** Types that are always packed; everything else is packed only when referenced from scenes, prefabs or the project. */
    private val alwaysPacked = setOf(FileType.PROJECT_FILE, FileType.SCENE, FileType.SCRIPT_KOTLIN, FileType.SCRIPT_JAVA, FileType.CONFIG)
    private val packable = alwaysPacked + setOf(
        FileType.TEXTURE, FileType.MODEL_3D, FileType.ANIMATION, FileType.SOUND, FileType.PREFAB, FileType.JSON,
        FileType.SHADER, FileType.MATERIAL, FileType.FONT, FileType.TEXT
    )

    /** Returns the files that [pack] would include, without writing anything. */
    fun selectFiles(project: Project, onlyReferenced: Boolean = true): List<File> {
        val projectDir = File(project.projectPath).parentFile
        val buildsDir = File(projectDir, BUILDS_DIR).canonicalFile
        val all = projectDir.walkTopDown()
            .onEnter { !it.name.startsWith(".") && it.canonicalFile != buildsDir }
            .filter { it.isFile && !it.name.startsWith(".") && !AssetMeta.isSidecar(it) }
            .filter { FileTypeResolver.resolve(it) in packable }
            .toList()
        if (!onlyReferenced) return all.sortedBy { it.path }

        val roots = all.filter { FileTypeResolver.resolve(it) in alwaysPacked }.toMutableList()
        if (project.iconPath.isNotBlank()) roots += File(project.iconPath).takeIf { it.isFile }?.let { listOf(it) }.orEmpty()
        val registry = AssetRegistry(projectDir).also { it.scan() }
        val referenced = ReferenceCollector(projectDir, registry).collect(roots)
        val allowed = all.map { it.canonicalFile }.toSet()
        return (referenced + roots.map { it.canonicalFile }).filter { it in allowed }.sortedBy { it.path }
    }

    fun pack(project: Project, compress: Boolean = true, onlyReferenced: Boolean = true): Boolean {
        atlas = Atlas()
        val projectDir = File(project.projectPath).parentFile
        val files = selectFiles(project, onlyReferenced).sortedBy { FileTypeResolver.resolve(it) }
        val projectInputs = files.map {
            PackInput(it.absolutePath, it.relativeTo(projectDir).invariantSeparatorsPath, FileTypeResolver.resolve(it).name, it.readBytes())
        }
        val inputs = projectInputs + gameStringInputs(projectInputs)
        val result = PackBuilder.build(project.name, inputs, compress)
        inputs.zip(result.manifest.entries).forEachIndexed { index, (input, entry) ->
            val type = FileType.valueOf(input.type)
            // Project files are looked up by their absolute path; game string tables by their pack path.
            val isProjectFile = index < projectInputs.size && !PackedStrings.isTable(input.relativePath)
            val path = if (isProjectFile) input.path else input.relativePath
            val extension = File(input.relativePath).extension
            val info = AssetInfo(type, path, entry.position, entry.storedSize, entry.rawSize, entry.compressed, entry.sha256)
            atlas[extension] = atlas[extension].orEmpty() + info
        }

        val outputDir = File(projectDir, BUILDS_DIR).also { it.mkdirs() }
        val atlasData = serializer.encode(atlas)
        val encodedAtlas = atlasData.toByteArray(Charsets.UTF_8)
        val encodedAtlasSize = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(encodedAtlas.size).array()

        File(outputDir, BIN_FILE).writeBytes(encodedAtlasSize + encodedAtlas + result.data)
        File(outputDir, MANIFEST_FILE).writeText(serializer.json.encodeToString(PackManifest.serializer(), result.manifest))
        lastManifest = result.manifest

        val stored = result.manifest.entries.sumOf { it.storedSize }
        val raw = result.manifest.entries.sumOf { it.rawSize }
        val report = "Packed ${inputs.size} assets: $raw bytes -> $stored bytes (atlas ${encodedAtlas.size} bytes)\n" +
            result.manifest.entries.joinToString("\n") { "  ${it.path} ${it.rawSize}->${it.storedSize} ${it.sha256.take(12)}" }
        File(outputDir, "atlas_report.txt").writeText(report)
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
        const val MANIFEST_FILE = "manifest.json"
        const val BUILDS_DIR = "builds"
    }
}
