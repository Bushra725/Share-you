package com.zapshare.app.premium

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.delay
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.zapshare.app.R

object Ads {
    const val BANNER_UNIT_ID = "ca-app-pub-9297250663056879/6703538283"
    const val INTERSTITIAL_UNIT_ID = "ca-app-pub-9297250663056879/2828205592"
    const val REWARDED_UNIT_ID = "ca-app-pub-9297250663056879/2672292927"
    const val APP_OPEN_UNIT_ID = "ca-app-pub-9297250663056879/2764293273"
    const val NATIVE_UNIT_ID = "ca-app-pub-9297250663056879/7000735969"

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var appOpen: AppOpenAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var loadingAppOpen = false
    private var showingFullScreen = false
    private var lastShown = 0L
    private var resumed: Activity? = null
    private var rewardWait: RewardRequest? = null
    private const val COOLDOWN_MS = 30_000L

    private fun adsOff() = Premium.adsHidden()

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        if (app is Application) watchForeground(app)
        MobileAds.initialize(app) {
            preload(app)
            loadRewarded(app)
            loadAppOpen(app)
        }
    }

    private fun watchForeground(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumed = activity }
            override fun onActivityPaused(activity: Activity) { if (resumed == activity) resumed = null }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                resumed?.let { showAppOpen(it) }
            }
        })
    }

    fun preload(ctx: Context) {
        if (adsOff() || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(ctx, INTERSTITIAL_UNIT_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                loadingInterstitial = false
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitial = null
                loadingInterstitial = false
            }
        })
    }

    private fun loadRewarded(ctx: Context) {
        if (adsOff() || rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(ctx, REWARDED_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                loadingRewarded = false
                val wait = rewardWait
                if (wait != null && !wait.activity.isFinishing) {
                    rewardWait = null
                    presentRewarded(wait.activity, ad, wait.onReward, wait.onUnavailable)
                } else {
                    rewarded = ad
                }
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                rewarded = null
                loadingRewarded = false
                val wait = rewardWait
                rewardWait = null
                wait?.activity?.runOnUiThread { wait.onUnavailable() }
            }
        })
    }

    private fun loadAppOpen(ctx: Context) {
        if (adsOff() || appOpen != null || loadingAppOpen) return
        loadingAppOpen = true
        AppOpenAd.load(ctx, APP_OPEN_UNIT_ID, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpen = ad
                loadingAppOpen = false
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                appOpen = null
                loadingAppOpen = false
            }
        })
    }

    private fun cooledDown(): Boolean = SystemClock.elapsedRealtime() - lastShown >= COOLDOWN_MS

    /** Full-screen ad when a transfer starts or the user leaves a feature. */
    fun show(activity: Activity) {
        if (adsOff() || activity.isFinishing || showingFullScreen) {
            preload(activity)
            return
        }
        val ad = interstitial
        if (ad == null || !cooledDown()) {
            preload(activity)
            return
        }
        interstitial = null
        showingFullScreen = true
        lastShown = SystemClock.elapsedRealtime()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showingFullScreen = false
                runCatching { activity.window.decorView.post { activity.window.decorView.requestLayout() } }
                preload(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showingFullScreen = false
                preload(activity)
            }
        }
        ad.show(activity)
    }

    /** Rewarded video. [onReward] runs only after the viewer earns the reward. */
    fun showRewarded(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit = {}) {
        if (activity.isFinishing) return
        if (adsOff()) {
            onReward()
            return
        }
        val ad = rewarded
        if (ad == null) {
            rewardWait = RewardRequest(activity, onReward, onUnavailable)
            if (!loadingRewarded) loadRewarded(activity)
            return
        }
        presentRewarded(activity, ad, onReward, onUnavailable)
    }

    private fun presentRewarded(activity: Activity, ad: RewardedAd, onReward: () -> Unit, onUnavailable: () -> Unit) {
        rewarded = null
        showingFullScreen = true
        lastShown = SystemClock.elapsedRealtime()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showingFullScreen = false
                loadRewarded(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showingFullScreen = false
                loadRewarded(activity)
                activity.runOnUiThread { onUnavailable() }
            }
        }
        ad.show(activity) { activity.runOnUiThread { onReward() } }
    }

    private class RewardRequest(
        val activity: Activity,
        val onReward: () -> Unit,
        val onUnavailable: () -> Unit
    )

    private fun showAppOpen(activity: Activity) {
        if (adsOff() || activity.isFinishing || showingFullScreen) return
        val ad = appOpen
        if (ad == null || !cooledDown()) {
            loadAppOpen(activity)
            return
        }
        appOpen = null
        showingFullScreen = true
        lastShown = SystemClock.elapsedRealtime()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showingFullScreen = false
                loadAppOpen(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showingFullScreen = false
                loadAppOpen(activity)
            }
        }
        ad.show(activity)
    }
}

/** Banner ad with a link to Premium. Hidden after a real Premium purchase. */
@Composable
fun AdBanner(onRemoveAds: () -> Unit, modifier: Modifier = Modifier) {
    val premium by Premium.isPremium.collectAsState()
    if (premium) return
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

/** Native ad for the home screen. */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val premium by Premium.isPremium.collectAsState()
    if (premium) return
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val loader = AdLoader.Builder(context, Ads.NATIVE_UNIT_ID)
            .forNativeAd { nativeAd = it }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) { failed = true }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose { nativeAd?.destroy() }
    }
    LaunchedEffect(nativeAd) {
        if (nativeAd == null) {
            delay(3500)
            if (nativeAd == null) failed = true
        }
    }
    if (failed) {
        AdBanner(onRemoveAds = {}, modifier = modifier)
        return
    }
    val ad = nativeAd
    if (ad == null) {
        Text(
            "Ad",
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        factory = { ctx ->
            val view = NativeAdView(ctx)
            val column = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
            }
            val headline = TextView(ctx).apply {
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            }
            val body = TextView(ctx).apply { textSize = 13f }
            val cta = Button(ctx).apply { gravity = Gravity.CENTER }
            column.addView(headline)
            column.addView(body)
            column.addView(cta)
            view.addView(column)
            view.headlineView = headline
            view.bodyView = body
            view.callToActionView = cta
            (view.headlineView as TextView).text = ad.headline
            (view.bodyView as TextView).text = ad.body ?: ""
            (view.callToActionView as Button).text = ad.callToAction ?: "Open"
            view.setNativeAd(ad)
            view
        }
    )
}
