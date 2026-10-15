package com.zapshare.app.converter

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Minimal Matroska writer for H.264 video and AAC audio.
 * Samples stay in the MP4 form MediaExtractor already provides (length-prefixed NALs, raw AAC).
 */
class MkvWriter(file: File) {
    data class Video(val width: Int, val height: Int, val csd: ByteArray)
    data class Audio(val sampleRate: Int, val channels: Int, val csd: ByteArray)

    private val out = BufferedOutputStream(FileOutputStream(file), 256 * 1024)
    private var clusterBaseUs = Long.MIN_VALUE

    fun start(video: Video, audio: Audio?) {
        out.write(element(0x1A45DFA3, concat(
            uintEl(0x4286, 1),
            uintEl(0x42F7, 1),
            uintEl(0x42F2, 4),
            uintEl(0x42F3, 8),
            stringEl(0x4282, "matroska"),
            uintEl(0x4287, 4),
            uintEl(0x4285, 2)
        )))
        out.write(id(0x18538067))
        out.write(UNKNOWN_SIZE)
        out.write(element(0x1549A966, concat(
            uintEl(0x2AD7B1, 1_000_000),
            stringEl(0x4D80, "Share"),
            stringEl(0x5741, "Share")
        )))
        val tracks = ByteArrayOutputStream()
        tracks.write(track(1, 1, "V_MPEG4/ISO/AVC", video.csd, videoEl(video.width, video.height)))
        if (audio != null && audio.csd.isNotEmpty()) {
            tracks.write(track(2, 2, "A_AAC", audio.csd, audioEl(audio.sampleRate, audio.channels)))
        }
        out.write(element(0x1654AE6B, tracks.toByteArray()))
    }

    fun videoSample(data: ByteArray, ptsUs: Long, key: Boolean) = sample(1, data, ptsUs, key)

    fun audioSample(data: ByteArray, ptsUs: Long) = sample(2, data, ptsUs, false)

    fun close() {
        out.flush()
        out.close()
    }

    private fun sample(track: Int, data: ByteArray, ptsUs: Long, key: Boolean) {
        if (data.isEmpty()) return
        val pts = ptsUs.coerceAtLeast(0)
        if (clusterBaseUs == Long.MIN_VALUE || pts - clusterBaseUs > 5_000_000 || pts < clusterBaseUs) {
            clusterBaseUs = pts
            out.write(id(0x1F43B675))
            out.write(UNKNOWN_SIZE)
            out.write(uintEl(0xE7, (pts / 1000).toInt()))
        }
        val rel = ((pts - clusterBaseUs) / 1000).toInt().coerceIn(0, 32767)
        val body = ByteArrayOutputStream(data.size + 8)
        body.write(vint(track))
        body.write((rel shr 8) and 0xFF)
        body.write(rel and 0xFF)
        body.write(if (key) 0x80 else 0x00)
        body.write(data)
        out.write(element(0xA3, body.toByteArray()))
    }

    private fun track(number: Int, type: Int, codec: String, csd: ByteArray, extra: ByteArray): ByteArray =
        element(0xAE, concat(
            uintEl(0xD7, number),
            uintEl(0x73C5, 0x5100 + number),
            uintEl(0x83, type),
            stringEl(0x86, codec),
            if (csd.isNotEmpty()) element(0x63A2, csd) else ByteArray(0),
            extra
        ))

    private fun videoEl(w: Int, h: Int) = element(0xE0, concat(uintEl(0xB0, w), uintEl(0xBA, h)))

    private fun audioEl(rate: Int, channels: Int) =
        element(0xE1, concat(floatEl(0xB5, rate.toDouble()), uintEl(0x9F, channels.coerceAtLeast(1))))

    private fun uintEl(id: Long, value: Int) = element(id, uint(value))
    private fun stringEl(id: Long, value: String) = element(id, value.toByteArray())
    private fun floatEl(id: Long, value: Double): ByteArray {
        val b = ByteBuffer.allocate(8).putDouble(value).array()
        return element(id, b)
    }

    private fun uint(value: Int): ByteArray {
        if (value == 0) return byteArrayOf(0)
        var v = value
        val raw = ByteArray(4)
        var n = 0
        while (v > 0) { raw[n++] = (v and 0xFF).toByte(); v = v ushr 8 }
        return ByteArray(n) { raw[n - 1 - it] }
    }

    private fun element(id: Long, payload: ByteArray): ByteArray {
        val i = id(id)
        val s = vint(payload.size)
        return concat(i, s, payload)
    }

    private fun id(value: Long): ByteArray {
        val n = when {
            value <= 0xFF -> 1
            value <= 0xFFFF -> 2
            value <= 0xFFFFFF -> 3
            else -> 4
        }
        return ByteArray(n) { ((value shr ((n - 1 - it) * 8)) and 0xFF).toByte() }
    }

    private fun vint(len: Int): ByteArray = when {
        len < 0x7F -> byteArrayOf((0x80 or len).toByte())
        len < 0x3FFF -> {
            val v = 0x4000 or len
            byteArrayOf((v shr 8).toByte(), v.toByte())
        }
        len < 0x1FFFFF -> {
            val v = 0x200000 or len
            byteArrayOf((v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        }
        else -> {
            val v = 0x10000000 or len
            byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        }
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val n = parts.sumOf { it.size }
        val out = ByteArray(n)
        var p = 0
        for (part in parts) {
            part.copyInto(out, p)
            p += part.size
        }
        return out
    }

    private companion object {
        val UNKNOWN_SIZE = byteArrayOf(0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
    }
}
