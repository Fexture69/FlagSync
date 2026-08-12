 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Warm up SharedPreferences on a background thread before setContent,
        // avoiding a blocking read on the Main thread during entry animations.
        Thread {
            getSharedPreferences("flag_sync_prefs", MODE_PRIVATE).all
        }.start()

        setContent {
            FlagSyncApp()
        }
    }
}