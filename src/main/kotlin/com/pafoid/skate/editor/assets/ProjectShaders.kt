package com.pafoid.skate.editor.assets

import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import java.io.File

/**
 * The shaders a material can use (EDT-80, REN-31) and where "New Shader" writes. GL- and ImGui-free.
 *
 * A material shader replaces the colour pass of a mesh, so the engine choices are the two basic mesh shaders. The
 * other basic shaders (includes, post-processing, sky, particles, shadows) are not material shaders. The project's
 * copies of basic shaders are left out of the project list: the `engine:` entries already resolve to them when they
 * are modified.
 */
object ProjectShaders {
    const val EXTENSION = "glsl"
    const val NEW_SHADER_NAME = "NewShader"

    val ENGINE_MATERIAL_SHADERS: List<String> = listOf(EngineDefaults.Shaders.DEFAULT_3D, EngineDefaults.Shaders.TEXTURE_3D)

    /** Project-relative paths (`Assets/...`) of the project's basic-shader copies. */
    private val basicCopies: Set<String> = EngineDefaults.Shaders.BASIC
        .mapNotNull { EngineDefaults.logicalPath(it) }
        .map { ProjectLayout.assetPath(it).lowercase() }
        .toSet()

    /** `.glsl` files under the project's `Assets/`, as project-relative references, sorted; basic copies excluded. */
    fun projectShaders(projectRoot: File?): List<String> {
        val assets = projectRoot?.let(ProjectLayout::assetsDir)?.takeIf { it.isDirectory } ?: return emptyList()
        return assets.walkTopDown()
            .onEnter { it == assets || !it.name.startsWith(".") }
            .filter { it.isFile && it.extension.equals(EXTENSION, ignoreCase = true) }
            .map { reference(projectRoot, it) }
            .filter { it.lowercase() !in basicCopies }
            .sorted()
            .toList()
    }

    /**
     * The Shader field's choices: the engine material shaders, then the project's shaders, plus [current] when it is
     * set and not among them (so an unusual reference still shows as selected). "None" is not in the list.
     */
    fun options(projectRoot: File?, current: String?): List<String> {
        val list = ENGINE_MATERIAL_SHADERS + projectShaders(projectRoot)
        return if (current != null && current !in list) list + current else list
    }

    /** Short label of [reference]: the file name, marked as the engine's for `engine:` references. */
    fun label(reference: String, engineTag: String): String {
        val name = reference.substringAfterLast('/').substringAfterLast(':')
        return if (EngineDefaults.isReference(reference)) "$name ($engineTag)" else name
    }

    /** `Assets/Shaders/<NewShader[n]>.glsl` in [projectRoot] that does not exist yet. */
    fun newShaderFile(projectRoot: File): File {
        val dir = ProjectLayout.typeDir(projectRoot, AssetFolder.SHADERS)
        var n = 0
        while (true) {
            val file = File(dir, "$NEW_SHADER_NAME${if (n == 0) "" else n.toString()}.$EXTENSION")
            if (!file.exists()) return file
            n++
        }
    }

    private fun reference(projectRoot: File, file: File): String =
        file.relativeTo(projectRoot).invariantSeparatorsPath
}
