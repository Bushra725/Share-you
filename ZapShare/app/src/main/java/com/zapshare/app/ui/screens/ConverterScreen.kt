@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.zapshare.app.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zapshare.app.converter.Converters
import com.zapshare.app.ui.components.GradientButton
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.Routes
import com.zapshare.app.ui.theme.CatApps
import com.zapshare.app.ui.theme.CatDocs
import com.zapshare.app.ui.theme.CatMusic
import com.zapshare.app.ui.theme.CatPdf
import com.zapshare.app.ui.theme.CatPhoto
import com.zapshare.app.ui.theme.CatVideo
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Conversion job state lives outside the screen so work continues if you navigate away. */
object ConverterJob {
    data class State(val running: Boolean = false, val title: String = "", val progress: Float? = null,
                     val message: String? = null, val lastUri: Uri? = null, val lastName: String? = null)
    val state = MutableStateFlow(State())

    fun run(title: String, block: suspend (report: (Float) -> Unit) -> Pair<String, Uri?>) {
        if (state.value.running) return
        state.value = State(running = true, title = title, progress = null)
        appScope.launch {
            try {
                val (msg, uri) = block { p -> state.value = state.value.copy(progress = p) }
                state.value = State(message = msg, lastUri = uri, lastName = msg)
            } catch (e: Throwable) {
                val raw = e.message?.trim().orEmpty()
                val detail = if (raw.isEmpty() || raw.equals("unknown error", true)) {
                    "Could not convert this file. Try another video."
                } else raw
                state.value = State(message = "Failed: $detail")
            }
        }
    }
}

