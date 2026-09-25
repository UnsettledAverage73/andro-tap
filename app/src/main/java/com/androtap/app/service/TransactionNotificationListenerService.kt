package com.androtap.app.service

import android.app.Notification
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.androtap.app.overlay.ExpenseOverlayManager
import com.androtap.app.util.PreferencesManager
import java.util.regex.Pattern

class TransactionNotificationListenerService : NotificationListenerService() {

    private lateinit var overlayManager: ExpenseOverlayManager
    private lateinit var preferencesManager: PreferencesManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastTriggerTime: Long = 0
    private val DEBOUNCE_INTERVAL_MS = 6000L

    // Payment apps & SMS apps packages
    private val recognizedPackages = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.dreamplug.androidapp" to "CRED",
        "in.org.npci.upiapp" to "BHIM",
        "com.google.android.apps.messaging" to "Bank SMS",
        "com.samsung.android.messaging" to "Bank SMS",
        "com.revolut.revolut" to "Revolut",
        "com.squareup.cash" to "Cash App",
        "com.paypal.android.p2pmobile" to "PayPal"
    )

    private val transactionKeywords = listOf(
        "paid", "sent", "debited", "spent", "purchase of", "transfer to", "payment of"
    )

    private val amountPattern = Pattern.compile(
        "(?:[₹$€£]|rs\\.?|inr)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    companion object {
        @Volatile
        var isConnected = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        overlayManager = ExpenseOverlayManager(this)
        preferencesManager = PreferencesManager(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !preferencesManager.isAutoDetectEnabled) return

        val packageName = sbn.packageName ?: return
        val appLabel = recognizedPackages[packageName] ?: return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val combinedContent = "$title $text $bigText".lowercase()

        // Check if notification describes an outflow/expense transaction
        val isExpense = transactionKeywords.any { kw -> combinedContent.contains(kw) }
        // Exclude incoming deposits or credits
        val isCredit = combinedContent.contains("credited") || combinedContent.contains("received")

        if (isExpense && !isCredit) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime < DEBOUNCE_INTERVAL_MS) {
                return
            }

            // Extract amount
            var extractedAmount: Double? = null
            val matcher = amountPattern.matcher("$title $text $bigText")
            if (matcher.find()) {
                val clean = matcher.group(1)?.replace(",", "")
                extractedAmount = clean?.toDoubleOrNull()
            }

            lastTriggerTime = now

            // Trigger overlay
            mainHandler.postDelayed({
                overlayManager.showExpensePanel(
                    detectedApp = appLabel,
                    suggestedAmount = extractedAmount
                )
            }, 600)
        }
    }
}
