package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.utils.ShaderConst
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.ShaderCompileError
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import com.pafoid.skate.engine.render.rhi.ShaderStage
import com.pafoid.skate.engine.render.shader.ShaderPreprocessor
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class ShaderCompileException(message: String) : RuntimeException(message)

class ShaderLoader(
    private var verbose: Boolean = false,
    private val logger: LoggerService? = null,
    /** Directory holding the editable shader resources (e.g. `src/main/resources`); null disables live sources. */
    private val devSourceRoot: File? = null,
    /** Device that compiles the programs; resolved at load time, so the loader can be built before the window. */
    private val device: () -> RhiDevice = RhiContext::require,
) {

    private fun log(message: String, level: LoggerService.LogLevel = LoggerService.LogLevel.INFO) {
        logger?.log(message, level)
    }


    fun loadCustomShader(filePath: String): Shader {
        val src = String(Files.readAllBytes(Paths.get(filePath)))
        return loadShader(src, filePath)
    }

    fun loadShader(filePath: String, defines: Set<String> = emptySet()): Shader {
        val src = readSource(filePath) ?: ""
        return loadShader(src, filePath, defines)
    }

    /** Reads a classpath shader resource, preferring the live file under [devSourceRoot] so edits show up without a rebuild. */
    fun readSource(resourcePath: String): String? {
        devSourceRoot?.resolve(resourcePath.trimStart('/'))?.takeIf { it.isFile }?.let { return it.readText() }
        return this.javaClass.getResourceAsStream(resourcePath)?.readAllBytes()?.toString(Charsets.UTF_8)
    }

    private fun loadShader(src: String, filePath: String, defines: Set<String> = emptySet()): Shader {
        val splitSrc = src.split(ShaderConst.SPLITTER_REGEX.toRegex())

        var index = src.indexOf(ShaderConst.TYPE_DELIMITER) + ShaderConst.TYPE_DELIMITER_COUNT
        var eol = src.indexOf("\n", index)
        if (eol == -1) eol = src.length // In case it's the last line without EOL
        val firstPattern = src.substring(index, eol).trim()

        index = src.indexOf(ShaderConst.TYPE_DELIMITER, eol) + ShaderConst.TYPE_DELIMITER_COUNT
        eol = src.indexOf("\n", index)
        if (eol == -1) eol = src.length
        val secondPattern = src.substring(index, eol).trim()

        var vertexSrc = ""
        var fragmentSrc = ""

        if(firstPattern == ShaderConst.VERTEX) {
            vertexSrc = splitSrc[1]
        } else if(firstPattern == ShaderConst.FRAGMENT) {
            fragmentSrc = splitSrc[2]
        } else {
            throw IOException("Unexpected token '$firstPattern' in $filePath")
        }

        if(secondPattern == ShaderConst.VERTEX) {
            vertexSrc = splitSrc[1]
        } else if(secondPattern == ShaderConst.FRAGMENT) {
            fragmentSrc = splitSrc[2]
        } else {
            throw IOException("Unexpected token '$secondPattern' in $filePath")
        }

        val dir = filePath.substringBeforeLast('/', "")
        val resolve = { name: String -> readSource(if (dir.isEmpty()) name else "$dir/$name") }
        vertexSrc = ShaderPreprocessor.process(vertexSrc, defines, filePath, resolve)
        fragmentSrc = ShaderPreprocessor.process(fragmentSrc, defines, filePath, resolve)

        if(verbose) {
            log("Vertex Source : '$vertexSrc'")
            log("Fragment Source : '$fragmentSrc'")
        }

        return compile(filePath, vertexSrc, fragmentSrc)
    }

    private fun compile(filePath: String, vertexSrc: String, fragmentSrc: String): Shader = try {
        Shader(device().createShader(ShaderDesc(filePath, vertexSrc, fragmentSrc)))
    } catch (e: ShaderCompileError) {
        val what = when (e.stage) {
            ShaderStage.VERTEX -> "Vertex compilation failed"
            ShaderStage.FRAGMENT -> "Fragment compilation failed"
            ShaderStage.LINK -> "Linking of shaders failed"
        }
        log("$what for '$filePath': ${e.log}", LoggerService.LogLevel.ERROR)
        throw ShaderCompileException("$what for '$filePath'")
    }
}