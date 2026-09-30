package com.zapshare.app.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.zapshare.app.downloader.VideoDownloader
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** All conversions run 100% on-device. Output goes to Download/ZapShare/Converted. */
object Converters {
    private const val OUT = "Converted"

    enum class ImageFormat(val ext: String, val mime: String) {
        JPG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp")
    }

    // ------------------------------------------------------------------ images
    fun decodeBitmap(ctx: Context, uri: Uri, maxDim: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width; val h = info.size.height
                if (maxDim > 0 && maxOf(w, h) > maxDim) {
                    val s = maxDim.toFloat() / maxOf(w, h)
                    decoder.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        if (maxDim > 0) while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("Unsupported image")
    }

    suspend fun convertImage(ctx: Context, uri: Uri, name: String, format: ImageFormat, quality: Int, maxDim: Int): Uri =
        withContext(Dispatchers.IO) {
            var bmp = decodeBitmap(ctx, uri, maxDim)
            if (format == ImageFormat.JPG && bmp.hasAlpha()) {   // JPG has no transparency: flatten on white
                val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
                bmp.recycle(); bmp = flat
            }
            val cf = when (format) {
                ImageFormat.JPG -> Bitmap.CompressFormat.JPEG
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
                else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }
            val outName = name.substringBeforeLast('.') + "." + format.ext
            val out = FileUtils.createOutput(ctx, outName, format.mime, OUT)
            var ok = false
            try {
                out.stream.use { bmp.compress(cf, quality, it) }
                ok = true
            } finally {
                bmp.recycle()
                FileUtils.finishOutput(ctx, out, ok)
            }
            out.uri
        }

    suspend fun imagesToPdf(ctx: Context, uris: List<Uri>, outName: String): Uri = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 24f   // A4 @ 72dpi
        try {
            uris.forEachIndexed { i, u ->
                val bmp = decodeBitmap(ctx, u, 2000)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create())
                val availW = pageW - 2 * margin; val availH = pageH - 2 * margin
                val s = minOf(availW / bmp.width, availH / bmp.height)
                val w = bmp.width * s; val h = bmp.height * s
                val left = (pageW - w) / 2; val top = (pageH - h) / 2
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                bmp.recycle()
            }
            val out = FileUtils.createOutput(ctx, "$outName.pdf", "application/pdf", OUT)
            var ok = false
            try { out.stream.use { doc.writeTo(it) }; ok = true } finally { FileUtils.finishOutput(ctx, out, ok) }
            out.uri
        } finally { doc.close() }
    }

    /** Returns number of pages exported. */
    suspend fun pdfToImages(ctx: Context, uri: Uri, name: String, format: ImageFormat): Int = withContext(Dispatchers.IO) {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalArgumentException("Cannot open PDF")
        pfd.use { fd ->
            PdfRenderer(fd).use { r ->
                val base = name.substringBeforeLast('.')
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { p ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(p.width * scale, p.height * scale, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = FileUtils.createOutput(ctx, "${base}_page${i + 1}.${format.ext}", format.mime, OUT)
                        var ok = false
                        try {
                            out.stream.use {
                                bmp.compress(if (format == ImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it)
                            }
                            ok = true
                        } finally { bmp.recycle(); FileUtils.finishOutput(ctx, out, ok) }
                    }
                }
                r.pageCount
            }
        }
    }

    suspend fun textToPdf(ctx: Context, uri: Uri, name: String): Uri = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().take(5_000_000).toByteArray().toString(Charsets.UTF_8) }
            ?: throw IllegalArgumentException("Cannot read file")
        val pageW = 595; val pageH = 842; val margin = 40
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
        val width = pageW - 2 * margin
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
        val doc = PdfDocument()
        try {
            var line = 0; var pageNo = 1
            val usable = pageH - 2 * margin
            while (line < layout.lineCount) {
                val top = layout.getLineTop(line)
                var end = line
                while (end + 1 < layout.lineCount && layout.getLineBottom(end + 1) - top <= usable) end++
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo++).create())
                val c = page.canvas
                c.save()
                c.translate(margin.toFloat(), (margin - top).toFloat())
                c.clipRect(0, top, width, layout.getLineBottom(end))
                layout.draw(c)
                c.restore()
                doc.finishPage(page)
                line = end + 1
            }
            if (layout.lineCount == 0) doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()))
            val out = FileUtils.createOutput(ctx, name.substringBeforeLast('.') + ".pdf", "application/pdf", OUT)
            var ok = false
            try { out.stream.use { doc.writeTo(it) }; ok = true } finally { FileUtils.finishOutput(ctx, out, ok) }
            out.uri
        } finally { doc.close() }
    }

    // ------------------------------------------------------------------ audio / video (FFmpeg)
    enum class MediaPreset(val label: String, val ext: String, val args: List<String>, val audioIn: Boolean = true) {
        TO_MP3("Video/Audio → MP3", "mp3", listOf("-vn", "-c:a", "libmp3lame", "-q:a", "2")),
        TO_M4A("Video/Audio → M4A (AAC)", "m4a", listOf("-vn", "-c:a", "aac", "-b:a", "192k")),
        TO_WAV("Audio → WAV", "wav", listOf("-vn", "-c:a", "pcm_s16le")),
        TO_OGG("Audio → OGG", "ogg", listOf("-vn", "-c:a", "libvorbis", "-q:a", "5")),
        TO_MP4("Video → MP4 (H.264)", "mp4", listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart")),
        COMPRESS_720("Compress video (720p)", "mp4", listOf("-vf", "scale=-2:'min(720,ih)'", "-c:v", "libx264", "-preset", "veryfast", "-crf", "28", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart")),
        COMPRESS_480("Compress video (480p, small)", "mp4", listOf("-vf", "scale=-2:'min(480,ih)'", "-c:v", "libx264", "-preset", "veryfast", "-crf", "30", "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart")),
        TO_GIF("Video → GIF (first 10s)", "gif", listOf("-t", "10", "-vf", "fps=12,scale=480:-1:flags=lanczos", "-loop", "0")),
        TO_MKV("Video → MKV (no re-encode)", "mkv", listOf("-c", "copy")),
    }

    suspend fun convertMedia(
        ctx: Context, uri: Uri, name: String, preset: MediaPreset, durationMs: Long, onProgress: (Float) -> Unit
    ): Uri = withContext(Dispatchers.IO) {
        val input = FileUtils.copyToCache(ctx, uri, name)
        val outFile = File(input.parentFile, input.nameWithoutExtension + "_out." + preset.ext)
        try {
            val ffmpegOk = VideoDownloader.awaitReady() && FfmpegRunner.available(ctx)
            if (ffmpegOk) {
                val args = listOf("-y", "-i", input.absolutePath) + preset.args + outFile.absolutePath
                val code = FfmpegRunner.run(ctx, args, durationMs, onProgress)
                if (code != 0 || !outFile.exists() || outFile.length() == 0L) throw IllegalStateException("FFmpeg failed (code $code)")
            } else if (preset == MediaPreset.TO_M4A) {
                extractAudioTrack(input, outFile)   // pure Android fallback
            } else {
                throw IllegalStateException("Media engine not available on this device")
            }
            val outName = name.substringBeforeLast('.') + "." + preset.ext
            FileUtils.copyToDownloads(ctx, Uri.fromFile(outFile), outName, FileUtils.mimeOf(outName), OUT)
        } finally {
            input.delete(); outFile.delete()
        }
    }

    /** Copies the AAC audio track out of an MP4 without re-encoding (no FFmpeg needed). */
    private fun extractAudioTrack(input: File, output: File) {
        val ex = MediaExtractor()
        ex.setDataSource(input.absolutePath)
        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/mp4a") == true) { track = i; fmt = f; break }
        }
        if (track < 0 || fmt == null) { ex.release(); throw IllegalStateException("No AAC audio track found") }
        ex.selectTrack(track)
        val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val dst = mux.addTrack(fmt)
        mux.start()
        val buf = ByteBuffer.allocate(1 shl 20)
        val info = MediaCodec.BufferInfo()
        while (true) {
            info.offset = 0
            info.size = ex.readSampleData(buf, 0)
            if (info.size < 0) break
            info.presentationTimeUs = ex.sampleTime
            info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            mux.writeSampleData(dst, buf, info)
            ex.advance()
        }
        mux.stop(); mux.release(); ex.release()
    }
}

/**
 * Runs the FFmpeg binary bundled by youtubedl-android's ffmpeg module (free, LGPL/GPL build).
 * Paths mirror what that library uses internally.
 */
object FfmpegRunner {
    private fun binary(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir, "libffmpeg.so")
    fun available(ctx: Context) = binary(ctx).exists()

    fun run(ctx: Context, args: List<String>, durationMs: Long, onProgress: (Float) -> Unit): Int {
        val base = File(ctx.noBackupFilesDir, "youtubedl-android/packages")
        val libPath = listOf(
            ctx.applicationInfo.nativeLibraryDir,
            File(base, "ffmpeg/usr/lib").absolutePath,
            File(base, "python/usr/lib").absolutePath
        ).joinToString(":")
        val pb = ProcessBuilder(listOf(binary(ctx).absolutePath) + args).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = libPath
        val proc = pb.start()
        val timeRe = Regex("time=(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)")
        proc.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                timeRe.find(line)?.let { m ->
                    val (h, mi, s) = m.destructured
                    val ms = ((h.toLong() * 3600 + mi.toLong() * 60) * 1000 + (s.toDouble() * 1000)).toLong()
                    if (durationMs > 0) onProgress((ms.toFloat() / durationMs).coerceIn(0f, 1f))
                }
            }
        }
        return proc.waitFor()
    }
}
