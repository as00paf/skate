package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.ShaderCompileError
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import com.pafoid.skate.engine.render.rhi.ShaderStage
import com.pafoid.skate.engine.render.rhi.vulkan.GlslToVulkan
import com.pafoid.skate.engine.render.shader.ProcessedSource
import com.pafoid.skate.engine.render.shader.ShaderErrorLines
import com.pafoid.skate.engine.render.shader.ShaderLoadException
import com.pafoid.skate.engine.render.shader.ShaderOrigin
import com.pafoid.skate.engine.render.shader.ShaderPreprocessor
import com.pafoid.skate.engine.render.shader.ShaderSource
import com.pafoid.skate.engine.render.shader.ShaderSourceResolver
import java.io.IOException

class ShaderCompileException(message: String) : RuntimeException(message)

/** A compiled shader and the ids of every file it was built from (the main file, includes, include misses). */
class LoadedShader(val shader: Shader, val id: String, val dependencies: Set<String>, val origins: Set<ShaderOrigin>)

/**
 * Loads `#type vertex` / `#type fragment` shader files through a [ShaderSourceResolver] (AST-26 §5.3): engine shaders
 * as `engine:Shaders/<name>` (or the legacy `/shaders/<name>`), with project overrides, includes resolved against
 * their includer, compile errors mapped back to the original file and line, and user shaders checked against the
 * GLSL subset the Vulkan backend accepts (AST-26 §5.4).
 */
