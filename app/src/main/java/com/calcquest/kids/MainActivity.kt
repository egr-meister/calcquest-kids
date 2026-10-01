package com.calcquest.kids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.calcquest.kids.ui.CalcQuestRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Launch screen is dismissed as soon as the first frame is ready (no artificial delay).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CalcQuestApp).container
        setContent {
            CalcQuestRoot(container)
        }
    }
}
