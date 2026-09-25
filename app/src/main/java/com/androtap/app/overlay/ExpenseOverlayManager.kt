package com.androtap.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.androtap.app.R
import com.androtap.app.data.db.AppDatabase
import com.androtap.app.data.model.Expense
import com.androtap.app.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExpenseOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val preferencesManager = PreferencesManager(context)
    private val database = AppDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private var overlayView: View? = null
    private var bubbleView: View? = null
    private var selectedCategory: String = "Food"

    /**
     * Display the full Expense Panel overlay
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showExpensePanel(
        detectedApp: String = "Quick Log",
        suggestedAmount: Double? = null
    ) {
        if (overlayView != null) {
            // Already showing, just update
            return
        }

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_expense_overlay, null)
        overlayView = view

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // View elements
        val tvSource = view.findViewById<TextView>(R.id.tv_detected_source)
        val tvCurrency = view.findViewById<TextView>(R.id.tv_currency_symbol)
        val etAmount = view.findViewById<EditText>(R.id.et_expense_amount)
        val etNote = view.findViewById<EditText>(R.id.et_expense_note)
        val btnClose = view.findViewById<ImageView>(R.id.btn_close_overlay)
        val btnSave = view.findViewById<Button>(R.id.btn_save_expense)
        val overlayRoot = view.findViewById<View>(R.id.overlay_root)

        // Set Currency & Source
        tvCurrency.text = preferencesManager.currencySymbol
        tvSource.text = "⚡ $detectedApp"

        // Prefill suggested amount if detected from screen
        if (suggestedAmount != null && suggestedAmount > 0) {
            val formatted = if (suggestedAmount % 1.0 == 0.0) {
                suggestedAmount.toLong().toString()
            } else {
                String.format("%.2f", suggestedAmount)
            }
            etAmount.setText(formatted)
            etAmount.setSelection(etAmount.text.length)
        }

        // Setup quick increment buttons
        fun addToAmount(addValue: Double) {
            val current = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val total = current + addValue
            val formatted = if (total % 1.0 == 0.0) total.toLong().toString() else String.format("%.2f", total)
            etAmount.setText(formatted)
            etAmount.setSelection(etAmount.text.length)
        }

        view.findViewById<Button>(R.id.btn_add_50).setOnClickListener { addToAmount(50.0) }
        view.findViewById<Button>(R.id.btn_add_100).setOnClickListener { addToAmount(100.0) }
        view.findViewById<Button>(R.id.btn_add_200).setOnClickListener { addToAmount(200.0) }
        view.findViewById<Button>(R.id.btn_add_500).setOnClickListener { addToAmount(500.0) }
        view.findViewById<Button>(R.id.btn_add_1000).setOnClickListener { addToAmount(1000.0) }

        // Setup Category selection
        val categoryChips = mapOf(
            "Food" to view.findViewById<TextView>(R.id.chip_food),
            "Shopping" to view.findViewById<TextView>(R.id.chip_shopping),
            "Transport" to view.findViewById<TextView>(R.id.chip_transport),
            "Groceries" to view.findViewById<TextView>(R.id.chip_groceries),
            "Bills" to view.findViewById<TextView>(R.id.chip_bills),
            "Entertainment" to view.findViewById<TextView>(R.id.chip_entertainment),
            "Other" to view.findViewById<TextView>(R.id.chip_other)
        )

        fun selectChip(cat: String) {
            selectedCategory = cat
            categoryChips.forEach { (name, chipView) ->
                if (name == cat) {
                    chipView.setBackgroundResource(R.drawable.bg_chip_selected)
                    chipView.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    chipView.setBackgroundResource(R.drawable.bg_chip_unselected)
                    chipView.setTextColor(0xFFCBD5E1.toInt())
                }
            }
        }

        categoryChips.forEach { (cat, chipView) ->
            chipView.setOnClickListener { selectChip(cat) }
        }

        // Close on background tap or close button
        btnClose.setOnClickListener { dismissOverlay() }
        overlayRoot.setOnClickListener { dismissOverlay() }

        // Prevent dialog interior click from dismissing
        view.findViewById<View>(R.id.layout_categories).setOnClickListener { /* consume */ }

        // Save Action
        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString().trim()
            val amount = amountText.toDoubleOrNull()

            if (amount == null || amount <= 0) {
                etAmount.error = "Please enter an amount"
                return@setOnClickListener
            }

            val note = etNote.text.toString().trim()
            val expense = Expense(
                amount = amount,
                category = selectedCategory,
                note = note,
                timestamp = System.currentTimeMillis(),
                sourceApp = detectedApp
            )

            scope.launch {
                withContext(Dispatchers.IO) {
                    database.expenseDao().insert(expense)
                }
                Toast.makeText(
                    context,
                    "Saved ${preferencesManager.currencySymbol}$amount in $selectedCategory!",
                    Toast.LENGTH_SHORT
                ).show()
                dismissOverlay()
            }
        }

        try {
            windowManager.addView(view, params)
            // Request keyboard focus
            etAmount.requestFocus()
            Handler(Looper.getMainLooper()).postDelayed({
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(etAmount, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        } catch (e: Exception) {
            e.printStackTrace()
            overlayView = null
        }
    }

    /**
     * Dismiss the full expense panel
     */
    fun dismissOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    /**
     * Show draggable floating quick-trigger bubble
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingBubble() {
        if (bubbleView != null || !preferencesManager.isFloatingBubbleEnabled) {
            return
        }

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_floating_bubble, null)
        bubbleView = view

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 350
        }

        // Handle dragging and tap
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        showExpensePanel("Quick Tap")
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
            bubbleView = null
        }
    }

    /**
     * Hide draggable floating quick-trigger bubble
     */
    fun hideFloatingBubble() {
        bubbleView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            bubbleView = null
        }
    }

    fun isOverlayShowing(): Boolean = overlayView != null
}
