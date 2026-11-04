package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemPhase
import kotlin.reflect.KClass

private fun monotonicMs(): Long = java.lang.System.nanoTime() / 1_000_000

/**
 * Runs the server once per fixed step, after physics: receives packets, applies one input per client through
 * [inputHandler] (the same [PredictionStep] the clients predict with, usually), then sends snapshots.
 */
class NetServerSystem(
    val server: ReplicationServer,
    private val inputHandler: (clientId: Int, payload: ByteArray) -> Unit = { _, _ -> },
) : System() {
    override val phase: SystemPhase get() = SystemPhase.FIXED_UPDATE
    override val runsAfter: List<KClass<out System>> get() = listOf(PhysicsSystem::class)

    override fun fixedUpdate(fixedDt: Float) {
        server.poll(monotonicMs())
        server.processInputs(inputHandler)
        server.tick()
    }

    override fun destroy() {
        server.stop()
        super.destroy()
    }
}

/** Receives snapshots and writes interpolated state before the transform/animation systems run. */
class NetClientSystem(val client: ReplicationClient) : System() {
    override val phase: SystemPhase get() = SystemPhase.PRE_UPDATE

    override fun update(dt: Float) {
        client.poll(monotonicMs())
        client.update(dt)
    }

    override fun destroy() {
        client.disconnect()
        super.destroy()
    }
}

/** Samples local input once per fixed step and sends it (predicting the local object when the client has prediction). */
class NetClientInputSystem(val client: ReplicationClient, private val inputProvider: () -> ByteArray?) : System() {
    override val phase: SystemPhase get() = SystemPhase.FIXED_UPDATE

    override fun fixedUpdate(fixedDt: Float) {
        inputProvider()?.let { client.sendInput(it) }
    }
}
