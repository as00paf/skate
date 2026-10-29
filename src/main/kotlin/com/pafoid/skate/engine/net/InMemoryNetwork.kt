package com.pafoid.skate.engine.net

import java.util.PriorityQueue

/** Process-local network used for listen servers (loopback) and unit tests; never opens sockets. */
class InMemoryNetwork {
    internal class Packet(val deliverAtMs: Long, val order: Long, val from: Any, val data: ByteArray)

    private val endpoints = HashMap<Int, InMemoryTransport>()
    private var nextPort = 40000
    private var order = 0L

    /** Fixed one-way latency applied to every datagram. */
    var latencyMs = 0L

    /** Return true to drop a datagram (simulated loss). */
    var dropFilter: (from: Any, to: Any, data: ByteArray) -> Boolean = { _, _, _ -> false }

    /** Clock used to stamp delivery times; tests advance it. */
    var nowMs = 0L

    fun createTransport(): InMemoryTransport = InMemoryTransport(this, nextPort++)

    internal fun register(port: Int, t: InMemoryTransport) { endpoints[port] = t }
    internal fun unregister(port: Int) { endpoints.remove(port) }

    internal fun deliver(from: Any, to: Any, data: ByteArray) {
        if (dropFilter(from, to, data)) return
        val target = endpoints[to as Int] ?: return
        target.inbox.add(Packet(nowMs + latencyMs, order++, from, data.copyOf()))
    }

    class InMemoryTransport internal constructor(private val net: InMemoryNetwork, private val localPort: Int) : DatagramTransport() {
        internal val inbox = PriorityQueue<Packet>(compareBy({ it.deliverAtMs }, { it.order }))

        init { net.register(localPort, this) }

        override fun openServer(port: Int) {
            net.unregister(localPort)
            net.register(port, this)
            currentPort = port
        }

        private var currentPort = localPort

        override fun openClient(host: String, port: Int): Any = port

        override fun sendRaw(remote: Any, data: ByteArray) = net.deliver(currentPort, remote, data)

        override fun drain(): List<Datagram> {
            val out = ArrayList<Datagram>()
            while (inbox.isNotEmpty() && inbox.peek().deliverAtMs <= net.nowMs) {
                val p = inbox.poll()
                out += Datagram(p.from, p.data)
            }
            return out
        }

        override fun closeSocket() = net.unregister(currentPort)
    }
}
