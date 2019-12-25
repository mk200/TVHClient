package org.tvheadend.tvhclient.util.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.SkuType
import com.android.billingclient.api.Purchase.PurchasesResult
import org.tvheadend.tvhclient.repository.AppRepository
import timber.log.Timber

class BillingManager(context: Context, private val billingUpdatesListener: BillingUpdatesListener, val appRepository: AppRepository) : PurchasesUpdatedListener {

    private var billingClient: BillingClient = BillingClient.newBuilder(context).enablePendingPurchases().setListener(this).build()
    private var isServiceConnected = false

    init {
        startServiceConnection(Runnable { billingUpdatesListener.onBillingClientSetupFinished() })
    }

    private fun startServiceConnection(runnable: Runnable) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.d("Billing setup finished successfully")
                    isServiceConnected = true
                    runnable.run()
                } else {
                    Timber.d("Billing setup did not finish successfully")
                }
            }

            override fun onBillingServiceDisconnected() {
                Timber.d("Billing service has been disconnected")
                isServiceConnected = false
            }
        })
    }

    /**
     * If you do not acknowledge a purchase, the Google Play Store will provide a refund to the
     * users within a few days of the transaction. Therefore you have to implement
     * BillingClient.acknowledgePurchaseAsync inside your app.
     */
    private fun handlePurchase(purchase: Purchase) {
        Timber.d("Handling purchased item ${purchase.sku}")
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Grant the item to the user, and then acknowledge the purchase

            if (purchase.sku == UNLOCKER) {
                Timber.d("Activating purchased item $UNLOCKER")
                appRepository.setIsUnlocked(true)
            }

            // Acknowledge the purchase if it hasn't already been acknowledged.
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                billingClient.acknowledgePurchase(params) { billingResult ->
                    when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.OK -> {
                            Timber.d("Successfully activated purchased item ${purchase.sku}")
                        }
                        else -> Timber.d("Acknowledgement of purchase response is ${billingResult.debugMessage}")
                    }
                }
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            // Here you can confirm to the user that they've started the pending
            // purchase, and to complete it, they should follow instructions that
            // are given to them. You can also choose to remind the user in the
            // future to complete the purchase if you detect that it is still
            // pending.
        }
    }

    private fun executeServiceRequest(runnable: Runnable) {
        if (isServiceConnected) {
            runnable.run()
        } else {
            // If the billing service disconnects, try to reconnect once.
            startServiceConnection(runnable)
        }
    }

    fun queryPurchases() {
        Timber.d("Querying purchases")
        val queryToExecute = Runnable {
            Timber.d("Adding available in-app items")
            val purchasesResult = billingClient.queryPurchases(SkuType.INAPP)
            if (areSubscriptionsSupported()) {
                Timber.d("Subscriptions are supported")
                val subscriptionResult = billingClient.queryPurchases(SkuType.SUBS)
                if (subscriptionResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.d("Adding available subscriptions")
                    purchasesResult.purchasesList.addAll(subscriptionResult.purchasesList)
                } else { // Handle any error response codes.
                    Timber.d("Error while querying for available subscriptions")
                }
            } else if (purchasesResult.responseCode == BillingClient.BillingResponseCode.OK) { // Skip subscription purchases query as they are not supported.
                Timber.d("Subscriptions are not supported")
            } else { // Handle any other error response codes.
                Timber.d("Error checking for supported features")
            }
            onQueryPurchasesFinished(purchasesResult)
        }
        executeServiceRequest(queryToExecute)
    }

    private fun areSubscriptionsSupported(): Boolean {
        val result = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Timber.w("Got error response ${result.responseCode} while checking if subscriptions are supported")
        }
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun onQueryPurchasesFinished(result: PurchasesResult) {
        // Have we been disposed of in the meantime? If so, or bad result code, then quit
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Timber.d("Billing result code (${result.responseCode}) was bad – quitting")
            return
        }
        Timber.d("Query inventory was successful.")
        // Update the UI and purchases inventory with new list of purchases mPurchases.clear();
        onPurchasesUpdated(result.billingResult, result.purchasesList)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Timber.d("Purchase update was successful")
                if (purchases != null) {
                    billingUpdatesListener.onPurchaseSuccessful(purchases)
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Timber.d("User cancelled the purchase flow – skipping")
                billingUpdatesListener.onPurchaseCancelled()
            }
            else -> {
                Timber.d("Error ${billingResult.responseCode} occurred during purchase")
                billingUpdatesListener.onPurchaseError(billingResult.responseCode)
            }
        }
    }

    private fun querySkuDetailsOfUnlockerItem(): SkuDetails? {
        Timber.d("Loading sku details for $UNLOCKER")
        val skuList = ArrayList<String>()
        skuList.add(UNLOCKER)

        val params = SkuDetailsParams.newBuilder()
                .setSkusList(skuList)
                .setType(SkuType.INAPP)
                .build()

        var skuDetailsResult: SkuDetails? = null


        billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    Timber.d("Received details of purchase item $UNLOCKER")
                    if (skuDetailsList.orEmpty().isNotEmpty()) {
                        skuDetailsList.forEach {
                            if (it.sku == UNLOCKER) skuDetailsResult = it
                        }
                    }
                }
                else -> Timber.e(billingResult.debugMessage)
            }
        }

        Timber.d("Returning sku details for $UNLOCKER")
        return skuDetailsResult
    }

    fun initiatePurchaseFlow(activity: Activity?, skuId: String?) {
        Timber.d("Initiating purchase flow for $skuId")

        val skuDetails = querySkuDetailsOfUnlockerItem()
        val purchaseFlowRequest = Runnable {
            val mParams: BillingFlowParams = BillingFlowParams.newBuilder()
                    .setSkuDetails(skuDetails)
                    .build()
            billingClient.launchBillingFlow(activity, mParams)
        }
        executeServiceRequest(purchaseFlowRequest)
    }

    companion object {
        // Product id for the in-app billing item to unlock the application
        const val UNLOCKER = "unlocker"
    }
}