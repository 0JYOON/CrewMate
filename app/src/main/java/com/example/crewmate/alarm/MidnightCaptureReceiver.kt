package com.example.crewmate.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.Log
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.schedule.categorizeSegments
import com.example.crewmate.schedule.CategorizedSegment
import com.example.crewmate.util.DiaCaptureOption
import com.example.crewmate.util.ScreenshotHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MidnightCaptureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("midnight_auto_capture", false)
        if (!enabled) return

        Log.i("MidnightCaptureReceiver", "Running midnight auto capture...")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()
                val todayStr = today.format(DateTimeFormatter.BASIC_ISO_DATE)
                val monthStr = today.format(DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT))
                val db = CrewMateDatabase.getInstance(context)

                // 1. Look up today's work schedule from monthly schedules
                val monthSchedules = db.workScheduleDao().getSchedulesForMonth(monthStr)
                val todaySchedules = monthSchedules.filter {
                    it.date == todayStr && !it.diaNo.isNullOrBlank() && it.diaNo != "S" && it.diaNo != "휴" && it.diaNo != "연차" && it.diaNo != "~"
                }

                // 2. Check if yesterday had an overnight continuation DIA extending into today
                val yesterday = today.minusDays(1)
                val yesterdayStr = yesterday.format(DateTimeFormatter.BASIC_ISO_DATE)
                val yesterdayMonthStr = yesterday.format(DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT))
                val yesterdaySchedules = db.workScheduleDao().getSchedulesForMonth(yesterdayMonthStr).filter {
                    it.date == yesterdayStr && !it.diaNo.isNullOrBlank() && it.diaNo != "S" && it.diaNo != "휴" && it.diaNo != "연차" && it.diaNo != "~"
                }
                val yesterdayOvernightSchedules = yesterdaySchedules.filter { sched ->
                    val st = sched.startTime?.replace(":", "")?.toIntOrNull()
                    val et = sched.endTime?.replace(":", "")?.toIntOrNull()
                    (st != null && et != null && st > et) || sched.isContinuation
                }

                // Combine target schedules: overnight continuation from yesterday first, then today's schedules
                val targetSchedules = mutableListOf<com.example.crewmate.database.WorkScheduleEntity>()
                targetSchedules.addAll(yesterdayOvernightSchedules)
                targetSchedules.addAll(todaySchedules)

                val entities = mutableListOf<com.example.crewmate.database.DiaDetailEntity>()

                if (targetSchedules.isNotEmpty()) {
                    targetSchedules.forEach { sched ->
                        val diaNo = sched.diaNo.orEmpty().trim()
                        val cleanDiaNo = diaNo.replace("~", "").trim()
                        val entity = db.diaDetailDao().getDiaDetail("${sched.date}|$diaNo")
                            ?: db.diaDetailDao().getDiaDetail("${sched.date}|$cleanDiaNo")
                            ?: db.diaDetailDao().getDiaDetail("$todayStr|$diaNo")
                            ?: db.diaDetailDao().getDiaDetail("$todayStr|$cleanDiaNo")
                            ?: db.diaDetailDao().getLatestDiaDetailByPdiaNo(diaNo)
                            ?: db.diaDetailDao().getLatestDiaDetailByPdiaNo(cleanDiaNo)
                        if (entity != null && entities.none { it.diaKey == entity.diaKey }) {
                            entities.add(entity)
                        }
                    }
                } else {
                    // Check if there is an explicit rest day for today
                    val isTodayRest = monthSchedules.any {
                        it.date == todayStr && (it.diaNo == "S" || it.diaNo == "휴" || it.diaNo == "연차")
                    }
                    if (isTodayRest) {
                        Log.i("MidnightCaptureReceiver", "Today ($todayStr) is a scheduled rest day (S/휴/연차). Auto capture skipped.")
                        return@launch
                    }
                    // Direct lookup fallback if work_schedules table is empty but dia_details has today's key
                    entities.addAll(db.diaDetailDao().getDiaDetailsForDate(todayStr))
                }

                if (entities.isNotEmpty()) {
                    val optionName = prefs.getString("midnight_auto_capture_option", DiaCaptureOption.TIMELINE_SUMMARY.name)
                        ?: DiaCaptureOption.TIMELINE_SUMMARY.name
                    val option = DiaCaptureOption.entries.find { it.name == optionName } ?: DiaCaptureOption.TIMELINE_SUMMARY
                    val hideSandwichedGamsi = prefs.getBoolean("hide_sandwiched_gamsi", true)

                    val parser = DiaDetailParser()
                    val renderList = mutableListOf<DiaRenderDataWrapper>()

                    entities.forEach { entity ->
                        val parsed = parser.parse(entity.detailJson)
                        if (parsed.segments.isNotEmpty()) {
                            val categorized = categorizeSegments(parsed.segments, today)
                            val filtered = if (option == DiaCaptureOption.TIMELINE_SUMMARY) {
                                categorized.filter { item ->
                                    val isSandwiched = item.isSandwichedGamsi && hideSandwichedGamsi
                                    item.rank != 3 && !isSandwiched
                                }
                            } else {
                                categorized
                            }
                            renderList.add(DiaRenderDataWrapper(entity.pdiaNo, filtered))
                        }
                    }

                    if (renderList.isNotEmpty()) {
                        val bitmap = renderDiaBitmap(today, renderList, option)
                        ScreenshotHelper.saveBitmapToGallery(context, bitmap, option.filePrefix)
                        Log.i("MidnightCaptureReceiver", "Auto capture saved with option: ${option.name}")
                    } else {
                        Log.i("MidnightCaptureReceiver", "No valid DIA segments to capture for $todayStr")
                    }
                } else {
                    Log.i("MidnightCaptureReceiver", "No DIA details cached for $todayStr. Auto capture skipped.")
                }
            } catch (e: Exception) {
                Log.e("MidnightCaptureReceiver", "Auto capture failed", e)
            } finally {
                scheduleNextMidnight(context)
                pendingResult.finish()
            }
        }
    }

    private fun renderDiaBitmap(
        today: LocalDate,
        diaList: List<DiaRenderDataWrapper>,
        option: DiaCaptureOption
    ): Bitmap {
        val width = 1080
        val outerMargin = 40f
        val cardWidth = width - outerMargin * 2

        // Measure dynamic height
        var totalHeight = 40f + 110f + 16f // Top margin + Clean Document Header + spacing
        val rowHeight = 72f
        val cardHeaderHeight = 84f
        val cardPadding = 32f

        diaList.forEach { dia ->
            val segCount = maxOf(dia.segments.size, 1)
            totalHeight += cardHeaderHeight + (segCount * rowHeight) + cardPadding + 20f
        }
        totalHeight += 80f // Footer + bottom margin

        val bitmap = Bitmap.createBitmap(width, maxOf(totalHeight.toInt(), 600), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F6F8FC")) // Clean modern theme background

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Clean Document Header (Matching Compose DiaCaptureLayout)
        val headerY = 40f
        val headerTitleY = headerY + 46f

        // Document Title
        paint.color = Color.parseColor("#0F2942") // Primary Navy
        paint.textSize = 36f
        paint.isFakeBoldText = true
        canvas.drawText("오늘의 근무", outerMargin + 8f, headerTitleY, paint)

        // Date Capsule Pill (Right aligned)
        val dateText = today.format(DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT))
        paint.textSize = 22f
        paint.isFakeBoldText = true
        val datePillWidth = paint.measureText(dateText) + 32f
        val datePillHeight = 44f
        val datePillRight = width - outerMargin - 8f
        val datePillLeft = datePillRight - datePillWidth
        val datePillTop = headerTitleY - 34f
        val datePillBottom = datePillTop + datePillHeight
        val datePillRect = RectF(datePillLeft, datePillTop, datePillRight, datePillBottom)

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E7FF") // Secondary container light blue
        }
        canvas.drawRoundRect(datePillRect, 10f, 10f, pillBgPaint)

        paint.color = Color.parseColor("#1E3A8A") // On Secondary container
        canvas.drawText(dateText, datePillLeft + 16f, datePillTop + 30f, paint)

        // Option Capsule Pill (Placed right next to Date pill)
        val optText = option.title
        val optPillWidth = paint.measureText(optText) + 28f
        val optPillRight = datePillLeft - 12f
        val optPillLeft = optPillRight - optPillWidth
        val optPillRect = RectF(optPillLeft, datePillTop, optPillRight, datePillBottom)

        val optBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EFF6FF")
        }
        canvas.drawRoundRect(optPillRect, 10f, 10f, optBgPaint)
        paint.color = Color.parseColor("#2563EB")
        canvas.drawText(optText, optPillLeft + 14f, datePillTop + 30f, paint)

        // 2. DIA Cards (Matching Compose DiaDetailCard in isCaptureMode)
        var curY = headerY + 80f
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1.5f
        }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 3f
        }

        diaList.forEach { dia ->
            val segCount = maxOf(dia.segments.size, 1)
            val thisCardHeight = cardHeaderHeight + (segCount * rowHeight) + cardPadding
            val cardRect = RectF(outerMargin, curY, width - outerMargin, curY + thisCardHeight)

            // White rounded card with border
            canvas.drawRoundRect(cardRect, 20f, 20f, cardBgPaint)
            canvas.drawRoundRect(cardRect, 20f, 20f, cardBorderPaint)

            // DIA Title
            paint.isFakeBoldText = true
            paint.textSize = 28f
            paint.color = Color.parseColor("#0F2942")
            canvas.drawText("${dia.pdiaNo} DIA", outerMargin + 24f, curY + 50f, paint)

            // Under-title thin divider
            canvas.drawLine(outerMargin + 20f, curY + cardHeaderHeight, width - outerMargin - 20f, curY + cardHeaderHeight, dividerPaint)

            // Timeline items
            var rowY = curY + cardHeaderHeight + 46f
            val trackX = outerMargin + 38f

            if (dia.segments.isEmpty()) {
                paint.isFakeBoldText = false
                paint.textSize = 22f
                paint.color = Color.parseColor("#94A3B8")
                canvas.drawText("표시할 상세 구간이 없습니다.", outerMargin + 32f, rowY, paint)
                rowY += rowHeight
            } else {
                dia.segments.forEachIndexed { sIdx, item ->
                    val seg = item.segment
                    val rawCat = item.name
                    val cat = when {
                        rawCat.contains("편승") -> "편승"
                        rawCat.contains("승무") -> "승무"
                        else -> rawCat
                    }
                    var trn = seg.trnNo?.trim().orEmpty()
                    if (cat == "편승" && trn == "도") trn = "도보"

                    // Vertical timeline track line
                    val trackTop = if (sIdx == 0) rowY - 14f else rowY - rowHeight + 10f
                    val trackBottom = if (sIdx == dia.segments.lastIndex) rowY - 6f else rowY + rowHeight - 10f
                    canvas.drawLine(trackX, trackTop, trackX, trackBottom, trackPaint)

                    // Dot color by rank
                    val (dotColor, badgeBg, badgeTextColor) = when (item.rank) {
                        1 -> Triple(Color.parseColor("#2563EB"), Color.parseColor("#EFF6FF"), Color.parseColor("#1D4ED8"))
                        2 -> Triple(Color.parseColor("#0284C7"), Color.parseColor("#F0F9FF"), Color.parseColor("#0369A1"))
                        else -> Triple(Color.parseColor("#94A3B8"), Color.parseColor("#F8FAFC"), Color.parseColor("#64748B"))
                    }

                    // Dot
                    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
                    canvas.drawCircle(trackX, rowY - 8f, 6f, dotPaint)

                    // Category Pill Badge
                    paint.textSize = 20f
                    paint.isFakeBoldText = true
                    val catWidth = paint.measureText(cat) + 24f
                    val badgeLeft = trackX + 20f
                    val badgeRect = RectF(badgeLeft, rowY - 26f, badgeLeft + catWidth, rowY + 10f)
                    val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeBg }
                    canvas.drawRoundRect(badgeRect, 6f, 6f, bPaint)

                    paint.color = badgeTextColor
                    canvas.drawText(cat, badgeLeft + 12f, rowY - 3f, paint)

                    // Train number
                    var textStartX = badgeLeft + catWidth + 14f
                    if (trn.isNotEmpty()) {
                        paint.color = Color.parseColor("#0F2942")
                        paint.textSize = 22f
                        paint.isFakeBoldText = true
                        canvas.drawText(trn, textStartX, rowY - 2f, paint)
                        textStartX += paint.measureText(trn) + 18f
                    }

                    // Station & Times
                    val dpt = "${seg.dptStnNm ?: ""}${seg.dptTm?.let { " ($it)" } ?: ""}"
                    val arv = "${seg.arvStnNm ?: ""}${seg.arvTm?.let { " ($it)" } ?: ""}"
                    val stnText = if (dpt.isNotEmpty() || arv.isNotEmpty()) "$dpt  →  $arv" else ""

                    if (stnText.isNotEmpty()) {
                        paint.color = Color.parseColor("#1E293B")
                        paint.textSize = 22f
                        paint.isFakeBoldText = false
                        canvas.drawText(stnText, maxOf(textStartX, 380f), rowY - 2f, paint)
                    }

                    rowY += rowHeight
                }
            }

            curY += thisCardHeight + 20f
        }

        // 3. Footer
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 20f
        paint.isFakeBoldText = false
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT))
        val footerText = "CrewMate 자동 캡처 • $nowStr"
        val footerWidth = paint.measureText(footerText)
        canvas.drawText(footerText, (width - footerWidth) / 2f, curY + 36f, paint)

        return bitmap
    }

    internal data class DiaRenderDataWrapper(
        val pdiaNo: String,
        val segments: List<CategorizedSegment>
    )

    companion object {
        fun scheduleMidnightCapture(context: Context) {
            scheduleNextMidnight(context)
        }

        fun cancelMidnightCapture(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, MidnightCaptureReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                9922,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun scheduleNextMidnight(context: Context) {
            val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("midnight_auto_capture", false)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, MidnightCaptureReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                9922,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (!enabled) {
                alarmManager.cancel(pendingIntent)
                return
            }

            val tomorrowMidnight = LocalDate.now().plusDays(1).atTime(0, 1)
            val epochMilli = tomorrowMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                }
            } catch (e: Exception) {
                Log.e("MidnightCaptureReceiver", "Failed to schedule next midnight", e)
            }
        }
    }
}
