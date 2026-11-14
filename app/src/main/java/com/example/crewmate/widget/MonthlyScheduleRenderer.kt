package com.example.crewmate.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import com.example.crewmate.schedule.DeviceEvent
import com.example.crewmate.schedule.ShiftType
import com.example.crewmate.schedule.WorkPatternData
import com.example.crewmate.schedule.WorkPatternSettings
import com.example.crewmate.schedule.WorkSchedule
import com.example.crewmate.schedule.determineShiftType
import com.example.crewmate.schedule.isValidActiveDia
import com.example.crewmate.ui.theme.getCanvasTypeface
import com.example.crewmate.ui.theme.isDarkThemeActive
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 월별 승무근무표 홈 화면 위젯용 1080px 고화질 2D Canvas 비트맵 렌더러.
 * 앱 내 월별 근무표와 동일한 근무 뱃지 색상(#FFD54F / #333333)을 사용하고,
 * 대표열번을 제외한 모든 정보(근무 뱃지, 출/퇴근 시각, 메모 내용, 기기 캘린더 일정)를
 * 주(row)별 동적 높이에 맞춰 짤림 없이 렌더링합니다.
 */
object MonthlyScheduleRenderer {

    private fun formatTime(raw: String?): String? {
        val digits = raw?.filter { it.isDigit() }.orEmpty()
        return if (digits.length >= 4) "${digits.substring(0, 2)}:${digits.substring(2, 4)}" else null
    }

    /**
     * 앱(ScheduleScreen.kt)과 동일하게 실동 다이아가 있는 날에만 "S"를 제외하고,
     * 실동 다이아가 없는 휴무일에는 "S"를 보존합니다.
     */
    fun filterDisplaySchedules(daySchedules: List<WorkSchedule>): List<WorkSchedule> {
        val hasActualDuty = daySchedules.any {
            val d = it.diaNo?.trim().orEmpty()
            d.isNotEmpty() && d != "S" && d != "연차"
        }
        return if (hasActualDuty) {
            daySchedules.filter { it.diaNo?.trim() != "S" && it.nonActiveType?.trim() != "S" }
        } else {
            daySchedules
        }
    }

    fun renderToBitmap(
        context: Context,
        yearMonth: YearMonth,
        schedules: List<WorkSchedule>,
        memosByDate: Map<LocalDate, List<String>> = emptyMap(),
        deviceEventsByDate: Map<LocalDate, List<DeviceEvent>> = emptyMap(),
        patternData: WorkPatternData? = null,
        width: Int = 1080,
        minTotalHeight: Int = 0,
        isDark: Boolean = context.isDarkThemeActive()
    ): Bitmap {
        val density = width / 480f
        val rawFontScale = runCatching { context.resources.configuration.fontScale }.getOrDefault(1.0f)
        val fontScale = if (rawFontScale > 0f) rawFontScale.coerceIn(0.85f, 1.2f) else 1.0f

        fun dp(v: Float) = v * density
        fun sp(v: Float) = v * density * fontScale

        val weightAdjustment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val adj = runCatching { context.resources.configuration.fontWeightAdjustment }.getOrDefault(0)
            if (adj != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED) adj else 0
        } else 0

        fun getTypeface(baseWeight: Int): Typeface = context.getCanvasTypeface(baseWeight, weightAdjustment)

        // M3 + 앱(ScheduleScreen.kt) 동일 색상 팔레트
        val canvasBgColor = if (isDark) Color.parseColor("#0B0F17") else Color.parseColor("#F6F8FC")
        val cardBgColor = if (isDark) Color.parseColor("#161E2E") else Color.WHITE
        val cardBorderColor = if (isDark) Color.parseColor("#263345") else Color.parseColor("#E2E8F0")
        val gridLineColor = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#EEF2F6")
        val primaryColor = if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#0A1E3F")
        val textPrimary = if (isDark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val textMuted = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        val sundayColor = if (isDark) Color.parseColor("#F87171") else Color.parseColor("#DC2626")
        val saturdayColor = if (isDark) Color.parseColor("#60A5FA") else Color.parseColor("#2563EB")
        val todayBgColor = if (isDark) Color.parseColor("#1A2744") else Color.parseColor("#EFF6FF")
        val todayBorderColor = if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#0F2C59")

        // 앱 내 ScheduleScreen.kt와 100% 동일한 근무 뱃지 색상
        val dayBadgeBg = Color.parseColor("#FFD54F")
        val dayBadgeFg = Color.BLACK
        val nightBadgeBg = Color.parseColor("#333333")
        val nightBadgeFg = Color.WHITE
        val annualTextColor = Color.parseColor("#0D9488")
        val patternRestBg = if (isDark) Color.argb(178, 30, 58, 138) else Color.argb(178, 220, 232, 248)
        val patternBorderColor = if (isDark) Color.argb(128, 51, 65, 85) else Color.argb(128, 203, 213, 225)

        // 메모 배지 색상 (Material secondaryContainer / onSecondaryContainer)
        val memoBgColor = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#E8EEF6")
        val memoTextColor = if (isDark) Color.parseColor("#F1F5F9") else Color.parseColor("#0F172A")

        // 달력 주(행) 수 계산 (일요일 시작)
        val firstDay = yearMonth.atDay(1)
        val firstDayOffset = firstDay.dayOfWeek.value % 7 // 일=0, 월=1, ..., 토=6
        val daysInMonth = yearMonth.lengthOfMonth()
        val totalCells = firstDayOffset + daysInMonth
        val numRows = ((totalCells + 6) / 7).coerceAtLeast(5)

        val outerPad = dp(10f)
        val cardPad = dp(8f)
        val weekdayRowH = dp(24f)

        val dayHeaderH = dp(17f)
        val badgeH = dp(15f)
        val timeLineH = dp(12f)
        val tagLineH = dp(13f)
        val tagGap = dp(2f)
        val cellBottomPad = dp(5f)
        val minCellH = dp(64f)

        // 날짜별 스케줄 매핑 및 미배정 달 여부 판정
        val scheduleByDate = schedules.groupBy {
            it.workDate?.filter { c -> c.isDigit() }.orEmpty()
        }
        val today = LocalDate.now()
        val fmt8 = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
        val curYmPrefix = yearMonth.format(DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT))
        val isMonthEmpty = schedules.none {
            it.workDate?.filter { c -> c.isDigit() }?.startsWith(curYmPrefix) == true
        }

        // 각 셀의 표시 데이터 미리 구성 & 주(row)별 최대 높이 동적 계산
        data class CellModel(
            val date: LocalDate,
            val dayNum: Int,
            val col: Int,
            val isHoliday: Boolean,
            val badgeText: String,
            val isDutyBadge: Boolean,
            val isNight: Boolean,
            val isPatternBadge: Boolean,
            val startTime: String?,
            val endTime: String?,
            val memos: List<String>,
            val events: List<DeviceEvent>
        )

        val cellModels = Array(numRows) { arrayOfNulls<CellModel>(7) }
        val rowHeights = FloatArray(numRows)

        for (row in 0 until numRows) {
            var maxCellContentH = minCellH
            for (col in 0 until 7) {
                val cellIndex = row * 7 + col
                val dayNum = cellIndex - firstDayOffset + 1
                if (dayNum < 1 || dayNum > daysInMonth) continue

                val date = yearMonth.atDay(dayNum)
                val dateStr = date.format(fmt8)
                val daySchedules = filterDisplaySchedules(scheduleByDate[dateStr].orEmpty())
                val primarySched = daySchedules.firstOrNull { it.isValidActiveDia() } ?: daySchedules.firstOrNull()
                val isHoliday = primarySched?.isHoliday == true || primarySched?.hldyYn == "Y"

                val patternBadge = if (isMonthEmpty && daySchedules.isEmpty() && patternData != null) {
                    WorkPatternSettings.getPatternForDate(patternData, date)
                } else null

                val st = formatTime(primarySched?.startTime)
                val et = formatTime(primarySched?.endTime)
                val dia = primarySched?.diaNo?.trim()?.takeIf { it.isNotEmpty() }
                    ?: primarySched?.nonActiveType?.trim().orEmpty()
                val hasTime = st != null || et != null
                val isDutyBadge = primarySched != null && hasTime && dia.isNotEmpty() && dia != "S" && dia != "연차"
                val shiftType = schedules.determineShiftType(dateStr)
                val isNight = if (patternBadge != null) {
                    patternBadge == "야간" || patternBadge == "~"
                } else {
                    primarySched != null && (
                        primarySched.isContinuation ||
                            shiftType == ShiftType.OVERNIGHT_DAY1 ||
                            shiftType == ShiftType.OVERNIGHT_DAY2 ||
                            (st != null && et != null && st > et)
                        )
                }
                val isPatternBadge = daySchedules.isEmpty() && patternBadge != null
                val badgeText = when {
                    isPatternBadge -> patternBadge!!
                    dia.isNotEmpty() -> {
                        val isCont = primarySched?.isContinuation == true || shiftType == ShiftType.OVERNIGHT_DAY2
                        if (isCont && isDutyBadge && !dia.startsWith("~")) "~$dia" else dia
                    }
                    isHoliday -> "휴일"
                    else -> ""
                }

                val memos = memosByDate[date].orEmpty()
                val events = deviceEventsByDate[date].orEmpty()

                var neededH = dayHeaderH
                if (badgeText.isNotEmpty()) neededH += badgeH + dp(2f)
                if (st != null) neededH += timeLineH
                if (et != null) neededH += timeLineH
                if (memos.isNotEmpty()) neededH += memos.size * (tagLineH + tagGap)
                if (events.isNotEmpty()) neededH += events.size * (tagLineH + tagGap)
                neededH += cellBottomPad

                if (neededH > maxCellContentH) {
                    maxCellContentH = neededH
                }

                cellModels[row][col] = CellModel(
                    date = date,
                    dayNum = dayNum,
                    col = col,
                    isHoliday = isHoliday,
                    badgeText = badgeText,
                    isDutyBadge = isDutyBadge,
                    isNight = isNight,
                    isPatternBadge = isPatternBadge,
                    startTime = st,
                    endTime = et,
                    memos = memos,
                    events = events
                )
            }
            rowHeights[row] = maxCellContentH
        }

        val cardTop = outerPad
        val gridTop = cardTop + cardPad + weekdayRowH
        val naturalTotalHeight = (gridTop + rowHeights.sum() + cardPad + outerPad).toInt()
        if (minTotalHeight > naturalTotalHeight) {
            val extraPerRow = (minTotalHeight - naturalTotalHeight).toFloat() / numRows
            for (r in 0 until numRows) {
                rowHeights[r] += extraPerRow
            }
        }
        val gridH = rowHeights.sum()
        val totalHeight = (gridTop + gridH + cardPad + outerPad).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(canvasBgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

        // 1. 달력 메인 카드 배경
        val cardRect = RectF(outerPad, cardTop, width - outerPad, cardTop + cardPad + weekdayRowH + gridH + cardPad)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBgColor }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorderColor
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }
        canvas.drawRoundRect(cardRect, dp(14f), dp(14f), cardBgPaint)
        canvas.drawRoundRect(cardRect, dp(14f), dp(14f), cardBorderPaint)

        val gridLeft = outerPad + cardPad
        val gridRight = width - outerPad - cardPad
        val colW = (gridRight - gridLeft) / 7f

        // 2. 요일 헤더
        val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")
        paint.textSize = sp(11.5f)
        paint.typeface = getTypeface(700)
        paint.textAlign = Paint.Align.CENTER
        val weekdayCenterY = cardTop + cardPad + weekdayRowH / 2f
        val weekdayBaseY = weekdayCenterY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        for (col in 0 until 7) {
            paint.color = when (col) {
                0 -> sundayColor
                6 -> saturdayColor
                else -> textMuted
            }
            val cx = gridLeft + col * colW + colW / 2f
            canvas.drawText(weekdays[col], cx, weekdayBaseY, paint)
        }

        val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gridLineColor
            strokeWidth = dp(1f)
        }
        canvas.drawLine(gridLeft, gridTop, gridRight, gridTop, gridLinePaint)

        // 3. 일자 셀 렌더링
        var curRowTop = gridTop
        for (row in 0 until numRows) {
            val rowH = rowHeights[row]
            val rowTop = curRowTop
            val rowBottom = rowTop + rowH
            curRowTop = rowBottom

            if (row > 0) {
                canvas.drawLine(gridLeft, rowTop, gridRight, rowTop, gridLinePaint)
            }

            for (col in 0 until 7) {
                val cell = cellModels[row][col] ?: continue
                val cellLeft = gridLeft + col * colW
                val cellRight = cellLeft + colW
                val innerLeft = cellLeft + dp(3f)
                val innerRight = cellRight - dp(3f)
                val innerW = (innerRight - innerLeft).coerceAtLeast(dp(10f))

                // 오늘 날짜 하이라이트
                if (cell.date == today) {
                    val todayRect = RectF(cellLeft + dp(1.5f), rowTop + dp(1.5f), cellRight - dp(1.5f), rowBottom - dp(1.5f))
                    val tBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = todayBgColor }
                    val tBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = todayBorderColor
                        style = Paint.Style.STROKE
                        strokeWidth = dp(1.5f)
                    }
                    canvas.drawRoundRect(todayRect, dp(6f), dp(6f), tBgPaint)
                    canvas.drawRoundRect(todayRect, dp(6f), dp(6f), tBorderPaint)
                }

                // 날짜 숫자 (일요일/휴일 빨강, 토요일 파랑)
                paint.textSize = sp(11f)
                paint.typeface = getTypeface(if (cell.date == today) 800 else 600)
                paint.textAlign = Paint.Align.LEFT
                paint.color = when {
                    col == 0 || cell.isHoliday -> sundayColor
                    col == 6 -> saturdayColor
                    else -> textPrimary
                }
                canvas.drawText(cell.dayNum.toString(), innerLeft + dp(2f), rowTop + dp(13.5f), paint)

                var cursorY = rowTop + dayHeaderH

                // 근무 뱃지 (앱과 동일한 색상 및 라벨 규칙 + 미배정 달 순환 패턴)
                if (cell.badgeText.isNotEmpty()) {
                    if (cell.isPatternBadge) {
                        val isRest = cell.badgeText == "S"
                        val bgC = when {
                            isRest -> patternRestBg
                            cell.isNight -> Color.argb(217, 51, 51, 51)
                            else -> Color.argb(217, 255, 213, 79)
                        }
                        val fgC = when {
                            isRest -> primaryColor
                            cell.isNight -> Color.WHITE
                            else -> Color.BLACK
                        }
                        textPaint.textSize = sp(9.5f)
                        textPaint.typeface = getTypeface(700)
                        val textW = textPaint.measureText(cell.badgeText)
                        val pillW = (textW + dp(10f)).coerceAtMost(innerW)
                        val pillLeft = cellLeft + (colW - pillW) / 2f
                        val badgeRect = RectF(pillLeft, cursorY, pillLeft + pillW, cursorY + badgeH)
                        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgC }
                        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = patternBorderColor
                            style = Paint.Style.STROKE
                            strokeWidth = dp(0.75f)
                        }
                        canvas.drawRoundRect(badgeRect, dp(3f), dp(3f), bPaint)
                        canvas.drawRoundRect(badgeRect, dp(3f), dp(3f), borderP)

                        textPaint.color = fgC
                        textPaint.textAlign = Paint.Align.CENTER
                        val ellipsized = TextUtils.ellipsize(cell.badgeText, textPaint, pillW - dp(4f), TextUtils.TruncateAt.END).toString()
                        val bBaseY = badgeRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
                        canvas.drawText(ellipsized, badgeRect.centerX(), bBaseY, textPaint)
                    } else if (cell.isDutyBadge) {
                        val bgC = if (cell.isNight) nightBadgeBg else dayBadgeBg
                        val fgC = if (cell.isNight) nightBadgeFg else dayBadgeFg
                        textPaint.textSize = sp(9.5f)
                        textPaint.typeface = getTypeface(700)
                        val textW = textPaint.measureText(cell.badgeText)
                        val pillW = (textW + dp(8f)).coerceAtMost(innerW)
                        val pillLeft = cellLeft + (colW - pillW) / 2f
                        val badgeRect = RectF(pillLeft, cursorY, pillLeft + pillW, cursorY + badgeH)
                        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgC }
                        canvas.drawRoundRect(badgeRect, dp(3f), dp(3f), bPaint)

                        textPaint.color = fgC
                        textPaint.textAlign = Paint.Align.CENTER
                        val ellipsized = TextUtils.ellipsize(cell.badgeText, textPaint, pillW - dp(4f), TextUtils.TruncateAt.END).toString()
                        val bBaseY = badgeRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
                        canvas.drawText(ellipsized, badgeRect.centerX(), bBaseY, textPaint)
                    } else {
                        textPaint.textSize = sp(9.5f)
                        textPaint.typeface = getTypeface(800)
                        textPaint.textAlign = Paint.Align.CENTER
                        textPaint.color = when (cell.badgeText) {
                            "연차" -> annualTextColor
                            "S" -> primaryColor
                            else -> textMuted
                        }
                        val ellipsized = TextUtils.ellipsize(cell.badgeText, textPaint, innerW, TextUtils.TruncateAt.END).toString()
                        val tCenterY = cursorY + badgeH / 2f
                        val tBaseY = tCenterY - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
                        canvas.drawText(ellipsized, cellLeft + colW / 2f, tBaseY, textPaint)
                    }
                    cursorY += badgeH + dp(2f)
                }

                // 출근 시각 & 퇴근 시각 (대표열번 제외)
                if (cell.startTime != null) {
                    paint.color = primaryColor
                    paint.textSize = sp(8.8f)
                    paint.typeface = getTypeface(700)
                    paint.textAlign = Paint.Align.CENTER
                    val baseY = cursorY + timeLineH / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
                    canvas.drawText(cell.startTime, cellLeft + colW / 2f, baseY, paint)
                    cursorY += timeLineH
                }
                if (cell.endTime != null) {
                    paint.color = primaryColor
                    paint.textSize = sp(8.8f)
                    paint.typeface = getTypeface(700)
                    paint.textAlign = Paint.Align.CENTER
                    val baseY = cursorY + timeLineH / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
                    canvas.drawText(cell.endTime, cellLeft + colW / 2f, baseY, paint)
                    cursorY += timeLineH
                }

                // 메모 내용 라벨 (점 표시 대신 실제 텍스트 배지 표출)
                cell.memos.forEach { memoText ->
                    val pillRect = RectF(innerLeft, cursorY, innerRight, cursorY + tagLineH)
                    val mBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = memoBgColor }
                    canvas.drawRoundRect(pillRect, dp(2.5f), dp(2.5f), mBgPaint)

                    textPaint.color = memoTextColor
                    textPaint.textSize = sp(8.3f)
                    textPaint.typeface = getTypeface(800)
                    textPaint.textAlign = Paint.Align.LEFT
                    val ellipsized = TextUtils.ellipsize(memoText, textPaint, innerW - dp(4f), TextUtils.TruncateAt.END).toString()
                    val baseY = pillRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
                    canvas.drawText(ellipsized, innerLeft + dp(2f), baseY, textPaint)
                    cursorY += tagLineH + tagGap
                }

                // 기기 캘린더 일정 라벨
                cell.events.forEach { evt ->
                    val pillRect = RectF(innerLeft, cursorY, innerRight, cursorY + tagLineH)
                    val eBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = evt.color }
                    canvas.drawRoundRect(pillRect, dp(2.5f), dp(2.5f), eBgPaint)

                    textPaint.color = Color.WHITE
                    textPaint.textSize = sp(8.3f)
                    textPaint.typeface = getTypeface(700)
                    textPaint.textAlign = Paint.Align.LEFT
                    val ellipsized = TextUtils.ellipsize(evt.title, textPaint, innerW - dp(4f), TextUtils.TruncateAt.END).toString()
                    val baseY = pillRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
                    canvas.drawText(ellipsized, innerLeft + dp(2f), baseY, textPaint)
                    cursorY += tagLineH + tagGap
                }
            }
        }

        return bitmap
    }
}
