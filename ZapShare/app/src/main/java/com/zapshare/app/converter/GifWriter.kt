package com.zapshare.app.converter

import android.graphics.Bitmap
import java.io.OutputStream

/** Writes an animated GIF. Each frame keeps its own 256 colours from the picture. */
class GifWriter(private val out: OutputStream, private val width: Int, private val height: Int) {
    private var opened = false

    fun frame(bmp: Bitmap, delayCs: Int) {
        val pixels = IntArray(width * height)
        val src = if (bmp.width == width && bmp.height == height) bmp
        else Bitmap.createScaledBitmap(bmp, width, height, true)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        if (src !== bmp) src.recycle()
        val palette = paletteFor(pixels)
        val indexed = dither(pixels, palette)
        if (!opened) {
            header(palette)
            opened = true
        }
        graphicControl(delayCs)
        out.write(0x2C)
        writeShort(0); writeShort(0); writeShort(width); writeShort(height)
        out.write(0x87)
        writePalette(palette)
        out.write(8)
        val lzw = Lzw(out)
        for (p in indexed) lzw.pixel(p.toInt() and 0xFF)
        lzw.finish()
    }

    fun close() {
        if (opened) out.write(0x3B)
        out.flush()
    }

    private fun header(palette: IntArray) {
        out.write("GIF89a".toByteArray())
        writeShort(width); writeShort(height)
        out.write(0xF7)
        out.write(0)
        out.write(0)
        writePalette(palette)
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    private fun writePalette(palette: IntArray) {
        for (i in 0 until 256) {
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }
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

    /** Median cut of the real pixels, so skin and walls are not crushed into six levels. */
    private fun paletteFor(pixels: IntArray): IntArray {
        val step = (pixels.size / 16384).coerceAtLeast(1)
        val colors = IntArray((pixels.size + step - 1) / step)
        var n = 0
        var i = 0
        while (i < pixels.size && n < colors.size) {
            colors[n++] = pixels[i] and 0x00FFFFFF
            i += step
        }
        val order = IntArray(n) { it }
        val boxes = ArrayList<Box>(256)
        boxes.add(Box(0, n).also { it.measure(colors, order) })
        while (boxes.size < 256) {
            var pick = -1
            var best = 0
            for (b in boxes.indices) {
                val spread = boxes[b].spread
                if (spread > best && boxes[b].end - boxes[b].start > 1) {
                    best = spread
                    pick = b
                }
            }
            if (pick < 0) break
            val box = boxes.removeAt(pick)
            val mid = box.split(colors, order)
            val left = Box(box.start, mid).also { it.measure(colors, order) }
            val right = Box(mid, box.end).also { it.measure(colors, order) }
            boxes.add(left)
            boxes.add(right)
        }
        val palette = IntArray(256)
        for (b in boxes.indices) palette[b] = boxes[b].average(colors, order)
        return palette
    }

    private class Box(val start: Int, val end: Int) {
        var spread = 0
        var channel = 0

        fun measure(colors: IntArray, order: IntArray) {
            var r0 = 255; var r1 = 0
            var g0 = 255; var g1 = 0
            var b0 = 255; var b1 = 0
            for (i in start until end) {
                val c = colors[order[i]]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r < r0) r0 = r
                if (r > r1) r1 = r
                if (g < g0) g0 = g
                if (g > g1) g1 = g
                if (b < b0) b0 = b
                if (b > b1) b1 = b
            }
            val rs = r1 - r0
            val gs = g1 - g0
            val bs = b1 - b0
            when {
                rs >= gs && rs >= bs -> { channel = 0; spread = rs }
                gs >= bs -> { channel = 1; spread = gs }
                else -> { channel = 2; spread = bs }
            }
        }

        fun split(colors: IntArray, order: IntArray): Int {
            val tmp = Array(end - start) { order[start + it] }
            tmp.sortWith { a, b -> component(colors[a], channel) - component(colors[b], channel) }
            for (i in tmp.indices) order[start + i] = tmp[i]
            return start + tmp.size / 2
        }

        fun average(colors: IntArray, order: IntArray): Int {
            var r = 0L
            var g = 0L
            var b = 0L
            val count = (end - start).coerceAtLeast(1)
            for (i in start until end) {
                val c = colors[order[i]]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            return ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
        }
    }

    private fun dither(pixels: IntArray, palette: IntArray): ByteArray {
        val lut = IntArray(32 * 32 * 32)
        for (key in lut.indices) {
            val r = ((key shr 10) and 31) * 8 + 4
            val g = ((key shr 5) and 31) * 8 + 4
            val b = (key and 31) * 8 + 4
            lut[key] = nearest(r, g, b, palette)
        }
        val rr = IntArray(pixels.size)
        val gg = IntArray(pixels.size)
        val bb = IntArray(pixels.size)
        for (i in pixels.indices) {
            rr[i] = (pixels[i] shr 16) and 0xFF
            gg[i] = (pixels[i] shr 8) and 0xFF
            bb[i] = pixels[i] and 0xFF
        }
        val indexed = ByteArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val r = rr[i].coerceIn(0, 255)
                val g = gg[i].coerceIn(0, 255)
                val b = bb[i].coerceIn(0, 255)
                val idx = lut[((r shr 3) shl 10) or ((g shr 5) shl 5) or (b shr 3)]
                indexed[i] = idx.toByte()
                val chosen = palette[idx]
                val er = r - ((chosen shr 16) and 0xFF)
                val eg = g - ((chosen shr 8) and 0xFF)
                val eb = b - (chosen and 0xFF)
                diffuse(rr, gg, bb, x + 1, y, er, eg, eb, 7)
                diffuse(rr, gg, bb, x - 1, y + 1, er, eg, eb, 3)
                diffuse(rr, gg, bb, x, y + 1, er, eg, eb, 5)
                diffuse(rr, gg, bb, x + 1, y + 1, er, eg, eb, 1)
            }
        }
        return indexed
    }

