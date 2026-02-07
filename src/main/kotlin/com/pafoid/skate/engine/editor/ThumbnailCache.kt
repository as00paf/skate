package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.ShaderConst.Attribs
import com.pafoid.skate.engine.assets.ShaderConst.Uniforms
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.scenes.components.toMatrix
import com.pafoid.skate.engine.scenes.components.Transform
import org.joml.Matrix4f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import kotlin.getValue

private const val THUMBNAIL_SIZE = 256

class ThumbnailCache: KoinComponent {
    private val resourceManager: ResourceManager by inject()

    private val thumbnails = mutableMapOf<String, Int>()
    private var frameBuffer: FrameBuffer? = null

    private val camera = Camera(Vector3f(2.5f, 2.5f, 2.5f))
    private val transform = Transform()
    
    init {
        camera.lookAt(Vector3f(0f, 0f, 0f))
    }

    fun getThumbnail(id: String, model: TexturedModel): Int {
        if (thumbnails.containsKey(id)) {
            return thumbnails[id]!!
        }

        val texId = renderThumbnail(model)
        thumbnails[id] = texId
        return texId
    }

    private fun renderThumbnail(model: TexturedModel): Int {
        if (frameBuffer == null) {
            frameBuffer = FrameBuffer(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
        }

        // Save current state
        val lastFbo = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val lastViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, lastViewport)

        val fbo = frameBuffer!!
        fbo.bind()
        
        glViewport(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
        glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glEnable(GL_DEPTH_TEST)

        val shader = resourceManager.loadShaderSync(Assets.Shaders.SHADER_3D_DEFAULT)
        shader.start()
        
        // Setup simple matrices
        val projectionMatrix = Matrix4f().perspective(Math.toRadians(45.0).toFloat(), 1.0f, 0.1f, 100f)
        val viewMatrix = camera.createViewMatrix()
        
        shader.uploadMat4f(Attribs.PROJECTION_MATRIX, projectionMatrix)
        shader.uploadMat4f(Attribs.VIEW_MATRIX, viewMatrix)
        shader.uploadMat4f(Attribs.TRANSFORMATION_MATRIX, transform.toMatrix())
        
        // Simple lighting
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
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, THUMBNAIL_SIZE, THUMBNAIL_SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as java.nio.ByteBuffer?)
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
