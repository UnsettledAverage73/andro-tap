package com.androtap.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.androtap.app.data.db.AppDatabase
import com.androtap.app.data.model.Expense
import com.androtap.app.overlay.ExpenseOverlayManager
import com.androtap.app.service.ExpenseAccessibilityService
import com.androtap.app.ui.theme.*
import com.androtap.app.util.PermissionHelper
import com.androtap.app.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)
        database = AppDatabase.getInstance(this)

        setContent {
            AndroTapTheme {
                MainScreen(
                    preferencesManager = preferencesManager,
                    database = database,
                    onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(this) },
                    onOpenOverlaySettings = { PermissionHelper.openOverlaySettings(this) },
                    onOpenNotificationSettings = { PermissionHelper.openNotificationListenerSettings(this) },
                    onRequestBatteryExemption = { PermissionHelper.requestIgnoreBatteryOptimization(this) },
                    onOpenAppDetails = { PermissionHelper.openAppDetailsSettings(this) },
                    onTestOverlay = {
                        if (!PermissionHelper.canDrawOverlays(this)) {
                            Toast.makeText(this, "Please grant Overlay Permission first", Toast.LENGTH_SHORT).show()
                            PermissionHelper.openOverlaySettings(this)
                        } else {
                            val overlayManager = ExpenseOverlayManager(this)
                            overlayManager.showExpensePanel(detectedApp = "Test Demo")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    preferencesManager: PreferencesManager,
    database: AppDatabase,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onTestOverlay: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe Room data
    val expenses by database.expenseDao().getAllExpenses().collectAsState(initial = emptyList())

    // Permissions state
    var isAccessibilityEnabled by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var isOverlayGranted by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var isNotificationListenerEnabled by remember { mutableStateOf(PermissionHelper.isNotificationListenerEnabled(context)) }
    var isBatteryExempt by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }

    // Dialog state
    var showCompatibilityGuide by remember { mutableStateOf(false) }

    // Settings state
    var autoDetect by remember { mutableStateOf(preferencesManager.isAutoDetectEnabled) }
    var floatingBubble by remember { mutableStateOf(preferencesManager.isFloatingBubbleEnabled) }
    val currency = preferencesManager.currencySymbol

    // Auto refresh permissions state on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)
                isOverlayGranted = PermissionHelper.canDrawOverlays(context)
                isNotificationListenerEnabled = PermissionHelper.isNotificationListenerEnabled(context)
                isBatteryExempt = PermissionHelper.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Calculations
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val monthStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val todayTotal = expenses.filter { it.timestamp >= todayStart }.sumOf { it.amount }
    val monthTotal = expenses.filter { it.timestamp >= monthStart }.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmeraldGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currency,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AndroTap",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Zero-Friction Expense Tracker",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showCompatibilityGuide = true }) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = "Device Setup Guide",
                            tint = EmeraldGreenLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onTestOverlay,
                containerColor = EmeraldGreen,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Quick Add")
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission & Service Status Cards
            item {
                ServiceStatusSection(
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isOverlayGranted = isOverlayGranted,
                    isNotificationListenerEnabled = isNotificationListenerEnabled,
                    isBatteryExempt = isBatteryExempt,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                    onOpenGuide = { showCompatibilityGuide = true }
                )
            }

            // Quick Test & Preferences
            item {
                QuickControlsCard(
                    autoDetect = autoDetect,
                    onAutoDetectChange = {
                        autoDetect = it
                        preferencesManager.isAutoDetectEnabled = it
                    },
                    floatingBubble = floatingBubble,
                    onFloatingBubbleChange = {
                        floatingBubble = it
                        preferencesManager.isFloatingBubbleEnabled = it
                        ExpenseAccessibilityService.instance?.updateFloatingBubbleState(it)
                    },
                    onTestOverlay = onTestOverlay
                )
            }

            // Summary Totals Card
            item {
                SpendingSummaryCard(
                    currency = currency,
                    todayTotal = todayTotal,
                    monthTotal = monthTotal,
                    count = expenses.size
                )
            }

            // Category Distribution Horizontal Chips
            if (expenses.isNotEmpty()) {
                item {
                    CategoryBreakdownRow(currency = currency, expenses = expenses)
                }
            }

            // Recent Expenses Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Expenses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (expenses.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.expenseDao().clearAll()
                                    }
                                }
                            }
                        ) {
                            Text("Clear All", color = AccentRed, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Expense Items or Empty State
            if (expenses.isEmpty()) {
                item {
                    EmptyExpensesPlaceholder(onTestClick = onTestOverlay)
                }
            } else {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseItemRow(
                        expense = expense,
                        currency = currency,
                        onDelete = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    database.expenseDao().delete(expense)
                                }
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showCompatibilityGuide) {
        DeviceCompatibilityModal(
            onDismiss = { showCompatibilityGuide = false },
            onOpenAppDetails = onOpenAppDetails
        )
    }
}

@Composable
fun ServiceStatusSection(
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    isNotificationListenerEnabled: Boolean,
    isBatteryExempt: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // 1. Accessibility Service Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAccessibilityEnabled) EmeraldGreen else AccentRed)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Accessibility Auto-Detection",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isAccessibilityEnabled) "Active • Instant screen detection" else "Tap Enable (See guide if restricted)",
                            color = if (isAccessibilityEnabled) EmeraldGreenLight else TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (!isAccessibilityEnabled) {
                    Button(
                        onClick = onOpenAccessibility,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Overlay Permission Status (if not granted)
        if (!isOverlayGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Display Over Other Apps",
                            fontWeight = FontWeight.SemiBold,
                            color = AccentAmber,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Required for the floating panel popup",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = onOpenOverlaySettings,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Grant", fontSize = 12.sp, color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Notification Listener (High reliability backup)
        if (!isNotificationListenerEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Listener (Backup)",
                            fontWeight = FontWeight.SemiBold,
                            color = AccentBlue,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Detects payment push alerts & bank SMS",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = onOpenNotificationSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Battery Optimization Exemption
        if (!isBatteryExempt) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Disable Battery Saver",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Prevents OEM systems from killing the service",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onRequestBatteryExemption,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Ignore", fontSize = 12.sp, color = EmeraldGreenLight)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickControlsCard(
    autoDetect: Boolean,
    onAutoDetectChange: (Boolean) -> Unit,
    floatingBubble: Boolean,
    onFloatingBubbleChange: (Boolean) -> Unit,
    onTestOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Auto-popup on Payments",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "GPay, PhonePe, Paytm, etc.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = autoDetect,
                    onCheckedChange = onAutoDetectChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = SurfaceCardLight)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Floating Quick-Add Bubble",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Minimal draggable dot on screen edge",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = floatingBubble,
                    onCheckedChange = onFloatingBubbleChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Button
            OutlinedButton(
                onClick = onTestOverlay,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Expense Popup Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SpendingSummaryCard(
    currency: String,
    todayTotal: Double,
    monthTotal: Double,
    count: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "TODAY'S SPENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$currency${formatAmount(todayTotal)}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = "THIS MONTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$currency${formatAmount(monthTotal)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(text = "$count transactions", fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun CategoryBreakdownRow(currency: String, expenses: List<Expense>) {
    val categoryTotals = expenses.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categoryTotals.toList()) { (category, total) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = getCategoryEmoji(category), fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$category: $currency${formatAmount(total)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseItemRow(
    expense: Expense,
    currency: String,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCardLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = getCategoryEmoji(expense.category), fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = expense.category,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkBackground)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = expense.sourceApp,
                                fontSize = 10.sp,
                                color = EmeraldGreenLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (expense.note.isNotEmpty()) {
                        Text(
                            text = expense.note,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = dateFormat.format(Date(expense.timestamp)),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "- $currency${formatAmount(expense.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AccentAmber
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyExpensesPlaceholder(onTestClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "⚡", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Zero Friction Expense Logging",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "When you pay via Google Pay, PhonePe, or Paytm, the panel pops open automatically. Or tap below to try it now!",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onTestClick,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Try Expense Popup", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeviceCompatibilityModal(
    onDismiss: () -> Unit,
    onOpenAppDetails: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = EmeraldGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Device Setup Guide", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "If settings are restricted or your phone kills background apps, follow these steps:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Divider(color = SurfaceCardLight)

                // Android 13/14 Restricted Settings
                Column {
                    Text(
                        text = "🔒 Android 13 & 14 Restricted Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AccentAmber
                    )
                    Text(
                        text = "If Accessibility is greyed out: Open App Info > Tap 3 dots (⋮) top right > Tap 'Allow restricted settings'.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                // Xiaomi / HyperOS / MIUI
                Column {
                    Text(
                        text = "📱 Xiaomi / HyperOS / MIUI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AccentBlue
                    )
                    Text(
                        text = "1. Enable 'Autostart'\n2. Other permissions > Allow 'Display pop-up windows in background'\n3. Battery Saver > Set to 'No restrictions'.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                // Samsung
                Column {
                    Text(
                        text = "📱 Samsung One UI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = EmeraldGreenLight
                    )
                    Text(
                        text = "Go to Settings > Battery > Background usage limits > Add AndroTap to 'Never sleeping apps'.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onOpenAppDetails()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("Open App Info")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", color = TextSecondary)
            }
        },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(20.dp)
    )
}

fun formatAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        amount.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", amount)
    }
}

fun getCategoryEmoji(category: String): String {
    return when (category.lowercase()) {
        "food" -> "🍔"
        "shopping" -> "🛍️"
        "transport", "travel" -> "🚕"
        "groceries", "grocery" -> "🥦"
        "bills" -> "💡"
        "entertainment", "fun" -> "🍿"
        "coffee" -> "☕"
        else -> "📦"
    }
}
