package com.example.crewmate.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.ZoneId

object WorkAlarmScheduler {
    private const val TAG = "WorkAlarmScheduler"

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleAlarms(context: Context, items: List<WorkAlarmItem>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = java.time.LocalDateTime.now()

        // Cancel previous alarms
        for (i in 0..15) {
            val intent = Intent(context, WorkAlarmReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }

        val futureItems = items.filter { it.triggerDateTime.isAfter(now) }
        Log.i(TAG, "scheduling ${futureItems.size} future alarms out of ${items.size} total")

        futureItems.forEachIndexed { index, item ->
            val epochMilli = item.triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = Intent(context, WorkAlarmReceiver::class.java).apply {
                putExtra("ruleTitle", item.ruleTitle)
                putExtra("cat", item.cat)
                putExtra("title", item.title)
                putExtra("message", item.message)
                putExtra("trainNo", item.trainNo)
                putExtra("dptStn", item.dptStnNm)
                putExtra("dptTimeStr", item.dptTimeStr)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val showIntent = Intent(context, com.example.crewmate.MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                0,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(epochMilli, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                }
            }
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleTestAlarm(context: Context, leadSeconds: Int = 5) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val testTime = java.time.LocalDateTime.now().plusSeconds(leadSeconds.toLong())
        val epochMilli = testTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val timeLabel = if (leadSeconds >= 60) "${leadSeconds / 60}분" else "${leadSeconds}초"

        val intent = Intent(context, WorkAlarmReceiver::class.java).apply {
            putExtra("ruleTitle", "테스트 알람 ($timeLabel 후)")
            putExtra("cat", "운전")
            putExtra("title", "테스트 알람")
            putExtra("message", "[TEST-4501] 안산 출발 (08:00)")
            putExtra("trainNo", "TEST-4501")
            putExtra("dptStn", "안산")
            putExtra("dptTimeStr", "08:00")
            putExtra("isTest", true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, com.example.crewmate.MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(epochMilli, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.i(TAG, "Test alarm scheduled via setAlarmClock for $leadSeconds seconds from now")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule test alarm via setAlarmClock, falling back", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
            }
        }
        Log.i(TAG, "Test alarm scheduled for $leadSeconds seconds from now")
    }
}
