package com.androtap.app.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("androtap_prefs", Context.MODE_PRIVATE)

    var isAutoDetectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DETECT, value).apply()

    var isFloatingBubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_BUBBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_BUBBLE, value).apply()

    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY_SYMBOL, "₹") ?: "₹"
        set(value) = prefs.edit().putString(KEY_CURRENCY_SYMBOL, value).apply()

    companion object {
        private const val KEY_AUTO_DETECT = "key_auto_detect"
        private const val KEY_FLOATING_BUBBLE = "key_floating_bubble"
        private const val KEY_CURRENCY_SYMBOL = "key_currency_symbol"
    }
}
