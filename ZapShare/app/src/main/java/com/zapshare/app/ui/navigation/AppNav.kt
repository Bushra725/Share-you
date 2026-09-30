@file:OptIn(ExperimentalLayoutApi::class)

package com.zapshare.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import com.zapshare.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.zapshare.app.IncomingShare
import com.zapshare.app.IntentBus
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.Prefs
import com.zapshare.app.data.ShareItem
import com.zapshare.app.player.MusicController
import com.zapshare.app.premium.Ads
import com.zapshare.app.ui.screens.findActivity
import com.zapshare.app.ui.screens.AppsScreen
import com.zapshare.app.ui.screens.BrowserScreen
import com.zapshare.app.ui.screens.CategoryScreen
import com.zapshare.app.ui.screens.ConverterScreen
import com.zapshare.app.ui.screens.DownloaderScreen
import com.zapshare.app.ui.screens.DownloaderState
import com.zapshare.app.ui.screens.FilesScreen
import com.zapshare.app.ui.screens.GroupScreen
import com.zapshare.app.ui.screens.HistoryScreen
import com.zapshare.app.ui.screens.HomeScreen
import com.zapshare.app.ui.screens.LargeFilesScreen
import com.zapshare.app.ui.screens.MeScreen
import com.zapshare.app.ui.screens.NowPlayingScreen
import com.zapshare.app.ui.screens.PcScreen
import com.zapshare.app.ui.screens.PermissionsScreen
import com.zapshare.app.ui.screens.PlayerScreen
import com.zapshare.app.ui.screens.PreviewScreen
import com.zapshare.app.ui.screens.PremiumScreen
import com.zapshare.app.ui.screens.ReceiveScreen
import com.zapshare.app.ui.screens.SafeboxScreen
import com.zapshare.app.ui.screens.SearchScreen
import com.zapshare.app.ui.screens.SelectFilesScreen
import com.zapshare.app.ui.screens.SendConnectScreen
import com.zapshare.app.ui.screens.SettingsScreen
import com.zapshare.app.ui.screens.ToolsScreen
import com.zapshare.app.ui.screens.TransferScreen
import com.zapshare.app.ui.screens.VideoPlayerScreen
import com.zapshare.app.ui.theme.BrandGradient
import com.zapshare.app.ui.theme.Cyan3

object Routes {
    const val HOME = "home"
    const val FILES = "files"
    const val PLAYER = "player"
    const val TOOLS = "tools"
    const val ME = "me"
    const val PERMS = "perms"
    const val SELECT = "select/{mode}"
    const val SEND = "send"
    const val RECEIVE = "receive"
    const val TRANSFER = "transfer"
    const val GROUP = "group"
    const val PC = "pc"
    const val HISTORY = "history"
    const val CATEGORY = "category/{cat}"
    const val BROWSER = "browser"
    const val LARGE = "large"
    const val SEARCH = "search"
    const val APPS = "apps"
    const val PREVIEW = "preview?uri={uri}&name={name}&mime={mime}"
    const val NOW_PLAYING = "now_playing"
    const val VIDEO = "video?uri={uri}&title={title}"
    const val DOWNLOADER = "downloader"
    const val CONVERTER = "converter"
    const val SAFEBOX = "safebox"
    const val SETTINGS = "settings"
    const val PREMIUM = "premium"

    fun select(mode: String) = "select/$mode"
    fun category(c: FileCategory) = "category/${c.name}"
    fun preview(uri: Uri, name: String, mime: String) =
        "preview?uri=${Uri.encode(uri.toString())}&name=${Uri.encode(name)}&mime=${Uri.encode(mime)}"
    fun video(uri: Uri, title: String) = "video?uri=${Uri.encode(uri.toString())}&title=${Uri.encode(title)}"
}

/** Files picked for sending — shared by the selector, send screen and PC share. */
object Selection {
    val items = mutableStateMapOf<String, ShareItem>()
    fun toggle(i: ShareItem) { if (items.containsKey(i.key)) items.remove(i.key) else items[i.key] = i }
    fun isSelected(i: ShareItem) = items.containsKey(i.key)
    fun setAll(list: List<ShareItem>, selected: Boolean) {
        if (selected) list.forEach { items[it.key] = it } else list.forEach { items.remove(it.key) }
    }
    fun replace(list: List<ShareItem>) { items.clear(); list.forEach { items[it.key] = it } }
    fun snapshot(): List<ShareItem> = items.values.toList()
    fun clear() = items.clear()
    val totalSize get() = items.values.sumOf { it.size.coerceAtLeast(0) }
}

