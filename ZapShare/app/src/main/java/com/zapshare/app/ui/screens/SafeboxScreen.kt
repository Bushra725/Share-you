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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
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
import java.util.concurrent.atomic.AtomicBoolean
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
    val leaving = remember { AtomicBoolean(false) }
    val leave: () -> Unit = {
        if (leaving.compareAndSet(false, true)) {
            if (nav.currentBackStackEntry?.destination?.route == "safebox") nav.popBackStack()
            Vault.lock(ctx)
        }
    }
    var setupQuestions by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<VaultOverlay?>(null) }
    var usePattern by remember { mutableStateOf(Vault.hasPattern()) }
    BackHandler {
        if (overlay != null) overlay = null else leave()
    }

    Box(Modifier.fillMaxSize()) {
        when {
            !Vault.isSetUp() -> PinScreen(
                "Create a Safebox PIN",
                "Choose 4–6 digits. You'll need it to open your private files.",
                confirm = true,
                onBack = leave
            ) { pin ->
                Vault.setup(ctx, pin)
                setupQuestions = true
                null
            }
            setupQuestions -> QuestionEditor(
                title = "Security questions",
                subtitle = "These answers reset your PIN or pattern if you forget them.",
                onBack = leave
            ) { answers ->
                Vault.saveQuestions(answers)
                setupQuestions = false
            }
            !unlocked && overlay == VaultOverlay.Reset -> ResetLock(onBack = { overlay = null }) { overlay = null }
            !unlocked && usePattern && Vault.hasPattern() -> PatternUnlock(
                onBack = leave,
                onUsePin = { usePattern = false },
                onForgot = { overlay = VaultOverlay.Reset },
                onBiometric = if (Prefs.vaultBiometric && canBiometric(ctx)) {
                    { biometric(activity as? FragmentActivity) { appScope.launch { Vault.unlockAfterBiometric(ctx) } } }
                } else null
            )
            !unlocked -> PinScreen(
                "Safebox is locked",
                "Enter your PIN",
                confirm = false,
                onBack = leave,
                onBiometric = if (Prefs.vaultBiometric && canBiometric(ctx)) {
                    { biometric(activity as? FragmentActivity) { appScope.launch { Vault.unlockAfterBiometric(ctx) } } }
                } else null,
                onForgot = { overlay = VaultOverlay.Reset },
                onPattern = if (Vault.hasPattern()) ({ usePattern = true }) else null
            ) { pin -> if (Vault.unlockWithPin(ctx, pin)) null else "Wrong PIN" }
            overlay == VaultOverlay.ChangePin -> ChangePinScreen(onBack = { overlay = null })
            overlay == VaultOverlay.Pattern -> PatternSetup(onBack = { overlay = null })
            overlay == VaultOverlay.Questions -> QuestionEditor(
                title = "Reset security questions",
                subtitle = "Choose two questions. The old answers stop working.",
                onBack = { overlay = null }
            ) { answers ->
                Vault.saveQuestions(answers)
                overlay = null
                Toast.makeText(ctx, "Security questions updated", Toast.LENGTH_SHORT).show()
            }
            else -> VaultContent(nav, leave) { overlay = it }
        }
    }
}

