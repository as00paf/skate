package com.pafoid.skate.engine.net

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel as NettyChannel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * UDP transport on Netty. Netty threads only enqueue received datagrams; everything else runs inside
 * [poll] on the caller's thread. The reliability layer lives in [DatagramTransport].
 */
class UdpTransport(maxConnections: Int = 16) : DatagramTransport(maxConnections = maxConnections) {
    private val inbound = ConcurrentLinkedQueue<Datagram>()
    private var group: NioEventLoopGroup? = null
    private var channel: NettyChannel? = null

    /** Local bound port (useful when listening on port 0). */
    val localPort: Int get() = (channel?.localAddress() as? InetSocketAddress)?.port ?: -1

    private fun open(bindPort: Int) {
        val g = NioEventLoopGroup(1, java.util.concurrent.ThreadFactory { r -> Thread(r, "skate-net-io").also { it.isDaemon = true } })
        group = g
        channel = Bootstrap().group(g)
            .channel(NioDatagramChannel::class.java)
            .option(ChannelOption.SO_BROADCAST, false)
            .handler(object : SimpleChannelInboundHandler<DatagramPacket>() {
                override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
                    inbound.add(Datagram(msg.sender(), ByteBufUtil.getBytes(msg.content())))
                }
            })
            .bind(bindPort).syncUninterruptibly().channel()
    }

    override fun openServer(port: Int) = open(port)

    override fun openClient(host: String, port: Int): Any {
        open(0)
        return InetSocketAddress(host, port)
    }

    override fun sendRaw(remote: Any, data: ByteArray) {
        channel?.writeAndFlush(DatagramPacket(Unpooled.wrappedBuffer(data), remote as InetSocketAddress))
    }

    override fun drain(): List<Datagram> {
        val out = ArrayList<Datagram>()
        while (true) out += inbound.poll() ?: break
        return out
    }

    override fun closeSocket() {
        channel?.close()?.syncUninterruptibly()
        group?.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS)
        channel = null
        group = null
    }
}
