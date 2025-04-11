package com.example.cosyvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.cosyvoice.ui.MainScreen
import com.example.cosyvoice.ui.theme.CosyVoiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosyVoiceTheme {
                MainScreen()
            }
        }
    }
}