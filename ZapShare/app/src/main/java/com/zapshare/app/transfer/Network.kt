package com.zapshare.app.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object NetUtils {
    const val TRANSFER_PORT = 48520
    const val PC_PORT = 8888

    /** (interfaceName, ipv4) for every up, non-loopback interface. */
    fun ipv4s(): List<Pair<String, String>> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni ->
                ni.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .mapNotNull { a -> a.hostAddress?.let { ni.name to it } }
            }
    }.getOrDefault(emptyList())

    private val hotspotPrefixes = listOf("ap", "swlan", "softap", "wlan1", "wigig")

    fun bestLocalIp(preferHotspot: Boolean): String? {
        val all = ipv4s().filterNot { it.first.startsWith("rmnet") || it.first.startsWith("ccmni") || it.first.startsWith("tun") }
        if (preferHotspot) all.firstOrNull { (n, _) -> hotspotPrefixes.any { n.startsWith(it) } }?.let { return it.second }
        all.firstOrNull { it.first.startsWith("wlan") }?.let { return it.second }
        all.firstOrNull { it.first.startsWith("eth") }?.let { return it.second }
        return all.firstOrNull { it.second.startsWith("192.168.") || it.second.startsWith("10.") || it.second.startsWith("172.") }?.second
    }

    /** All LAN-ish IPs; placed in the QR so the sender can try each one. */
    fun candidateIps(): List<String> = ipv4s()
        .filterNot { it.first.startsWith("rmnet") || it.first.startsWith("ccmni") || it.first.startsWith("tun") }
        .map { it.second }

    fun intToIp(i: Int) = "${i and 0xff}.${i shr 8 and 0xff}.${i shr 16 and 0xff}.${i shr 24 and 0xff}"

    fun isOnWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

// ------------------------------------------------------------------------------------------
// Offline hotspot (no internet, no router needed). Uses Android's LocalOnlyHotspot.
// ------------------------------------------------------------------------------------------
data class HotspotCreds(val ssid: String, val pass: String, val security: Int)

class HotspotManager(ctx: Context) {
    private val wifi = ctx.applicationContext.getSystemService(WifiManager::class.java)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    val isActive get() = reservation != null

    @SuppressLint("MissingPermission")
    suspend fun start(): HotspotCreds = suspendCancellableCoroutine { cont ->
        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = res
                    val creds = if (Build.VERSION.SDK_INT >= 30) {
                        val c = res.softApConfiguration
                        @Suppress("DEPRECATION")
                        HotspotCreds(c.ssid ?: "", c.passphrase ?: "", c.securityType)
                    } else {
                        @Suppress("DEPRECATION")
                        val c = res.wifiConfiguration
                        @Suppress("DEPRECATION")
                        HotspotCreds(c?.SSID?.trim('"') ?: "", c?.preSharedKey?.trim('"') ?: "", 1)
                    }
                    if (cont.isActive) cont.resume(creds)
                }

                override fun onFailed(reason: Int) {
                    val msg = when (reason) {
                        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Turn off your personal hotspot and try again"
                        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "Hotspot is disabled on this device"
                        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "No free Wi-Fi channel. Toggle Wi-Fi and retry"
                        else -> "Could not start hotspot. Make sure Wi-Fi and Location are on"
                    }
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(msg))
                }

                override fun onStopped() { reservation = null }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    fun stop() {
        runCatching { reservation?.close() }
        reservation = null
    }
}