    private fun diffuse(rr: IntArray, gg: IntArray, bb: IntArray, x: Int, y: Int, er: Int, eg: Int, eb: Int, factor: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val i = y * width + x
        rr[i] += er * factor / 16
        gg[i] += eg * factor / 16
        bb[i] += eb * factor / 16
    }

    private fun nearest(r: Int, g: Int, b: Int, palette: IntArray): Int {
        var best = 0
        var bestD = Int.MAX_VALUE
        for (i in palette.indices) {
            val c = palette[i]
            val dr = r - ((c shr 16) and 0xFF)
            val dg = g - ((c shr 8) and 0xFF)
            val db = b - (c and 0xFF)
            val d = dr * dr * 2 + dg * dg * 4 + db * db * 3
            if (d < bestD) {
                bestD = d
                best = i
                if (d == 0) break
            }
        }
        return best
    }

    /**
     * Each colour is its own code. The first code after a clear does not grow
     * the table, and the width stays at 12 bits when the table fills.
     */
    private class Lzw(private val out: OutputStream) {
        private val clear = 256
        private val eoi = 257
        private var codeSize = 9
        private var next = 258
        private var first = true
        private var acc = 0L
        private var bits = 0
        private val block = ByteArray(255)
        private var blockLen = 0

        init { writeCode(clear) }

        fun pixel(p: Int) {
            writeCode(p and 0xFF)
            if (first) {
                first = false
                return
            }
            next++
            if (next == 1 shl codeSize) {
                if (codeSize < 12) {
                    codeSize++
                } else {
                    writeCode(clear)
                    codeSize = 9
                    next = 258
                    first = true
                }
            }
        }

        fun finish() {
            writeCode(eoi)
            if (bits > 0) emit((acc and 0xFF).toInt())
            flushBlock()
            out.write(0)
        }

        private fun writeCode(code: Int) {
            acc = acc or (code.toLong() shl bits)
            bits += codeSize
            while (bits >= 8) {
                emit((acc and 0xFF).toInt())
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

    companion object {
        private fun component(color: Int, channel: Int): Int = when (channel) {
            0 -> (color shr 16) and 0xFF
            1 -> (color shr 8) and 0xFF
            else -> color and 0xFF
        }
    }
}
