package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoopbackPredictionSystemTest {
    private val dt = 1f / 60f

    /** Always moves +x at 2 m/s, quantized like a real deterministic step. */
    private val walker = object : PredictionModel {
        override val step = PredictionStep { go, input, step ->
            val t = go.getComponent<Transform>()!!.translation
            t.x = Quantize.toFloat(Quantize.toInt(t.x + input[0] * 2f * step, TransformReplicator.POS_SCALE), TransformReplicator.POS_SCALE)
        }
        override fun demoInput(view: GameObject, tick: Int) = byteArrayOf(1)
    }

    private val origin = Vector3f(10f, 0.5f, -3f)
    private val demo = LoopbackPredictionComponent("test-walker", latencyMs = 100, serverView = "S", predictedView = "P", interpolatedView = "I")
    private val scene = Scene("Demo").apply {
        addChild(GameObject("Session").addComponent(Transform(Vector3f(origin))).addComponent(demo))
        listOf("S", "P", "I").forEach { addChild(GameObject(it).addComponent(Transform())) }
    }
    private val system = LoopbackPredictionSystem().also { it.init(scene) }

    private fun x(name: String) = scene.children.single { it.name == name }.getComponent<Transform>()!!.translation.x - origin.x

    init { PredictionModels.register("test-walker", walker) }

    @Test
    fun LoopbackDemo_MirrorsServerPredictionAndInterpolationRelativeToTheSessionObject() {
        scene.isRunning = true
        repeat(180) { system.fixedUpdate(dt) }

        val server = x("S")
        assertTrue(server > 2f, "server at $server")
        // The owner sees its newest inputs at once: ahead of the server by the inputs still in flight (100 ms at 2 m/s).
        val lead = x("P") - server
        assertTrue(lead in 0f..0.3f, "prediction lead $lead")
        assertTrue(server - x("I") > 0.3f, "interpolated copy lags: ${server - x("I")}") // RTT + interpolation delay
        assertEquals(origin.y, scene.children.single { it.name == "S" }.getComponent<Transform>()!!.translation.y)
        assertEquals(0, demo.session!!.predicting.predictor!!.correctionCount)
        assertTrue(demo.interpolationLag > 0.3f)
    }

    @Test
    fun LoopbackDemo_LatencyChangeRestartsAndStoppingThePlayEndsTheSession() {
        scene.isRunning = true
        repeat(10) { system.fixedUpdate(dt) }
        val first = assertNotNull(demo.session)

        demo.latencyMs = 50
        system.fixedUpdate(dt)
        assertNotSame(first, demo.session)
        assertEquals(50, demo.session!!.latencyMs)

        scene.isRunning = false
        system.fixedUpdate(dt)
        assertNull(demo.session)
    }

    @Test
    fun LoopbackDemo_UnknownModelDoesNothing() {
        demo.modelId = "missing"
        scene.isRunning = true
        repeat(10) { system.fixedUpdate(dt) }
        assertNull(demo.session)
        assertEquals(0f, x("S") + origin.x)
    }
}
