package com.hect0x7.proxy.core

import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ProxyServerControllerTest {
  @Test
  fun initialStateIsStopped() {
    val controller = ProxyServerController()

    assertEquals(null, controller.config.value)
    assertEquals(
        ProxyStats(
            running = false,
            activeConnections = 0,
            totalConnections = 0,
            sentBytes = 0,
            receivedBytes = 0,
            lastError = null,
        ),
        controller.stats.value,
    )
  }

  @Test
  fun occupiedSocksPortReportsProtocolAndPort() = runBlocking {
    ServerSocket(0).use { occupiedSocket ->
      val controller = ProxyServerController()
      val error = assertFailsWith<IllegalStateException> {
        controller.start(
            ProxyConfig(
                httpEnabled = false,
                socksEnabled = true,
                socksPort = occupiedSocket.localPort,
            )
        )
      }

      assertContains(error.message.orEmpty(), "SOCKS port ${occupiedSocket.localPort}")
      assertContains(error.message.orEmpty(), "already in use")
      assertEquals(null, controller.config.value)
    }
  }

  @Test
  fun successfulStartPassesListenerSelfTestWithoutRecordingAConnection() = runBlocking {
    val port = ServerSocket(0).use { it.localPort }
    val controller = ProxyServerController()

    try {
      controller.start(ProxyConfig(httpEnabled = false, socksEnabled = true, socksPort = port))

      assertTrue(controller.stats.value.running)
      assertEquals(0, controller.stats.value.activeConnections)
      assertEquals(0, controller.stats.value.totalConnections)
    } finally {
      controller.stop()
    }
  }
}
