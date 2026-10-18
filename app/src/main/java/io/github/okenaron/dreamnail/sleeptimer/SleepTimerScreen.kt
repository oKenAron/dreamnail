package io.github.okenaron.dreamnail.sleeptimer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.okenaron.dreamnail.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

private const val MIN_MINUTES = 1f
private const val MAX_MINUTES = 120f

@Composable
fun SleepTimerScreen(modifier: Modifier = Modifier) {
    val endAt by SleepTimerService.endAt.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val end = endAt) {
            null -> TimerSetup()
            else -> TimerRunning(end)
        }
    }
}

@Composable
private fun TimerSetup() {
    val context = LocalContext.current
    var minutes by rememberSaveable { mutableFloatStateOf(30f) }
    val startTimer = { SleepTimerService.start(context, minutes.roundToInt().minutes) }
    // Android 13 以后发通知要用户同意。等用户回答完再启动，否则通知在授权前就发出去了，授权后也不会显示。
    // 拒绝了计时照样能跑，只是看不到通知。
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startTimer()
    }

    Text(
        text = stringResource(R.string.sleep_timer_minutes, minutes.roundToInt()),
        style = MaterialTheme.typography.displayMedium,
    )
    Slider(
        value = minutes,
        onValueChange = { minutes = it },
        valueRange = MIN_MINUTES..MAX_MINUTES,
    )
    Button(onClick = {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            startTimer()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }) {
        Text(stringResource(R.string.sleep_timer_start))
    }
}

@Composable
private fun TimerRunning(endAt: Long) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        text = formatRemaining(endAt - now),
        style = MaterialTheme.typography.displayLarge,
    )
    OutlinedButton(onClick = { SleepTimerService.cancel(context) }) {
        Text(stringResource(R.string.sleep_timer_cancel))
    }
}
