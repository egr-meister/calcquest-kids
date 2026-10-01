package com.calcquest.kids.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.calcquest.kids.ui.common.SimpleTopBar
import com.calcquest.kids.ui.theme.MapColors

private val privacySections = listOf(
    "Everything stays on this device" to
        "CalcQuest Kids works completely offline. Calculations, calculator history, quest progress and settings are stored only in the app's private storage on this device.",
    "No accounts, ads or tracking" to
        "There are no accounts, advertising, analytics, payments, cloud sync, chat or external links. The app does not request internet access or any runtime permission, and it does not use the camera, microphone, location or contacts.",
    "No backup or transfer" to
        "App data is excluded from cloud backup and from device-to-device transfer. If the app is uninstalled, its data is removed with it.",
    "What is stored" to
        "The latest 50 calculator results, quest questions and answers for recent attempts (up to 20 finished attempts per level and difficulty), which levels are completed, and parent settings.",
    "Removing data" to
        "Parents can clear calculator history, reset quest progress, or clear all local data in Parent settings. Clearing all data restores the default settings.",
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MapColors.Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { SimpleTopBar(title = "Privacy", onBack = onBack, backLabel = "Back to parent settings") },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                privacySections.forEach { (title, body) ->
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(body, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
