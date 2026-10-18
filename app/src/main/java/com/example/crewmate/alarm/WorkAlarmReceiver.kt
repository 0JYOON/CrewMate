package com.example.crewmate.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.crewmate.MainActivity
import com.example.crewmate.navigation.Routes
import com.example.crewmate.database.AlarmHistoryEntity
import com.example.crewmate.database.CrewMateDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WorkAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CrewMate:AlarmWakeLock"
            )
            runCatching { wakeLock?.acquire(10_000L) }

        val ruleTitle = intent.getStringExtra("ruleTitle") ?: intent.getStringExtra("title") ?: "오늘의 근무 알람"
        val cat = intent.getStringExtra("cat") ?: "운전"
        val trainNo = intent.getStringExtra("trainNo") ?: "-"
        val dptStn = intent.getStringExtra("dptStn") ?: "-"
        val dptTimeStr = intent.getStringExtra("dptTimeStr") ?: "-"
        val isTest = intent.getBooleanExtra("isTest", false)

        val settingsStore = WorkAlarmSettingsStore(context)
        val settings = settingsStore.loadSettings()

        if (!settings.alarmEnabled && !isTest) return

        val formattedTime = if (dptTimeStr.length == 4 && dptTimeStr.all(Char::isDigit)) {
            "${dptTimeStr.substring(0, 2)}:${dptTimeStr.substring(2, 4)}"
        } else {
            dptTimeStr
        }

        // Delegate single notification creation & looping audio/vibration directly to AlarmLoopService
        try {
            AlarmLoopService.start(
                context = context,
                ruleTitle = ruleTitle,
                cat = cat,
                trainNo = trainNo,
                dptStn = dptStn,
                formattedTime = formattedTime,
                soundMode = settings.soundMode
            )
        } catch (e: Exception) {
            Log.e("WorkAlarmReceiver", "Failed to start AlarmLoopService", e)
        }

        // Save alarm history into Room DB (14 days retention)
        val firedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val thresholdDateStr = LocalDateTime.now().minusDays(14).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val db = CrewMateDatabase.getInstance(context)
                val historyDao = db.alarmHistoryDao()
                historyDao.insertHistory(
                    AlarmHistoryEntity(
                        firedAt = firedAt,
                        title = ruleTitle,
                        message = "[$trainNo] $dptStn 출발 ($formattedTime)",
                        trainNo = trainNo,
                        dptStnNm = dptStn
                    )
                )
                historyDao.cleanupOldHistories(thresholdDateStr)
            } catch (e: Exception) {
                Log.e("WorkAlarmReceiver", "Failed to insert alarm history", e)
            } finally {
                pendingResult.finish()
            }
        }
        } catch (e: Exception) {
            Log.e("WorkAlarmReceiver", "Unhandled exception in WorkAlarmReceiver onReceive", e)
        }
    }
}
