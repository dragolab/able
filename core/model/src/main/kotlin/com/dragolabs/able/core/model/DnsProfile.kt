package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PrivateDnsMode(val systemValue: String) {
    OFF("off"),
    OPPORTUNISTIC("opportunistic"),
    HOSTNAME("hostname")
}

/**
 * Saved Private DNS configuration profile (SYS-004, ADR-023).
 */
@Serializable
data class DnsProfile(
    val id: String,
    val name: String,
    val mode: PrivateDnsMode,
    val hostname: String = "",
    val isDefault: Boolean = false
) {
    companion object {
        val Off = DnsProfile(id = "off", name = "Off", mode = PrivateDnsMode.OFF)
        val Opportunistic = DnsProfile(id = "auto", name = "Automatic", mode = PrivateDnsMode.OPPORTUNISTIC, isDefault = true)
        val Cloudflare = DnsProfile(id = "cloudflare", name = "Cloudflare (1.1.1.1)", mode = PrivateDnsMode.HOSTNAME, hostname = "one.one.one.one")
        val AdGuard = DnsProfile(id = "adguard", name = "AdGuard DNS (Ad Blocking)", mode = PrivateDnsMode.HOSTNAME, hostname = "dns.adguard.com")
        val Google = DnsProfile(id = "google", name = "Google DNS", mode = PrivateDnsMode.HOSTNAME, hostname = "dns.google")
        val Quad9 = DnsProfile(id = "quad9", name = "Quad9 (Malware Blocking)", mode = PrivateDnsMode.HOSTNAME, hostname = "dns.quad9.net")

        fun presets(): List<DnsProfile> = listOf(
            Off,
            Opportunistic,
            Quad9,
            Cloudflare,
            AdGuard,
            Google
        )
    }
}
