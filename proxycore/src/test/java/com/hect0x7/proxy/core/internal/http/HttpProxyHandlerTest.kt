// Modified for the bovf fork: exercise the real forwarding pipeline with bounded local sockets.
package com.hect0x7.proxy.core.internal.http

import com.hect0x7.proxy.core.internal.StatsTracker
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.GlobalEventExecutor
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test

class HttpProxyHandlerTest {
  private val group = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
  private val origins = DefaultChannelGroup(GlobalEventExecutor.INSTANCE, true)
  private val accepted = CompletableFuture<SocketChannel>()
  private var listener: Channel? = null

  // The protocol initializer is tested directly; production's self-connection guard is unchanged.
  private fun client(): Socket {
    listener = ServerBootstrap().group(group).channel(NioServerSocketChannel::class.java)
        .childHandler(object : ChannelInitializer<SocketChannel>() {
          override fun initChannel(channel: SocketChannel) {
            channel.pipeline().addLast(HttpProxyInitializer(origins, StatsTracker()))
            accepted.complete(channel)
          }
        }).bind(InetAddress.getLoopbackAddress(), 0).sync().channel()
    return Socket().apply {
      soTimeout = TIMEOUT_MILLIS
      connect(listener!!.localAddress(), TIMEOUT_MILLIS)
    }
  }

  @After
  fun cleanup() {
    accepted.getNow(null)?.close()?.syncUninterruptibly()
    origins.close().awaitUninterruptibly()
    listener?.close()?.syncUninterruptibly()
    group.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly()
  }

  @Test
  fun stripsAllProxyCredentialsButPreservesOriginAuthorizationAndBody() {
    origin().use { server ->
      val received = CompletableFuture.supplyAsync {
        server.accept().use { socket ->
          socket.soTimeout = TIMEOUT_MILLIS
          val headers = readHeaders(socket.getInputStream())
          val body = socket.getInputStream().readNBytes(4).toString(Charsets.US_ASCII)
          socket.getOutputStream().write(
              "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello".toByteArray())
          headers to body
        }
      }
      client().use { socket ->
        send(socket, "POST http://127.0.0.1:${server.localPort}/path?q=1 HTTP/1.1\r\n" +
            "Host: example.test\r\nProxy-Authorization: Basic synthetic-proxy-secret\r\n" +
            "pRoXy-AuThOrIzAtIoN: Bearer synthetic-other-secret\r\n" +
            "Authorization: Bearer synthetic-origin-token\r\nProxy-Connection: keep-alive\r\n" +
            "Content-Length: 4\r\n\r\nping")
        assertTrue(readHeaders(socket.getInputStream()).startsWith("HTTP/1.1 200 OK\r\n"))
        assertEquals("hello", socket.getInputStream().readNBytes(5).toString(Charsets.US_ASCII))
      }
      val (headers, body) = received.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
      assertTrue(headers.startsWith("POST /path?q=1 HTTP/1.1\r\n"))
      assertFalse(headers.contains("proxy-authorization", ignoreCase = true))
      assertFalse(headers.contains("synthetic-proxy-secret"))
      assertFalse(headers.contains("synthetic-other-secret"))
      assertFalse(headers.contains("proxy-connection", ignoreCase = true))
      assertTrue(headers.contains("Authorization: Bearer synthetic-origin-token", ignoreCase = true))
      assertEquals("ping", body)
    }
  }

