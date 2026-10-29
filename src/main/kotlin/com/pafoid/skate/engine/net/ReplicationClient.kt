package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.ecs.GameObject

/**
 * Client side of replication: spawns/despawns objects through the game supplied [spawner]/[despawner]
 * and drives their replicated components from interpolated snapshots. Drive it from a single thread:
 * [poll] then [update] every frame.
 */
class ReplicationClient(
    private val transport: Transport,
    val schema: ReplicationSchema = ReplicationSchema(),
    val config: NetConfig = NetConfig(),
    /** Creates the object for a spawn message (prefab lookup is game-defined); null to ignore it. */
    private val spawner: (netId: Int, ownerClientId: Int, prefabId: String) -> GameObject?,
    private val despawner: (GameObject) -> Unit = {},
) : TransportListener {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, REJECTED }

    private class PendingSnapshot(val baselineTick: Int, val partCount: Int) {
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
        connection?.close()
        transport.close()
        state = State.DISCONNECTED
    }

    fun poll(nowMs: Long) = transport.poll(this, nowMs)

    /** Sends game input to the server (unreliable sequenced; include redundancy in [payload] if needed). */
    fun sendInput(clientTick: Int, payload: ByteArray) {
        if (state != State.CONNECTED) return
        val w = NetWriter(payload.size + 8)
        w.writeByte(MessageType.INPUT); w.writeVarInt(clientTick); w.writeBytes(payload)
        connection?.send(Channel.UNRELIABLE_SEQUENCED, w.toByteArray())
    }

    /** Advances the interpolation clock and writes interpolated state into the replicated objects. */
    fun update(dt: Float) {
        clock.advance(dt)
        if (clock.latestSnapshotTick == 0) return
        val renderTick = clock.renderTick
        for ((id, go) in objects) interpolator.sample(id, renderTick)?.let { schema.apply(go, it) }
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
        // Snapshots that arrived before the spawn message already filled history; keep the newest as base sample.
        interpolator.add(netId, maxOf(clock.latestSnapshotTick, 1), initial)
    }

    private fun onDespawn(netId: Int) {
        tombstones += netId
        interpolator.remove(netId)
        objects.remove(netId)?.let(despawner)
    }

    private fun onSnapshotPart(r: NetReader, connection: Connection) {
        val tick = r.readVarInt()
        val baselineTick = r.readVarInt()
        val part = r.readByte()
        val partCount = r.readByte()
        if (partCount == 0 || part >= partCount || tick <= clock.latestSnapshotTick) return
        val objs = List(r.readVarInt()) { SnapshotCodec.readObject(r, schema.fieldCount) }

        val p = pending.getOrPut(tick) { PendingSnapshot(baselineTick, partCount) }
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
        for ((id, s) in full) interpolator.add(id, tick, s)
        clock.onSnapshot(tick)

        val w = NetWriter(8)
        w.writeByte(MessageType.SNAPSHOT_ACK); w.writeVarInt(tick)
        connection.send(Channel.UNRELIABLE_SEQUENCED, w.toByteArray())
    }

    private companion object { const val HISTORY = 32 }
}
