package io.github.okenaron.dreamnail.sleeptimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import io.github.okenaron.dreamnail.MainActivity
import io.github.okenaron.dreamnail.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * 睡眠定时器的前台服务。
 *
 * 用户在 App 画面点"开始"时启动（这样才有 while-in-use 能力，Android 17 才允许后台调音量），
 * 一直常驻通知到结束：提前 [FADE_MILLIS] 开始缓降音量 → 到点发暂停键 → 恢复原音量。
 */
class SleepTimerService : Service() {

    companion object {
        private const val TAG = "SleepTimer"
        private const val ACTION_START = "io.github.okenaron.dreamnail.sleeptimer.START"
        private const val ACTION_CANCEL = "io.github.okenaron.dreamnail.sleeptimer.CANCEL"
        private const val EXTRA_DURATION_MILLIS = "duration_millis"
        private const val CHANNEL_ID = "sleep_timer"
        private const val NOTIFICATION_ID = 1
        private const val FADE_MILLIS = 30_000L
        private const val RESTORE_DELAY_MILLIS = 2_000L

        private val _endAt = MutableStateFlow<Long?>(null)

        /** 计时结束的时刻（`System.currentTimeMillis()` 基准），没在计时则为 null。 */
        val endAt: StateFlow<Long?> = _endAt.asStateFlow()

        fun start(context: Context, duration: Duration) {
            val intent = Intent(context, SleepTimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DURATION_MILLIS, duration.inWholeMilliseconds)
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            context.startService(cancelIntent(context))
        }

        private fun cancelIntent(context: Context) =
            Intent(context, SleepTimerService::class.java).setAction(ACTION_CANCEL)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer(intent.getLongExtra(EXTRA_DURATION_MILLIS, 0L))
            else -> {
                Log.d(TAG, "cancel")
                stopTimer()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        _endAt.value = null
        super.onDestroy()
    }

    private fun startTimer(durationMillis: Long) {
        val endAt = System.currentTimeMillis() + durationMillis
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(endAt), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        _endAt.value = endAt
        Log.d(TAG, "start: duration=${durationMillis}ms")

        timerJob?.cancel()
        timerJob = scope.launch {
            delay(endAt - FADE_MILLIS - System.currentTimeMillis())
            fadeOutAndPause(endAt)
            stopTimer()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _endAt.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 从现在到 [endAt] 把媒体音量逐格降到 0，然后暂停播放，最后恢复原音量（中途取消也会恢复）。 */
    private suspend fun fadeOutAndPause(endAt: Long) {
        val audioManager = getSystemService(AudioManager::class.java)
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        Log.d(TAG, "fade: originalVolume=$originalVolume")
        try {
            for (volume in originalVolume - 1 downTo 0) {
                // 剩余时间平均分给剩下的每一格，这样即使开始晚了也能在 endAt 降完
                val stepMillis = (endAt - System.currentTimeMillis()) / (volume + 1)
                delay(stepMillis)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                // Android 17 的后台限制会静默忽略调音量，这里读回来确认是否真的生效
                Log.d(TAG, "fade: set=$volume actual=${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}")
            }
            delay(endAt - System.currentTimeMillis())
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            Log.d(TAG, "pause sent")
            delay(RESTORE_DELAY_MILLIS)
        } finally {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            Log.d(TAG, "volume restored to $originalVolume")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sleep_timer_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(endAt: Long): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this, 0, cancelIntent(this), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.sleep_timer_notification_title))
            .setShowWhen(true)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(Notification.Action.Builder(null, getString(R.string.sleep_timer_cancel), cancel).build())
            .build()
    }
}
