package com.zapshare.app.converter

import android.graphics.Bitmap
import java.io.OutputStream

/** Writes an animated GIF from bitmaps already scaled to the same size. */
class GifWriter(private val out: OutputStream, private val width: Int, private val height: Int) {
    private var opened = false

    fun frame(bmp: Bitmap, delayCs: Int) {
        if (!opened) {
            header()
            opened = true
        }
        val pixels = IntArray(width * height)
        val src = if (bmp.width == width && bmp.height == height) bmp
        else Bitmap.createScaledBitmap(bmp, width, height, true)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        if (src !== bmp) src.recycle()
        graphicControl(delayCs)
        out.write(0x2C)
        writeShort(0); writeShort(0); writeShort(width); writeShort(height)
        out.write(0)
        out.write(8)
        val lzw = Lzw(out)
        for (c in pixels) lzw.pixel(index(c))
        lzw.finish()
    }

    fun close() {
        if (opened) out.write(0x3B)
        out.flush()
    }

    private fun header() {
        out.write("GIF89a".toByteArray())
        writeShort(width); writeShort(height)
        out.write(0xF7)
        out.write(0)
        out.write(0)
        val scale = 255 / 5
        for (i in 0 until 216) {
            val r = i / 36
            val g = (i / 6) % 6
            val b = i % 6
            out.write(r * scale); out.write(g * scale); out.write(b * scale)
        }
        repeat(256 - 216) { out.write(0); out.write(0); out.write(0) }
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    private fun graphicControl(delayCs: Int) {
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04))
        writeShort(delayCs.coerceIn(1, 500))
        out.write(0)
        out.write(0)
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun index(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 6 / 256) * 36 + (g * 6 / 256) * 6 + (b * 6 / 256)
    }

    private class Lzw(private val out: OutputStream) {
        private val clear = 256
        private val eoi = 257
        private var codeSize = 9
        private var next = 258
        private val table = HashMap<Int, Int>(5000)
        private var prefix = -1
        private var acc = 0
        private var bits = 0
        private val block = ByteArray(255)
        private var blockLen = 0

        init { writeCode(clear) }

        fun pixel(p: Int) {
            if (prefix < 0) { prefix = p; return }
            val key = (prefix shl 8) or p
            val found = table[key]
            if (found != null) {
                prefix = found
                return
            }
            writeCode(prefix)
            if (next < 4096) {
                table[key] = next
                next++
                if (next == (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                writeCode(clear)
                table.clear()
                codeSize = 9
                next = 258
            }
            prefix = p
        }

        fun finish() {
            if (prefix >= 0) writeCode(prefix)
            writeCode(eoi)
            if (bits > 0) emit(acc and 0xFF)
            flushBlock()
            out.write(0)
        }

        private fun writeCode(code: Int) {
            acc = acc or (code shl bits)
            bits += codeSize
            while (bits >= 8) {
                emit(acc and 0xFF)
                acc = acc ushr 8
                bits -= 8
            }
        }

        private fun emit(b: Int) {
            block[blockLen++] = b.toByte()
            if (blockLen == 255) flushBlock()
        }

        private fun flushBlock() {
            if (blockLen == 0) return
            out.write(blockLen)
            out.write(block, 0, blockLen)
            blockLen = 0
        }
    }
}
