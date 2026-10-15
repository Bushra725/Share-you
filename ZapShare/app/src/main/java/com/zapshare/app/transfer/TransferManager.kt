package com.zapshare.app.transfer

import android.content.Context
import android.net.Network
import android.net.Uri
import com.zapshare.app.data.HistoryStore
import com.zapshare.app.data.Prefs
import com.zapshare.app.data.ShareItem
import com.zapshare.app.premium.Premium
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The transfer engine.
 *  - HOST   = receiver / group owner. Opens a server socket (optionally on its own offline hotspot)
 *             and shows a QR code.
 *  - CLIENT = sender / group member. Scans the QR, joins the hotspot, connects.
 * Once connected both sides can send to each other. In group mode the host relays files to everyone.
 */
object TransferManager {
    lateinit var context: Context
        private set

    val session = MutableStateFlow(SessionState())
    val items = MutableStateFlow<List<TransferItem>>(emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peers = ConcurrentHashMap<String, Peer>()
    private var server: ServerSocket? = null
    private val hotspot by lazy { HotspotManager(context) }
    private val wifi by lazy { WifiConnector(context) }
    val nsd by lazy { NsdHelper(context) }

    private val pendingBytes = ConcurrentHashMap<String, AtomicLong>()
    private val windowBytes = AtomicLong(0)
    /** share key -> connection that first delivered it in this group session */
    private val groupFiles = ConcurrentHashMap<String, String>()
    private var ticker: Job? = null
    private var smoothed = 0.0

    fun init(ctx: Context) { context = ctx.applicationContext }

    // ======================================================================= host
    suspend fun startHost(useHotspot: Boolean, group: Boolean) = withContext(Dispatchers.IO) {
        stop()
        items.value = emptyList()
        session.value = SessionState(role = Role.HOST, status = "Preparing…", busy = true, group = group)
        try {
            var creds: HotspotCreds? = null
            if (useHotspot) {
                session.update { it.copy(status = "Starting offline hotspot…") }
                creds = hotspot.start()
            }
            val ss = ServerSocket().apply {
                reuseAddress = true
                receiveBufferSize = Protocol.SOCKET_BUF
                bind(InetSocketAddress(NetUtils.TRANSFER_PORT))
            }
            server = ss
            var ip: String? = null
            for (i in 0 until 10) {
                ip = NetUtils.bestLocalIp(preferHotspot = useHotspot, ctx = context)
                if (ip != null) break
                delay(500)
            }
            if (!useHotspot) nsd.register(Prefs.deviceName, NetUtils.TRANSFER_PORT)
            val info = HostInfo(
                name = Prefs.deviceName, avatar = Prefs.avatar,
                ip = ip ?: "192.168.43.1", ips = NetUtils.candidateIps(),
                port = NetUtils.TRANSFER_PORT,
                ssid = creds?.ssid, pass = creds?.pass, security = creds?.security ?: 1, group = group
            )
            session.update {
                it.copy(hostInfo = info, busy = false,
                    status = if (group) "Group ready. Members scan the QR to join" else "Waiting for sender to scan…")
            }
            TransferService.acquire(context, "transfer")
            startTicker()
            scope.launch { acceptLoop(ss) }
        } catch (e: Exception) {
            cleanupNetwork()
            session.update { it.copy(busy = false, error = e.message ?: "Could not start") }
        }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val s = try { ss.accept() } catch (e: Exception) { break }
            tune(s)
            val p = Peer(s, this)
            peers[p.id] = p
            p.start()
        }
    }

    // ===================================================================== client
    suspend fun connect(info: HostInfo) = withContext(Dispatchers.IO) {
        stop()
        items.value = emptyList()
        session.value = SessionState(role = Role.CLIENT, status = "Connecting to ${info.name}…", busy = true, group = info.group)
        try {
            var network: Network? = null
            if (info.ssid != null) {
                session.update { it.copy(status = "Joining ${info.name}'s hotspot…") }
                network = wifi.connect(info.ssid, info.pass, info.security)
            }
            val candidates = buildList {
                add(info.ip)
                addAll(info.ips)
                wifi.gatewayIp()?.let { add(it) }
            }.distinct()
            val socket = openSocket(candidates, info.port, network)
                ?: throw IllegalStateException("Could not reach ${info.name}. Keep both screens open and retry.")
            tune(socket)
            val p = Peer(socket, this@TransferManager)
            peers[p.id] = p
            p.start()
            session.update { it.copy(busy = false, status = "Connected", hostInfo = info) }
            TransferService.acquire(context, "transfer")
            startTicker()
        } catch (e: Exception) {
            cleanupNetwork()
            session.update { it.copy(busy = false, error = e.message ?: "Connection failed") }
        }
    }