@Composable
private fun VaultContent(nav: NavController, leave: () -> Unit, onManage: (VaultOverlay) -> Unit) {
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
                    TextButton(onClick = { onManage(VaultOverlay.ChangePin) }) { Text("Change PIN") }
                    TextButton(onClick = { onManage(VaultOverlay.Pattern) }) {
                        Text(if (Vault.hasPattern()) "Change pattern lock" else "Set a pattern lock")
                    }
                    TextButton(onClick = { onManage(VaultOverlay.Questions) }) {
                        Text(if (Vault.hasQuestions()) "Reset security questions" else "Set security questions")
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
    onBiometric: (() -> Unit)? = null,
    onForgot: (() -> Unit)? = null,
    onPattern: (() -> Unit)? = null,
    onSubmit: suspend (String) -> String?
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
            if (onPattern != null || onForgot != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onPattern != null) TextButton(onClick = onPattern) { Text("Use pattern") }
                if (onForgot != null) TextButton(onClick = onForgot) { Text("Forgot PIN?") }
            }
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

private enum class VaultOverlay { ChangePin, Pattern, Questions, Reset }

private val SECURITY_QUESTIONS = listOf(
    "What city were you born in?",
    "What is your mother's first name?",
    "What was the name of your first school?",
    "What is your favorite color?",
    "What is your first pet's name?"
)

@Composable
private fun ChangePinScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf<String?>(null) }
    if (current == null) {
        PinScreen("Enter your current PIN", "Confirm it's you before choosing a new PIN.", confirm = false, onBack = onBack) { pin ->
            if (Vault.unlockWithPin(ctx, pin)) { current = pin; null } else "Wrong PIN"
        }
    } else {
        PinScreen("Choose a new PIN", "Enter it twice. The old PIN will stop working.", confirm = true, onBack = onBack) { pin ->
            if (Vault.changePin(current!!, pin, ctx)) {
                Toast.makeText(ctx, "PIN changed", Toast.LENGTH_SHORT).show()
                onBack()
                null
            } else "Could not change the PIN"
        }
    }
}

@Composable
private fun PatternSetup(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    ZapScaffold("Pattern lock", onBack = onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Pattern, null, tint = Cyan3, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))
            Text(if (first == null) "Draw a new pattern" else "Draw the same pattern again", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Connect at least 4 dots.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(error ?: "", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            PatternPad(reset) { pattern ->
                val saved = first
                if (saved == null) {
                    first = pattern
                    error = null
                    reset++
                } else if (saved != pattern) {
                    first = null
                    error = "Patterns don't match. Try again"
                    reset++
                } else scope.launch {
                    Vault.setPattern(pattern)
                    Toast.makeText(ctx, "Pattern lock saved", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
            Spacer(Modifier.weight(1f))
            if (Vault.hasPattern()) TextButton(onClick = {
                Vault.clearPattern()
                Toast.makeText(ctx, "Pattern lock removed", Toast.LENGTH_SHORT).show()
                onBack()
            }) { Text("Remove pattern lock") }
        }
    }
}

@Composable
private fun PatternUnlock(
    onBack: () -> Unit,
    onUsePin: () -> Unit,
    onForgot: () -> Unit,
    onBiometric: (() -> Unit)?
) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onBiometric?.invoke() }
    ZapScaffold("Safebox", onBack = onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Draw your pattern", style = MaterialTheme.typography.titleLarge)
            Text(error ?: " ", color = MaterialTheme.colorScheme.error)
            PatternPad(reset) { pattern ->
                scope.launch {
                    if (Vault.unlockWithPattern(ctx, pattern)) error = null
                    else { error = "Wrong pattern"; reset++ }
                }
            }
            if (onBiometric != null) TextButton(onClick = onBiometric) { Text("Use fingerprint") }
            TextButton(onClick = onUsePin) { Text("Use PIN") }
            TextButton(onClick = onForgot) { Text("Forgot pattern?") }
        }
    }
}

