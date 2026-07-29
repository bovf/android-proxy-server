package com.hect0x7.proxy.core.internal

import com.hect0x7.proxy.core.ProxyConfig
import com.hect0x7.proxy.core.internal.http.HttpProxyInitializer
import com.hect0x7.proxy.core.internal.socks.Socks5ProxyInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.GlobalEventExecutor
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class ProxyRuntime(private val stats: StatsTracker) {
  private val channels: ChannelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE, true)
  private val healthProbes = ConcurrentHashMap<Int, CompletableFuture<Unit>>()
  private var bossGroup: EventLoopGroup? = null
  private var workerGroup: EventLoopGroup? = null

  suspend fun start(config: ProxyConfig) {
    val boss = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
    val worker = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
    bossGroup = boss
    workerGroup = worker

    try {
      if (config.httpEnabled) {
        bind("HTTP", config.httpPort, boss, worker, HttpProxyInitializer(channels, stats))
      }
      if (config.socksEnabled) {
        bind("SOCKS", config.socksPort, boss, worker, Socks5ProxyInitializer(channels, stats))
      }
    } catch (error: Throwable) {
      stop()
      throw error
    }
  }

  suspend fun stop() {
    channels.close().awaitCompletion()
    bossGroup?.shutdownGracefully()?.awaitCompletion()
    workerGroup?.shutdownGracefully()?.awaitCompletion()
    bossGroup = null
    workerGroup = null
  }

  private suspend fun bind(
      protocol: String,
      port: Int,
      boss: EventLoopGroup,
      worker: EventLoopGroup,
      protocolInitializer: ChannelInitializer<SocketChannel>,
  ) {
    val bindFuture =
        ServerBootstrap()
            .group(boss, worker)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.AUTO_READ, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(
                object : ChannelInitializer<SocketChannel>() {
                  override fun initChannel(channel: SocketChannel) {
                    if (completeHealthProbe(channel)) {
                      channels.add(channel)
                      channel.close()
                      return
                    }
                    if (isSelfConnection(channel.localAddress(), channel.remoteAddress())) {
                      stats.localLoopBlocked()
                      channel.close()
                      return
                    }
                    channels.add(channel)
                    channel.pipeline().addLast(ConnectionLifecycleHandler(stats))
                    channel.pipeline().addLast(protocolInitializer)
                  }
                }
            )
            .bind(LISTEN_HOST, port)

    try {
      bindFuture.awaitCompletion()
    } catch (error: Throwable) {
      throw IllegalStateException("$protocol port $port is unavailable or already in use", error)
    }
    channels.add(bindFuture.channel())
    verifyListener(protocol, port)
  }

  private fun completeHealthProbe(channel: SocketChannel): Boolean {
    val remote = channel.remoteAddress() as? InetSocketAddress ?: return false
    if (!remote.address.isLoopbackAddress) return false
    val completion = healthProbes.remove(remote.port) ?: return false
    completion.complete(Unit)
    return true
  }

  private fun verifyListener(protocol: String, port: Int) {
    val completion = CompletableFuture<Unit>()
    Socket().use { socket ->
      socket.bind(InetSocketAddress(LOOPBACK_HOST, 0))
      val sourcePort = socket.localPort
      healthProbes[sourcePort] = completion
      try {
        socket.connect(
            InetSocketAddress(LOOPBACK_HOST, port),
            HEALTH_CHECK_TIMEOUT_MILLIS,
        )
        completion.get(HEALTH_CHECK_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
      } catch (error: Throwable) {
        throw IllegalStateException("$protocol listener self-test failed on port $port", error)
      } finally {
        healthProbes.remove(sourcePort)
      }
    }
  }

  private companion object {
    const val LISTEN_HOST = "0.0.0.0"
    const val LOOPBACK_HOST = "127.0.0.1"
    const val HEALTH_CHECK_TIMEOUT_MILLIS = 2_000
  }
}
