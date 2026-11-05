package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoopbackPredictionSessionTest {
    /** Walks +x at 2 m/s while the input is 1, quantized like a real deterministic step. */
    private val walker = object : PredictionModel {
        override val step = PredictionStep { go, input, step ->
            val t = go.getComponent<Transform>()!!.translation
            t.x = Quantize.toFloat(Quantize.toInt(t.x + input[0] * 2f * step, TransformReplicator.POS_SCALE), TransformReplicator.POS_SCALE)
        }
        override fun demoInput(view: GameObject, tick: Int) = byteArrayOf(if (tick % 240 < 120) 1 else 0)
    }

    @Test
    fun LoopbackSession_MaxDemoLatencyReplaysWithoutDivergence() {
        val session = LoopbackPredictionSession(walker, latencyMs = LoopbackPredictionSystem.MAX_LATENCY_MS, lossPercent = 0)
        repeat(900) { session.step() }
        val predictor = assertNotNull(session.predicting.predictor)
        assertTrue(predictor.pendingCount >= 120, "in flight: ${predictor.pendingCount}") // the old fixed buffer was 128
        assertEquals(0, predictor.correctionCount, "replays must not diverge (last error ${predictor.lastErrorDistance} m)")
        session.close()
    }

    @Test
    fun Prediction_BufferCoversTheDemoRoundTripWithHeadroomForLossAndQueueing() {
        val cfg = NetConfig()
        val inFlightMs = 2 * LoopbackPredictionSystem.MAX_LATENCY_MS + 1000 / cfg.snapshotRate + 8 * 1000 / cfg.tickRate // RTT + snapshot gap + full server queue
        assertTrue(cfg.maxPredictionMs >= inFlightMs + 500, "buffer ${cfg.maxPredictionMs} ms for $inFlightMs ms in flight")

        val client = ReplicationClient(InMemoryNetwork().createTransport(), config = cfg, spawner = { _, _, _ -> null }, prediction = walker.step)
        val predictor = assertNotNull(client.predictor)
        val go = GameObject("p").also { it.addComponent(Transform()); it.addComponent(NetState()) }
        predictor.attach(go, predictor.schema.capture(go))
        repeat(cfg.maxPendingInputs + 20) { predictor.predict(InputCommand(it + 1, byteArrayOf(1))) }
        assertEquals(cfg.maxPendingInputs, predictor.pendingCount)
        assertEquals(180, cfg.maxPendingInputs)
    }

    @Test
    fun LoopbackSession_PredictionMatchesServerBitForBitOnceInputsAreAcknowledged() {
        val session = LoopbackPredictionSession(walker, latencyMs = 150, lossPercent = 10, seed = 7)
        repeat(600) { session.step() }
        val predictor = session.predicting.predictor!!
        assertEquals(0, predictor.correctionCount)
        session.close()
    }

    @Test
    fun Prediction_HotPathReusesItsStateBuffer() {
        val predictor = ClientPredictor(ReplicationSchema(), walker.step, 1f / 60f)
        val go = GameObject("p").also { it.addComponent(Transform()); it.addComponent(NetState()) }
        predictor.attach(go, predictor.schema.capture(go))
        val buffer = predictor.predictedState
        repeat(10) { predictor.predict(InputCommand(it + 1, byteArrayOf(1))) }
        predictor.reconcile(4, predictor.schema.capture(go))
        predictor.render(1f / 60f)
        assertSame(buffer, predictor.predictedState)
        assertEquals(6, predictor.pendingCount)
    }
}
