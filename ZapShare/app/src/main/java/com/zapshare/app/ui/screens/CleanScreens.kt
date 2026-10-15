@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.zapshare.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.zapshare.app.clean.ChatApp
import com.zapshare.app.clean.ChatScan
import com.zapshare.app.clean.CleanGroup
import com.zapshare.app.clean.CleanRepository
import com.zapshare.app.clean.NotifyBlock
import com.zapshare.app.clean.ScanProgress
import com.zapshare.app.data.AppInfo
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.MediaFile
import com.zapshare.app.data.MediaRepository
import com.zapshare.app.ui.components.AppIcon
import com.zapshare.app.ui.components.EmptyState
import com.zapshare.app.ui.components.FileThumb
import com.zapshare.app.ui.components.GradientButton
import com.zapshare.app.ui.components.LoadingBox
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.SelectCircle
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.Routes
import com.zapshare.app.ui.theme.BrandGradient
import com.zapshare.app.ui.theme.CatApps
import com.zapshare.app.ui.theme.CatDocs
import com.zapshare.app.ui.theme.CatDownload
import com.zapshare.app.ui.theme.CatMusic
import com.zapshare.app.ui.theme.CatPdf
import com.zapshare.app.ui.theme.CatPhoto
import com.zapshare.app.ui.theme.CatPink
import com.zapshare.app.ui.theme.CatVideo
import com.zapshare.app.ui.theme.Cyan1
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.ui.theme.Navy
import com.zapshare.app.util.FileUtils
import com.zapshare.app.util.Permissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val DangerRed = Color(0xFFEF4444)

// =====================================================================================
//  HUB
// =====================================================================================
private data class HubStats(
    val whatsapp: Long? = null,
    val telegram: Long? = null,
    val big: Long? = null,
    val apk: Long? = null,
)

