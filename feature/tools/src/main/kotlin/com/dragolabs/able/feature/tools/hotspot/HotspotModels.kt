package com.dragolabs.able.feature.tools.hotspot

/*
 * Pure Kotlin hotspot model, validation and wire format (HOT-001). No Android types so the rules
 * are covered by JVM tests.
 *
 * Validation limits follow the ones Delta (https://github.com/supershadoe/delta, BSD-3-Clause,
 * Copyright 2024, 2025 supershadoe) and Android's SoftApConfiguration.Builder enforce.
 */

enum class HotspotState { ENABLED, ENABLING, DISABLED, DISABLING, FAILED, UNKNOWN;

    companion object {
        /** Maps WifiManager.WIFI_AP_STATE_* values (stable since API 26). */
        fun fromApState(value: Int): HotspotState = when (value) {
            10 -> DISABLING
            11 -> DISABLED
            12 -> ENABLING
            13 -> ENABLED
            14 -> FAILED
            else -> UNKNOWN
        }
    }
}

/** SoftApConfiguration.SECURITY_TYPE_* values. */
enum class HotspotSecurity(val value: Int) {
    OPEN(0),
    WPA2_PSK(1),
    WPA3_SAE_TRANSITION(2),
    WPA3_SAE(3),
    WPA3_OWE_TRANSITION(4),
    WPA3_OWE(5),
    UNKNOWN(-1),
    ;

    val needsPassphrase: Boolean get() = this == WPA2_PSK || this == WPA3_SAE_TRANSITION || this == WPA3_SAE

    companion object {
        fun from(value: Int): HotspotSecurity = entries.firstOrNull { it.value == value } ?: UNKNOWN

        /** Types Able offers for editing. */
        val editable = listOf(WPA2_PSK, WPA3_SAE_TRANSITION, WPA3_SAE, OPEN)
    }
}

/**
 * Frequency band as the user picks it. Values are SoftApConfiguration.BAND_* bit masks:
 * "5 GHz" means 5 GHz preferred with 2.4 GHz fallback, as Android Settings does.
 */
enum class HotspotBand(val mask: Int) {
    BAND_2GHZ(1),
    BAND_5GHZ(1 or 2),
    BAND_6GHZ(1 or 2 or 4),
    DUAL(-2),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromBands(bands: List<Int>): HotspotBand = when {
            bands.size > 1 -> DUAL
            bands.isEmpty() -> UNKNOWN
            bands[0] and 4 != 0 -> BAND_6GHZ
            bands[0] and 2 != 0 -> BAND_5GHZ
            bands[0] and 1 != 0 -> BAND_2GHZ
            else -> UNKNOWN
        }

        val editable = listOf(BAND_2GHZ, BAND_5GHZ, BAND_6GHZ)
    }
}

/** Soft-AP MAC randomization (SoftApConfiguration.RANDOMIZATION_*). Read-only in Able. */
enum class HotspotMacRandomization { NONE, PERSISTENT, NON_PERSISTENT, UNKNOWN;

    companion object {
        fun from(value: Int): HotspotMacRandomization = when (value) {
            0 -> NONE
            1 -> PERSISTENT
            2 -> NON_PERSISTENT
            else -> UNKNOWN
        }
    }
}

data class HotspotConfig(
    val ssid: String,
    val passphrase: String?,
    val security: HotspotSecurity,
    val band: HotspotBand,
    val hidden: Boolean,
    /** 0 means no limit. */
    val maxClients: Int,
    val autoShutdown: Boolean,
    /** -1 or 0 means the system default timeout. */
    val shutdownTimeoutMs: Long,
    val blockedClients: List<String>,
    val macRandomization: HotspotMacRandomization,
)

data class HotspotCapabilities(
    val supports5Ghz: Boolean? = null,
    val supports6Ghz: Boolean? = null,
    val supportsWpa3: Boolean? = null,
    val maxSupportedClients: Int? = null,
    val clientForceDisconnect: Boolean? = null,
)

data class HotspotClient(val macAddress: String, val hostname: String?, val ipAddress: String?)

