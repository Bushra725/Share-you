@file:OptIn(ExperimentalMaterial3Api::class)

package com.zapshare.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.zapshare.app.BuildConfig
import com.zapshare.app.R
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.HistoryStore
import com.zapshare.app.data.MediaRepository
import com.zapshare.app.data.Prefs
import com.zapshare.app.downloader.VideoDownloader
import com.zapshare.app.premium.AdBanner
import com.zapshare.app.premium.Premium
import com.zapshare.app.ui.components.AvatarBubble
import com.zapshare.app.ui.components.BrandIconBox
import com.zapshare.app.ui.components.EmptyState
import com.zapshare.app.ui.components.FileThumb
import com.zapshare.app.ui.components.GradientButton
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.ToolTile
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.Routes
import com.zapshare.app.ui.navigation.Selection
import com.zapshare.app.ui.navigation.goPreview
import com.zapshare.app.ui.theme.BrandGradient
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.ui.theme.CatCyan
import com.zapshare.app.ui.theme.CatPdf
import com.zapshare.app.ui.theme.CatPhoto
import com.zapshare.app.ui.theme.CatDownload
import com.zapshare.app.ui.theme.CatPink
import com.zapshare.app.ui.theme.CatDocs
import com.zapshare.app.ui.theme.CatSafe
import com.zapshare.app.ui.theme.CatMusic
import com.zapshare.app.ui.theme.CatVideo
import com.zapshare.app.ui.theme.CatApps
import com.zapshare.app.ui.theme.Navy
import com.zapshare.app.util.FileUtils
import com.zapshare.app.util.Permissions
import kotlinx.coroutines.launch

/** Tile model: icon, label, colour, click. */
class ToolItem(val icon: ImageVector, val label: String, val color: Color, val onClick: () -> Unit)

// ================================================================================ HOME
@Composable
fun HomeScreen(nav: NavController) {
    val name by Prefs.name.collectAsState()
    val avatar by Prefs.avatarFlow.collectAsState()
    val history by HistoryStore.entries.collectAsState()
    val premium by Premium.isPremium.collectAsState()
    val scan = rememberQrConnect(nav)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
    ) {
        // ---- Hero header
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(BrandGradient)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBubble(avatar, 44.dp, ring = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text(name, color = Navy.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)).clickable { scan() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.QrCodeScanner, stringResource(R.string.scan_qr), tint = Navy) }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigAction(stringResource(R.string.send), Icons.Filled.Upload) { Selection.clear(); nav.navigate(Routes.select("send")) }
                    BigAction(stringResource(R.string.receive), Icons.Filled.Download) { nav.navigate(Routes.RECEIVE) }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!premium) PremiumBanner { nav.navigate(Routes.PREMIUM) }

            SectionCard {
                Text(stringResource(R.string.quick_tools), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TileGrid(
                    listOf(
                        ToolItem(Icons.Filled.Computer, stringResource(R.string.connect_pc), CatApps) { nav.navigate(Routes.PC) },
                        ToolItem(Icons.Filled.Groups, stringResource(R.string.group_share), CatVideo) { nav.navigate(Routes.GROUP) },
                        ToolItem(Icons.Filled.CloudDownload, stringResource(R.string.downloader), CatMusic) { nav.navigate(Routes.DOWNLOADER) },
                        ToolItem(Icons.Filled.Lock, stringResource(R.string.safebox), CatSafe) { nav.navigate(Routes.SAFEBOX) },
                        ToolItem(Icons.Filled.Transform, stringResource(R.string.converter), CatDocs) { nav.navigate(Routes.CONVERTER) },
                        ToolItem(Icons.Filled.CleaningServices, stringResource(R.string.clean), CatPink) { nav.navigate(Routes.LARGE) },
                        ToolItem(Icons.Filled.Apps, stringResource(R.string.apps), CatDownload) { nav.navigate(Routes.APPS) },
                    )
                )
            }

            StorageCard { nav.navigate(Routes.BROWSER) }

            AdBanner(onRemoveAds = { nav.navigate(Routes.PREMIUM) })

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recently_received), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate(Routes.HISTORY) }) { Text(stringResource(R.string.history)) }
                }
                val recent = history.filter { !it.sent }.take(5)
                if (recent.isEmpty()) {
                    Text(stringResource(R.string.recent_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp))
                } else recent.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = e.uri != null) { e.uri?.let { nav.goPreview(Uri.parse(it), e.name, e.mime) } }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileThumb(Uri.parse(e.uri ?: ""), e.mime, e.name, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${FileUtils.formatSize(e.size)} • ${e.peer}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun PremiumBanner(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF0B2530), Color(0xFF004E66))))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFFFC83D)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = Navy, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.go_premium), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(stringResource(R.string.premium_banner_sub), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
    }
}