// ------------------------------------------------------------------------------------------
// Join the receiver's hotspot from a QR code.
// ------------------------------------------------------------------------------------------
class WifiConnector(private val ctx: Context) {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val wifi = ctx.applicationContext.getSystemService(WifiManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    var network: Network? = null
        private set

    suspend fun connect(ssid: String, pass: String?, security: Int): Network? {
        disconnect()
        return if (Build.VERSION.SDK_INT >= 29) {
            withTimeout(45_000) {
                suspendCancellableCoroutine<Network> { cont ->
                    val b = WifiNetworkSpecifier.Builder().setSsid(ssid)
                    if (!pass.isNullOrEmpty()) {
                        // SoftApConfiguration.SECURITY_TYPE_WPA3_SAE == 3
                        if (security == 3) b.setWpa3Passphrase(pass) else b.setWpa2Passphrase(pass)
                    }
                    val req = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(b.build())
                        .build()
                    val cb = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(n: Network) {
                            network = n
                            cm.bindProcessToNetwork(n)
                            if (cont.isActive) cont.resume(n)
                        }

                        override fun onUnavailable() {
                            if (cont.isActive) cont.resumeWithException(IOException("Could not join \"$ssid\""))
                        }
                    }
                    callback = cb
                    cm.requestNetwork(req, cb)
                }
            }
        } else {
            legacyConnect(ssid, pass)
            null
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun legacyConnect(ssid: String, pass: String?) {
        if (!wifi.isWifiEnabled) wifi.isWifiEnabled = true
        val conf = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            if (pass.isNullOrEmpty()) allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            else preSharedKey = "\"$pass\""
        }
        val id = wifi.addNetwork(conf)
        wifi.disconnect()
        wifi.enableNetwork(id, true)
        wifi.reconnect()
        repeat(40) {
            val info = wifi.connectionInfo
            if (info != null && info.ssid == "\"$ssid\"" && info.ipAddress != 0) return
            delay(500)
        }
        throw IOException("Could not join \"$ssid\"")
    }

    /** The hotspot owner is almost always the gateway / DHCP server of the joined network. */
    fun gatewayIp(): String? {
        val net = network ?: cm.activeNetwork
        val lp = net?.let { cm.getLinkProperties(it) }
        if (lp != null) {
            if (Build.VERSION.SDK_INT >= 30) lp.dhcpServerAddress?.hostAddress?.let { return it }
            lp.routes.firstOrNull { r ->
                val g = r.gateway
                g is Inet4Address && !g.isAnyLocalAddress
            }?.gateway?.hostAddress?.let { return it }
        }
        @Suppress("DEPRECATION")
        val g = wifi.dhcpInfo?.gateway ?: 0
        return if (g != 0) NetUtils.intToIp(g) else null
    }

    fun disconnect() {
        runCatching { cm.bindProcessToNetwork(null) }
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        network = null
    }
}

// ------------------------------------------------------------------------------------------
// Nearby discovery on the same Wi-Fi (mDNS / DNS-SD). Free and built into Android.
// ------------------------------------------------------------------------------------------
data class NearbyDevice(val name: String, val ip: String, val port: Int)

class NsdHelper(ctx: Context) {
    private val nsd = ctx.getSystemService(NsdManager::class.java)
    private var regListener: NsdManager.RegistrationListener? = null

    fun register(name: String, port: Int) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(s: NsdServiceInfo?, e: Int) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo?, e: Int) {}
            override fun onServiceRegistered(s: NsdServiceInfo?) {}
            override fun onServiceUnregistered(s: NsdServiceInfo?) {}
        }
        regListener = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun unregister() {
        regListener?.let { runCatching { nsd.unregisterService(it) } }
        regListener = null
    }

    fun discover(): Flow<List<NearbyDevice>> = callbackFlow {
        val found = ConcurrentHashMap<String, NearbyDevice>()
        val mine = NetUtils.candidateIps().toSet()
        val lock = Mutex()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                launch {
                    lock.withLock {
                        resolve(info)?.let { d ->
                            if (d.ip !in mine) {
                                found[info.serviceName] = d
                                trySend(found.values.toList())
                            }
                        }
                    }
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                trySend(found.values.toList())
            }

            override fun onDiscoveryStarted(t: String?) {}
            override fun onDiscoveryStopped(t: String?) {}
            override fun onStartDiscoveryFailed(t: String?, e: Int) {}
            override fun onStopDiscoveryFailed(t: String?, e: Int) {}
        }
        trySend(emptyList())
        runCatching { nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): NearbyDevice? = withTimeoutOrNull(6000) {
        suspendCancellableCoroutine<NearbyDevice?> { cont ->
            runCatching {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo?, e: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(s: NsdServiceInfo) {
                        val ip = s.host?.hostAddress
                        if (cont.isActive) cont.resume(if (ip != null) NearbyDevice(s.serviceName, ip, s.port) else null)
                    }
                })
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    companion object { const val TYPE = "_zapshare._tcp." }
}
