package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toMatrix
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.utils.ShaderConst
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.GL_VIEWPORT
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glClearColor
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glGetIntegerv
import org.lwjgl.opengl.GL11.glReadPixels
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL30.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.glBindFramebuffer
import java.nio.ByteBuffer
import kotlin.math.max

private const val THUMBNAIL_SIZE = 256

/**
 * Renders 3D model thumbnails to off-screen textures.
 *
 * Encapsulates all OpenGL calls for thumbnail generation:
 * - FBO lifecycle management
 * - Viewport save/restore
 * - Camera auto-framing based on model bounds
 * - Texture creation and copying from FBO
 *
 * Each call to [renderThumbnail] returns a NEW OpenGL texture ID
 * containing the rendered thumbnail. The caller is responsible for
 * caching and eventually deleting the texture.
 */
class ThumbnailRenderer(
    private val assetsManager: AssetsManager,
    private val modelRenderer: ModelRenderer,
) {
    private var shader: Shader = assetsManager.getShader(Assets.Shaders.SHADER_3D_DEFAULT)
    private var fbo: FrameBuffer = FrameBuffer(THUMBNAIL_SIZE, THUMBNAIL_SIZE)

    // Reusable temp buffers
    private val camera = CameraComponent()
    private val transform = Transform()

    init {
        fbo.initialize()
    }

    /**
     * Renders a thumbnail for the given model.
     * @return A NEW OpenGL texture ID containing the rendered thumbnail.
     *         The caller must eventually delete this texture with glDeleteTextures.
     */
    fun renderThumbnail(model: TexturedModel): Int {
        // Save OpenGL state
        val lastFbo = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val lastViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, lastViewport)

        // Setup FBO for off-screen rendering
        fbo.bind()
        glViewport(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
        glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glEnable(GL_DEPTH_TEST)

        // Auto-frame camera based on model bounds
        val (cameraPos, lookAt) = computeCameraTransform(model)
        camera.position.set(cameraPos)
        camera.lookAt(lookAt)

        // Create temporary GameObject (not added to any Scene)
        val tempGameObject = GameObject("ThumbnailTemp")
        transform.translation.set(0f, 0f, 0f)
        transform.rotation.set(0f, 0f, 0f)
        transform.scale.set(1f, 1f, 1f)
        tempGameObject.addComponent(transform)

        val renderComponent = RenderComponent(model = model, castShadow = false, receiveShadow = false)
        tempGameObject.addComponent(renderComponent)

        // Upload view/projection matrices
        val projectionMatrix = Matrix4f().perspective(Math.toRadians(45.0).toFloat(), 1.0f, 0.1f, 100f)
        camera.update(0f)
        val viewMatrix = camera.view
        shader.uploadMat4f(ShaderConst.Uniforms.PROJECTION_MATRIX, projectionMatrix)
        shader.uploadMat4f(ShaderConst.Uniforms.VIEW_MATRIX, viewMatrix)
        shader.uploadMat4f(ShaderConst.Uniforms.TRANSFORMATION_MATRIX, transform.toMatrix())

        // Upload basic lighting for thumbnails
        shader.uploadVec3f(ShaderConst.Uniforms.LIGHT_POSITION, Vector3f(5f, 5f, 5f))
        shader.uploadVec3f(ShaderConst.Uniforms.LIGHT_COLOR, Vector3f(2.0f, 2.0f, 2.0f))
        shader.uploadVec3f(ShaderConst.Uniforms.AMBIENT_LIGHT, Vector3f(0.8f, 0.8f, 0.8f))

        // Delegate actual model rendering to ModelRenderer
        modelRenderer.render(
            transform = transform,
            renderComponent = renderComponent,
            shader = shader,
            cameraPosition = camera.position
        )

        // Create a NEW texture and copy FBO content into it
        val thumbnailTextureId = createTextureFromFbo(fbo)

        // Restore OpenGL state
        glBindFramebuffer(GL_FRAMEBUFFER, lastFbo)
        glViewport(lastViewport[0], lastViewport[1], lastViewport[2], lastViewport[3])

        return thumbnailTextureId
    }

    /**
     * Creates a new OpenGL texture and copies the FBO's color attachment into it.
     * Uses glReadPixels for simplicity and reliability.
     */
    private fun createTextureFromFbo(fbo: FrameBuffer): Int {
        // Read pixels from FBO into CPU memory
        val pixelBuffer = ByteBuffer.allocateDirect(THUMBNAIL_SIZE * THUMBNAIL_SIZE * 4)
        glReadPixels(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer)
        pixelBuffer.rewind()

        // Create new texture
        val textureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, textureId)

        // Upload pixel data (flip Y because OpenGL textures are bottom-up)
        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            THUMBNAIL_SIZE,
            THUMBNAIL_SIZE,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            pixelBuffer
        )

        // Set texture filtering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

        return textureId
    }

    /**
     * Computes camera position and look-at target based on model bounding box.
     * Auto-frames the model for optimal thumbnail framing.
     */
    private fun computeCameraTransform(model: TexturedModel): Pair<Vector3f, Vector3f> {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        for (meshPart in model.mesh) {
            val vertices = meshPart.vertices
            var i = 0
            while (i < vertices.size) {
                val x = vertices[i]
                val y = vertices[i + 1]
                val z = vertices[i + 2]
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (z < minZ) minZ = z
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                if (z > maxZ) maxZ = z
                i += 3
            }
        }

        // Handle edge case: empty or single-point model
        if (minX > maxX) {
            minX = -0.5f; maxX = 0.5f
            minY = -0.5f; maxY = 0.5f
            minZ = -0.5f; maxZ = 0.5f
        }

        // Center of bounds
        val center = Vector3f(
            (minX + maxX) / 2f,
            (minY + maxY) / 2f,
            (minZ + maxZ) / 2f
        )

        // Size of bounding box
        val size = Vector3f(maxX - minX, maxY - minY, maxZ - minZ)
        val maxDimension = max(size.x, max(size.y, size.z))

        // Camera distance: 2.5x the max dimension (provides good framing with 45° FOV)
        val distance = max(maxDimension * 2.5f, 2.0f)

        // Camera position at 45-degree angle
        val cameraPos = Vector3f(center).add(Vector3f(distance * 0.5f, distance * 0.5f, distance * 0.5f))

        return Pair(cameraPos, center)
    }

    /**
     * Cleans up the FBO resource.
     * Note: Thumbnail textures are owned by ThumbnailCache and deleted there.
     */
    fun destroy() {
        fbo.destroy()
    }
}
