package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.firebase.configureAppCheck
import com.example.ui.TasmeeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or ViewModel creation
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge and render RTL Tasmee UI
        enableEdgeToEdge()
        setContent {
            TasmeeApp()
        }
    }
}
