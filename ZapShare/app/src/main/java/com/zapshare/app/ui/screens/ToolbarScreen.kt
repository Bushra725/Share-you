package com.zapshare.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.zapshare.app.data.Prefs
import com.zapshare.app.toolbar.ShareToolbar
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.ZapScaffold

@Composable
fun ToolbarScreen(nav: NavController) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(Prefs.toolbarEnabled) }
    var style by remember { mutableStateOf(Prefs.toolbarStyle) }
    var askOpen by remember { mutableStateOf(false) }
    var chooseStyle by remember { mutableStateOf(false) }
    var pendingStyle by remember { mutableStateOf(style) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Prefs.toolbarEnabled = true
            enabled = true
            ShareToolbar.show(ctx)
            askOpen = true
        } else {
            enabled = false
            Prefs.toolbarEnabled = false
            Toast.makeText(ctx, "Allow notifications to show the toolbar", Toast.LENGTH_SHORT).show()
        }
    }
    fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    fun turnOn() {
        if (!notificationsAllowed()) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        Prefs.toolbarEnabled = true
        enabled = true
        ShareToolbar.show(ctx)
        askOpen = true
    }
    ZapScaffold("Toolbar", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Toolbar", style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(enabled, { on ->
                        if (on) turnOn() else {
                            enabled = false
                            Prefs.toolbarEnabled = false
                            ShareToolbar.hide(ctx)
                        }
                    })
                }
            }
            SectionCard(Modifier.clickable { pendingStyle = style; chooseStyle = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Toolbar Style", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(if (style == "color") "Color" else "White", color = MaterialTheme.colorScheme.primary)
                }
            }
            SectionCard {
                Text("Toolbar Function", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Enable to display core feature entrances.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FunctionChip(Icons.Filled.SwapVert, "Transfer", Color(0xFF3B82F6))
                    FunctionChip(Icons.Filled.Explore, "Discover", Color(0xFF8B5CF6))
                    FunctionChip(Icons.Filled.Folder, "Files", Color(0xFFF59E0B))
                    FunctionChip(Icons.Filled.CleaningServices, "Clean", Color(0xFFEF4444))
                }
            }
        }
    }
    if (askOpen) AlertDialog(
        onDismissRequest = { askOpen = false },
        title = { Text("Toolbar") },
        text = { Text("Add the toolbar to your notification bar and access various convenient functions with just one click.") },
        confirmButton = {
            TextButton(onClick = {
                askOpen = false
                ShareToolbar.show(ctx)
                ShareToolbar.expand(ctx)
            }) { Text("OPEN") }
        }
    )
    if (chooseStyle) AlertDialog(
        onDismissRequest = { chooseStyle = false },
        title = { Text("Choose Style") },
        text = {
            Column {
                StyleChoice("White", pendingStyle == "white") { pendingStyle = "white" }
                StyleChoice("Color", pendingStyle == "color") { pendingStyle = "color" }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                style = pendingStyle
                Prefs.toolbarStyle = pendingStyle
                chooseStyle = false
                if (enabled) ShareToolbar.show(ctx)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { chooseStyle = false }) { Text("CANCEL") } }
    )
}

@Composable
private fun StyleChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(label)
    }
}

@Composable
private fun FunctionChip(icon: ImageVector, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(44.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
