package com.zapshare.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zapshare.app.R
import com.zapshare.app.data.Prefs
import com.zapshare.app.ui.components.EmptyState
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.Routes
import com.zapshare.app.ui.navigation.goPreview
import com.zapshare.app.util.FileUtils
import com.zapshare.app.util.Permissions
import java.io.File
import java.io.FileInputStream

private data class StatusItem(val uri: Uri, val name: String, val mime: String, val size: Long, val video: Boolean)

private val statusDirs = listOf(
    "Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
    "WhatsApp/Media/.Statuses",
    "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses",
    "WhatsApp Business/Media/.Statuses"
)

private fun loadStatuses(ctx: android.content.Context): List<StatusItem> {
    val tree = Prefs.statusTreeUri
    val fromTree = if (tree.isNullOrBlank()) emptyList() else readTree(ctx, Uri.parse(tree))
    if (fromTree.isNotEmpty()) return fromTree
    if (!Permissions.hasAllFiles()) return emptyList()
    val root = Environment.getExternalStorageDirectory()
    return statusDirs.flatMap { rel ->
        val dir = File(root, rel)
        if (!dir.isDirectory) emptyList()
        else dir.listFiles().orEmpty().mapNotNull { file ->
            if (!file.isFile || file.name.startsWith(".") || file.length() <= 0L) return@mapNotNull null
            val mime = FileUtils.mimeOf(file.name)
            if (!mime.startsWith("image") && !mime.startsWith("video")) return@mapNotNull null
            StatusItem(Uri.fromFile(file), file.name, mime, file.length(), mime.startsWith("video"))
        }
    }.sortedByDescending { it.size }
}

private fun readTree(ctx: android.content.Context, treeUri: Uri): List<StatusItem> = runCatching {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    val out = ArrayList<StatusItem>()
    ctx.contentResolver.query(
        children,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        ),
        null, null, null
    )?.use { cursor ->
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        while (cursor.moveToNext()) {
            if (idCol < 0 || nameCol < 0) continue
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
            if (!mime.startsWith("image") && !mime.startsWith("video")) continue
            val name = cursor.getString(nameCol) ?: continue
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
            val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
            out += StatusItem(uri, name, mime, size, mime.startsWith("video"))
        }
    }
    out.sortedByDescending { it.size }
}.getOrDefault(emptyList())

private fun saveStatus(ctx: android.content.Context, item: StatusItem) {
    val out = FileUtils.createOutput(ctx, item.name, item.mime, "Status")
    var ok = false
    try {
        val input = if (item.uri.scheme == "file") FileInputStream(File(item.uri.path!!))
        else ctx.contentResolver.openInputStream(item.uri) ?: throw IllegalStateException("Cannot read this status")
        input.use { src -> out.stream.use { src.copyTo(it) } }
        ok = true
    } finally {
        FileUtils.finishOutput(ctx, out, ok)
    }
    if (!ok) throw IllegalStateException("Could not save ${item.name}")
}

@Composable
fun StatusSaverScreen(nav: NavController) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val items = remember(tick) { loadStatuses(ctx) }
    var saving by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, flags) }
            Prefs.statusTreeUri = uri.toString()
            tick++
        }
    }
    ZapScaffold(stringResource(R.string.status_saver), onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (items.isEmpty()) {
                EmptyState(Icons.Filled.PhotoLibrary, "No statuses yet. Open a status in WhatsApp first, then come back. If nothing appears, choose the WhatsApp statuses folder.")
                Button(onClick = { pick.launch(null) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Choose statuses folder")
                }
            } else {
                Text(
                    "Tap a status to view it. Saving copies it into ${FileUtils.saveLocationLabel()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(items, key = { it.uri.toString() }) { item ->
                        Column {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(14.dp)).clickable {
                                    if (item.video) nav.navigate(Routes.video(item.uri, item.name))
                                    else nav.goPreview(item.uri, item.name, item.mime)
                                }
                            ) {
                                AsyncImage(model = item.uri, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (item.video) Text("Video", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                            }
                            Button(
                                onClick = {
                                    saving = item.name
                                    val message = runCatching { saveStatus(ctx, item); "Saved ${item.name}" }
                                        .getOrElse { it.message ?: "Could not save" }
                                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                                    saving = null
                                },
                                enabled = saving == null,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) { Text(if (saving == item.name) "Saving…" else "Save") }
                        }
                    }
                }
            }
        }
    }
}
