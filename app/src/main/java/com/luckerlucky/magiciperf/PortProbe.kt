package com.luckerlucky.magiciperf

import java.net.BindException
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Best-effort port precheck for server runs, mirroring the iOS app: only a
 * bind failing with "address in use" counts as taken — every other outcome
 * reports free, and the engine's own bind remains the sole authority.
 *
 * On Android iperf runs as a child process, so a finished run cannot leak a
 * listener into this process; the occupant here is another still-running
 * child or another app.
 */
object PortProbe {

    fun tcpPortInUse(port: Int): Boolean {
        if (port !in 1..65535) return false
        return try {
            ServerSocket().use { socket ->
                socket.reuseAddress = true  // mirror the engine: only a LIVE socket trips this
                socket.bind(InetSocketAddress(port))
            }
            false
        } catch (e: BindException) {
            isAddressInUse(e)
        } catch (_: Exception) {
            false
        }
    }

    fun udpPortInUse(port: Int): Boolean {
        if (port !in 1..65535) return false
        return try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = false  // with reuse on, a second UDP bind can mask a live occupant
                socket.bind(InetSocketAddress(port))
            }
            false
        } catch (e: BindException) {
            isAddressInUse(e)
        } catch (_: Exception) {
            false
        }
    }

    private fun isAddressInUse(e: BindException): Boolean {
        val message = e.message ?: return false
        return message.contains("EADDRINUSE") || message.contains("in use", ignoreCase = true)
    }
}
