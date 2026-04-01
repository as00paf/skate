package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toMatrix
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.utils.ShaderConst.Attribs
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_NEAREST
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.GL_VIEWPORT
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glClearColor
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glGetIntegerv
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL20.glDisableVertexAttribArray
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glBlitFramebuffer
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glFramebufferTexture2D
import org.lwjgl.opengl.GL30.glGenFramebuffers
import java.nio.ByteBuffer

private const val THUMBNAIL_SIZE = 256

class ThumbnailCache(
    private val resourceManager: ResourceManager
) {
    private val thumbnails = mutableMapOf<String, Int>()
    private var frameBuffer: FrameBuffer? = null

    private val camera = Camera(position = Vector3f(2.5f, 2.5f, 2.5f))
    private val transform = Transform()

    init {
        camera.lookAt(Vector3f(0f, 0f, 0f))
    }

    fun getThumbnail(id: String, model: TexturedModel): Int {
        if (thumbnails.containsKey(id)) {
            return thumbnails[id] ?: 0
        }

        val texId = renderThumbnail(model)
        thumbnails[id] = texId
        return texId
    }

    private fun renderThumbnail(model: TexturedModel): Int {
        if (frameBuffer == null) {
            frameBuffer = FrameBuffer(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
        }

        val lastFbo = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val lastViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, lastViewport)

        val fbo = frameBuffer ?: throw IllegalStateException("FrameBuffer failed to initialize")
        fbo.bind()
        
        glViewport(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
        glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glEnable(GL_DEPTH_TEST)

        val shader = resourceManager.loadShaderSync(Assets.Shaders.SHADER_3D_DEFAULT)
        shader.start()

        val projectionMatrix = Matrix4f().perspective(Math.toRadians(45.0).toFloat(), 1.0f, 0.1f, 100f)
        val viewMatrix = camera.createViewMatrix()
        
        shader.uploadMat4f(Attribs.PROJECTION_MATRIX, projectionMatrix)
        shader.uploadMat4f(Attribs.VIEW_MATRIX, viewMatrix)
        shader.uploadMat4f(Attribs.TRANSFORMATION_MATRIX, transform.toMatrix())

        shader.uploadVec3f(Uniforms.LIGHT_POSITION, Vector3f(5f, 5f, 5f))
        shader.uploadVec3f(Uniforms.LIGHT_COLOR, Vector3f(2.0f, 2.0f, 2.0f))
        shader.uploadVec3f(Uniforms.AMBIENT_LIGHT, Vector3f(0.8f, 0.8f, 0.8f))
        shader.uploadVec3f(Uniforms.SUN_DIRECTION, Vector3f(1f, -1f, 1f).normalize())
        shader.uploadVec3f(Uniforms.SUN_COLOR, Vector3f(2.0f, 2.0f, 2.0f))
        
        // Render each part
        for (part in model.mesh) {
            val rawModel = part.rawModel
            val material = part.material
            
            glBindVertexArray(rawModel.vaoId)
            rawModel.enabledAttributes.forEach { glEnableVertexAttribArray(it) }
            
            glActiveTexture(GL_TEXTURE0)
            material.baseColorTexture?.bind() ?: resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind()
            shader.uploadInt(Uniforms.BASE_COLOR_TEXTURE, 0)
            shader.uploadVec4f(Uniforms.BASE_COLOR_FACTOR, material.baseColorFactor)
            
            shader.uploadBoolean(Uniforms.HAS_NORMAL_MAP, false)
            shader.uploadBoolean(Uniforms.HAS_METALLIC_ROUGHNESS_TEXTURE, false)
            shader.uploadBoolean(Uniforms.HAS_AO_TEXTURE, false)
            shader.uploadBoolean(Uniforms.HAS_EMISSIVE_TEXTURE, false)
            shader.uploadInt(Uniforms.ALPHA_MODE, 0)
            shader.uploadBoolean(Uniforms.HAS_SKIN, false)

            glDrawElements(rawModel.drawMode, rawModel.vertexCount, GL_UNSIGNED_INT, 0)
            
            rawModel.enabledAttributes.forEach { glDisableVertexAttribArray(it) }
        }
        
        shader.stop()
        
        // Create a new texture and copy the FBO content
        val resultTexId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, resultTexId)
        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            THUMBNAIL_SIZE,
            THUMBNAIL_SIZE,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            null as ByteBuffer?
        )
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        
        val tempFbo = glGenFramebuffers()
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo.getFboId())
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, tempFbo)
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, resultTexId, 0)
        
        glBlitFramebuffer(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, GL_COLOR_BUFFER_BIT, GL_NEAREST)
        
        // Restore state
        glBindFramebuffer(GL_FRAMEBUFFER, lastFbo)
        glViewport(lastViewport[0], lastViewport[1], lastViewport[2], lastViewport[3])
        glDeleteFramebuffers(tempFbo)
        
        return resultTexId
    }
}