class ShaderLoader(
    private var verbose: Boolean = false,
    private val logger: LoggerService? = null,
    /** Where sources come from; the default reads the jar's `/shaders/` only. */
    private val sources: ShaderSourceResolver = ShaderSourceResolver(),
    /** Device that compiles the programs; resolved at load time, so the loader can be built before the window. */
    private val device: () -> RhiDevice = RhiContext::require,
) {

    private fun log(message: String, level: LoggerService.LogLevel = LoggerService.LogLevel.INFO) {
        logger?.log(message, level)
    }

    /** Loads the shader file at the absolute [filePath] (no resolution). */
    fun loadCustomShader(filePath: String): Shader {
        val file = java.io.File(filePath)
        return compileSource(ShaderSource(filePath, file.readText(), ShaderOrigin.PROJECT, file.path), emptySet(), sources).shader
    }

    fun loadShader(filePath: String, defines: Set<String> = emptySet()): Shader = load(filePath, defines).shader

    /** Resolves and compiles [reference] with [defines]; throws [ShaderLoadException] with the files it read. */
    fun load(reference: String, defines: Set<String> = emptySet()): LoadedShader = load(reference, defines, sources)

    /** Like [load], but from the engine's own files only: the fallback when an override or its includes fail. */
    fun loadEngine(reference: String, defines: Set<String> = emptySet()): LoadedShader = load(reference, defines, sources.engineOnly())

    /** The text of shader [reference] as it resolves now, or null. */
    fun readSource(reference: String): String? = sources.resolve(reference)?.text

    private fun load(reference: String, defines: Set<String>, resolver: ShaderSourceResolver): LoadedShader {
        val id = ShaderSourceResolver.canonical(reference)
        val source = resolver.resolve(id) ?: throw ShaderLoadException("Shader '$reference' not found", setOf(id))
        return compileSource(source, defines, resolver)
    }

    private fun compileSource(source: ShaderSource, defines: Set<String>, resolver: ShaderSourceResolver): LoadedShader {
        val dependencies = linkedSetOf(source.id)
        val origins = linkedSetOf(source.origin)
        val misses = HashSet<String>()
        try {
            val stages = splitStages(source.text, source.id)
            val resolve = { name: String, includer: String ->
                resolver.resolveInclude(name, includer, misses)?.also { origins += it.origin }?.asResolved()
            }
            val vertex = ShaderPreprocessor.process(stages.getValue(VERTEX).text, defines, source.id, stages.getValue(VERTEX).firstLine, resolve)
            val fragment = ShaderPreprocessor.process(stages.getValue(FRAGMENT).text, defines, source.id, stages.getValue(FRAGMENT).firstLine, resolve)
            dependencies += vertex.dependencies
            dependencies += fragment.dependencies
            dependencies += misses
            if (verbose) {
                log("Vertex Source : '${vertex.text}'")
                log("Fragment Source : '${fragment.text}'")
            }
            if (origins.any { it == ShaderOrigin.PROJECT || it == ShaderOrigin.PROJECT_OVERRIDE || it == ShaderOrigin.PACK }) {
                checkVulkanSubset(source, vertex.text, fragment.text)
            }
            return LoadedShader(compile(source, vertex, fragment), source.id, dependencies, origins)
        } catch (e: ShaderLoadException) {
            throw e
        } catch (e: Exception) {
            dependencies += misses
            throw ShaderLoadException(e.message ?: "Shader '${source.id}' failed", dependencies, e)
        }
    }

    /** User shaders must stay inside the GLSL subset `GlslToVulkan` handles, or they would break only in Vulkan exports. */
    private fun checkVulkanSubset(source: ShaderSource, vertex: String, fragment: String) {
        try {
            GlslToVulkan.convert(vertex, fragment)
        } catch (e: Exception) {
            log(
                "Shader ${source.displayName} is outside the GLSL subset the Vulkan backend supports (docs/shaders.md): " +
                    "${e.message}. It works on OpenGL but will fail in a Vulkan export.",
                LoggerService.LogLevel.ERROR,
            )
        }
    }

    private class Stage(val text: String, val firstLine: Int)

    /** Splits a `#type vertex` / `#type fragment` file into its two stages, keeping each one's line offset. */
    private fun splitStages(src: String, id: String): Map<String, Stage> {
        val markers = TYPE_MARKER.findAll(src).toList()
        val stages = HashMap<String, Stage>()
        markers.forEachIndexed { i, m ->
            val type = m.groupValues[1]
            if (type != VERTEX && type != FRAGMENT) throw IOException("Unexpected token '$type' in $id")
            val end = if (i + 1 < markers.size) markers[i + 1].range.first else src.length
            val firstLine = src.substring(0, m.range.first).count { it == '\n' } + 1
            stages[type] = Stage(src.substring(m.range.last + 1, end), firstLine)
        }
        if (VERTEX !in stages || FRAGMENT !in stages) throw IOException("Shader $id needs '#type vertex' and '#type fragment' sections")
        return stages
    }

    private fun compile(source: ShaderSource, vertex: ProcessedSource, fragment: ProcessedSource): Shader = try {
        Shader(device().createShader(ShaderDesc(source.id, vertex.text, fragment.text)))
    } catch (e: ShaderCompileError) {
        val what = when (e.stage) {
            ShaderStage.VERTEX -> "Vertex compilation failed"
            ShaderStage.FRAGMENT -> "Fragment compilation failed"
            ShaderStage.LINK -> "Linking of shaders failed"
        }
        val mapped = when (e.stage) {
            ShaderStage.VERTEX -> ShaderErrorLines.map(e.log, vertex)
            ShaderStage.FRAGMENT -> ShaderErrorLines.map(e.log, fragment)
            ShaderStage.LINK -> e.log
        }
        log("$what for '${source.displayName}': $mapped", LoggerService.LogLevel.ERROR)
        throw ShaderCompileException("$what for '${source.displayName}': $mapped")
    }

    companion object {
        /** Classpath folder of the engine shaders (filled from the repo's `assets/Shaders` by `processResources`). */
        const val CLASSPATH_DIR = "shaders/"

        /** The repo's engine shader folder (AST-26 §4.1), read live in dev runs. */
        const val REPO_DIR = "assets/Shaders"

        private const val VERTEX = "vertex"
        private const val FRAGMENT = "fragment"
        private val TYPE_MARKER = Regex("""#type +([a-zA-Z]+)""")
    }
}
