package com.search.browser

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

/**
 * Wraps Google Play Billing for the "Buy me a coffee" supporter tiers.
 * Products are consumable (so a supporter can give again), and consuming
 * implicitly acknowledges the purchase. A persistent "supporter" flag is
 * kept in Settings so the badge/thank-you survive the consume.
 */
class BillingManager(
    private val activity: Activity,
    private val onPrices: (Map<String, String>) -> Unit,
    private val onSupporterChanged: (Boolean) -> Unit
) {
    private val productIds = listOf("supporter_coffee", "supporter_snack", "supporter_meal")
    private var details: Map<String, ProductDetails> = emptyMap()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private val client = BillingClient.newBuilder(activity)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryOwned()
                }
            }
            override fun onBillingServiceDisconnected() { /* reconnect lazily on next action */ }
        })
    }

    private fun queryProducts() {
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val map = HashMap<String, ProductDetails>()
                val prices = HashMap<String, String>()
                queryResult.productDetailsList.forEach { pd ->
                    map[pd.productId] = pd
                    pd.oneTimePurchaseOfferDetails?.formattedPrice?.let { prices[pd.productId] = it }
                }
                details = map
                activity.runOnUiThread { onPrices(prices) }
            }
        }
    }

    fun launch(productId: String) {
        val pd = details[productId] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        // Mark supporter (persist), then consume so they can give again later.
        Settings.setBool(activity, Settings.IS_SUPPORTER, true)
        activity.runOnUiThread { onSupporterChanged(true) }
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.consumeAsync(consumeParams) { _, _ -> /* consumed (implicitly acknowledged) */ }
    }

    private fun queryOwned() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    fun isSupporter(context: Context): Boolean =
        Settings.getBool(context, Settings.IS_SUPPORTER, false)

    fun end() { client.endConnection() }
}