@Composable
private fun BigAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(92.dp).clip(CircleShape).background(Color.White).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = Navy, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun TileGrid(tiles: List<ToolItem>, columns: Int = 4) {
    tiles.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { t -> ToolTile(t.icon, t.label, t.color, Modifier.weight(1f), onClick = t.onClick) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun StorageCard(onClick: () -> Unit) {
    val st = remember { MediaRepository.storage() }
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandIconBox(Icons.Filled.Storage, 44.dp, 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.internal_storage), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.used_of, FileUtils.formatSize(st.used), FileUtils.formatSize(st.total)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
        Spacer(Modifier.height(10.dp))
        val frac = if (st.total > 0) st.used.toFloat() / st.total else 0f
        LinearProgressIndicator(
            progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (frac > 0.85f) Color(0xFFEF4444) else Cyan3, trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(stringResource(R.string.free_space, FileUtils.formatSize(st.free)), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

// ================================================================================ TOOLS
@Composable
fun ToolsScreen(nav: NavController) {
    val premium by Premium.isPremium.collectAsState()
    ZapScaffold(stringResource(R.string.tools)) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!premium) PremiumBanner { nav.navigate(Routes.PREMIUM) }
            ToolSection(stringResource(R.string.sec_transfer), listOf(
                ToolItem(Icons.Filled.Upload, stringResource(R.string.send), CatApps) { Selection.clear(); nav.navigate(Routes.select("send")) },
                ToolItem(Icons.Filled.Download, stringResource(R.string.receive), CatPhoto) { nav.navigate(Routes.RECEIVE) },
                ToolItem(Icons.Filled.Groups, stringResource(R.string.group_share), CatVideo) { nav.navigate(Routes.GROUP) },
                ToolItem(Icons.Filled.Computer, stringResource(R.string.connect_pc), CatApps) { nav.navigate(Routes.PC) },
                ToolItem(Icons.Filled.History, stringResource(R.string.history), CatDocs) { nav.navigate(Routes.HISTORY) },
            ))
            ToolSection(stringResource(R.string.sec_download), listOf(
                ToolItem(Icons.Filled.CloudDownload, stringResource(R.string.downloader), CatMusic) { nav.navigate(Routes.DOWNLOADER) },
            ))
            ToolSection(stringResource(R.string.sec_media), listOf(
                ToolItem(Icons.Filled.MusicNote, stringResource(R.string.music), CatPink) { nav.navigate(Routes.PLAYER) },
                ToolItem(Icons.Filled.OndemandVideo, stringResource(R.string.videos), CatVideo) { nav.navigate(Routes.category(FileCategory.VIDEO)) },
                ToolItem(Icons.Filled.Transform, stringResource(R.string.converter), CatDocs) { nav.navigate(Routes.CONVERTER) },
            ))
            ToolSection(stringResource(R.string.nav_files), listOf(
                ToolItem(Icons.Filled.FolderOpen, stringResource(R.string.browse), CatCyan) { nav.navigate(Routes.BROWSER) },
                ToolItem(Icons.Filled.CleaningServices, stringResource(R.string.large_files), CatPdf) { nav.navigate(Routes.LARGE) },
                ToolItem(Icons.Filled.Lock, stringResource(R.string.safebox), CatSafe) { nav.navigate(Routes.SAFEBOX) },
                ToolItem(Icons.Filled.Apps, stringResource(R.string.apps), CatDownload) { nav.navigate(Routes.APPS) },
            ))
        }
    }
}

@Composable
private fun ToolSection(title: String, tiles: List<ToolItem>) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        TileGrid(tiles)
    }
}

