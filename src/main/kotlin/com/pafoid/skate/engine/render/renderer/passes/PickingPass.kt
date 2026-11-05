package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.PickingTexture
import com.pafoid.skate.engine.render.data.Renderable2D
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.PickingRenderer
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.rhi.ClearValues
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.opengl.GLStateTracker
import com.pafoid.skate.engine.utils.EntityIdEncoder
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.lwjgl.opengl.GL30.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL30.glDisable

/**
 * Render pass for object picking (selection/hover detection).
 *
 * Renders all pickable objects to a special texture where pixel color
 * encodes the entity ID. This allows CPU-side identification of which
 * object the mouse is over.
 *
 * The pass renders on demand (REN-22): only in frames after a pick was requested ([requestPick], called by
 * [com.pafoid.skate.engine.render.renderer.Renderer.readPixel]). While the pointer hovers the viewport the editor
 * reads a pixel every frame, so the texture stays one frame old as before; with the pointer elsewhere the pass
 * costs nothing. A read of a stale texture first renders it with [renderNow].
 *
 * ## Technical Details
 *
 * - Objects are rendered with their entity ID encoded as a color value
 * - The CPU reads the pixel under the mouse to determine which object is hovered
 * - Objects marked with [NonPickable] component are excluded from picking
 *
 * @param pickingTexture The texture to render picking IDs to (provides width/height)
 * @param pickingShader3D The shader used for 3D picking rendering
 * @param pickingRenderer The renderer for executing picking draw calls
 * @param renderer2D The 2D renderer for sprite picking
 * @param pickingShader The shader used for 2D picking rendering
 * @param modelRenderer The 3D model renderer for simple rendering
 */
class PickingPass(
    private val pickingTexture: PickingTexture,
    private val pickingShader3D: Shader,
    private val pickingRenderer: PickingRenderer,
    private val renderer2D: Renderer2D,
    private val pickingShader: Shader,
    private val modelRenderer: ModelRenderer,
    private val cameraManager: CameraManager
) : BaseRenderPass() {

    override val name: String = "PickingPass"
    override val description: String = "Renders object IDs for mouse picking and selection"

    /** Frees the GL objects owned by the picking renderer. */
    fun destroy() = pickingRenderer.destroy()

    fun resize(width: Int, height: Int) {
        pickingTexture.resize(width, height)
    }

    private val schedule = PickingSchedule()

    /** True when the picking texture was rendered in the last frame (or just now), i.e. it can be read as is. */
    val isFresh: Boolean get() = schedule.fresh

    /** Asks for the picking texture to be rendered in the next frame. */
    fun requestPick() = schedule.request()

    override fun execute(scene: Scene) {
        if (schedule.onFrame()) render(scene)
    }

    /** Renders the picking texture immediately (outside the render graph), for a read of a stale texture. */
    fun renderNow(scene: Scene) {
        if (!isEnabled) return
        render(scene)
        schedule.onRenderedNow()
    }

    private fun render(scene: Scene) {
        // CRITICAL: Reset state that might have been changed by ImGui or previous passes
        glDisable(GL_SCISSOR_TEST)
        GLStateTracker.resetToDefaults()

        val cmd = RhiContext.require().commandList()
        cmd.beginPass(pickingTexture.target, clear = CLEAR)

        // Render 2D and 3D objects for picking
        renderer2D.bindCamera(cameraManager.camera)
        render2D(scene, pickingShader)
        render3DPicking(scene)
        pickingRenderer.draw()

        cmd.endPass()
    }

    private fun render3DPicking(scene: Scene) {
        val camera = cameraManager.camera

        pickingShader3D.start()
        pickingShader3D.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        pickingShader3D.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)

        forEachPickable(scene) { go ->
            // Same components the geometry pass draws: a disabled render component is not a click target
            val renderComponent = go.getComponent<RenderComponent>()?.takeIf { it.enabled }
            val transform = go.getComponent<Transform>()?.takeIf { it.enabled }
            if (renderComponent != null && transform != null) {
                val skeletonComponent = go.getComponent<SkeletonComponent>()

                pickingShader3D.uploadFloat(Uniforms.ENTITY_ID, EntityIdEncoder.encode(go.uId))
                pickingShader3D.uploadBoolean(Uniforms.USE_BATCH, false)

                // Use ModelRenderer's simple render (no textures/PBR)
                modelRenderer.render(
                    transform = transform,
                    renderComponent = renderComponent,
                    shader = pickingShader3D,
                    skeletonComponent = skeletonComponent,
                    simple = true
                )
            }
        }
        pickingShader3D.stop()
    }

    private fun render2D(scene: Scene, shader: Shader) {
        renderer2D.bindShader(shader)
        renderer2D.bindCamera(cameraManager.camera)

        forEachPickable(scene) { go ->
            go.getComponent<SpriteRenderer>()?.takeIf { it.enabled }?.let { sprite ->
                go.getComponent<Transform>()?.takeIf { it.enabled }?.let {
                    renderer2D.add(Renderable2D(sprite, it.worldMatrix))
                }
            }
        }
        renderer2D.render()
        renderer2D.clear()
    }

    companion object {
        /** Id 0 (nothing) everywhere, far depth. */
        private val CLEAR = ClearValues(floatArrayOf(0f, 0f, 0f, 0f), 1f)

        /**
         * Visits every rendered ([RenderTraversal]), unlocked descendant of [node] at any depth, so models on child
         * objects get their own picking id. Hidden, disabled or locked objects prune their whole subtree;
         * [NonPickable] skips only the object itself.
         */
        fun forEachPickable(node: GameObject, action: (GameObject) -> Unit) {
            for (go in node.children) {
                if (!RenderTraversal.isRendered(go) || go.isLocked) continue
                if (go.getComponent<NonPickable>() == null) action(go)
                forEachPickable(go, action)
            }
        }
    }
}

/**
 * When the picking pass renders (pure bookkeeping, tested without GL): a request made during frame N renders the
 * picking texture in frame N's render graph, so reads in frame N+1 see a fresh texture.
 */
class PickingSchedule {
    private var requested = false

    var fresh = false
        private set

    fun request() {
        requested = true
    }

    /** Called once per render-graph frame; returns true when the pass must render this frame. */
    fun onFrame(): Boolean {
        val render = requested
        requested = false
        fresh = render
        return render
    }

    fun onRenderedNow() {
        fresh = true
    }
}
