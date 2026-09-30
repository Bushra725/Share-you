@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.zapshare.app.ui.screens

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zapshare.app.data.AppInfo
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.MediaFile
import com.zapshare.app.data.MediaRepository
import com.zapshare.app.data.MediaRepository.toMediaFile
import com.zapshare.app.player.MusicController
import com.zapshare.app.ui.components.AppIcon
import com.zapshare.app.ui.components.EmptyState
import com.zapshare.app.ui.components.FileThumb
import com.zapshare.app.ui.components.GradientButton
import com.zapshare.app.ui.components.LoadingBox
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.SelectCircle
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.Routes
import com.zapshare.app.ui.navigation.Selection
import com.zapshare.app.ui.navigation.goPreview
import com.zapshare.app.ui.theme.CatApps
import com.zapshare.app.ui.theme.CatDocs
import com.zapshare.app.ui.theme.CatDownload
import com.zapshare.app.ui.theme.CatMusic
import com.zapshare.app.ui.theme.CatPhoto
import com.zapshare.app.ui.theme.CatSafe
import com.zapshare.app.ui.theme.CatVideo
import com.zapshare.app.premium.AdBanner
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.util.FileUtils
import com.zapshare.app.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Files queued for import into the Safebox (picked from the file manager). */
object SafeboxQueue { val pending = mutableStateListOf<Uri>() }

/** Opens a file with the best built-in viewer. */
fun openFile(nav: NavController, ctx: android.content.Context, f: MediaFile, siblings: List<MediaFile> = emptyList()) {
    when (f.category) {
        FileCategory.AUDIO -> {
            val list = siblings.filter { it.category == FileCategory.AUDIO }.ifEmpty { listOf(f) }
            MusicController.playList(ctx, list, list.indexOf(f).coerceAtLeast(0))
            nav.navigate(Routes.NOW_PLAYING)
        }
        FileCategory.VIDEO -> nav.navigate(Routes.video(f.uri, f.name))
        else -> nav.goPreview(f.uri, f.name, f.mime)
    }
}

/** Deletes files, asking the system for consent on Android 11+ where required. */
@Composable
fun rememberDeleter(onDone: () -> Unit): (List<Uri>) -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        onDone()
    }
    return { uris ->
        val files = uris.filter { it.scheme == "file" }
        files.forEach { u -> File(u.path ?: "").let { if (it.isDirectory) it.deleteRecursively() else it.delete() } }
        val content = uris.filter { it.scheme == "content" }
        if (content.isEmpty()) {
            onDone()
        } else if (Build.VERSION.SDK_INT >= 30) {
            val media = ArrayList<Uri>()
            val other = ArrayList<Uri>()
            content.forEach { uri ->
                val mediaUri = mediaItemUri(ctx, uri)
                if (mediaUri != null) media += mediaUri else other += uri
            }
            var failed = 0
            other.forEach { if (!deleteStoredFile(ctx, it)) failed++ }
            if (media.isNotEmpty()) {
                val launched = runCatching {
                    val pi = MediaStore.createDeleteRequest(ctx.contentResolver, media)
                    launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }.isSuccess
                if (!launched) {
                    Toast.makeText(ctx, "Couldn't delete the selected files", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            } else {
                if (failed > 0) {
                    Toast.makeText(
                        ctx,
                        if (Permissions.hasAllFiles()) "Some files could not be deleted"
                        else "Allow all-files access to delete these files",
                        Toast.LENGTH_LONG
                    ).show()
                    if (!Permissions.hasAllFiles()) ctx.startActivity(Permissions.allFilesIntent(ctx))
                }
                onDone()
            }
        } else {
            content.forEach { runCatching { ctx.contentResolver.delete(it, null, null) } }
            onDone()
        }
    }
}

/** Photos, videos, and audio can use the system delete prompt. Other files cannot. */
private fun mediaItemUri(ctx: Context, uri: Uri): Uri? {
    val text = uri.toString()
    if (text.contains("/images/media/") || text.contains("/video/media/") || text.contains("/audio/media/")) return uri
    return runCatching {
        ctx.contentResolver.query(
            uri,
            arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns._ID),
            null, null, null
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val id = c.getLong(1)
            val base = when (c.getInt(0)) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> return null
            }
            ContentUris.withAppendedId(base, id)
        }
    }.getOrNull()
}

