package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.ecs.GameObject

/**
 * Client side of replication: spawns/despawns objects through the game supplied [spawner]/[despawner]
 * and drives their replicated components from interpolated snapshots. Drive it from a single thread:
 * [poll] then [update] every frame, and [sendInput] once per fixed step.
 *
 * With a [prediction] step, the object owned by this client is not interpolated: it is predicted from local
 * input and reconciled with each snapshot ([ClientPredictor]).
 */
class ReplicationClient(
    private val transport: Transport,
    val schema: ReplicationSchema = ReplicationSchema(),
    val config: NetConfig = NetConfig(),
    /** Creates the object for a spawn message (prefab lookup is game-defined); null to ignore it. */
    private val spawner: (netId: Int, ownerClientId: Int, prefabId: String) -> GameObject?,
    private val despawner: (GameObject) -> Unit = {},
    prediction: PredictionStep? = null,
) : TransportListener {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, REJECTED }

    private class PendingSnapshot(val baselineTick: Int, val partCount: Int, val ackedInput: Int) {
        val parts = HashMap<Int, List<ObjectDelta>>()
    }

    private var connection: Connection? = null
    private var playerName = "player"
    private val objects = HashMap<Int, GameObject>()
    private val tombstones = HashSet<Int>()
    private val pending = HashMap<Int, PendingSnapshot>()
    private val history = ArrayDeque<Pair<Int, Map<Int, IntArray>>>()
    private val interpolator = SnapshotInterpolator(schema)
    private val clock = ClientClock(config)
    private val unackedInputs = ArrayDeque<InputCommand>()
    private var inputSeq = 0

    /** Prediction for the locally owned object; null when prediction is disabled. */
    val predictor: ClientPredictor? = prediction?.let { ClientPredictor(schema, it, 1f / config.tickRate, maxPending = config.maxPendingInputs) }

    /** Net id of the predicted local object, or 0. */
    var predictedNetId = 0
        private set

    var state = State.DISCONNECTED
        private set
    var failureReason: String? = null
        private set
    var clientId = -1
        private set
    var serverTickRate = config.tickRate
        private set

    val rttMs: Float get() = connection?.rttMs ?: 0f
    val objectCount: Int get() = objects.size
    fun gameObject(netId: Int): GameObject? = objects[netId]

    fun connect(host: String, port: Int, name: String = "player") {
        playerName = name
        state = State.CONNECTING
        failureReason = null
        transport.connect(host, port)
    }

    fun disconnect() {
        predictor?.detach(); predictedNetId = 0
        connection?.close()
        transport.close()
        state = State.DISCONNECTED
    }

    fun poll(nowMs: Long) = transport.poll(this, nowMs)

    /**
     * Sends this fixed step's game input (at most [InputCodec.MAX_INPUT_PAYLOAD] bytes) and, with prediction, applies
     * it to the local object at once. Each message repeats the newest [NetConfig.inputRedundancy] inputs.
     * Returns the input sequence number, or 0 when not connected.
     */
    fun sendInput(payload: ByteArray): Int {
        if (state != State.CONNECTED) return 0
        require(payload.size <= InputCodec.MAX_INPUT_PAYLOAD) { "Input payload ${payload.size} > ${InputCodec.MAX_INPUT_PAYLOAD}" }
        val view16 = (clock.renderTick * 16).toInt().coerceAtLeast(0)
        val cmd = InputCommand(++inputSeq, payload.copyOf(), view16)
        unackedInputs.addLast(cmd)
        while (unackedInputs.size > config.inputRedundancy) unackedInputs.removeFirst()
        val w = NetWriter(64 + payload.size * unackedInputs.size)
        w.writeByte(MessageType.INPUT)
        InputCodec.write(w, view16, unackedInputs.toList())
        connection?.send(Channel.UNRELIABLE_SEQUENCED, w.toByteArray())
        predictor?.predict(cmd)
        return cmd.seq
    }

    /** Advances the interpolation clock and writes interpolated state into the replicated objects. */
    fun update(dt: Float) {
        clock.advance(dt)
        if (clock.latestSnapshotTick == 0) return
        val renderTick = clock.renderTick
        for ((id, go) in objects) if (id != predictedNetId) interpolator.sample(id, renderTick)?.let { schema.apply(go, it) }
        predictor?.render(dt)
    }

    override fun onConnected(connection: Connection) {
        this.connection = connection
        val w = NetWriter(32)
        w.writeByte(MessageType.HELLO); w.writeVarInt(config.protocolVersion); w.writeString(playerName)
        connection.send(Channel.RELIABLE_ORDERED, w.toByteArray())
    }

    override fun onConnectFailed(reason: String) {
        state = State.REJECTED
        failureReason = reason
    }

    override fun onDisconnected(connection: Connection, reason: String) {
        if (state != State.REJECTED) state = State.DISCONNECTED
        if (failureReason == null) failureReason = reason
        this.connection = null
        predictor?.detach(); predictedNetId = 0
        objects.values.forEach(despawner)
        objects.clear()
    }

    override fun onMessage(connection: Connection, channel: Channel, payload: ByteArray) {
        try {
            val r = NetReader(payload)
            when (r.readByte()) {
                MessageType.WELCOME -> {
                    clientId = r.readVarInt()
                    serverTickRate = r.readVarInt()
                    r.readVarInt() // snapshot rate (server decides)
                    r.readVarInt() // server tick at join; the first snapshot sets the clock
                    state = State.CONNECTED
                }
                MessageType.REJECT -> {
                    state = State.REJECTED
                    failureReason = r.readString()
                    connection.close("rejected")
                }
                MessageType.SPAWN -> onSpawn(r)
                MessageType.DESPAWN -> onDespawn(r.readVarInt())
                MessageType.SNAPSHOT -> onSnapshotPart(r, connection)
            }
        } catch (_: IllegalArgumentException) {
        } catch (_: java.nio.BufferUnderflowException) {
        }
    }

    private fun onSpawn(r: NetReader) {
        val netId = r.readVarInt()
        val owner = r.readVarInt()
        val prefab = r.readString()
        val initial = IntArray(schema.fieldCount) { r.readZigZag() }
        if (netId in tombstones || netId in objects) return
        val go = spawner(netId, owner, prefab) ?: return
        objects[netId] = go
        schema.apply(go, initial)
        val p = predictor
        if (p != null && owner == clientId && predictedNetId == 0) {
            predictedNetId = netId
            p.attach(go, initial)
            return
        }
        // Snapshots that arrived before the spawn message already filled history; keep the newest as base sample.
        interpolator.add(netId, maxOf(clock.latestSnapshotTick, 1), initial)
    }

    private fun onDespawn(netId: Int) {
        tombstones += netId
        interpolator.remove(netId)
        if (netId == predictedNetId) { predictor?.detach(); predictedNetId = 0 }
        objects.remove(netId)?.let(despawner)
    }

    private fun onSnapshotPart(r: NetReader, connection: Connection) {
        val tick = r.readVarInt()
        val baselineTick = r.readVarInt()
        val ackedInput = r.readVarInt()
        val part = r.readByte()
        val partCount = r.readByte()
        if (partCount == 0 || part >= partCount || tick <= clock.latestSnapshotTick) return
        val objs = List(r.readVarInt()) { SnapshotCodec.readObject(r, schema.fieldCount) }

        val p = pending.getOrPut(tick) { PendingSnapshot(baselineTick, partCount, ackedInput) }
        p.parts[part] = objs
        if (p.parts.size < p.partCount) return
        pending.keys.removeAll { it <= tick }

        val baseline: Map<Int, IntArray>? = if (p.baselineTick == 0) null else history.firstOrNull { it.first == p.baselineTick }?.second
        if (p.baselineTick != 0 && baseline == null) return // baseline evicted; wait for a full snapshot

        val full = HashMap<Int, IntArray>()
        baseline?.forEach { (id, s) -> if (id !in tombstones) full[id] = s }
        for (d in p.parts.values.flatten()) {
            if (d.netId in tombstones) continue
            full[d.netId] = SnapshotCodec.apply(d, full[d.netId], schema.fieldCount)
        }
        history.addLast(tick to full)
        while (history.size > HISTORY) history.removeFirst()
        for ((id, s) in full) {
            if (id == predictedNetId) predictor?.reconcile(p.ackedInput, s) else interpolator.add(id, tick, s)
        }
        while (unackedInputs.isNotEmpty() && unackedInputs.first().seq <= p.ackedInput) unackedInputs.removeFirst()
        clock.onSnapshot(tick)

        val w = NetWriter(8)
        w.writeByte(MessageType.SNAPSHOT_ACK); w.writeVarInt(tick)
        connection.send(Channel.UNRELIABLE_SEQUENCED, w.toByteArray())
    }

    private companion object { const val HISTORY = 32 }
}
