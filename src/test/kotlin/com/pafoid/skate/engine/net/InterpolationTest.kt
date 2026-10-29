package com.pafoid.skate.engine.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterpolationTest {
    private fun state(x: Float, yaw: Float = 0f): IntArray {
        val s = IntArray(ReplicationSchema().fieldCount)
        s[0] = Quantize.toInt(x, TransformReplicator.POS_SCALE)
        s[4] = Quantize.toInt(yaw, TransformReplicator.ROT_SCALE)
        return s
    }

    @Test
    fun Interpolation_BlendsBetweenSamplesAndClampsOutside() {
        val i = SnapshotInterpolator(ReplicationSchema())
        i.add(1, 10, state(0f)); i.add(1, 13, state(3f))
        assertEquals(1.5f, Quantize.toFloat(i.sample(1, 11.5)!![0], TransformReplicator.POS_SCALE), 0.01f)
        assertEquals(0f, Quantize.toFloat(i.sample(1, 5.0)!![0], TransformReplicator.POS_SCALE))
        assertEquals(3f, Quantize.toFloat(i.sample(1, 99.0)!![0], TransformReplicator.POS_SCALE))
        assertNull(i.sample(2, 11.0))
    }

    @Test
    fun Interpolation_RotationTakesShortestPath() {
        val i = SnapshotInterpolator(ReplicationSchema())
        i.add(1, 0, state(0f, 350f)); i.add(1, 2, state(0f, 10f))
        val yaw = Quantize.toFloat(i.sample(1, 1.0)!![4], TransformReplicator.ROT_SCALE)
        assertEquals(360f, yaw, 0.1f)
    }

    @Test
    fun Interpolation_IgnoresOutOfOrderSamples() {
        val i = SnapshotInterpolator(ReplicationSchema())
        i.add(1, 10, state(1f)); i.add(1, 5, state(9f))
        assertEquals(1f, Quantize.toFloat(i.sample(1, 0.0)!![0], TransformReplicator.POS_SCALE))
    }

    @Test
    fun ClientClock_RendersBehindLatestSnapshotWithinBufferedRange() {
        val cfg = NetConfig()
        val c = ClientClock(cfg)
        c.onSnapshot(30)
        repeat(600) { c.advance(1f / 60f) }
        assertTrue(c.renderTick <= 30 + cfg.snapshotIntervalTicks - cfg.interpolationDelayTicks + 0.001)
        c.onSnapshot(33)
        assertEquals(33, c.latestSnapshotTick)
    }
}
