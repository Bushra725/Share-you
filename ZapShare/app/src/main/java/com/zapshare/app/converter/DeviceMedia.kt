package com.zapshare.app.converter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import com.github.axet.lamejni.Lame
import com.github.axet.vorbisjni.Vorbis
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts audio and video with the codecs built into Android, plus small MP3 and OGG encoders.
 * No FFmpeg binary is required.
 */
object DeviceMedia {
    private const val TIMEOUT = 10_000L
    private var lameReady = false
    private var vorbisReady = false

    fun convert(input: File, output: File, preset: Converters.MediaPreset, durationMs: Long, onProgress: (Float) -> Unit) {
        val src = withPlayableName(input)
        try {
            when (preset) {
                Converters.MediaPreset.TO_MP3 -> audio(src, durationMs, onProgress) { rate, ch -> Mp3Sink(output, rate, ch) }
                Converters.MediaPreset.TO_M4A -> audio(src, durationMs, onProgress) { rate, ch -> AacSink(output, rate, ch) }
                Converters.MediaPreset.TO_WAV -> audio(src, durationMs, onProgress) { rate, ch -> WavSink(output, rate, ch) }
                Converters.MediaPreset.TO_OGG -> audio(src, durationMs, onProgress) { rate, ch -> OggSink(output, rate, ch) }
                Converters.MediaPreset.TO_MP4 -> toMp4(src, output, 0, durationMs, onProgress)
                Converters.MediaPreset.COMPRESS_720 -> toMp4(src, output, 720, durationMs, onProgress)
                Converters.MediaPreset.COMPRESS_480 -> toMp4(src, output, 480, durationMs, onProgress)
                Converters.MediaPreset.TO_GIF -> toGif(src, output, durationMs, onProgress)
                Converters.MediaPreset.TO_MKV -> toMkv(src, output, durationMs, onProgress)
            }
        } finally {
            if (src != input) src.delete()
        }
    }

    /** Some phones refuse to open a file whose name extension does not match the contents. */
    private fun withPlayableName(input: File): File {
        val ext = sniffExt(input) ?: return input
        val current = input.extension.lowercase()
        val ok = when (ext) {
            "mp4" -> current in setOf("mp4", "m4a", "mov", "3gp", "m4v")
            "mkv" -> current in setOf("mkv", "webm")
            "ogg" -> current in setOf("ogg", "oga", "opus")
            else -> current == ext
        }
        if (ok) return input
        val renamed = File(input.parentFile, input.nameWithoutExtension + ".$ext")
        return if (input.copyTo(renamed, overwrite = true).length() > 0) renamed else input
    }

    private fun sniffExt(file: File): String? {
        val b = ByteArray(16)
        val n = FileInputStream(file).use { it.read(b) }
        if (n < 4) return null
        val head = String(b, 0, 4)
        return when {
            n >= 12 && head == "RIFF" && String(b, 8, 4) == "WAVE" -> "wav"
            head == "OggS" -> "ogg"
            head == "fLaC" -> "flac"
            head == "ID3" -> "mp3"
            b[0] == 0xFF.toByte() && (b[1].toInt() and 0xE0) == 0xE0 -> "mp3"
            n >= 8 && String(b, 4, 4) == "ftyp" -> "mp4"
            b[0] == 0x1A.toByte() && b[1] == 0x45.toByte() && b[2] == 0xDF.toByte() && b[3] == 0xA3.toByte() -> "mkv"
            else -> null
        }
    }

    private fun audio(input: File, durationMs: Long, onProgress: (Float) -> Unit, factory: (Int, Int) -> PcmSink) {
        var sink: PcmSink? = null
        try {
            decodePcm(input, durationMs, onProgress) { rate, channels, pcm ->
                if (sink == null) sink = factory(rate, channels)
                sink?.write(pcm)
            }
            if (sink == null) throw IllegalStateException("This file has no audio track.")
            sink?.finish()
        } finally {
            if (sink != null && sink?.finished == false) runCatching { sink?.release() }
        }
    }

    private fun toMp4(input: File, output: File, maxHeight: Int, durationMs: Long, onProgress: (Float) -> Unit) {
        requireVideo(input)
        if (maxHeight == 0 && canCopy(input)) {
            remuxMp4(input, output, durationMs, onProgress)
            if (videoHasSamples(output)) return
            output.delete()
        }
        transcodeMp4(input, output, maxHeight, durationMs, onProgress)
    }

    private fun toMkv(input: File, output: File, durationMs: Long, onProgress: (Float) -> Unit) {
        requireVideo(input)
        if (canCopy(input) && remuxMkv(input, output, durationMs, onProgress)) return
        val tmp = File(output.parentFile, output.nameWithoutExtension + "_mkv.mp4")
        try {
            transcodeMp4(input, tmp, 0, durationMs) { onProgress(it * 0.85f) }
            if (!remuxMkv(tmp, output, durationMs) { onProgress(0.85f + it * 0.15f) }) {
                throw IllegalStateException("Could not write the MKV file.")
            }
        } finally {
            tmp.delete()
        }
    }

    private fun toGif(input: File, output: File, durationMs: Long, onProgress: (Float) -> Unit) {
        requireVideo(input)
        if (gifFromRetriever(input, output, durationMs, onProgress) && output.length() > 32) return
        output.delete()
        if (!gifFromDecoder(input, output, durationMs, onProgress) || output.length() <= 32) {
            output.delete()
            throw IllegalStateException("Could not read the picture in this video.")
        }
    }

