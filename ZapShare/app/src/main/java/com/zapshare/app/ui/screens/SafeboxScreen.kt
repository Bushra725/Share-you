@file:OptIn(ExperimentalMaterial3Api::class)

package com.zapshare.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import com.zapshare.app.data.Prefs
import com.zapshare.app.safebox.Vault
import com.zapshare.app.ui.components.EmptyState
import com.zapshare.app.ui.components.categoryStyle
import com.zapshare.app.ui.components.LoadingBox
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.navigation.goPreview
import com.zapshare.app.ui.theme.BrandGradient
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.ui.theme.Navy
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.launch

@Composable
fun SafeboxScreen(nav: NavController) {
    val premium by com.zapshare.app.premium.Premium.isPremium.collectAsState()
    if (!premium) {
        PremiumGate(androidx.compose.ui.res.stringResource(com.zapshare.app.R.string.safebox),
            androidx.compose.ui.res.stringResource(com.zapshare.app.R.string.premium_only_safebox), nav)
        return
    }
    SafeboxContent(nav)
}

@Composable
private fun SafeboxContent(nav: NavController) {
    val ctx = LocalContext.current
    val unlocked by Vault.unlocked.collectAsState()
    val activity = remember { ctx.findActivity() }

    // Auto-lock when the app goes to the background.
    DisposableEffect(activity) {
        val owner = activity as? FragmentActivity
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && !Vault.suppressAutoLock) Vault.lock(ctx)
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    val leave: () -> Unit = { Vault.lock(ctx); nav.popBackStack() }
    BackHandler { leave() }

    when {
        !Vault.isSetUp() -> PinScreen("Create a Safebox PIN", "Choose 4–6 digits. You'll need it to open your private files.", confirm = true, onBack = leave) { pin ->
            Vault.setup(ctx, pin); null
        }
        !unlocked -> {
            val bio = Prefs.vaultBiometric && canBiometric(ctx)
            PinScreen("Safebox is locked", "Enter your PIN", confirm = false, onBack = leave,
                onBiometric = if (bio) ({ biometric(activity as? FragmentActivity) { appScope.launch { Vault.unlockAfterBiometric(ctx) } } }) else null
            ) { pin -> if (Vault.unlockWithPin(ctx, pin)) null else "Wrong PIN" }
        }
        else -> VaultContent(nav, leave)
    }
}

