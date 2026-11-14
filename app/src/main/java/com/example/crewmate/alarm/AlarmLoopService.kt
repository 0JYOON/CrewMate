package com.example.crewmate.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.crewmate.MainActivity
import com.example.crewmate.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmLoopService : Service() {

    private var playerManager: AlarmPlayerManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        playerManager = AlarmPlayerManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val ruleTitle = intent?.getStringExtra("ruleTitle") ?: "오늘의 근무 알람"
        val cat = intent?.getStringExtra("cat") ?: "운전"
        val trainNo = intent?.getStringExtra("trainNo") ?: "-"
        val dptStn = intent?.getStringExtra("dptStn") ?: "-"
        val formattedTime = intent?.getStringExtra("formattedTime") ?: "-"
        val soundModeName = intent?.getStringExtra("soundMode") ?: AlarmSoundMode.SOUND.name
        val soundMode = AlarmSoundMode.entries.find { it.name == soundModeName } ?: AlarmSoundMode.SOUND

        // 1. Quiet Foreground Service Notification for Android OS
        val quietServiceNotif = buildQuietServiceNotification(ruleTitle)
        startForeground(SERVICE_NOTIFICATION_ID, quietServiceNotif)

        // 2. Pure, Unblocked Single Alarm Notification FORWARDED DIRECTLY TO PHONE AND WATCH
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val watchNotification = buildWatchAlarmNotification(ruleTitle, cat, trainNo, dptStn, formattedTime, 0)
        notificationManager.notify(WATCH_NOTIFICATION_ID, watchNotification)

        // 3. Audio & Vibration Player
        playerManager?.startPlaybackAndVibration(soundMode)

        // 4. Loop to continuously refresh smartwatch vibration signal every 2 seconds until dismissed!
        loopJob?.cancel()
        var pulseCount = 0
        loopJob = serviceScope.launch {
            while (isActive) {
                delay(2000L)
                pulseCount++
                val refreshedWatchNotification = buildWatchAlarmNotification(ruleTitle, cat, trainNo, dptStn, formattedTime, pulseCount)
                runCatching { notificationManager.notify(WATCH_NOTIFICATION_ID, refreshedWatchNotification) }
            }
        }

        return START_STICKY
    }

    private fun buildQuietServiceNotification(ruleTitle: String): Notification {
        val channelId = "crewmate_alarm_service_quiet"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "정밀 알람 서비스 백그라운드",
                NotificationManager.IMPORTANCE_MIN
            )
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(com.example.crewmate.R.mipmap.ic_launcher)
            .setContentTitle("⏰ CrewMate 알람 서비스 ($ruleTitle)")
            .setContentText("오늘의 근무 정밀 알람 서비스가 실행 중입니다.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun buildWatchAlarmNotification(
        ruleTitle: String,
        cat: String,
        trainNo: String,
        dptStn: String,
        formattedTime: String,
        pulseCount: Int = 0
    ): Notification {
        // Dynamic Channel ID to bypass Wear OS vibration suppression (ス팸 진동 억제 시스템 회피)
        val channelId = "crewmate_alarm_watch_pulse_$pulseCount"
        val heavyVibrationPattern = longArrayOf(0, 1200, 200, 1200, 200, 1200, 200, 1200)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete previous pulse channel to keep settings clean
            if (pulseCount > 0) {
                runCatching { notificationManager.deleteNotificationChannel("crewmate_alarm_watch_pulse_${pulseCount - 1}") }
            } else {
                runCatching { notificationManager.deleteNotificationChannel("crewmate_alarm_watch_alert_v4") }
            }

            val channel = NotificationChannel(
                channelId,
                "오늘의 근무 정밀 알람 (워치 진동 루프)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "갤럭시 워치 손목 무한 연속 진동 수신 전용 알람 채널"
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(alarmSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = heavyVibrationPattern
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 1) Action Intent: Stop Alarm Service on Dismiss Button
        val stopServiceIntent = Intent(this, AlarmLoopService::class.java).apply {
            action = ACTION_STOP
        }
        val stopServicePendingIntent = PendingIntent.getService(
            this,
            WATCH_NOTIFICATION_ID,
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2) Content Intent: Open MainActivity AND stop alarm service when notification card is tapped
        val openAppAndStopIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("target_route", Routes.TodayWork)
            putExtra("stopAlarmService", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            WATCH_NOTIFICATION_ID + 1,
            openAppAndStopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pulseSuffix = if (pulseCount > 0) " " else ""
        val notificationTitle = "⏰ $formattedTime 출발 [$trainNo $cat]$pulseSuffix"
        val notificationText = "🚉 $dptStn 출발 ($ruleTitle)"
        val bigTextContent = "⏰ 출발시각: $formattedTime\n🚆 열차번호: $trainNo ($cat)\n🚉 출발역: $dptStn\n📋 적용규칙: $ruleTitle"

        val wearableExtender = NotificationCompat.WearableExtender()

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(com.example.crewmate.R.mipmap.ic_launcher)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextContent))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
            .setVibrate(heavyVibrationPattern)
            .extend(wearableExtender)
            .setDeleteIntent(stopServicePendingIntent) // Swiping away notification stops alarm!
            .setContentIntent(contentPendingIntent)    // Tapping notification opens app and stops alarm!
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🔕 알람 해제", stopServicePendingIntent) // Button dismisses alarm!
            .build()
    }

    private fun stopAlarm() {
        try {
            loopJob?.cancel()
            loopJob = null
            playerManager?.stop()
            playerManager = null
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(WATCH_NOTIFICATION_ID)
            notificationManager.cancel(SERVICE_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm in AlarmLoopService", e)
        }
    }

    override fun onDestroy() {
        stopAlarm()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmLoopService"
        const val SERVICE_NOTIFICATION_ID = 8800
        const val WATCH_NOTIFICATION_ID = 8899

        const val ACTION_START = "com.example.crewmate.alarm.ACTION_LOOP_START"
        const val ACTION_STOP = "com.example.crewmate.alarm.ACTION_LOOP_STOP"

        fun start(
            context: Context,
            ruleTitle: String,
            cat: String,
            trainNo: String,
            dptStn: String,
            formattedTime: String,
            soundMode: AlarmSoundMode
        ) {
            val intent = Intent(context, AlarmLoopService::class.java).apply {
                action = ACTION_START
                putExtra("ruleTitle", ruleTitle)
                putExtra("cat", cat)
                putExtra("trainNo", trainNo)
                putExtra("dptStn", dptStn)
                putExtra("formattedTime", formattedTime)
                putExtra("soundMode", soundMode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmLoopService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