    suspend fun connectNearby(d: NearbyDevice) = connect(
        HostInfo(d.name, 0, d.ip, emptyList(), d.port, null, null, 0, false)
    )

    private suspend fun openSocket(ips: List<String>, port: Int, network: Network?): Socket? {
        repeat(10) { attempt ->
            for (ip in ips) {
                try {
                    val s = network?.socketFactory?.createSocket() ?: Socket()
                    s.sendBufferSize = Protocol.SOCKET_BUF
                    s.connect(InetSocketAddress(ip, port), 2500)
                    return s
                } catch (_: Exception) { }
            }
            if (attempt < 9) delay(1200)   // DHCP on a fresh hotspot can take a few seconds
        }
        return null
    }

    private fun tune(s: Socket) {
        runCatching {
            s.tcpNoDelay = true
            s.keepAlive = true
            s.sendBufferSize = Protocol.SOCKET_BUF
            s.receiveBufferSize = Protocol.SOCKET_BUF
        }
    }

    // ===================================================================== sending
    /** Queue files for every connected device. */
    fun send(list: List<ShareItem>) = scope.launch {
        val targets = peers.values.toList()
        if (targets.isEmpty()) {
            session.update { it.copy(error = "No device connected") }
            return@launch
        }
        for (share in list) {
            var uri = share.uri
            var size = share.size
            val shareKey = UUID.randomUUID().toString()
            if (size <= 0) {   // unknown size (some content providers): stage in cache first
                try {
                    val f = FileUtils.copyToCache(context, share.uri, share.name)
                    uri = Uri.fromFile(f); size = f.length()
                } catch (e: Exception) {
                    continue
                }
            }
            if (!Premium.isPremium.value && size > Premium.FREE_FILE_LIMIT) {
                session.update { it.copy(error = "Files over 100 MB need Premium") }
                continue
            }
            for (p in targets) {
                val item = TransferItem(
                    UUID.randomUUID().toString(), share.name, size, share.mime,
                    Direction.SEND, p.id, p.name,
                    shareKey = shareKey, originName = Prefs.deviceName
                )
                items.update { it + item }
                p.enqueueFile(item, uri)
            }
        }
    }

    // ============================================================ callbacks (from Peer)
    internal fun onPeerHello(p: Peer) {
        refreshPeers()
        if (session.value.role == Role.HOST && session.value.group) {
            val list = peers.values.filter { it.helloReceived }.map { it.info() } +
                PeerInfo("host", Prefs.deviceName, Prefs.avatar)
            peers.values.forEach { it.enqueuePeers(list) }
        }
    }

    private var groupMembers: List<PeerInfo> = emptyList()
    internal fun onPeerList(list: List<PeerInfo>) {
        groupMembers = list
        refreshPeers()
    }

    internal fun onPeerClosed(p: Peer) {
        if (peers.remove(p.id) == null) return
        items.update { l ->
            l.map { if (it.peerId == p.id && (it.status == ItemStatus.WAITING || it.status == ItemStatus.ACTIVE))
                it.copy(status = ItemStatus.FAILED, error = "Disconnected") else it }
        }
        refreshPeers()
        if (session.value.role == Role.CLIENT && peers.isEmpty()) {
            session.update { it.copy(disconnected = true, status = "Disconnected") }
            cleanupNetwork()
        } else if (session.value.role == Role.HOST && session.value.group) {
            onPeerHello(p)
        }
    }

    private fun refreshPeers() {
        val direct = peers.values.filter { it.helloReceived }.map { it.info() }
        val shown = if (session.value.role == Role.CLIENT && session.value.group && groupMembers.isNotEmpty())
            groupMembers.filter { it.name != Prefs.deviceName || it.id == "host" }
        else direct
        session.update { it.copy(peers = if (direct.isEmpty()) emptyList() else shown,
            status = if (direct.isEmpty()) it.status else "Connected") }
    }

    /**
     * One shared file should arrive once. A second delivery of the same share from
     * another phone (the host forwarding it again) is ignored.
     */
    internal fun claimGroupFile(key: String, peerId: String): Boolean {
        if (!session.value.group || key.isBlank()) return true
        val owner = groupFiles.putIfAbsent(key, peerId)
        return owner == null || owner == peerId
    }

