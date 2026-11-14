package com.example.crewmate.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crewmate.MainActivity
import com.example.crewmate.navigation.Routes
import com.example.crewmate.ui.theme.CrewMateTheme

class FullScreenAlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching { setShowWhenLocked(true) }
            runCatching { setTurnScreenOn(true) }
            runCatching {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val ruleTitle = intent.getStringExtra("ruleTitle") ?: intent.getStringExtra("title") ?: "오늘의 근무 알람"
        val cat = intent.getStringExtra("cat") ?: "운전"
        val trainNo = intent.getStringExtra("trainNo") ?: "-"
        val dptStn = intent.getStringExtra("dptStn") ?: "-"
        val dptTimeStr = intent.getStringExtra("dptTimeStr") ?: "-"

        val settingsStore = WorkAlarmSettingsStore(this)
        val settings = settingsStore.loadSettings()

        val prefs = getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val themeModeName = prefs.getString("app_theme", com.example.crewmate.ui.theme.AppThemeMode.SYSTEM.name) ?: com.example.crewmate.ui.theme.AppThemeMode.SYSTEM.name
        val appThemeMode = com.example.crewmate.ui.theme.AppThemeMode.entries.find { it.name == themeModeName } ?: com.example.crewmate.ui.theme.AppThemeMode.SYSTEM

        startAudioVibration(settings.soundMode)

        val notificationId = intent.getIntExtra("notificationId", -1)

        fun cancelNotification() {
            if (notificationId != -1) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
        }

        setContent {
            CrewMateTheme(appThemeMode = appThemeMode) {
                FullScreenAlarmContent(
                    ruleTitle = ruleTitle,
                    cat = cat,
                    trainNo = trainNo,
                    dptStn = dptStn,
                    dptTimeStr = dptTimeStr,
                    onConfirm = {
                        stopAudioVibration()
                        cancelNotification()
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            putExtra("target_route", Routes.TodayWork)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onCloseWithoutNavigation = {
                        stopAudioVibration()
                        cancelNotification()
                        finish()
                    }
                )
            }
        }
    }

    private fun startAudioVibration(soundMode: AlarmSoundMode) {
        when (soundMode) {
            AlarmSoundMode.SOUND -> {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                try {
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()

                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(this@FullScreenAlarmActivity, alarmUri)
                        setAudioAttributes(audioAttributes)
                        isLooping = true
                        prepare()
                        start()
                    }
                } catch (_: Exception) {
                    ringtone = RingtoneManager.getRingtone(this, alarmUri)?.apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            isLooping = true
                        }
                        play()
                    }
                }
                startVibration()
            }
            AlarmSoundMode.VIBRATION -> {
                startVibration()
            }
            AlarmSoundMode.SILENT -> {
                // Silent
            }
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 300, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("FullScreenAlarmActivity", "Failed to start vibration", e)
        }
    }

    private fun stopAudioVibration() {
        try {
            AlarmLoopService.stop(this)
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            ringtone?.stop()
            ringtone = null
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {
            // Ignored
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioVibration()
    }
}

@Composable
private fun FullScreenAlarmContent(
    ruleTitle: String,
    cat: String,
    trainNo: String,
    dptStn: String,
    dptTimeStr: String,
    onConfirm: () -> Unit,
    onCloseWithoutNavigation: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(10.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "🔔 오늘의 근무 알람",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Text(
                        text = "✕",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = onCloseWithoutNavigation)
                            .padding(8.dp)
                    )
                }

                // Emphasized Departure Time (Most Important Information)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dptTimeStr,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "출발 예정 시각",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("열차 번호 / 항목", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$trainNo ($cat)", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("출발역", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dptStn, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("적용 알람 규칙", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(ruleTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp)
                        }
                    }
                }
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("알람 해제 / 오늘의 근무 확인", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
