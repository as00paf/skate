package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkinningPerformanceTest {

    private fun component(boneCount: Int = 2): SkeletonComponent {
        val bones = (0 until boneCount).map { i ->
            Bone(i, "b$i", Matrix4f().translation(0f, 1f, 0f)).also {
                // Bind-space position of bone i is (0, i, 0)
                it.inverseBindMatrix.translation(0f, -i.toFloat(), 0f)
            }
        }
        for (i in 1 until bones.size) bones[i - 1].addChild(bones[i])
        return SkeletonComponent(SkeletonPose(Skeleton(bones[0], boneCount)))
    }

    @Test
    fun `UpdateGate_VisibleUpdatesEveryFrame`() {
        val gate = AnimationUpdateGate(0.25f)
        assertEquals(0.016f, gate.consume(true, 0.016f), 1e-6f)
        assertEquals(0.016f, gate.consume(true, 0.016f), 1e-6f)
    }

    @Test
    fun `UpdateGate_OffscreenSkipsThenCatchesUpWithAccumulatedTime`() {
        val gate = AnimationUpdateGate(0.1f)
        assertEquals(0f, gate.consume(false, 0.04f))
        assertEquals(0f, gate.consume(false, 0.04f))
        assertEquals(0.12f, gate.consume(false, 0.04f), 1e-6f)
        assertEquals(0f, gate.consume(false, 0.04f))
    }

    @Test
    fun `UpdateGate_BecomingVisibleFlushesAccumulatedTime`() {
        val gate = AnimationUpdateGate(1f)
        gate.consume(false, 0.3f)
        assertEquals(0.4f, gate.consume(true, 0.1f), 1e-6f)
    }

    @Test
    fun `UpdateGate_ZeroIntervalNeverSkips`() {
        val gate = AnimationUpdateGate(0f)
        assertEquals(0.016f, gate.consume(false, 0.016f), 1e-6f)
    }

    @Test
    fun `FrustumVisibility_CullsSpheresBehindAndBesideCamera`() {
        val projection = Matrix4f().perspective(Math.toRadians(60.0).toFloat(), 1f, 0.1f, 100f)
        val view = Matrix4f() // looking down -Z from the origin
        val vis = FrustumVisibility { projection to view }
        vis.beginFrame()
        assertTrue(vis.isVisible(Vector3f(0f, 0f, -10f), 1f))
        assertFalse(vis.isVisible(Vector3f(0f, 0f, 10f), 1f))
        assertFalse(vis.isVisible(Vector3f(100f, 0f, -10f), 1f))
        // A large radius reaches back into view
        assertTrue(vis.isVisible(Vector3f(30f, 0f, -10f), 40f))
    }

    @Test
    fun `Palette_VersionChangesOnlyWhenMarkedDirty_AndPackIsColumnMajor`() {
        val c = component()
        val v0 = c.paletteVersion
        c.matrixPalette[1].translation(1f, 2f, 3f)
        assertEquals(v0, c.paletteVersion)
        c.markPaletteDirty()
        assertEquals(v0 + 1, c.paletteVersion)

        val buf = c.packedPalette()
        assertEquals(32, buf.limit())
        // column-major translation lives at floats 12..14 of the second matrix
        assertEquals(1f, buf.get(16 + 12))
        assertEquals(2f, buf.get(16 + 13))
        assertEquals(3f, buf.get(16 + 14))
    }

    @Test
    fun `Palette_PackIsCachedUntilDirty`() {
        val c = component()
        val first = c.packedPalette()
        c.matrixPalette[0].translation(9f, 0f, 0f) // changed without marking: stale by design
        assertEquals(0f, c.packedPalette().get(12))
        c.markPaletteDirty()
        assertEquals(9f, c.packedPalette().get(12))
        assertTrue(first === c.packedPalette())
    }

    @Test
    fun `Palette_TruncatesToMaxBones`() {
        val c = component(SkeletonComponent.MAX_BONES + 20)
        assertEquals(SkeletonComponent.MAX_BONES * 16, c.packedPalette().limit())
    }

    @Test
    fun `Bounds_EncloseAllBindPoseBones`() {
        val c = component(5) // bones at y = 0..4
        assertEquals(2f, c.boundsCenter.y, 1e-4f)
        assertTrue(c.boundsRadius >= 2f)
    }
}
