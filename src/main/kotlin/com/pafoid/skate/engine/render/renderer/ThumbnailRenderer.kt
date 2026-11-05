package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.render.rhi.ClearValues
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.utils.ShaderConst
import com.pafoid.skate.engine.utils.toRadiansF
import org.joml.Matrix4f
import org.joml.Vector3f
import java.nio.ByteBuffer
import kotlin.math.max

private const val THUMBNAIL_SIZE = 256

/** Grey background, far depth. */
private val CLEAR = ClearValues(floatArrayOf(0.5f, 0.5f, 0.5f, 1f), 1f)

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
    private val cameraTransform = Transform()
    private val transform = Transform()

    init {
        fbo.initialize()
    }

    /**
     * Renders a thumbnail for the given model.
     * @return A NEW texture holding the thumbnail; the caller owns it and closes it when done.
     */
    fun renderThumbnail(model: `3dModel`): RhiTexture {
        // Off-screen pass on a grey background. Called from the editor UI, possibly after ImGui drew: start from a
        // clean slate like a frame does.
        val device = RhiContext.require()
        device.beginFrame()
        val cmd = device.commandList()
        cmd.beginPass(fbo.target, clear = CLEAR)

        // Auto-frame camera based on model bounds
        val (cameraPos, lookAt) = computeCameraTransform(model)
        cameraTransform.translation.set(cameraPos)
        cameraTransform.lookAt(lookAt)

        // Create temporary GameObject (not added to any Scene)
        transform.translation.set(0f, 0f, 0f)
        transform.rotation.set(0f, 0f, 0f)
        transform.scale.set(1f, 1f, 1f)

        // Upload view/projection matrices
        val projectionMatrix = Matrix4f().perspective(Math.toRadians(45.0).toFloat(), 1.0f, 0.1f, 100f)
        val viewMatrix = Matrix4f()
            .rotate(cameraTransform.rotation.x.toRadiansF(), Vector3f(1f, 0f, 0f))
            .rotate(cameraTransform.rotation.y.toRadiansF(), Vector3f(0f, 1f, 0f))
            .rotate(cameraTransform.rotation.z.toRadiansF(), Vector3f(0f, 0f, 1f))
            .translate(Vector3f(cameraPos).negate())
        shader.uploadMat4f(ShaderConst.Uniforms.PROJECTION_MATRIX, projectionMatrix)
        shader.uploadMat4f(ShaderConst.Uniforms.VIEW_MATRIX, viewMatrix)
        shader.uploadMat4f(ShaderConst.Uniforms.TRANSFORMATION_MATRIX, transform.worldMatrix)

        // Upload basic lighting for thumbnails
        shader.uploadVec3f(ShaderConst.Uniforms.LIGHT_POSITION, Vector3f(5f, 5f, 5f))
        shader.uploadVec3f(ShaderConst.Uniforms.LIGHT_COLOR, Vector3f(2.0f, 2.0f, 2.0f))
        shader.uploadVec3f(ShaderConst.Uniforms.AMBIENT_LIGHT, Vector3f(0.8f, 0.8f, 0.8f))
        LightingUniformsLoader.disableShadows(shader)

        // Delegate actual model rendering to ModelRenderer
        modelRenderer.render(
            transform = transform,
            renderComponent = RenderComponent(model = model, castShadow = false, receiveShadow = false),
            shader = shader,
            cameraPosition = cameraPos
        )
        cmd.endPass()

        // Create a NEW texture and copy the target's content into it
        return createTextureFromFbo(device, fbo)
    }

    /**
     * Creates a new texture and copies the target's color attachment into it through a synchronous readback.
     */
    private fun createTextureFromFbo(device: RhiDevice, fbo: FrameBuffer): RhiTexture {
        // Read pixels from the target into CPU memory (bottom-up rows, as the texture expects)
        val pixelBuffer = ByteBuffer.allocateDirect(THUMBNAIL_SIZE * THUMBNAIL_SIZE * 4)
        device.readPixels(fbo.target, 0, IntRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE), PixelFormat.RGBA8, pixelBuffer)
        pixelBuffer.rewind()

        val texture = device.createTexture(TextureDesc(
            TextureType.TEXTURE_2D, PixelFormat.RGBA8, THUMBNAIL_SIZE, THUMBNAIL_SIZE,
            sampler = SamplerDesc(minFilter = Filter.LINEAR, magFilter = Filter.LINEAR, wrap = Wrap.CLAMP_TO_EDGE),
        ))
        texture.upload(0, 0, pixelBuffer)
        return texture
    }

    /**
     * Computes camera position and look-at target based on model bounding box.
     * Auto-frames the model for optimal thumbnail framing.
     */
    private fun computeCameraTransform(model: `3dModel`): Pair<Vector3f, Vector3f> {
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
