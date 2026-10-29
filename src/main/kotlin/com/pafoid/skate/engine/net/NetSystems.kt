package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemPhase
import kotlin.reflect.KClass

private fun monotonicMs(): Long = java.lang.System.nanoTime() / 1_000_000

/** Runs the server transport and sends snapshots once per fixed step, after physics. */
class NetServerSystem(val server: ReplicationServer) : System() {
    override val phase: SystemPhase get() = SystemPhase.FIXED_UPDATE
    override val runsAfter: List<KClass<out System>> get() = listOf(PhysicsSystem::class)

    override fun fixedUpdate(fixedDt: Float) {
        server.poll(monotonicMs())
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
