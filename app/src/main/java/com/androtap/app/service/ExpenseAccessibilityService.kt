package com.androtap.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.androtap.app.overlay.ExpenseOverlayManager
import com.androtap.app.util.PreferencesManager
import java.util.regex.Pattern

class ExpenseAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: ExpenseOverlayManager
    private lateinit var preferencesManager: PreferencesManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastTriggerTime: Long = 0
    private var lastDetectedPackage: String? = null
    private val DEBOUNCE_INTERVAL_MS = 8000L // 8 seconds debounce between auto-popups

    // Known payment & banking app package names and readable display labels
    private val paymentApps = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.dreamplug.androidapp" to "CRED",
        "in.org.npci.upiapp" to "BHIM",
        "com.samsung.android.spay" to "Samsung Pay",
        "com.paypal.android.p2pmobile" to "PayPal",
        "com.squareup.cash" to "Cash App",
        "com.venmo" to "Venmo",
        "com.revolut.revolut" to "Revolut",
        "com.transferwise.android" to "Wise"
    )

    // Keywords indicating successful payment on screen
    private val successKeywords = listOf(
        "paid to", "payment successful", "successful", "transaction successful",
        "sent successfully", "money sent", "payment done", "bill paid",
        "transfer successful", "completed", "upi ref no"
    )

    // Regex pattern to extract payment amount (e.g. ₹ 450, Rs. 1,200.50, $25.00)
    private val amountPattern = Pattern.compile(
        "(?:[₹$€£]|rs\\.?)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    companion object {
        @Volatile
        var instance: ExpenseAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlayManager = ExpenseOverlayManager(this)
        preferencesManager = PreferencesManager(this)

        if (preferencesManager.isFloatingBubbleEnabled) {
            overlayManager.showFloatingBubble()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !preferencesManager.isAutoDetectEnabled) return

        val packageName = event.packageName?.toString() ?: return

        // Check if current event is from a known payment app
        val appLabel = paymentApps[packageName]
        if (appLabel != null) {
            lastDetectedPackage = packageName

            // Check if window content changed or state changed
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            ) {
                inspectScreenAndTriggerIfNeeded(appLabel)
            }
        }
    }

    private fun inspectScreenAndTriggerIfNeeded(appLabel: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < DEBOUNCE_INTERVAL_MS) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        val textCollector = mutableListOf<String>()
        traverseNodes(rootNode, textCollector)

        // Check for success keywords
        val foundSuccess = textCollector.any { text ->
            val lower = text.lowercase()
            successKeywords.any { kw -> lower.contains(kw) }
        }

        if (foundSuccess) {
            lastTriggerTime = currentTime

            // Attempt to extract amount from the nodes
            var detectedAmount: Double? = null
            for (text in textCollector) {
                val matcher = amountPattern.matcher(text)
                if (matcher.find()) {
                    val amountStr = matcher.group(1)?.replace(",", "")
                    val parsed = amountStr?.toDoubleOrNull()
                    if (parsed != null && parsed > 0) {
                        detectedAmount = parsed
                        break
                    }
                }
            }

            // Trigger overlay on main thread
            mainHandler.postDelayed({
                overlayManager.showExpensePanel(
                    detectedApp = appLabel,
                    suggestedAmount = detectedAmount
                )
            }, 500)
        }
    }

    private fun traverseNodes(node: AccessibilityNodeInfo?, collector: MutableList<String>) {
        if (node == null) return
        node.text?.let { collector.add(it.toString()) }
        node.contentDescription?.let { collector.add(it.toString()) }

        for (i in 0 until node.childCount) {
            traverseNodes(node.getChild(i), collector)
        }
    }

    /**
     * Manually trigger the expense popup (e.g. from test button in MainActivity)
     */
    fun showManualOverlay(source: String = "Quick Log") {
        mainHandler.post {
            overlayManager.showExpensePanel(detectedApp = source)
        }
    }

    fun updateFloatingBubbleState(enabled: Boolean) {
        if (enabled) {
            overlayManager.showFloatingBubble()
        } else {
            overlayManager.hideFloatingBubble()
        }
    }

    override fun onInterrupt() {
        // Handle accessibility interruption
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.dismissOverlay()
        overlayManager.hideFloatingBubble()
        instance = null
    }
}
