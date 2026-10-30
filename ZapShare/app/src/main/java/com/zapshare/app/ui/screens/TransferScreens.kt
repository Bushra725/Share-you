@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.zapshare.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zapshare.app.premium.AdBanner
import com.zapshare.app.premium.Ads
import com.zapshare.app.premium.Premium
import com.zapshare.app.R
import androidx.compose.ui.res.stringResource
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.zapshare.app.data.AppInfo
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.MediaFile
import com.zapshare.app.data.MediaRepository
import com.zapshare.app.data.Prefs
import com.zapshare.app.data.ShareItem
import com.zapshare.app.pc.PcShare
import com.zapshare.app.transfer.Direction
import com.zapshare.app.transfer.HostInfo
import com.zapshare.app.transfer.ItemStatus
import com.zapshare.app.transfer.NearbyDevice
import com.zapshare.app.transfer.NetUtils
import com.zapshare.app.transfer.Role
import com.zapshare.app.transfer.TransferItem
import com.zapshare.app.transfer.TransferManager
import com.zapshare.app.ui.components.AppIcon
import com.zapshare.app.ui.components.categoryStyle
import com.zapshare.app.ui.components.AvatarBubble
import com.zapshare.app.ui.components.EmptyState
import com.zapshare.app.ui.components.FileThumb
import com.zapshare.app.ui.components.GradientButton
import com.zapshare.app.ui.components.LoadingBox
import com.zapshare.app.ui.components.QrImage
import com.zapshare.app.ui.components.RadarPulse
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.SelectCircle
import com.zapshare.app.ui.components.StatPill
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.Routes
import com.zapshare.app.ui.navigation.Selection
import com.zapshare.app.ui.navigation.goPreview
import com.zapshare.app.ui.theme.BrandGradient
import com.zapshare.app.ui.theme.CatPdf
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.ui.theme.Navy
import com.zapshare.app.util.FileUtils
import com.zapshare.app.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/** Survives screen changes (connect keeps running while we navigate to the transfer screen). */
internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Connect to a host and immediately send whatever is selected. */
fun connectAndSend(info: HostInfo) {
    val toSend = Selection.snapshot()
    appScope.launch {
        TransferManager.connect(info)
        if (TransferManager.session.value.error == null && toSend.isNotEmpty()) {
            TransferManager.send(toSend)
            Selection.clear()
        }
    }
}

fun connectNearbyAndSend(d: NearbyDevice) =
    connectAndSend(HostInfo(d.name, 0, d.ip, emptyList(), d.port, null, null, 0, false))

/** Returns a function that opens the camera QR scanner and connects to the scanned device. */
@Composable
fun rememberQrConnect(nav: NavController): () -> Unit {
    val ctx = LocalContext.current
    val scanner = rememberLauncherForActivityResult(ScanContract()) { res ->
        val info = HostInfo.parse(res.contents)
        if (res.contents == null) return@rememberLauncherForActivityResult
        if (info == null) {
            Toast.makeText(ctx, "That is not a ZapShare code", Toast.LENGTH_SHORT).show()
        } else if (Premium.blocksSend(Selection.snapshot())) {
            nav.navigate(Routes.PREMIUM)
        } else {
            connectAndSend(info)
            nav.navigate(Routes.TRANSFER)
        }
    }
    val options = remember {
        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan the QR code on the receiver's screen")
            .setBeepEnabled(false).setOrientationLocked(false)
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) scanner.launch(options) else Toast.makeText(ctx, "Camera permission is needed to scan", Toast.LENGTH_SHORT).show()
    }
    return {
        if (Permissions.granted(ctx, Permissions.camera())) scanner.launch(options)
        else perm.launch(android.Manifest.permission.CAMERA)
    }
}

// ================================================================================ SELECT FILES
private val selectTabs = listOf("PHOTOS", "VIDEOS", "APPS", "MUSIC", "FILES")

