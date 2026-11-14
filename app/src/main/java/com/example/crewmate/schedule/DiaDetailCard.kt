package com.example.crewmate.schedule

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import java.util.Locale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DiaDetailCard(
    schedule: WorkSchedule,
    detail: DiaDetailResponse,
    workDate: LocalDate? = null,
    forceShowAllDetails: Boolean? = null,
    isCaptureMode: Boolean = false,
) {
    val diaPrefix = if (schedule.isContinuation) "~" else ""
    val diaText = "$diaPrefix${schedule.diaNo.orEmpty()}"

    val computedStartTime = schedule.startTime?.takeIf { it.isNotBlank() }
        ?: detail.segments.mapNotNull { it.dptTm?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull()
    val computedEndTime = schedule.endTime?.takeIf { it.isNotBlank() }
        ?: detail.segments.mapNotNull { it.arvTm?.trim()?.takeIf(String::isNotEmpty) }.lastOrNull()

    val startTimeStr = computedStartTime.toDisplayTime() ?: ""
    val endTimeStr = computedEndTime.toDisplayTime() ?: ""
    val timeInfo = when {
        startTimeStr.isNotEmpty() && endTimeStr.isNotEmpty() -> "출근 $startTimeStr / 퇴근 $endTimeStr"
        startTimeStr.isNotEmpty() -> "출근 $startTimeStr"
        endTimeStr.isNotEmpty() -> "퇴근 $endTimeStr"
        else -> ""
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val routeDiagramLabEnabled = com.example.crewmate.labs.rememberLabEnabled(com.example.crewmate.labs.LabFeature.ROUTE_DIAGRAM)
    var showRouteDiagram by remember(routeDiagramLabEnabled, isCaptureMode) {
        mutableStateOf(routeDiagramLabEnabled && !isCaptureMode)
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${diaText} DIA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (timeInfo.isNotEmpty()) {
                    Text(
                        text = timeInfo,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (detail.segments.isEmpty()) {
                Text(
                    text = "상세 구간이 없습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                if (routeDiagramLabEnabled && !isCaptureMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = !showRouteDiagram,
                            onClick = { showRouteDiagram = false },
                            label = { Text("타임라인", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        androidx.compose.material3.FilterChip(
                            selected = showRouteDiagram,
                            onClick = { showRouteDiagram = true },
                            label = { Text("🧪 행로도", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
                if (routeDiagramLabEnabled && showRouteDiagram && !isCaptureMode) {
                    com.example.crewmate.labs.LabSafeContainer(
                        feature = com.example.crewmate.labs.LabFeature.ROUTE_DIAGRAM,
                        onDisable = { showRouteDiagram = false }
                    ) {
                        com.example.crewmate.labs.routediagram.RouteDiagramView(
                            schedule = schedule,
                            detail = detail
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val prefs = remember { context.getSharedPreferences("crewmate_prefs", android.content.Context.MODE_PRIVATE) }
                    var hideSandwichedGamsi by remember { mutableStateOf(prefs.getBoolean("hide_sandwiched_gamsi", true)) }
                    var showPrepDivider by remember { mutableStateOf(prefs.getBoolean("show_prep_divider", true)) }

                    androidx.compose.runtime.DisposableEffect(prefs) {
                        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                            if (key == "hide_sandwiched_gamsi") {
                                hideSandwichedGamsi = sp.getBoolean("hide_sandwiched_gamsi", true)
                            }
                            if (key == "show_prep_divider") {
                                showPrepDivider = sp.getBoolean("show_prep_divider", true)
                            }
                        }
                        prefs.registerOnSharedPreferenceChangeListener(listener)
                        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
                    }

                    val categorizedSegments = remember(detail.segments, workDate) { categorizeSegments(detail.segments, workDate) }

                    var showHiddenDetailsState by remember { mutableStateOf(false) }
                    val showHiddenDetails = forceShowAllDetails ?: showHiddenDetailsState

                    categorizedSegments.forEachIndexed { index, item ->
                        val seg = item.segment
                        val rawCat = item.name
                        val cat = when {
                            rawCat.contains("편승") -> "편승"
                            rawCat.contains("승무") -> "승무"
                            else -> rawCat
                        }
                        var trn = seg.trnNo?.trim()?.takeIf(String::isNotEmpty) ?: ""
                        if (cat == "편승" && trn == "도") trn = "도보"
                        
                        val isRank3 = item.rank == 3
                        val isRank1 = item.rank == 1
                        
                        // 구분선 (준비, 출고준비 위) - 설정 토글이 켜져있고 이전 항목이 준비/출고준비가 아닐 때만
                        val isPrep = rawCat.contains("준비") || rawCat.contains("출고준비")
                        val prevCat = if (index > 0) categorizedSegments[index-1].name else ""
                        val prevIsPrep = prevCat.contains("준비") || prevCat.contains("출고준비")
                        
                        if (showPrepDivider && isPrep && !prevIsPrep && index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                thickness = 1.5.dp
                            )
                        }

                        val isSandwiched = item.isSandwichedGamsi && hideSandwichedGamsi
                        val isHiddenItem = isRank3 || isSandwiched

                        if (isHiddenItem) {
                            if (isCaptureMode) {
                                if (showHiddenDetails) {
                                    SegmentRow(item, isRank1, isRank3, cat, trn, index == categorizedSegments.lastIndex, workDate)
                                }
                            } else {
                                AnimatedVisibility(visible = showHiddenDetails) {
                                    SegmentRow(item, isRank1, isRank3, cat, trn, index == categorizedSegments.lastIndex, workDate)
                                }
                            }
                        } else {
                            SegmentRow(item, isRank1, isRank3, cat, trn, index == categorizedSegments.lastIndex, workDate)
                        }
                    }
                    
                    val hasHiddenItems = categorizedSegments.any { it.rank == 3 || (it.isSandwichedGamsi && hideSandwichedGamsi) }
                    if (hasHiddenItems && !isCaptureMode) {
                        TextButton(onClick = { showHiddenDetailsState = !showHiddenDetails }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showHiddenDetails) "상세 구간 접기" else "상세 구간 더보기", color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // 사업시간/거리 요약 정보 (캡처 모드에서는 제외하고 타임라인만 표출)
                    if (!isCaptureMode) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                        var showSummary by remember { mutableStateOf(false) }
                        TextButton(onClick = { showSummary = !showSummary }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showSummary) "사업시간/거리 요약 접기" else "사업시간/거리 요약 보기", color = Color(0xFF0D9488), fontWeight = FontWeight.Bold)
                        }
                        
                        AnimatedVisibility(visible = showSummary) {
                            DiaSummaryContent(detail)
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun DiaSummaryContent(detail: DiaDetailResponse) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        // 운용요일, 시행기간 표출
        val runDays = detail.extractRunDays()
        val effectiveTerm = detail.extractEffectiveTermStr()
        
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("운용요일", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(runDays, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("시행기간", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(effectiveTerm, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

        // 표출할 정보: 준비, 운전/승무, 감시, 휴게, 승계, 총사업시간, 총사업거리
        val prepTime = detail.summaries.find { it.pjtHrDvNm?.contains("준비") == true }?.pjtTnum ?: 0
        val driveTime = detail.summaries.filter { it.pjtHrDvNm?.contains("운전") == true || it.pjtHrDvNm?.contains("승무") == true }.sumOf { it.pjtTnum ?: 0 }
        val monitorTime = detail.summaries.find { it.pjtHrDvNm?.contains("감시") == true }?.pjtTnum ?: 0
        val restTime = detail.summaries.find { it.pjtHrDvNm?.contains("휴게") == true }?.pjtTnum ?: 0
        val transferTime = detail.summaries.find { it.pjtHrDvNm?.contains("승계") == true }?.pjtTnum ?: 0
        
        val totalTime = detail.summaries.firstOrNull()?.totWrkTnum ?: 0
        val totalDist = detail.summaries.firstOrNull()?.totPjtDst ?: 0.0

        fun Int.toHourMin() = String.format(Locale.US, "%02d:%02d", this / 60, this % 60)

        SummaryRow("준비", prepTime.toHourMin())
        SummaryRow("운전/승무", driveTime.toHourMin())
        SummaryRow("감시", monitorTime.toHourMin())
        SummaryRow("휴게", restTime.toHourMin())
        SummaryRow("승계", transferTime.toHourMin())
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
        SummaryRow("총사업시간", totalTime.toHourMin(), isTotal = true)
        SummaryRow("총사업거리", String.format(Locale.US, "%.1f km", totalDist), isTotal = true)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, isTotal: Boolean = false) {
    val fw = if (isTotal) FontWeight.ExtraBold else FontWeight.Medium
    val color = if (isTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, fontWeight = fw, color = color)
        Text(value, fontSize = 12.sp, fontWeight = fw, color = color)
    }
}

// --- Data & Logic Helpers ---

data class CategorizedSegment(val segment: DiaDetailSegment, val name: String, val rank: Int, val isCurrent: Boolean = false, val isSandwichedGamsi: Boolean = false)

internal fun categorizeSegments(segments: List<DiaDetailSegment>, workDate: LocalDate?): List<CategorizedSegment> {
    val segmentDateTimes = computeSegmentDateTimes(segments, workDate)
    val now = LocalDateTime.now()

    val categorized = segments.mapIndexed { idx, seg ->
        val name = seg.pjtHrDvNm?.trim() ?: ""
        val rank = when {
            name.contains("운전") || name.contains("승무") || name.contains("출고준비") || name.contains("입환") -> 1
            name.contains("준비") || name.contains("감시") || name.contains("편승") -> 2
            name.contains("승계") || name.contains("휴게") || name.contains("정리") || name.contains("입고정리") -> 3
            else -> 3
        }
        val dt = segmentDateTimes.getOrNull(idx)
        val isCurrent = if (dt?.startDateTime != null && dt.endDateTime != null) {
            (now == dt.startDateTime || now.isAfter(dt.startDateTime)) && now.isBefore(dt.endDateTime)
        } else isCurrentSegment(seg, workDate)

        CategorizedSegment(seg, name, rank, isCurrent, false)
    }.toMutableList()

    for (i in 0 until categorized.size) {
        val curr = categorized[i]
        if (curr.name.contains("감시")) {
            var prevValid: CategorizedSegment? = null
            for (j in i - 1 downTo 0) {
                if (categorized[j].rank != 3) {
                    prevValid = categorized[j]
                    break
                }
            }
            var nextValid: CategorizedSegment? = null
            for (j in i + 1 until categorized.size) {
                if (categorized[j].rank != 3) {
                    nextValid = categorized[j]
                    break
                }
            }
            if (prevValid?.rank == 1 && nextValid?.rank == 1) {
                categorized[i] = curr.copy(isSandwichedGamsi = true)
            }
        }
    }

    // 3순위(휴게/정리 등) 항목에 현재시간 강조가 잡혀 접힌 상태에서 강조가 보이지 않는 문제 방지
    // 3순위(또는 숨겨진 감시)일 경우, 아래에서 가장 가까운 2순위/1순위에 강조 전달
    val currentIndex = categorized.indexOfFirst { it.isCurrent }
    if (currentIndex != -1) {
        val currentItem = categorized[currentIndex]
        val isHiddenByRank = currentItem.rank == 3 || currentItem.isSandwichedGamsi
        if (isHiddenByRank) {
            var targetIndex = -1
            // 1. 아래로 가장 가까운 2순위/1순위 찾기
            for (i in currentIndex + 1 until categorized.size) {
                val candidate = categorized[i]
                if (candidate.rank != 3 && !candidate.isSandwichedGamsi) {
                    targetIndex = i
                    break
                }
            }
            // 2. 아래에 없으면 위로 가장 가까운 2순위/1순위 찾기
            if (targetIndex == -1) {
                for (i in currentIndex - 1 downTo 0) {
                    val candidate = categorized[i]
                    if (candidate.rank != 3 && !candidate.isSandwichedGamsi) {
                        targetIndex = i
                        break
                    }
                }
            }
            if (targetIndex != -1) {
                categorized[currentIndex] = currentItem.copy(isCurrent = false)
                categorized[targetIndex] = categorized[targetIndex].copy(isCurrent = true)
            }
        }
    }

    return categorized
}

@Composable
private fun SegmentRow(
    item: CategorizedSegment,
    isRank1: Boolean,
    isRank3: Boolean,
    cat: String,
    trn: String,
    isLast: Boolean,
    workDate: LocalDate? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val seg = item.segment
    val isCurrent = item.isCurrent
    val rowBg = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else if (isRank1) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    
    val timelineColor = if (isRank1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else if (isRank3) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface

    val dptStn = seg.dptStnNm?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
    val arvStn = seg.arvStnNm?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
    val dptTime = seg.dptTm.toDisplayTime() ?: "-"
    val arvTime = seg.arvTm.toDisplayTime() ?: "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 수직 노선도 (제일 왼쪽)
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(36.dp)
                .drawBehind {
                    val strokeW = 3.dp.toPx()
                    val circleR = 4.dp.toPx()
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    
                    // Top line (except first)
                    drawLine(
                        color = timelineColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, centerY - circleR),
                        strokeWidth = strokeW,
                        pathEffect = if (isRank3 || cat.contains("휴게")) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                    )
                    
                    // Bottom line (except last)
                    if (!isLast) {
                        drawLine(
                            color = timelineColor,
                            start = Offset(centerX, centerY + circleR),
                            end = Offset(centerX, size.height),
                            strokeWidth = strokeW,
                            pathEffect = if (isRank3 || cat.contains("휴게")) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                        )
                    }

                    // Circle marker
                    drawCircle(
                        color = if (isCurrent) Color(0xFF60A5FA) else timelineColor,
                        radius = if (isCurrent) 6.dp.toPx() else circleR,
                        center = Offset(centerX, centerY)
                    )
                }
        )

        Spacer(Modifier.width(4.dp))

        // 2. 열차번호 / 항목 뱃지 (Grid Column 1)
        Box(
            modifier = Modifier.weight(1.4f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (trn.isNotEmpty()) {
                Surface(
                    color = if (isRank1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable {
                        val dateStr = (workDate ?: LocalDate.now()).format(DateTimeFormatter.BASIC_ISO_DATE)
                        val cleanTrn = trn.trim()
                        val url = "https://rail.blue/railroad/logis/Default.aspx?date=$dateStr&train=$cleanTrn#!"
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text(
                        text = "$trn $cat",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRank1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            } else if (cat.isNotEmpty()) {
                Text(
                    text = cat,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }

        // 3. 출발역 (Grid Column 2)
        Box(
            modifier = Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dptStn,
                fontSize = if (isCurrent || isRank1) 13.sp else 12.sp,
                fontWeight = if (isCurrent || isRank1) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        // 4. 출발시각 (Grid Column 3)
        Box(
            modifier = Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            if (dptTime != "-") {
                Text(
                    text = dptTime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        // 5. 화살표 (Grid Column 4 - Balanced center placement)
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (arvStn != "-" || arvTime != "-") {
                Text(
                    text = "→",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // 6. 도착역 (Grid Column 5)
        Box(
            modifier = Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            if (arvStn != "-" || arvTime != "-") {
                Text(
                    text = arvStn,
                    fontSize = if (isCurrent || isRank1) 13.sp else 12.sp,
                    fontWeight = if (isCurrent || isRank1) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        // 7. 도착시각 (Grid Column 6)
        Box(
            modifier = Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            if (arvStn != "-" || arvTime != "-") {
                if (arvTime != "-") {
                    Text(
                        text = arvTime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}



private data class SegmentDateTime(
    val startDateTime: LocalDateTime?,
    val endDateTime: LocalDateTime?
)

private fun computeSegmentDateTimes(segments: List<DiaDetailSegment>, workDate: LocalDate?): List<SegmentDateTime> {
    if (workDate == null) return segments.map { SegmentDateTime(null, null) }

    var currentDate = workDate
    var lastHour = -1
    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    return segments.map { seg ->
        val startStr = seg.dptTm.toDisplayTime()
        val endStr = seg.arvTm.toDisplayTime()

        val startTime = runCatching { startStr?.let { LocalTime.parse(it, formatter) } }.getOrNull()
        val endTime = runCatching { endStr?.let { LocalTime.parse(it, formatter) } }.getOrNull()

        var startDateTime: LocalDateTime? = null
        var endDateTime: LocalDateTime? = null

        if (startTime != null) {
            if (lastHour in 18..23 && startTime.hour in 0..12) {
                currentDate = currentDate?.plusDays(1)
            }
            startDateTime = currentDate?.atTime(startTime)
            lastHour = startTime.hour
        }

        if (endTime != null) {
            if (lastHour in 18..23 && endTime.hour in 0..12) {
                currentDate = currentDate?.plusDays(1)
            }
            endDateTime = currentDate?.atTime(endTime)
            lastHour = endTime.hour
        }

        SegmentDateTime(startDateTime, endDateTime)
    }
}

private fun isCurrentSegment(segment: DiaDetailSegment, workDate: LocalDate?): Boolean {
    if (workDate == null) return false
    val now = LocalDateTime.now()

    val startStr = segment.dptTm.toDisplayTime() ?: return false
    val endStr = segment.arvTm.toDisplayTime() ?: return false

    try {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val startTime = LocalTime.parse(startStr, formatter)
        val endTime = LocalTime.parse(endStr, formatter)
        val currentTime = now.toLocalTime()

        // 시작 시간 <= 종료 시간 (당일)
        if (startTime.isBefore(endTime) || startTime == endTime) {
            return (currentTime == startTime || currentTime.isAfter(startTime)) && currentTime.isBefore(endTime)
        }
        // 시작 시간 > 종료 시간 (자정 넘어감)
        else {
            return currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
        }
    } catch (e: Exception) {
        return false
    }
}

private fun String?.toDisplayTime(): String? {
    val value = this?.trim() ?: return null
    if (value.length == 5 && value[2] == ':' && value.take(2).all(Char::isDigit) && value.takeLast(2).all(Char::isDigit)) return value
    if (value.length != 4 || !value.all(Char::isDigit)) return null
    return "${value.substring(0, 2)}:${value.substring(2, 4)}"
}