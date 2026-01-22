package marki.renderer

import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

const val SPLITTER_REGEX = "(#type)( )+([a-zA-Z]+)"
const val TYPE_DELIMITER = "#type"
const val EOL_DELIMITER = "\r\n"
const val TYPE_DELIMITER_COUNT = 6
const val FRAGMENT = "fragment"
const val VERTEX = "vertex"

class Shader(val filePath: String) {

    private var shaderProgId: Int = -1
    private var vertexSrc: String = ""
    private var fragmentSrc: String = ""

    private var vertexId: Int = -1
    private var fragmentId: Int = -1

    private var isUsed = false

    init {
        try {
            loadFromFile()
        } catch(e: IOException) {
            assert(false) { "Error: Could not open file for shader: $filePath" }
        }
    }

    fun compile():Shader {
        // First load and compile the vertex shader
        vertexId = glCreateShader(GL_VERTEX_SHADER)
        // Pass the shader source code to the GPU
        glShaderSource(vertexId, vertexSrc)
        glCompileShader(vertexId)

        //Check for errors in compilation process
        var success = glGetShaderi(vertexId, GL_COMPILE_STATUS)
        if(success == GL_FALSE) {
            val len = glGetShaderi(vertexId, GL_INFO_LOG_LENGTH)
            println("Error: $filePath \r\nVertex compilation failed")
            println(glGetShaderInfoLog(vertexId, len))
            assert(false) { "" }
        }
        
        // Then load and compile the fragment shader
        fragmentId = glCreateShader(GL_FRAGMENT_SHADER)
        // Pass the shader source code to the GPU
        glShaderSource(fragmentId, fragmentSrc)
        glCompileShader(fragmentId)

        //Check for errors in compilation process
        success = glGetShaderi(fragmentId, GL_COMPILE_STATUS)
        if(success == GL_FALSE) {
            val len = glGetShaderi(fragmentId, GL_INFO_LOG_LENGTH)
            println("Error: '$filePath'\r\nFragment compilation failed")
            println(glGetShaderInfoLog(fragmentId, len))
            assert(false) { "" }
        }
        
        // Link shaders and check for error
        shaderProgId = glCreateProgram()
        glAttachShader(shaderProgId, vertexId)
        glAttachShader(shaderProgId, fragmentId)
        glLinkProgram(shaderProgId)

        // Check for linking errors
        success = glGetProgrami(shaderProgId, GL_LINK_STATUS)
        if(success == GL_FALSE) {
            val len = glGetProgrami(shaderProgId, GL_INFO_LOG_LENGTH)
            println("Error: '$filePath'\r\nLinking of shaders failed")
            println(glGetProgramInfoLog(shaderProgId, len))
            assert(false) {""}
        }

        return this
    }

    fun use() {
        if(!isUsed){
            glUseProgram(shaderProgId)
            isUsed = true
        }
    }

    fun detach() {
        glUseProgram(0)
        isUsed = false
    }

    private fun loadFromFile(verbose: Boolean = false) {
        val src = String(Files.readAllBytes(Paths.get(filePath)))
        val splitSrc = src.split(SPLITTER_REGEX.toRegex())

        var index = src.indexOf(TYPE_DELIMITER) + TYPE_DELIMITER_COUNT
        var eol = src.indexOf(EOL_DELIMITER, index)
        val firstPattern = src.substring(index, eol).trim()

        index = src.indexOf(TYPE_DELIMITER, eol) + TYPE_DELIMITER_COUNT
        eol = src.indexOf(EOL_DELIMITER, index)
        val secondPattern = src.substring(index, eol).trim()

        if(firstPattern == VERTEX) {
            vertexSrc = splitSrc[1]
        } else if(firstPattern == FRAGMENT) {
            fragmentSrc = splitSrc[2]
        } else {
            throw IOException("Unexpected token '$firstPattern'")
        }

        if(secondPattern == VERTEX) {
            vertexSrc = splitSrc[1]
        } else if(secondPattern == FRAGMENT) {
            fragmentSrc = splitSrc[2]
        } else {
            throw IOException("Unexpected token '$secondPattern'")
        }

        if(verbose) {
            println("Vertex Source : '$vertexSrc'")
            println("Fragment Source : '$fragmentSrc'")
        }
    }

    fun uploadMat4f(varName: String, mat4f: Matrix4f) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        use()
        val matBuffer = BufferUtils.createFloatBuffer(16)
        mat4f.get(matBuffer)
        glUniformMatrix4fv(varLocation, false, matBuffer)
    }

    fun uploadMat3f(varName: String, mat3f: Matrix3f) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        use()
        val matBuffer = BufferUtils.createFloatBuffer(9)
        mat3f.get(matBuffer)
        glUniformMatrix4fv(varLocation, false, matBuffer)
    }

    fun uploadVec2f(varName: String, vec: Vector2f) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        use()
        glUniform2f(varLocation, vec.x, vec.y)
    }

    fun uploadVec3f(varName: String, vec: Vector3f) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        use()
        glUniform3f(varLocation, vec.x, vec.y, vec.z)
    }

    fun uploadVec4f(varName: String, vec: Vector4f) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        use()
        glUniform4f(varLocation, vec.x, vec.y, vec.z, vec.w)
    }

    fun uploadFloat(varName: String, value: Float) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        use()
        glUniform1f(varLocation, value)
    }

    fun uploadInt(varName: String, value: Int) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        use()
        glUniform1i (varLocation, value)
    }

    fun uploadIntArray(varName: String, array: IntArray) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        use()
        glUniform1iv (varLocation, array)
    }

    fun uploadTexture(varName: String, slot: Int) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        use()
        glUniform1i (varLocation, slot)
    }

    companion object {
        const val DEFAULT = "assets/shaders/default.glsl"
        const val PICKING = "assets/shaders/picking.glsl"
        const val DEBUG = "assets/shaders/debugLine2D.glsl"
    }
}