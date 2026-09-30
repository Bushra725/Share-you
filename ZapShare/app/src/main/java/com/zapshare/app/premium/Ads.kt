package com.zapshare.app.premium

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.zapshare.app.R
import com.zapshare.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Ads {
    /** Google's public TEST ids. Replace with your own AdMob unit ids before release. */
    const val BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var lastShown = 0L
    private const val COOLDOWN_MS = 20_000L

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { MobileAds.initialize(app) { preload(app) } }
        }
    }

    fun preload(ctx: Context) {
        if (Prefs.premiumPurchased || interstitial != null || loading) return
        loading = true
        InterstitialAd.load(ctx, INTERSTITIAL_UNIT_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                loading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitial = null
                loading = false
            }
        })
    }

    /** Full-screen test ad. Hidden after a real Premium purchase, and not more than once every 20 seconds. */
    fun show(activity: Activity) {
        if (Prefs.premiumPurchased || activity.isFinishing) {
            preload(activity)
            return
        }
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        if (ad == null || now - lastShown < COOLDOWN_MS) {
            preload(activity)
            return
        }
        interstitial = null
        lastShown = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { preload(activity) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { preload(activity) }
        }
        ad.show(activity)
    }
}

/** Banner test ad with a "Remove ads" link. Hidden after a real Premium purchase. */
@Composable
fun AdBanner(onRemoveAds: () -> Unit, modifier: Modifier = Modifier) {
    if (Prefs.premiumPurchased) return
    val widthDp = LocalConfiguration.current.screenWidthDp
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                    adUnitId = Ads.BANNER_UNIT_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() }
        )
        Text(
            stringResource(R.string.remove_ads),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRemoveAds).padding(6.dp)
        )
    }
}
