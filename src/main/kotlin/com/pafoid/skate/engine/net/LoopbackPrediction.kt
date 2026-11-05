package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemPhase
import com.pafoid.skate.engine.getComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Random

/**
 * A server and two clients in one process over an [InMemoryNetwork] with simulated [latencyMs] (one way) and
 * [lossPercent] packet loss. The first client owns the player and predicts it with [model]; the second only
 * interpolates it, the way every other player sees it. No sockets, no physics: everything runs in [step].
 */
class LoopbackPredictionSession(
    private val model: PredictionModel,
    val latencyMs: Int,
    val lossPercent: Int,
    private val config: NetConfig = NetConfig(),
    seed: Long = 1L,
) {
    private val net = InMemoryNetwork()
    private val dt = 1f / config.tickRate
    private val stepMs = 1000.0 / config.tickRate
    private var clockMs = 0.0
    private var tick = 0

    val server = ReplicationServer(net.createTransport(), config = config)
    val predicting = client(model.step)
    val observing = client(null)

    /** The authoritative player (server side), or null until the predicting client has joined. */
    var serverPlayer: GameObject? = null
        private set

    /** Sequence number of the last input the predicting client sent (0 before its player spawned). */
    var lastInputSeq = 0
        private set

    /** The player as the predicting client shows it (its own, predicted). */
    val predictedPlayer: GameObject? get() = predicting.gameObject(predicting.predictedNetId)

    /** The player as the observing client shows it (remote, interpolated). */
    val interpolatedPlayer: GameObject? get() = serverPlayer?.getComponent<NetworkIdentity>()?.netId?.let(observing::gameObject)

    init {
        val random = Random(seed)
        net.latencyMs = latencyMs.coerceAtLeast(0).toLong()
        net.dropFilter = { _, _, _ -> lossPercent > 0 && random.nextInt(100) < lossPercent }
        server.start(PORT)
        server.onClientJoined = { clientId, _ -> if (serverPlayer == null) serverPlayer = server.run { newPlayer().also { spawn(it, PLAYER_PREFAB, clientId) } } }
        predicting.connect("loopback", PORT, "predicting")
        observing.connect("loopback", PORT, "observing")
    }

    private fun client(step: PredictionStep?) = ReplicationClient(
        net.createTransport(), config = config, spawner = { _, _, _ -> newPlayer() }, prediction = step,
    )

    private fun newPlayer() = GameObject(PLAYER_PREFAB).also {
        it.addComponent(Transform())
        it.addComponent(NetState())
        model.prepare(it)
    }

    /** One fixed network tick: clients send input, the server applies it with the model's step and snapshots. */
    fun step() {
        clockMs += stepMs
        net.nowMs = clockMs.toLong()
        predicting.poll(net.nowMs)
        observing.poll(net.nowMs)
        predictedPlayer?.let { lastInputSeq = predicting.sendInput(model.demoInput(it, tick)) }
        server.poll(net.nowMs)
        server.processInputs { clientId, payload -> server.ownedBy(clientId).forEach { model.step.step(it, payload, dt) } }
        server.tick()
        predicting.poll(net.nowMs); predicting.update(dt)
        observing.poll(net.nowMs); observing.update(dt)
        tick++
    }

    fun close() {
        predicting.disconnect()
        observing.disconnect()
        server.stop()
    }

    companion object {
        const val PORT = 40000
        const val PLAYER_PREFAB = "player"
    }
}

/**
 * Feature Showcase / test bench for client-side prediction (NET-03, GPL-17): in Play, runs a [LoopbackPredictionSession]
 * with the [PredictionModels] entry [modelId] and mirrors its three views of the player onto scene objects, placed
 * relative to this object: the server's authoritative state ([serverView]), the owning client's prediction
 * ([predictedView]) and another client's interpolated copy ([interpolatedView]). Changing [latencyMs] or
 * [lossPercent] in Play restarts the session.
 */
@Serializable
class LoopbackPredictionComponent(
    var modelId: String = "",
    /** One-way simulated latency in milliseconds (round trip is twice this). */
    var latencyMs: Int = 120,
    /** Share of datagrams dropped, 0..90. */
    var lossPercent: Int = 0,
    var serverView: String = "",
    var predictedView: String = "",
    var interpolatedView: String = "",
) : Component() {
    @Transient @kotlin.jvm.Transient var session: LoopbackPredictionSession? = null

    /** Live readouts (inspector): meters from the prediction to the server state, and from the interpolated copy to it. */
    @Transient var predictionError = 0f
    @Transient var interpolationLag = 0f
}

/** Drives every [LoopbackPredictionComponent] while the scene runs; sessions stop with the scene. */
class LoopbackPredictionSystem : System() {
    override val phase: SystemPhase get() = SystemPhase.FIXED_UPDATE

    private val demos = query(LoopbackPredictionComponent::class, Transform::class)

    override fun fixedUpdate(fixedDt: Float) {
        for (go in demos.entities) {
            val demo = go.getComponent<LoopbackPredictionComponent>() ?: continue
            if (!scene.isRunning) { stop(demo); continue }
            val session = sessionFor(demo) ?: continue
            session.step()
            show(go, demo, session)
        }
    }

    private fun sessionFor(demo: LoopbackPredictionComponent): LoopbackPredictionSession? {
        val latency = demo.latencyMs.coerceIn(0, MAX_LATENCY_MS)
        val loss = demo.lossPercent.coerceIn(0, MAX_LOSS_PERCENT)
        demo.session?.let { if (it.latencyMs == latency && it.lossPercent == loss) return it }
        stop(demo)
        val model = PredictionModels[demo.modelId] ?: return null
        return LoopbackPredictionSession(model, latency, loss).also { demo.session = it }
    }

    private fun stop(demo: LoopbackPredictionComponent) {
        demo.session?.close()
        demo.session = null
    }

    private fun show(anchor: GameObject, demo: LoopbackPredictionComponent, session: LoopbackPredictionSession) {
        val origin = anchor.getComponent<Transform>()!!.translation
        val server = session.serverPlayer?.getComponent<Transform>() ?: return
        val predicted = session.predictedPlayer?.getComponent<Transform>()
        val interpolated = session.interpolatedPlayer?.getComponent<Transform>()
        mirror(demo.serverView, server, origin)
        predicted?.let { mirror(demo.predictedView, it, origin) }
        interpolated?.let { mirror(demo.interpolatedView, it, origin) }
        demo.predictionError = predicted?.translation?.distance(server.translation) ?: 0f
        demo.interpolationLag = interpolated?.translation?.distance(server.translation) ?: 0f
    }

    private fun mirror(name: String, from: Transform, origin: org.joml.Vector3f) {
        if (name.isEmpty()) return
        val target = scene.children.firstOrNull { it.name == name }?.getComponent<Transform>() ?: return
        target.translation.set(from.translation).add(origin)
        target.rotation.set(from.rotation)
    }

    override fun destroy() {
        demos.entities.forEach { it.getComponent<LoopbackPredictionComponent>()?.let(::stop) }
        super.destroy()
    }

    companion object {
        const val MAX_LATENCY_MS = 1000
        const val MAX_LOSS_PERCENT = 90
    }
}
