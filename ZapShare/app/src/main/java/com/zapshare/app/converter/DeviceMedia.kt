package com.zapshare.app.converter

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import com.github.axet.lamejni.Lame
import com.github.axet.vorbisjni.Vorbis
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
        val retriever = MediaMetadataRetriever()
        var gif: GifWriter? = null
        val stream = FileOutputStream(output)
        try {
            retriever.setDataSource(input.absolutePath)
            val dur = durationMs.takeIf { it > 0 }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 10_000L
            val limitUs = minOf(dur, 10_000L).coerceAtLeast(400) * 1000L
            val stepUs = 1_000_000L / 8
            var time = 0L
            var frames = 0
            while (time < limitUs && frames < 80) {
                val frame = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val w = frame.width.coerceAtMost(480).coerceAtLeast(2)
                    val h = (frame.height * (w.toFloat() / frame.width)).toInt().coerceAtLeast(2)
                    if (gif == null) gif = GifWriter(stream, w, h)
                    gif.frame(frame, 12)
                    frame.recycle()
                    frames++
                    onProgress(frames / 80f)
                }
                time += stepUs
            }
            if (gif == null || frames == 0) throw IllegalStateException("This video has no frames to convert.")
            gif.close()
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Could not read this video. Choose another file and try again.")
        } finally {
            runCatching { retriever.release() }
            runCatching { stream.close() }
        }
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
        val hasAudio = try {
            audio(input, durationMs, { p -> onProgress(p * 0.25f) }) { rate, ch -> AacSink(audioFile, rate, ch) }
            audioFile.exists() && audioFile.length() > 0
        } catch (e: IllegalStateException) {
            if (e.message?.startsWith("Could not read this media file") == true) throw e
            false
        }
        if (!hasAudio) audioFile.delete()
        val audioPath = if (hasAudio) audioFile else null
        try {
            val surfaceOk = runCatching { transcodeSurface(input, output, audioPath, maxHeight, durationMs, onProgress) }.getOrDefault(false)
            if (!surfaceOk || !videoHasSamples(output)) {
                output.delete()
                val decoded = runCatching { transcodeFromDecoder(input, output, audioPath, maxHeight, durationMs, onProgress) }.getOrDefault(false)
                if (!decoded || !videoHasSamples(output)) {
                    output.delete()
                    transcodeFromFrames(input, output, audioPath, maxHeight, durationMs, onProgress)
                }
            }
        } finally {
            audioFile.delete()
        }
        if (!videoHasSamples(output)) throw IllegalStateException("Could not create a playable video.")
    }

    /**
     * Decodes onto the encoder surface. Some phones report a buffer size of 0 for surface
     * frames; those frames still have to be rendered or the saved file has no picture.
     */
    private fun transcodeSurface(
        input: File, output: File, audioFile: File?, maxHeight: Int, durationMs: Long, onProgress: (Float) -> Unit
    ): Boolean {
        val ex = openExtractor(input)
        val vIndex = findTrack(ex, "video/")
        ex.selectTrack(vIndex)
        val srcFmt = ex.getTrackFormat(vIndex)
        val (width, height, rotation) = targetSize(srcFmt, maxHeight)
        val mime = srcFmt.getString(MediaFormat.KEY_MIME) ?: "video/avc"
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerOn = false
        var videoSamples = 0
        try {
            val encFmt = surfaceEncoderFormat(width, height, maxHeight)
            val enc = MediaCodec.createEncoderByType("video/avc")
            encoder = enc
            enc.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = enc.createInputSurface()
            enc.start()
            requestSyncFrame(enc)
            val dec = MediaCodec.createDecoderByType(mime)
            decoder = dec
            dec.configure(videoDecoderFormat(srcFmt), surface, null, 0)
            dec.start()
            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            if (rotation != 0) mux.setOrientationHint(rotation)

            var videoTrack = -1
            var audioTrack = -1
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
                            val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            dec.queueInputBuffer(inIndex, 0, size, ex.sampleTime.coerceAtLeast(0), flags)
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
                        dec.releaseOutputBuffer(outIndex, !config)
                        if (!config) requestSyncFrame(enc)
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
                            videoTrack = mux.addTrack(enc.outputFormat)
                            audioTrack = addAudioTrack(mux, audioExtractor)
                            mux.start()
                            muxerOn = true
                            for (s in pendingVideo) mux.writeSampleData(videoTrack, ByteBuffer.wrap(s.data), s.info())
                            pendingVideo.clear()
                        }
                        moved = true
                    }
                    encIndex >= 0 -> {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val data = bytesOf(enc.getOutputBuffer(encIndex)!!, info)
                            val flags = keyFlags(data, info.flags, sawKey)
                            if (flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) sawKey = true
                            val sample = Sample(data, info.presentationTimeUs.coerceAtLeast(0), flags)
                            if (muxerOn) mux.writeSampleData(videoTrack, ByteBuffer.wrap(data), sample.info())
                            else pendingVideo.add(sample)
                            videoSamples++
                            if (durationMs > 0) onProgress((0.25f + 0.75f * (sample.ptsUs / 1000f / durationMs)).coerceIn(0.25f, 1f))
                        }
                        enc.releaseOutputBuffer(encIndex, false)
                        if (eos) encoderDone = true
                        moved = true
                    }
                }
                if (!moved) {
                    spins++
                    if (spins > 400) break
                } else spins = 0
            }
            if (muxerOn && audioTrack >= 0 && audioExtractor != null && audioBuf != null) {
                writeAudioSamples(audioExtractor, audioBuf, mux, audioTrack)
            }
            audioExtractor?.release()
            if (muxerOn && videoSamples > 0) mux.stop() else return false
            return videoSamples > 0
        } catch (e: Exception) {
            return false
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
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
        val (srcW, srcH) = sizeOf(srcFmt)
        val (width, height, rotation) = targetSize(srcFmt, maxHeight)
        if (kotlin.math.abs(width - align16(srcW)) > 16 || kotlin.math.abs(height - align16(srcH)) > 16) {
            ex.release()
            return false
        }
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var videoSamples = 0
        try {
            val dec = MediaCodec.createDecoderByType(mime)
            decoder = dec
            dec.configure(videoDecoderFormat(srcFmt), null, null, 0)
            dec.start()
            val colorChoice = encoderColorFormat()
            val enc = MediaCodec.createEncoderByType("video/avc")
            encoder = enc
            val encFmt = MediaFormat.createVideoFormat("video/avc", width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorChoice)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height, maxHeight))
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
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
            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            if (rotation != 0) mux.setOrientationHint(rotation)
            val audioExtractor = audioFile?.let { runCatching { openExtractor(it) }.getOrNull() }
            var videoTrack = -1
            var audioTrack = -1
            var muxerOn = false
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
                                videoTrack = mux.addTrack(enc.outputFormat)
                                audioTrack = addAudioTrack(mux, audioExtractor)
                                mux.start()
                                muxerOn = true
                                for (s in pending) mux.writeSampleData(videoTrack, ByteBuffer.wrap(s.data), s.info())
                                pending.clear()
                            }
                        }
                        out >= 0 -> {
                            val eos = encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            if (encInfo.size > 0 && encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val data = bytesOf(enc.getOutputBuffer(out)!!, encInfo)
                                val flags = keyFlags(data, encInfo.flags, sawKey)
                                if (flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) sawKey = true
                                val sample = Sample(data, encInfo.presentationTimeUs.coerceAtLeast(0), flags)
                                if (muxerOn) mux.writeSampleData(videoTrack, ByteBuffer.wrap(data), sample.info())
                                else pending.add(sample)
                                videoSamples++
                                if (durationMs > 0) onProgress((0.25f + 0.75f * (sample.ptsUs / 1000f / durationMs)).coerceIn(0.25f, 1f))
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
                val flags = MediaCodec.BUFFER_FLAG_KEY_FRAME or
                    (if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
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
                        if (!config) {
                            val image = dec.getOutputImage(outIndex)
                            if (image != null) {
                                val yuv = if (planar) imageToI420(image, width, height, stride, slice)
                                else imageToNv12(image, width, height, stride, slice)
                                image.close()
                                queueYuv(yuv, info.presentationTimeUs, eos)
                            } else if (eos) {
                                queueYuv(ByteArray(stride * slice * 3 / 2), info.presentationTimeUs, true)
                            }
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
            if (muxerOn && audioTrack >= 0 && audioExtractor != null) {
                writeAudioSamples(audioExtractor, ByteBuffer.allocateDirect(256 * 1024), mux, audioTrack)
            }
            audioExtractor?.release()
            if (muxerOn && videoSamples > 0) mux.stop()
            return videoSamples > 0
        } catch (e: Exception) {
            return false
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
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
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
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
                                val data = bytesOf(enc.getOutputBuffer(out)!!, info)
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
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            })
        }
    }

    /** MediaTek's muxer drops every frame until one is marked as a key frame. */
    private fun keyFlags(data: ByteArray, flags: Int, sawKey: Boolean): Int {
        val key = flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0 || isAvcKey(data) || !sawKey
        return if (key) flags or MediaCodec.BUFFER_FLAG_KEY_FRAME else flags
    }

    private fun isAvcKey(data: ByteArray): Boolean {
        if (data.size >= 5 && data[0] == 0.toByte() && data[1] == 0.toByte()) {
            val nal = if (data[2] == 1.toByte()) data[3] else if (data[2] == 0.toByte() && data[3] == 1.toByte()) data[4] else return false
            return (nal.toInt() and 0x1F) == 5
        }
        var i = 0
        while (i + 4 < data.size) {
            val len = ((data[i].toInt() and 0xFF) shl 24) or
                ((data[i + 1].toInt() and 0xFF) shl 16) or
                ((data[i + 2].toInt() and 0xFF) shl 8) or
                (data[i + 3].toInt() and 0xFF)
            if (len <= 0 || i + 4 + len > data.size) return false
            if ((data[i + 4].toInt() and 0x1F) == 5) return true
            i += 4 + len
        }
        return false
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

    private fun surfaceEncoderFormat(width: Int, height: Int, maxHeight: Int): MediaFormat =
        MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height, maxHeight))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
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

    private fun videoHasSamples(file: File): Boolean {
        if (!file.exists() || file.length() < 64) return false
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(file.absolutePath)
            val v = findTrack(ex, "video/")
            if (v < 0) false
            else {
                ex.selectTrack(v)
                ex.readSampleData(ByteBuffer.allocate(256), 0) > 0
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
                decoder.configure(fmt, null, null, 0)
                decoder.start()
                var rate = fmt.intOr(MediaFormat.KEY_SAMPLE_RATE, 44100)
                var channels = fmt.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceIn(1, 2)
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
                            val buf = decoder.getInputBuffer(inIndex)!!
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
                            if (info.size > 0) {
                                val encoding = if (Build.VERSION.SDK_INT >= 24 && decoder.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                    decoder.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else 2
                                val shorts = pcmShorts(decoder.getOutputBuffer(outIndex)!!, info, encoding == 4)
                                val (mixed, ch) = downmix(shorts, channels)
                                if (mixed.isNotEmpty()) onChunk(rate, ch, mixed)
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                    if (moved) idle = 0
                    else if (++idle > 100) throw IllegalStateException("Could not decode this audio. Try another file.")
                }
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
            val displayH = if (rotation == 90 || rotation == 270) w else h
            if (displayH > maxHeight) {
                val s = maxHeight.toFloat() / displayH
                w = (w * s).toInt()
                h = (h * s).toInt()
            }
        }
        return Triple(align16(w), align16(h), rotation)
    }

    private fun even(v: Int) = v.coerceAtLeast(2) and 1.inv()

    /** MediaTek's AVC encoder drops every frame when the size is not a multiple of 16. */
    private fun align16(v: Int): Int {
        val x = v.coerceAtLeast(16)
        return (x + 15) and 15.inv()
    }

    private fun bitrateFor(w: Int, h: Int, maxHeight: Int): Int = when {
        maxHeight in 1..480 -> 900_000
        maxHeight in 481..720 -> 1_800_000
        else -> (w * h * 3).coerceIn(800_000, 8_000_000)
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

    private class Mp3Sink(private val file: File, private val rate: Int, private val channels: Int) : PcmSink {
        private val lame: Lame
        private val targetRate = supportedRate(rate)
        private val body = File(file.parentFile, file.name + ".body")
        private val out = FileOutputStream(body).buffered()
        override var finished = false
        init {
            loadLame()
            lame = try {
                Lame().also { it.open(channels, targetRate, 128, 4) }
            } catch (e: Exception) {
                throw IllegalStateException("Could not start the MP3 encoder on this phone.")
            }
        }
        override fun write(pcm: ShortArray) {
            val data = if (rate == targetRate) pcm else resample(pcm, channels, rate, targetRate)
            val n = data.size - (data.size % channels)
            if (n <= 0) return
            val encoded = lame.encode(data, 0, n)
            if (encoded != null && encoded.isNotEmpty()) out.write(encoded)
        }
        override fun finish() {
            if (finished) return
            finished = true
            val flush = lame.encode(ShortArray(0), 0, 0)
            if (flush != null && flush.isNotEmpty()) out.write(flush)
            out.close()
            val tag = lame.close()
            FileOutputStream(file).use { dest ->
                if (tag != null && tag.isNotEmpty()) dest.write(tag)
                FileInputStream(body).use { it.copyTo(dest) }
            }
            body.delete()
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
