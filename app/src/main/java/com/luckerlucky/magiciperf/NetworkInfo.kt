package com.luckerlucky.magiciperf

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Local IPv4 discovery via plain interface enumeration (no permissions
 * needed), mirroring the iOS app's NetworkInfo: the header shows the Wi-Fi
 * and hotspot addresses, the self-target check uses every owned address, and
 * server runs use the preferred address for the copy-ready peer command.
 */
object NetworkInfo {

    data class InterfaceAddr(val name: String, val address: String)

    fun interfaceIPv4Addresses(): List<InterfaceAddr> = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { nif ->
            if (!nif.isUp || nif.isLoopback) emptyList()
            else nif.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .mapNotNull { addr -> addr.hostAddress?.let { InterfaceAddr(nif.name, it) } }
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Wi-Fi (station) address: wlan0 on effectively all Android devices. */
    fun wifiIPv4(): String? =
        interfaceIPv4Addresses().firstOrNull { it.name == "wlan0" }?.address

    /**
     * Hotspot (AP) address when tethering is on. Interface naming varies by
     * vendor (ap0, swlan0, sometimes wlan1); the legacy fixed gateway
     * 192.168.43.1 is matched as a fallback.
     */
    fun hotspotIPv4(): String? {
        val addresses = interfaceIPv4Addresses()
        return addresses.firstOrNull {
            it.name.startsWith("ap") || it.name.startsWith("swlan") || it.name == "wlan1"
        }?.address ?: addresses.firstOrNull { it.address == "192.168.43.1" }?.address
    }

    /** Every IPv4 this device owns — the "server address is this device" check. */
    fun ownIPv4Addresses(): List<String> = interfaceIPv4Addresses().map { it.address }

    /** Address a peer should target when this phone runs the server. */
    fun preferredPeerTargetIP(): String? = hotspotIPv4() ?: wifiIPv4()
}
