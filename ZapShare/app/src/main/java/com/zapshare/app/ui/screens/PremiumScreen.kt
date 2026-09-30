package com.zapshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zapshare.app.R
import com.zapshare.app.premium.Premium
import com.zapshare.app.ui.components.BrandIconBox
import com.zapshare.app.ui.components.GradientButton
import com.zapshare.app.ui.components.SectionCard
import com.zapshare.app.ui.components.ZapScaffold
import com.zapshare.app.ui.theme.BrandGradient
import com.zapshare.app.ui.theme.Cyan3
import com.zapshare.app.ui.theme.Navy
import kotlinx.coroutines.launch

@Composable
fun PremiumScreen(nav: NavController) {
    val ctx = LocalContext.current
    val premium by Premium.isPremium.collectAsState()
    val plans by Premium.plans.collectAsState()
    val message by Premium.message.collectAsState()
    var selected by remember { mutableIntStateOf(1) }   // yearly preselected

    ZapScaffold(stringResource(R.string.premium_title), onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(BrandGradient).padding(24.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFFFC83D)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.WorkspacePremium, null, tint = Navy, modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.premium_title), color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text(stringResource(R.string.premium_banner_sub), color = Navy, textAlign = TextAlign.Center)
                }
            }

            SectionCard {
                Feature(Icons.Filled.AllInclusive, stringResource(R.string.premium_feat_size))
                Feature(Icons.Filled.Block, stringResource(R.string.premium_feat_ads))
                Feature(Icons.Filled.Lock, stringResource(R.string.premium_feat_safebox))
                Feature(Icons.Filled.Favorite, stringResource(R.string.premium_feat_support))
            }

            if (premium) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Cyan3, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.you_are_premium), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                plans.forEachIndexed { i, p ->
                    val title = when (p.id) {
                        Premium.SUB_MONTHLY -> stringResource(R.string.plan_monthly)
                        Premium.SUB_YEARLY -> stringResource(R.string.plan_yearly)
                        else -> stringResource(R.string.plan_lifetime)
                    }
                    val isSel = selected == i
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, if (isSel) Cyan3 else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                            .clickable { selected = i }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(isSel, { selected = i })
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = MaterialTheme.typography.titleMedium)
                                if (p.id == Premium.SUB_YEARLY) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.best_value), color = Navy, style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFC83D)).padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text(p.price ?: p.fallbackPrice, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                GradientButton(stringResource(R.string.continue_btn), {
                    Premium.message.value = null
                    ctx.findActivity()?.let { Premium.buy(it, plans[selected]) }
                }, Modifier.fillMaxWidth())
                TextButton(onClick = { appScope.launch { Premium.restore() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.restore_purchases))
                }
                Text("Subscriptions renew automatically and can be cancelled anytime in Google Play → Subscriptions.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandIconBox(icon, 38.dp, 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Shown instead of a Premium-only screen (e.g. Safebox) for free users. */
@Composable
fun PremiumGate(title: String, message: String, nav: NavController) {
    ZapScaffold(title, onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            BrandIconBox(Icons.Filled.Lock, 88.dp, 48.dp)
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.premium_banner_sub), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            GradientButton(stringResource(R.string.go_premium), { nav.navigate(com.zapshare.app.ui.navigation.Routes.PREMIUM) },
                Modifier.fillMaxWidth(), icon = Icons.Filled.WorkspacePremium)
        }
    }
}
