package com.zapshare.app.transfer

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

enum class Direction { SEND, RECEIVE }
enum class ItemStatus { WAITING, ACTIVE, DONE, FAILED }
enum class Role { NONE, HOST, CLIENT }

data class TransferItem(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
    val direction: Direction,
    val peerId: String,
    val peerName: String,
    val transferred: Long = 0,
    val status: ItemStatus = ItemStatus.WAITING,
    val resultUri: Uri? = null,
    val error: String? = null
) {
    val progress: Float
        get() = when {
            status == ItemStatus.DONE -> 1f
            size <= 0 -> 0f
            else -> (transferred.toFloat() / size).coerceIn(0f, 1f)
        }
}

data class PeerInfo(val id: String, val name: String, val avatar: Int)

/** Everything a sender needs to reach a receiver. Encoded into the QR code. */
data class HostInfo(
    val name: String,
    val avatar: Int,
    val ip: String,
    val ips: List<String>,
    val port: Int,
    val ssid: String?,
    val pass: String?,
    val security: Int,
    val group: Boolean
) {
    fun toQr(): String = PREFIX + JSONObject()
        .put("n", name).put("a", avatar).put("ip", ip).put("ips", JSONArray(ips)).put("p", port)
        .put("s", ssid ?: "").put("k", pass ?: "").put("sec", security).put("g", group)
        .toString()

    companion object {
        const val PREFIX = "ZAPSHARE:"
        fun parse(text: String?): HostInfo? {
            if (text == null || !text.startsWith(PREFIX)) return null
            return runCatching {
                val o = JSONObject(text.removePrefix(PREFIX))
                val ipsArr = o.optJSONArray("ips")
                HostInfo(
                    name = o.optString("n", "Device"),
                    avatar = o.optInt("a", 0),
                    ip = o.getString("ip"),
                    ips = if (ipsArr == null) emptyList() else (0 until ipsArr.length()).map { ipsArr.getString(it) },
                    port = o.optInt("p", NetUtils.TRANSFER_PORT),
                    ssid = o.optString("s").ifEmpty { null },
                    pass = o.optString("k").ifEmpty { null },
                    security = o.optInt("sec", 1),
                    group = o.optBoolean("g", false)
                )
            }.getOrNull()
        }
    }
}

data class SessionState(
    val role: Role = Role.NONE,
    val status: String = "",
    val hostInfo: HostInfo? = null,
    val peers: List<PeerInfo> = emptyList(),
    val speed: Double = 0.0,
    val error: String? = null,
    val busy: Boolean = false,
    val group: Boolean = false,
    val disconnected: Boolean = false
) {
    val active get() = role != Role.NONE
    val connected get() = peers.isNotEmpty()
}

/** Wire protocol: [type:1][headerLen:4][headerJson][raw file bytes (FILE only)] */
object Protocol {
    const val T_HELLO: Byte = 1
    const val T_FILE: Byte = 2
    const val T_PEERS: Byte = 3
    const val T_BYE: Byte = 4
    const val BUF = 512 * 1024
    const val SOCKET_BUF = 2 * 1024 * 1024
}