/** Everything one helper run reports. Null fields were not readable on this device. */
data class HotspotSnapshot(
    val state: HotspotState,
    val config: HotspotConfig?,
    val capabilities: HotspotCapabilities,
    val clients: List<HotspotClient>?,
    val errors: List<String>,
)

enum class HotspotFieldError {
    SSID_EMPTY,
    SSID_TOO_LONG,
    PASSPHRASE_TOO_SHORT,
    PASSPHRASE_TOO_LONG,
    PASSPHRASE_NOT_ASCII,
    BAND_UNSUPPORTED,
    BAND_6GHZ_NEEDS_WPA3,
    MAX_CLIENTS_INVALID,
    TIMEOUT_INVALID,
    MAC_INVALID,
}

/** The fields the user can edit; nulls mean "keep the current value". */
data class HotspotEdit(
    val ssid: String? = null,
    val passphrase: String? = null,
    val security: HotspotSecurity? = null,
    val band: HotspotBand? = null,
    val hidden: Boolean? = null,
    val maxClients: Int? = null,
    val autoShutdown: Boolean? = null,
    val shutdownTimeoutMs: Long? = null,
    val blockedClients: List<String>? = null,
) {
    val isEmpty: Boolean get() = this == HotspotEdit()

    /** Changes that Android only applies when the access point restarts. */
    val needsRestart: Boolean get() = ssid != null || passphrase != null || security != null || band != null || hidden != null

    /** Names of edited fields whose read-back value differs from what was requested. */
    fun unconfirmedFields(readBack: HotspotConfig): List<String> = buildList {
        if (ssid != null && readBack.ssid != ssid) add("ssid")
        if (security != null && readBack.security != security) add("security")
        if (security?.needsPassphrase == true && readBack.passphrase != null && readBack.passphrase != passphrase) add("passphrase")
        if (band != null && readBack.band != band) add("band")
        if (hidden != null && readBack.hidden != hidden) add("hidden")
        if (maxClients != null && readBack.maxClients != maxClients) add("maxClients")
        if (autoShutdown != null && readBack.autoShutdown != autoShutdown) add("autoShutdown")
        if (shutdownTimeoutMs != null && !sameTimeout(readBack.shutdownTimeoutMs, shutdownTimeoutMs)) add("shutdownTimeout")
        if (blockedClients != null && readBack.blockedClients.map { it.lowercase() }.toSet() != blockedClients.map { it.lowercase() }.toSet()) {
            add("blockedClients")
        }
    }

    companion object {
        /** Zero and -1 both mean "system default timeout". */
        fun sameTimeout(a: Long, b: Long): Boolean = a == b || (a <= 0 && b <= 0)

        /** Builds the minimal edit that turns [current] into [wanted]. */
        fun between(current: HotspotConfig, wanted: HotspotConfig): HotspotEdit {
            val securityChanged = wanted.security != current.security
            val passphraseChanged = wanted.security.needsPassphrase && wanted.passphrase != current.passphrase
            // Android sets the passphrase and the security type together.
            val credentialsChanged = securityChanged || passphraseChanged
            return HotspotEdit(
                ssid = wanted.ssid.takeIf { it != current.ssid },
                passphrase = if (credentialsChanged) wanted.passphrase.orEmpty().takeIf { wanted.security.needsPassphrase }.orEmpty() else null,
                security = wanted.security.takeIf { credentialsChanged },
                band = wanted.band.takeIf { it != current.band },
                hidden = wanted.hidden.takeIf { it != current.hidden },
                maxClients = wanted.maxClients.takeIf { it != current.maxClients },
                autoShutdown = wanted.autoShutdown.takeIf { it != current.autoShutdown },
                shutdownTimeoutMs = wanted.shutdownTimeoutMs.takeIf { !sameTimeout(it, current.shutdownTimeoutMs) },
                blockedClients = wanted.blockedClients.takeIf { it.toSet() != current.blockedClients.toSet() },
            )
        }
    }
}

object HotspotValidator {
    const val SSID_MAX_BYTES = 32
    const val PASSPHRASE_MIN = 8
    const val PASSPHRASE_MAX = 63
    const val MAX_CLIENTS_FALLBACK = 255
    private val MAC = Regex("^[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}$")

