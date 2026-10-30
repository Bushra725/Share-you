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
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LanLink(val network: Network, val ip: String, val name: String)

object NetUtils {
    const val TRANSFER_PORT = 48520
    const val PC_PORT = 8888

    /** (interfaceName, ipv4) for every up, non-loopback interface. */
    fun ipv4s(): List<Pair<String, String>> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni ->
                ni.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .mapNotNull { a -> ipPair(ni.name, a.hostAddress) }
            }
    }.getOrDefault(emptyList())

    /**
     * Address the other device should open.
     * Offline hotspot interfaces are named differently on Samsung, Xiaomi and Pixel
     * (ap0, swlan0, wlan2, …) and often appear a moment after the hotspot starts.
     * Browsers refuse 0.0.0.0, so this never returns that.
     */
    fun bestLocalIp(preferHotspot: Boolean, ctx: Context? = null): String? {
        val pairs = collectIps(ctx, includeHotspotFallbacks = preferHotspot)
        var best: String? = null
        var bestScore = Int.MIN_VALUE
        for ((name, ip) in pairs) {
            if (!usableIp(name, ip)) continue
            val score = ipScore(name, ip, preferHotspot)
            if (score > bestScore) {
                bestScore = score
                best = ip
            }
        }
        if (preferHotspot && best != null && !isHotspotCandidate(best, pairs)) return null
        return best
    }

    /** Addresses already assigned, captured before the hotspot starts. */
    fun assignedIps(): Set<String> = ipv4s().map { it.second }.toSet()

    /**
     * Address a PC on the offline hotspot can open.
     * Ignores addresses the phone already had (Wi-Fi, mobile) and prefers the new soft-AP gateway.
     */
    fun hotspotIp(before: Set<String>, ctx: Context?): String? {
        val pairs = collectIps(ctx, includeHotspotFallbacks = true)
        var best: String? = null
        var bestScore = 0
        for ((name, ip) in pairs) {
            val score = hotspotScore(name, ip, before)
            if (score > bestScore) {
                bestScore = score
                best = ip
            }
        }
        return best
    }

    /**
     * Network whose interface is the soft AP. Binding to any other network hides the page from the PC.
     */
    fun hotspotNetwork(ctx: Context, ip: String): Network? {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.firstOrNull { network ->
            val lp = cm.getLinkProperties(network) ?: return@firstOrNull false
            val name = lp.interfaceName ?: return@firstOrNull false
            val owns = lp.linkAddresses.any { cleanIp(it.address.hostAddress) == ip }
            owns && hotspotIface(name)
        }
    }

    private fun collectIps(ctx: Context?, includeHotspotFallbacks: Boolean): List<Pair<String, String>> {
        val pairs = ArrayList<Pair<String, String>>()
        pairs += ipv4s()
        if (includeHotspotFallbacks) {
            pairs += knownHotspotIfaces()
            pairs += ipsFromIpCommand()
            pairs += ipsFromRoutes()
            pairs += ipsFromFibTrie()
        }
        if (ctx != null) pairs += ipsFromConnectivity(ctx)
        return pairs
    }

    private fun knownHotspotIfaces(): List<Pair<String, String>> = runCatching {
        listOf(
            "ap0", "ap1", "swlan0", "swlan1", "softap0", "softap1",
            "wlan1", "wlan2", "wlan3", "wlan4", "wigig0",
            "ap_br_wlan0", "ap_br_wlan1", "sap0"
        ).flatMap { name ->
            val ni = NetworkInterface.getByName(name) ?: return@flatMap emptyList()
            ni.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .mapNotNull { a -> ipPair(name, a.hostAddress) }
        }
    }.getOrDefault(emptyList())

    private fun ipsFromConnectivity(ctx: Context): List<Pair<String, String>> = runCatching {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
        cm.allNetworks.flatMap { network ->
            val lp = cm.getLinkProperties(network) ?: return@flatMap emptyList()
            val name = lp.interfaceName ?: ""
            lp.linkAddresses.mapNotNull { la ->
                val a = la.address
                if (a is Inet4Address && !a.isLoopbackAddress) ipPair(name, a.hostAddress) else null
            }
        }
    }.getOrDefault(emptyList())

    /** `ip addr` still prints the softap address on devices that hide it from NetworkInterface. */
    private fun ipsFromIpCommand(): List<Pair<String, String>> = runCatching {
        val process = ProcessBuilder("ip", "-4", "addr").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        val out = ArrayList<Pair<String, String>>()
        var iface = ""
        for (line in text.lineSequence()) {
            val named = Regex("""^\d+:\s+(\S+?):""").find(line)
            if (named != null) iface = named.groupValues[1].substringBefore('@')
            val inet = Regex("""\binet\s+(\d+\.\d+\.\d+\.\d+)""").find(line)
            if (inet != null && iface.isNotEmpty()) out += iface to inet.groupValues[1]
        }
        out
    }.getOrDefault(emptyList())

    /** `src` on the soft-AP route is the gateway the PC actually uses. */
    private fun ipsFromRoutes(): List<Pair<String, String>> = runCatching {
        val process = ProcessBuilder("ip", "-4", "route").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        Regex("""\bdev\s+(\S+)\b.*\bsrc\s+(\d+\.\d+\.\d+\.\d+)""")
            .findAll(text)
            .map { it.groupValues[1].substringBefore('@') to it.groupValues[2] }
            .filter { (name, _) -> hotspotIface(name) }
            .toList()
    }.getOrDefault(emptyList())

    private fun ipsFromFibTrie(): List<Pair<String, String>> = runCatching {
        Regex("""\b(\d{1,3}(?:\.\d{1,3}){3})\b""")
            .findAll(File("/proc/net/fib_trie").readText())
            .map { it.groupValues[1] }
            .distinct()
            .filter { it.endsWith(".1") && knownHotspotPrefix(it) }
            .map { "fib" to it }
            .toList()
    }.getOrDefault(emptyList())

    private fun isHotspotCandidate(ip: String, pairs: List<Pair<String, String>>): Boolean =
        pairs.any { (name, candidate) -> candidate == ip && acceptableHotspot(name, candidate) }

    private fun acceptableHotspot(name: String, ip: String): Boolean {
        if (!usableIp(name, ip)) return false
        if (hotspotIface(name)) return true
        if (knownHotspotPrefix(ip)) return true
        if (name == "fib") return false
        return ip.endsWith(".1") && (name.startsWith("wlan") || name == "ip" || name.isEmpty())
    }

    private fun knownHotspotPrefix(ip: String) =
        ip.startsWith("192.168.43.") || ip.startsWith("192.168.49.") ||
            ip.startsWith("192.168.137.") || ip.startsWith("192.168.229.")

    private fun hotspotIface(name: String): Boolean {
        val n = name.lowercase().substringBefore('@')
        if (n == "wlan0" || n.startsWith("wlan0")) return false
        return n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("softap") ||
            n.startsWith("sap") || n.startsWith("wigig") || n.contains("softap") || n.contains("ap_br")
    }

    private fun hotspotScore(name: String, ip: String, before: Set<String>): Int {
        if (!usableIp(name, ip)) return 0
        val n = name.lowercase()
        val fresh = ip !in before
        val gateway = ip.endsWith(".1")
        var score = 0
        if (hotspotIface(n) || n == "route") score += 200
        if (fresh && (n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan"))) score += 140
        if (gateway) score += 70
        if (knownHotspotPrefix(ip)) score += 40
        if (!fresh && !hotspotIface(n)) score -= 250
        if (n.startsWith("wlan0")) score -= 200
        return score
    }

    private fun usableIp(name: String, ip: String): Boolean {
        if (ip.isBlank() || ip == "0.0.0.0" || ip.startsWith("169.254.")) return false
        val n = name.lowercase()
        if (n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("pdp") ||
            n.startsWith("tun") || n.startsWith("dummy") || n.startsWith("lo") ||
            n.contains("rmnet")
        ) return false
        return isPrivate(ip)
    }

    private fun isPrivate(ip: String): Boolean {
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) return true
        if (!ip.startsWith("172.")) return false
        val second = ip.split('.').getOrNull(1)?.toIntOrNull() ?: return false
        return second in 16..31
    }

    private fun ipScore(name: String, ip: String, preferHotspot: Boolean): Int {
        var score = 0
        val gateway = ip.endsWith(".1")
        val known = knownHotspotPrefix(ip)
        if (preferHotspot) {
            if (hotspotIface(name)) score += 100
            if (gateway) score += 50
            if (known) score += 40
            if (name == "fib" && gateway) score += 20
            if (name.startsWith("wlan0") && !gateway) score -= 40
        } else {
            if (name.startsWith("wlan")) score += 80
            if (name.startsWith("eth")) score += 60
            if (hotspotIface(name)) score -= 10
        }
        return score
    }

    private fun cleanIp(raw: String?): String? = raw?.substringBefore('%')?.takeIf { it.isNotBlank() }

    private fun ipPair(name: String, raw: String?): Pair<String, String>? {
        val ip = cleanIp(raw) ?: return null
        return name to ip
    }

    /** All LAN-ish IPs; placed in the QR so the sender can try each one. */
    fun candidateIps(): List<String> = ipv4s()
        .filterNot { it.first.startsWith("rmnet") || it.first.startsWith("ccmni") || it.first.startsWith("tun") }
        .map { it.second }

    fun intToIp(i: Int) = "${i and 0xff}.${i shr 8 and 0xff}.${i shr 16 and 0xff}.${i shr 24 and 0xff}"

    /** The phone's normal Wi-Fi (or Ethernet), never the offline hotspot. */
    fun clientWifi(ctx: Context): LanLink? {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return null
        var best: LanLink? = null
        var bestScore = Int.MIN_VALUE
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!wifi && !ethernet) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val lp = cm.getLinkProperties(network) ?: continue
            val iface = lp.interfaceName.orEmpty()
            if (hotspotIface(iface)) continue
            val ip = lp.linkAddresses.mapNotNull { la ->
                val address = la.address
                if (address is Inet4Address && !address.isLoopbackAddress) cleanIp(address.hostAddress) else null
            }.firstOrNull { usableIp(iface, it) } ?: continue
            var score = if (wifi) 80 else 60
            if (iface.startsWith("wlan")) score += 20
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) score += 5
            if (score > bestScore) {
                bestScore = score
                best = LanLink(network, ip, wifiTitle(caps) ?: "Wi-Fi")
            }
        }
        return best
    }

    /** Network that actually owns [ip], so the PC page is reachable on that interface. */
    fun networkOwning(ctx: Context, ip: String): Network? {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.firstOrNull { network ->
            val lp = cm.getLinkProperties(network) ?: return@firstOrNull false
            lp.linkAddresses.any { cleanIp(it.address.hostAddress) == ip }
        }
    }

    private fun wifiTitle(caps: NetworkCapabilities): String? {
        val info = caps.transportInfo as? android.net.wifi.WifiInfo ?: return null
        val ssid = info.ssid?.trim('"')
        return ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

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