@Composable
private fun VaultContent(nav: NavController, leave: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by Vault.entries.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Vault.Entry?>(null) }
    var imported by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var bio by remember { mutableStateOf(Prefs.vaultBiometric) }
    val deleter = rememberDeleter { imported = emptyList() }
    var showGallery by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    /** Gallery picks are MOVED: after encrypting, the originals are removed from the gallery. */
    var moveAfterImport by remember { mutableStateOf(false) }
    var savedPopup by remember { mutableStateOf(false) }
    var pendingGalleryDelete by remember { mutableStateOf<List<Uri>>(emptyList()) }

    suspend fun importFiles(uris: List<Uri>) {
        busy = true
        val ok = mutableListOf<Uri>()
        uris.forEach { u -> runCatching { Vault.add(ctx, u) }.onSuccess { ok += u } }
        busy = false
        if (ok.isNotEmpty()) {
            if (moveAfterImport) {
                moveAfterImport = false
                pendingGalleryDelete = ok
                savedPopup = true
            } else imported = ok
        }
        if (ok.size < uris.size) Toast.makeText(ctx, "${uris.size - ok.size} file(s) could not be added", Toast.LENGTH_SHORT).show()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        Vault.suppressAutoLock = false
        uris.forEach { uri ->
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (uris.isNotEmpty()) scope.launch { importFiles(uris) }
    }
    LaunchedEffect(Unit) {
        if (SafeboxQueue.pending.isNotEmpty()) {
            val q = SafeboxQueue.pending.toList(); SafeboxQueue.pending.clear(); importFiles(q)
        }
    }

    if (showGallery) {
        SafeboxGalleryPicker(onCancel = { showGallery = false }) { picked ->
            showGallery = false
            moveAfterImport = true
            scope.launch { importFiles(picked) }
        }
        return
    }

    ZapScaffold("Safebox", onBack = leave, actions = {
        IconButton(onClick = { Vault.lock(ctx) }) { Icon(Icons.Filled.Lock, "Lock") }
    }, floatingActionButton = {
        Box {
            ExtendedFloatingActionButton(onClick = { addMenu = true }, containerColor = Cyan3, contentColor = Navy,
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add") })
            androidx.compose.material3.DropdownMenu(addMenu, { addMenu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.stringResource(com.zapshare.app.R.string.add_photos)) },
                    leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                    onClick = { addMenu = false; showGallery = true })
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.stringResource(com.zapshare.app.R.string.add_files)) },
                    leadingIcon = { Icon(Icons.Filled.Folder, null) },
                    onClick = {
                        addMenu = false
                        Vault.suppressAutoLock = true
                        picker.launch(arrayOf("*/*"))
                    })
            }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard {
                    Text("🔒 ${entries.size} private file(s) • AES-256 encrypted", style = MaterialTheme.typography.titleSmall)
                    Text("Files here are hidden from the gallery and other apps. Restore moves them back to Download/ZapShare/Restored.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (canBiometric(ctx)) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Unlock with fingerprint / face", Modifier.weight(1f))
                        Switch(bio, { bio = it; Prefs.vaultBiometric = it })
                    }
                }
            }
            if (busy) item { LoadingBox() }
            if (entries.isEmpty() && !busy) item { EmptyState(Icons.Filled.Lock, "Your Safebox is empty.\nTap “Add files” to hide photos, videos or documents.") }
            items(entries, key = { it.id }) { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        scope.launch {
                            busy = true
                            val f = runCatching { Vault.openTemp(ctx, e) }.getOrNull()
                            busy = false
                            if (f != null) nav.goPreview(Uri.fromFile(f), e.name, e.mime)
                        }
                    }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, color) = categoryStyle(FileUtils.categoryOf(e.mime, e.name), e.name)
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (e.size > 0) FileUtils.formatSize(e.size) else ""} • added ${FileUtils.formatDate(e.added)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { menuFor = e }) { Icon(Icons.Filled.Restore, "Options") }
                }
            }
        }
    }

    menuFor?.let { e ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(e.name, maxLines = 2) },
            text = { Text("Restore puts the file back in public storage. Delete removes it permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    menuFor = null
                    scope.launch {
                        runCatching { Vault.restore(ctx, e) }
                            .onSuccess { Toast.makeText(ctx, "Restored to Download/ZapShare/Restored", Toast.LENGTH_SHORT).show() }
                    }
                }) { Icon(Icons.Filled.Restore, null); Text(" Restore") }
            },
            dismissButton = {
                TextButton(onClick = { menuFor = null; Vault.delete(ctx, e) }) { Icon(Icons.Filled.Delete, null); Text(" Delete") }
            }
        )
    }
    if (savedPopup) AlertDialog(
        onDismissRequest = {
            savedPopup = false
            val move = pendingGalleryDelete
            pendingGalleryDelete = emptyList()
            if (move.isNotEmpty()) deleter(move)
        },
        title = { Text("Saved") },
        text = { Text(if (pendingGalleryDelete.size == 1) "Added to Safebox" else "${pendingGalleryDelete.size} files added to Safebox") },
        confirmButton = {
            TextButton(onClick = {
                savedPopup = false
                val move = pendingGalleryDelete
                pendingGalleryDelete = emptyList()
                if (move.isNotEmpty()) deleter(move)
            }) { Text("OK") }
        }
    )
    if (imported.isNotEmpty()) AlertDialog(
        onDismissRequest = { imported = emptyList() },
        title = { Text("Saved") },
        text = { Text("Added to Safebox. Delete the ${imported.size} original file(s) so they no longer appear in other apps?") },
        confirmButton = { TextButton(onClick = { deleter(imported) }) { Text("Delete originals") } },
        dismissButton = { TextButton(onClick = { imported = emptyList() }) { Text("Keep") } }
    )
}