    /** One sequential decode of the first few seconds. Seeking each frame was slow and often black. */
    private fun gifFromDecoder(input: File, output: File, durationMs: Long, onProgress: (Float) -> Unit): Boolean {
        val ex = openExtractor(input)
        val stream = FileOutputStream(output)
        var gif: GifWriter? = null
        var decoder: MediaCodec? = null
        return try {
            val vIndex = findTrack(ex, "video/")
            if (vIndex < 0) return false
            ex.selectTrack(vIndex)
            val srcFmt = ex.getTrackFormat(vIndex)
            val mime = srcFmt.getString(MediaFormat.KEY_MIME) ?: return false
            val (srcW, srcH) = sizeOf(srcFmt)
            val rotation = if (srcFmt.containsKey(MediaFormat.KEY_ROTATION)) srcFmt.getInteger(MediaFormat.KEY_ROTATION) else 0
            val longSide = maxOf(srcW, srcH).coerceAtLeast(1)
            val scale = minOf(1f, 720f / longSide)
            var outW = even((srcW * scale).toInt()).coerceIn(2, 720)
            var outH = even((srcH * scale).toInt()).coerceIn(2, 720)
            if (rotation == 90 || rotation == 270) {
                val swap = outW
                outW = outH
                outH = swap
            }
            val limitUs = minOf((durationMs.takeIf { it > 0 } ?: 6_000L) * 1000L, 6_000_000L)
            val stepUs = 1_000_000L / 6
            val targetFrames = 24
            val dec = MediaCodec.createDecoderByType(mime)
            decoder = dec
            val flexible = videoDecoderFormat(srcFmt).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            }
            try {
                dec.configure(flexible, null, null, 0)
            } catch (e: Exception) {
                dec.reset()
                dec.configure(videoDecoderFormat(srcFmt), null, null, 0)
            }
            dec.start()
            var inputDone = false
            var outputDone = false
            var frames = 0
            var nextUs = 0L
            var idle = 0
            val info = MediaCodec.BufferInfo()
            while (!outputDone && frames < targetFrames) {
                var moved = false
                if (!inputDone) {
                    val inIndex = dec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = dec.getInputBuffer(inIndex)
                        if (buf == null) {
                            dec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val size = ex.readSampleData(buf, 0)
                            val pts = ex.sampleTime
                            if (size < 0 || (pts > limitUs && frames > 0)) {
                                dec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                dec.queueInputBuffer(inIndex, 0, size, pts.coerceAtLeast(0), 0)
                                ex.advance()
                            }
                        }
                        moved = true
                    }
                }
                val outIndex = dec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    moved = true
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val image = if (info.size > 0) dec.getOutputImage(outIndex) else null
                    if (image != null) {
                        try {
                            if (info.presentationTimeUs >= nextUs && frames < targetFrames) {
                                val raw = imageToBitmap(
                                    image,
                                    if (rotation == 90 || rotation == 270) outH else outW,
                                    if (rotation == 90 || rotation == 270) outW else outH
                                )
                                val frame = rotateBitmap(raw, rotation)
                                if (!looksBlank(frame)) {
                                    if (gif == null) gif = GifWriter(stream, frame.width, frame.height)
                                    gif.frame(frame, 16)
                                    frames++
                                    nextUs += stepUs
                                    onProgress(frames / targetFrames.toFloat())
                                }
                                if (frame !== raw) raw.recycle()
                                frame.recycle()
                            }
                        } finally {
                            image.close()
                        }
                    }
                    dec.releaseOutputBuffer(outIndex, false)
                    if (eos || info.presentationTimeUs > limitUs) outputDone = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) moved = true
                if (moved) idle = 0 else if (++idle > 40) break
            }
            gif?.close()
            frames > 0
        } catch (e: Exception) {
            false
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { stream.close() }
            ex.release()
        }
    }

    private fun gifFromRetriever(input: File, output: File, durationMs: Long, onProgress: (Float) -> Unit): Boolean {
        val retriever = MediaMetadataRetriever()
        var gif: GifWriter? = null
        val stream = FileOutputStream(output)
        return try {
            retriever.setDataSource(input.absolutePath)
            val durMs = durationMs.takeIf { it > 0 }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 6_000L
            val limitUs = minOf(durMs, 6_000L) * 1000L
            val stepUs = 200_000L
            var time = 0L
            var frames = 0
            while (time <= limitUs && frames < 20) {
                val grabbed = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)
                if (grabbed != null) {
                    val soft = softwareBitmap(grabbed)
                    if (soft !== grabbed) grabbed.recycle()
                    if (looksBlank(soft)) {
                        soft.recycle()
                    } else {
                        val frame = scaleBitmap(soft, 720)
                        if (frame !== soft) soft.recycle()
                        if (gif == null) gif = GifWriter(stream, frame.width.coerceAtLeast(2), frame.height.coerceAtLeast(2))
                        gif.frame(frame, 20)
                        frames++
                        onProgress(frames / 20f)
                        frame.recycle()
                    }
                }
                time += stepUs
            }
            if (gif == null || frames < 2) false
            else {
                gif.close()
                true
            }
        } catch (e: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
            runCatching { stream.close() }
        }
    }

    private fun softwareBitmap(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width.coerceAtLeast(2), src.height.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(src, 0f, 0f, null)
        return out
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        return out
    }

    private fun scaleBitmap(src: Bitmap, maxSide: Int): Bitmap {
        val longSide = maxOf(src.width, src.height).coerceAtLeast(1)
        if (longSide <= maxSide) return src
        val scale = maxSide.toFloat() / longSide
        return Bitmap.createScaledBitmap(src, even((src.width * scale).toInt()), even((src.height * scale).toInt()), true)
    }

    private fun looksBlank(bmp: Bitmap): Boolean {
        val stepX = (bmp.width / 6).coerceAtLeast(1)
        val stepY = (bmp.height / 6).coerceAtLeast(1)
        var bright = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                val sum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
                if (sum > 30 && ++bright >= 4) return false
                x += stepX
            }
            y += stepY
        }
        return true
    }

    /** Android's JPEG converter understands the phone's YUV layout. A hand-rolled read was coming out black. */
    private fun imageToBitmap(image: Image, outW: Int, outH: Int): Bitmap {
        val nv21 = yuvToNv21(image)
        val w = image.cropRect.width() and 1.inv()
        val h = image.cropRect.height() and 1.inv()
        if (w < 2 || h < 2 || nv21.size < w * h * 3 / 2) {
            return Bitmap.createBitmap(outW.coerceAtLeast(2), outH.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        }
        val jpeg = ByteArrayOutputStream()
        val ok = YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 70, jpeg)
        val decoded = if (ok) BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size()) else null
        if (decoded == null) {
            return Bitmap.createBitmap(outW.coerceAtLeast(2), outH.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        }
        val scaled = scaleBitmap(decoded, maxOf(outW, outH).coerceAtLeast(2))
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun yuvToNv21(image: Image): ByteArray {
        val crop = image.cropRect
        val width = crop.width() and 1.inv()
        val height = crop.height() and 1.inv()
        if (width < 2 || height < 2 || image.planes.size < 3) return ByteArray(0)
        val nv21 = ByteArray(width * height * 3 / 2)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer.duplicate().apply { clear() }
        val uBuf = uPlane.buffer.duplicate().apply { clear() }
        val vBuf = vPlane.buffer.duplicate().apply { clear() }
        var pos = 0
        for (row in 0 until height) {
            val yRow = (crop.top + row) * yPlane.rowStride + crop.left * yPlane.pixelStride
            if (yPlane.pixelStride == 1 && yRow >= 0 && yRow + width <= yBuf.capacity()) {
                yBuf.position(yRow)
                yBuf.get(nv21, pos, width)
                pos += width
            } else {
                for (col in 0 until width) {
                    val index = yRow + col * yPlane.pixelStride
                    nv21[pos++] = if (index in 0 until yBuf.capacity()) yBuf.get(index) else 0
                }
            }
        }
        var cPos = width * height
        val chromaH = height / 2
        val chromaW = width / 2
        for (row in 0 until chromaH) {
            for (col in 0 until chromaW) {
                val uIndex = (crop.top / 2 + row) * uPlane.rowStride + (crop.left / 2 + col) * uPlane.pixelStride
                val vIndex = (crop.top / 2 + row) * vPlane.rowStride + (crop.left / 2 + col) * vPlane.pixelStride
                nv21[cPos++] = if (vIndex in 0 until vBuf.capacity()) vBuf.get(vIndex) else 0
                nv21[cPos++] = if (uIndex in 0 until uBuf.capacity()) uBuf.get(uIndex) else 0
            }
        }
        return nv21
    }

    private fun requireVideo(input: File) {
        val ex = openExtractor(input)
        try {
            if (findTrack(ex, "video/") < 0) throw IllegalStateException("Choose a video for this format.")
        } finally {
            ex.release()
        }
    }

    private fun canCopy(input: File): Boolean {
        val ex = openExtractor(input)
        try {
            val v = findTrack(ex, "video/")
            if (v < 0) return false
            val videoMime = ex.getTrackFormat(v).getString(MediaFormat.KEY_MIME)
            val a = findTrack(ex, "audio/")
            val audioMime = if (a >= 0) ex.getTrackFormat(a).getString(MediaFormat.KEY_MIME) else null
            return videoMime == "video/avc" && (audioMime == null || audioMime == "audio/mp4a-latm")
        } finally {
            ex.release()
        }
    }

    private fun remuxMp4(input: File, output: File, durationMs: Long, onProgress: (Float) -> Unit) {
        val probe = openExtractor(input)
        val vIndex = findTrack(probe, "video/")
        val aIndex = findTrack(probe, "audio/")
        val vFmt = probe.getTrackFormat(vIndex)
        val aFmt = if (aIndex >= 0) probe.getTrackFormat(aIndex) else null
        val rotation = if (vFmt.containsKey(MediaFormat.KEY_ROTATION)) vFmt.getInteger(MediaFormat.KEY_ROTATION) else 0
        probe.release()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            if (rotation != 0) muxer.setOrientationHint(rotation)
            val vTrack = muxer.addTrack(vFmt)
            val aTrack = if (aFmt != null) muxer.addTrack(aFmt) else -1
            muxer.start()
            copySamples(input, vIndex, muxer, vTrack, true, durationMs, onProgress)
            if (aTrack >= 0) copySamples(input, aIndex, muxer, aTrack, false, durationMs) {}
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
        }
    }

    private fun remuxMkv(input: File, output: File, durationMs: Long, onProgress: (Float) -> Unit): Boolean {
        val probe = openExtractor(input)
        val vIndex = findTrack(probe, "video/")
        val aIndex = findTrack(probe, "audio/")
        if (vIndex < 0) { probe.release(); return false }
        val vFmt = probe.getTrackFormat(vIndex)
        val aFmt = if (aIndex >= 0) probe.getTrackFormat(aIndex) else null
        if (vFmt.getString(MediaFormat.KEY_MIME) != "video/avc") { probe.release(); return false }
        if (aFmt != null && aFmt.getString(MediaFormat.KEY_MIME) != "audio/mp4a-latm") { probe.release(); return false }
        val vCsd = codecPrivate(vFmt)
        if (vCsd.isEmpty()) { probe.release(); return false }
        val (w, h) = sizeOf(vFmt)
        val audio = if (aFmt != null) {
            val csd = codecPrivate(aFmt)
            if (csd.isEmpty()) { probe.release(); return false }
            MkvWriter.Audio(
                aFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                aFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                csd
            )
        } else null
        probe.release()
        val mkv = MkvWriter(output)
        try {
            mkv.start(MkvWriter.Video(w, h, vCsd), audio)
            copyToMkv(input, vIndex, true, mkv, durationMs, onProgress)
            if (audio != null) copyToMkv(input, aIndex, false, mkv, durationMs) {}
        } finally {
            runCatching { mkv.close() }
        }
        return output.length() > 0
    }

    private fun transcodeMp4(input: File, output: File, maxHeight: Int, durationMs: Long, onProgress: (Float) -> Unit) {
        val audioFile = File(output.parentFile, output.nameWithoutExtension + "_a.m4a")
        // AAC already in the source is copied. Decoding the whole track to PCM first made a
        // long video sit near the start of the bar, then the video pass still had to run.
        val copyAudio = audioMime(input) == "audio/mp4a-latm"
        var encodedAudio = false
        val hasAudio = if (copyAudio) true else try {
            audio(input, durationMs, { p -> onProgress((p * 0.12f).coerceIn(0f, 0.12f)) }) { rate, ch -> AacSink(audioFile, rate, ch) }
            encodedAudio = audioFile.exists() && audioFile.length() > 0
            encodedAudio
        } catch (e: IllegalStateException) {
            if (e.message?.startsWith("Could not read this media file") == true) throw e
            false
        }
        if (!encodedAudio) audioFile.delete()
        val audioPath = when {
            copyAudio -> input
            hasAudio -> audioFile
            else -> null
        }
        var shown = 0.12f
        fun videoProgress(fraction: Float) {
            val next = (0.12f + 0.88f * fraction.coerceIn(0f, 1f)).coerceAtLeast(shown)
            if (next > shown) {
                shown = next
                onProgress(next)
            }
        }
        try {
            // One hardware pass. A second full-length encode made the bar finish and start over,
            // and the frame-by-frame fallback never finished a multi-minute video.
            output.delete()
            val surfaceOk = runCatching {
                transcodeSurface(input, output, audioPath, maxHeight, durationMs, false, Int.MAX_VALUE, ::videoProgress)
            }.getOrDefault(false)
            if (surfaceOk && videoHasSamples(output)) return
            output.delete()
            // A second full-length encode is what made a 3-minute video sit on the bar and then fail.
            val decoded = runCatching {
                transcodeFromDecoder(input, output, audioPath, if (maxHeight <= 0) 720 else maxHeight, durationMs, ::videoProgress)
            }.getOrDefault(false)
            if (!decoded || !videoHasSamples(output)) {
                output.delete()
                throw IllegalStateException("Could not create a playable video.")
            }
        } finally {
            if (encodedAudio) audioFile.delete()
        }
    }

    /**
     * Decodes onto the encoder surface. Some phones report a buffer size of 0 for surface
     * frames; those frames still have to be rendered or the saved file has no picture.
     */
    private fun transcodeSurface(
        input: File, output: File, audioFile: File?, maxHeight: Int, durationMs: Long,
        software: Boolean, maxSamples: Int, onProgress: (Float) -> Unit
    ): Boolean {
        val ex = openExtractor(input)
        val vIndex = findTrack(ex, "video/")
        ex.selectTrack(vIndex)
        val srcFmt = ex.getTrackFormat(vIndex)
        val (width, height, rotation) = targetSize(srcFmt, maxHeight)
        val mime = srcFmt.getString(MediaFormat.KEY_MIME) ?: "video/avc"
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var scaler: SurfaceScaler? = null
        val pack = Mp4Pack(output)
        var muxerOn = false
        var videoSamples = 0
        try {
            val encFmt = surfaceEncoderFormat(width, height, maxHeight)
            val enc = openAvcEncoder(software)
            encoder = enc
            enc.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = enc.createInputSurface()
            enc.start()
            requestSyncFrame(enc)
            // A smaller surface fed straight from the decoder comes out as green lines.
            // Draw the full frame through GL when the picture is being resized.
            scaler = if (maxHeight > 0) {
                val (srcW, srcH) = sizeOf(srcFmt)
                SurfaceScaler(surface, srcW, srcH, width, height)
            } else null
            val dec = MediaCodec.createDecoderByType(mime)
            decoder = dec
            dec.configure(videoDecoderFormat(srcFmt), scaler?.inputSurface ?: surface, null, 0)
            dec.start()
            val audioExtractor = audioFile?.let { runCatching { openExtractor(it) }.getOrNull() }
            var audioBuf: ByteBuffer? = null
            if (audioExtractor != null) {
                val ai = findTrack(audioExtractor, "audio/")
                if (ai >= 0) {
                    audioExtractor.selectTrack(ai)
                    audioBuf = ByteBuffer.allocateDirect(256 * 1024)
                }
            }
            val pendingVideo = ArrayList<Sample>()
            var sawKey = false
            var extractorDone = false
            var decoderDone = false
            var encoderDone = false
            var signaled = false
            var spins = 0
            var rendered = 0
            var rejected = 0
            var lastPts = -1L
            val info = MediaCodec.BufferInfo()
            val wait = 100L
            while (!encoderDone) {
                var moved = false
                if (!extractorDone) {
                    val inIndex = dec.dequeueInputBuffer(wait)
                    if (inIndex >= 0) {
                        val buf = dec.getInputBuffer(inIndex)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            dec.queueInputBuffer(inIndex, 0, size, ex.sampleTime.coerceAtLeast(0), 0)
                            ex.advance()
                        }
                        moved = true
                    }
                }
                if (!decoderDone) {
                    val outIndex = dec.dequeueOutputBuffer(info, wait)
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) moved = true
                    else if (outIndex >= 0) {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val render = info.size > 0 && !config
                        if (render) {
                            rendered++
                            if (!sawKey) requestSyncFrame(enc)
                        }
                        dec.releaseOutputBuffer(outIndex, render)
                        if (render) scaler?.draw(info.presentationTimeUs)
                        if (eos && !signaled) {
                            enc.signalEndOfInputStream()
                            signaled = true
                            decoderDone = true
                        }
                        moved = true
                    }
                }
                val encIndex = enc.dequeueOutputBuffer(info, wait)
                when {
                    encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerOn) {
                            val sets = parameterSets(enc.outputFormat) ?: return false
                            pack.addVideo(width, height, rotation, sets.first, sets.second)
                            aacInfo(audioExtractor)?.let { pack.addAac(it.rate, it.channels, it.asc) }
                            muxerOn = true
                            for (s in pendingVideo) {
                                pack.video(s.data, s.ptsUs, s.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0)
                            }
                            pendingVideo.clear()
                        }
                        moved = true
                    }
                    encIndex >= 0 -> {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val data = avccSample(bytesOf(enc.getOutputBuffer(encIndex)!!, info))
                            val flags = keyFlags(data, info.flags, sawKey)
                            val key = flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                            if (key) sawKey = true
                            if (!sawKey) {
                                // The muxer drops every frame until byte 4 is an IDR. Writing
                                // those frames still takes the whole clip and then fails.
                                rejected++
                                if (rejected > 48) encoderDone = true
                            } else {
                                val rawPts = info.presentationTimeUs.coerceAtLeast(0)
                                val pts = if (rawPts <= lastPts) lastPts + 1 else rawPts
                                lastPts = pts
                                val sample = Sample(data, pts, flags)
                                if (muxerOn) pack.video(data, pts, key)
                                else pendingVideo.add(sample)
                                videoSamples++
                                if (videoSamples >= maxSamples) encoderDone = true
                                if (durationMs > 0 && maxSamples == Int.MAX_VALUE) {
                                    onProgress((sample.ptsUs / 1000f / durationMs).coerceIn(0f, 1f))
                                }
                            }
                        }
                        enc.releaseOutputBuffer(encIndex, false)
                        if (eos) encoderDone = true
                        moved = true
                    }
                }
                if (!moved) {
                    spins++
                    val limit = if (decoderDone) 80 else 1200
                    if (spins > limit) break
                } else spins = 0
                // The encoder stayed silent after many pictures. Stop and try another size.
                if (rendered > 24 && videoSamples == 0 && !muxerOn) break
            }
            if (muxerOn && audioExtractor != null && audioBuf != null) {
                writePackedAudio(audioExtractor, audioBuf, pack)
            }
            audioExtractor?.release()
            if (muxerOn && videoSamples > 0) pack.finish() else return false
            return videoSamples > 0
        } catch (e: Exception) {
            return false
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { scaler?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            pack.close()
            ex.release()
        }
    }

    /**
     * Decodes the video once and feeds an H.264 encoder. This stays fast on phones where
     * drawing onto an encoder surface produces no picture.
     */
    private fun transcodeFromDecoder(
        input: File, output: File, audioFile: File?, maxHeight: Int, durationMs: Long, onProgress: (Float) -> Unit
    ): Boolean {
        val ex = openExtractor(input)
        val vIndex = findTrack(ex, "video/")
        if (vIndex < 0) { ex.release(); return false }
        ex.selectTrack(vIndex)
        val srcFmt = ex.getTrackFormat(vIndex)
        val mime = srcFmt.getString(MediaFormat.KEY_MIME) ?: run { ex.release(); return false }
        val (width, height, rotation) = targetSize(srcFmt, maxHeight)
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        val pack = Mp4Pack(output)
        var videoSamples = 0
        try {
            val dec = MediaCodec.createDecoderByType(mime)
            decoder = dec
            val flexible = videoDecoderFormat(srcFmt).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            }
            try {
                dec.configure(flexible, null, null, 0)
            } catch (e: Exception) {
                dec.reset()
                dec.configure(videoDecoderFormat(srcFmt), null, null, 0)
            }
            dec.start()
            val colorChoice = encoderColorFormat()
            val enc = MediaCodec.createEncoderByType("video/avc")
            encoder = enc
            val encFmt = MediaFormat.createVideoFormat("video/avc", width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorChoice)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height, maxHeight))
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_OPERATING_RATE, 120)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
            enc.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            requestSyncFrame(enc)
            val inFmt = enc.inputFormat
            val stride = inFmt.intOr(MediaFormat.KEY_STRIDE, width).coerceAtLeast(width)
            val slice = inFmt.intOr(MediaFormat.KEY_SLICE_HEIGHT, height).coerceAtLeast(height)
            val planar = inFmt.intOr(MediaFormat.KEY_COLOR_FORMAT, colorChoice) ==
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            val audioExtractor = audioFile?.let { runCatching { openExtractor(it) }.getOrNull() }
            var muxerOn = false
            var lastQueuedUs = Long.MIN_VALUE / 4
            var extractorDone = false
            var decoderDone = false
            var encoderDone = false
            var sawKey = false
            var spins = 0
            val pending = ArrayList<Sample>()
            val info = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            fun drainEncoder(wait: Long) {
                while (!encoderDone) {
                    val out = enc.dequeueOutputBuffer(encInfo, wait)
                    when {
                        out == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                        out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerOn) {
                                val sets = parameterSets(enc.outputFormat) ?: return
                                pack.addVideo(width, height, rotation, sets.first, sets.second)
                                aacInfo(audioExtractor)?.let { pack.addAac(it.rate, it.channels, it.asc) }
                                muxerOn = true
                                for (s in pending) {
                                    pack.video(s.data, s.ptsUs, s.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0)
                                }
                                pending.clear()
                            }
                        }
                        out >= 0 -> {
                            val eos = encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            if (encInfo.size > 0 && encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val data = avccSample(bytesOf(enc.getOutputBuffer(out)!!, encInfo))
                                val flags = keyFlags(data, encInfo.flags, sawKey)
                                val key = flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                                if (key) sawKey = true
                                val sample = Sample(data, encInfo.presentationTimeUs.coerceAtLeast(0), flags)
                                if (muxerOn) pack.video(data, sample.ptsUs, key)
                                else pending.add(sample)
                                videoSamples++
                                if (durationMs > 0) onProgress((sample.ptsUs / 1000f / durationMs).coerceIn(0f, 1f))
                            }
                            enc.releaseOutputBuffer(out, false)
                            if (eos) encoderDone = true
                        }
                        else -> return
                    }
                }
            }
            fun queueYuv(yuv: ByteArray, pts: Long, eos: Boolean): Boolean {
                var inIndex = enc.dequeueInputBuffer(0)
                if (inIndex < 0) {
                    drainEncoder(TIMEOUT)
                    inIndex = enc.dequeueInputBuffer(TIMEOUT)
                }
                if (inIndex < 0) return false
                val buf = enc.getInputBuffer(inIndex)!!
                buf.clear()
                val size = minOf(yuv.size, buf.remaining())
                buf.put(yuv, 0, size)
                val flags = (if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                enc.queueInputBuffer(inIndex, 0, size, pts.coerceAtLeast(0), flags)
                return true
            }
            while (!encoderDone) {
                var moved = false
                if (!extractorDone) {
                    val inIndex = dec.dequeueInputBuffer(TIMEOUT)
                    if (inIndex >= 0) {
                        val buf = dec.getInputBuffer(inIndex)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            dec.queueInputBuffer(inIndex, 0, size, ex.sampleTime.coerceAtLeast(0), 0)
                            ex.advance()
                        }
                        moved = true
                    }
                }
                if (!decoderDone) {
                    val outIndex = dec.dequeueOutputBuffer(info, if (extractorDone) TIMEOUT else 0)
                    if (outIndex >= 0) {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val keep = eos || info.presentationTimeUs - lastQueuedUs >= 100_000L
                        if (!config && keep) {
                            val image = dec.getOutputImage(outIndex)
                            if (image != null) {
                                val yuv = if (planar) imageToI420(image, width, height, stride, slice)
                                else imageToNv12(image, width, height, stride, slice)
                                image.close()
                                if (queueYuv(yuv, info.presentationTimeUs, eos)) lastQueuedUs = info.presentationTimeUs
                            } else if (eos) {
                                queueYuv(ByteArray(stride * slice * 3 / 2), info.presentationTimeUs, true)
                            }
                        } else if (!config) {
                            dec.getOutputImage(outIndex)?.close()
                        } else if (eos) {
                            queueYuv(ByteArray(stride * slice * 3 / 2), 0, true)
                        }
                        dec.releaseOutputBuffer(outIndex, false)
                        if (eos) decoderDone = true
                        moved = true
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) moved = true
                }
                drainEncoder(0)
                if (!moved) {
                    spins++
                    if (spins > 80 && (decoderDone || extractorDone)) break
                    if (spins > 400) break
                } else spins = 0
                if (decoderDone && !encoderDone) drainEncoder(TIMEOUT)
            }
            if (muxerOn && audioExtractor != null) {
                writePackedAudio(audioExtractor, ByteBuffer.allocateDirect(256 * 1024), pack)
            }
            audioExtractor?.release()
            if (muxerOn && videoSamples > 0) pack.finish()
            return videoSamples > 0
        } catch (e: Exception) {
            return false
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            pack.close()
            ex.release()
        }
    }

    /** Draws decoded frames into an H.264 file when the phone cannot encode from a surface. */
    private fun transcodeFromFrames(
        input: File, output: File, audioFile: File?, maxHeight: Int, durationMs: Long, onProgress: (Float) -> Unit
    ) {
        val retriever = MediaMetadataRetriever()
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var shown: Bitmap? = null
        try {
            retriever.setDataSource(input.absolutePath)
            val durMs = durationMs.takeIf { it > 0 }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw IllegalStateException("Could not read this video.")
            val first = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw IllegalStateException("This video format is not supported on this phone.")
            val fitted = fitBitmap(first, maxHeight)
            if (fitted != first) first.recycle()
            shown = fitted
            val width = fitted.width
            val height = fitted.height
            val color = encoderColorFormat()
            val enc = MediaCodec.createEncoderByType("video/avc")
            encoder = enc
            val encFmt = MediaFormat.createVideoFormat("video/avc", width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, color)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height, maxHeight))
                setInteger(MediaFormat.KEY_FRAME_RATE, 12)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height * 3 / 2)
            }
            try {
                enc.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (e: Exception) {
                throw IllegalStateException("This video format is not supported on this phone.")
            }
            enc.start()
            requestSyncFrame(enc)
            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val audioExtractor = audioFile?.let { runCatching { openExtractor(it) }.getOrNull() }
            var videoTrack = -1
            var audioTrack = -1
            var muxerOn = false
            var encoderDone = false
            var sawKey = false
            val pending = ArrayList<Sample>()
            val info = MediaCodec.BufferInfo()
            fun drain(wait: Long) {
                while (!encoderDone) {
                    val out = enc.dequeueOutputBuffer(info, wait)
                    when {
                        out == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                        out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerOn) {
                                videoTrack = mux.addTrack(enc.outputFormat)
                                audioTrack = addAudioTrack(mux, audioExtractor)
                                mux.start()
                                muxerOn = true
                                for (s in pending) mux.writeSampleData(videoTrack, ByteBuffer.wrap(s.data), s.info())
                                pending.clear()
                            }
                        }
                        out >= 0 -> {
                            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val data = avccSample(bytesOf(enc.getOutputBuffer(out)!!, info))
                                val flags = keyFlags(data, info.flags, sawKey)
                                if (flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) sawKey = true
                                val sample = Sample(data, info.presentationTimeUs.coerceAtLeast(0), flags)
                                if (muxerOn) mux.writeSampleData(videoTrack, ByteBuffer.wrap(data), sample.info())
                                else pending.add(sample)
                            }
                            enc.releaseOutputBuffer(out, false)
                            if (eos) encoderDone = true
                        }
                        else -> return
                    }
                }
            }
            val fps = if (durMs > 60_000) 8 else 12
            val stepUs = 1_000_000L / fps
            val limitUs = durMs * 1000L
            var timeUs = 0L
            var index = 0
            while (timeUs < limitUs && index < fps * 60 * 10) {
                if (index > 0) {
                    val next = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (next != null) {
                        val scaled = fitBitmap(next, width, height)
                        if (scaled != next) next.recycle()
                        if (shown != scaled) shown?.recycle()
                        shown = scaled
                    }
                }
                val picture = shown ?: break
                val yuv = if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) bitmapToI420(picture) else bitmapToNv12(picture)
                var inIndex = enc.dequeueInputBuffer(0)
                if (inIndex < 0) {
                    drain(TIMEOUT)
                    inIndex = enc.dequeueInputBuffer(TIMEOUT)
                }
                if (inIndex >= 0) {
                    val buf = enc.getInputBuffer(inIndex)!!
                    buf.clear()
                    val size = minOf(yuv.size, buf.remaining())
                    buf.put(yuv, 0, size)
                    val last = timeUs + stepUs >= limitUs || index + 1 >= fps * 60 * 10
                    val flags = MediaCodec.BUFFER_FLAG_KEY_FRAME or
                        (if (last) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    enc.queueInputBuffer(inIndex, 0, size, timeUs, flags)
                }
                drain(0)
                index++
                onProgress((0.25f + 0.75f * (timeUs.toFloat() / limitUs)).coerceIn(0.25f, 1f))
                timeUs += stepUs
            }
            var spins = 0
            while (!encoderDone && spins++ < 40) drain(200)
            if (muxerOn && audioTrack >= 0 && audioExtractor != null) {
                writeAudioSamples(audioExtractor, ByteBuffer.allocateDirect(256 * 1024), mux, audioTrack)
            }
            audioExtractor?.release()
            if (muxerOn) mux.stop()
        } finally {
            shown?.recycle()
            runCatching { retriever.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
        }
    }

    private fun requestSyncFrame(enc: MediaCodec) {
        runCatching {
            enc.setParameters(android.os.Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 1)
            })
        }
    }

    /** This phone keeps a frame only when the sample itself is an IDR, not when the flag is set. */
    private fun keyFlags(data: ByteArray, flags: Int, sawKey: Boolean): Int {
        val key = isAvcKey(data)
        return if (key) flags or MediaCodec.BUFFER_FLAG_KEY_FRAME else flags and MediaCodec.BUFFER_FLAG_KEY_FRAME.inv()
    }

    private fun openAvcEncoder(software: Boolean): MediaCodec {
        if (software) {
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            val soft = infos.firstOrNull { info ->
                info.isEncoder &&
                    info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) } &&
                    (info.name.startsWith("c2.android") || info.name.startsWith("OMX.google"))
            }
            if (soft != null) return MediaCodec.createByCodecName(soft.name)
        }
        return MediaCodec.createEncoderByType("video/avc")
    }

    /**
     * This phone's muxer keeps a frame only when byte 4 is an IDR NAL. Annex-B sync frames and
     * frames that start with SPS are dropped, so the saved file has no picture.
     */
    private fun avccSample(data: ByteArray): ByteArray {
        val nals = nalUnits(data)
        if (nals.isEmpty()) return data
        val idr = nals.indexOfFirst { it.isNotEmpty() && (it[0].toInt() and 0x1F) == 5 }
        val use = if (idr >= 0) nals.subList(idr, nals.size) else nals
        val out = java.io.ByteArrayOutputStream(data.size + use.size * 4)
        for (nal in use) {
            val len = nal.size
            out.write(len ushr 24)
            out.write(len ushr 16)
            out.write(len ushr 8)
            out.write(len)
            out.write(nal)
        }
        val bytes = out.toByteArray()
        return if (bytes.isEmpty()) data else bytes
    }

    private fun nalUnits(data: ByteArray): List<ByteArray> {
        if (data.size < 3) return emptyList()
        val annex = (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte()) ||
            (data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte())
        if (annex) {
            val starts = ArrayList<Int>()
            var i = 0
            while (i + 3 < data.size) {
                val three = data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()
                val four = i + 4 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                    data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()
                if (four) { starts.add(i + 4); i += 4 }
                else if (three) { starts.add(i + 3); i += 3 }
                else i++
            }
            return starts.mapIndexed { idx, s ->
                val end = if (idx + 1 < starts.size) {
                    var cut = starts[idx + 1]
                    if (cut >= 4 && data[cut - 1] == 1.toByte() && data[cut - 2] == 0.toByte() && data[cut - 3] == 0.toByte() && data[cut - 4] == 0.toByte()) cut -= 4
                    else if (cut >= 3 && data[cut - 1] == 1.toByte() && data[cut - 2] == 0.toByte() && data[cut - 3] == 0.toByte()) cut -= 3
                    cut.coerceAtLeast(s)
                } else data.size
                data.copyOfRange(s, end)
            }.filter { it.isNotEmpty() }
        }
        val out = ArrayList<ByteArray>()
        var i = 0
        while (i + 4 <= data.size) {
            val len = ((data[i].toInt() and 0xFF) shl 24) or
                ((data[i + 1].toInt() and 0xFF) shl 16) or
                ((data[i + 2].toInt() and 0xFF) shl 8) or
                (data[i + 3].toInt() and 0xFF)
            if (len <= 0 || i + 4 + len > data.size) break
            out.add(data.copyOfRange(i + 4, i + 4 + len))
            i += 4 + len
        }
        return out
    }

    private fun isAvcKey(data: ByteArray): Boolean {
        if (data.size < 5) return false
        val nal = if (data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte()) data[3] else data[4]
        return (nal.toInt() and 0x1F) == 5
    }

    private fun videoDecoderFormat(src: MediaFormat): MediaFormat {
        val mime = src.getString(MediaFormat.KEY_MIME) ?: "video/avc"
        val (w, h) = sizeOf(src)
        val fmt = MediaFormat.createVideoFormat(mime, w, h)
        for (key in listOf("csd-0", "csd-1", "csd-2")) {
            src.getByteBuffer(key)?.let { fmt.setByteBuffer(key, it.duplicate()) }
        }
        if (src.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, src.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
        }
        if (src.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, src.getInteger(MediaFormat.KEY_FRAME_RATE))
        }
        return fmt
    }

    /** The raw extractor format carries keys that make this phone's audio decoder fail with no message. */
    private fun audioDecoderFormat(src: MediaFormat): MediaFormat {
        val mime = src.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
        val rate = src.intOr(MediaFormat.KEY_SAMPLE_RATE, 44100).takeIf { it > 0 } ?: 44100
        val channels = src.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceIn(1, 8)
        val fmt = MediaFormat.createAudioFormat(mime, rate, channels)
        var hasCsd = false
        for (key in listOf("csd-0", "csd-1")) {
            val raw = src.getByteBuffer(key) ?: continue
            val copy = copyCsd(raw)
            if (copy.remaining() in 1..16) {
                fmt.setByteBuffer(key, copy)
                hasCsd = true
            }
        }
        if (mime.contains("mp4a")) {
            if (!hasCsd) fmt.setByteBuffer("csd-0", aacConfig(rate, channels))
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, 2)
            fmt.setInteger(MediaFormat.KEY_IS_ADTS, 0)
        }
        fmt.setInteger(
            MediaFormat.KEY_MAX_INPUT_SIZE,
            src.intOr(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024).coerceIn(16 * 1024, 1024 * 1024)
        )
        return fmt
    }

    private fun copyCsd(src: ByteBuffer): ByteBuffer {
        val d = src.duplicate()
        if (d.remaining() <= 0) return ByteBuffer.allocate(0)
        val n = d.remaining().coerceAtMost(64)
        val bytes = ByteArray(n)
        d.get(bytes)
        return ByteBuffer.wrap(bytes)
    }

    private fun aacConfig(rate: Int, channels: Int): ByteBuffer {
        val rates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
        val idx = rates.indexOf(rate).let { if (it >= 0) it else 4 }
        val ch = channels.coerceIn(1, 6)
        val b0 = (2 shl 3) or (idx shr 1)
        val b1 = ((idx and 1) shl 7) or (ch shl 3)
        return ByteBuffer.wrap(byteArrayOf(b0.toByte(), b1.toByte()))
    }

    private fun surfaceEncoderFormat(width: Int, height: Int, maxHeight: Int): MediaFormat =
        MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height, maxHeight))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger(MediaFormat.KEY_OPERATING_RATE, if (maxHeight > 0) 30 else 120)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            if (maxHeight > 0 && Build.VERSION.SDK_INT >= 24) {
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            }
            if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
        }

    private fun encoderColorFormat(): Int {
        val semi = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        val planar = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        return try {
            val enc = MediaCodec.createEncoderByType("video/avc")
            val formats = enc.codecInfo.getCapabilitiesForType("video/avc").colorFormats
            enc.release()
            if (semi in formats) semi else if (planar in formats) planar else semi
        } catch (e: Exception) {
            semi
        }
    }

    private class AacInfo(val rate: Int, val channels: Int, val asc: ByteArray)

    private fun parameterSets(fmt: MediaFormat): Pair<ByteArray, ByteArray>? {
        val nals = ArrayList<ByteArray>()
        for (key in listOf("csd-0", "csd-1", "csd-2")) {
            val raw = fmt.getByteBuffer(key) ?: continue
            val d = raw.duplicate()
            val bytes = ByteArray(d.remaining())
            if (bytes.isEmpty()) continue
            d.get(bytes)
            if (bytes[0] == 1.toByte() && bytes.size > 7) nals.addAll(nalsFromAvcC(bytes))
            val parts = nalUnits(bytes)
            if (parts.isEmpty()) nals.add(bytes) else nals.addAll(parts)
        }
        val sps = nals.firstOrNull { it.isNotEmpty() && (it[0].toInt() and 0x1F) == 7 } ?: return null
        val pps = nals.firstOrNull { it.isNotEmpty() && (it[0].toInt() and 0x1F) == 8 } ?: return null
        return sps to pps
    }

    private fun nalsFromAvcC(data: ByteArray): List<ByteArray> {
        if (data.size < 7 || data[0] != 1.toByte()) return emptyList()
        val out = ArrayList<ByteArray>()
        var i = 5
        val nSps = data[i].toInt() and 0x1F
        i++
        repeat(nSps) {
            if (i + 2 > data.size) return out
            val len = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            if (len <= 0 || i + len > data.size) return out
            out.add(data.copyOfRange(i, i + len))
            i += len
        }
        if (i >= data.size) return out
        val nPps = data[i].toInt() and 0xFF
        i++
        repeat(nPps) {
            if (i + 2 > data.size) return out
            val len = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            if (len <= 0 || i + len > data.size) return out
            out.add(data.copyOfRange(i, i + len))
            i += len
        }
        return out
    }

    private fun aacInfo(audio: MediaExtractor?): AacInfo? {
        if (audio == null) return null
        val index = findTrack(audio, "audio/")
        if (index < 0) return null
        val src = audio.getTrackFormat(index)
        val mime = src.getString(MediaFormat.KEY_MIME) ?: return null
        if (!mime.contains("mp4a")) return null
        val rate = src.intOr(MediaFormat.KEY_SAMPLE_RATE, 44100).takeIf { it > 0 } ?: 44100
        val channels = src.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceIn(1, 2)
        val csd = src.getByteBuffer("csd-0")
        val asc = if (csd != null && csd.remaining() in 2..16) {
            val d = csd.duplicate()
            ByteArray(d.remaining()).also { d.get(it) }
        } else {
            val bb = aacConfig(rate, channels)
            ByteArray(bb.remaining()).also { bb.get(it) }
        }
        return AacInfo(rate, channels, asc)
    }

    private fun writePackedAudio(audio: MediaExtractor, buf: ByteBuffer, pack: Mp4Pack) {
        while (true) {
            buf.clear()
            val n = audio.readSampleData(buf, 0)
            if (n <= 0) break
            val bytes = ByteArray(n)
            val view = buf.duplicate()
            view.position(0)
            view.limit(n)
            view.get(bytes)
            pack.audio(bytes, audio.sampleTime.coerceAtLeast(0))
            audio.advance()
        }
    }

    private fun addAudioTrack(mux: MediaMuxer, audio: MediaExtractor?): Int {
        if (audio == null) return -1
        val index = findTrack(audio, "audio/")
        if (index < 0) return -1
        val src = audio.getTrackFormat(index)
        val mime = src.getString(MediaFormat.KEY_MIME) ?: return -1
        val fmt = MediaFormat.createAudioFormat(mime, src.intOr(MediaFormat.KEY_SAMPLE_RATE, 44100), src.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1))
        src.getByteBuffer("csd-0")?.let { fmt.setByteBuffer("csd-0", it.duplicate()) }
        if (src.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, src.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
        }
        return mux.addTrack(fmt)
    }

    private fun writeAudioSamples(audio: MediaExtractor, buf: ByteBuffer, mux: MediaMuxer, track: Int) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            buf.clear()
            val n = audio.readSampleData(buf, 0)
            if (n < 0) break
            info.set(0, n, audio.sampleTime.coerceAtLeast(0), MediaCodec.BUFFER_FLAG_KEY_FRAME)
            mux.writeSampleData(track, buf, info)
            audio.advance()
        }
    }

    private fun audioMime(file: File): String? {
        val ex = runCatching { openExtractor(file) }.getOrNull() ?: return null
        return try {
            val index = findTrack(ex, "audio/")
            if (index < 0) null else ex.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
        } finally {
            ex.release()
        }
    }

    /** A real encoded frame is far larger than a few hundred bytes. Reading into a tiny buffer reports no video. */
    private fun videoHasSamples(file: File): Boolean {
        if (!file.exists() || file.length() < 1024) return false
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(file.absolutePath)
            val v = findTrack(ex, "video/")
            if (v < 0) false
            else {
                ex.selectTrack(v)
                ex.sampleTime >= 0
            }
        } catch (e: Exception) {
            false
        } finally {
            runCatching { ex.release() }
        }
    }

    private fun fitBitmap(src: Bitmap, maxHeight: Int): Bitmap {
        var w = even(src.width)
        var h = even(src.height)
        if (maxHeight > 0 && src.height > maxHeight) {
            val scale = maxHeight.toFloat() / src.height
            w = even((src.width * scale).toInt())
            h = even(maxHeight)
        }
        w = align16(w)
        h = align16(h)
        return if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun fitBitmap(src: Bitmap, width: Int, height: Int): Bitmap =
        if (src.width == width && src.height == height) src else Bitmap.createScaledBitmap(src, width, height, true)

    private fun bitmapToNv12(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        val ySize = w * h
        val out = ByteArray(ySize + ySize / 2)
        var uv = ySize
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = argb[j * w + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[j * w + i] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0 && uv + 1 < out.size) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    out[uv++] = u.coerceIn(0, 255).toByte()
                    out[uv++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return out
    }

    private fun bitmapToI420(bmp: Bitmap): ByteArray {
        val nv12 = bitmapToNv12(bmp)
        val ySize = bmp.width * bmp.height
        val chroma = ySize / 4
        val out = ByteArray(nv12.size)
        nv12.copyInto(out, 0, 0, ySize)
        var u = ySize
        var v = ySize + chroma
        var i = ySize
        while (i + 1 < nv12.size && v < out.size) {
            out[u++] = nv12[i]
            out[v++] = nv12[i + 1]
            i += 2
        }
        return out
    }

    private fun imageToNv12(image: Image, width: Int, height: Int, stride: Int, slice: Int): ByteArray {
        val ySize = stride * slice
        val out = ByteArray(ySize + stride * slice / 2)
        val crop = image.cropRect
        val planes = image.planes
        val copyW = minOf(crop.width(), width)
        val copyH = minOf(crop.height(), height)
        for (y in 0 until copyH) {
            for (x in 0 until copyW) out[y * stride + x] = planeByte(planes[0], y, x).toByte()
        }
        val chromaW = copyW / 2
        val chromaH = copyH / 2
        for (y in 0 until chromaH) {
            for (x in 0 until chromaW) {
                val o = ySize + y * stride + x * 2
                if (o + 1 < out.size) {
                    out[o] = planeByte(planes[1], y, x).toByte()
                    out[o + 1] = planeByte(planes[2], y, x).toByte()
                }
            }
        }
        return out
    }

    private fun imageToI420(image: Image, width: Int, height: Int, stride: Int, slice: Int): ByteArray {
        val nv12 = imageToNv12(image, width, height, stride, slice)
        val ySize = stride * slice
        val chroma = ySize / 4
        val out = ByteArray(nv12.size)
        nv12.copyInto(out, 0, 0, ySize)
        var u = ySize
        var v = ySize + chroma
        var i = ySize
        while (i + 1 < nv12.size && v < out.size) {
            out[u++] = nv12[i]
            out[v++] = nv12[i + 1]
            i += 2
        }
        return out
    }

    private fun planeByte(plane: Image.Plane, row: Int, col: Int): Int {
        val buf = plane.buffer
        val index = buf.position() + row * plane.rowStride + col * plane.pixelStride
        if (index < 0 || index >= buf.limit()) return 0
        return buf.get(index).toInt() and 0xFF
    }

    /** Matroska stores raw NAL units. MediaExtractor hands us MP4 length-prefixed NALs. */
    private fun avccToNals(data: ByteArray): ByteArray {
        if (data.size < 5) return data
        val out = java.io.ByteArrayOutputStream(data.size)
        var i = 0
        while (i + 4 <= data.size) {
            val len = ((data[i].toInt() and 0xFF) shl 24) or
                ((data[i + 1].toInt() and 0xFF) shl 16) or
                ((data[i + 2].toInt() and 0xFF) shl 8) or
                (data[i + 3].toInt() and 0xFF)
            if (len <= 0 || i + 4 + len > data.size) return data
            out.write(data, i + 4, len)
            i += 4 + len
        }
        val raw = out.toByteArray()
        return if (i == data.size && raw.isNotEmpty()) raw else data
    }

    private fun decodePcm(input: File, durationMs: Long, onProgress: (Float) -> Unit, onChunk: (Int, Int, ShortArray) -> Unit) {
        if (readWav(input, durationMs, onProgress, onChunk)) return
        val ex = openExtractor(input)
        try {
            val index = findTrack(ex, "audio/")
            if (index < 0) throw IllegalStateException("This file has no audio track.")
            ex.selectTrack(index)
            val fmt = ex.getTrackFormat(index)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime == "audio/raw" || mime == "audio/wav" || mime == "audio/x-wav") {
                readRaw(ex, fmt, durationMs, onProgress, onChunk)
                return
            }
            val decoder = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw IllegalStateException("This audio format is not supported on this phone.")
            }
            try {
                val rate0 = fmt.intOr(MediaFormat.KEY_SAMPLE_RATE, 44100).takeIf { it > 0 } ?: 44100
                val channels0 = fmt.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceIn(1, 2)
                try {
                    decoder.configure(audioDecoderFormat(fmt), null, null, 0)
                    decoder.start()
                } catch (e: Exception) {
                    runCatching { decoder.reset() }
                    try {
                        val bare = MediaFormat.createAudioFormat(mime, rate0, channels0)
                        if (mime.contains("mp4a")) {
                            bare.setByteBuffer("csd-0", aacConfig(rate0, channels0))
                            bare.setInteger(MediaFormat.KEY_AAC_PROFILE, 2)
                        }
                        bare.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024)
                        decoder.configure(bare, null, null, 0)
                        decoder.start()
                    } catch (e2: Exception) {
                        throw IllegalStateException("Could not read the audio in this file.")
                    }
                }
                var rate = rate0
                var channels = channels0
                var inputDone = false
                var outputDone = false
                var idle = 0
                val info = MediaCodec.BufferInfo()
                while (!outputDone) {
                    var moved = false
                    if (!inputDone) {
                        val inIndex = decoder.dequeueInputBuffer(200)
                        if (inIndex >= 0) {
                            moved = true
                            val buf = decoder.getInputBuffer(inIndex)
                            if (buf == null) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                                continue
                            }
                            val size = ex.readSampleData(buf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val pts = ex.sampleTime.coerceAtLeast(0)
                                decoder.queueInputBuffer(inIndex, 0, size, pts, 0)
                                ex.advance()
                                if (durationMs > 0) onProgress((pts / 1000f / durationMs).coerceIn(0f, 1f))
                            }
                        }
                    }
                    val outIndex = decoder.dequeueOutputBuffer(info, 200)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val o = decoder.outputFormat
                            rate = o.intOr(MediaFormat.KEY_SAMPLE_RATE, rate)
                            channels = o.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                            moved = true
                        }
                        outIndex >= 0 -> {
                            moved = true
                            val outBuf = decoder.getOutputBuffer(outIndex)
                            if (info.size > 0 && outBuf != null) {
                                val encoding = if (Build.VERSION.SDK_INT >= 24 && decoder.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                    decoder.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else 2
                                val shorts = pcmShorts(outBuf, info, encoding == 4)
                                val (mixed, ch) = downmix(shorts, channels)
                                if (mixed.isNotEmpty()) onChunk(rate, ch, mixed)
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                    if (moved) idle = 0
                    else if (++idle > 40) throw IllegalStateException("Could not decode this audio. Try another file.")
                }
            } catch (e: IllegalStateException) {
                val detail = e.message?.trim().orEmpty()
                if (detail.isEmpty() || detail.equals("unknown error", true)) {
                    throw IllegalStateException("Could not decode this audio. Try another file.")
                }
                throw e
            } finally {
                runCatching { decoder.stop() }
                runCatching { decoder.release() }
            }
        } finally {
            ex.release()
        }
    }

    private fun readRaw(ex: MediaExtractor, fmt: MediaFormat, durationMs: Long, onProgress: (Float) -> Unit, onChunk: (Int, Int, ShortArray) -> Unit) {
        val rate = fmt.intOr(MediaFormat.KEY_SAMPLE_RATE, 44100)
        val channels = fmt.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
        val buf = ByteBuffer.allocateDirect(64 * 1024)
        while (true) {
            val n = ex.readSampleData(buf, 0)
            if (n <= 0) break
            val bytes = ByteArray(n)
            buf.position(0)
            buf.get(bytes, 0, n)
            val shorts = ShortArray(n / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            val (mixed, ch) = downmix(shorts, channels)
            if (mixed.isNotEmpty()) onChunk(rate, ch, mixed)
            if (durationMs > 0) onProgress((ex.sampleTime / 1000f / durationMs).coerceIn(0f, 1f))
            ex.advance()
        }
    }

    private fun copySamples(input: File, track: Int, muxer: MediaMuxer, dst: Int, video: Boolean, durationMs: Long, onProgress: (Float) -> Unit) {
        val ex = openExtractor(input)
        try {
            ex.selectTrack(track)
            val buf = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                var flags = 0
                if (video && ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
                info.set(0, n, ex.sampleTime.coerceAtLeast(0), flags)
                muxer.writeSampleData(dst, buf, info)
                if (durationMs > 0 && video) onProgress((info.presentationTimeUs / 1000f / durationMs).coerceIn(0f, 1f))
                ex.advance()
            }
        } finally {
            ex.release()
        }
    }

    private fun copyToMkv(input: File, track: Int, video: Boolean, mkv: MkvWriter, durationMs: Long, onProgress: (Float) -> Unit) {
        val ex = openExtractor(input)
        try {
            ex.selectTrack(track)
            val buf = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            while (true) {
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                val data = ByteArray(n)
                buf.position(0)
                buf.get(data, 0, n)
                val pts = ex.sampleTime.coerceAtLeast(0)
                if (video) mkv.videoSample(avccToNals(data), pts, ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                else mkv.audioSample(data, pts)
                if (durationMs > 0 && video) onProgress((pts / 1000f / durationMs).coerceIn(0f, 1f))
                ex.advance()
            }
        } finally {
            ex.release()
        }
    }

    private fun openExtractor(file: File): MediaExtractor {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
        } catch (e: Exception) {
            ex.release()
            throw IllegalStateException("Could not read this media file. Choose a video or audio file and try again.")
        }
        if (ex.trackCount == 0) {
            ex.release()
            throw IllegalStateException("Could not read this media file. Choose a video or audio file and try again.")
        }
        return ex
    }

    private fun findTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private fun sizeOf(fmt: MediaFormat): Pair<Int, Int> {
        var w = fmt.intOr(MediaFormat.KEY_WIDTH, 0)
        var h = fmt.intOr(MediaFormat.KEY_HEIGHT, 0)
        if (fmt.containsKey("crop-right") && fmt.containsKey("crop-left")) {
            w = fmt.getInteger("crop-right") - fmt.getInteger("crop-left") + 1
        }
        if (fmt.containsKey("crop-bottom") && fmt.containsKey("crop-top")) {
            h = fmt.getInteger("crop-bottom") - fmt.getInteger("crop-top") + 1
        }
        return w.coerceAtLeast(2) to h.coerceAtLeast(2)
    }

    private fun targetSize(fmt: MediaFormat, maxHeight: Int): Triple<Int, Int, Int> {
        var (w, h) = sizeOf(fmt)
        val rotation = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else 0
        if (maxHeight > 0) {
            // 720p fits in 1280×720. 480p fits in 854×480. A tall phone video
            // shrinks on both sides, so it is lighter than the original.
            val longSide = if (maxHeight <= 480) 854 else 1280
            val displayW = if (rotation == 90 || rotation == 270) h else w
            val displayH = if (rotation == 90 || rotation == 270) w else h
            val scaleW = longSide.toFloat() / displayW.coerceAtLeast(1)
            val scaleH = maxHeight.toFloat() / displayH.coerceAtLeast(1)
            val scale = minOf(scaleW, scaleH, 1f)
            if (scale < 0.98f) {
                w = (w * scale).toInt()
                h = (h * scale).toInt()
            }
        }
        return Triple(align16(w), align16(h), rotation)
    }

    private fun even(v: Int) = v.coerceAtLeast(2) and 1.inv()

    /**
     * MediaTek's AVC encoder drops every frame when the size is not a multiple of 16.
     * Rounding 1080 up to 1088 is past the encoder limit, so the file ends up with no picture.
     */
    private fun align16(v: Int): Int {
        val x = v.coerceIn(16, 1920)
        val down = x and 15.inv()
        return down.coerceAtLeast(16)
    }

    /**
     * A fixed 1.8 Mbps on a small 720p frame still looks like the original.
     * Tie the rate to the picture size so 720p and 480p are actually lighter.
     */
    private fun bitrateFor(w: Int, h: Int, maxHeight: Int): Int {
        val pixels = w.toLong() * h.toLong()
        return when {
            maxHeight in 1..480 -> (pixels * 2).toInt().coerceIn(280_000, 800_000)
            maxHeight in 481..720 -> (pixels * 3).toInt().coerceIn(450_000, 1_600_000)
            else -> (pixels * 6).toInt().coerceIn(800_000, 8_000_000)
        }
    }

    private fun codecPrivate(fmt: MediaFormat): ByteArray {
        val parts = listOf("csd-0", "csd-1", "csd-2").mapNotNull { key ->
            fmt.getByteBuffer(key)?.let { bb ->
                val d = bb.duplicate()
                ByteArray(d.remaining()).also { d.get(it) }
            }
        }
        var n = 0
        parts.forEach { n += it.size }
        val out = ByteArray(n)
        var p = 0
        for (part in parts) {
            part.copyInto(out, p)
            p += part.size
        }
        return out
    }

    private fun pcmShorts(buf: ByteBuffer, info: MediaCodec.BufferInfo, floatPcm: Boolean): ShortArray {
        val d = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        d.position(info.offset)
        d.limit(info.offset + info.size)
        return if (floatPcm) {
            val n = d.remaining() / 4
            ShortArray(n) { (d.float.coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
        } else {
            val n = d.remaining() / 2
            ShortArray(n) { d.short }
        }
    }

    private val codecRates = intArrayOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)

    private fun supportedRate(rate: Int): Int {
        if (rate <= 0) return 44100
        return codecRates.minBy { kotlin.math.abs(it - rate) }
    }

    private fun resample(pcm: ShortArray, channels: Int, from: Int, to: Int): ShortArray {
        if (from == to || from <= 0 || to <= 0 || channels <= 0) return pcm
        val frames = pcm.size / channels
        if (frames <= 1) return pcm
        val outFrames = ((frames.toLong() * to) / from).toInt().coerceAtLeast(1)
        val out = ShortArray(outFrames * channels)
        val last = frames - 1
        for (i in 0 until outFrames) {
            val pos = i.toDouble() * last / (outFrames - 1).coerceAtLeast(1)
            val i0 = pos.toInt().coerceIn(0, last)
            val i1 = (i0 + 1).coerceAtMost(last)
            val frac = (pos - i0).toFloat()
            for (c in 0 until channels) {
                val a = pcm[i0 * channels + c].toInt()
                val b = pcm[i1 * channels + c].toInt()
                out[i * channels + c] = (a + (b - a) * frac).toInt().toShort()
            }
        }
        return out
    }

    /** Returns true when the file is a WAV and its samples were delivered. */
    private fun readWav(input: File, durationMs: Long, onProgress: (Float) -> Unit, onChunk: (Int, Int, ShortArray) -> Unit): Boolean {
        FileInputStream(input).use { raw ->
            val header = ByteArray(12)
            if (raw.read(header) != 12) return false
            if (String(header, 0, 4) != "RIFF" || String(header, 8, 4) != "WAVE") return false
            val stream = raw.buffered()
            var audioFormat = 0
            var channels = 0
            var rate = 0
            var bits = 0
            var dataBytes = -1L
            while (dataBytes < 0) {
                val id = ByteArray(4)
                val sizeBuf = ByteArray(4)
                if (readFully(stream, id) != 4 || readFully(stream, sizeBuf) != 4) {
                    throw IllegalStateException("Could not read this audio file.")
                }
                val size = ByteBuffer.wrap(sizeBuf).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                when (String(id)) {
                    "fmt " -> {
                        if (size < 16 || size > 1024) throw IllegalStateException("Could not read this audio file.")
                        val fmt = ByteArray(size.toInt())
                        if (readFully(stream, fmt) != size.toInt()) throw IllegalStateException("Could not read this audio file.")
                        val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        audioFormat = bb.short.toInt() and 0xFFFF
                        channels = (bb.short.toInt() and 0xFFFF).coerceAtLeast(1)
                        rate = bb.int
                        bits = if (fmt.size >= 16) {
                            bb.int
                            bb.short
                            bb.short.toInt() and 0xFFFF
                        } else 16
                    }
                    "data" -> dataBytes = size
                    else -> if (!skipFully(stream, size)) throw IllegalStateException("Could not read this audio file.")
                }
                if (String(id) != "data" && size % 2L == 1L) stream.read()
            }
            if (rate <= 0 || channels <= 0 || audioFormat !in setOf(1, 3) || bits !in setOf(8, 16, 24, 32)) {
                throw IllegalStateException("This audio format is not supported on this phone.")
            }
            val bytesPer = bits / 8
            val frameBytes = bytesPer * channels
            val buf = ByteArray(frameBytes * 2048)
            var left = if (dataBytes > Int.MAX_VALUE) Long.MAX_VALUE / 4 else dataBytes
            var framesRead = 0L
            while (left >= frameBytes) {
                val cap = if (left > buf.size) buf.size else left.toInt()
                val want = cap - cap % frameBytes
                val n = readFully(stream, buf, want)
                if (n < frameBytes) break
                val frames = n / frameBytes
                val shorts = wavShorts(buf, frames, channels, bits, audioFormat == 3)
                val (mixed, ch) = downmix(shorts, channels)
                if (mixed.isNotEmpty()) onChunk(rate, ch, mixed)
                left -= frames * frameBytes
                framesRead += frames
                if (durationMs > 0) onProgress((framesRead * 1000f / rate / durationMs).coerceIn(0f, 1f))
            }
            return true
        }
    }

    private fun wavShorts(buf: ByteArray, frames: Int, channels: Int, bits: Int, floatPcm: Boolean): ShortArray {
        val out = ShortArray(frames * channels)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        for (i in out.indices) {
            out[i] = when {
                floatPcm && bits == 32 -> (bb.float.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                bits == 8 -> (((buf[i].toInt() and 0xFF) - 128) shl 8).toShort()
                bits == 16 -> bb.short
                bits == 24 -> {
                    bb.get()
                    val hi = bb.get().toInt() and 0xFF
                    val top = bb.get().toInt() and 0xFF
                    ((top shl 8) or hi).toShort()
                }
                bits == 32 -> (bb.int shr 16).toShort()
                else -> 0
            }
        }
        return out
    }

    private fun readFully(stream: java.io.InputStream, buf: ByteArray, len: Int = buf.size): Int {
        var off = 0
        while (off < len) {
            val n = stream.read(buf, off, len - off)
            if (n < 0) break
            off += n
        }
        return off
    }

    private fun skipFully(stream: java.io.InputStream, count: Long): Boolean {
        var left = count
        while (left > 0) {
            val n = stream.skip(left)
            if (n <= 0) {
                if (stream.read() < 0) return false
                left--
            } else left -= n
        }
        return true
    }

    private fun downmix(pcm: ShortArray, channels: Int): Pair<ShortArray, Int> {
        if (channels <= 1) return pcm to 1
        if (channels == 2) return pcm to 2
        val frames = pcm.size / channels
        val out = ShortArray(frames * 2)
        for (i in 0 until frames) {
            out[i * 2] = pcm[i * channels]
            out[i * 2 + 1] = pcm[i * channels + 1]
        }
        return out to 2
    }

    private fun bytesOf(buf: ByteBuffer, info: MediaCodec.BufferInfo): ByteArray {
        val d = buf.duplicate()
        d.position(info.offset)
        d.limit(info.offset + info.size)
        return ByteArray(d.remaining()).also { d.get(it) }
    }

    private fun MediaFormat.intOr(key: String, fallback: Int) = if (containsKey(key)) getInteger(key) else fallback

    private fun loadLame() {
        if (lameReady) return
        synchronized(this) {
            if (lameReady) return
            try {
                val field = Class.forName("com.github.axet.lamejni.Config").getField("natives")
                if (field.getBoolean(null)) {
                    runCatching { System.loadLibrary("lame") }
                    System.loadLibrary("lamejni")
                    field.setBoolean(null, false)
                }
                lameReady = true
            } catch (e: UnsatisfiedLinkError) {
                throw IllegalStateException("Could not start the MP3 encoder on this phone.")
            }
        }
    }

    private fun loadVorbis() {
        if (vorbisReady) return
        synchronized(this) {
            if (vorbisReady) return
            try {
                val field = Class.forName("com.github.axet.vorbisjni.Config").getField("natives")
                if (field.getBoolean(null)) {
                    runCatching { System.loadLibrary("ogg") }
                    runCatching { System.loadLibrary("vorbis") }
                    System.loadLibrary("vorbisjni")
                    field.setBoolean(null, false)
                }
                vorbisReady = true
            } catch (e: UnsatisfiedLinkError) {
                throw IllegalStateException("Could not start the OGG encoder on this phone.")
            }
        }
    }

    private interface PcmSink {
        val finished: Boolean
        fun write(pcm: ShortArray)
        fun finish()
        fun release()
    }

    private class Mp3Sink(private val file: File, rateIn: Int, channelsIn: Int) : PcmSink {
        private val lame: Lame
        private val rate = if (rateIn > 0) rateIn else 44100
        private val channels = channelsIn.coerceIn(1, 2)
        private val targetRate = supportedRate(rate)
        private val body = File(file.parentFile, file.name + ".body")
        private val out = FileOutputStream(body).buffered()
        private var pending = ShortArray(32768)
        private var count = 0
        override var finished = false
        init {
            loadLame()
            lame = try {
                Lame().also { it.open(channels, targetRate, 128, 4) }
            } catch (e: Throwable) {
                throw IllegalStateException("Could not start the MP3 encoder on this phone.")
            }
        }
        override fun write(pcm: ShortArray) {
            val data = if (rate == targetRate) pcm else resample(pcm, channels, rate, targetRate)
            var off = 0
            while (off < data.size) {
                if (count == pending.size) pending = pending.copyOf(pending.size * 2)
                val n = minOf(data.size - off, pending.size - count)
                data.copyInto(pending, count, off, off + n)
                count += n
                off += n
                while (count >= 16384) encodePending(16384)
            }
        }
        override fun finish() {
            if (finished) return
            finished = true
            val leftover = count - (count % channels)
            if (leftover > 0) encodePending(leftover, pad = true)
            out.close()
            val tag = runCatching { lame.close() }.getOrNull()
            FileOutputStream(file).use { dest ->
                if (tag != null && tag.isNotEmpty()) dest.write(tag)
                FileInputStream(body).use { it.copyTo(dest) }
            }
            body.delete()
        }
        /** The native encoder sizes its buffer from the sample count and overflows on short chunks. */
        private fun encodePending(requested: Int, pad: Boolean = false) {
            val aligned = minOf(requested, count) - (minOf(requested, count) % channels)
            if (aligned <= 0) return
            val need = if (pad) maxOf(aligned, 16384 - (16384 % channels)) else aligned
            val chunk = ShortArray(need)
            pending.copyInto(chunk, 0, 0, aligned)
            pending.copyInto(pending, 0, aligned, count)
            count -= aligned
            val encoded = try {
                lame.encode(chunk, 0, chunk.size)
            } catch (e: Throwable) {
                throw IllegalStateException("Could not write the MP3 file.")
            }
            if (encoded != null && encoded.isNotEmpty()) out.write(encoded)
        }
        override fun release() {
            runCatching { out.close() }
            runCatching { lame.close() }
            body.delete()
            finished = true
        }
    }

    private class OggSink(private val file: File, private val rate: Int, private val channels: Int) : PcmSink {
        private val vorbis: Vorbis
        private val targetRate = supportedRate(rate)
        private val out = FileOutputStream(file).buffered()
        override var finished = false
        init {
            loadVorbis()
            vorbis = try {
                Vorbis().also { it.open(channels, targetRate, 0.4f) }
            } catch (e: Exception) {
                throw IllegalStateException("Could not start the OGG encoder on this phone.")
            }
        }
        override fun write(pcm: ShortArray) {
            val data = if (rate == targetRate) pcm else resample(pcm, channels, rate, targetRate)
            val n = data.size - (data.size % channels)
            if (n <= 0) return
            val encoded = vorbis.encode(data, 0, n)
            if (encoded != null && encoded.isNotEmpty()) out.write(encoded)
        }
        override fun finish() {
            if (finished) return
            finished = true
            val tail = vorbis.encode(ShortArray(0), 0, 0)
            if (tail != null && tail.isNotEmpty()) out.write(tail)
            out.close()
            vorbis.close()
        }
        override fun release() {
            runCatching { out.close() }
            runCatching { vorbis.close() }
            finished = true
        }
    }

    private class WavSink(file: File, private val rate: Int, private val channels: Int) : PcmSink {
        private val raf = java.io.RandomAccessFile(file, "rw")
        private var dataBytes = 0
        override var finished = false
        init {
            raf.setLength(0)
            raf.write(ByteArray(44))
        }
        override fun write(pcm: ShortArray) {
            val raw = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) raw.putShort(s)
            raf.write(raw.array())
            dataBytes += pcm.size * 2
        }
        override fun finish() {
            if (finished) return
            finished = true
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataBytes)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(rate)
            header.putInt(rate * channels * 2)
            header.putShort((channels * 2).toShort())
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(dataBytes)
            raf.seek(0)
            raf.write(header.array())
            raf.close()
        }
        override fun release() {
            runCatching { raf.close() }
            finished = true
        }
    }

    private class AacSink(file: File, private val rate: Int, private val channels: Int) : PcmSink {
        private val targetRate = supportedRate(rate)
        private val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
        private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private val pending = ArrayList<Short>(8192)
        private var head = 0
        private var track = -1
        private var started = false
        private var samples = 0L
        override var finished = false

        init {
            val fmt = MediaFormat.createAudioFormat("audio/mp4a-latm", targetRate, channels)
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
        }

        override fun write(pcm: ShortArray) {
            val data = if (rate == targetRate) pcm else resample(pcm, channels, rate, targetRate)
            for (s in data) pending.add(s)
            val need = 1024 * channels
            while (pending.size - head >= need) queue(take(need), false)
            if (head > 8192) {
                val rest = pending.subList(head, pending.size).toMutableList()
                pending.clear()
                pending.addAll(rest)
                head = 0
            }
        }

        override fun finish() {
            if (finished) return
            try {
                val need = 1024 * channels
                val left = pending.size - head
                val last = ShortArray(if (left > 0) need else 0)
                for (i in 0 until left) last[i] = pending[head + i]
                queue(if (left > 0) last else null, true)
                drain(true)
            } finally {
                if (started) runCatching { muxer.stop() }
                runCatching { muxer.release() }
                runCatching { encoder.stop() }
                runCatching { encoder.release() }
                finished = true
            }
        }

        override fun release() {
            runCatching { if (started) muxer.stop() }
            runCatching { muxer.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            finished = true
        }

        private fun take(n: Int): ShortArray {
            val chunk = ShortArray(n) { pending[head + it] }
            head += n
            return chunk
        }

        private fun queue(chunk: ShortArray?, eos: Boolean) {
            var spins = 0
            var index = encoder.dequeueInputBuffer(TIMEOUT)
            while (index < 0 && spins < 30) {
                drain(false)
                index = encoder.dequeueInputBuffer(TIMEOUT)
                spins++
            }
            if (index < 0) {
                if (eos) drain(true)
                return
            }
            val buffer = encoder.getInputBuffer(index)!!
            buffer.clear()
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val count = if (chunk != null) minOf(chunk.size, buffer.remaining() / 2) else 0
            for (i in 0 until count) buffer.putShort(chunk!![i])
            val pts = samples * 1_000_000L / targetRate
            samples += if (channels > 0) count / channels else 0
            val flags = if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            encoder.queueInputBuffer(index, 0, count * 2, pts, flags)
            drain(eos)
        }

        private fun drain(untilEos: Boolean) {
            val info = MediaCodec.BufferInfo()
            var spins = 0
            while (true) {
                val out = encoder.dequeueOutputBuffer(info, if (untilEos) 200 else 0)
                when {
                    out == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!untilEos || spins++ > 50) return
                    }
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!started) {
                            track = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            started = true
                        }
                    }
                    out >= 0 -> {
                        if (started && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val buf = encoder.getOutputBuffer(out)!!
                            muxer.writeSampleData(track, buf, info)
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(out, false)
                        if (eos) return
                    }
                    else -> if (!untilEos) return
                }
            }
        }
    }

    private class Sample(val data: ByteArray, val ptsUs: Long, val flags: Int) {
        fun info(): MediaCodec.BufferInfo = MediaCodec.BufferInfo().apply {
            set(0, data.size, ptsUs, flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
        }
    }
}
