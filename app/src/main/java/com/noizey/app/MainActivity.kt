package com.noizey.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.noizey.app.ui.NoizeyApp
import com.noizey.app.ui.theme.NoizeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NoizeyTheme {
                NoizeyApp()
            }
        }
    }
}
