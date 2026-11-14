package com.example.crewmate.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.widget.TextView
import com.example.crewmate.schedule.categorizeSegments
import com.example.crewmate.ui.theme.getCanvasTypeface
import com.example.crewmate.ui.theme.isDarkThemeActive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 전 화면 다이아 스크린샷 캡처(오늘의 근무, 근무표, 다이아 조회) 및 자정 자동 캡처가
 * 100% 동일한 비례 가중치(flex weights)와 기기 폰트 설정(서체 종류, 글자 크기, 글자 굵기)으로
 * 비트맵을 생성하도록 일원화한 단일 통합 렌더러.
 *
 * (v1.0.34: 1440px QHD 와이드 뷰포트 및 동적 충돌 방지 안전 간격 적용으로 가독성 대폭 향상)
 */
object DiaCaptureRenderer {

    fun renderToBitmap(
        context: Context,
        headerTitle: String,
        workDate: LocalDate,
        dias: List<DiaCaptureItem>,
        option: DiaCaptureOption,
        width: Int = 1440,
        isDark: Boolean = context.isDarkThemeActive(),
        showPrepDivider: Boolean = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
            .getBoolean("show_prep_divider", true),
        badgeText: String? = null,
        reserveTopEndSpaceDp: Float = 0f
    ): Bitmap? {
        if (dias.isEmpty()) return null

        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val hideSandwichedGamsi = prefs.getBoolean("hide_sandwiched_gamsi", true)

        // 가상 뷰포트 480dp 와이드 규격에 맞춘 동적 density 계산 (1440px -> 3.0f, 1080px -> 2.25f)
        val density = width / 480f

        // 1. 기기 설정 글자 크기(fontScale) 정밀 반영 (극단치 보호: 0.8f ~ 1.35f)
        val rawFontScale = runCatching { context.resources.configuration.fontScale }.getOrDefault(1.0f)
        val fontScale = if (rawFontScale > 0f) rawFontScale.coerceIn(0.8f, 1.35f) else 1.0f

        fun dp(v: Float) = v * density
        fun sp(v: Float) = v * density * fontScale

        // 2. 앱 설정 글꼴(시스템 기본/프리텐다드) 및 기기 '글자 굵게' 설정 반영
        val weightAdjustment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val adj = runCatching { context.resources.configuration.fontWeightAdjustment }.getOrDefault(0)
            if (adj != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED) adj else 0
        } else {
            0
        }

        fun getTypeface(baseWeight: Int): Typeface = context.getCanvasTypeface(baseWeight, weightAdjustment)

