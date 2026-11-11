package com.pafoid.skate.editor.animation.clipeditor

import com.pafoid.skate.engine.animation.edit.ClipRig
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.render.preview.PosedModelRenderer
import com.pafoid.skate.engine.render.preview.PreviewCamera
import org.joml.Matrix4f

/**
 * The Animation Editor's character preview (REN-33): the clip's pose at the playhead drawn on the rig's skinned mesh
 * by an offscreen [PosedModelRenderer], with the same orbit camera as the bone overlay ([SkeletonPreview]), so joints
 * drawn on top line up with the mesh. [modelFor] gives the mesh of the current preview rig (the skater, or the
 * selected object); without one, or with a mesh whose skeleton doesn't match the rig, [draw] returns 0 and the window
 * keeps the skeleton-only view.
 */
class SkinnedPosePreview(
    private val renderer: () -> PosedModelRenderer?,
    private val modelFor: () -> `3dModel`?,
) {
    /** Draws the pose; returns the texture id to show (rows bottom-up), or 0 when there is no mesh to draw. */
    fun draw(rig: ClipRig, locals: Array<Matrix4f>, camera: SkeletonPreview, widthPx: Int, heightPx: Int): Int {
        if (widthPx <= 0 || heightPx <= 0) return 0
        val model = modelFor() ?: return 0
        val skeleton = model.skeleton ?: return 0
        if (skeleton.boneCount != rig.boneCount) return 0
        val r = renderer() ?: return 0
        val view = camera.view()
        val projection = camera.projection(widthPx.toFloat(), heightPx.toFloat())
        return r.render(widthPx, heightPx, model, rig.parents, locals, PreviewCamera(view, projection, camera.eye()))
    }
}