// ================================================================================ ME
@Composable
fun MeScreen(nav: NavController) {
    val name by Prefs.name.collectAsState()
    val avatar by Prefs.avatarFlow.collectAsState()
    val history by HistoryStore.entries.collectAsState()
    val premium by Premium.isPremium.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var engineMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().background(BrandGradient).windowInsetsPadding(WindowInsets.statusBars).padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { Prefs.avatar = (avatar + 1) % Prefs.avatars.size }) { AvatarBubble(avatar, 72.dp, ring = true) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (premium) "⭐ Premium" else "Free", color = Navy.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Filled.Edit, "Edit name", tint = Navy, modifier = Modifier.clickable { editing = true }.padding(8.dp))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!premium) PremiumBanner { nav.navigate(Routes.PREMIUM) }
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(stringResource(R.string.sent), FileUtils.formatSize(history.filter { it.sent }.sumOf { it.size }),
                        stringResource(R.string.files_count, history.count { it.sent }))
                    Stat(stringResource(R.string.received), FileUtils.formatSize(history.filter { !it.sent }.sumOf { it.size }),
                        stringResource(R.string.files_count, history.count { !it.sent }))
                }
            }
            SectionCard(padding = 4.dp) {
                MenuRow(Icons.Filled.WorkspacePremium, stringResource(R.string.premium_title),
                    if (premium) stringResource(R.string.you_are_premium) else stringResource(R.string.premium_banner_sub)) { nav.navigate(Routes.PREMIUM) }
                MenuRow(Icons.Filled.History, stringResource(R.string.transfer_history)) { nav.navigate(Routes.HISTORY) }
                MenuRow(Icons.Filled.Lock, stringResource(R.string.safebox)) { nav.navigate(Routes.SAFEBOX) }
                MenuRow(Icons.Filled.Settings, stringResource(R.string.settings)) { nav.navigate(Routes.SETTINGS) }
                MenuRow(Icons.Filled.Update, stringResource(R.string.update_engine), engineMsg) {
                    engineMsg = "…"
                    scope.launch { engineMsg = VideoDownloader.updateEngine(ctx) }
                }
                MenuRow(Icons.Filled.Info, stringResource(R.string.about), "ZapShare ${BuildConfig.VERSION_NAME}") {}
            }
        }
    }
    if (editing) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Device name") },
            text = { OutlinedTextField(text, { text = it.take(24) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (text.isNotBlank()) Prefs.deviceName = text.trim(); editing = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Menu / settings row with a large brand-blue icon tile. */
@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        leadingContent = { BrandIconBox(icon, size = 44.dp, iconSize = 26.dp) },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { s -> @Composable { Text(s) } },
        trailingContent = trailing ?: @Composable { Icon(Icons.Filled.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ================================================================================ SETTINGS
/** In-app languages: tag → native name. "" = follow the phone. */
val appLanguages = listOf(
    "" to "System default",
    "en" to "English",
    "ur" to "اردو (Urdu)",
    "hi" to "हिन्दी (Hindi)",
    "ar" to "العربية (Arabic)",
    "es" to "Español",
    "fr" to "Français",
    "pt" to "Português",
    "in" to "Bahasa Indonesia",
    "tr" to "Türkçe",
)

fun currentLanguageTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    .substringBefore(',').substringBefore('-')
    .let { if (it == "id") "in" else it }   // Android reports Indonesian as "id", resources use "in"

fun setAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(
        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
    )
}

@Composable
fun SettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val theme by Prefs.theme.collectAsState()
    val premium by Premium.isPremium.collectAsState()
    var relay by remember { mutableStateOf(Prefs.groupRelay) }
    var screenOn by remember { mutableStateOf(Prefs.keepScreenOn) }
    var confirmClear by remember { mutableStateOf(false) }
    var langDialog by remember { mutableStateOf(false) }
    var debugPremium by remember { mutableStateOf(Prefs.premiumDebug) }
    val langName = appLanguages.firstOrNull { it.first == currentLanguageTag() }?.second ?: appLanguages[0].second

    ZapScaffold(stringResource(R.string.settings), onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!premium) PremiumBanner { nav.navigate(Routes.PREMIUM) }
            SectionCard(padding = 4.dp) {
                SectionTitle(stringResource(R.string.appearance))
                MenuRow(Icons.Filled.Language, stringResource(R.string.language), langName) { langDialog = true }
                val themes = listOf(
                    Triple(0, stringResource(R.string.follow_system), Icons.Filled.PhoneAndroid),
                    Triple(1, stringResource(R.string.light), Icons.Filled.LightMode),
                    Triple(2, stringResource(R.string.dark), Icons.Filled.DarkMode),
                )
                themes.forEach { (i, label, icon) ->
                    MenuRow(icon, label, trailing = {
                        if (theme == i) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }) { Prefs.themeMode = i }
                }
            }
            SectionCard(padding = 4.dp) {
                SectionTitle(stringResource(R.string.sec_transfer))
                MenuRow(Icons.Filled.Groups, stringResource(R.string.group_relay), stringResource(R.string.group_relay_sub),
                    trailing = { Switch(relay, { relay = it; Prefs.groupRelay = it }) }) { relay = !relay; Prefs.groupRelay = relay }
                MenuRow(Icons.Filled.WifiTethering, stringResource(R.string.keep_screen_on), null,
                    trailing = { Switch(screenOn, { screenOn = it; Prefs.keepScreenOn = it }) }) { screenOn = !screenOn; Prefs.keepScreenOn = screenOn }
                MenuRow(Icons.Filled.FolderOpen, stringResource(R.string.save_location), "Download/ZapShare") {}
            }
            SectionCard(padding = 4.dp) {
                SectionTitle(stringResource(R.string.permissions))
                MenuRow(Icons.Filled.Storage, stringResource(R.string.all_files_access), if (Permissions.hasAllFiles()) "✓" else null) {
                    ctx.startActivity(Permissions.allFilesIntent(ctx))
                }
                MenuRow(Icons.Filled.Apps, stringResource(R.string.install_apps), if (Permissions.canInstallApks(ctx)) "✓" else null) {
                    ctx.startActivity(Permissions.installSourceIntent(ctx))
                }
                MenuRow(Icons.Filled.Settings, stringResource(R.string.app_permissions), null) { ctx.startActivity(Permissions.appSettings(ctx)) }
            }
            SectionCard(padding = 4.dp) {
                MenuRow(Icons.Filled.History, stringResource(R.string.clear_history), null) { confirmClear = true }
                if (BuildConfig.DEBUG) {
                    MenuRow(Icons.Filled.BugReport, "Debug: unlock Premium", "Only in debug builds, for testing",
                        trailing = { Switch(debugPremium, { debugPremium = it; Premium.setDebugPremium(it) }) }) {
                        debugPremium = !debugPremium; Premium.setDebugPremium(debugPremium)
                    }
                }
            }
        }
    }
    if (langDialog) AlertDialog(
        onDismissRequest = { langDialog = false },
        title = { Text(stringResource(R.string.language)) },
        text = {
            LazyColumn {
                items(appLanguages) { (tag, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            langDialog = false; setAppLanguage(tag)
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, Modifier.weight(1f))
                        if (tag == currentLanguageTag()) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { langDialog = false }) { Text(stringResource(R.string.close)) } }
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_history)) },
        text = { Text("Only the list is cleared. Your received files stay on the device.") },
        confirmButton = { TextButton(onClick = { HistoryStore.clear(); confirmClear = false }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))

// ================================================================================ HISTORY
@Composable
fun HistoryScreen(nav: NavController) {
    val all by HistoryStore.entries.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    ZapScaffold(stringResource(R.string.history), onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad)) {
            SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.received)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.sent)) })
            }
            val list = all.filter { it.sent == (tab == 1) }
            if (list.isEmpty()) EmptyState(Icons.Filled.History, "—")
            else LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(list) { e ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = e.uri != null) { e.uri?.let { nav.goPreview(Uri.parse(it), e.name, e.mime) } }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileThumb(Uri.parse(e.uri ?: ""), e.mime, e.name, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${FileUtils.formatSize(e.size)} • ${e.peer} • ${FileUtils.formatDate(e.time)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ================================================================================ PERMISSIONS
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val media = remember(tick) { Permissions.hasMedia(ctx) }
    val nearby = remember(tick) { Permissions.granted(ctx, Permissions.nearby()) }
    val camera = remember(tick) { Permissions.granted(ctx, Permissions.camera()) }
    val notif = remember(tick) { Permissions.granted(ctx, Permissions.notifications()) }
    val allFiles = remember(tick) { Permissions.hasAllFiles() }
    ZapScaffold("Preparations") { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.WifiTethering, null, tint = Navy, modifier = Modifier.size(56.dp))
                    Text("A few permissions to share at full speed", color = Navy, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermRow(Icons.Filled.PhotoLibrary, "Photos, videos & music", "Pick files to send and show what you received",
                    media) { launcher.launch(Permissions.media()) }
                PermRow(Icons.Filled.WifiTethering, "Nearby devices", "Create the offline hotspot and find friends faster",
                    nearby) { launcher.launch(Permissions.nearby()) }
                PermRow(Icons.Filled.CameraAlt, "Camera", "Scan the QR code to connect",
                    camera) { launcher.launch(Permissions.camera()) }
                if (Permissions.notifications().isNotEmpty())
                    PermRow(Icons.Filled.Notifications, "Notifications", "Show transfer progress in the background",
                        notif) { launcher.launch(Permissions.notifications()) }
                PermRow(Icons.Filled.Storage, "All files access (optional)", "Full file manager and documents",
                    allFiles) { ctx.startActivity(Permissions.allFilesIntent(ctx)) }
            }
            GradientButton(stringResource(R.string.continue_btn), onDone, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandIconBox(icon, 42.dp, 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            else TextButton(onClick = onClick) { Text("ALLOW") }
        }
    }
}
