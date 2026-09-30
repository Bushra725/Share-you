@file:OptIn(ExperimentalMaterial3Api::class)

package com.zapshare.app.ui.screens

import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zapshare.app.premium.AdBanner
import com.zapshare.app.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import coil.compose.AsyncImage
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.MediaFile
import com.zapshare.app.downloader.VideoDownloader
import com.zapshare.app.ui.components.EmptyState
import com.zapshare.app.ui.components.FileThumb
import com.zapshare.app.ui.components.GradientButton
import com.zapshare.app.ui.components.LoadingBox
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.StatPill
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.Routes
import com.zapshare.app.ui.navigation.goPreview
import com.zapshare.app.ui.theme.BrandGradient
import com.zapshare.app.ui.theme.CatPdf
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.ui.theme.Navy
import com.zapshare.app.util.FileUtils
import com.zapshare.app.util.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object DownloaderState { val prefill = MutableStateFlow<String?>(null) }

// ================================================================================ VIDEO DOWNLOADER
@Composable
fun DownloaderScreen(nav: NavController) {
    val ctx = LocalContext.current
    val tasks by VideoDownloader.tasks.collectAsState()
    val engine by VideoDownloader.engineStatus.collectAsState()
    val prefill by DownloaderState.prefill.collectAsState()
    var url by remember { mutableStateOf(prefill ?: "") }
    var audioOnly by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(prefill) {
        prefill?.let { url = it; DownloaderState.prefill.value = null }
    }

    ZapScaffold(stringResource(R.string.downloader), onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                // Short header: title + supported platforms as icons
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BrandGradient).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.download_video), color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        PlatformIcon(R.drawable.ic_platform_facebook, "Facebook", Color(0xFF1877F2))
                        PlatformIcon(R.drawable.ic_platform_instagram, "Instagram", Color(0xFFD62976))
                        PlatformIcon(R.drawable.ic_platform_x, "X", Color(0xFF111111))
                        PlatformIcon(R.drawable.ic_platform_tiktok, "TikTok", Color(0xFF010101))
                    }
                }
            }
            item {
                SectionCard {
                    OutlinedTextField(
                        url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text(stringResource(R.string.paste_link)) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip
                                val text = clip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                                url = VideoDownloader.extractUrl(text) ?: text ?: ""
                            }) { Icon(Icons.Filled.ContentPaste, "Paste") }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!audioOnly, { audioOnly = false }, label = { Text(stringResource(R.string.video_mp4)) })
                        FilterChip(audioOnly, { audioOnly = true }, label = { Text(stringResource(R.string.audio_mp3)) })
                    }
                    Spacer(Modifier.height(10.dp))
                    GradientButton(stringResource(R.string.download), {
                        if (VideoDownloader.extractUrl(url) == null) Toast.makeText(ctx, "Paste a valid link", Toast.LENGTH_SHORT).show()
                        else { VideoDownloader.download(ctx, url, audioOnly); url = "" }
                    }, Modifier.fillMaxWidth(), icon = Icons.Filled.Download)
                    Text("Engine: $engine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                Text("Recent downloads", style = MaterialTheme.typography.titleMedium)
            }
            if (tasks.isEmpty()) item {
                EmptyState(Icons.Filled.CloudDownload, "Videos you download are saved to your gallery and listed here.")
            }
            items(tasks, key = { it.id }) { t -> DownloadRow(t, nav) }
            item { AdBanner(onRemoveAds = { nav.navigate(Routes.PREMIUM) }) }
        }
    }
}

@Composable
private fun DownloadRow(t: VideoDownloader.Task, nav: NavController) {
    val ctx = LocalContext.current
    SectionCard(padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = t.resultUri != null) {
                val name = t.fileName ?: "video.mp4"
                t.resultUri?.let { nav.goPreview(it, name, FileUtils.mimeOf(name)) }
            }) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (t.thumbnail != null) AsyncImage(t.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.CloudDownload, null, Modifier.align(Alignment.Center), tint = Cyan3)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title ?: t.url, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatPill(t.platform)
                    Spacer(Modifier.width(6.dp))
                    val label = when (t.state) {
                        VideoDownloader.State.QUEUED -> "Queued"
                        VideoDownloader.State.FETCHING -> "Reading link…"
                        VideoDownloader.State.DOWNLOADING -> "${(t.progress * 100).toInt()}%" + if (t.eta > 0) " • ${t.eta}s left" else ""
                        VideoDownloader.State.SAVING -> "Saving…"
                        VideoDownloader.State.DONE -> if (t.audioOnly) "Saved to Music" else "Saved to Gallery"
                        VideoDownloader.State.FAILED -> "Failed"
                        VideoDownloader.State.CANCELLED -> "Cancelled"
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (t.state == VideoDownloader.State.DOWNLOADING || t.state == VideoDownloader.State.FETCHING) {
                    Spacer(Modifier.height(4.dp))
                    if (t.state == VideoDownloader.State.FETCHING) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan3)
                    else LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth(), color = Cyan3)
                }
                t.error?.let { Text(it, color = CatPdf, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
            }
            when (t.state) {
                VideoDownloader.State.DONE -> Icon(Icons.Filled.CheckCircle, "Done", tint = Cyan3)
                VideoDownloader.State.FAILED, VideoDownloader.State.CANCELLED -> Row {
                    IconButton(onClick = { VideoDownloader.remove(t.id); VideoDownloader.download(ctx, t.url, t.audioOnly) }) {
                        Icon(Icons.Filled.Refresh, "Retry")
                    }
                    IconButton(onClick = { VideoDownloader.remove(t.id) }) { Icon(Icons.Filled.Close, "Remove") }
                }
                else -> IconButton(onClick = { VideoDownloader.cancel(t.id) }) { Icon(Icons.Filled.Close, "Cancel") }
            }
        }
    }
}

@Composable
private fun PlatformIcon(@DrawableRes res: Int, label: String, bg: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(painterResource(res), label, tint = Color.Unspecified, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Navy, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
