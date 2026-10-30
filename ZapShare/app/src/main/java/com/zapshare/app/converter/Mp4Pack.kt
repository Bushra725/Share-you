package com.zapshare.app.converter

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Writes an H.264 + AAC MP4 without Android's MPEG4Writer.
 * On this phone that writer drops every frame, so the saved file has no picture.
 */
internal class Mp4Pack(file: File) {
    private val out = RandomAccessFile(file, "rw")
    private var mdatAt = 0L
    private var video: Track? = null
    private var audio: Track? = null
    private var closed = false

    private class Track(
        val video: Boolean,
        val timescale: Int,
        val width: Int,
        val height: Int,
        val rotation: Int,
        val sps: ByteArray?,
        val pps: ByteArray?,
        val channels: Int,
        val rate: Int,
        val asc: ByteArray?
    ) {
        val samples = ArrayList<Sample>(256)
    }

    private class Sample(val offset: Long, val size: Int, val pts: Long, val key: Boolean)

    init {
        out.setLength(0)
        val ftyp = Buf().apply {
            str("isom")
            i32(0x200)
            str("isom")
            str("iso2")
            str("avc1")
            str("mp41")
        }
        writeBox("ftyp", ftyp.bytes())
        mdatAt = out.filePointer
        out.writeInt(0)
        out.write(byteArrayOf('m'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte()))
    }

    fun addVideo(width: Int, height: Int, rotation: Int, sps: ByteArray, pps: ByteArray) {
        video = Track(true, 90_000, width, height, rotation, sps, pps, 0, 0, null)
    }

    fun addAac(rate: Int, channels: Int, asc: ByteArray) {
        audio = Track(false, rate.coerceAtLeast(1), 0, 0, 0, null, null, channels, rate, asc)
    }

    fun video(data: ByteArray, ptsUs: Long, key: Boolean) {
        val track = video ?: return
        val ticks = ptsUs * track.timescale / 1_000_000L
        writeSample(track, data, ticks, key)
    }

    fun audio(data: ByteArray, ptsUs: Long) {
        val track = audio ?: return
        val ticks = ptsUs * track.timescale / 1_000_000L
        writeSample(track, data, ticks, true)
    }

    fun finish() {
        if (closed) return
        val videoTrack = video
        if (videoTrack == null || videoTrack.samples.isEmpty()) return
        val end = out.filePointer
        val mdatSize = end - mdatAt
        out.seek(mdatAt)
        out.writeInt(mdatSize.toInt())
        out.seek(end)
        writeBox("moov", moov())
        closed = true
    }

    fun close() {
        runCatching { out.close() }
    }

    private fun writeSample(track: Track, data: ByteArray, pts: Long, key: Boolean) {
        val offset = out.filePointer
        out.write(data)
        val last = track.samples.lastOrNull()?.pts ?: -1L
        val time = if (pts <= last) last + 1 else pts
        track.samples.add(Sample(offset, data.size, time, key))
    }

    private fun moov(): ByteArray {
        val videoTrack = video!!
        val audioTrack = audio?.takeIf { it.samples.isNotEmpty() }
        val movieScale = 1000
        val videoMs = durationMs(videoTrack)
        val audioMs = audioTrack?.let { durationMs(it) } ?: 0L
        val movieDur = maxOf(videoMs, audioMs).coerceAtLeast(1)
        return Buf().apply {
            box("mvhd") {
                i32(0)
                i32(0); i32(0)
                i32(movieScale)
                i32(movieDur.toInt())
                i32(0x00010000)
                i16(0x0100)
                i16(0); i32(0); i32(0)
                matrix(0, 0, 0)
                repeat(6) { i32(0) }
                i32(if (audioTrack == null) 2 else 3)
            }
            box("trak") { trak(videoTrack, 1, movieScale) }
            if (audioTrack != null) box("trak") { trak(audioTrack, 2, movieScale) }
        }.bytes()
    }

