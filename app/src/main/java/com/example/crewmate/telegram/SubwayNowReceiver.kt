package com.example.crewmate.telegram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubwayNowReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DISPATCH_TRACK = "com.example.crewmate.telegram.ACTION_DISPATCH_TRACK"
        const val EXTRA_TRAIN_NO = "extra_train_no"
        const val EXTRA_TARGET_STATION = "extra_target_station"
        const val EXTRA_INTERVAL = "extra_interval"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_START_DATETIME_ISO = "extra_start_datetime_iso"
        const val EXTRA_RAW_COMMAND = "extra_raw_command"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISPATCH_TRACK) return

        val trainNo = intent.getStringExtra(EXTRA_TRAIN_NO).orEmpty()
        val targetStation = intent.getStringExtra(EXTRA_TARGET_STATION).orEmpty()
        val interval = intent.getIntExtra(EXTRA_INTERVAL, 3)
        val startTime = intent.getStringExtra(EXTRA_START_TIME).orEmpty()
        val startDateTimeIso = intent.getStringExtra(EXTRA_START_DATETIME_ISO)
        val rawCommand = intent.getStringExtra(EXTRA_RAW_COMMAND).orEmpty()

        if (trainNo.isBlank() || rawCommand.isBlank()) return

        val todayKey = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.ROOT))
        if (SubwayNowSettings.isTrainStopped(context, trainNo, todayKey)) {
            android.util.Log.i("SubwayNowReceiver", "$trainNo 열차는 사용자에 의해 중지되어 자동 발송을 건너뜁니다.")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SubwayNowManager.dispatchScheduledCommand(
                    context = context,
                    trainNo = trainNo,
                    targetStation = targetStation,
                    interval = interval,
                    startTime = startTime,
                    rawCommand = rawCommand,
                    startDateTimeIso = startDateTimeIso
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