/** PIN pad. [onSubmit] returns an error message, or null on success. */
@Composable
private fun PinScreen(
    title: String, subtitle: String, confirm: Boolean, onBack: () -> Unit,
    onBiometric: (() -> Unit)? = null, onSubmit: suspend (String) -> String?
) {
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onBiometric?.invoke() }

    fun submit() {
        if (pin.length < 4) { error = "Use at least 4 digits"; return }
        if (confirm && first == null) { first = pin; pin = ""; error = null; return }
        if (confirm && first != pin) { error = "PINs don't match. Try again"; first = null; pin = ""; return }
        working = true
        val entered = pin
        scope.launch {
            error = onSubmit(entered)
            working = false
            if (error != null) pin = ""
        }
    }

    ZapScaffold("Safebox", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, null, tint = Navy, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(if (confirm && first != null) "Confirm your PIN" else title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { i ->
                    Box(Modifier.size(16.dp).clip(CircleShape).background(
                        if (i < pin.length) Cyan3 else MaterialTheme.colorScheme.surfaceVariant))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            if (working) LoadingBox()
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { k ->
                        Box(
                            Modifier.size(76.dp).clip(CircleShape).clickable(enabled = !working) {
                                when (k) {
                                    "del" -> pin = pin.dropLast(1)
                                    "bio" -> onBiometric?.invoke()
                                    else -> if (pin.length < 6) { pin += k; error = null; if (pin.length == 6) submit() }
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            when (k) {
                                "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Delete")
                                "bio" -> if (onBiometric != null) Icon(Icons.Filled.Fingerprint, "Biometric", tint = Cyan3, modifier = Modifier.size(32.dp)) else Unit
                                else -> Text(k, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { submit() }, enabled = pin.length >= 4 && !working) { Text("OK", fontSize = 18.sp) }
        }
    }
}

private fun canBiometric(ctx: android.content.Context) =
    BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

private fun biometric(activity: FragmentActivity?, onSuccess: () -> Unit) {
    activity ?: return
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Safebox")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
    )
}


/** In-app gallery: tick photos/videos, tap "Hide N" → they are encrypted into the Safebox and removed from the gallery. */
@Composable
private fun SafeboxGalleryPicker(onCancel: () -> Unit, onDone: (List<Uri>) -> Unit) {
    val ctx = LocalContext.current
    val media by androidx.compose.runtime.produceState<List<com.zapshare.app.data.MediaFile>?>(null) {
        value = (com.zapshare.app.data.MediaRepository.images(ctx) + com.zapshare.app.data.MediaRepository.videos(ctx))
            .sortedByDescending { it.dateModified }
    }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<Uri>() }
    BackHandler { onCancel() }
    ZapScaffold(androidx.compose.ui.res.stringResource(com.zapshare.app.R.string.add_photos), onBack = onCancel, bottomBar = {
        if (picked.isNotEmpty()) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            com.zapshare.app.ui.components.GradientButton(
                androidx.compose.ui.res.stringResource(com.zapshare.app.R.string.hide_n, picked.size),
                { onDone(picked.toList()) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Lock
            )
        }
    }) { pad ->
        val list = media
        if (list == null) { Box(Modifier.padding(pad)) { LoadingBox() }; return@ZapScaffold }
        if (list.isEmpty()) { Box(Modifier.padding(pad)) { EmptyState(Icons.Filled.PhotoLibrary, "No photos or videos found") }; return@ZapScaffold }
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), Modifier.padding(pad),
            contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(list.size, key = { list[it].uri.toString() }) { i ->
                val f = list[i]
                val sel = f.uri in picked
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable {
                    if (sel) picked.remove(f.uri) else picked.add(f.uri)
                }) {
                    com.zapshare.app.ui.components.FileThumb(f.uri, f.mime, f.name, Modifier.fillMaxSize(), 10.dp)
                    if (sel) Box(Modifier.fillMaxSize().background(Cyan3.copy(alpha = .3f)))
                    com.zapshare.app.ui.components.SelectCircle(sel, Modifier.align(Alignment.TopEnd).padding(6.dp), onDark = true)
                }
            }
        }
    }
}