    private fun Buf.trak(track: Track, id: Int, movieScale: Int) {
        val durMs = durationMs(track).coerceAtLeast(1)
        val displayW = if (track.rotation == 90 || track.rotation == 270) track.height else track.width
        val displayH = if (track.rotation == 90 || track.rotation == 270) track.width else track.height
        box("tkhd") {
            i32(0x7)
            i32(0); i32(0)
            i32(id)
            i32(0)
            i32(durMs.toInt())
            i32(0); i32(0)
            i16(0); i16(0)
            i16(if (track.video) 0 else 0x0100)
            i16(0)
            matrix(track.rotation, track.width, track.height)
            i32(displayW shl 16)
            i32(displayH shl 16)
        }
        box("mdia") {
            box("mdhd") {
                i32(0)
                i32(0); i32(0)
                i32(track.timescale)
                i32(trackTicks(track).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                i16(0x55C4)
                i16(0)
            }
            box("hdlr") {
                i32(0)
                i32(0)
                str(if (track.video) "vide" else "soun")
                i32(0); i32(0); i32(0)
                bytes(if (track.video) "VideoHandler\u0000".encodeToByteArray() else "SoundHandler\u0000".encodeToByteArray())
            }
            box("minf") {
                if (track.video) {
                    box("vmhd") {
                        i32(1)
                        i16(0); i16(0); i16(0); i16(0)
                    }
                } else {
                    box("smhd") {
                        i32(0)
                        i16(0)
                        i16(0)
                    }
                }
                box("dinf") {
                    box("dref") {
                        i32(0)
                        i32(1)
                        box("url ") {
                            i32(1)
                        }
                    }
                }
                box("stbl") { stbl(track) }
            }
        }
    }

    private fun Buf.stbl(track: Track) {
        box("stsd") {
            i32(0)
            i32(1)
            if (track.video) avc1(track) else mp4a(track)
        }
        val deltas = durations(track)
        box("stts") {
            i32(0)
            val runs = ArrayList<Pair<Int, Int>>()
            for (d in deltas) {
                val last = runs.lastOrNull()
                if (last != null && last.second == d) runs[runs.lastIndex] = last.first + 1 to d
                else runs.add(1 to d)
            }
            i32(runs.size)
            for ((count, delta) in runs) {
                i32(count)
                i32(delta)
            }
        }
        if (track.video) {
            val keys = track.samples.mapIndexedNotNull { index, sample -> if (sample.key) index + 1 else null }
            box("stss") {
                i32(0)
                i32(keys.size.coerceAtLeast(1))
                if (keys.isEmpty()) i32(1) else keys.forEach { i32(it) }
            }
        }
        box("stsc") {
            i32(0)
            i32(1)
            i32(1)
            i32(1)
            i32(1)
        }
        box("stsz") {
            i32(0)
            i32(0)
            i32(track.samples.size)
            track.samples.forEach { i32(it.size) }
        }
        box("stco") {
            i32(0)
            i32(track.samples.size)
            track.samples.forEach { i32(it.offset.toInt()) }
        }
    }

    private fun Buf.avc1(track: Track) {
        val sps = track.sps ?: ByteArray(0)
        val pps = track.pps ?: ByteArray(0)
        box("avc1") {
            bytes(ByteArray(6))
            i16(1)
            i16(0); i16(0)
            i32(0); i32(0); i32(0)
            i16(track.width)
            i16(track.height)
            i32(0x00480000)
            i32(0x00480000)
            i32(0)
            i16(1)
            bytes(ByteArray(32))
            i16(0x18)
            i16(-1)
            box("avcC") {
                i8(1)
                i8(sps.getOrElse(1) { 0x42 }.toInt())
                i8(sps.getOrElse(2) { 0 }.toInt())
                i8(sps.getOrElse(3) { 0x1E }.toInt())
                i8(0xFF)
                i8(0xE1)
                i16(sps.size)
                bytes(sps)
                i8(1)
                i16(pps.size)
                bytes(pps)
            }
        }
    }

    private fun Buf.mp4a(track: Track) {
        box("mp4a") {
            bytes(ByteArray(6))
            i16(1)
            i32(0); i32(0)
            i16(track.channels.coerceIn(1, 2))
            i16(16)
            i16(0); i16(0)
            i32(track.rate shl 16)
            box("esds") {
                bytes(esds(track.asc ?: byteArrayOf(0x12, 0x10)))
            }
        }
    }

    private fun durationMs(track: Track): Long {
        val ticks = trackTicks(track)
        return ticks * 1000L / track.timescale
    }

    private fun trackTicks(track: Track): Long {
        if (track.samples.isEmpty()) return 0
        return durations(track).sumOf { it.toLong() }
    }

    private fun durations(track: Track): IntArray {
        val samples = track.samples
        val fallback = (track.timescale / 30).coerceAtLeast(1)
        val out = IntArray(samples.size)
        for (i in samples.indices) {
            val delta = if (i + 1 < samples.size) samples[i + 1].pts - samples[i].pts else fallback.toLong()
            out[i] = delta.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
        return out
    }

    private fun writeBox(type: String, payload: ByteArray) {
        out.writeInt(payload.size + 8)
        out.write(type.encodeToByteArray())
        out.write(payload)
    }

    private class Buf {
        private val raw = ByteArrayOutputStream()

        fun i32(v: Int) {
            raw.write((v ushr 24) and 0xFF)
            raw.write((v ushr 16) and 0xFF)
            raw.write((v ushr 8) and 0xFF)
            raw.write(v and 0xFF)
        }

        fun i16(v: Int) {
            raw.write((v ushr 8) and 0xFF)
            raw.write(v and 0xFF)
        }

        fun i8(v: Int) {
            raw.write(v and 0xFF)
        }

        fun str(s: String) {
            raw.write(s.encodeToByteArray())
        }

        fun bytes(b: ByteArray) {
            raw.write(b)
        }

        fun box(type: String, body: Buf.() -> Unit) {
            val payload = Buf().apply(body).bytes()
            i32(payload.size + 8)
            str(type)
            bytes(payload)
        }

        fun matrix(rotation: Int, width: Int, height: Int) {
            val one = 0x00010000
            val neg = -0x00010000
            val w = width shl 16
            val h = height shl 16
            val m = when (rotation) {
                90 -> intArrayOf(0, one, 0, neg, 0, 0, h, 0, 0x40000000)
                180 -> intArrayOf(neg, 0, 0, 0, neg, 0, w, h, 0x40000000)
                270 -> intArrayOf(0, neg, 0, one, 0, 0, 0, w, 0x40000000)
                else -> intArrayOf(one, 0, 0, 0, one, 0, 0, 0, 0x40000000)
            }
            m.forEach { i32(it) }
        }

        fun bytes(): ByteArray = raw.toByteArray()
    }

    private fun esds(asc: ByteArray): ByteArray {
        val specific = descriptor(0x05, asc)
        val config = ByteArrayOutputStream()
        config.write(0x40)
        config.write(0x15)
        config.write(0); config.write(0); config.write(0)
        writeInt(config, 128_000)
        writeInt(config, 128_000)
        config.write(specific)
        val sl = descriptor(0x06, byteArrayOf(0x02))
        val es = ByteArrayOutputStream()
        es.write(0); es.write(0); es.write(0)
        es.write(descriptor(0x04, config.toByteArray()))
        es.write(sl)
        val full = ByteArrayOutputStream()
        writeInt(full, 0)
        full.write(descriptor(0x03, es.toByteArray()))
        return full.toByteArray()
    }

    private fun descriptor(tag: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        var left = payload.size
        val parts = ArrayList<Int>()
        parts.add(left and 0x7F)
        left = left ushr 7
        while (left > 0) {
            parts.add((left and 0x7F) or 0x80)
            left = left ushr 7
        }
        for (i in parts.indices.reversed()) out.write(parts[i])
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }
}