@Composable
fun CleanHubScreen(nav: NavController) {
    val ctx = LocalContext.current
    val storage = remember { MediaRepository.storage() }
    val records by NotifyBlock.records.collectAsState()
    val stats by produceState(HubStats()) {
        NotifyBlock.init(ctx)
        value = value.copy(whatsapp = runCatching { CleanRepository.quickChatSize(ctx, ChatApp.WHATSAPP) }.getOrNull())
        value = value.copy(telegram = runCatching { CleanRepository.quickChatSize(ctx, ChatApp.TELEGRAM) }.getOrNull())
        value = value.copy(big = runCatching { CleanRepository.bigFiles(ctx, 10 * CleanRepository.MB).values.flatten().sumOf { it.size } }.getOrNull())
        value = value.copy(apk = runCatching { MediaRepository.apkFiles(ctx).sumOf { it.size } }.getOrNull())
    }
    fun sizeOrScan(v: Long?) = when (v) { null -> "Checking…"; 0L -> "All clean"; else -> FileUtils.formatSize(v) }

    ZapScaffold("Phone Cleaner", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- Storage header
            val frac = if (storage.total > 0) storage.used.toFloat() / storage.total else 0f
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(BrandGradient).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StorageRing(frac, 118.dp)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Storage used", color = Navy.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge)
                        Text(FileUtils.formatSize(storage.used), color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        Text("of ${FileUtils.formatSize(storage.total)} • ${FileUtils.formatSize(storage.free)} free",
                            color = Navy.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Navy).clickable { nav.navigate(Routes.SCAN) }
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CleaningServices, null, tint = Cyan1, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clean junk", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ---- Chat apps
            SectionLabel("Chat apps")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChatCard(ChatApp.WHATSAPP, sizeOrScan(stats.whatsapp), Modifier.weight(1f)) {
                    nav.navigate(Routes.cleanChat(ChatApp.WHATSAPP.key))
                }
                ChatCard(ChatApp.TELEGRAM, sizeOrScan(stats.telegram), Modifier.weight(1f)) {
                    nav.navigate(Routes.cleanChat(ChatApp.TELEGRAM.key))
                }
            }

            // ---- Deep clean tools
            SectionLabel("Deep clean")
            val tools = listOf(
                CleanTool(Icons.Filled.Storage, "Big Files", sizeOrScan(stats.big), CatPdf) { nav.navigate(Routes.CLEAN_BIG) },
                CleanTool(Icons.Filled.ContentCopy, "Duplicate Files", "Find exact copies", CatDocs) { nav.navigate(Routes.CLEAN_DUPES) },
                CleanTool(Icons.Filled.PhotoLibrary, "Photo Cleanup", "Similar, blurry, screenshots", CatPhoto) { nav.navigate(Routes.cleanMedia("photo")) },
                CleanTool(Icons.Filled.VideoLibrary, "Video Cleanup", "Large, short, recordings", CatVideo) { nav.navigate(Routes.cleanMedia("video")) },
                CleanTool(Icons.Filled.MusicNote, "Audio Cleanup", "Voice notes, recordings", CatPink) { nav.navigate(Routes.cleanMedia("audio")) },
                CleanTool(Icons.Filled.Apps, "App Cleanup", stats.apk?.takeIf { it > 0 }?.let { "APKs: ${FileUtils.formatSize(it)}" } ?: "Uninstall unused apps", CatApps) {
                    nav.navigate(Routes.CLEAN_APPS)
                },
                CleanTool(Icons.Filled.NotificationsOff, "Notification Blocker",
                    if (records.isNotEmpty()) "${records.size} blocked" else "Silence noisy apps", CatDownload) { nav.navigate(Routes.CLEAN_NOTIFY) },
            )
            tools.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t -> CleanToolCard(t, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (!Permissions.hasAllFiles()) {
                SectionCard {
                    Text("Allow all-files access for a deeper clean (WhatsApp sent files, voice notes, and every folder).",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { ctx.startActivity(Permissions.allFilesIntent(ctx)) }) { Text("Allow access") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private class CleanTool(val icon: ImageVector, val title: String, val subtitle: String, val color: Color, val onClick: () -> Unit)

@Composable
private fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun CleanToolCard(t: CleanTool, modifier: Modifier) {
    Surface(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = t.onClick), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp)) {
            ColorTile(t.icon, t.color, 46.dp)
            Spacer(Modifier.height(10.dp))
            Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = t.color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChatCard(app: ChatApp, sizeText: String, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val color = Color(app.color)
    val pkg = remember { app.packages.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null } }
    Box(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, .15f), color)))
            .clickable(onClick = onClick).padding(14.dp)
    ) {
        Column {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
                // The real app icon from the phone if installed, otherwise a generic chat icon.
                if (pkg != null) AppIcon(pkg, Modifier.size(34.dp))
                else Icon(if (app == ChatApp.WHATSAPP) Icons.Filled.Chat else Icons.Filled.Forum, null, tint = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Text("${app.label} Cleanup", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(sizeText, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ColorTile(icon: ImageVector, color: Color, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, .28f), color))),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
private fun StorageRing(fraction: Float, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Color.White.copy(alpha = .35f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(Navy, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fraction * 100).toInt()}%", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("used", color = Navy.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// =====================================================================================
//  SCANNING ANIMATION (shared)
// =====================================================================================
@Composable
fun ScanningView(title: String, progress: ScanProgress, color: Color, icon: ImageVector, iconContent: (@Composable () -> Unit)? = null) {
    val t = rememberInfiniteTransition(label = "scan")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val pulse by t.animateFloat(0.92f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Column(Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().rotate(angle)) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                val s = Size(size.width - stroke, size.height - stroke)
                drawArc(color.copy(alpha = .12f), 0f, 360f, false, Offset(inset, inset), s, style = Stroke(stroke))
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, color.copy(alpha = .4f), color)),
                    0f, 300f, false, Offset(inset, inset), s, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Box(
                Modifier.size(110.dp * pulse).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(lerp(color, Color.White, .3f), color))),
                contentAlignment = Alignment.Center
            ) {
                if (iconContent != null) iconContent() else Icon(icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        if (progress.files > 0 || progress.bytes > 0) {
            Text(
                buildString {
                    append("${progress.files} files")
                    if (progress.bytes > 0) append(" • ${FileUtils.formatSize(progress.bytes)}")
                },
                color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
        }
        Text(progress.label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
        val f = progress.fraction
        if (f != null) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = color, trackColor = color.copy(alpha = .15f))
        }
        Spacer(Modifier.height(12.dp))
        Text("Everything is checked on your phone. Nothing is uploaded.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Runs [block] and keeps the scan animation on screen for at least [minMs] so it doesn't flash. */
private suspend fun <T> withMinDuration(minMs: Long, block: suspend () -> T): T {
    val start = System.currentTimeMillis()
    val r = block()
    val left = minMs - (System.currentTimeMillis() - start)
    if (left > 0) delay(left)
    return r
}

// =====================================================================================
//  SELECTION + DELETE (shared)
// =====================================================================================
@Composable
private fun rememberCleanDeleter(onDone: () -> Unit): (List<MediaFile>) -> Unit {
    val ctx = LocalContext.current
    val del = rememberDeleter {
        appScope.launch {
            CleanRepository.prune(ctx)
            onDone()
        }
    }
    return { files -> del(files.map { it.uri }) }
}

@Composable
private fun DeleteBar(selected: List<MediaFile>, label: String = "Delete", onConfirmed: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    if (selected.isEmpty()) return
    val size = selected.sumOf { it.size }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${selected.size} selected", style = MaterialTheme.typography.titleSmall)
                Text(FileUtils.formatSize(size), color = DangerRed, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(DangerRed).clickable { ask = true }
                    .padding(horizontal = 26.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        icon = { Icon(Icons.Filled.Delete, null, tint = DangerRed) },
        title = { Text("Delete ${selected.size} file(s)?") },
        text = { Text("This frees ${FileUtils.formatSize(size)}. Deleted files can't be recovered.") },
        confirmButton = { TextButton(onClick = { ask = false; onConfirmed() }) { Text("Delete", color = DangerRed) } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancel") } }
    )
}

@Composable
private fun SelectAllRow(files: List<MediaFile>, selected: SnapshotStateList<MediaFile>, extra: String? = null) {
    val all = files.isNotEmpty() && selected.containsAll(files)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${files.size} files • ${FileUtils.formatSize(files.sumOf { it.size })}", style = MaterialTheme.typography.titleSmall)
            if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Select all", style = MaterialTheme.typography.labelLarge)
        Checkbox(all, { on ->
            if (on) files.forEach { if (it !in selected) selected.add(it) } else selected.removeAll(files.toSet())
        }, colors = CheckboxDefaults.colors(checkedColor = Cyan3))
    }
}

private fun SnapshotStateList<MediaFile>.toggle(f: MediaFile) { if (f in this) remove(f) else add(f) }

@Composable
private fun SelectableGrid(files: List<MediaFile>, selected: SnapshotStateList<MediaFile>, nav: NavController, header: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    LazyVerticalGrid(GridCells.Adaptive(108.dp), modifier, contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(files, key = { it.uri.toString() + it.path }) { f ->
            val sel = f in selected
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).combinedClickable(
                onClick = { selected.toggle(f) },
                onLongClick = { openFile(nav, ctx, f, files) }
            )) {
                FileThumb(f.uri, f.mime, f.name, Modifier.fillMaxSize(), 10.dp)
                if (sel) Box(Modifier.fillMaxSize().background(Cyan3.copy(alpha = .3f)))
                SelectCircle(sel, Modifier.align(Alignment.TopEnd).padding(6.dp), onDark = true)
                Text(FileUtils.formatSize(f.size), color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                        .background(Color(0x88000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun SelectableList(files: List<MediaFile>, selected: SnapshotStateList<MediaFile>, nav: NavController, header: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        if (header != null) item { header() }
        items(files, key = { it.uri.toString() + it.path }) { f ->
            val sub = buildString {
                append(FileUtils.formatSize(f.size))
                if (f.duration > 0) append(" • ${FileUtils.formatDuration(f.duration)}")
                f.path?.substringBeforeLast('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
            }
            FileRow(f.uri, f.name, f.mime, sub, selected = f in selected, onLongClick = { openFile(nav, ctx, f, files) }) {
                selected.toggle(f)
            }
        }
    }
}

/** Groups of look-alike files. The first of each group is marked KEEP and left unselected by default. */
@Composable
private fun ClusterList(clusters: List<List<MediaFile>>, selected: SnapshotStateList<MediaFile>, nav: NavController, thumbs: Boolean) {
    val ctx = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${clusters.size} sets • ${FileUtils.formatSize(clusters.sumOf { c -> c.drop(1).sumOf { it.size } })} can be freed",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    selected.clear(); clusters.forEach { selected.addAll(it.drop(1)) }
                }) { Text("Smart select") }
            }
        }
        items(clusters.size) { i ->
            val c = clusters[i]
            SectionCard(padding = 10.dp) {
                Text("${c.size} ${if (thumbs) "similar" else "copies"} • ${FileUtils.formatSize(c.first().size)} each",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
                if (thumbs) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(c, key = { it.uri.toString() }) { f ->
                            val sel = f in selected
                            Box(Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(
                                onClick = { selected.toggle(f) }, onLongClick = { openFile(nav, ctx, f, c) })) {
                                FileThumb(f.uri, f.mime, f.name, Modifier.fillMaxSize(), 10.dp)
                                if (sel) Box(Modifier.fillMaxSize().background(DangerRed.copy(alpha = .25f)))
                                SelectCircle(sel, Modifier.align(Alignment.TopEnd).padding(4.dp), onDark = true)
                                if (f == c.first()) KeepBadge(Modifier.align(Alignment.BottomStart).padding(4.dp))
                            }
                        }
                    }
                } else {
                    c.forEach { f ->
                        Box {
                            FileRow(f.uri, f.name, f.mime, f.path ?: FileUtils.formatSize(f.size), selected = f in selected,
                                onLongClick = { openFile(nav, ctx, f, c) }) { selected.toggle(f) }
                            if (f == c.first()) KeepBadge(Modifier.align(Alignment.TopStart).padding(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepBadge(modifier: Modifier) {
    Text("KEEP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.background(Color(0xFF16A34A), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
}

// =====================================================================================
//  PHOTO / VIDEO / AUDIO CLEANUP
// =====================================================================================
private fun kindInfo(kind: String): Triple<String, ImageVector, Color> = when (kind) {
    "photo" -> Triple("Photo Cleanup", Icons.Filled.PhotoLibrary, CatPhoto)
    "video" -> Triple("Video Cleanup", Icons.Filled.VideoLibrary, CatVideo)
    "audio" -> Triple("Audio Cleanup", Icons.Filled.MusicNote, CatPink)
    else -> Triple("Duplicate Files", Icons.Filled.ContentCopy, CatDocs)
}

private fun groupIcon(id: String): ImageVector = when (id) {
    "screen" -> Icons.Filled.FiberManualRecord
    "short" -> Icons.Filled.Timer
    "large" -> Icons.Filled.Storage
    "chat" -> Icons.Filled.Chat
    "downloads" -> Icons.Filled.Download
    "old" -> Icons.Filled.History
    "similar" -> Icons.Filled.Collections
    "blurry" -> Icons.Filled.BlurOn
    "screenshots" -> Icons.Filled.Screenshot
    "voice" -> Icons.Filled.Mic
    "recordings" -> Icons.Filled.KeyboardVoice
    else -> Icons.Filled.InsertDriveFile
}

@Composable
fun MediaCleanScreen(nav: NavController, kind: String) {
    val ctx = LocalContext.current
    val (title, icon, color) = kindInfo(kind)
    val stored by CleanRepository.groups.collectAsState()
    var scanTick by remember { mutableIntStateOf(if (CleanRepository.groups.value[kind] == null) 1 else 0) }
    var progress by remember { mutableStateOf(ScanProgress(true, "Starting…")) }
    var scanning by remember { mutableStateOf(scanTick > 0) }
    LaunchedEffect(scanTick) {
        if (scanTick == 0) return@LaunchedEffect
        scanning = true
        runCatching {
            withMinDuration(1500) {
                when (kind) {
                    "photo" -> CleanRepository.scanPhotos(ctx) { progress = it }
                    "video" -> CleanRepository.scanVideos(ctx) { progress = it }
                    else -> CleanRepository.scanAudio(ctx) { progress = it }
                }
            }
        }
        scanning = false
    }
    val groups = stored[kind].orEmpty()

    ZapScaffold(title, onBack = { nav.popBackStack() }, actions = {
        if (!scanning) IconButton(onClick = { scanTick++ }) { Icon(Icons.Filled.Refresh, "Scan again") }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                scanning -> ScanningView("Scanning ${title.substringBefore(' ').lowercase()}s…", progress, color, icon)
                groups.isEmpty() -> EmptyState(Icons.Filled.CheckCircle, "Nothing to clean here. Nice and tidy!")
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { SummaryCard(color, icon, groups.sumOf { it.reclaimable }, "can be cleaned", "${groups.sumOf { it.files.size }} files found") }
                    items(groups, key = { it.id }) { g -> GroupCard(g, color) { nav.navigate(Routes.cleanGroup(kind, g.id)) } }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(color: Color, icon: ImageVector, bytes: Long, line: String, sub: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(lerp(color, Color.White, .2f), color))).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(FileUtils.formatSize(bytes), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                Text(line, color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(sub, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
            }
            Icon(icon, null, tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
private fun GroupCard(g: CleanGroup, color: Color, onClick: () -> Unit) {
    SectionCard(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorTile(groupIcon(g.id), color, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.titleSmall)
                Text(g.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(FileUtils.formatSize(g.reclaimable), color = color, fontWeight = FontWeight.Bold)
                Text("${g.files.size} files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Preview strip
        val preview = g.files.take(4)
        if (preview.isNotEmpty() && preview.first().category != FileCategory.AUDIO) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                preview.forEach { f -> FileThumb(f.uri, f.mime, f.name, Modifier.weight(1f).aspectRatio(1f), 10.dp) }
                repeat(4 - preview.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun CleanGroupScreen(nav: NavController, kind: String, id: String) {
    val stored by CleanRepository.groups.collectAsState()
    val g = stored[kind]?.firstOrNull { it.id == id }
    val selected = remember { mutableStateListOf<MediaFile>() }
    var preselected by remember { mutableStateOf(false) }
    LaunchedEffect(g?.clusters) {
        if (!preselected && g?.clusters != null) { g.clusters.forEach { selected.addAll(it.drop(1)) }; preselected = true }
    }
    val delete = rememberCleanDeleter { selected.clear() }
    ZapScaffold(g?.title ?: "Cleanup", onBack = { nav.popBackStack() }, bottomBar = {
        DeleteBar(selected.toList()) { delete(selected.toList()) }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                g == null || g.files.isEmpty() -> EmptyState(Icons.Filled.CheckCircle, "All cleaned up!")
                g.clusters != null -> ClusterList(g.clusters, selected, nav, thumbs = kind == "photo")
                kind == "audio" -> SelectableList(g.files, selected, nav, header = { SelectAllRow(g.files, selected, g.subtitle) })
                else -> SelectableGrid(g.files, selected, nav, header = { SelectAllRow(g.files, selected, g.subtitle) })
            }
        }
    }
}

// =====================================================================================
//  DUPLICATES
// =====================================================================================
@Composable
fun DuplicatesScreen(nav: NavController) {
    val ctx = LocalContext.current
    val stored by CleanRepository.groups.collectAsState()
    var scanTick by remember { mutableIntStateOf(if (CleanRepository.groups.value["dupes"] == null) 1 else 0) }
    var progress by remember { mutableStateOf(ScanProgress(true, "Starting…")) }
    var scanning by remember { mutableStateOf(scanTick > 0) }
    LaunchedEffect(scanTick) {
        if (scanTick == 0) return@LaunchedEffect
        scanning = true
        runCatching { withMinDuration(1500) { CleanRepository.scanDuplicates(ctx) { progress = it } } }
        scanning = false
    }
    val clusters = stored["dupes"]?.firstOrNull()?.clusters.orEmpty()
    val selected = remember { mutableStateListOf<MediaFile>() }
    LaunchedEffect(scanning) { if (!scanning) { selected.clear(); clusters.forEach { selected.addAll(it.drop(1)) } } }
    val delete = rememberCleanDeleter { selected.clear() }

    ZapScaffold("Duplicate Files", onBack = { nav.popBackStack() }, actions = {
        if (!scanning) IconButton(onClick = { scanTick++ }) { Icon(Icons.Filled.Refresh, "Scan again") }
    }, bottomBar = { if (!scanning) DeleteBar(selected.toList()) { delete(selected.toList()) } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                scanning -> ScanningView("Finding duplicates…", progress, CatDocs, Icons.Filled.ContentCopy)
                clusters.isEmpty() -> EmptyState(Icons.Filled.CheckCircle, "No duplicate files found.")
                else -> ClusterList(clusters, selected, nav, thumbs = false)
            }
        }
    }
}

// =====================================================================================
//  BIG FILES
// =====================================================================================
private val bigThresholds = listOf(10L to "10 MB", 50L to "50 MB", 100L to "100 MB", 500L to "500 MB")

@Composable
fun BigFilesScreen(nav: NavController) {
    val ctx = LocalContext.current
    var minMb by remember { mutableLongStateOf(10L) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    val data by produceState<Map<String, List<MediaFile>>?>(null, minMb, reload) {
        value = null
        value = CleanRepository.bigFiles(ctx, minMb * CleanRepository.MB)
    }
    val selected = remember { mutableStateListOf<MediaFile>() }
    val delete = rememberCleanDeleter { selected.clear(); reload++ }
    val tabs = listOf("Photos" to Icons.Filled.Image, "Videos" to Icons.Filled.Movie, "Music" to Icons.Filled.Audiotrack, "Other" to Icons.Filled.InsertDriveFile)

    ZapScaffold("Big Files", onBack = { nav.popBackStack() }, bottomBar = {
        DeleteBar(selected.toList()) { delete(selected.toList()) }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val d = data
            Box(Modifier.padding(horizontal = 16.dp)) {
                SummaryCard(CatPdf, Icons.Filled.Storage, d?.values?.flatten()?.sumOf { it.size } ?: 0L,
                    "in files over $minMb MB", "${d?.values?.sumOf { it.size } ?: 0} big files")
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bigThresholds.forEach { (mb, label) ->
                    FilterChip(minMb == mb, { minMb = mb; selected.clear() }, label = { Text("> $label") })
                }
            }
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, (name, ic) ->
                    val n = d?.get(name)?.size
                    Tab(tab == i, { tab = i }, icon = { Icon(ic, null) },
                        text = { Text(if (n != null) "$name ($n)" else name, maxLines = 1, fontSize = 12.sp) })
                }
            }
            val list = d?.get(tabs[tab].first)
            Box(Modifier.weight(1f)) {
                when {
                    list == null -> LoadingBox()
                    list.isEmpty() -> EmptyState(Icons.Filled.CheckCircle, "No ${tabs[tab].first.lowercase()} over $minMb MB.")
                    tab <= 1 -> SelectableGrid(list, selected, nav, header = { SelectAllRow(list, selected) })
                    else -> SelectableList(list, selected, nav, header = { SelectAllRow(list, selected) })
                }
            }
        }
    }
}

// =====================================================================================
//  WHATSAPP / TELEGRAM CLEANUP
// =====================================================================================
@Composable
fun ChatCleanScreen(nav: NavController, appKey: String) {
    val ctx = LocalContext.current
    val app = remember(appKey) { ChatApp.of(appKey) }
    val color = Color(app.color)
    val stored by CleanRepository.chats.collectAsState()
    var scanTick by remember { mutableIntStateOf(1) }   // always rescan on open so new media shows up
    var progress by remember { mutableStateOf(ScanProgress(true, "Looking for ${app.label} folders…")) }
    var scanning by remember { mutableStateOf(true) }
    LaunchedEffect(scanTick) {
        scanning = true
        runCatching { withMinDuration(2200) { CleanRepository.scanChat(ctx, app) { progress = it } } }
        scanning = false
    }
    val scan = stored[app.key]
    var tab by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<MediaFile>() }
    val delete = rememberCleanDeleter { selected.clear() }
    val pkg = remember { app.packages.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null } }

    ZapScaffold("${app.label} Cleanup", onBack = { nav.popBackStack() }, actions = {
        if (!scanning) IconButton(onClick = { selected.clear(); scanTick++ }) { Icon(Icons.Filled.Refresh, "Scan again") }
    }, bottomBar = { if (!scanning) DeleteBar(selected.toList()) { delete(selected.toList()) } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (scanning || scan == null) {
                ScanningView("Scanning ${app.label}…", progress, color, Icons.Filled.Chat,
                    iconContent = pkg?.let { p -> @Composable { AppIcon(p, Modifier.size(64.dp)) } })
                return@Box
            }
            if (scan.all.isEmpty()) {
                EmptyState(Icons.Filled.CheckCircle,
                    if (!app.isInstalled(ctx)) "${app.label} isn't installed on this phone."
                    else "No ${app.label} media found.") {
                    if (!Permissions.hasAllFiles()) TextButton(onClick = { ctx.startActivity(Permissions.allFilesIntent(ctx)) }) {
                        Text("Allow all-files access to search deeper")
                    }
                }
                return@Box
            }
            ChatResults(scan, color, tab, { tab = it }, selected, nav)
        }
    }
}

@Composable
private fun ChatResults(
    scan: ChatScan, color: Color, tab: Int, onTab: (Int) -> Unit,
    selected: SnapshotStateList<MediaFile>, nav: NavController
) {
    val ctx = LocalContext.current
    val sections = listOf(
        Triple("Photos", Icons.Filled.Image, scan.photos),
        Triple("Videos", Icons.Filled.Movie, scan.videos),
        Triple("Audio", Icons.Filled.Audiotrack, scan.audio),
    )
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(16.dp)) {
            SummaryCard(color, Icons.Filled.CleaningServices, scan.size, "of media found",
                "${scan.photos.size} photos • ${scan.videos.size} videos • ${scan.audio.size} audio")
        }
        if (!Permissions.hasAllFiles()) {
            Text("Tip: allow all-files access to also find sent files and voice notes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp).clickable { ctx.startActivity(Permissions.allFilesIntent(ctx)) })
        }
        // Category cards act as tabs
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEachIndexed { i, (name, ic, files) ->
                val on = i == tab
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (on) color else MaterialTheme.colorScheme.surface)
                        .clickable { onTab(i) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(ic, null, tint = if (on) Color.White else color)
                    Text(name, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge)
                    Text(FileUtils.formatSize(files.sumOf { it.size }), style = MaterialTheme.typography.labelSmall,
                        color = if (on) Color.White.copy(alpha = .9f) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val files = sections[tab].third
        Box(Modifier.weight(1f)) {
            when {
                files.isEmpty() -> EmptyState(Icons.Filled.CheckCircle, "No ${sections[tab].first.lowercase()} found.")
                tab == 2 -> SelectableList(files, selected, nav, header = { SelectAllRow(files, selected) })
                else -> SelectableGrid(files, selected, nav, header = { SelectAllRow(files, selected) })
            }
        }
    }
}

// =====================================================================================
//  APP CLEANUP
// =====================================================================================
@Composable
fun AppCleanScreen(nav: NavController) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    val apps by produceState<List<AppInfo>?>(null, reload) {
        value = MediaRepository.apps(ctx).filter { it.packageName != ctx.packageName }.sortedByDescending { it.size }
    }
    val chosen = remember { mutableStateListOf<String>() }
    // Uninstall one app at a time: Android shows its own confirmation for each.
    val queue = remember { mutableStateListOf<String>() }
    var current by remember { mutableStateOf<String?>(null) }
    val uninstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (queue.isNotEmpty()) queue.removeAt(0)
        current = queue.firstOrNull()
        if (current == null) { chosen.clear(); reload++ }
    }
    LaunchedEffect(current) {
        current?.let { pkg ->
            uninstall.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).putExtra(Intent.EXTRA_RETURN_RESULT, true))
        }
    }

    // APK files
    var apkReload by remember { mutableIntStateOf(0) }
    val apks by produceState<List<Pair<MediaFile, String>>?>(null, apkReload) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            MediaRepository.apkFiles(ctx).sortedByDescending { it.size }.map { f -> f to apkStatus(ctx, f) }
        }
    }
    val selectedApks = remember { mutableStateListOf<MediaFile>() }
    val deleteApks = rememberCleanDeleter { selectedApks.clear(); apkReload++ }

    ZapScaffold("App Cleanup", onBack = { nav.popBackStack() }, bottomBar = {
        if (tab == 0 && chosen.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val size = apps.orEmpty().filter { it.packageName in chosen }.sumOf { it.size }
                Column(Modifier.weight(1f)) {
                    Text("${chosen.size} app(s)", style = MaterialTheme.typography.titleSmall)
                    Text(FileUtils.formatSize(size), color = DangerRed, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(DangerRed).clickable {
                    queue.clear(); queue.addAll(chosen); current = queue.firstOrNull()
                }.padding(horizontal = 22.dp, vertical = 13.dp)) {
                    Text("Uninstall", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (tab == 1) DeleteBar(selectedApks.toList()) { deleteApks(selectedApks.toList()) }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Installed apps") }, icon = { Icon(Icons.Filled.Apps, null) })
                Tab(tab == 1, { tab = 1 }, text = { Text("APK files") }, icon = { Icon(Icons.Filled.Android, null) })
            }
            if (tab == 0) {
                val list = apps
                if (list == null) LoadingBox()
                else LazyColumn(contentPadding = PaddingValues(12.dp)) {
                    item {
                        Text("${list.size} apps • ${FileUtils.formatSize(list.sumOf { it.size })} • biggest first",
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(8.dp))
                    }
                    items(list, key = { it.packageName }) { a ->
                        val on = a.packageName in chosen
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { if (on) chosen.remove(a.packageName) else chosen.add(a.packageName) }
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f) else Color.Transparent)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(a.packageName, Modifier.size(46.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${FileUtils.formatSize(a.size)} • installed ${DateUtils.getRelativeTimeSpanString(a.installTime)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            SelectCircle(on)
                        }
                    }
                }
            } else {
                val list = apks
                when {
                    list == null -> LoadingBox()
                    list.isEmpty() -> EmptyState(Icons.Filled.Android,
                        if (Permissions.hasAllFiles()) "No APK files found." else "No APK files found. Allow all-files access to search every folder.")
                    else -> {
                        val files = list.map { it.first }
                        LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            item { SelectAllRow(files, selectedApks, "Installer files you no longer need") }
                            items(list, key = { it.first.uri.toString() }) { (f, status) ->
                                FileRow(f.uri, f.name, f.mime, "${FileUtils.formatSize(f.size)} • $status",
                                    selected = f in selectedApks, onLongClick = { openFile(nav, ctx, f) }) { selectedApks.toggle(f) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun apkStatus(ctx: android.content.Context, f: MediaFile): String {
    val path = f.path ?: return "APK"
    return runCatching {
        @Suppress("DEPRECATION")
        val info = ctx.packageManager.getPackageArchiveInfo(path, 0) ?: return "APK"
        @Suppress("DEPRECATION")
        val installed = runCatching { ctx.packageManager.getPackageInfo(info.packageName, 0) }.getOrNull()
        when {
            installed == null -> "Not installed"
            (installed.versionName ?: "") == (info.versionName ?: "") -> "Installed (safe to delete)"
            else -> "Older/other version"
        }
    }.getOrDefault("APK")
}

// =====================================================================================
//  NOTIFICATION BLOCKER
// =====================================================================================
@Composable
fun NotificationBlockerScreen(nav: NavController) {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(NotifyBlock.hasAccess(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        NotifyBlock.init(ctx)
        access = NotifyBlock.hasAccess(ctx)
        if (access && !NotifyBlock.enabled.value && NotifyBlock.blocked.value.isEmpty()) NotifyBlock.setEnabled(true)
    }
    val enabled by NotifyBlock.enabled.collectAsState()
    val blocked by NotifyBlock.blocked.collectAsState()
    val records by NotifyBlock.records.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val apps by produceState<List<AppInfo>?>(null) {
        value = MediaRepository.apps(ctx).filter { it.packageName != ctx.packageName }
    }
    val labels = remember(apps) { apps.orEmpty().associate { it.packageName to it.label } }

    ZapScaffold("Notification Blocker", onBack = { nav.popBackStack() }) { pad ->
        if (!access) {
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ColorTile(Icons.Filled.NotificationsOff, CatDownload, 110.dp)
                Spacer(Modifier.height(20.dp))
                Text("Block annoying notifications", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                listOf(
                    "Choose the apps you don't want to hear from",
                    "Their notifications are cleared automatically",
                    "Blocked messages are saved here so you never lose anything",
                    "Music, calls and navigation are never blocked"
                ).forEach {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Cyan3, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
                GradientButton("Enable", { ctx.startActivity(NotifyBlock.accessIntent(ctx)) }, Modifier.fillMaxWidth())
                Text("On the next screen, turn on ZapShare.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            return@ZapScaffold
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Master switch card
            Box(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(lerp(CatDownload, Color.White, .2f), CatDownload))).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${records.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                        Text("notifications blocked", color = Color.White)
                        Text(if (enabled) "Blocking ${blocked.size} app(s)" else "Blocker is off",
                            color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(enabled, { NotifyBlock.setEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Navy, checkedThumbColor = Color.White))
                }
            }
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Apps") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Blocked (${records.size})") })
            }
            if (tab == 0) {
                val list = apps
                if (list == null) LoadingBox()
                else LazyColumn(contentPadding = PaddingValues(12.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("Turn on to block an app", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { NotifyBlock.setAll(list.map { it.packageName }, blocked.size < list.size) }) {
                                Text(if (blocked.size < list.size) "Block all" else "Allow all")
                            }
                        }
                    }
                    items(list, key = { it.packageName }) { a ->
                        val on = a.packageName in blocked
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { NotifyBlock.setBlocked(a.packageName, !on) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(a.packageName, Modifier.size(42.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Switch(on, { NotifyBlock.setBlocked(a.packageName, it) }, enabled = enabled)
                        }
                    }
                }
            } else {
                if (records.isEmpty()) EmptyState(Icons.Filled.NotificationsOff, "Blocked notifications will show up here.")
                else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tap one to open the app", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { NotifyBlock.clearRecords() }) { Text("Clear all") }
                        }
                    }
                    items(records.size) { i ->
                        val r = records[i]
                        SectionCard(Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                            ctx.packageManager.getLaunchIntentForPackage(r.pkg)?.let { ctx.startActivity(it) }
                        }, padding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(r.pkg, Modifier.size(36.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        Text(labels[r.pkg] ?: r.pkg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(DateUtils.getRelativeTimeSpanString(r.time).toString(), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (r.title.isNotBlank()) Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (r.text.isNotBlank()) Text(r.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
