package com.example.crewmate.labs.routediagram

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.schedule.WorkSchedule
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val COLOR_RUN_BLUE = Color(0xFF1D4ED8)
private val COLOR_DEADHEAD_PURPLE = Color(0xFF7C3AED)
private val COLOR_SHUNT_TEAL = Color(0xFF0F766E)
private val COLOR_START_GREEN = Color(0xFF15803D)
private val COLOR_END_RED = Color(0xFFDC2626)

@Composable
fun RouteDiagramView(
    schedule: WorkSchedule,
    detail: DiaDetailResponse,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var masterRevision by remember { mutableIntStateOf(0) }
    var showMasterDialog by remember { mutableStateOf(false) }
    var showSummaryDetails by remember { mutableStateOf(false) } // 접어둔 상태가 기본

    val master = remember(masterRevision) { RailwayMasterRepository.loadMergedMaster(context) }
    val model = remember(detail, schedule, master) {
        RouteDiagramBuilder.build(detail, schedule, master)
    }

    val stations = model.stations
    if (stations.isEmpty()) {
        Text(
            text = "행로도를 그릴 역 구간 정보가 없습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        return
    }

    val density = LocalDensity.current
    val topHeaderHeightDp = 38.dp
    val startRowHeightDp = 36.dp
    val runRowHeightDp = 72.dp
    val dwellRowHeightDp = 54.dp
    val endRowHeightDp = 36.dp

    data class PlacedNode(
        val step: RouteStep,
        val yTopPx: Float,
        val yBottomPx: Float
    )

    val topHeaderPx = with(density) { topHeaderHeightDp.toPx() }
    val startRowPx = with(density) { startRowHeightDp.toPx() }
    val runRowPx = with(density) { runRowHeightDp.toPx() }
    val dwellRowPx = with(density) { dwellRowHeightDp.toPx() }
    val endRowPx = with(density) { endRowHeightDp.toPx() }

    val placedNodes = remember(model.steps, density) {
        var cursorY = topHeaderPx + with(density) { 14.dp.toPx() }
        model.steps.map { step ->
            val h = when (step) {
                is RouteStep.Start -> startRowPx
                is RouteStep.Run -> runRowPx
                is RouteStep.Dwell -> dwellRowPx
                is RouteStep.End -> endRowPx
            }
            val node = PlacedNode(step, cursorY, cursorY + h)
            cursorY += h
            node
        }
    }
    val totalHeightDp = with(density) {
        ((placedNodes.lastOrNull()?.yBottomPx ?: (topHeaderPx + 80f)) + with(density) { 18.dp.toPx() }).toDp()
    }

    val stationIndexMap = remember(stations) {
        stations.mapIndexed { idx, name -> name to idx }.toMap()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 상단 미니 툴바 (마스터 버전 표출)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧪 승무 행로도 (마스터 v${master.version})",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 화면 폭에 맞춘 행로도 캔버스 + 오버레이 텍스트 (가로 잘림 없이 한눈에 표출)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sidePadDp = 8.dp
            val colWidthDp = ((maxWidth - sidePadDp * 2) / stations.size).coerceAtLeast(54.dp)
            val totalDiagramWidthDp = (colWidthDp * stations.size + sidePadDp * 2).coerceAtLeast(maxWidth)
            val colWidthPx = with(density) { colWidthDp.toPx() }
            val sidePadPx = with(density) { sidePadDp.toPx() }
            fun xForStation(station: String): Float {
                val idx = stationIndexMap[station] ?: 0
                return sidePadPx + colWidthPx * idx + colWidthPx / 2f
            }

            val hScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScroll)
            ) {
                Box(
                    modifier = Modifier
                        .width(totalDiagramWidthDp)
                        .height(totalHeightDp)
                ) {
                    // 1. 역 헤더
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topHeaderHeightDp)
                            .padding(horizontal = sidePadDp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        stations.forEach { stn ->
                            Box(
                                modifier = Modifier
                                    .width(colWidthDp)
                                    .padding(horizontal = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = stn,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. 세로 가이드라인 및 선형 연결선 Canvas
                    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    val dwellNeutralColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        stations.indices.forEach { idx ->
                            val x = sidePadPx + colWidthPx * idx + colWidthPx / 2f
                            drawLine(
                                color = gridColor,
                                start = Offset(x, topHeaderPx),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dash
                            )
                        }

                        placedNodes.forEach { placed ->
                            when (val step = placed.step) {
                                is RouteStep.Start -> {
                                    val x = xForStation(step.station)
                                    val y = placed.yTopPx + (placed.yBottomPx - placed.yTopPx) * 0.5f
                                    drawCircle(color = COLOR_START_GREEN, radius = 4.5.dp.toPx(), center = Offset(x, y))
                                }
                                is RouteStep.End -> {
                                    val x = xForStation(step.station)
                                    val y = placed.yTopPx + (placed.yBottomPx - placed.yTopPx) * 0.5f
                                    drawCircle(color = COLOR_END_RED, radius = 4.5.dp.toPx(), center = Offset(x, y))
                                }
                                is RouteStep.Dwell -> {
                                    val x = xForStation(step.station)
                                    val yMid = (placed.yTopPx + placed.yBottomPx) * 0.5f
                                    // 감시·출고준비·입환은 파란색 실선(1.6dp), 정리·승계·준비·휴게·입고정리는 점선(1.6dp)
                                    val strokeColor = if (step.isBlueCore) COLOR_RUN_BLUE else dwellNeutralColor
                                    val effect = if (step.isBlueCore) null else dash
                                    drawLine(
                                        color = strokeColor,
                                        start = Offset(x, placed.yTopPx),
                                        end = Offset(x, placed.yBottomPx),
                                        strokeWidth = 1.6.dp.toPx(),
                                        pathEffect = effect
                                    )
                                    if (step.isWatchOrPrepNode) {
                                        drawCircle(
                                            color = COLOR_RUN_BLUE,
                                            radius = 3.5.dp.toPx(),
                                            center = Offset(x, yMid)
                                        )
                                    }
                                }
                                is RouteStep.Run -> {
                                    val x1 = xForStation(step.fromStation)
                                    val x2 = xForStation(step.toStation)
                                    val y = placed.yTopPx + (placed.yBottomPx - placed.yTopPx) * 0.5f
                                    val lineColor = when {
                                        step.isDeadhead -> COLOR_DEADHEAD_PURPLE
                                        else -> COLOR_RUN_BLUE
                                    }
                                    val strokeWidth = if (step.isWatch || step.isShunt) 1.8.dp.toPx() else 3.2.dp.toPx()
                                    val effect = if (step.isDeadhead) PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) else null

                                    drawLine(
                                        color = lineColor,
                                        start = Offset(x1, y),
                                        end = Offset(x2, y),
                                        strokeWidth = strokeWidth,
                                        pathEffect = effect
                                    )
                                    drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = Offset(x1, y))
                                    drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = Offset(x2, y))
                                }
                            }
                        }
                    }

                    // 3. 텍스트 & 뱃지 오버레이 (시각 강조 + 겹침 방지)
                    val surfaceBg = MaterialTheme.colorScheme.surface
                    placedNodes.forEach { placed ->
                        when (val step = placed.step) {
                            is RouteStep.Start -> {
                                val x = xForStation(step.station)
                                val y = placed.yTopPx + (placed.yBottomPx - placed.yTopPx) * 0.5f
                                val timeStr = RouteDiagramBuilder.formatTimeDisplay(step.dptTm)
                                Text(
                                    text = "출근 $timeStr",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = COLOR_START_GREEN,
                                    modifier = Modifier
                                        .offset { IntOffset((x + 8.dp.toPx()).roundToInt(), (y - 9.dp.toPx()).roundToInt()) }
                                        .background(surfaceBg.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 3.dp)
                                )
                            }
                            is RouteStep.End -> {
                                val x = xForStation(step.station)
                                val y = placed.yTopPx + (placed.yBottomPx - placed.yTopPx) * 0.5f
                                val timeStr = RouteDiagramBuilder.formatTimeDisplay(step.arvTm)
                                Text(
                                    text = "퇴근 $timeStr",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = COLOR_END_RED,
                                    modifier = Modifier
                                        .offset { IntOffset((x + 8.dp.toPx()).roundToInt(), (y - 9.dp.toPx()).roundToInt()) }
                                        .background(surfaceBg.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 3.dp)
                                )
                            }
                            is RouteStep.Dwell -> {
                                val x = xForStation(step.station)
                                val yMid = (placed.yTopPx + placed.yBottomPx) * 0.5f
                                val stnIdx = stationIndexMap[step.station] ?: 0
                                val isRightmost = stnIdx == stations.lastIndex && stations.size > 1
                                val maxLabelW = 108.dp
                                val maxLabelWPx = with(density) { maxLabelW.toPx() }
                                if (step.label.isNotEmpty()) {
                                    val xOffset = if (isRightmost) x - maxLabelWPx - with(density) { 6.dp.toPx() } else x + with(density) { 6.dp.toPx() }
                                    Box(
                                        modifier = Modifier
                                            .width(maxLabelW)
                                            .offset { IntOffset(xOffset.roundToInt(), (yMid - 9.dp.toPx()).roundToInt()) },
                                        contentAlignment = if (isRightmost) Alignment.CenterEnd else Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = step.label,
                                            fontSize = 10.sp,
                                            lineHeight = 12.sp,
                                            maxLines = 2,
                                            textAlign = if (isRightmost) TextAlign.End else TextAlign.Start,
                                            fontWeight = if (step.isBlueCore) FontWeight.Bold else FontWeight.Medium,
                                            color = if (step.isBlueCore) COLOR_RUN_BLUE else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .background(surfaceBg.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (step.displayStartTime.isNotEmpty()) {
                                    val timeOnLeft = !isRightmost && stnIdx > 0
                                    val tOffset = if (timeOnLeft) x - with(density) { 42.dp.toPx() } else x + with(density) { 6.dp.toPx() }
                                    val tY = if (timeOnLeft) yMid - with(density) { 8.dp.toPx() } else yMid + with(density) { 9.dp.toPx() }
                                    Text(
                                        text = step.displayStartTime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .offset { IntOffset(tOffset.roundToInt(), tY.roundToInt()) }
                                            .background(surfaceBg.copy(alpha = 0.92f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 2.dp)
                                    )
                                }
                            }
                            is RouteStep.Run -> {
                                val x1 = xForStation(step.fromStation)
                                val x2 = xForStation(step.toStation)
                                val yMid = placed.yTopPx + (placed.yBottomPx - placed.yTopPx) * 0.5f
                                val isRightward = x2 >= x1
                                val arrow = if (isRightward) "▶" else "◀"
                                val textColor = if (step.isDeadhead) COLOR_DEADHEAD_PURPLE else COLOR_RUN_BLUE
                                val prefix = if (step.isDeadhead) "편) " else ""
                                val trainLabel = if (isRightward) "$prefix${step.trnNo} $arrow" else "$arrow $prefix${step.trnNo}"

                                val midX = (x1 + x2) * 0.5f
                                val labelW = with(density) { 84.dp.toPx() }
                                // 열차번호: 뱃지 없이 행로 선 위에 시각과 동일한 크기(12.sp)로 표기
                                Box(
                                    modifier = Modifier
                                        .width(84.dp)
                                        .offset { IntOffset((midX - labelW / 2f).roundToInt(), (yMid - 21.dp.toPx()).roundToInt()) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = trainLabel.trim(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textColor,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .background(surfaceBg.copy(alpha = 0.92f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 2.dp)
                                    )
                                }

                                // 출발·도착 시각: 선 아래(yMid + 8.dp)에 큰 글씨(12.sp)로 배치해 선·열번과 겹침 방지
                                val dptStr = RouteDiagramBuilder.formatTimeDisplay(step.dptTm)
                                val arvStr = RouteDiagramBuilder.formatTimeDisplay(step.arvTm)
                                if (dptStr.isNotEmpty()) {
                                    Text(
                                        text = dptStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .offset { IntOffset((x1 - 18.dp.toPx()).roundToInt(), (yMid + 8.dp.toPx()).roundToInt()) }
                                            .background(surfaceBg.copy(alpha = 0.92f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 2.dp)
                                    )
                                }
                                if (arvStr.isNotEmpty()) {
                                    Text(
                                        text = arvStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .offset { IntOffset((x2 - 18.dp.toPx()).roundToInt(), (yMid + 8.dp.toPx()).roundToInt()) }
                                            .background(surfaceBg.copy(alpha = 0.92f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 하단 접이식 사업시간/거리 요약 보기 (접어둔 상태가 기본)
        Spacer(Modifier.height(8.dp))
        val h = model.totalTimeMinutes / 60
        val m = model.totalTimeMinutes % 60
        val totalTimeFormatted = "%02d:%02d".format(h, m)
        val totalDistFormatted = String.format(Locale.US, "%.1fkm", model.totalDistanceKm)

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSummaryDetails = !showSummaryDetails }
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "사업시간 / 거리 요약 보기",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (showSummaryDetails) "접기 ▲" else "펼치기 ▼",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = showSummaryDetails) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("총 사업시간", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(totalTimeFormatted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("총 거리", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(totalDistFormatted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (model.startTime.isNotEmpty() || model.endTime.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("출근 ~ 퇴근", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${model.startTime} ~ ${model.endTime}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        detail.summaries.forEachIndexed { idx, summary ->
                            val label = summary.pjtHrDvNm?.takeIf { it.isNotBlank() } ?: "요약 ${idx + 1}"
                            val mins = summary.pjtTnum
                            if (mins != null && mins > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "%02d:%02d".format(mins / 60, mins % 60),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMasterDialog) {
        RailwayMasterDialog(
            master = master,
            lastSyncMeta = RailwayMasterRepository.getLastSyncMeta(context),
            onSync = {
                scope.launch {
                    val res = RailwayMasterRepository.syncFromRemotes(context)
                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    masterRevision++
                }
            },
            onAddDepot = { depot, parent, pos ->
                RailwayMasterRepository.addCustomDepot(context, depot, parent, pos)
                masterRevision++
            },
            onClearCustom = {
                RailwayMasterRepository.clearCustomMaster(context)
                masterRevision++
            },
            onDismiss = { showMasterDialog = false }
        )
    }
}

@Composable
private fun RailwayMasterDialog(
    master: RailwayMasterData,
    lastSyncMeta: String,
    onSync: () -> Unit,
    onAddDepot: (String, String, String) -> Unit,
    onClearCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    var depotInput by remember { mutableStateOf("") }
    var parentInput by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("after") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("노선·기지 마스터 통합 관리", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "현재 적용: v${master.version} (${master.updatedAt.ifEmpty { "내장" }}) · 노선 ${master.lines.size}개, 기지 ${master.depots.size}개",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (lastSyncMeta.isNotEmpty()) {
                    Text(
                        text = "최근 크로스체크: $lastSyncMeta",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                    Text("GitHub ↔ Alpha 크로스체크 최신화", fontSize = 12.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("로컬 기지/화물역 커스텀 추가", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = depotInput,
                    onValueChange = { depotInput = it },
                    label = { Text("기지/화물역명 (예: 시흥기지)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = parentInput,
                    onValueChange = { parentInput = it },
                    label = { Text("인접 본선역 (예: 오이도)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { position = "before" },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            if (position == "before") 2.dp else 1.dp,
                            if (position == "before") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) { Text("앞(상행쪽)", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { position = "after" },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            if (position == "after") 2.dp else 1.dp,
                            if (position == "after") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) { Text("뒤(하행쪽)", fontSize = 11.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (depotInput.isNotBlank() && parentInput.isNotBlank()) {
                                onAddDepot(depotInput, parentInput, position)
                                depotInput = ""
                                parentInput = ""
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("추가", fontSize = 12.sp) }
                    OutlinedButton(onClick = onClearCustom, modifier = Modifier.weight(1f)) {
                        Text("커스텀 초기화", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}