    fun validateSsid(ssid: String): HotspotFieldError? = when {
        ssid.isEmpty() -> HotspotFieldError.SSID_EMPTY
        ssid.encodeToByteArray().size > SSID_MAX_BYTES -> HotspotFieldError.SSID_TOO_LONG
        else -> null
    }

    fun validatePassphrase(passphrase: String, security: HotspotSecurity): HotspotFieldError? {
        if (!security.needsPassphrase) return null
        return when {
            passphrase.any { it.code !in 0x20..0x7E } -> HotspotFieldError.PASSPHRASE_NOT_ASCII
            passphrase.length < PASSPHRASE_MIN -> HotspotFieldError.PASSPHRASE_TOO_SHORT
            passphrase.length > PASSPHRASE_MAX -> HotspotFieldError.PASSPHRASE_TOO_LONG
            else -> null
        }
    }

    fun validateBand(band: HotspotBand, security: HotspotSecurity, capabilities: HotspotCapabilities): HotspotFieldError? = when (band) {
        HotspotBand.BAND_5GHZ -> HotspotFieldError.BAND_UNSUPPORTED.takeIf { capabilities.supports5Ghz == false }
        HotspotBand.BAND_6GHZ -> when {
            capabilities.supports6Ghz == false -> HotspotFieldError.BAND_UNSUPPORTED
            // Android requires WPA3-SAE (or OWE) on 6 GHz.
            security != HotspotSecurity.WPA3_SAE && security != HotspotSecurity.WPA3_OWE -> HotspotFieldError.BAND_6GHZ_NEEDS_WPA3
            else -> null
        }
        else -> null
    }

    fun validateMaxClients(value: Int, capabilities: HotspotCapabilities): HotspotFieldError? {
        val max = capabilities.maxSupportedClients?.takeIf { it > 0 } ?: MAX_CLIENTS_FALLBACK
        return HotspotFieldError.MAX_CLIENTS_INVALID.takeIf { value < 0 || value > max }
    }

    fun validateTimeout(value: Long): HotspotFieldError? = HotspotFieldError.TIMEOUT_INVALID.takeIf { value < -1 }

    fun isValidMac(mac: String): Boolean = MAC.matches(mac)

    /** All errors for a full configuration, in display order. */
    fun validate(config: HotspotConfig, capabilities: HotspotCapabilities): List<HotspotFieldError> = listOfNotNull(
        validateSsid(config.ssid),
        validatePassphrase(config.passphrase.orEmpty(), config.security),
        validateBand(config.band, config.security, capabilities),
        validateMaxClients(config.maxClients, capabilities),
        validateTimeout(config.shutdownTimeoutMs),
        HotspotFieldError.MAC_INVALID.takeIf { config.blockedClients.any { !isValidMac(it) } },
    )
}

/** Wi-Fi join payload understood by Android and iOS cameras (ZXing "WIFI:" format). */
object WifiQrPayload {
    fun escape(value: String): String = buildString {
        for (c in value) {
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
            append(c)
        }
    }

    /** Returns null when the security type cannot be expressed in the payload. */
    fun build(ssid: String, passphrase: String?, security: HotspotSecurity, hidden: Boolean): String? {
        val type = when (security) {
            HotspotSecurity.OPEN -> "nopass"
            HotspotSecurity.WPA2_PSK, HotspotSecurity.WPA3_SAE_TRANSITION -> "WPA"
            HotspotSecurity.WPA3_SAE -> "SAE"
            else -> return null
        }
        if (security.needsPassphrase && passphrase.isNullOrEmpty()) return null
        return buildString {
            append("WIFI:T:").append(type).append(';')
            append("S:").append(escape(ssid)).append(';')
            if (security.needsPassphrase) append("P:").append(escape(passphrase!!)).append(';')
            if (hidden) append("H:true;")
            append(';')
        }
    }
}

/**
 * Line protocol between [HotspotShellMain] (running as the Shizuku shell user) and the app.
 * Free text (SSID, passphrase, hostnames) is hex-encoded UTF-8 so no quoting can break it.
 */
