package org.cheburnet.passdpi.store


data class PassDpiVPNOptions(
    val port: Int,
    val dnsIp: String,
    val enableIpV6: Boolean,
)
