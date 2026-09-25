package com.androtap.app

import android.app.Application
import com.androtap.app.data.db.AppDatabase

class AndroTapApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize Room Database eagerly
        AppDatabase.getInstance(this)
    }
}
