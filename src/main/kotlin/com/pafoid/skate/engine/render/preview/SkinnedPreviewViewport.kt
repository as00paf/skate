package com.pafoid.skate.engine.render.preview

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.rhi.ClearValues
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Matrix4f
import org.joml.Vector3f

/** Where the preview camera is: [view] and [projection] matrices and the [eye] position (for lighting). */
class PreviewCamera(val view: Matrix4f, val projection: Matrix4f, val eye: Vector3f)

/** Draws a model in a bone-local pose offscreen and returns the texture id (0: nothing drawn). [SkinnedPreviewViewport]. */
fun interface PosedModelRenderer {
    fun render(widthPx: Int, heightPx: Int, model: `3dModel`, parents: IntArray, locals: Array<Matrix4f>, camera: PreviewCamera): Int
}

/**
 * An offscreen viewport that draws a skinned model in a pose (REN-33): the Animation Editor feeds it the bone-local
 * pose at the playhead, and shows the result with `ImGui.image` under its bone overlay, so the animation plays on the
 * character instead of on skeleton lines. Video to Animation's result opens in the Animation Editor, so it gets it too.
 *
 * - Its own target, resized to the preview area; nothing of the scene's frame is touched.
 * - Its own program ([shader]: a variant of the default mesh shader), so the preview's camera and lighting uniforms
 *   never leak into the scene's draws.
 * - A fixed studio light (sun from the front-left, soft ambient), no shadows, no fog, no sky lighting.
 * - One [SkeletonComponent] per skeleton is kept for the skinning buffer; its palette is filled by [PreviewSkinning],
 *   so the shared skeleton's bones are never written per frame.
 *
 * Main (GL) thread only, like the rest of the editor UI. [render] returns the colour texture's native id for
 * `ImGui.image` (rows bottom-up: use UVs (0,1)-(1,0)), or 0 when there is nothing to draw.
 */
class SkinnedPreviewViewport(
    private val shader: () -> Shader,
    private val modelRenderer: ModelRenderer,
    private val device: () -> RhiDevice = RhiContext::require,
) : PosedModelRenderer {
    private var target: FrameBuffer? = null
    private var skeletonComponent: SkeletonComponent? = null
    private var renderComponent: RenderComponent? = null
    private var world: Array<Matrix4f> = emptyArray()
    private val transform = Transform()
    private val clear = ClearValues(floatArrayOf(0.1f, 0.1f, 0.12f, 1f), 1f)

    /** Size of the current target, or 0 × 0 before the first [render]. */
    var width = 0; private set
    var height = 0; private set

    /** The offscreen target (readback in tests), or null before the first [render]. */
    internal val renderTarget get() = target?.target

    /** Number of times the model was drawn (tests, stats). */
    var draws = 0; private set

    /**
     * Draws [model] posed by [locals] (bone-local matrices, indexed like the skeleton, with [parents] from the clip
     * rig) into a [widthPx] × [heightPx] target seen from [camera]. Returns the texture id, or 0 if the size is empty
     * or the model has no skeleton.
     */
    override fun render(widthPx: Int, heightPx: Int, model: `3dModel`, parents: IntArray, locals: Array<Matrix4f>, camera: PreviewCamera): Int {
        val skeleton = model.skeleton ?: return 0
        if (widthPx <= 0 || heightPx <= 0) return 0
        val fbo = ensureTarget(widthPx, heightPx)

        val skin = skeletonComponent?.takeIf { it.pose.skeleton === skeleton }
            ?: SkeletonComponent(SkeletonPose(skeleton)).also { skeletonComponent = it }
        if (world.size != skin.matrixPalette.size) world = Array(skin.matrixPalette.size) { Matrix4f() }
        PreviewSkinning.palette(skeleton, parents, locals, world, skin.matrixPalette)
        skin.markPaletteDirty()
        val rc = renderComponent?.takeIf { it.model === model }
            ?: RenderComponent(model = model, castShadow = false, receiveShadow = false).also { renderComponent = it }

        val d = device()
        d.beginFrame() // called from the UI, possibly after ImGui drew: start from a clean state, like ThumbnailRenderer
        val cmd = d.commandList()
        cmd.beginPass(fbo.target, clear = clear)
        val program = shader()
        program.start()
        uploadStudio(program, camera)
        modelRenderer.render(transform = transform, renderComponent = rc, shader = program, cameraPosition = camera.eye, skeletonComponent = skin)
        program.stop()
        cmd.endPass()
        draws++
        return fbo.getTextureId()
    }

    private fun ensureTarget(w: Int, h: Int): FrameBuffer {
        val fbo = target ?: FrameBuffer(w, h).also { it.initialize(); target = it }
        if (w != width || h != height) {
            if (width != 0) fbo.resize(w, h)
            width = w
            height = h
        }
        return fbo
    }

    private fun uploadStudio(program: Shader, camera: PreviewCamera) {
        program.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        program.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        program.uploadVec3f(Uniforms.CAMERA_POSITION, camera.eye)
        program.uploadVec3f(Uniforms.SUN_DIRECTION, SUN_DIRECTION)
        program.uploadVec3f(Uniforms.SUN_COLOR, SUN_COLOR)
        program.uploadVec3f(Uniforms.AMBIENT_LIGHT, AMBIENT)
        program.uploadInt(Uniforms.LIGHT_COUNT, 0)
        program.uploadInt(Uniforms.OBJECT_LIGHT_COUNT, 0)
        program.uploadInt(Uniforms.FOG_MODE, 0)
        program.uploadInt(Uniforms.ENV_ENABLED, 0)
        program.uploadFloat(Uniforms.SELECTED, 0f)
        LightingUniformsLoader.disableShadows(program)
    }

    fun destroy() {
        target?.destroy()
        target = null
        width = 0
        height = 0
        skeletonComponent = null
        renderComponent = null
    }

    companion object {
        /** The direction light travels: from the front-left, above. */
        private val SUN_DIRECTION = Vector3f(-0.4f, -0.8f, -0.45f).normalize()
        private val SUN_COLOR = Vector3f(2.2f, 2.1f, 2.0f)
        private val AMBIENT = Vector3f(0.35f, 0.36f, 0.4f)
    }
}
