# AndroTap ⚡
> Ultra-fast, zero-friction expense tracker for Android inspired by Tappy.

AndroTap automatically opens a minimal floating panel whenever you complete a transaction in payment apps (Google Pay, PhonePe, Paytm, BHIM, CRED, Amazon Pay, etc.) or when you tap the floating quick-trigger dot.

---

## 🚀 Key Features

1. **Automatic Payment Detection (Accessibility Service)**:
   - Listens for payment completion screens across major financial apps.
   - Smart keyword detection (`Paid to`, `Payment Successful`, `Transaction Successful`, etc.).
   - Regex screen parsing to **auto-extract and prefill the exact amount** spent.
   - Built-in debouncing to prevent duplicate popups.

2. **Zero-Friction Floating Overlay Panel**:
   - Pops up on top of any app instantly without switching away.
   - **"How much did you spend?"**: Bold numeric input with quick increment chips (`+50`, `+100`, `+200`, `+500`, `+1000`).
   - **"Where did you spend?"**: One-tap category chips (🍔 Food, 🛍️ Shopping, 🚕 Travel, 🥦 Groceries, 💡 Bills, 🍿 Fun, 📦 Other).
   - Optional note description field.
   - Saves to local database with haptic feedback in less than 2 seconds.

3. **Draggable Floating Quick-Trigger Bubble**:
   - Minimalist dot anchored to the edge of the screen.
   - Tap anytime from any app to log cash or offline expenses instantly.

4. **Modern Jetpack Compose Dashboard**:
   - Real-time spend totals: **Today's Spent** and **This Month's Spent**.
   - Spending distribution across categories.
   - Clean recent transactions feed with source app tags (`Google Pay`, `PhonePe`, `Manual`).
   - One-tap permission guides & instant **"Test Expense Popup Now"** button.

5. **100% Offline & Private**:
   - Built with Room Database & Kotlin Coroutines.
   - No internet permissions, no external tracking, no accounts needed.

---

## 🏗️ Architecture

```
com.androtap.app/
├── AndroTapApplication.kt          # Application setup & DB initialization
├── data/
│   ├── model/Expense.kt            # Room Entity (id, amount, category, note, timestamp, sourceApp)
│   └── db/
│       ├── ExpenseDao.kt           # Reactive Room DAO with Kotlin Flow
│       └── AppDatabase.kt          # Room SQLite Database singleton
├── service/
│   └── ExpenseAccessibilityService.kt  # Detects payment app transitions, success nodes & amounts
├── overlay/
│   └── ExpenseOverlayManager.kt    # WindowManager floating overlay panel & draggable bubble
├── ui/
│   ├── MainActivity.kt             # Material 3 Jetpack Compose Dashboard & Settings
│   └── theme/                      # Typography, Color schemes, Dark Mode theme
└── util/
    ├── PermissionHelper.kt         # Accessibility & Overlay permission checks & deep intents
    └── PreferencesManager.kt       # Persistent toggles for bubble, auto-detection & currency
```

---

## 🛠️ How to Build and Run

### Option 1: Open in Android Studio
1. Open **Android Studio** (Hedgehog or newer recommended).
2. Choose **File > Open** and select the `/andro-tap` directory.
3. Allow Gradle to sync dependencies.
4. Connect an Android phone or launch an Android emulator (Android 8.0+ / API 26+).
5. Click **Run (`Shift + F10`)**.

### Option 2: Command Line (Gradle)
```bash
# Build Debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug
```
The output APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Setting Up on Your Device

1. **Launch AndroTap**:
   - Open the app. The home screen clearly indicates service status.
2. **Grant Overlay Permission**:
   - Tap the **Grant** button to allow *Display over other apps* (`SYSTEM_ALERT_WINDOW`).
3. **Enable Accessibility Service**:
   - Tap the **Enable** button next to *Accessibility Auto-Detection*.
   - In Android Settings > Accessibility > Downloaded Apps, locate **AndroTap** and toggle it **ON**.
4. **Try It Instantly**:
   - Tap the **"Test Expense Popup Now"** button on the home screen to test the floating panel immediately without needing to make a transaction!
