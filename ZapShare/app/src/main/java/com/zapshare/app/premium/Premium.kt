package com.zapshare.app.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.zapshare.app.data.Prefs
import com.zapshare.app.data.ShareItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * ZapShare Premium via Google Play Billing.
 *
 * Create these products in Play Console → Monetize (IDs must match exactly):
 *  - Subscriptions:  zapshare_premium_monthly, zapshare_premium_yearly  (one base plan each)
 *  - In-app product: zapshare_premium_lifetime
 *
 * Premium unlocks: files larger than 100 MB, no ads, Safebox.
 */
object Premium {
    const val FREE_FILE_LIMIT = 100L * 1024 * 1024   // 100 MB per file for free users
    private const val AD_UNLOCK_MS = 24L * 60 * 60 * 1000

    const val SUB_MONTHLY = "zapshare_premium_monthly"
    const val SUB_YEARLY = "zapshare_premium_yearly"
    const val LIFETIME = "zapshare_premium_lifetime"
    private val ALL = setOf(SUB_MONTHLY, SUB_YEARLY, LIFETIME)

    data class Plan(
        val id: String,
        val isSubscription: Boolean,
        val fallbackPrice: String,
        val price: String? = null,
        val details: ProductDetails? = null
    )

    val isPremium = MutableStateFlow(false)
    val plans = MutableStateFlow(
        listOf(
            Plan(SUB_MONTHLY, true, "$1.99"),
            Plan(SUB_YEARLY, true, "$9.99"),
            Plan(LIFETIME, false, "$19.99"),
        )
    )
    /** One-line message for the paywall (errors, "store unavailable", etc). */
    val message = MutableStateFlow<String?>(null)
    val storeReady = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: BillingClient? = null

    fun init(ctx: Context) {
        refresh()
        client = BillingClient.newBuilder(ctx.applicationContext)
            .setListener { result, purchases ->
                when {
                    result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null ->
                        scope.launch { handle(purchases) }
                    result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> message.value = null
                    result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { restore() }
                    else -> message.value = "Purchase failed (${result.debugMessage.ifEmpty { result.responseCode.toString() }})"
                }
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        if (c.isReady) return
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    storeReady.value = true
                    scope.launch { loadProducts(); restore() }
                } else {
                    storeReady.value = false
                    message.value = "Google Play store is not available on this device"
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnect is handled by enableAutoServiceReconnection().
                storeReady.value = false
            }
        })
    }

    private suspend fun loadProducts() {
        val c = client ?: return
        fun product(id: String, type: String) =
            QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()
        val subs = c.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(
                listOf(product(SUB_MONTHLY, BillingClient.ProductType.SUBS), product(SUB_YEARLY, BillingClient.ProductType.SUBS))
            ).build()
        )
        val inapp = c.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product(LIFETIME, BillingClient.ProductType.INAPP))).build()
        )
        val byId = (subs.productDetailsList.orEmpty() + inapp.productDetailsList.orEmpty()).associateBy { it.productId }
        plans.value = plans.value.map { p ->
            val d = byId[p.id] ?: return@map p.copy(details = null, price = null)
            p.copy(details = d, price = priceOf(d, p.isSubscription))
        }
    }

    private fun priceOf(details: ProductDetails, subscription: Boolean): String? {
        return if (subscription) {
            details.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        } else {
            details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                ?: details.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    private fun offerToken(details: ProductDetails, subscription: Boolean): String? {
        val token = if (subscription) {
            details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        } else {
            details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
                ?: details.oneTimePurchaseOfferDetails?.offerToken
        }
        return token?.takeIf { it.isNotEmpty() }
    }

    /** Re-checks what the user owns (also used by "Restore purchases"). */
    suspend fun restore() {
        val c = client ?: return
        if (!c.isReady) { connect(); return }
        val subs = c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        val inapp = c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (subs.billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
            inapp.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        val owned = (subs.purchasesList + inapp.purchasesList).filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.any { id -> id in ALL }
        }
        if (owned.isEmpty()) {
            setPurchased(false)
        } else if (owned.any { acknowledge(it) }) {
            setPurchased(true)
        }
    }

    private suspend fun handle(purchases: List<Purchase>) {
        purchases.forEach { p ->
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.any { it in ALL }) {
                if (acknowledge(p)) {
                    setPurchased(true)
                    message.value = "Welcome to Premium! 🎉"
                }
            } else if (p.purchaseState == Purchase.PurchaseState.PENDING) {
                message.value = "Payment pending. Premium unlocks when it completes."
            }
        }
    }

    /** Acknowledge within 3 days, or Google refunds the purchase. */
    private suspend fun acknowledge(p: Purchase): Boolean {
        if (p.isAcknowledged) return true
        val result = client?.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        ) ?: return false
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun buy(activity: Activity, plan: Plan) {
        val c = client
        val d = plan.details
        val token = d?.let { offerToken(it, plan.isSubscription) }
        if (c == null || d == null || token == null) {
            message.value = "This plan isn't available yet. Install the app from Google Play and set up the products in Play Console."
            if (c?.isReady != true) connect()
            return
        }
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(d)
                        .setOfferToken(token)
                        .build()
                )
            )
            .build()
        val result = c.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            message.value = "Purchase failed (${result.debugMessage.ifEmpty { result.responseCode.toString() }})"
        }
    }

    private fun setPurchased(v: Boolean) {
        Prefs.premiumPurchased = v
        refresh()
    }

    /** Rewarded-ad unlock. Kept separate from a Play purchase so restore cannot clear it. */
    fun grantFromAd() {
        Prefs.premiumAdUntil = System.currentTimeMillis() + AD_UNLOCK_MS
        refresh()
    }

    fun refresh() {
        isPremium.value = adsHidden()
    }

    fun adsHidden(): Boolean =
        Prefs.premiumPurchased || Prefs.premiumDebug || System.currentTimeMillis() < Prefs.premiumAdUntil

    /** Debug builds only: lets you test premium screens without a Play account. */
    fun setDebugPremium(v: Boolean) {
        Prefs.premiumDebug = v
        refresh()
    }

    /** Free users can only send files up to 100 MB each. */
    fun blocksSend(items: Collection<ShareItem>): Boolean =
        !isPremium.value && items.any { it.size > FREE_FILE_LIMIT }
}