private data class Tab(val route: String, val label: Int, val filled: ImageVector, val outlined: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    Tab(Routes.FILES, R.string.nav_files, Icons.Filled.Folder, Icons.Outlined.Folder),
    Tab(Routes.PLAYER, R.string.nav_player, Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    Tab(Routes.TOOLS, R.string.nav_tools, Icons.Filled.Widgets, Icons.Outlined.Widgets),
    Tab(Routes.ME, R.string.nav_me, Icons.Filled.Person, Icons.Outlined.Person),
)

/**
 * Bottom bar: every tab gets an equal share of the width (nothing can overflow the screen edge).
 * Off = outlined black icon; On = filled icon in the brand colour with a soft pill behind it.
 */
@Composable
private fun ZapBottomBar(current: String?, onSelect: (String) -> Unit) {
    val onColor = MaterialTheme.colorScheme.primary
    val offColor = if (com.zapshare.app.ui.theme.isAppDark()) Color.White else Color.Black
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { t ->
                val selected = current == t.route
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(t.route) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (selected) t.filled else t.outlined,
                            contentDescription = stringResource(t.label),
                            tint = if (selected) onColor else offColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(t.label), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) onColor else offColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZapShareApp() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }
    val incoming by IntentBus.incoming.collectAsState()

    LaunchedEffect(Unit) { MusicController.connect(ctx) }
    FeatureExitAd(nav, ctx)
    LaunchedEffect(incoming) {
        when (val inc = incoming) {
            is IncomingShare.Files -> { Selection.replace(inc.items); nav.navigate(Routes.SEND) }
            is IncomingShare.Link -> { DownloaderState.prefill.value = inc.url; nav.navigate(Routes.DOWNLOADER) }
            null -> {}
        }
        IntentBus.incoming.value = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) Column {
                MiniPlayer { nav.navigate(Routes.NOW_PLAYING) }
                ZapBottomBar(route) { r ->
                    nav.navigate(r) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (Prefs.onboarded) Routes.HOME else Routes.PERMS,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            composable(Routes.PERMS) {
                PermissionsScreen(onDone = {
                    Prefs.onboarded = true
                    nav.navigate(Routes.HOME) { popUpTo(Routes.PERMS) { inclusive = true } }
                })
            }
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.FILES) { FilesScreen(nav) }
            composable(Routes.PLAYER) { PlayerScreen(nav) }
            composable(Routes.TOOLS) { ToolsScreen(nav) }
            composable(Routes.ME) { MeScreen(nav) }

            composable(Routes.SELECT, arguments = listOf(navArgument("mode") { type = NavType.StringType })) {
                SelectFilesScreen(nav, it.arguments?.getString("mode") ?: "send")
            }
            composable(Routes.SEND) { SendConnectScreen(nav) }
            composable(Routes.RECEIVE) { ReceiveScreen(nav) }
            composable(Routes.TRANSFER) { TransferScreen(nav) }
            composable(Routes.GROUP) { GroupScreen(nav) }
            composable(Routes.PC) { PcScreen(nav) }
            composable(Routes.HISTORY) { HistoryScreen(nav) }

            composable(Routes.CATEGORY, arguments = listOf(navArgument("cat") { type = NavType.StringType })) {
                val c = runCatching { FileCategory.valueOf(it.arguments?.getString("cat") ?: "") }.getOrDefault(FileCategory.OTHER)
                CategoryScreen(nav, c)
            }
            composable(Routes.BROWSER) { BrowserScreen(nav) }
            composable(Routes.LARGE) { LargeFilesScreen(nav) }
            composable(Routes.SEARCH) { SearchScreen(nav) }
            composable(Routes.APPS) { AppsScreen(nav) }
            composable(
                Routes.PREVIEW,
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType; defaultValue = "" },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("mime") { type = NavType.StringType; defaultValue = "" },
                )
            ) {
                val a = it.arguments
                PreviewScreen(nav, Uri.parse(a?.getString("uri") ?: ""), a?.getString("name") ?: "", a?.getString("mime") ?: "")
            }
            composable(Routes.NOW_PLAYING) { NowPlayingScreen(onBack = back) }
            composable(
                Routes.VIDEO,
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                )
            ) {
                VideoPlayerScreen(Uri.parse(it.arguments?.getString("uri") ?: ""), it.arguments?.getString("title") ?: "", onBack = back)
            }
            composable(Routes.DOWNLOADER) { DownloaderScreen(nav) }
            composable(Routes.CONVERTER) { ConverterScreen(nav) }
            composable(Routes.SAFEBOX) { SafeboxScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.PREMIUM) { PremiumScreen(nav) }
        }
    }
}

private val mainTabs = setOf(Routes.HOME, Routes.FILES, Routes.PLAYER, Routes.TOOLS, Routes.ME)

/** Test interstitial when the user leaves a feature and lands back on a main tab. */
@Composable
private fun FeatureExitAd(nav: NavHostController, ctx: android.content.Context) {
    DisposableEffect(nav) {
        var previous: String? = null
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, dest, _ ->
            val from = previous
            val to = dest.route
            previous = to
            val leftFeature = from != null && from !in mainTabs && from != Routes.PREMIUM && from != Routes.PERMS
            if (leftFeature && to in mainTabs) ctx.findActivity()?.let { Ads.show(it) }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
}

/** Global mini player shown above the bottom bar while music is loaded. */
@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
    val np by MusicController.state.collectAsState()
    if (!np.hasMedia) return
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            LinearProgressIndicator(
                progress = { if (np.duration > 0) np.position.toFloat() / np.duration else 0f },
                modifier = Modifier.fillMaxWidth(), color = Cyan3,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = np.artwork, contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(BrandGradient)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(np.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(np.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { MusicController.toggle() }) {
                    Icon(if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause")
                }
                IconButton(onClick = { MusicController.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
                IconButton(onClick = { MusicController.stop() }) { Icon(Icons.Filled.Close, stringResource(R.string.stop_player)) }
            }
        }
    }
}

fun NavController.goPreview(uri: Uri, name: String, mime: String) = navigate(Routes.preview(uri, name, mime))
