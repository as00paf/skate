package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.utils.ShaderConst
import org.lwjgl.opengl.GL20
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class ShaderCompileException(message: String) : RuntimeException(message)

class ShaderLoader(private var verbose: Boolean = false, private val logger: LoggerService? = null) {

    private fun log(message: String, level: LoggerService.LogLevel = LoggerService.LogLevel.INFO) {
        logger?.log(message, level)
    }


    fun loadCustomShader(filePath: String): Shader {
        val src = String(Files.readAllBytes(Paths.get(filePath)))
        return loadShader(src, filePath)
    }

    fun loadShader(filePath: String): Shader {
        val src = this.javaClass.getResourceAsStream(filePath)?.readAllBytes()?.toString(Charsets.UTF_8).orEmpty()
        return loadShader(src, filePath)
    }

    private fun loadShader(src: String, filePath: String): Shader {
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

        if(verbose) {
            log("Vertex Source : '$vertexSrc'")
            log("Fragment Source : '$fragmentSrc'")
        }

        return compile(filePath, vertexSrc, fragmentSrc)
    }

    private fun compile(filePath: String, vertexSrc:String, fragmentSrc:String): Shader {
        // First load and compile the vertex shader
        val vertexShaderId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER)
        // Pass the shader source code to the GPU
        GL20.glShaderSource(vertexShaderId, vertexSrc)
        GL20.glCompileShader(vertexShaderId)

        //Check for errors in compilation process
        var success = GL20.glGetShaderi(vertexShaderId, GL20.GL_COMPILE_STATUS)
        if(success == GL20.GL_FALSE) {
            val len = GL20.glGetShaderi(vertexShaderId, GL20.GL_INFO_LOG_LENGTH)
            fail(filePath, "Vertex compilation failed", GL20.glGetShaderInfoLog(vertexShaderId, len)) {
                GL20.glDeleteShader(vertexShaderId)
            }
        }

        // Then load and compile the fragment shader
        val fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER)
        // Pass the shader source code to the GPU
        GL20.glShaderSource(fragmentShaderId, fragmentSrc)
        GL20.glCompileShader(fragmentShaderId)

        //Check for errors in compilation process
        success = GL20.glGetShaderi(fragmentShaderId, GL20.GL_COMPILE_STATUS)
        if(success == GL20.GL_FALSE) {
            val len = GL20.glGetShaderi(fragmentShaderId, GL20.GL_INFO_LOG_LENGTH)
            fail(filePath, "Fragment compilation failed", GL20.glGetShaderInfoLog(fragmentShaderId, len)) {
                GL20.glDeleteShader(vertexShaderId)
                GL20.glDeleteShader(fragmentShaderId)
            }
        }

        // Link shaders and check for error
        val shaderProgId = GL20.glCreateProgram()
        GL20.glAttachShader(shaderProgId, vertexShaderId)
        GL20.glAttachShader(shaderProgId, fragmentShaderId)

        GL20.glLinkProgram(shaderProgId)

        // Check for linking errors
        success = GL20.glGetProgrami(shaderProgId, GL20.GL_LINK_STATUS)
        if(success == GL20.GL_FALSE) {
            val len = GL20.glGetProgrami(shaderProgId, GL20.GL_INFO_LOG_LENGTH)
            fail(filePath, "Linking of shaders failed", GL20.glGetProgramInfoLog(shaderProgId, len)) {
                GL20.glDeleteProgram(shaderProgId)
                GL20.glDeleteShader(vertexShaderId)
                GL20.glDeleteShader(fragmentShaderId)
            }
        }

        return Shader(shaderProgId, vertexShaderId, fragmentShaderId)
    }

    private fun fail(filePath: String, what: String, infoLog: String, cleanup: () -> Unit): Nothing {
        cleanup()
        log("$what for '$filePath': $infoLog", LoggerService.LogLevel.ERROR)
        throw ShaderCompileException("$what for '$filePath'")
    }

    private fun bindAttribute(shaderProgId:Int, attribute: Int, varName: String) {
        GL20.glBindAttribLocation(shaderProgId, attribute, varName)
    }
}