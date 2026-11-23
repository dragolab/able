package com.dragolabs.able.feature.tools.hotspot

import android.net.MacAddress
import android.net.wifi.WifiSsid
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Privileged hotspot helper (HOT-001). It is NOT run inside the Able app process: Able starts it
 * through Shizuku as `app_process` with Able's APK on the class path, so it runs with the shell
 * (or root) identity Shizuku provides, outside the app's hidden-API restrictions.
 *
 * The approach follows Delta (https://github.com/supershadoe/delta, BSD-3-Clause, Copyright 2024,
 * 2025 supershadoe): the tethering connector (`ITetheringConnector`) starts and stops Wi-Fi
 * tethering as `com.android.shell`, `IWifiManager.get/setSoftApConfiguration` read and write the
 * soft-AP configuration, and soft-AP/tethering callbacks report capabilities and connected
 * clients. Delta links compile-time hidden-API stubs; Able uses reflection plus small hand-written
 * callback binders instead, so every API that is missing on a given Android build is reported as
 * an `error=` line rather than crashing.
 *
 * Output: one `key=value` per line (see [HotspotWire]); always exits with code 0.
 */
@Keep
object HotspotShellMain {
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val TETHERING_WIFI = 0
    private const val WIFI_MANAGER = "android.net.wifi.IWifiManager"
    private const val TETHERING_CONNECTOR = "android.net.ITetheringConnector"
    private const val SOFT_AP_CALLBACK = "android.net.wifi.ISoftApCallback"
    private const val TETHERING_EVENT_CALLBACK = "android.net.ITetheringEventCallback"
    private const val INT_RESULT_LISTENER = "android.net.IIntResultListener"

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            when (args.firstOrNull()) {
                "state" -> printState()
                "info" -> printInfo()
                "start" -> startTethering()
                "stop" -> stopTethering()
                "set" -> setConfiguration(args.drop(1))
                else -> out("error=usage")
            }
        } catch (t: Throwable) {
            out("error=helper:${describe(t)}")
        }
        System.out.flush()
        exitProcess(0)
    }

    // -----------------------------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------------------------

    private fun printState() = optional("state") { wifi.call("getWifiApEnabledState") }

    private fun printInfo() {
        printState()
        printConfiguration()
        val capabilities = CapabilityProbe()
        val clients = ClientsProbe()
        capabilities.register()
        clients.register()
        capabilities.awaitAndPrint()
        clients.awaitAndPrint()
    }

    private fun printConfiguration() {
        val config = try {
            wifi.call("getSoftApConfiguration") ?: return out("error=config:null")
        } catch (t: Throwable) {
            return out("error=config:${describe(t)}")
        }
        val ssid = runCatching { (config.call("getWifiSsid")?.call("getBytes") as ByteArray?)?.decodeToString() }.getOrNull()
            ?: runCatching { config.call("getSsid") as String? }.getOrNull()
        out("ssid=${HotspotWire.hex(ssid.orEmpty())}")
        optional("pass") { (config.call("getPassphrase") as String?)?.let(HotspotWire::hex) ?: "" }
        optional("security") { config.call("getSecurityType") }
        val bands = runCatching { (config.call("getBands") as IntArray).toList() }.getOrNull()
            ?: runCatching { listOf(config.call("getBand") as Int) }.getOrNull()
        if (bands == null) out("error=bands") else bands.forEach { out("band=$it") }
        optional("hidden") { config.call("isHiddenSsid") }
        optional("maxclients") { config.call("getMaxNumberOfClients") }
        optional("autoshutdown") { config.call("isAutoShutdownEnabled") }
        optional("timeout") { config.call("getShutdownTimeoutMillis") }
        optional("macrandom") { config.call("getMacRandomizationSetting") }
        runCatching { (config.call("getBlockedClientList") as List<*>).forEach { out("blocked=$it") } }
            .onFailure { out("error=blocked:${describe(it)}") }
    }

    private fun startTethering() {
        val parcel = try {
            val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
            val builder = builderClass.getConstructor(Int::class.javaPrimitiveType).newInstance(TETHERING_WIFI)
            builder.call("build")!!.call("getParcel")
        } catch (t: Throwable) {
            return out("error=request:${describe(t)}")
        }
        val listener = ResultListener()
        val sent = attempt(
            { tethering.call("startTethering", parcel, SHELL_PACKAGE, null, listener.proxy) },
            { tethering.call("startTethering", parcel, SHELL_PACKAGE, listener.proxy) },
        )
        if (sent != null) return out("error=start:$sent")
        listener.awaitAndPrint(10)
    }

    private fun stopTethering() {
        val listener = ResultListener()
        val sent = attempt(
            { tethering.call("stopTethering", TETHERING_WIFI, SHELL_PACKAGE, null, listener.proxy) },
            { tethering.call("stopTethering", TETHERING_WIFI, SHELL_PACKAGE, listener.proxy) },
        )
        if (sent != null) return out("error=stop:$sent")
        listener.awaitAndPrint(10)
    }

    private fun setConfiguration(args: List<String>) {
        val values = args.associate { it.substringBefore('=') to it.substringAfter('=') }
        val config = try {
            val current = wifi.call("getSoftApConfiguration")!!
            val builder = Class.forName("android.net.wifi.SoftApConfiguration\$Builder")
                .getConstructor(current.javaClass)
                .newInstance(current)
            values["ssid"]?.let { hex ->
                val bytes = HotspotWire.unhex(hex)!!.encodeToByteArray()
                builder.call("setWifiSsid", WifiSsid.fromBytes(bytes))
            }
            values["security"]?.toInt()?.let { security ->
                val passphrase = HotspotWire.unhex(values["pass"].orEmpty()).orEmpty()
                val needsPassphrase = HotspotSecurity.from(security).needsPassphrase
                builder.call("setPassphrase", if (needsPassphrase) passphrase else null, security)
            }
            values["band"]?.toInt()?.let { builder.call("setBand", it) }
            values["hidden"]?.toBooleanStrict()?.let { builder.call("setHiddenSsid", it) }
            values["maxclients"]?.toInt()?.let { builder.call("setMaxNumberOfClients", it) }
            values["autoshutdown"]?.toBooleanStrict()?.let { builder.call("setAutoShutdownEnabled", it) }
            values["timeout"]?.toLong()?.let { builder.call("setShutdownTimeoutMillis", it) }
            values["blocked"]?.let { list ->
                val macs = if (list == "-") emptyList() else list.split(',').filter { it.isNotBlank() }.map { MacAddress.fromString(it) }
                builder.call("setBlockedClientList", macs)
            }
            builder.call("build")!!
        } catch (t: Throwable) {
            return out("error=invalid:${describe(t)}")
        }
        val valid = runCatching { wifi.call("validateSoftApConfiguration", config) as Boolean }.getOrNull()
        if (valid == false) return out("error=rejected:validate")
        val applied = try {
            wifi.call("setSoftApConfiguration", config, SHELL_PACKAGE) as? Boolean ?: true
        } catch (t: Throwable) {
            return out("error=apply:${describe(t)}")
        }
        out("result=${if (applied) 0 else 1}")
        // Read-back for the caller.
        printState()
        printConfiguration()
    }

    // -----------------------------------------------------------------------------------------
    // Callbacks
    // -----------------------------------------------------------------------------------------

    /** Binder that receives callbacks for an AIDL interface without its generated Stub. */
    private class CallbackBinder(private val descriptor: String, private val onCall: (Int, Parcel) -> Unit) : Binder() {
        init {
            attachInterface(null, descriptor)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code in FIRST_CALL_TRANSACTION..LAST_CALL_TRANSACTION) {
                runCatching {
                    data.enforceInterface(descriptor)
                    onCall(code, data)
                }
                reply?.writeNoException()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    /** Object implementing the AIDL interface [iface] whose `asBinder()` is [binder]. */
    private fun callbackProxy(iface: String, binder: IBinder): Any {
        val type = Class.forName(iface)
        return Proxy.newProxyInstance(HotspotShellMain::class.java.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "asBinder" -> binder
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "AbleCallback($iface)"
                else -> defaultValue(method.returnType)
            }
        }
    }

    private class ResultListener {
        private val latch = CountDownLatch(1)

        @Volatile private var result: Int? = null
        private val code = transactionCode(INT_RESULT_LISTENER, "onResult") ?: IBinder.FIRST_CALL_TRANSACTION
        private val binder = CallbackBinder(INT_RESULT_LISTENER) { c, data ->
            if (c == code) {
                result = data.readInt()
                latch.countDown()
            }
        }
        val proxy: Any = callbackProxy(INT_RESULT_LISTENER, binder)

        fun awaitAndPrint(seconds: Long) {
            if (latch.await(seconds, TimeUnit.SECONDS)) out("result=$result") else out("error=timeout")
        }
    }

    private class CapabilityProbe {
        private val latch = CountDownLatch(1)

        @Volatile private var capability: Any? = null
        private val code = transactionCode(SOFT_AP_CALLBACK, "onCapabilityChanged")
        private val creator = runCatching { creatorOf("android.net.wifi.SoftApCapability") }.getOrNull()
        private val binder = CallbackBinder(SOFT_AP_CALLBACK) { c, data ->
            if (c == code && creator != null) {
                capability = if (data.readInt() != 0) creator.createFromParcel(data) else null
                latch.countDown()
            }
        }
        private val proxy = callbackProxy(SOFT_AP_CALLBACK, binder)
        private var registered = false

        fun register() {
            if (code == null || creator == null) return out("error=capabilities:unsupported")
            val failure = attempt({ wifi.call("registerSoftApCallback", proxy) })
            if (failure != null) out("error=capabilities:$failure") else registered = true
        }

        fun awaitAndPrint() {
            if (!registered) return
            latch.await(2, TimeUnit.SECONDS)
            runCatching { wifi.call("unregisterSoftApCallback", proxy) }
            val cap = capability ?: return out("error=capabilities:timeout")
            fun feature(name: String, fallback: Long): Boolean? = runCatching {
                val bit = runCatching { cap.javaClass.getField(name).getLong(null) }.getOrDefault(fallback)
                cap.call("areFeaturesSupported", bit) as Boolean
            }.getOrNull()
            fun channels(band: Int): Boolean? = runCatching { (cap.call("getSupportedChannelList", band) as IntArray).isNotEmpty() }.getOrNull()
            val band5 = feature("SOFTAP_FEATURE_BAND_5G_SUPPORTED", 64)
            val band6 = feature("SOFTAP_FEATURE_BAND_6G_SUPPORTED", 128)
            band5?.let { out("cap5=${it && channels(2) != false}") }
            band6?.let { out("cap6=${it && channels(4) != false}") }
            feature("SOFTAP_FEATURE_WPA3_SAE", 4)?.let { out("capwpa3=$it") }
            feature("SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT", 2)?.let { out("capdisconnect=$it") }
            runCatching { cap.call("getMaxSupportedClients") }.getOrNull()?.let { out("capmaxclients=$it") }
        }
    }

    private class ClientsProbe {
        private val latch = CountDownLatch(1)

        @Volatile private var clients: List<*>? = null
        private val startedCode = transactionCode(TETHERING_EVENT_CALLBACK, "onCallbackStarted")
        private val startedCreator = runCatching { creatorOf("android.net.TetheringCallbackStartedParcel") }.getOrNull()
        private val binder = CallbackBinder(TETHERING_EVENT_CALLBACK) { c, data ->
            if (c == startedCode && startedCreator != null) {
                val parcel = if (data.readInt() != 0) startedCreator.createFromParcel(data) else null
                clients = parcel?.let { p ->
                    when (val value = p.javaClass.getField("tetheredClients").get(p)) {
                        is List<*> -> value
                        is Array<*> -> value.toList()
                        else -> null
                    }
                }
                latch.countDown()
            }
        }
        private val proxy = callbackProxy(TETHERING_EVENT_CALLBACK, binder)
        private var registered = false

        fun register() {
            if (startedCode == null || startedCreator == null) return out("error=clients:unsupported")
            val failure = attempt({ tethering.call("registerTetheringEventCallback", proxy, SHELL_PACKAGE) })
            if (failure != null) out("error=clients:$failure") else registered = true
        }

        fun awaitAndPrint() {
            if (!registered) return
            latch.await(2, TimeUnit.SECONDS)
            runCatching { tethering.call("unregisterTetheringEventCallback", proxy, SHELL_PACKAGE) }
            val list = clients ?: return out("error=clients:timeout")
            out("clients=ok")
            list.filterNotNull().forEach { client ->
                runCatching {
                    if (client.call("getTetheringType") != TETHERING_WIFI) return@runCatching
                    val mac = client.call("getMacAddress").toString()
                    val addresses = (client.call("getAddresses") as List<*>?).orEmpty().filterNotNull()
                    val hostname = addresses.firstNotNullOfOrNull { runCatching { it.call("getHostname") as String? }.getOrNull() }
                    val ip = addresses.firstNotNullOfOrNull { info ->
                        runCatching { (info.call("getAddress")?.call("getAddress") as java.net.InetAddress?)?.hostAddress }.getOrNull()
                    }
                    out("client=$mac|${hostname?.let(HotspotWire::hex).orEmpty()}|${ip.orEmpty()}")
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Reflection helpers (hidden APIs are reachable here because this is not an app process)
    // -----------------------------------------------------------------------------------------

    private val wifi: Any by lazy { asInterface(WIFI_MANAGER, systemService("wifi")) }
    private val tethering: Any by lazy { asInterface(TETHERING_CONNECTOR, systemService("tethering")) }

    private fun systemService(name: String): IBinder =
        Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name) as IBinder?
            ?: error("service $name unavailable")

    private fun asInterface(iface: String, binder: IBinder): Any =
        Class.forName("$iface\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("$iface unavailable")

    private fun transactionCode(iface: String, method: String): Int? = runCatching {
        Class.forName("$iface\$Stub").getDeclaredField("TRANSACTION_$method").apply { isAccessible = true }.getInt(null)
    }.getOrNull()

    private fun creatorOf(className: String): Parcelable.Creator<*> =
        Class.forName(className).getField("CREATOR").get(null) as Parcelable.Creator<*>

    /** Invokes the first public method named [name] whose parameters accept [args]. */
    private fun Any.call(name: String, vararg args: Any?): Any? {
        val candidates = javaClass.methods.filter { it.name == name && it.parameterTypes.size == args.size && accepts(it, args) }
        if (candidates.isEmpty()) throw NoSuchMethodException("$name/${args.size}")
        try {
            return candidates.first().invoke(this, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun accepts(method: Method, args: Array<out Any?>): Boolean = method.parameterTypes.withIndex().all { (i, type) ->
        val arg = args[i]
        when {
            arg == null -> !type.isPrimitive
            type.isPrimitive -> boxed(type)?.isInstance(arg) == true
            else -> type.isInstance(arg)
        }
    }

    private fun boxed(type: Class<*>): Class<*>? = when (type) {
        Int::class.javaPrimitiveType -> Int::class.javaObjectType
        Long::class.javaPrimitiveType -> Long::class.javaObjectType
        Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
        else -> null
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        else -> null
    }

    /** Runs the first variant that exists; returns null on success or the last failure reason. */
    private fun attempt(vararg variants: () -> Unit): String? {
        var failure = "unsupported"
        for (variant in variants) {
            try {
                variant()
                return null
            } catch (e: NoSuchMethodException) {
                failure = "unsupported"
            } catch (t: Throwable) {
                return describe(t)
            }
        }
        return failure
    }

    private fun optional(key: String, read: () -> Any?) {
        runCatching(read).onSuccess { out("$key=$it") }.onFailure { out("error=$key:${describe(it)}") }
    }

    private fun describe(t: Throwable): String {
        val cause = (t as? InvocationTargetException)?.targetException ?: t
        return "${cause.javaClass.simpleName}:${cause.message.orEmpty()}".replace('\n', ' ').take(200)
    }

    private fun out(line: String) = println(line)
}
