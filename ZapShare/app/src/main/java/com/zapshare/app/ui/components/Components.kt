@file:OptIn(ExperimentalMaterial3Api::class)

package com.zapshare.app.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.Prefs
import com.zapshare.app.ui.theme.BrandGradient
import com.zapshare.app.ui.theme.BrandTileGradient
import com.zapshare.app.ui.theme.CatApps
import com.zapshare.app.ui.theme.CatDocs
import com.zapshare.app.ui.theme.CatMusic
import com.zapshare.app.ui.theme.CatPdf
import com.zapshare.app.ui.theme.CatPhoto
import com.zapshare.app.ui.theme.CatVideo
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.ui.theme.Navy
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ scaffold
@Composable
fun ZapScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content
    )
}

// ------------------------------------------------------------------ buttons & cards
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) BrandGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (enabled) Navy else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = if (enabled) Navy else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) { Column(Modifier.padding(padding), content = content) }
}

@Composable
fun ToolTile(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, badge: String? = null, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Colourful tile: light-to-deep gradient of the tool's own colour.
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(androidx.compose.ui.graphics.lerp(color, Color.White, 0.28f), color)
                )
            ),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Rounded brand-gradient square with a white icon — used for tools, menus and settings. */
@Composable
fun BrandIconBox(icon: ImageVector, size: Dp = 42.dp, iconSize: Dp = 24.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(BrandTileGradient),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

@Composable
fun SelectCircle(selected: Boolean, modifier: Modifier = Modifier, onDark: Boolean = false) {
    if (selected) {
        Icon(Icons.Filled.CheckCircle, "Selected", tint = Cyan3,
            modifier = modifier.size(24.dp).background(Color.White, CircleShape))
    } else {
        Icon(Icons.Filled.RadioButtonUnchecked, "Not selected",
            tint = if (onDark) Color.White else MaterialTheme.colorScheme.outline,
            modifier = modifier.size(24.dp).then(if (onDark) Modifier.background(Color(0x33000000), CircleShape) else Modifier))
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Cyan3)
    }
}

// ------------------------------------------------------------------ file visuals
fun categoryStyle(cat: FileCategory, name: String = ""): Pair<ImageVector, Color> = when (cat) {
    FileCategory.IMAGE -> Icons.Filled.Image to CatPhoto
    FileCategory.VIDEO -> Icons.Filled.Movie to CatVideo
    FileCategory.AUDIO -> Icons.Filled.MusicNote to CatMusic
    FileCategory.APK -> Icons.Filled.Android to CatApps
    FileCategory.ARCHIVE -> Icons.Filled.Archive to Color(0xFF64748B)
    FileCategory.DOCUMENT -> if (FileUtils.extension(name) == "pdf") Icons.Filled.PictureAsPdf to CatPdf
    else Icons.Filled.Description to CatDocs
    FileCategory.OTHER -> Icons.Filled.InsertDriveFile to Color(0xFF64748B)
}

@Composable
fun FileThumb(uri: Uri, mime: String, name: String, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val cat = FileUtils.categoryOf(mime, name)
    Box(modifier.clip(RoundedCornerShape(corner)), contentAlignment = Alignment.Center) {
        if (cat == FileCategory.IMAGE || cat == FileCategory.VIDEO) {
            AsyncImage(
                model = uri, contentDescription = name, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (cat == FileCategory.VIDEO) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.size(26.dp).background(Color(0x66000000), CircleShape).padding(3.dp))
            }
        } else if (cat == FileCategory.APK && uri.scheme == "file") {
            ApkIcon(uri.path ?: "", Modifier.fillMaxSize())
        } else {
            val (icon, color) = categoryStyle(cat, name)
            Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.fillMaxSize(0.55f))
            }
        }
    }
}

private val iconCache = LruCache<String, ImageBitmap>(200)

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var bmp by remember(packageName) { mutableStateOf(iconCache.get(packageName)) }
    LaunchedEffect(packageName) {
        if (bmp == null) bmp = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(packageName).toBitmap(144, 144).asImageBitmap() }
                .getOrNull()?.also { iconCache.put(packageName, it) }
        }
    }
    val b = bmp
    if (b != null) Image(BitmapPainter(b), null, modifier)
    else Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun ApkIcon(path: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var bmp by remember(path) { mutableStateOf(iconCache.get(path)) }
    LaunchedEffect(path) {
        if (bmp == null) bmp = withContext(Dispatchers.IO) {
            runCatching {
                val pm = ctx.packageManager
                @Suppress("DEPRECATION")
                val info = pm.getPackageArchiveInfo(path, 0)!!
                info.applicationInfo!!.sourceDir = path
                info.applicationInfo!!.publicSourceDir = path
                info.applicationInfo!!.loadIcon(pm).toBitmap(144, 144).asImageBitmap()
            }.getOrNull()?.also { iconCache.put(path, it) }
        }
    }
    val b = bmp
    if (b != null) Image(BitmapPainter(b), null, modifier)
    else {
        val (icon, color) = categoryStyle(FileCategory.APK)
        Box(modifier.background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.fillMaxSize(0.55f))
        }
    }
}

// ------------------------------------------------------------------ avatar / radar / QR
@Composable
fun AvatarBubble(index: Int, size: Dp = 56.dp, ring: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape).background(BrandGradient)
            .then(if (ring) Modifier.border(3.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(Prefs.avatars[index.mod(Prefs.avatars.size)], fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun RadarPulse(modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2
            for (i in 0 until 3) {
                val f = (p + i / 3f) % 1f
                drawCircle(Cyan3.copy(alpha = (1f - f) * 0.45f), radius = maxR * (0.3f + 0.7f * f), style = Stroke(width = 3.dp.toPx()))
            }
        }
        center()
    }
}

fun makeQr(content: String, sizePx: Int = 720): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
    val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    val dark = android.graphics.Color.rgb(0, 43, 58)
    for (y in 0 until sizePx) for (x in 0 until sizePx) {
        pixels[y * sizePx + x] = if (m[x, y]) dark else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

@Composable
fun QrImage(content: String, modifier: Modifier = Modifier) {
    val bmp = remember(content) { makeQr(content).asImageBitmap() }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(10.dp)) {
        Image(BitmapPainter(bmp), "QR code", Modifier.fillMaxSize())
    }
}

@Composable
fun StatPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer
    )
}
