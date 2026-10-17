package io.github.okenaron.dreamnail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import io.github.okenaron.dreamnail.sleeptimer.SleepTimerScreen
import io.github.okenaron.dreamnail.ui.theme.DreamNailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DreamNailTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SleepTimerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