/** Deletes a document, archive, or other non-media file without the media-only delete request. */
private fun deleteStoredFile(ctx: Context, uri: Uri): Boolean {
    if (Permissions.hasAllFiles()) {
        val path = runCatching {
            ctx.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        if (!path.isNullOrEmpty()) {
            val file = File(path)
            if (file.exists() && (if (file.isDirectory) file.deleteRecursively() else file.delete())) {
                runCatching { ctx.contentResolver.delete(uri, null, null) }
                return true
            }
        }
    }
    return runCatching { ctx.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
}

// ================================================================================ FILES HOME
@Composable
fun FilesScreen(nav: NavController) {
    val ctx = LocalContext.current
    val counts by produceState(mapOf<String, Int>()) {
        value = withContext(Dispatchers.IO) {
            mapOf(
                "music" to MediaRepository.audio(ctx).size,
                "videos" to MediaRepository.videos(ctx).size,
                "photos" to MediaRepository.images(ctx).size,
                "docs" to MediaRepository.documents(ctx).size,
                "received" to MediaRepository.received(ctx).size,
                "apps" to MediaRepository.apps(ctx).size,
            )
        }
    }
    val recent by produceState<List<MediaFile>?>(null) { value = MediaRepository.recent(ctx, 20) }

    ZapScaffold("Files", actions = {
        IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Filled.Search, "Search") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { nav.navigate(Routes.SEARCH) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text("Search for local files", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard {
                fun c(k: String) = counts[k]?.toString() ?: "…"
                val tiles: List<Pair<Triple<ImageVector, String, Color>, () -> Unit>> = listOf(
                    Triple(Icons.Filled.MusicNote, "Music", CatMusic) to { nav.navigate(Routes.category(FileCategory.AUDIO)) },
                    Triple(Icons.Filled.Apps, "Apps", CatApps) to { nav.navigate(Routes.APPS) },
                    Triple(Icons.Filled.Movie, "Videos", CatVideo) to { nav.navigate(Routes.category(FileCategory.VIDEO)) },
                    Triple(Icons.Filled.Image, "Photos", CatPhoto) to { nav.navigate(Routes.category(FileCategory.IMAGE)) },
                    Triple(Icons.Filled.Description, "Documents", CatDocs) to { nav.navigate(Routes.category(FileCategory.DOCUMENT)) },
                    Triple(Icons.Filled.Transform, "Converter", Color(0xFFEF4444)) to { nav.navigate(Routes.CONVERTER) },
                    Triple(Icons.Filled.Lock, "Safebox", CatSafe) to { nav.navigate(Routes.SAFEBOX) },
                    Triple(Icons.Filled.Download, "Received", CatDownload) to { nav.navigate(Routes.category(FileCategory.OTHER)) },
                )
                val badges = listOf(c("music"), c("apps"), c("videos"), c("photos"), c("docs"), "", "", c("received"))
                tiles.chunked(4).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, (t, click) ->
                            com.zapshare.app.ui.components.ToolTile(t.first, t.second, t.third, Modifier.weight(1f),
                                badge = badges[r * 4 + i].ifEmpty { null }, onClick = click)
                        }
                    }
                }
            }
            StorageCard { nav.navigate(Routes.BROWSER) }
            com.zapshare.app.premium.AdBanner(onRemoveAds = { nav.navigate(Routes.PREMIUM) })
            SectionCard(padding = 4.dp) {
                MenuRow(Icons.Filled.FolderOpen, "Browse internal storage") { nav.navigate(Routes.BROWSER) }
                MenuRow(Icons.Filled.CleaningServices, "Clean large files", "Find what fills up your phone") { nav.navigate(Routes.LARGE) }
            }
            SectionCard {
                Text("Recent files", style = MaterialTheme.typography.titleMedium)
                val r = recent
                if (r == null) LoadingBox() else if (r.isEmpty()) Text("No recent files", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else r.forEach { f ->
                    FileRow(f.uri, f.name, f.mime, "${FileUtils.formatSize(f.size)} • ${FileUtils.formatDate(f.dateModified)}") {
                        openFile(nav, ctx, f, r)
                    }
                }
            }
        }
    }
}

// ================================================================================ CATEGORY
@Composable
fun CategoryScreen(nav: NavController, cat: FileCategory) {
    val ctx = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val files by produceState<List<MediaFile>?>(null, cat, reload) { value = MediaRepository.byCategory(ctx, cat) }
    val selected = remember { mutableStateListOf<MediaFile>() }
    val deleter = rememberDeleter { selected.clear(); reload++ }
    val selecting = selected.isNotEmpty()
    BackHandler(selecting) { selected.clear() }
    val title = if (cat == FileCategory.OTHER) "Received" else cat.label

    ZapScaffold(
        if (selecting) "${selected.size} selected" else title,
        onBack = { if (selecting) selected.clear() else nav.popBackStack() },
        actions = {
            if (selecting) {
                IconButton(onClick = { files?.let { selected.clear(); selected.addAll(it) } }) { Icon(Icons.Filled.SelectAll, "Select all") }
                IconButton(onClick = {
                    Selection.replace(selected.map { it.toShareItem() }); selected.clear(); nav.navigate(Routes.SEND)
                }) { Icon(Icons.Filled.Send, "Send with ZapShare") }
                IconButton(onClick = { FileUtils.shareExternal(ctx, selected.map { it.uri }) }) { Icon(Icons.Filled.Share, "Share") }
                IconButton(onClick = {
                    SafeboxQueue.pending.addAll(selected.map { it.uri }); selected.clear(); nav.navigate(Routes.SAFEBOX)
                }) { Icon(Icons.Filled.Lock, "Move to Safebox") }
                IconButton(onClick = { deleter(selected.map { it.uri }) }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        }
    ) { pad ->
        val list = files
        Box(Modifier.padding(pad)) {
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(Icons.Filled.FolderOpen, "No ${title.lowercase()} found")
                cat == FileCategory.IMAGE || cat == FileCategory.VIDEO ->
                    LazyVerticalGrid(GridCells.Adaptive(110.dp), contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(list, key = { it.uri.toString() }) { f ->
                            val sel = f in selected
                            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).combinedClickable(
                                onClick = { if (selecting) { if (sel) selected.remove(f) else selected.add(f) } else openFile(nav, ctx, f, list) },
                                onLongClick = { if (!sel) selected.add(f) }
                            )) {
                                FileThumb(f.uri, f.mime, f.name, Modifier.fillMaxSize(), 10.dp)
                                if (sel) Box(Modifier.fillMaxSize().background(Cyan3.copy(alpha = .3f)))
                                if (selecting) SelectCircle(sel, Modifier.align(Alignment.TopEnd).padding(6.dp), onDark = true)
                                if (f.duration > 0) Text(FileUtils.formatDuration(f.duration), color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0x88000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
                            }
                        }
                    }
                else -> LazyColumn(contentPadding = PaddingValues(12.dp)) {
                    items(list, key = { it.uri.toString() }) { f ->
                        val sel = f in selected
                        val sub = buildString {
                            append(FileUtils.formatSize(f.size))
                            if (f.duration > 0) append(" • ${FileUtils.formatDuration(f.duration)}")
                            f.artist?.let { append(" • $it") }
                            if (f.artist == null) append(" • ${FileUtils.formatDate(f.dateModified)}")
                        }
                        FileRow(f.uri, f.name, f.mime, sub, selected = if (selecting) sel else null,
                            onLongClick = { if (!sel) selected.add(f) }) {
                            if (selecting) { if (sel) selected.remove(f) else selected.add(f) } else openFile(nav, ctx, f, list)
                        }
                    }
                }
            }
        }
    }
}

// ================================================================================ BROWSER
private data class Clip(val files: List<File>, val move: Boolean)

@Composable
fun BrowserScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(root) }
    var reload by remember { mutableIntStateOf(0) }
    var hasAccess by remember { mutableStateOf(Permissions.hasAllFiles()) }
    var menuFor by remember { mutableStateOf<File?>(null) }
    var renaming by remember { mutableStateOf<File?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var clip by remember { mutableStateOf<Clip?>(null) }
    var busy by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { hasAccess = Permissions.hasAllFiles() }

    val entries by produceState<List<File>?>(null, dir, reload, hasAccess) {
        value = withContext(Dispatchers.IO) {
            dir.listFiles()?.filterNot { it.name.startsWith(".") }
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        }
    }
    BackHandler(dir != root) { dir = dir.parentFile ?: root }

    ZapScaffold(
        if (dir == root) "Internal storage" else dir.name,
        onBack = { if (dir != root) dir = dir.parentFile ?: root else nav.popBackStack() },
        actions = { if (hasAccess) IconButton(onClick = { newFolder = true }) { Icon(Icons.Filled.CreateNewFolder, "New folder") } },
        floatingActionButton = {
            clip?.let { c ->
                FloatingActionButton(onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            c.files.forEach { f ->
                                val target = FileUtils.uniqueFile(dir, f.name)
                                if (c.move && f.renameTo(target)) return@forEach
                                f.copyRecursively(target, overwrite = false)
                                if (c.move) f.deleteRecursively()
                            }
                        }
                        clip = null; busy = false; reload++
                    }
                }, containerColor = Cyan3) { Icon(Icons.Filled.ContentPaste, "Paste here") }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (!hasAccess) {
                EmptyState(Icons.Filled.Storage, "Allow “All files access” to browse every folder, like a full file manager.") {
                    GradientButton("Allow access", { ctx.startActivity(Permissions.allFilesIntent(ctx)) })
                }
                return@Column
            }
            Text(dir.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (busy) LoadingBox()
            val list = entries
            if (list == null) LoadingBox()
            else if (list.isEmpty()) EmptyState(Icons.Filled.FolderOpen, "This folder is empty")
            else LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(list, key = { it.absolutePath }) { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(
                            onClick = { if (f.isDirectory) dir = f else openFile(nav, ctx, f.toMediaFile()) },
                            onLongClick = { menuFor = f }
                        ).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (f.isDirectory) {
                            com.zapshare.app.ui.components.BrandIconBox(Icons.Filled.Folder, 48.dp, 26.dp)
                        } else FileThumb(Uri.fromFile(f), FileUtils.mimeOf(f.name), f.name, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (f.isDirectory) "${f.list()?.size ?: 0} items" else "${FileUtils.formatSize(f.length())} • ${FileUtils.formatDate(f.lastModified())}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (f.isDirectory) Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    menuFor?.let { f ->
        val actions = buildList<Pair<String, () -> Unit>> {
            if (!f.isDirectory) {
                add("Open with…" to { FileUtils.openWith(ctx, Uri.fromFile(f), FileUtils.mimeOf(f.name)) })
                add("Send with ZapShare" to { Selection.replace(listOf(f.toMediaFile().toShareItem())); nav.navigate(Routes.SEND) })
                add("Share" to { FileUtils.shareExternal(ctx, listOf(Uri.fromFile(f))) })
                add("Move to Safebox" to { SafeboxQueue.pending.add(Uri.fromFile(f)); nav.navigate(Routes.SAFEBOX) })
            }
            add("Rename" to { renaming = f })
            add("Copy" to { clip = Clip(listOf(f), false); Toast.makeText(ctx, "Open a folder and tap Paste", Toast.LENGTH_SHORT).show() })
            add("Move" to { clip = Clip(listOf(f), true); Toast.makeText(ctx, "Open a folder and tap Paste", Toast.LENGTH_SHORT).show() })
            add("Delete" to { if (f.isDirectory) f.deleteRecursively() else f.delete(); reload++ })
        }
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    actions.forEach { (label, act) ->
                        Text(label, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { menuFor = null; act() }.padding(12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Close") } }
        )
    }
    renaming?.let { f ->
        var name by remember(f) { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val ok = name.isNotBlank() && f.renameTo(File(f.parentFile, FileUtils.safeName(name)))
                    if (!ok) Toast.makeText(ctx, "Rename failed", Toast.LENGTH_SHORT).show()
                    renaming = null; reload++
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
    if (newFolder) {
        var name by remember { mutableStateOf("New folder") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text("New folder") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { File(dir, FileUtils.safeName(name)).mkdirs(); newFolder = false; reload++ }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Cancel") } }
        )
    }
}

// ================================================================================ LARGE FILES (Clean)
@Composable
fun LargeFilesScreen(nav: NavController) {
    val ctx = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val files by produceState<List<MediaFile>?>(null, reload) { value = MediaRepository.largeFiles(ctx) }
    val selected = remember { mutableStateListOf<MediaFile>() }
    val deleter = rememberDeleter { selected.clear(); reload++ }
    ZapScaffold("Clean large files", onBack = { nav.popBackStack() }, bottomBar = {
        if (selected.isNotEmpty()) Box(Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            GradientButton("Delete ${selected.size} • free ${FileUtils.formatSize(selected.sumOf { it.size })}",
                { deleter(selected.map { it.uri }) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Delete)
        }
    }) { pad ->
        val list = files
        Column(Modifier.padding(pad).fillMaxSize()) {
            AdBanner(onRemoveAds = { nav.navigate(Routes.PREMIUM) })
            Box(Modifier.weight(1f)) {
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(Icons.Filled.CleaningServices, "No files bigger than 50 MB. Nice and tidy!")
                else -> LazyColumn(contentPadding = PaddingValues(12.dp)) {
                    item {
                        Text("${list.size} files over 50 MB • ${FileUtils.formatSize(list.sumOf { it.size })} total",
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(8.dp))
                    }
                    items(list, key = { it.uri.toString() }) { f ->
                        val sel = f in selected
                        FileRow(f.uri, f.name, f.mime, "${FileUtils.formatSize(f.size)} • ${f.path?.substringBeforeLast('/')?.substringAfterLast('/') ?: ""}",
                            selected = sel, onLongClick = { openFile(nav, ctx, f) }) { if (sel) selected.remove(f) else selected.add(f) }
                    }
                }
            }
            }
        }
    }
}

// ================================================================================ SEARCH
@Composable
fun SearchScreen(nav: NavController) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val results by produceState<List<MediaFile>>(emptyList(), q) {
        kotlinx.coroutines.delay(250)
        value = MediaRepository.search(ctx, q)
    }
    ZapScaffold("Search", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad)) {
            TextField(
                q, { q = it }, singleLine = true, placeholder = { Text("File name…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
            )
            LazyColumn(contentPadding = PaddingValues(12.dp)) {
                if (q.isNotBlank() && results.isEmpty()) item { EmptyState(Icons.Filled.Search, "No matches") }
                items(results, key = { it.uri.toString() }) { f ->
                    FileRow(f.uri, f.name, f.mime, "${FileUtils.formatSize(f.size)} • ${f.path ?: ""}") { openFile(nav, ctx, f, results) }
                }
            }
        }
    }
}

// ================================================================================ APPS
@Composable
fun AppsScreen(nav: NavController) {
    val ctx = LocalContext.current
    var showSystem by remember { mutableStateOf(false) }
    val apps by produceState<List<AppInfo>?>(null, showSystem) { value = MediaRepository.apps(ctx, showSystem) }
    var menu by remember { mutableStateOf<AppInfo?>(null) }
    ZapScaffold("Apps", onBack = { nav.popBackStack() }, actions = {
        TextButton(onClick = { showSystem = !showSystem }) { Text(if (showSystem) "Hide system" else "Show system") }
    }) { pad ->
        val list = apps
        Box(Modifier.padding(pad)) {
            if (list == null) LoadingBox() else LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(list, key = { it.packageName }) { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { menu = a }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.packageName, Modifier.size(46.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${a.versionName ?: ""} • ${FileUtils.formatSize(a.size)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { Selection.replace(listOf(a.toShareItem())); nav.navigate(Routes.SEND) }) {
                            Icon(Icons.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    menu?.let { a ->
        val opts = listOf<Pair<String, () -> Unit>>(
            "Open" to { ctx.packageManager.getLaunchIntentForPackage(a.packageName)?.let { ctx.startActivity(it) } },
            "Send with ZapShare" to { Selection.replace(listOf(a.toShareItem())); nav.navigate(Routes.SEND) },
            "Share APK" to { FileUtils.shareExternal(ctx, listOf(Uri.fromFile(File(a.apkPath))), "application/vnd.android.package-archive") },
            "Backup APK to Download/ZapShare/Apps" to {
                runCatching { FileUtils.copyToDownloads(ctx, Uri.fromFile(File(a.apkPath)), a.toShareItem().name, "application/vnd.android.package-archive", "Apps") }
                    .onSuccess { Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show() }
            },
            "App info" to { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}"))) },
            "Uninstall" to { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.packageName}"))) },
        )
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text(a.label) },
            text = {
                Column {
                    if (a.splitPaths.isNotEmpty()) Text("Note: this app uses split APKs; only the base APK is shared.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    opts.forEach { (label, act) ->
                        Text(label, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { menu = null; act() }.padding(12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menu = null }) { Text("Close") } }
        )
    }
}