    internal fun startIncoming(
        p: Peer, name: String, size: Long, mime: String,
        origin: String, shareKey: String, relay: Boolean, relayPath: String?
    ): TransferItem {
        val shown = origin.ifBlank { p.name }
        val item = TransferItem(
            UUID.randomUUID().toString(), name, size, mime, Direction.RECEIVE, p.id, shown,
            status = ItemStatus.ACTIVE, shareKey = shareKey, originName = shown, relay = relay, relayPath = relayPath
        )
        items.update { it + item }
        return item
    }

    internal fun addProgress(id: String, n: Long) {
        pendingBytes.getOrPut(id) { AtomicLong() }.addAndGet(n)
        windowBytes.addAndGet(n)
    }

    internal fun markActive(id: String) = setStatus(id) { it.copy(status = ItemStatus.ACTIVE) }

    internal fun markFailed(id: String, err: String) {
        pendingBytes.remove(id)
        val failed = items.value.find { it.id == id }
        if (failed != null && failed.direction == Direction.RECEIVE && failed.shareKey.isNotBlank()) {
            groupFiles.remove(failed.shareKey, failed.peerId)
        }
        setStatus(id) { it.copy(status = ItemStatus.FAILED, error = err) }
    }

    internal fun markSent(item: TransferItem) {
        pendingBytes.remove(item.id)
        setStatus(item.id) { it.copy(status = ItemStatus.DONE, transferred = it.size) }
        HistoryStore.add(item.name, item.size, item.mime, true, item.peerName, null)
    }

    internal fun markReceived(from: Peer, item: TransferItem, uri: Uri) {
        pendingBytes.remove(item.id)
        val s = session.value
        setStatus(item.id) { it.copy(status = ItemStatus.DONE, transferred = it.size, resultUri = uri) }
        if (s.group && item.shareKey.isNotBlank()) {
            items.update { list ->
                list.filterNot {
                    it.id != item.id && it.shareKey == item.shareKey && it.direction == Direction.RECEIVE &&
                        it.status == ItemStatus.FAILED
                }
            }
        }
        HistoryStore.add(item.name, item.size, item.mime, false, item.peerName, uri)
        val source = item.relayPath?.let { path -> File(path).takeIf { it.isFile && it.length() == item.size } }
        if (s.role == Role.HOST && s.group && Prefs.groupRelay && !item.relay && source != null &&
            (Premium.isPremium.value || item.size <= Premium.FREE_FILE_LIMIT)
        ) {
            val relayUri = Uri.fromFile(source)
            peers.values.filter { it.id != from.id }.forEach { p ->
                val out = TransferItem(
                    UUID.randomUUID().toString(), item.name, item.size, item.mime,
                    Direction.SEND, p.id, p.name,
                    shareKey = item.shareKey, originName = item.originName, relay = true, relayPath = item.relayPath
                )
                items.update { it + out }
                p.enqueueFile(out, relayUri)
            }
        }
    }

    private fun setStatus(id: String, f: (TransferItem) -> TransferItem) {
        items.update { l -> l.map { if (it.id == id) f(it) else it } }
    }

    // ===================================================================== progress
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var last = System.nanoTime()
            while (isActive) {
                delay(300)
                val now = System.nanoTime()
                val secs = (now - last) / 1e9
                last = now
                val bytes = windowBytes.getAndSet(0)
                smoothed = if (smoothed == 0.0) bytes / secs else smoothed * 0.6 + (bytes / secs) * 0.4
                if (bytes == 0L && smoothed < 1024) smoothed = 0.0
                if (pendingBytes.isNotEmpty()) {
                    val snapshot = pendingBytes.mapValues { it.value.get() }
                    items.update { l ->
                        l.map { i -> snapshot[i.id]?.let { b -> if (i.status == ItemStatus.ACTIVE) i.copy(transferred = b) else i } ?: i }
                    }
                }
                session.update { it.copy(speed = smoothed) }
                TransferService.updateProgress(context, items.value, smoothed)
            }
        }
    }

    // ======================================================================= stop
    fun clearError() = session.update { it.copy(error = null) }

    fun stop() {
        ticker?.cancel(); ticker = null
        peers.values.toList().forEach { it.close() }
        peers.clear()
        groupMembers = emptyList()
        groupFiles.clear()
        runCatching { server?.close() }
        server = null
        cleanupNetwork()
        smoothed = 0.0
        session.value = SessionState()
        if (::context.isInitialized) {
            TransferService.release(context, "transfer")
            File(context.cacheDir, "work").listFiles()?.forEach { it.delete() }
        }
    }

    private fun cleanupNetwork() {
        hotspot.stop()
        nsd.unregister()
        wifi.disconnect()
    }
}