@Composable
private fun QuestionEditor(title: String, subtitle: String, onBack: () -> Unit, onSave: suspend (List<Pair<Int, String>>) -> Unit) {
    val scope = rememberCoroutineScope()
    var firstQ by remember { mutableIntStateOf(0) }
    var secondQ by remember { mutableIntStateOf(1) }
    var firstA by remember { mutableStateOf("") }
    var secondA by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    ZapScaffold(title, onBack = onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Question 1", fontWeight = FontWeight.SemiBold)
            SECURITY_QUESTIONS.forEachIndexed { index, q ->
                Text(
                    q,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (firstQ == index) Cyan3.copy(alpha = .25f) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { if (index != secondQ) firstQ = index }
                        .padding(10.dp)
                )
            }
            OutlinedTextField(firstA, { firstA = it }, label = { Text("Answer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Question 2", fontWeight = FontWeight.SemiBold)
            SECURITY_QUESTIONS.forEachIndexed { index, q ->
                Text(
                    q,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (secondQ == index) Cyan3.copy(alpha = .25f) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { if (index != firstQ) secondQ = index }
                        .padding(10.dp)
                )
            }
            OutlinedTextField(secondA, { secondA = it }, label = { Text("Answer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(error ?: "", color = MaterialTheme.colorScheme.error)
            com.zapshare.app.ui.components.GradientButton("Save", {
                when {
                    firstA.trim().length < 2 || secondA.trim().length < 2 -> error = "Enter both answers"
                    firstQ == secondQ -> error = "Choose two different questions"
                    else -> {
                        working = true
                        scope.launch {
                            onSave(listOf(firstQ to firstA, secondQ to secondA))
                            working = false
                        }
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = !working)
        }
    }
}

@Composable
private fun ResetLock(onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val indexes = remember { Vault.questionIndexes() }
    var stage by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf(List(indexes.size) { "" }) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (indexes.size < 2) {
        ZapScaffold("Reset lock", onBack = onBack) { pad ->
            Column(Modifier.padding(pad).padding(24.dp)) {
                Text("Security questions are not set yet. Open the Safebox with your PIN, then set questions from the lock options.")
            }
        }
        return
    }
    if (stage == 0) {
        ZapScaffold("Reset lock", onBack = onBack) { pad ->
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Answer your security questions to choose a new PIN.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                indexes.forEachIndexed { i, q ->
                    Text(SECURITY_QUESTIONS.getOrElse(q) { "Question" }, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        answers.getOrElse(i) { "" },
                        { value -> answers = answers.toMutableList().also { it[i] = value } },
                        label = { Text("Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
                com.zapshare.app.ui.components.GradientButton("Continue", {
                    scope.launch {
                        val pairs = indexes.mapIndexed { i, q -> q to answers.getOrElse(i) { "" } }
                        if (Vault.checkQuestions(pairs)) { error = null; stage = 1 }
                        else error = "Those answers don't match"
                    }
                }, Modifier.fillMaxWidth())
            }
        }
    } else {
        PinScreen("Create a new PIN", "This replaces your old PIN and removes the pattern lock.", confirm = true, onBack = onBack) { pin ->
            Vault.resetPin(ctx, pin)
            Toast.makeText(ctx, "PIN reset", Toast.LENGTH_SHORT).show()
            onDone()
            null
        }
    }
}

@Composable
private fun PatternPad(resetKey: Int, onComplete: (String) -> Unit) {
    var selected by remember(resetKey) { mutableStateOf(listOf<Int>()) }
    Canvas(
        Modifier.size(280.dp).pointerInput(resetKey) {
            val picked = ArrayList<Int>()
            fun hit(p: Offset): Int? {
                val cell = size.width / 3f
                for (i in 0 until 9) {
                    val c = Offset((i % 3) * cell + cell / 2f, (i / 3) * cell + cell / 2f)
                    if ((p - c).getDistance() < cell * 0.34f) return i
                }
                return null
            }
            detectDragGestures(
                onDragStart = { p ->
                    picked.clear()
                    hit(p)?.let { picked.add(it) }
                    selected = picked.toList()
                },
                onDrag = { change, _ ->
                    hit(change.position)?.let { dot ->
                        if (dot !in picked && picked.isNotEmpty()) picked.add(dot)
                    }
                    selected = picked.toList()
                },
                onDragEnd = {
                    val done = picked.toList()
                    picked.clear()
                    if (done.size >= 4) onComplete(done.joinToString(","))
                    else selected = emptyList()
                },
                onDragCancel = { picked.clear(); selected = emptyList() }
            )
        }
    ) {
        val cell = size.width / 3f
        fun center(i: Int) = Offset((i % 3) * cell + cell / 2f, (i / 3) * cell + cell / 2f)
        for (n in 0 until selected.size - 1) {
            drawLine(Cyan3, center(selected[n]), center(selected[n + 1]), strokeWidth = 10f, cap = StrokeCap.Round)
        }
        for (i in 0 until 9) {
            val on = i in selected
            drawCircle(if (on) Cyan3 else Navy.copy(alpha = 0.25f), radius = if (on) cell * 0.16f else cell * 0.1f, center = center(i))
        }
    }
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
