package com.pafoid.skate.editor.animation.clipeditor

import com.pafoid.skate.engine.animation.edit.ClipRig
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.render.preview.PosedModelRenderer
import com.pafoid.skate.engine.render.preview.PreviewCamera
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-33: the Animation Editor's preview feeds the pose at the playhead to the offscreen skinned-mesh renderer. */
class SkinnedPosePreviewTest {
    private val skeleton = Skeleton(Bone(0, "Hips").also { it.addChild(Bone(1, "Spine")) }, 2)
    private val rig = ClipRig(skeleton)
    private val locals = Array(2) { Matrix4f().translation(0f, it.toFloat(), 0f) }
    private val camera = SkeletonPreview().apply { yaw = 40f; distance = 4f }

    private class Call(val w: Int, val h: Int, val model: `3dModel`, val parents: IntArray, val locals: Array<Matrix4f>, val camera: PreviewCamera)
    private val calls = ArrayList<Call>()
    private val renderer = PosedModelRenderer { w, h, model, parents, l, cam -> calls += Call(w, h, model, parents, l, cam); 42 }

    @Test
    fun pose_IsDrawnOnTheRigsMesh_WithTheOverlayCamera() {
        val model = `3dModel`("james.glb", skeleton = skeleton)
        val preview = SkinnedPosePreview({ renderer }, { model })
        assertEquals(42, preview.draw(rig, locals, camera, 640, 360))
        val call = calls.single()
        assertEquals(640, call.w); assertEquals(360, call.h)
        assertSame(model, call.model)
        assertSame(rig.parents, call.parents)
        assertSame(locals, call.locals)
        assertEquals(camera.view(), call.camera.view)
        assertEquals(camera.projection(640f, 360f), call.camera.projection)
        assertEquals(camera.eye(), call.camera.eye)
        // Same camera as the joints drawn on top: viewProjection == projection * view
        assertTrue(Matrix4f(call.camera.projection).mul(call.camera.view).equals(camera.viewProjection(640f, 360f), 1e-5f))
    }

    @Test
    fun withoutAMatchingMesh_TheWindowKeepsTheSkeletonView() {
        assertEquals(0, SkinnedPosePreview({ renderer }, { null }).draw(rig, locals, camera, 640, 360))
        assertEquals(0, SkinnedPosePreview({ renderer }, { `3dModel`("static.obj") }).draw(rig, locals, camera, 640, 360))
        val otherRig = Skeleton(Bone(0, "root"), 1)
        assertEquals(0, SkinnedPosePreview({ renderer }, { `3dModel`("x", skeleton = otherRig) }).draw(rig, locals, camera, 640, 360))
        assertEquals(0, SkinnedPosePreview({ renderer }, { `3dModel`("x", skeleton = skeleton) }).draw(rig, locals, camera, 0, 360))
        assertEquals(0, SkinnedPosePreview({ null }, { `3dModel`("x", skeleton = skeleton) }).draw(rig, locals, camera, 640, 360))
        assertTrue(calls.isEmpty())
    }
}