  @Test
  fun stripsProxyCredentialsFromChunkedTrailers() {
    origin().use { server ->
      val received = CompletableFuture.supplyAsync {
        server.accept().use { socket ->
          socket.soTimeout = TIMEOUT_MILLIS
          readHeaders(socket.getInputStream())
          val chunks = readHeaders(socket.getInputStream())
          send(socket, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
          chunks
        }
      }
      client().use { socket ->
        send(socket, "POST http://127.0.0.1:${server.localPort}/ HTTP/1.1\r\nHost: example.test\r\n" +
            "Transfer-Encoding: chunked\r\n\r\n4\r\nping\r\n0\r\n" +
            "Proxy-Authorization: Basic synthetic-trailer-secret\r\nX-Test-Trailer: retained\r\n\r\n")
        assertTrue(readHeaders(socket.getInputStream()).startsWith("HTTP/1.1 200 OK\r\n"))
      }
      val chunks = received.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
      assertTrue(chunks.startsWith("4\r\nping\r\n0\r\n"))
      assertFalse(chunks.contains("proxy-authorization", ignoreCase = true))
      assertFalse(chunks.contains("synthetic-trailer-secret"))
      assertTrue(chunks.contains("X-Test-Trailer: retained", ignoreCase = true))
    }
  }

  @Test
  fun connectRelaysOpaqueTlsAndSshBytesWithoutHttpHeaders() {
    origin().use { server ->
      // TLS record-shaped bytes and an SSH banner must pass through unchanged, not HTTP encoding.
      val payload = byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x04, 0x01, 0x00, 0x00, 0x00) +
          "SSH-2.0-synthetic-test\r\n".toByteArray()
      val echoed = CompletableFuture.supplyAsync {
        server.accept().use { socket ->
          socket.soTimeout = TIMEOUT_MILLIS
          val bytes = socket.getInputStream().readNBytes(payload.size)
          socket.getOutputStream().write(bytes)
          bytes
        }
      }
      client().use { socket ->
        send(socket, "CONNECT 127.0.0.1:${server.localPort} HTTP/1.1\r\n" +
            "Host: example.test\r\nProxy-Authorization: Basic synthetic-proxy-secret\r\n\r\n")
        assertTrue(readHeaders(socket.getInputStream()).startsWith("HTTP/1.1 200 Connection Established\r\n"))
        socket.getOutputStream().write(payload)
        assertContentEquals(payload, socket.getInputStream().readNBytes(payload.size))
      }
      assertContentEquals(payload, echoed.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
    }
  }

  @Test
  fun httpsConnectCompletesTlsHandshakeAndForwardsHttp() {
    val directory = Files.createTempDirectory("proxy-tls-test")
    try {
      val keystore = directory.resolve("synthetic.p12")
      val password = "synthetic-test-only".toCharArray()
      val process = ProcessBuilder(
          Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
          "-genkeypair", "-keystore", keystore.toString(), "-storetype", "PKCS12",
          "-storepass:env", "TEST_TLS_PASSWORD", "-keypass:env", "TEST_TLS_PASSWORD",
          "-alias", "test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
          "-dname", "CN=localhost",
      ).redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).apply {
        environment()["TEST_TLS_PASSWORD"] = String(password)
      }.start()
      if (!process.waitFor(20, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        error("Synthetic TLS certificate generation timed out")
      }
      assertEquals(0, process.exitValue())
      val keys = KeyStore.getInstance("PKCS12").apply {
        Files.newInputStream(keystore).use { load(it, password) }
      }
      val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
        init(keys, password)
      }
      val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(keys)
      }
      val tls = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, trust.trustManagers, null) }
      tls.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
        server.soTimeout = TIMEOUT_MILLIS
        val response = CompletableFuture.runAsync {
          server.accept().use { secure ->
            secure.soTimeout = TIMEOUT_MILLIS
            assertTrue(readHeaders(secure.getInputStream()).startsWith("GET /tls HTTP/1.1\r\n"))
            send(secure, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello")
          }
        }
        client().use { socket ->
          send(socket, "CONNECT 127.0.0.1:${server.localPort} HTTP/1.1\r\nHost: example.test\r\n\r\n")
          assertTrue(readHeaders(socket.getInputStream()).startsWith("HTTP/1.1 200 Connection Established\r\n"))
          tls.socketFactory.createSocket(socket, "localhost", server.localPort, true).use { secure ->
            secure.soTimeout = TIMEOUT_MILLIS
            send(secure, "GET /tls HTTP/1.1\r\nHost: localhost\r\n\r\n")
            assertTrue(readHeaders(secure.getInputStream()).startsWith("HTTP/1.1 200 OK\r\n"))
            assertEquals("hello", secure.getInputStream().readNBytes(5).toString(Charsets.US_ASCII))
          }
        }
        response.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
      }
    } finally {
      Files.walk(directory).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
      }
    }
  }

  @Test
  fun responseReadsPauseAndResumeForNonWritableClient() {
    origin().use { server ->
      client().use { socket ->
        send(socket, "GET http://127.0.0.1:${server.localPort}/ HTTP/1.1\r\nHost: example.test\r\n\r\n")
        server.accept().use { upstream ->
          upstream.soTimeout = TIMEOUT_MILLIS
          readHeaders(upstream.getInputStream())
          send(upstream, "HTTP/1.1 200 OK\r\nContent-Length: 4096\r\n\r\n")
          readHeaders(socket.getInputStream())
          val remote = origins.single()
          val downstream = accepted.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
          val read = CountDownLatch(1)
          remote.eventLoop().submit {
            remote.pipeline().addFirst(object : ChannelInboundHandlerAdapter() {
              override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                read.countDown()
                ctx.fireChannelRead(msg)
              }
            })
            downstream.unsafe().outboundBuffer()!!.setUserDefinedWritability(1, false)
          }.sync()
          downstream.eventLoop().submit {}.sync()
          assertFalse(remote.config().isAutoRead, "origin reads must pause")
          val body = ByteArray(4096) { (it % 127).toByte() }
          upstream.getOutputStream().write(body)
          assertFalse(read.await(200, TimeUnit.MILLISECONDS), "paused origin must not read the bounded body")
          downstream.eventLoop().submit {
            downstream.unsafe().outboundBuffer()!!.setUserDefinedWritability(1, true)
          }.sync()
          downstream.eventLoop().submit {}.sync()
          assertTrue(remote.config().isAutoRead, "origin reads must resume")
          assertContentEquals(body, socket.getInputStream().readNBytes(body.size))
          assertTrue(read.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
        }
      }
    }
  }

  @Test
  fun initiallyNonWritableClientKeepsOriginPausedAndDisconnectClosesIt() {
    checkPausedCleanup(fail = false)
  }

  @Test
  fun clientErrorClosesPausedOriginAndClient() {
    checkPausedCleanup(fail = true)
  }

  private fun checkPausedCleanup(fail: Boolean) {
    origin().use { server ->
      client().use { socket ->
        val downstream = accepted.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        downstream.eventLoop().submit {
          downstream.unsafe().outboundBuffer()!!.setUserDefinedWritability(1, false)
        }.sync()
        downstream.eventLoop().submit {}.sync()
        send(socket, "GET http://127.0.0.1:${server.localPort}/ HTTP/1.1\r\nHost: example.test\r\n\r\n")
        server.accept().use { upstream ->
          upstream.soTimeout = TIMEOUT_MILLIS
          readHeaders(upstream.getInputStream())
          val remote = origins.single()
          remote.eventLoop().submit {}.sync()
          assertFalse(remote.config().isAutoRead, "startup must respect existing client backpressure")
          if (fail) {
            downstream.eventLoop().submit {
              downstream.pipeline().fireExceptionCaught(IllegalStateException("synthetic client error"))
            }.sync()
          } else {
            socket.close()
          }
          assertTrue(remote.closeFuture().await(TIMEOUT_MILLIS.toLong()), "paused origin leaked")
          assertTrue(downstream.closeFuture().await(TIMEOUT_MILLIS.toLong()), "client leaked")
          assertEquals(-1, upstream.getInputStream().read())
        }
      }
    }
  }

  private fun origin() = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply {
    soTimeout = TIMEOUT_MILLIS
  }

  private fun send(socket: Socket, text: String) {
    socket.getOutputStream().write(text.toByteArray(Charsets.US_ASCII))
  }

  private fun readHeaders(input: InputStream): String {
    val bytes = StringBuilder()
    while (!bytes.endsWith("\r\n\r\n")) {
      val byte = input.read()
      check(byte != -1 && bytes.length < 8192) { "Missing or oversized HTTP headers" }
      bytes.append(byte.toChar())
    }
    return bytes.toString()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000
  }
}