@Composable
fun ConverterScreen(nav: NavController) {
    val ctx = LocalContext.current
    val job by ConverterJob.state.collectAsState()
    var imageUris by remember { mutableStateOf<List<Uri>?>(null) }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { if (it.isNotEmpty()) imageUris = it }
    val pickImagesPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) ConverterJob.run("Images → PDF") { _ ->
            val u = Converters.imagesToPdf(ctx, uris, "ZapShare_${System.currentTimeMillis()}")
            "PDF created from ${uris.size} image(s)" to u
        }
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = FileUtils.queryMeta(ctx, uri).name
        ConverterJob.run("PDF → Images") { _ ->
            val n = Converters.pdfToImages(ctx, uri, name, Converters.ImageFormat.JPG)
            "Exported $n page(s) as JPG" to null
        }
    }
    val pickText = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = FileUtils.queryMeta(ctx, uri).name
        ConverterJob.run("Text → PDF") { _ -> "Created ${name.substringBeforeLast('.')}.pdf" to Converters.textToPdf(ctx, uri, name) }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            mediaUri = uri
        }
    }

    ZapScaffold("File converter", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (job.running || job.message != null) SectionCard {
                if (job.running) {
                    Text("${job.title}…", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val p = job.progress
                    if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan3)
                    else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = Cyan3)
                } else {
                    Text(job.message ?: "", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        job.lastUri?.let { u ->
                            TextButton(onClick = {
                                val m = FileUtils.queryMeta(ctx, u)
                                nav.navigate(Routes.preview(u, m.name, m.mime))
                            }) { Text("Open") }
                        }
                        TextButton(onClick = { ConverterJob.state.value = ConverterJob.State() }) { Text("Dismiss") }
                    }
                    Text("Saved in Download/ZapShare/Converted", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ToolCard(Icons.Filled.Image, CatPhoto, "Image converter & compressor", "JPG ⇄ PNG ⇄ WEBP, resize, reduce size") { pickImages.launch("image/*") }
            ToolCard(Icons.Filled.Collections, CatApps, "Images → PDF", "Combine photos or scans into one PDF") { pickImagesPdf.launch("image/*") }
            ToolCard(Icons.Filled.PictureAsPdf, CatPdf, "PDF → Images", "Export every page as a JPG") { pickPdf.launch(arrayOf("application/pdf")) }
            ToolCard(Icons.Filled.TextSnippet, CatDocs, "Text → PDF", "Turn .txt notes into a PDF") { pickText.launch(arrayOf("text/*")) }
            ToolCard(Icons.Filled.Audiotrack, CatMusic, "Audio & video converter", "MP4 → MP3, compress video, GIF, WAV, OGG, MKV") {
                pickMedia.launch(arrayOf("video/*", "audio/*"))
            }
            Text("Everything runs on your phone. Nothing is uploaded.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    imageUris?.let { uris -> ImageOptionsDialog(ctx, uris) { imageUris = null } }
    mediaUri?.let { uri -> MediaOptionsDialog(ctx, uri) { mediaUri = null } }
}

@Composable
private fun ToolCard(icon: ImageVector, color: Color, title: String, sub: String, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(color, Color.White, 0.28f), color))
                ),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.Transform, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImageOptionsDialog(ctx: Context, uris: List<Uri>, onClose: () -> Unit) {
    var format by remember { mutableStateOf(Converters.ImageFormat.JPG) }
    var quality by remember { mutableFloatStateOf(85f) }
    var maxDim by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Convert ${uris.size} image(s)") },
        text = {
            Column {
                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Converters.ImageFormat.entries.forEach { f -> FilterChip(format == f, { format = f }, label = { Text(f.name) }) }
                }
                if (format != Converters.ImageFormat.PNG) {
                    Text("Quality ${quality.toInt()}% (lower = smaller file)", style = MaterialTheme.typography.labelLarge)
                    Slider(quality, { quality = it }, valueRange = 30f..100f)
                }
                Text("Max size", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Original", 2048 to "2048px", 1280 to "1280px", 720 to "720px").forEach { (v, l) ->
                        FilterChip(maxDim == v, { maxDim = v }, label = { Text(l) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onClose()
                ConverterJob.run("Converting images") { report ->
                    var last: Uri? = null
                    uris.forEachIndexed { i, u ->
                        val name = FileUtils.queryMeta(ctx, u).name
                        last = Converters.convertImage(ctx, u, name, format, quality.toInt(), maxDim)
                        report((i + 1f) / uris.size)
                    }
                    "Converted ${uris.size} image(s) to ${format.name}" to last
                }
            }) { Text("Convert") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

private data class OutputFormat(
    val preset: Converters.MediaPreset,
    val badge: String,
    val detail: String,
    val audio: Boolean
)

private val outputFormats = listOf(
    OutputFormat(Converters.MediaPreset.TO_MP3, "MP3", "Sound only, plays everywhere", audio = true),
    OutputFormat(Converters.MediaPreset.TO_M4A, "M4A", "AAC audio, smaller than WAV", audio = true),
    OutputFormat(Converters.MediaPreset.TO_WAV, "WAV", "Uncompressed audio", audio = true),
    OutputFormat(Converters.MediaPreset.TO_OGG, "OGG", "Compressed audio", audio = true),
    OutputFormat(Converters.MediaPreset.TO_MP4, "MP4", "Standard video, H.264", audio = false),
    OutputFormat(Converters.MediaPreset.COMPRESS_720, "720p", "Lighter picture, much smaller file", audio = false),
    OutputFormat(Converters.MediaPreset.COMPRESS_480, "480p", "Lowest picture, smallest file", audio = false),
    OutputFormat(Converters.MediaPreset.TO_GIF, "GIF", "First 10 seconds, no sound", audio = false),
    OutputFormat(Converters.MediaPreset.TO_MKV, "MKV", "New container, same quality", audio = false),
)

@Composable
private fun MediaOptionsDialog(ctx: Context, uri: Uri, onClose: () -> Unit) {
    val meta = remember(uri) { FileUtils.queryMeta(ctx, uri) }
    var selected by remember { mutableStateOf(outputFormats.first()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text("Choose output format")
                Text(
                    meta.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatGroup("Audio", outputFormats.filter { it.audio }, selected) { selected = it }
                FormatGroup("Video", outputFormats.filter { !it.audio }, selected) { selected = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val choice = selected
                onClose()
                ConverterJob.run("Converting to ${choice.badge}") { report ->
                    val dur = durationOf(ctx, uri)
                    val out = Converters.convertMedia(ctx, uri, meta.name, choice.preset, dur, report)
                    val base = "${meta.name.substringBeforeLast('.')}.${choice.preset.ext}"
                    val note = if (choice.preset == Converters.MediaPreset.COMPRESS_720 || choice.preset == Converters.MediaPreset.COMPRESS_480) {
                        "Saved $base (${choice.badge})"
                    } else "Saved $base"
                    note to out
                }
            }) { Text("Convert") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun FormatGroup(title: String, formats: List<OutputFormat>, selected: OutputFormat, onSelect: (OutputFormat) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    formats.forEach { format ->
        val on = format == selected
        val tint = if (format.audio) CatMusic else CatVideo
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, if (on) Cyan3 else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .background(if (on) Cyan3.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { onSelect(format) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(tint),
                contentAlignment = Alignment.Center
            ) {
                Text(format.badge, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(format.badge, fontWeight = FontWeight.SemiBold)
                Text(format.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (on) Icon(Icons.Filled.CheckCircle, "Selected", tint = Cyan3)
        }
    }
}

private fun durationOf(ctx: Context, uri: Uri): Long = runCatching {
    MediaMetadataRetriever().run {
        setDataSource(ctx, uri)
        val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        release(); d
    }
}.getOrDefault(0L)
