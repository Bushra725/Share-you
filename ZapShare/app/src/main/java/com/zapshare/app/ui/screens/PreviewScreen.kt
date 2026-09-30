@file:OptIn(ExperimentalMaterial3Api::class)

package com.zapshare.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.ShareItem
import com.zapshare.app.ui.components.ApkIcon
import com.zapshare.app.ui.components.EmptyState
import com.zapshare.app.ui.components.GradientButton
import com.zapshare.app.ui.components.LoadingBox
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.Routes
import com.zapshare.app.ui.navigation.Selection
import com.zapshare.app.ui.theme.MonoStyle
import com.zapshare.app.util.FileUtils
import com.zapshare.app.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

@Composable
fun PreviewScreen(nav: NavController, uri: Uri, name: String, mimeIn: String) {
    val ctx = LocalContext.current
    val mime = mimeIn.ifEmpty { FileUtils.mimeOf(name) }
    val cat = FileUtils.categoryOf(mime, name)
    val ext = FileUtils.extension(name)
    ZapScaffold(
        name, onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = {
                Selection.replace(listOf(FileUtils.queryMeta(ctx, uri).let { if (it.size > 0) it else ShareItem(uri, name, it.size, mime) }))
                nav.navigate(Routes.SEND)
            }) { Icon(Icons.Filled.Send, "Send with ZapShare") }
            IconButton(onClick = { FileUtils.shareExternal(ctx, listOf(uri), mime) }) { Icon(Icons.Filled.Share, "Share") }
            IconButton(onClick = { FileUtils.openWith(ctx, uri, mime) }) { Icon(Icons.Filled.OpenInNew, "Open with") }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                cat == FileCategory.IMAGE -> ZoomableImage(uri)
                cat == FileCategory.VIDEO -> VideoSurface(uri, Modifier.fillMaxSize())
                cat == FileCategory.AUDIO -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                    VideoSurface(uri, Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp)), audio = true)
                }
                ext == "pdf" || mime == "application/pdf" -> PdfViewer(uri)
                cat == FileCategory.APK -> ApkInfo(uri, name)
                ext == "zip" -> ZipViewer(uri)
                FileUtils.isTextLike(mime, name) -> TextViewer(uri)
                else -> EmptyState(Icons.Filled.InsertDriveFile, "No built-in preview for .$ext files") {
                    GradientButton("Open with another app", { FileUtils.openWith(ctx, uri, mime) })
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(uri: Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
        )
    }
}

/** ExoPlayer view used by the previewer and the video player. */
@AndroidOptIn(UnstableApi::class)
@Composable
fun VideoSurface(uri: Uri, modifier: Modifier = Modifier, audio: Boolean = false, onPlayer: (ExoPlayer) -> Unit = {}, onFullscreen: ((Boolean) -> Unit)? = null) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true
        }
    }
    LaunchedEffect(player) { onPlayer(player) }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                keepScreenOn = true
                setShowNextButton(false); setShowPreviousButton(false)
                if (audio) { controllerShowTimeoutMs = 0; controllerHideOnTouch = false; defaultArtwork = null }
                if (onFullscreen != null) setFullscreenButtonClickListener { full -> onFullscreen(full) }
            }
        },
        modifier = modifier.background(Color.Black)
    )
}

@Composable
private fun PdfViewer(uri: Uri) {
    val ctx = LocalContext.current
    val state by produceState<Pair<PdfRenderer, ParcelFileDescriptor>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")!!
                PdfRenderer(pfd) to pfd
            }.getOrNull()
        }
    }
    DisposableEffect(state) {
        val open = state
        onDispose { open?.let { runCatching { it.first.close(); it.second.close() } } }
    }
    val renderer = state?.first ?: run { LoadingBox(); return }
    val lock = remember { Mutex() }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0 until renderer.pageCount).toList()) { i ->
                val bmp by produceState<Bitmap?>(null, i) {
                    value = withContext(Dispatchers.IO) {
                        lock.withLock {
                            runCatching {
                                renderer.openPage(i).use { p ->
                                    val h = (widthPx.toFloat() * p.height / p.width).toInt()
                                    Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { b ->
                                        b.eraseColor(AColor.WHITE)
                                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }.getOrNull()
                        }
                    }
                }
                val b = bmp
                if (b == null) Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White))
                else Image(b.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            }
        }
    }
}

@Composable
private fun TextViewer(uri: Uri) {
    val ctx = LocalContext.current
    val text by produceState<String?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)!!.use { s ->
                    val bytes = s.readNBytesCompat(1_000_000)
                    String(bytes, Charsets.UTF_8) + if (bytes.size >= 1_000_000) "\n\n… (preview limited to 1 MB)" else ""
                }
            }.getOrElse { "Cannot read file: ${it.message}" }
        }
    }
    val t = text ?: run { LoadingBox(); return }
    SelectionContainer {
        Text(t, style = MonoStyle, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()).padding(12.dp))
    }
}

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

@Composable
private fun ZipViewer(uri: Uri) {
    val ctx = LocalContext.current
    val entries by produceState<List<Pair<String, Long>>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val list = mutableListOf<Pair<String, Long>>()
                ZipInputStream(ctx.contentResolver.openInputStream(uri)!!).use { z ->
                    var e = z.nextEntry
                    while (e != null && list.size < 2000) { list += e.name to e.size; e = z.nextEntry }
                }
                list
            }.getOrDefault(emptyList())
        }
    }
    val list = entries ?: run { LoadingBox(); return }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { Text("${list.size} entries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp)) }
        items(list) { (n, s) ->
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(10.dp))
                Text(n, Modifier.weight(1f), maxLines = 1)
                if (s >= 0) Text(FileUtils.formatSize(s), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ApkInfo(uri: Uri, name: String) {
    val ctx = LocalContext.current
    val path by produceState<String?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            if (uri.scheme == "file") uri.path else runCatching { FileUtils.copyToCache(ctx, uri, name).absolutePath }.getOrNull()
        }
    }
    val p = path ?: run { LoadingBox(); return }
    val info = remember(p) {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageArchiveInfo(p, 0)?.also {
            it.applicationInfo?.sourceDir = p; it.applicationInfo?.publicSourceDir = p
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ApkIcon(p, Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(16.dp))
        Text(info?.applicationInfo?.loadLabel(ctx.packageManager)?.toString() ?: name, style = MaterialTheme.typography.titleLarge)
        Text("Version ${info?.versionName ?: "?"} • ${FileUtils.formatSize(File(p).length())}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(info?.packageName ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        GradientButton("Install", {
            if (Permissions.canInstallApks(ctx)) FileUtils.installApk(ctx, Uri.fromFile(File(p)))
            else ctx.startActivity(Permissions.installSourceIntent(ctx))
        }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        SectionCard { Text("Only install apps from people you trust.", style = MaterialTheme.typography.bodySmall) }
    }
}