@Composable
fun SelectFilesScreen(nav: NavController, mode: String) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var permTick by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val hasMedia = remember(permTick) { Permissions.hasMedia(ctx) }
    val count = Selection.items.size
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        Selection.setAll(uris.map { FileUtils.queryMeta(ctx, it) }, true)
    }
    ZapScaffold(
        stringResource(R.string.select_files), onBack = { nav.popBackStack() },
        actions = { TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Any file") } },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.files_selected, count), style = MaterialTheme.typography.titleSmall)
                        Text(FileUtils.formatSize(Selection.totalSize), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GradientButton(if (mode == "pc") "ADD" else stringResource(R.string.next), enabled = count > 0, modifier = Modifier.width(150.dp), onClick = {
                        if (Premium.blocksSend(Selection.items.values)) nav.navigate(Routes.PREMIUM)
                        else when (mode) {
                            "more" -> { TransferManager.send(Selection.snapshot()); Selection.clear(); nav.popBackStack() }
                            "pc" -> { PcShare.addShared(Selection.snapshot()); Selection.clear(); nav.popBackStack() }
                            else -> nav.navigate(Routes.SEND)
                        }
                    })
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
                selectTabs.forEachIndexed { i, t ->
                    Tab(tab == i, { tab = i }, text = { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) })
                }
            }
            if (!hasMedia && tab != 2) {
                EmptyState(Icons.Filled.PhotoLibrary, "Allow access to your media to pick files") {
                    GradientButton("Allow access", { permLauncher.launch(Permissions.media()) })
                }
            } else when (tab) {
                0 -> MediaGrid(ctx, FileCategory.IMAGE)
                1 -> MediaGrid(ctx, FileCategory.VIDEO)
                2 -> AppsGrid()
                3 -> MediaList(FileCategory.AUDIO)
                else -> MediaList(FileCategory.DOCUMENT, onBrowse = { picker.launch(arrayOf("*/*")) })
            }
        }
    }
}

private fun dayLabel(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(c, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(c, today)) return "Yesterday"
    return java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(c.time)
}

