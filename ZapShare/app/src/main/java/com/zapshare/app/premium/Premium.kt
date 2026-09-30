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
        isPremium.value = Prefs.premiumPurchased || Prefs.premiumDebug
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

            override fun onBillingServiceDisconnected() { storeReady.value = false }
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
        ).productDetailsList.orEmpty()
        val inapp = c.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product(LIFETIME, BillingClient.ProductType.INAPP))).build()
        ).productDetailsList.orEmpty()
        val byId = (subs + inapp).associateBy { it.productId }
        plans.value = plans.value.map { p ->
            val d = byId[p.id] ?: return@map p
            val price = if (p.isSubscription)
                d.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            else d.oneTimePurchaseOfferDetails?.formattedPrice
            p.copy(details = d, price = price)
        }
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
        owned.forEach { acknowledge(it) }
        setPurchased(owned.isNotEmpty())
    }

    private suspend fun handle(purchases: List<Purchase>) {
        purchases.forEach { p ->
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.any { it in ALL }) {
                acknowledge(p)
                setPurchased(true)
                message.value = "Welcome to Premium! 🎉"
            } else if (p.purchaseState == Purchase.PurchaseState.PENDING) {
                message.value = "Payment pending. Premium unlocks when it completes."
            }
        }
    }

    private suspend fun acknowledge(p: Purchase) {
        if (p.isAcknowledged) return
        client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
    }

    fun buy(activity: Activity, plan: Plan) {
        val c = client
        val d = plan.details
        if (c == null || !c.isReady || d == null) {
            message.value = "This plan isn't available yet. Install the app from Google Play and set up the products in Play Console."
            connect()
            return
        }
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        if (plan.isSubscription) {
            val token = d.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
            builder.setOfferToken(token)
        }
        c.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(builder.build())).build())
    }

    private fun setPurchased(v: Boolean) {
        Prefs.premiumPurchased = v
        isPremium.value = v || Prefs.premiumDebug
    }

    /** Debug builds only: lets you test premium screens without a Play account. */
    fun setDebugPremium(v: Boolean) {
        Prefs.premiumDebug = v
        isPremium.value = v || Prefs.premiumPurchased
    }

    /** Free users can only send files up to 100 MB each. */
    fun blocksSend(items: Collection<ShareItem>): Boolean =
        !isPremium.value && items.any { it.size > FREE_FILE_LIMIT }
}
