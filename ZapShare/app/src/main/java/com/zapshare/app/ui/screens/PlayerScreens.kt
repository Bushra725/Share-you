@file:OptIn(ExperimentalMaterial3Api::class)

package com.zapshare.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.systemBarsPadding
import com.zapshare.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.filled.Close
import coil.compose.AsyncImage
import com.zapshare.app.data.MediaFile
import com.zapshare.app.data.MediaRepository
import com.zapshare.app.player.MusicController
import com.zapshare.app.ui.components.EmptyState
import com.zapshare.app.ui.components.FileThumb
import com.zapshare.app.ui.components.GradientButton
import com.zapshare.app.ui.components.LoadingBox
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.Routes
import com.zapshare.app.ui.theme.BrandGradient
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.ui.theme.Navy
import com.zapshare.app.util.FileUtils
import com.zapshare.app.util.Permissions

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

// ================================================================================ PLAYER TAB
@Composable
fun PlayerScreen(nav: NavController) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    ZapScaffold("Player") { pad ->
        Column(Modifier.padding(pad)) {
            SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Music") }, icon = { Icon(Icons.Filled.MusicNote, null) })
                Tab(tab == 1, { tab = 1 }, text = { Text("Videos") }, icon = { Icon(Icons.Filled.VideoLibrary, null) })
            }
            if (!Permissions.hasMedia(ctx)) {
                EmptyState(Icons.Filled.MusicNote, "Allow media access on the first screen (Me → Settings → App permissions).")
                return@Column
            }
            if (tab == 0) MusicList(nav) else VideoGrid(nav)
        }
    }
}

@Composable
private fun MusicList(nav: NavController) {
    val ctx = LocalContext.current
    val songs by produceState<List<MediaFile>?>(null) { value = MediaRepository.audio(ctx) }
    val np by MusicController.state.collectAsState()
    val list = songs ?: run { LoadingBox(); return }
    if (list.isEmpty()) { EmptyState(Icons.Filled.MusicNote, "No music found"); return }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Play all", { MusicController.playList(ctx, list, 0); nav.navigate(Routes.NOW_PLAYING) },
                    Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
                GradientButton("Shuffle", {
                    val sh = list.shuffled(); MusicController.playList(ctx, sh, 0); nav.navigate(Routes.NOW_PLAYING)
                }, Modifier.weight(1f), icon = Icons.Filled.Shuffle)
            }
        }
        items(list, key = { it.uri.toString() }) { s ->
            val playing = np.mediaId == s.uri.toString()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { MusicController.playList(ctx, list, list.indexOf(s)); nav.navigate(Routes.NOW_PLAYING) }
                    .background(if (playing) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, tint = Navy)
                    if (s.albumId > 0) AsyncImage(MediaRepository.albumArtUri(s.albumId), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name.substringBeforeLast('.'), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("${s.artist ?: "Unknown artist"} • ${FileUtils.formatDuration(s.duration)}", maxLines = 1,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VideoGrid(nav: NavController) {
    val ctx = LocalContext.current
    val videos by produceState<List<MediaFile>?>(null) { value = MediaRepository.videos(ctx) }
    val list = videos ?: run { LoadingBox(); return }
    if (list.isEmpty()) { EmptyState(Icons.Filled.VideoLibrary, "No videos found"); return }
    LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.uri.toString() }) { v ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(Routes.video(v.uri, v.name)) }) {
                Box {
                    FileThumb(v.uri, v.mime, v.name, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
                    Text(FileUtils.formatDuration(v.duration), color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
                }
                Text(v.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Text("${FileUtils.formatSize(v.size)} • ${v.bucket ?: ""}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

// ================================================================================ NOW PLAYING
@Composable
fun NowPlayingScreen(onBack: () -> Unit) {
    val np by MusicController.state.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    ZapScaffold(stringResource(R.string.now_playing), onBack = onBack, actions = {
        // Close = stop the music and remove the player (mini player disappears too)
        IconButton(onClick = { MusicController.stop(); onBack() }) {
            Icon(Icons.Filled.Close, stringResource(R.string.stop_player))
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MusicNote, null, tint = Navy, modifier = Modifier.size(120.dp))
                if (np.artwork != null) AsyncImage(np.artwork, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(24.dp))
            Text(np.title.ifEmpty { "Nothing playing" }, style = MaterialTheme.typography.titleLarge, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(np.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            val dur = np.duration.coerceAtLeast(1)
            Slider(
                value = if (dragging) dragPos else np.position.toFloat() / dur,
                onValueChange = { dragging = true; dragPos = it },
                onValueChangeFinished = { MusicController.seekTo((dragPos * dur).toLong()); dragging = false },
                colors = SliderDefaults.colors(thumbColor = Cyan3, activeTrackColor = Cyan3)
            )
            Row(Modifier.fillMaxWidth()) {
                Text(FileUtils.formatDuration(np.position), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(FileUtils.formatDuration(np.duration), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { MusicController.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (np.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { MusicController.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                Box(Modifier.size(72.dp).clip(CircleShape).background(BrandGradient).clickable { MusicController.toggle() }, contentAlignment = Alignment.Center) {
                    Icon(if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", tint = Navy, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { MusicController.next() }) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp)) }
                IconButton(onClick = { MusicController.cycleRepeat() }) {
                    Icon(if (np.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
                        tint = if (np.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ================================================================================ VIDEO PLAYER
@Composable
fun VideoPlayerScreen(uri: Uri, title: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = remember { ctx.findActivity() }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }

    fun applyOrientation(land: Boolean) {
        landscape = land
        val a = activity ?: return
        a.requestedOrientation = if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val c = WindowCompat.getInsetsController(a.window, a.window.decorView)
        if (land) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) { onDispose { applyOrientation(false) } }
    BackHandler { if (landscape) applyOrientation(false) else onBack() }

    // Portrait: top bar + video fitted between the status bar and the navigation bar, so the
    // time bar, fullscreen and settings buttons are never hidden behind the system bars.
    // Landscape: true fullscreen (system bars hidden).
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .then(if (landscape) Modifier.displayCutoutPadding() else Modifier.systemBarsPadding())
    ) {
        if (!landscape) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { speedMenu = true }) { Icon(Icons.Filled.Speed, "Speed", tint = Color.White) }
                DropdownMenu(speedMenu, { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                        DropdownMenuItem(text = { Text("${sp}x") }, onClick = {
                            player?.playbackParameters = PlaybackParameters(sp); speedMenu = false
                        })
                    }
                }
            }
            IconButton(onClick = { applyOrientation(!landscape) }) { Icon(Icons.Filled.ScreenRotation, "Rotate", tint = Color.White) }
        }
        VideoSurface(
            uri, Modifier.weight(1f).fillMaxWidth().padding(bottom = if (landscape) 4.dp else 12.dp),
            onPlayer = { player = it }, onFullscreen = { applyOrientation(it) }
        )
    }
}
