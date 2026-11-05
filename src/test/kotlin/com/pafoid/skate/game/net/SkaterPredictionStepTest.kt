package com.pafoid.skate.game.net

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.net.ClientPredictor
import com.pafoid.skate.engine.net.InputCommand
import com.pafoid.skate.engine.net.LoopbackPredictionSession
import com.pafoid.skate.engine.net.NetState
import com.pafoid.skate.engine.net.NetworkIdentity
import com.pafoid.skate.engine.net.PredictionModel
import com.pafoid.skate.engine.net.PredictionStep
import com.pafoid.skate.engine.net.ReplicationSchema
import com.pafoid.skate.engine.net.TransformReplicator
import com.pafoid.skate.game.player.SkateControllerConfig
import com.pafoid.skate.game.player.SkateState
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class SkaterPredictionStepTest {

    private val config = SkateControllerConfig()
    private val step = SkaterPredictionStep(config)
    private val dt = 1f / 60f
    private val schema = ReplicationSchema()

    private fun skater() = GameObject("skater").also { it.addComponent(Transform()); it.addComponent(NetState()) }
    private fun GameObject.pos() = getComponent<Transform>()!!.translation

    /** Push, carve both ways, ollie (held), brake and roll: every branch of the step. */
    private val script: List<SkaterNetInput> =
        List(70) { SkaterNetInput(push = true) } +
            List(45) { SkaterNetInput(steer = 0.8f) } +
            List(5) { SkaterNetInput(steer = -0.3f, ollie = true) } +
            List(60) { SkaterNetInput(steer = -1f) } +
            List(30) { SkaterNetInput(steer = 0.4f, push = true, ollie = it % 20 < 2) } +
            List(40) { SkaterNetInput(brake = true) } +
            List(20) { SkaterNetInput() }

    @Test
    fun SkaterNet_ServerAndClientStepToIdenticalStatesFromTheSameInputs() {
        // Server: steps its object in place. Client: ClientPredictor decodes the state into its object before each
        // step and re-captures (quantizes) after it, then replays from an authoritative snapshot halfway.
        val server = skater()
        val client = skater()
        val predictor = ClientPredictor(schema, step, dt)
        predictor.attach(client, schema.capture(server))
        val serverStates = ArrayList<IntArray>()

        script.forEachIndexed { i, input ->
            step.step(server, input.encode(), dt)
            serverStates += schema.capture(server)
            predictor.predict(InputCommand(i + 1, input.encode()))
            assertArrayEquals(serverStates[i], predictor.predictedState, "step ${i + 1}")
        }

        // Reconcile with the server's state at a recent input (the predictor keeps 128 pending): the replay of the
        // inputs after it lands on the same state.
        val acked = script.size - 40
        predictor.reconcile(acked, serverStates[acked - 1])
        assertArrayEquals(serverStates.last(), predictor.predictedState)
        assertEquals(0f, predictor.lastErrorDistance)
        assertEquals(0, predictor.correctionCount)
        assertTrue(script.any { it.ollie } && serverStates.any { it[schema.fieldCount - NetState.SIZE + SkaterPredictionStep.STATE] == SkateState.AIR.ordinal })
    }

    /** Runs the demo rider for [ticks] and returns the server's and the client's state after each input sequence. */
    private fun loopback(latencyMs: Int, lossPercent: Int, ticks: Int): Triple<LoopbackPredictionSession, Map<Int, IntArray>, Map<Int, IntArray>> {
        val serverStates = HashMap<Int, IntArray>()
        val clientStates = HashMap<Int, IntArray>()
        lateinit var session: LoopbackPredictionSession
        val skate = SkaterPredictionModel(config)
        // Same step on both sides; records the server's state after each input it processed.
        val recording = object : PredictionModel {
            override val step = PredictionStep { go, input, dt ->
                skate.step.step(go, input, dt)
                val serverPlayer = session.serverPlayer
                if (go === serverPlayer) {
                    val owner = serverPlayer.getComponent<NetworkIdentity>()!!.ownerClientId
                    serverStates[session.server.lastProcessedInput(owner)] = schema.capture(go)
                }
            }
            override fun demoInput(view: GameObject, tick: Int) = skate.demoInput(view, tick)
        }
        session = LoopbackPredictionSession(recording, latencyMs, lossPercent)
        repeat(ticks) {
            val sentBefore = session.lastInputSeq
            session.step()
            // The prediction after this tick's input (replays during reconcile end on the same input).
            if (session.lastInputSeq != sentBefore) clientStates[session.lastInputSeq] = session.predicting.predictor!!.predictedState!!.copyOf()
        }
        return Triple(session, serverStates, clientStates)
    }

    @Test
    fun SkaterNet_LoopbackClientMatchesTheServerBitForBitUnderLatency() {
        val (session, serverStates, clientStates) = loopback(latencyMs = 120, lossPercent = 0, ticks = 900)

        val common = serverStates.keys intersect clientStates.keys
        assertTrue(common.size > 800, "inputs compared: ${common.size}")
        common.forEach { seq -> assertArrayEquals(serverStates[seq], clientStates[seq], "input $seq") }
        val predictor = session.predicting.predictor!!
        assertEquals(0, predictor.correctionCount)
        assertEquals(0f, predictor.lastErrorDistance)
        assertTrue(session.serverPlayer!!.pos().length() > 0.5f, "server player at ${session.serverPlayer!!.pos()}")
        session.close()
    }

    @Test
    fun SkaterNet_InputsLostOnTheWireAreCorrectedAndThePredictionConverges() {
        // 15 % loss: an input whose three redundant copies are all dropped never reaches the server, so the client
        // mispredicts until the next snapshot. Everything else still matches exactly.
        val (session, serverStates, clientStates) = loopback(latencyMs = 120, lossPercent = 15, ticks = 900)

        val common = (serverStates.keys intersect clientStates.keys).sorted()
        val mismatched = common.filter { !serverStates[it]!!.contentEquals(clientStates[it]!!) }
        assertTrue(common.size > 600, "inputs compared: ${common.size}")
        assertTrue(mismatched.size < common.size / 10, "mismatched ${mismatched.size} of ${common.size}")
        // Converged: the latest compared inputs match again.
        common.takeLast(30).forEach { seq -> assertArrayEquals(serverStates[seq], clientStates[seq], "input $seq") }
        session.close()
    }

    @Test
    fun SkaterNet_PushAcceleratesToMaxSpeedThenRollsToIdle() {
        val go = skater()
        repeat(240) { step.step(go, SkaterNetInput(push = true).encode(), dt) }
        assertEquals(config.push.maxSpeed, SkaterPredictionStep.speed(go), 1e-3f)
        assertEquals(SkateState.RIDE, SkaterPredictionStep.state(go))
        assertEquals(0f, go.pos().x, 1e-3f) // yaw 0 rides along +z
        assertTrue(go.pos().z > 10f)

        repeat(60 * 15) { step.step(go, SkaterNetInput().encode(), dt) }
        assertEquals(0f, SkaterPredictionStep.speed(go))
        assertEquals(SkateState.IDLE, SkaterPredictionStep.state(go))
    }

    @Test
    fun SkaterNet_OllieLiftsOffOnThePressAndLandsOnTheGround() {
        val go = skater()
        repeat(60) { step.step(go, SkaterNetInput(push = true).encode(), dt) }
        var peak = 0f
        var airTicks = 0
        repeat(120) { i ->
            step.step(go, SkaterNetInput(ollie = true).encode(), dt) // held: only the press jumps
            if (SkaterPredictionStep.state(go) == SkateState.AIR) airTicks++
            peak = maxOf(peak, go.pos().y)
            if (i > 0 && SkaterPredictionStep.state(go) != SkateState.AIR) return@repeat
        }
        val takeOff = config.air.ollieImpulse / config.prediction.mass
        assertEquals(takeOff * takeOff / (2 * config.prediction.gravity), peak, 0.05f)
        assertEquals(config.prediction.groundY, go.pos().y)
        assertTrue(airTicks in 40..55, "air ticks $airTicks")
        assertTrue(SkaterPredictionStep.state(go) != SkateState.AIR)
    }

    @Test
    fun SkaterNet_SteeringRightTurnsTheRiderLikeTheStick() {
        val go = skater()
        repeat(60) { step.step(go, SkaterNetInput(push = true).encode(), dt) }
        repeat(30) { step.step(go, SkaterNetInput(steer = 1f).encode(), dt) }
        // Same sign as SkateBehavior: stick right (x > 0) lowers the yaw.
        assertTrue(go.getComponent<Transform>()!!.rotation.y < -10f)
    }

    @Test
    fun SkaterNet_WritesOnlyValuesOnTheReplicationGrid() {
        val go = skater()
        script.forEach { input ->
            step.step(go, input.encode(), dt)
            val state = schema.capture(go)
            val copy = skater().also { schema.apply(it, state) }
            assertEquals(go.pos(), copy.pos())
            assertEquals(go.getComponent<Transform>()!!.rotation, copy.getComponent<Transform>()!!.rotation)
        }
        val yaw = go.getComponent<Transform>()!!.rotation.y
        assertEquals(yaw, Math.round(yaw * TransformReplicator.ROT_SCALE) / TransformReplicator.ROT_SCALE)
    }

    @Test
    fun SkaterNetInput_RoundTripsAndTreatsShortPayloadsAsNoInput() {
        val input = SkaterNetInput(steer = -0.5f, push = true, ollie = true)
        val decoded = SkaterNetInput.decode(input.encode())
        assertEquals(-0.5f, decoded.steer, 1f / 127f)
        assertEquals(input.copy(steer = decoded.steer), decoded)
        assertEquals(SkaterNetInput(), SkaterNetInput.decode(ByteArray(0)))
        assertEquals(2, input.encode().size)
    }

    @Test
    fun SkaterNetDemo_RiderCirclesInsideTheShowcasePad() {
        val session = LoopbackPredictionSession(SkaterPredictionModel(config), latencyMs = 120, lossPercent = 0)
        var maxRadius = 0f
        var airborne = false
        repeat(60 * 20) {
            session.step()
            val p = session.serverPlayer?.pos() ?: return@repeat
            maxRadius = maxOf(maxRadius, sqrt(p.x * p.x + p.z * p.z))
            airborne = airborne || SkaterPredictionStep.state(session.serverPlayer!!) == SkateState.AIR
        }
        // The Netcode pad is 7 x 8 m; the boxes are 0.5 m long.
        assertTrue(maxRadius < 3f, "max radius $maxRadius")
        assertTrue(maxRadius > 1f, "max radius $maxRadius")
        assertTrue(airborne)
        session.close()
    }
}
