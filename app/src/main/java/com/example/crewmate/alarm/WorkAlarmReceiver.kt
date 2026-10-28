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
import java.util.Locale

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

        // 출무 알람인 경우: 출무 상태(적합성미완료 등)와 무관하게 실제 출무 시각이 이미 확인되어 있다면 알람 발동 즉시 취소
        if (cat == "출무" && !isTest) {
            try {
                val db = CrewMateDatabase.getInstance(context)
                val todayStr = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                val cached = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    db.attendanceStatusDao().getAttendanceStatus(todayStr)
                }
                if (cached != null && cached.jsonString.isNotBlank()) {
                    val parser = com.example.crewmate.data.remote.xcrew.AttendanceStatusParser()
                    val statuses = parser.parse(cached.jsonString)
                    val isAttended = statuses.any { 
                        val act = it.actualTime?.trim()
                        (!act.isNullOrBlank() && act != "-" && act != "null") ||
                                it.gtwcGubunCd?.trim() == "3" || it.gtwcGubunCd?.trim() == "6"
                    }
                    if (isAttended) {
                        Log.i("WorkAlarmReceiver", "Actual attendance already confirmed for today. Cancelling 출무 siren.")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.w("WorkAlarmReceiver", "Failed to check attendance status before siren", e)
            }
        }

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
