package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent

/**
 * Server-authoritative replication. Drive it from a single thread, once per fixed step: [poll], then
 * [processInputs] (the game steps each client's objects with that input), then [tick].
 * Snapshots are sent every `snapshotIntervalTicks`, delta compressed against the last snapshot each client acked,
 * and tell each client the last input sequence applied for it (client-side prediction, NET-03).
 */
class ReplicationServer(
    private val transport: Transport,
    val schema: ReplicationSchema = ReplicationSchema(),
    val config: NetConfig = NetConfig(),
) : TransportListener {
    private class Slot(val connection: Connection, val clientId: Int) {
        var name = ""
        var ready = false
        var lastAckedTick = 0
        val history = ArrayDeque<Pair<Int, Map<Int, IntArray>>>()
        val inputs = ServerInputQueue()
    }

    private val objects = LinkedHashMap<Int, GameObject>()
    private val slots = HashMap<ConnectionId, Slot>()
    private var nextNetId = 1
    private var nextClientId = 1

    var currentTick = 0
        private set

    var onClientJoined: (clientId: Int, name: String) -> Unit = { _, _ -> }
    var onClientLeft: (clientId: Int) -> Unit = {}

    /** Per-tick states kept for lag compensation ([NetConfig.lagCompensationMs]). */
    val stateHistory = StateHistory(schema, maxOf(1, config.lagCompensationMs * config.tickRate / 1000 + 1))
    private val rewinder = Rewinder(schema)

    val clientCount: Int get() = slots.values.count { it.ready }

    fun start(port: Int) = transport.listen(port)

    fun stop() = transport.close()

    fun poll(nowMs: Long) = transport.poll(this, nowMs)

    /** Starts replicating [go]; returns its network id. [prefabId] tells clients what to instantiate. */
    fun spawn(go: GameObject, prefabId: String, ownerClientId: Int = NetworkIdentity.SERVER_OWNER): Int {
        val id = nextNetId++
        val ident = go.getComponent<NetworkIdentity>() ?: NetworkIdentity().also { go.addComponent(it) }
        ident.netId = id
        ident.ownerClientId = ownerClientId
        ident.prefabId = prefabId
        objects[id] = go
        val msg = spawnMessage(id, go)
        slots.values.filter { it.ready }.forEach { it.connection.send(Channel.RELIABLE_ORDERED, msg) }
        return id
    }

    fun despawn(netId: Int) {
        if (objects.remove(netId) == null) return
        val msg = NetWriter(8).also { it.writeByte(MessageType.DESPAWN); it.writeVarInt(netId) }.toByteArray()
        slots.values.filter { it.ready }.forEach { it.connection.send(Channel.RELIABLE_ORDERED, msg) }
    }

    fun gameObject(netId: Int): GameObject? = objects[netId]

    /** Objects owned by [clientId] (e.g. its player), in net id order. */
    fun ownedBy(clientId: Int): List<GameObject> =
        objects.entries.sortedBy { it.key }.filter { it.value.getComponent<NetworkIdentity>()?.ownerClientId == clientId }.map { it.value }

    /**
     * Applies at most one queued input per connected client: calls [handler] with the client id and the game-defined
     * payload. Clients without a queued input this tick are skipped. Call between [poll] and [tick].
     */
    fun processInputs(handler: (clientId: Int, payload: ByteArray) -> Unit) {
        for (slot in slots.values.sortedBy { it.clientId }) {
            if (!slot.ready) continue
            val cmd = slot.inputs.take() ?: continue
            handler(slot.clientId, cmd.payload)
        }
    }

    /** Last input sequence applied for [clientId], or 0. */
    fun lastProcessedInput(clientId: Int): Int = slotOf(clientId)?.inputs?.lastProcessedSeq ?: 0

    /** Server tick [clientId] was rendering when it sent the input applied last, clamped to the lag compensation window. */
    fun viewTickOf(clientId: Int): Double {
        val view = slotOf(clientId)?.inputs?.viewTick ?: return currentTick.toDouble()
        return view.coerceIn(stateHistory.oldestTick.toDouble(), currentTick.toDouble())
    }

    /**
     * Lag compensation: runs [block] with every object not owned by [clientId] moved back to where that client saw
     * it ([viewTickOf]); Transforms are restored exactly afterwards. Use for hit tests on that client's input.
     */
    fun <T> rewound(clientId: Int, block: () -> T): T = rewinder.rewound(objects, stateHistory, viewTickOf(clientId),
        skip = { _, go -> go.getComponent<NetworkIdentity>()?.ownerClientId == clientId }, block = block)

    private fun slotOf(clientId: Int): Slot? = slots.values.firstOrNull { it.clientId == clientId }

    /** Advances one fixed step and sends snapshots when due. */
    fun tick() {
        currentTick++
        val record = config.lagCompensationMs > 0
        val snapshot = currentTick % config.snapshotIntervalTicks == 0
        if (!record && !snapshot) return
        val states = objects.keys.sorted().associateWith { schema.capture(objects.getValue(it)) }
        if (record) stateHistory.record(currentTick, states)
        if (!snapshot) return
        for (slot in slots.values) if (slot.ready) sendSnapshot(slot, states)
    }

    private fun spawnMessage(netId: Int, go: GameObject): ByteArray {
        val ident = go.getComponent<NetworkIdentity>()!!
        val w = NetWriter(64)
        w.writeByte(MessageType.SPAWN)
        w.writeVarInt(netId)
        w.writeVarInt(ident.ownerClientId)
        w.writeString(ident.prefabId)
        schema.capture(go).forEach { w.writeZigZag(it) }
        return w.toByteArray()
    }

    private fun sendSnapshot(slot: Slot, states: Map<Int, IntArray>) {
        val baseline = slot.history.firstOrNull { it.first == slot.lastAckedTick && slot.lastAckedTick != 0 }
        val deltas = states.mapNotNull { (id, s) -> SnapshotCodec.delta(id, s, baseline?.second?.get(id)) }

        val parts = ArrayList<List<ObjectDelta>>()
        var cur = ArrayList<ObjectDelta>()
        var size = 0
        for (d in deltas) {
            val sz = SnapshotCodec.encodedSize(d)
            if (cur.isNotEmpty() && size + sz > PART_BUDGET) { parts += cur; cur = ArrayList(); size = 0 }
            cur += d; size += sz
        }
        parts += cur // always at least one (possibly empty) part so the client learns the tick

        require(parts.size <= 255) { "Too many snapshot parts" }
        parts.forEachIndexed { i, objs ->
            val w = NetWriter(256)
            w.writeByte(MessageType.SNAPSHOT)
            w.writeVarInt(currentTick)
            w.writeVarInt(baseline?.first ?: 0)
            w.writeVarInt(slot.inputs.lastProcessedSeq)
            w.writeByte(i); w.writeByte(parts.size)
            w.writeVarInt(objs.size)
            objs.forEach { SnapshotCodec.writeObject(w, it) }
            slot.connection.send(Channel.UNRELIABLE_SEQUENCED, w.toByteArray())
        }
        slot.history.addLast(currentTick to states)
        while (slot.history.size > HISTORY) slot.history.removeFirst()
    }

    override fun onConnected(connection: Connection) {
        slots[connection.id] = Slot(connection, nextClientId++)
    }

    override fun onDisconnected(connection: Connection, reason: String) {
        val slot = slots.remove(connection.id) ?: return
        if (slot.ready) onClientLeft(slot.clientId)
    }

    override fun onMessage(connection: Connection, channel: Channel, payload: ByteArray) {
        val slot = slots[connection.id] ?: return
        try {
            val r = NetReader(payload)
            when (r.readByte()) {
                MessageType.HELLO -> onHello(slot, r)
                MessageType.SNAPSHOT_ACK -> if (slot.ready) slot.lastAckedTick = maxOf(slot.lastAckedTick, r.readVarInt())
                MessageType.INPUT -> if (slot.ready) slot.inputs.receive(InputCodec.read(r))
            }
        } catch (_: IllegalArgumentException) {
            // malformed message from a client: ignore
        } catch (_: java.nio.BufferUnderflowException) {
        }
    }

    private fun onHello(slot: Slot, r: NetReader) {
        if (slot.ready) return
        val version = r.readVarInt()
        val name = r.readString()
        if (version != config.protocolVersion) return reject(slot, "protocol mismatch (server ${config.protocolVersion}, client $version)")
        if (clientCount >= config.maxPlayers) return reject(slot, "server full")
        slot.name = name
        slot.ready = true
        val w = NetWriter(16)
        w.writeByte(MessageType.WELCOME)
        w.writeVarInt(slot.clientId); w.writeVarInt(config.tickRate); w.writeVarInt(config.snapshotRate); w.writeVarInt(currentTick)
        slot.connection.send(Channel.RELIABLE_ORDERED, w.toByteArray())
        objects.forEach { (id, go) -> slot.connection.send(Channel.RELIABLE_ORDERED, spawnMessage(id, go)) }
        onClientJoined(slot.clientId, name)
    }

    private fun reject(slot: Slot, reason: String) {
        slot.connection.send(Channel.RELIABLE_ORDERED, NetWriter(32).also { it.writeByte(MessageType.REJECT); it.writeString(reason) }.toByteArray())
    }

    private companion object {
        const val PART_BUDGET = 800
        const val HISTORY = 32
    }
}