@Composable
private fun MediaGrid(ctx: android.content.Context, cat: FileCategory) {
    val files by produceState<List<MediaFile>?>(null, cat) {
        value = if (cat == FileCategory.IMAGE) MediaRepository.images(ctx) else MediaRepository.videos(ctx)
    }
    val list = files ?: run { LoadingBox(); return }
    if (list.isEmpty()) { EmptyState(Icons.Filled.PhotoLibrary, "Nothing found"); return }
    val groups = remember(list) { list.groupBy { dayLabel(it.dateModified) } }
    LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { (label, group) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "h_$label") {
                val shares = group.map { it.toShareItem() }
                val all = shares.all { Selection.isSelected(it) }
                Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp, 8.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$label (${group.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    SelectCircle(all, Modifier.clickable { Selection.setAll(shares, !all) })
                }
            }
            items(group, key = { it.uri.toString() }) { f ->
                val s = f.toShareItem()
                val sel = Selection.isSelected(s)
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { Selection.toggle(s) }) {
                    FileThumb(f.uri, f.mime, f.name, Modifier.fillMaxSize(), corner = 10.dp)
                    if (sel) Box(Modifier.fillMaxSize().background(Cyan3.copy(alpha = 0.25f)))
                    SelectCircle(sel, Modifier.align(Alignment.TopEnd).padding(6.dp), onDark = true)
                    if (f.duration > 0) Text(FileUtils.formatDuration(f.duration), color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0x88000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun AppsGrid() {
    val ctx = LocalContext.current
    val apps by produceState<List<AppInfo>?>(null) { value = MediaRepository.apps(ctx) }
    val list = apps ?: run { LoadingBox(); return }
    LazyVerticalGrid(GridCells.Fixed(4), contentPadding = PaddingValues(8.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Installed (${list.size}) — received apps can be installed on the other phone",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp))
        }
        items(list, key = { it.packageName }) { a ->
            val s = a.toShareItem()
            val sel = Selection.isSelected(s)
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { Selection.toggle(s) }
                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    AppIcon(a.packageName, Modifier.size(52.dp))
                    if (sel) Icon(Icons.Filled.CheckCircle, null, tint = Cyan3,
                        modifier = Modifier.align(Alignment.TopEnd).size(18.dp).background(Color.White, CircleShape))
                }
                Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                Text(FileUtils.formatSize(a.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MediaList(cat: FileCategory, onBrowse: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val files by produceState<List<MediaFile>?>(null, cat) { value = MediaRepository.byCategory(ctx, cat) }
    val list = files ?: run { LoadingBox(); return }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        if (onBrowse != null) item {
            OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Pick any file type from storage")
            }
        }
        if (list.isEmpty()) item { EmptyState(Icons.Filled.FolderOpen, "Nothing found here") }
        items(list, key = { it.uri.toString() }) { f ->
            val s = f.toShareItem()
            FileRow(f.uri, f.name, f.mime, FileUtils.formatSize(f.size) + (f.artist?.let { " • $it" } ?: ""),
                selected = Selection.isSelected(s)) { Selection.toggle(s) }
        }
    }
}

@Composable
fun FileRow(
    uri: Uri, name: String, mime: String, subtitle: String, selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null, thumb: Boolean = true, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(
                if (onLongClick != null) Modifier.combinedClickableCompat(onClick, onLongClick) else Modifier.clickable(onClick = onClick)
            )
            .background(if (selected == true) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f) else Color.Transparent)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (thumb) FileThumb(uri, mime, name, Modifier.size(48.dp))
        else {
            val (icon, color) = categoryStyle(FileUtils.categoryOf(mime, name), name)
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (selected != null) SelectCircle(selected)
    }
}

fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

// ================================================================================ SEND (connect)
@Composable
fun SendConnectScreen(nav: NavController) {
    val scan = rememberQrConnect(nav)
    val session by TransferManager.session.collectAsState()
    val nearby by remember { TransferManager.nsd.discover() }.collectAsState(initial = emptyList())
    val avatar by Prefs.avatarFlow.collectAsState()
    var manual by remember { mutableStateOf(false) }
    val picked = Selection.snapshot()
    // Share sheet and file screens open Send directly, so send the user to Premium here too.
    LaunchedEffect(Unit) {
        if (Premium.blocksSend(picked)) {
            nav.navigate(Routes.PREMIUM) { popUpTo(Routes.SEND) { inclusive = true } }
        }
    }

    ZapScaffold("Send", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            SectionCard {
                Text("${picked.size} file(s) • ${FileUtils.formatSize(Selection.totalSize)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(picked.take(30)) { FileThumb(it.uri, it.mime, it.name, Modifier.size(52.dp)) }
                }
            }
            Spacer(Modifier.height(20.dp))
            RadarPulse(Modifier.size(220.dp)) { AvatarBubble(avatar, 76.dp, ring = true) }
            Text("Ask your friend to tap Receive, then scan the QR code on their screen",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(8.dp))
            if (session.connected) {
                GradientButton("Send to ${session.peers.joinToString { it.name }}", {
                    if (Premium.blocksSend(picked)) nav.navigate(Routes.PREMIUM)
                    else {
                        TransferManager.send(picked); Selection.clear(); nav.navigate(Routes.TRANSFER) { popUpTo(Routes.HOME) }
                    }
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            GradientButton(stringResource(R.string.scan_qr), scan, Modifier.fillMaxWidth(), icon = Icons.Filled.QrCodeScanner)
            Spacer(Modifier.height(16.dp))
            AdBanner(onRemoveAds = { nav.navigate(Routes.PREMIUM) })
            Spacer(Modifier.height(20.dp))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Wifi, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Nearby on the same Wi-Fi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                if (nearby.isEmpty()) Text(
                    "Searching… (the receiver must choose “Same Wi-Fi” mode)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
                nearby.forEach { d ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        if (Premium.blocksSend(picked)) nav.navigate(Routes.PREMIUM)
                        else { connectNearbyAndSend(d); nav.navigate(Routes.TRANSFER) }
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarBubble(d.name.hashCode(), 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(d.name); Text(d.ip, style = MaterialTheme.typography.bodySmall) }
                        Text("SEND", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = { manual = true }) { Text("Enter IP address manually") }
            }
        }
    }
    if (manual) {
        var ip by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("Connect by IP") },
            text = { OutlinedTextField(ip, { ip = it.trim() }, singleLine = true, label = { Text("e.g. 192.168.1.23") }) },
            confirmButton = {
                TextButton(onClick = {
                    manual = false
                    if (Premium.blocksSend(picked)) nav.navigate(Routes.PREMIUM)
                    else { connectNearbyAndSend(NearbyDevice(ip, ip, NetUtils.TRANSFER_PORT)); nav.navigate(Routes.TRANSFER) }
                }) { Text("Connect") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancel") } }
        )
    }
}

// ================================================================================ RECEIVE
@Composable
fun ReceiveScreen(nav: NavController) = HostScreen(nav, group = false)

@Composable
fun GroupScreen(nav: NavController) {
    val session by TransferManager.session.collectAsState()
    var hosting by remember { mutableStateOf(session.active && session.group && session.role == Role.HOST) }
    val scan = rememberQrConnect(nav)
    if (hosting) { HostScreen(nav, group = true); return }
    ZapScaffold("Group share", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Share with up to 8 phones at once. One phone creates the group, the others scan its QR code. " +
                "Every file sent in the group reaches every member.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionCard(Modifier.clickable { hosting = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Groups, null, tint = Navy, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column { Text("Create a group", style = MaterialTheme.typography.titleMedium); Text("Works offline — your phone becomes the hotspot", style = MaterialTheme.typography.bodySmall) }
                }
            }
            SectionCard(Modifier.clickable { scan() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column { Text("Join a group", style = MaterialTheme.typography.titleMedium); Text("Scan the group owner's QR code", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun HostScreen(nav: NavController, group: Boolean) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val session by TransferManager.session.collectAsState()
    var offline by remember { mutableStateOf(TransferManager.session.value.hostInfo?.let { it.ssid != null } ?: true) }
    var permTick by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val ready = remember(permTick) { !offline || Permissions.granted(ctx, Permissions.nearby()) }
    var handedOff by remember { mutableStateOf(false) }

    LaunchedEffect(offline, ready) {
        if (!ready) { permLauncher.launch(Permissions.nearby()); return@LaunchedEffect }
        if (offline && !Permissions.locationOn(ctx)) {
            Toast.makeText(ctx, "Turn on Location to create the offline hotspot", Toast.LENGTH_LONG).show()
            ctx.startActivity(Permissions.locationSettings())
        }
        val s = TransferManager.session.value
        if (!(s.active && s.role == Role.HOST && s.group == group && (s.hostInfo?.ssid != null) == offline)) {
            TransferManager.startHost(useHotspot = offline, group = group)
        }
    }
    // In 1:1 mode jump to the transfer screen as soon as a sender connects.
    LaunchedEffect(session.connected) {
        if (session.connected && !group) {
            handedOff = true
            nav.navigate(Routes.TRANSFER) { popUpTo(Routes.RECEIVE) { inclusive = true } }
        }
    }
    DisposableEffect(Unit) {
        if (Prefs.keepScreenOn) view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            if (!handedOff && !(group && TransferManager.session.value.connected)) TransferManager.stop()
        }
    }

    ZapScaffold(if (group) "Group share" else "Receive", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(offline, { offline = true }, label = { Text("Offline (no internet)") },
                    leadingIcon = { Icon(Icons.Filled.WifiTethering, null, Modifier.size(18.dp)) })
                FilterChip(!offline, { offline = false }, label = { Text("Same Wi-Fi") },
                    leadingIcon = { Icon(Icons.Filled.Wifi, null, Modifier.size(18.dp)) })
            }
            Spacer(Modifier.height(16.dp))
            val info = session.hostInfo
            when {
                session.error != null -> ErrorCard(session.error!!) {
                    TransferManager.clearError(); appScope.launch { TransferManager.startHost(offline, group) }
                }
                session.busy || info == null -> {
                    RadarPulse(Modifier.size(240.dp)) { AvatarBubble(Prefs.avatar, 80.dp, ring = true) }
                    Text(session.status.ifEmpty { "Preparing…" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    SectionCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarBubble(info.avatar, 36.dp); Spacer(Modifier.width(8.dp))
                                Text(info.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                            QrImage(info.toQr(), Modifier.size(250.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (group) "Members: open ZapShare → Group share → Join, and scan this code"
                                else "On the sending phone tap Send → pick files → Scan QR code",
                                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
                            )
                            if (info.ssid != null) {
                                Spacer(Modifier.height(10.dp))
                                StatPill("Hotspot: ${info.ssid}")
                            } else {
                                Spacer(Modifier.height(10.dp))
                                StatPill("${info.ip}:${info.port}")
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(session.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    AdBanner(onRemoveAds = { nav.navigate(Routes.PREMIUM) })
                    if (group) {
                        Spacer(Modifier.height(12.dp))
                        SectionCard {
                            Text("Members (${session.peers.size})", style = MaterialTheme.typography.titleMedium)
                            session.peers.forEach { p ->
                                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AvatarBubble(p.avatar, 32.dp); Spacer(Modifier.width(10.dp)); Text(p.name)
                                }
                            }
                            if (session.peers.isEmpty()) Text("Waiting for members…", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GradientButton("Share files", { handedOff = true; nav.navigate(Routes.select("more")) },
                                Modifier.weight(1f), enabled = session.connected)
                            OutlinedButton(onClick = { handedOff = true; nav.navigate(Routes.TRANSFER) }, modifier = Modifier.weight(1f).height(50.dp)) {
                                Text("Transfers")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(msg: String, onRetry: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, null, tint = CatPdf)
            Spacer(Modifier.width(10.dp))
            Text(msg, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        GradientButton("Try again", onRetry, Modifier.fillMaxWidth())
    }
}

// ================================================================================ TRANSFER
@Composable
fun TransferScreen(nav: NavController) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val session by TransferManager.session.collectAsState()
    val transferItems by TransferManager.items.collectAsState()
    var askDisconnect by remember { mutableStateOf(false) }
    var transferAdShown by remember { mutableStateOf(false) }
    LaunchedEffect(transferItems.size) {
        if (!transferAdShown && transferItems.isNotEmpty()) {
            transferAdShown = true
            ctx.findActivity()?.let { Ads.show(it) }
        }
    }
    val leave: () -> Unit = {
        TransferManager.stop()
        nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
    }
    BackHandler(enabled = session.active) { askDisconnect = true }
    DisposableEffect(Unit) {
        if (Prefs.keepScreenOn) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    ZapScaffold(
        "Transfer", onBack = { if (session.active) askDisconnect = true else nav.popBackStack() },
        bottomBar = {
            if (session.connected) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { askDisconnect = true }, modifier = Modifier.weight(1f).height(50.dp)) { Text("Disconnect") }
                    GradientButton("Send more", { nav.navigate(Routes.select("more")) }, Modifier.weight(1f), icon = Icons.Filled.Add)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // ---- header
            Box(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BrandGradient).padding(18.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBubble(Prefs.avatar, 44.dp, ring = true)
                        Text("  ⇄  ", color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        if (session.peers.isEmpty()) Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .4f)))
                        session.peers.take(4).forEach { AvatarBubble(it.avatar, 44.dp, ring = true); Spacer(Modifier.width(4.dp)) }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(FileUtils.formatSpeed(session.speed), color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("speed", color = Navy.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val statusText = when {
                        session.busy -> session.status
                        session.error != null -> session.error!!
                        session.disconnected -> "Disconnected"
                        session.connected -> "Connected to ${session.peers.joinToString { it.name }}"
                        !session.active -> "Not connected"
                        else -> session.status
                    }
                    Text(statusText, color = Navy, style = MaterialTheme.typography.bodyMedium)
                    val active = transferItems.filter { it.status != ItemStatus.FAILED }
                    val total = active.sumOf { it.size }.coerceAtLeast(1)
                    val done = active.sumOf { if (it.status == ItemStatus.DONE) it.size else it.transferred }
                    if (transferItems.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Navy, trackColor = Color.White.copy(alpha = .45f))
                        Text("${FileUtils.formatSize(done)} / ${FileUtils.formatSize(total)} • ${transferItems.count { it.status == ItemStatus.DONE }}/${transferItems.size} files",
                            color = Navy, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            if (session.error != null && !session.connected) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ErrorCard(session.error!!) { TransferManager.clearError(); nav.popBackStack() }
                }
            } else if (session.busy) {
                RadarPulse(Modifier.fillMaxWidth().height(260.dp)) { AvatarBubble(Prefs.avatar, 72.dp, ring = true) }
            }
            if (transferItems.isEmpty() && session.connected) {
                EmptyState(Icons.Filled.Schedule, "Connected! Waiting for files…\nOr tap “Send more” to share yours.")
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(transferItems.reversed(), key = { it.id }) { item -> TransferRow(item, nav) }
            }
        }
    }
    if (askDisconnect) AlertDialog(
        onDismissRequest = { askDisconnect = false },
        title = { Text("Disconnect?") },
        text = { Text("Ongoing transfers will stop.") },
        confirmButton = { TextButton(onClick = { askDisconnect = false; leave() }) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { askDisconnect = false }) { Text("Stay") } }
    )
}

@Composable
private fun TransferRow(item: TransferItem, nav: NavController) {
    val ctx = LocalContext.current
    SectionCard(padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = item.resultUri != null) {
                item.resultUri?.let { nav.goPreview(it, item.name, item.mime) }
            }) {
            FileThumb(item.resultUri ?: Uri.EMPTY, item.mime, item.name, Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dir = if (item.direction == Direction.SEND) "to ${item.peerName}" else "from ${item.peerName}"
                val st = when (item.status) {
                    ItemStatus.WAITING -> "Waiting"
                    ItemStatus.ACTIVE -> "${FileUtils.formatSize(item.transferred)} / ${FileUtils.formatSize(item.size)}"
                    ItemStatus.DONE -> FileUtils.formatSize(item.size)
                    ItemStatus.FAILED -> item.error ?: "Failed"
                }
                Text("$st • $dir", style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == ItemStatus.FAILED) CatPdf else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (item.status == ItemStatus.ACTIVE || item.status == ItemStatus.WAITING) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth(), color = Cyan3,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                item.status == ItemStatus.DONE && item.direction == Direction.RECEIVE &&
                    FileUtils.categoryOf(item.mime, item.name) == FileCategory.APK ->
                    TextButton(onClick = {
                        if (Permissions.canInstallApks(ctx)) FileUtils.installApk(ctx, item.resultUri!!)
                        else ctx.startActivity(Permissions.installSourceIntent(ctx))
                    }) { Text("INSTALL") }
                item.status == ItemStatus.DONE -> Icon(Icons.Filled.CheckCircle, "Done", tint = Cyan3)
                item.status == ItemStatus.FAILED -> Icon(Icons.Filled.Close, "Failed", tint = CatPdf)
            }
        }
    }
}

// ================================================================================ PC
private fun openPcPage(ctx: Context, url: String?) {
    if (url.isNullOrBlank()) return
    val opened = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!opened) Toast.makeText(ctx, "Could not open the browser", Toast.LENGTH_SHORT).show()
}

@Composable
fun PcScreen(nav: NavController) {
    val ctx = LocalContext.current
    val st by PcShare.state.collectAsState()
    var starting by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        starting = true; appScope.launch { PcShare.start(ctx, true); starting = false }
    }
    ZapScaffold("Connect to PC", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!st.running) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BrandGradient).padding(20.dp)) {
                    Column {
                        Icon(Icons.Filled.Computer, null, tint = Navy, modifier = Modifier.size(48.dp))
                        Text("No software needed on the PC", color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Your phone runs a tiny private web page. Open it in any browser on Windows, Mac or Linux to send and receive files both ways.",
                            color = Navy)
                    }
                }
                st.error?.let { Text(it, color = CatPdf) }
                if (starting) LoadingBox()
                GradientButton("PC is on the same Wi-Fi", {
                    starting = true; appScope.launch { PcShare.start(ctx, false); starting = false }
                }, Modifier.fillMaxWidth(), icon = Icons.Filled.Wifi, enabled = !starting)
                OutlinedButton(onClick = {
                    if (Permissions.granted(ctx, Permissions.nearby())) { starting = true; appScope.launch { PcShare.start(ctx, true); starting = false } }
                    else permLauncher.launch(Permissions.nearby())
                }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !starting) {
                    Icon(Icons.Filled.WifiTethering, null); Spacer(Modifier.width(8.dp)); Text("No Wi-Fi? Create a hotspot for the PC")
                }
            } else {
                SectionCard {
                    Text("1. Connect the PC", style = MaterialTheme.typography.titleMedium)
                    val h = st.hotspot
                    if (h != null) {
                        Text("On the PC, disconnect from other Wi-Fi and join this one. Windows will say there is no internet. Stay connected anyway.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Network: ${h.ssid}", fontWeight = FontWeight.Bold)
                        Text("Password: ${h.pass}", fontWeight = FontWeight.Bold)
                    } else Text(
                        "On the PC, stay connected to ${st.networkName ?: "the same Wi-Fi as this phone"}. Internet is not required.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("2. In the PC browser, type this http address", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            st.url ?: "",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f).clickable { openPcPage(ctx, st.url) }
                        )
                        IconButton(onClick = {
                            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("url", st.url))
                            if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                    }
                    TextButton(onClick = { openPcPage(ctx, st.url) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open in browser")
                    }
                    Text(
                        "Type it in Chrome or Edge on the PC, starting with http://. Do not use https. The page loads with no internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Access code: ${st.token}", style = MaterialTheme.typography.bodySmall)
                }
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shared with PC (${st.shared.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { Selection.clear(); nav.navigate(Routes.select("pc")) }) { Icon(Icons.Filled.Add, null); Text("Add files") }
                    }
                    st.shared.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { FileRow(f.uri, f.name, f.mime, FileUtils.formatSize(f.size)) {} }
                            IconButton(onClick = { PcShare.removeShared(f) }) { Icon(Icons.Filled.Close, "Remove") }
                        }
                    }
                }
                SectionCard {
                    Text("Received from PC (${st.received.size})", style = MaterialTheme.typography.titleMedium)
                    if (st.received.isEmpty()) Text("Drag files onto the web page to send them here.", style = MaterialTheme.typography.bodySmall)
                    st.received.reversed().forEach { f ->
                        FileRow(f.uri, f.name, f.mime, FileUtils.formatSize(f.size)) { nav.goPreview(f.uri, f.name, f.mime) }
                    }
                }
                if (st.log.isNotEmpty()) SectionCard {
                    Text("Activity", style = MaterialTheme.typography.titleMedium)
                    st.log.take(8).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                OutlinedButton(onClick = { PcShare.stop(ctx) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Filled.Link, null); Spacer(Modifier.width(8.dp)); Text("Stop PC connection")
                }
            }
        }
    }
}