        // 색상 팔레트 (M3 Theme 토큰 1:1 일치)
        val canvasBgColor = if (isDark) Color.parseColor("#0B0F17") else Color.parseColor("#F6F8FC")
        val cardBgColor = if (isDark) Color.parseColor("#161E2E") else Color.WHITE
        val cardBorderColor = if (isDark) Color.parseColor("#263345") else Color.parseColor("#E2E8F0")
        val headerTitleColor = if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#0A1E3F")
        val pillBgColor = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#E8EEF6")
        val pillTextColor = if (isDark) Color.parseColor("#F1F5F9") else Color.parseColor("#0F172A")
        val diaTitleColor = if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#0A1E3F")
        val timeInfoColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        val rank1RowBgColor = if (isDark) Color.parseColor("#232D3F") else Color.parseColor("#E9EEF5")
        val rank1BadgeBgColor = if (isDark) Color.parseColor("#1E3A8A") else Color.parseColor("#DCE8F8")
        val rank1BadgeTextColor = if (isDark) Color.parseColor("#E0F2FE") else Color.parseColor("#0A1E3F")
        val defaultBadgeBgColor = if (isDark) Color.parseColor("#232D3F") else Color.parseColor("#E9EEF5")
        val defaultBadgeTextColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        val stnTextColor = if (isDark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val primaryTimeColor = if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#0A1E3F")
        val arrowColor = if (isDark) Color.parseColor("#9994A3B8") else Color.parseColor("#9964748B")
        val rank1LineColor = if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#0A1E3F")
        val outlineColor = if (isDark) Color.parseColor("#334155") else Color.parseColor("#CBD5E1")
        // 직전 버전(v1.0.30) 롤백: alpha 50%
        val prepDividerColor = if (isDark) Color.parseColor("#8038BDF8") else Color.parseColor("#800A1E3F")
        // 야간 2일차 / 비근무일 빨간색 계열 뱃지 색상 (errorContainer / onErrorContainer)
        val dutyBadgeBgColor = if (isDark) Color.parseColor("#93000A") else Color.parseColor("#FFDAD6")
        val dutyBadgeTextColor = if (isDark) Color.parseColor("#FFDAD6") else Color.parseColor("#410002")

        // 3. 각 카드별 세그먼트 데이터 준비 및 전체 캔버스 높이 사전 계산
        class CardRenderData(
            val diaText: String,
            val timeInfo: String,
            val segments: List<com.example.crewmate.schedule.CategorizedSegment>
        )

        val cardDataList = dias.map { item ->
            val diaText = item.schedule.diaNo?.trim().orEmpty()
            val startDisp = (item.schedule.startTime ?: item.detail.segments.firstOrNull { !it.dptTm.isNullOrBlank() }?.dptTm).toDisplayTime() ?: ""
            val endDisp = (item.schedule.endTime ?: item.detail.segments.lastOrNull { !it.arvTm.isNullOrBlank() }?.arvTm).toDisplayTime() ?: ""
            val timeInfo = when {
                startDisp.isNotEmpty() && endDisp.isNotEmpty() -> "출근 $startDisp / 퇴근 $endDisp"
                startDisp.isNotEmpty() -> "출근 $startDisp"
                endDisp.isNotEmpty() -> "퇴근 $endDisp"
                else -> ""
            }

            val categorized = categorizeSegments(item.detail.segments, workDate)
            val filtered = if (option.showAll) {
                categorized
            } else {
                categorized.filter { seg ->
                    val isSandwiched = seg.isSandwichedGamsi && hideSandwichedGamsi
                    seg.rank != 3 && !isSandwiched
                }
            }
            CardRenderData(diaText, timeInfo, filtered)
        }

        // 와이드 화면 비율에 맞춘 여백 및 동적 가변 높이 계산
        val docPadding = dp(20f)
        val headerRowH = maxOf(dp(28f), sp(24f) + dp(6f))
        val headerBottomGap = dp(16f)
        val cardInnerPadding = dp(16f)
        val cardHeaderH = maxOf(dp(26f), sp(20f) + dp(6f))
        val cardHeaderBottomGap = dp(14f)
        val rowH = maxOf(dp(44f), dp(28f) + sp(14f))
        val dividerGap = dp(8f)
        val cardGap = dp(16f)

        var totalHeight = docPadding + headerRowH + headerBottomGap

        cardDataList.forEachIndexed { cIdx, card ->
            var thisCardContentH = cardInnerPadding + cardHeaderH + cardHeaderBottomGap
            if (card.segments.isEmpty()) {
                thisCardContentH += dp(36f)
            } else {
                card.segments.forEachIndexed { sIdx, seg ->
                    val rawCat = seg.name
                    val isPrep = rawCat.contains("준비") || rawCat.contains("출고준비")
                    val prevCat = if (sIdx > 0) card.segments[sIdx - 1].name else ""
                    val prevIsPrep = prevCat.contains("준비") || prevCat.contains("출고준비")
                    if (showPrepDivider && isPrep && !prevIsPrep && sIdx > 0) {
                        thisCardContentH += dividerGap
                    }
                    thisCardContentH += rowH
                }
            }
            thisCardContentH += cardInnerPadding
            totalHeight += thisCardContentH
            if (cIdx < cardDataList.lastIndex) {
                totalHeight += cardGap
            }
        }
        totalHeight += docPadding

        val bitmap = Bitmap.createBitmap(width, maxOf(totalHeight.toInt(), 600), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(canvasBgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 4. 최상단 문서 헤더 ("승무 다이아 상세" + 날짜 알약 캡슐)
        var curY = docPadding

        // 좌측 타이틀 (Bold 700)
        paint.color = headerTitleColor
        paint.textSize = sp(24f)
        paint.typeface = getTypeface(700)
        paint.textAlign = Paint.Align.LEFT
        val headerCenterY = curY + (headerRowH / 2f)
        val titleBaselineY = headerCenterY - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
        canvas.drawText(headerTitle, docPadding, titleBaselineY, paint)

        // 야간 2일차 / 비근무일 붉은색 뱃지 (예: "11.08 근무")
        if (!badgeText.isNullOrBlank()) {
            val titleW = paint.measureText(headerTitle)
            val badgeGap = dp(10f)
            val badgeLeft = docPadding + titleW + badgeGap

            paint.textSize = sp(12f)
            paint.typeface = getTypeface(800) // ExtraBold
            val textW = paint.measureText(badgeText)
            val badgePadH = dp(8f)
            val badgePadV = dp(4f)
            val badgeW = textW + (badgePadH * 2)
            val badgeH = maxOf(dp(22f), sp(12f) + (badgePadV * 2))
            val badgeTop = headerCenterY - (badgeH / 2f)
            val badgeBottom = badgeTop + badgeH
            val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeBottom)

            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dutyBadgeBgColor }
            canvas.drawRoundRect(badgeRect, dp(8f), dp(8f), badgeBgPaint)

            paint.color = dutyBadgeTextColor
            paint.textAlign = Paint.Align.CENTER
            val badgeBaselineY = badgeRect.centerY() - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
            canvas.drawText(badgeText, badgeRect.centerX(), badgeBaselineY, paint)
        }

        // 우측 날짜 알약 (Bold 700)
        val dateText = workDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT))
        paint.textSize = sp(12.5f)
        paint.typeface = getTypeface(700)
        val dateTextWidth = paint.measureText(dateText)
        val pillPaddingH = dp(12f)
        val pillW = dateTextWidth + (pillPaddingH * 2)
        val pillH = maxOf(dp(26f), sp(12.5f) + dp(10f))
        val pillRight = width - docPadding - dp(reserveTopEndSpaceDp)
        val pillLeft = pillRight - pillW
        val pillTop = curY + (headerRowH - pillH) / 2f
        val pillBottom = pillTop + pillH
        val pillRect = RectF(pillLeft, pillTop, pillRight, pillBottom)

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBgColor }
        canvas.drawRoundRect(pillRect, dp(8f), dp(8f), pillBgPaint)

        paint.color = pillTextColor
        paint.textAlign = Paint.Align.CENTER
        val pillBaselineY = pillRect.centerY() - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
        canvas.drawText(dateText, pillRect.centerX(), pillBaselineY, paint)

        curY += headerRowH + headerBottomGap

        // 5. DIA 카드 렌더링
        val cardWidth = width - (docPadding * 2)
        val cardLeft = docPadding
        val cardRight = width - docPadding

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBgColor }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorderColor
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }
        val rank1LinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rank1LineColor
            strokeWidth = dp(3.5f)
        }
        val defaultLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outlineColor
            strokeWidth = dp(3.5f)
        }
        val dashedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outlineColor
            strokeWidth = dp(3.5f)
            pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
        }
        val prepDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = prepDividerColor
            strokeWidth = dp(1.5f) // 직전 버전(v1.0.30) 롤백: 1.5dp
        }
        val rank1RowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rank1RowBgColor }
        val rank1BadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rank1BadgeBgColor }
        val defaultBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = defaultBadgeBgColor }

        cardDataList.forEachIndexed { cIdx, card ->
            // 이 카드의 세로 높이 계산
            var thisCardH = cardInnerPadding + cardHeaderH + cardHeaderBottomGap
            if (card.segments.isEmpty()) {
                thisCardH += dp(36f)
            } else {
                card.segments.forEachIndexed { sIdx, seg ->
                    val rawCat = seg.name
                    val isPrep = rawCat.contains("준비") || rawCat.contains("출고준비")
                    val prevCat = if (sIdx > 0) card.segments[sIdx - 1].name else ""
                    val prevIsPrep = prevCat.contains("준비") || prevCat.contains("출고준비")
                    if (showPrepDivider && isPrep && !prevIsPrep && sIdx > 0) {
                        thisCardH += dividerGap
                    }
                    thisCardH += rowH
                }
            }
            thisCardH += cardInnerPadding

            val cardRect = RectF(cardLeft, curY, cardRight, curY + thisCardH)
            canvas.drawRoundRect(cardRect, dp(16f), dp(16f), cardBgPaint)
            canvas.drawRoundRect(cardRect, dp(16f), dp(16f), cardBorderPaint)

            val innerLeft = cardLeft + cardInnerPadding
            val innerRight = cardRight - cardInnerPadding

            // 카드 헤더 (${diaText} DIA + 출퇴근 시각)
            val cardHeaderCenterY = curY + cardInnerPadding + (cardHeaderH / 2f)

            paint.textAlign = Paint.Align.LEFT
            paint.textSize = sp(20f)
            paint.typeface = getTypeface(700)
            paint.color = diaTitleColor
            val diaBaselineY = cardHeaderCenterY - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
            canvas.drawText("${card.diaText} DIA", innerLeft, diaBaselineY, paint)

            if (card.timeInfo.isNotEmpty()) {
                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = sp(14f)
                paint.typeface = getTypeface(700)
                paint.color = timeInfoColor
                val timeBaselineY = cardHeaderCenterY - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
                canvas.drawText(card.timeInfo, innerRight, timeBaselineY, paint)
            }

            var itemY = curY + cardInnerPadding + cardHeaderH + cardHeaderBottomGap

            if (card.segments.isEmpty()) {
                paint.textAlign = Paint.Align.LEFT
                paint.textSize = sp(13.5f)
                paint.typeface = getTypeface(400)
                paint.color = timeInfoColor
                canvas.drawText("상세 구간이 없습니다.", innerLeft, itemY + dp(22f), paint)
                itemY += dp(36f)
            } else {
                // 비례 가중치 그리드 수학적 계산 (와이드 뷰포트 5.8f 비례)
                // 트랙: 폭 20dp + 간격 6dp = 26dp
                val trackW = dp(20f)
                val trackGap = dp(6f)
                val trackCenterX = innerLeft + (trackW / 2f)

                val gridLeft = innerLeft + trackW + trackGap
                val gridRight = innerRight
                val gridTotalW = gridRight - gridLeft

                val arrowW = dp(32f)
                val availableWeightW = gridTotalW - arrowW
                // weights: col1(1.6f) + col2(1.1f) + col3(1.0f) + col5(1.1f) + col6(1.0f) = 5.8f
                val unitW = availableWeightW / 5.8f

                val col1W = unitW * 1.6f
                val col2W = unitW * 1.1f
                val col3W = unitW * 1.0f
                val col5W = unitW * 1.1f
                val col6W = unitW * 1.0f

                val col2CenterBase = gridLeft + col1W + (col2W * 0.5f)
                val col3Center = gridLeft + col1W + col2W + (col3W * 0.5f)
                val col4Center = gridLeft + col1W + col2W + col3W + (arrowW * 0.5f)
                val col5CenterBase = gridLeft + col1W + col2W + col3W + arrowW + (col5W * 0.5f)
                val col6Center = gridLeft + col1W + col2W + col3W + arrowW + col5W + (col6W * 0.5f)

                card.segments.forEachIndexed { sIdx, item ->
                    val seg = item.segment
                    val rawCat = item.name
                    val cat = when {
                        rawCat.contains("편승") -> "편승"
                        rawCat.contains("승무") -> "승무"
                        else -> rawCat
                    }
                    var trn = seg.trnNo?.trim().orEmpty()
                    if (cat == "편승" && trn == "도") trn = "도보"

                    val isRank1 = item.rank == 1
                    val isPrep = rawCat.contains("준비") || rawCat.contains("출고준비")
                    val prevCat = if (sIdx > 0) card.segments[sIdx - 1].name else ""
                    val prevIsPrep = prevCat.contains("준비") || prevCat.contains("출고준비")

                    // 준비 상단 구분선 (직전 버전 v1.0.30 롤백: 1.5dp, 50% alpha)
                    if (showPrepDivider && isPrep && !prevIsPrep && sIdx > 0) {
                        val divY = itemY + (dividerGap / 2f)
                        canvas.drawLine(innerLeft, divY, innerRight, divY, prepDividerPaint)
                        itemY += dividerGap
                    }

                    val rowCenterY = itemY + (rowH / 2f)

                    // Rank 1 운전/승무 행 강조 배경 박스 (카드 내부 전체 너비에 8dp 라운드 적용)
                    if (isRank1) {
                        val rowBgRect = RectF(innerLeft, itemY + dp(4f), innerRight, itemY + rowH - dp(4f))
                        canvas.drawRoundRect(rowBgRect, dp(8f), dp(8f), rank1RowBgPaint)
                    }

                    // 1. 수직 노선도 트랙
                    val circleR = dp(4.5f)
                    val topY = if (sIdx == 0) rowCenterY else itemY
                    val bottomY = if (sIdx == card.segments.lastIndex) rowCenterY else itemY + rowH

                    val currentLinePaint = when {
                        isRank1 -> rank1LinePaint
                        item.rank == 3 || cat.contains("휴게") -> dashedLinePaint
                        else -> defaultLinePaint
                    }

                    if (sIdx > 0) {
                        canvas.drawLine(trackCenterX, topY, trackCenterX, rowCenterY - circleR, currentLinePaint)
                    }
                    if (sIdx < card.segments.lastIndex) {
                        canvas.drawLine(trackCenterX, rowCenterY + circleR, trackCenterX, bottomY, currentLinePaint)
                    }

                    // 원점 마커
                    val dotColor = if (isRank1) rank1LineColor else outlineColor
                    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
                    canvas.drawCircle(trackCenterX, rowCenterY, circleR, dotPaint)

                    // 2. Col 1: 열차번호/항목 뱃지 (왼쪽 정렬, Bold 700)
                    var badgeActualRight = gridLeft
                    if (trn.isNotEmpty()) {
                        val badgeText = "$trn $cat"
                        paint.textSize = sp(11.5f)
                        paint.typeface = getTypeface(700)
                        val badgeTextW = paint.measureText(badgeText)
                        val badgePadH = dp(6f)
                        val badgePadV = dp(2.5f)
                        val badgeH = sp(11.5f) + (badgePadV * 2)
                        val badgeW = badgeTextW + (badgePadH * 2)
                        val badgeTop = rowCenterY - (badgeH / 2f)
                        val badgeRect = RectF(gridLeft, badgeTop, gridLeft + badgeW, badgeTop + badgeH)
                        badgeActualRight = badgeRect.right

                        val bgPaint = if (isRank1) rank1BadgeBgPaint else defaultBadgeBgPaint
                        canvas.drawRoundRect(badgeRect, dp(4f), dp(4f), bgPaint)

                        paint.textAlign = Paint.Align.LEFT
                        paint.color = if (isRank1) rank1BadgeTextColor else defaultBadgeTextColor
                        val badgeBaseline = badgeRect.centerY() - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
                        canvas.drawText(badgeText, gridLeft + badgePadH, badgeBaseline, paint)
                    } else if (cat.isNotEmpty()) {
                        paint.textSize = sp(11.5f)
                        paint.typeface = getTypeface(700)
                        paint.textAlign = Paint.Align.LEFT
                        paint.color = if (isDark) Color.parseColor("#CCF8FAFC") else Color.parseColor("#CC0F172A")
                        val catBaseline = rowCenterY - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
                        canvas.drawText(cat, gridLeft, catBaseline, paint)
                        badgeActualRight = gridLeft + paint.measureText(cat)
                    }

                    // 3. Col 2: 출발역 (중앙 정렬, Rank1: Bold 700, 일반: Medium 500)
                    // 동적 겹침 방지: 뱃지 우측에서 최소 10dp(30px) 안전 이격 강제
                    val dptStn = seg.dptStnNm?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
                    paint.textSize = if (isRank1) sp(13.5f) else sp(12.5f)
                    paint.typeface = getTypeface(if (isRank1) 700 else 500)
                    val stnW = paint.measureText(dptStn)
                    val safeMinStnLeft = badgeActualRight + dp(10f)
                    val col2CenterActual = maxOf(col2CenterBase, safeMinStnLeft + (stnW / 2f))

                    paint.textAlign = Paint.Align.CENTER
                    paint.color = stnTextColor
                    val stnBaseline = rowCenterY - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
                    canvas.drawText(dptStn, col2CenterActual, stnBaseline, paint)

                    // 4. Col 3: 출발시각 (중앙 정렬, primary ExtraBold 800)
                    val dptTime = seg.dptTm.toDisplayTime() ?: "-"
                    if (dptTime != "-") {
                        paint.textSize = sp(13f)
                        paint.typeface = getTypeface(800)
                        paint.textAlign = Paint.Align.CENTER
                        paint.color = primaryTimeColor
                        val timeBaseline = rowCenterY - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
                        canvas.drawText(dptTime, col3Center, timeBaseline, paint)
                    }

                    // 5. Col 4: 화살표 "→" (중앙 정렬, Normal 400)
                    val arvStn = seg.arvStnNm?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
                    val arvTime = seg.arvTm.toDisplayTime() ?: "-"
                    if (arvStn != "-" || arvTime != "-") {
                        paint.textSize = sp(13f)
                        paint.typeface = getTypeface(400)
                        paint.textAlign = Paint.Align.CENTER
                        paint.color = arrowColor
                        val arrowBaseline = rowCenterY - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
                        canvas.drawText("→", col4Center, arrowBaseline, paint)
                    }

                    // 6. Col 5: 도착역 (중앙 정렬, Rank1: Bold 700, 일반: Medium 500)
                    // 화살표 우측에서 최소 6dp 안전 이격 강제
                    if (arvStn != "-" || arvTime != "-") {
                        paint.textSize = if (isRank1) sp(13.5f) else sp(12.5f)
                        paint.typeface = getTypeface(if (isRank1) 700 else 500)
                        val arvStnW = paint.measureText(arvStn)
                        val arrowRight = col4Center + (arrowW / 2f)
                        val safeMinArvLeft = arrowRight + dp(6f)
                        val col5CenterActual = maxOf(col5CenterBase, safeMinArvLeft + (arvStnW / 2f))

                        paint.textAlign = Paint.Align.CENTER
                        paint.color = stnTextColor
                        canvas.drawText(arvStn, col5CenterActual, stnBaseline, paint)
                    }

                    // 7. Col 6: 도착시각 (중앙 정렬, primary ExtraBold 800)
                    if (arvTime != "-") {
                        paint.textSize = sp(13f)
                        paint.typeface = getTypeface(800)
                        paint.textAlign = Paint.Align.CENTER
                        paint.color = primaryTimeColor
                        val arvTimeBaseline = rowCenterY - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
                        canvas.drawText(arvTime, col6Center, arvTimeBaseline, paint)
                    }

                    itemY += rowH
                }
            }

            curY += thisCardH
            if (cIdx < cardDataList.lastIndex) {
                curY += cardGap
            }
        }

        return bitmap
    }

    private fun String?.toDisplayTime(): String? {
        val value = this?.trim() ?: return null
        if (value.length == 5 && value[2] == ':' && value.take(2).all(Char::isDigit) && value.takeLast(2).all(Char::isDigit)) return value
        if (value.length != 4 || !value.all(Char::isDigit)) return null
        return "${value.substring(0, 2)}:${value.substring(2, 4)}"
    }
}