object HotspotWire {
    fun hex(value: String): String = value.encodeToByteArray().joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun unhex(value: String): String? = runCatching {
        if (value.length % 2 != 0) return null
        ByteArray(value.length / 2) { i -> value.substring(i * 2, i * 2 + 2).toInt(16).toByte() }.decodeToString()
    }.getOrNull()

    /** Arguments for the helper's `set` command; only changed fields are sent. */
    fun editArgs(edit: HotspotEdit): List<String> = buildList {
        edit.ssid?.let { add("ssid=${hex(it)}") }
        if (edit.security != null) {
            add("security=${edit.security.value}")
            add("pass=${hex(edit.passphrase.orEmpty())}")
        }
        edit.band?.takeIf { it.mask > 0 }?.let { add("band=${it.mask}") }
        edit.hidden?.let { add("hidden=$it") }
        edit.maxClients?.let { add("maxclients=$it") }
        edit.autoShutdown?.let { add("autoshutdown=$it") }
        edit.shutdownTimeoutMs?.let { add("timeout=$it") }
        edit.blockedClients?.let { add("blocked=${it.joinToString(",").ifEmpty { "-" }}") }
    }

    fun parse(output: String): HotspotSnapshot {
        val values = mutableMapOf<String, String>()
        val blocked = mutableListOf<String>()
        val clients = mutableListOf<HotspotClient>()
        val bands = mutableListOf<Int>()
        val errors = mutableListOf<String>()
        var clientsRead = false
        output.lineSequence().map { it.trim() }.filter { it.contains('=') }.forEach { line ->
            val key = line.substringBefore('=')
            val value = line.substringAfter('=')
            when (key) {
                "blocked" -> if (value.isNotEmpty()) blocked += value
                "band" -> value.toIntOrNull()?.let { bands += it }
                "clients" -> clientsRead = value == "ok"
                "client" -> {
                    val parts = value.split('|')
                    val mac = parts.getOrNull(0).orEmpty()
                    if (mac.isNotEmpty()) {
                        clients += HotspotClient(
                            macAddress = mac,
                            hostname = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let(::unhex),
                            ipAddress = parts.getOrNull(2)?.takeIf { it.isNotEmpty() },
                        )
                    }
                }
                "error" -> errors += value
                else -> values[key] = value
            }
        }
        val config = values["ssid"]?.let { ssidHex ->
            HotspotConfig(
                ssid = unhex(ssidHex).orEmpty(),
                passphrase = values["pass"]?.takeIf { it.isNotEmpty() }?.let(::unhex),
                security = HotspotSecurity.from(values["security"]?.toIntOrNull() ?: -1),
                band = HotspotBand.fromBands(bands),
                hidden = values["hidden"] == "true",
                maxClients = values["maxclients"]?.toIntOrNull() ?: 0,
                autoShutdown = values["autoshutdown"] == "true",
                shutdownTimeoutMs = values["timeout"]?.toLongOrNull() ?: -1,
                blockedClients = blocked.toList(),
                macRandomization = HotspotMacRandomization.from(values["macrandom"]?.toIntOrNull() ?: -1),
            )
        }
        val features = values["features"]
        return HotspotSnapshot(
            state = HotspotState.fromApState(values["state"]?.toIntOrNull() ?: -1),
            config = config,
            capabilities = HotspotCapabilities(
                supports5Ghz = values["cap5"]?.toBooleanStrictOrNull(),
                supports6Ghz = values["cap6"]?.toBooleanStrictOrNull(),
                supportsWpa3 = values["capwpa3"]?.toBooleanStrictOrNull() ?: features?.let { it.contains("wpa3") },
                maxSupportedClients = values["capmaxclients"]?.toIntOrNull(),
                clientForceDisconnect = values["capdisconnect"]?.toBooleanStrictOrNull(),
            ),
            clients = clients.toList().takeIf { clientsRead },
            errors = errors.toList(),
        )
    }

    /** Result of `start`, `stop` and `set`: `result=<code>` or `error=<reason>`. */
    fun parseResult(output: String): Int? = output.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("result=") }
        ?.substringAfter('=')
        ?.toIntOrNull()

    fun parseErrors(output: String): List<String> = output.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("error=") }
        .map { it.substringAfter('=') }
        .toList()
}
