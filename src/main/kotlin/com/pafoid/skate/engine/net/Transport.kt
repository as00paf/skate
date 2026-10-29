package com.pafoid.skate.engine.net

enum class Channel(val id: Int) {
    /** Delivered exactly once, in order (resent until acked). */
    RELIABLE_ORDERED(0),

    /** Delivered at most once; messages from older packets than one already delivered are dropped. */
    UNRELIABLE_SEQUENCED(1),

    /** Delivered at most once, any order. */
    UNRELIABLE(2);

    companion object {
        fun fromId(id: Int): Channel? = values().firstOrNull { it.id == id }
    }
}

@JvmInline
value class ConnectionId(val value: Int)

interface Connection {
    val id: ConnectionId

    /** Smoothed round-trip time in milliseconds (0 until measured). */
    val rttMs: Float

    /** Queues [payload]; it is sent on the next [Transport.poll]. */
    fun send(channel: Channel, payload: ByteArray)

    fun close(reason: String = "closed")
}

interface TransportListener {
    fun onConnected(connection: Connection)
    fun onDisconnected(connection: Connection, reason: String)
    fun onMessage(connection: Connection, channel: Channel, payload: ByteArray)

    /** A [Transport.connect] attempt gave up (timeout or rejected). */
    fun onConnectFailed(reason: String) {}
}

/**
 * A network endpoint. All callbacks and queued sends happen inside [poll], which the engine calls from
 * a single thread (the ECS PreUpdate/FixedUpdate phases), so game state is never touched from IO threads.
 */
interface Transport {
    /** Starts accepting connections (server side). */
    fun listen(port: Int)

    /** Starts connecting (client side); success is reported through [TransportListener.onConnected]. */
    fun connect(host: String, port: Int)

    /** Processes inbound packets, resends and flushes outbound packets. [nowMs] is a monotonic clock. */
    fun poll(listener: TransportListener, nowMs: Long)

    fun close()

    companion object {
        /** Maximum payload of one message; larger payloads must be split by the caller. */
        const val MAX_PAYLOAD = 1000
    }
}
