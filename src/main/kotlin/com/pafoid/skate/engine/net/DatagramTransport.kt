package com.pafoid.skate.engine.net

/**
 * Shared connection management for datagram based transports: handshake, per-connection
 * [ReliabilityLayer], timeouts. Subclasses only move raw datagrams.
 *
 * Datagram: `kind:u8` then CONNECT/ACCEPT `salt:i32`, REJECT `reason:string`, DATA `reliability packet`, DISCONNECT.
 * The salt echoed by ACCEPT ties a connect attempt to its answer; DATA from unknown remotes is dropped.
 */
abstract class DatagramTransport(
    private val maxConnections: Int = 16,
    private val timeoutMs: Long = 10_000,
    private val connectRetryMs: Long = 200,
    private val connectTimeoutMs: Long = 5_000,
) : Transport {
    protected class Datagram(val remote: Any, val data: ByteArray)

    private inner class Conn(val remote: Any, override val id: ConnectionId) : Connection {
        val layer = ReliabilityLayer()
        var lastReceiveMs = 0L
        var closed = false
        var closeReason: String? = null
        override val rttMs: Float get() = layer.rttMs
        override fun send(channel: Channel, payload: ByteArray) {
            if (!closed) layer.send(channel, payload)
        }

        override fun close(reason: String) {
            if (closed) return
            closed = true
            closeReason = reason
            sendRaw(remote, byteArrayOf(DISCONNECT.toByte()))
        }
    }

    private val connections = LinkedHashMap<Any, Conn>()
    private var nextId = 1
    private var serverMode = false
    private var pendingRemote: Any? = null
    private var pendingSalt = 0
    private var pendingSinceMs = -1L
    private var lastConnectSendMs = Long.MIN_VALUE

    protected abstract fun openServer(port: Int)
    protected abstract fun openClient(host: String, port: Int): Any
    protected abstract fun sendRaw(remote: Any, data: ByteArray)
    protected abstract fun drain(): List<Datagram>
    protected abstract fun closeSocket()

    override fun listen(port: Int) {
        serverMode = true
        openServer(port)
    }

    override fun connect(host: String, port: Int) {
        serverMode = false
        pendingRemote = openClient(host, port)
        pendingSalt = java.util.concurrent.ThreadLocalRandom.current().nextInt()
        pendingSinceMs = -1L
        lastConnectSendMs = Long.MIN_VALUE
    }

    override fun poll(listener: TransportListener, nowMs: Long) {
        for (d in drain()) handle(d, listener, nowMs)

        val remote = pendingRemote
        if (remote != null) {
            if (pendingSinceMs < 0) pendingSinceMs = nowMs
            if (nowMs - pendingSinceMs > connectTimeoutMs) {
                pendingRemote = null
                listener.onConnectFailed("timeout")
            } else if (lastConnectSendMs == Long.MIN_VALUE || nowMs - lastConnectSendMs >= connectRetryMs) {
                lastConnectSendMs = nowMs
                val w = NetWriter(8)
                w.writeByte(CONNECT); w.writeInt(pendingSalt)
                sendRaw(remote, w.toByteArray())
            }
        }

        for (c in connections.values.toList()) {
            if (c.closed) {
                drop(c, c.closeReason ?: "closed", listener, notifyPeer = false)
            } else if (c.layer.overflowed) {
                c.close("overflow")
                drop(c, "overflow", listener, notifyPeer = false)
            } else if (nowMs - c.lastReceiveMs > timeoutMs) {
                drop(c, "timeout", listener, notifyPeer = true)
            } else {
                for (p in c.layer.flush(nowMs)) sendRaw(c.remote, byteArrayOf(DATA.toByte()) + p)
            }
        }
    }

    private fun drop(c: Conn, reason: String, listener: TransportListener, notifyPeer: Boolean) {
        if (connections.remove(c.remote) == null) return
        if (notifyPeer) sendRaw(c.remote, byteArrayOf(DISCONNECT.toByte()))
        listener.onDisconnected(c, reason)
    }

    private fun handle(d: Datagram, listener: TransportListener, nowMs: Long) {
        if (d.data.isEmpty()) return
        val kind = d.data[0].toInt()
        val conn = connections[d.remote]
        try {
            when (kind) {
                CONNECT -> if (serverMode) onConnectRequest(d, conn, listener, nowMs)
                ACCEPT -> if (!serverMode && d.remote == pendingRemote && NetReader(d.data, 1).readInt() == pendingSalt) {
                    pendingRemote = null
                    val c = newConn(d.remote, nowMs)
                    listener.onConnected(c)
                }
                REJECT -> if (!serverMode && d.remote == pendingRemote) {
                    pendingRemote = null
                    listener.onConnectFailed(NetReader(d.data, 1).readString())
                }
                DATA -> if (conn != null && !conn.closed) {
                    conn.lastReceiveMs = nowMs
                    conn.layer.receive(d.data.copyOfRange(1, d.data.size), nowMs) { ch, payload ->
                        if (!conn.closed) listener.onMessage(conn, ch, payload)
                    }
                }
                DISCONNECT -> if (conn != null) drop(conn, "remote closed", listener, notifyPeer = false)
            }
        } catch (_: IllegalArgumentException) {
        } catch (_: java.nio.BufferUnderflowException) {
        }
    }

    private fun onConnectRequest(d: Datagram, existing: Conn?, listener: TransportListener, nowMs: Long) {
        val salt = NetReader(d.data, 1).readInt()
        val accept = NetWriter(8).also { it.writeByte(ACCEPT); it.writeInt(salt) }.toByteArray()
        if (existing != null) {
            sendRaw(d.remote, accept) // our ACCEPT was lost
            return
        }
        if (connections.size >= maxConnections) {
            sendRaw(d.remote, NetWriter(16).also { it.writeByte(REJECT); it.writeString("server full") }.toByteArray())
            return
        }
        val c = newConn(d.remote, nowMs)
        sendRaw(d.remote, accept)
        listener.onConnected(c)
    }

    private fun newConn(remote: Any, nowMs: Long): Conn =
        Conn(remote, ConnectionId(nextId++)).also { it.lastReceiveMs = nowMs; connections[remote] = it }

    override fun close() {
        connections.values.toList().forEach { sendRaw(it.remote, byteArrayOf(DISCONNECT.toByte())) }
        connections.clear()
        pendingRemote = null
        closeSocket()
    }

    private companion object {
        const val CONNECT = 0
        const val ACCEPT = 1
        const val REJECT = 2
        const val DATA = 3
        const val DISCONNECT = 4
    }
}
