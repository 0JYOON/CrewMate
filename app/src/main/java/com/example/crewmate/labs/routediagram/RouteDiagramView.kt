package com.example.crewmate.labs.routediagram

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RouteDiagramView(
    schedule: WorkSchedule,
    detail: DiaDetailResponse,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var showSummaryDetails by remember { mutableStateOf(false) }

    val master = remember { RailwayMasterRepository.loadMergedMaster(context) }
    val model = remember(detail, schedule, master) {
        RouteDiagramBuilder.build(detail, schedule, master)
    }

    val stations = model.stations
    if (stations.isEmpty()) {
        Text(
            text = "행로도를 그릴 역 구간 정보가 없습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }

    // 라이트/다크 모드 대응 색상 팔레트
    val gridLineColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val runColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val deadheadColor = if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
    val startGreenColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
    val endRedColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
    val dwellNeutralColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baseStationPillBg = Color(0xFF1E3A8A)

    val density = LocalDensity.current
    val topHeaderHeightDp = 30.dp
    val startRowHeightDp = 22.dp
    val runRowHeightDp = 46.dp
    val dwellRowHeightDp = 56.dp
    val endRowHeightDp = 22.dp

    data class PlacedNode(
        val step: RouteStep,
        val yTopPx: Float,
        val yBottomPx: Float,
        val topBufPx: Float,
        val botBufPx: Float
    )

    val topHeaderPx = with(density) { topHeaderHeightDp.toPx() }
    val startRowPx = with(density) { startRowHeightDp.toPx() }
    val runRowPx = with(density) { runRowHeightDp.toPx() }
    val dwellRowPx = with(density) { dwellRowHeightDp.toPx() }
    val endRowPx = with(density) { endRowHeightDp.toPx() }

    val placedNodes = remember(model.steps, density) {
        var cursorY = topHeaderPx + with(density) { 10.dp.toPx() }
        model.steps.mapIndexed { idx, step ->
            val prev = model.steps.getOrNull(idx - 1)
            val next = model.steps.getOrNull(idx + 1)
            val topBuf = if (step is RouteStep.Dwell && prev is RouteStep.Run) with(density) { 22.dp.toPx() } else 0f
            val botBuf = if (step is RouteStep.Dwell && next is RouteStep.Run) with(density) { 20.dp.toPx() } else 0f
            val h = when (step) {
                is RouteStep.Start -> if (next is RouteStep.Run) startRowPx else 0f
                is RouteStep.Run -> if (next == null || next is RouteStep.Run || next is RouteStep.End) runRowPx else 0f
                is RouteStep.Dwell -> maxOf(dwellRowPx, topBuf + with(density) { 26.dp.toPx() } + botBuf)
                is RouteStep.End -> endRowPx
            }
            val node = PlacedNode(step, cursorY, cursorY + h, topBuf, botBuf)
            cursorY += h
            node
        }
    }
    val endDotY = placedNodes.lastOrNull()?.yTopPx ?: (topHeaderPx + 40f)
    val totalHeightDp = with(density) {
        (endDotY + with(density) { 18.dp.toPx() }).toDp()
    }

    val stationIndexMap = remember(stations) {
        stations.mapIndexed { idx, name -> name to idx }.toMap()
    }
    val startStn = remember(model.steps) {
        (model.steps.firstOrNull { it is RouteStep.Start } as? RouteStep.Start)?.station.orEmpty()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sidePadDp = 6.dp
            val colWidthDp = (maxWidth - sidePadDp * 2) / stations.size
            val colWidthPx = with(density) { colWidthDp.toPx() }
            val sidePadPx = with(density) { sidePadDp.toPx() }
            fun xForStation(station: String): Float {
                val idx = stationIndexMap[station] ?: 0
                return sidePadPx + colWidthPx * idx + colWidthPx / 2f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeightDp)
            ) {
                // 1. 최하단 전체 배경 그리드 + 행로 선형 Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridStroke = 1.dp.toPx()
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

                    // 끊김 없는 1dp 수직 실선 (#E2E8F0 / 다크 #334155)
                    stations.indices.forEach { idx ->
                        val x = sidePadPx + colWidthPx * idx + colWidthPx / 2f
                        drawLine(
                            color = gridLineColor,
                            start = Offset(x, topHeaderPx),
                            end = Offset(x, endDotY),
                            strokeWidth = gridStroke
                        )
                    }

                    placedNodes.forEachIndexed { idx, placed ->
                        val nextPlaced = placedNodes.getOrNull(idx + 1)
                        when (val step = placed.step) {
                            is RouteStep.Start -> {
                                val x = xForStation(step.station)
                                val y = placed.yTopPx
                                if (nextPlaced != null && nextPlaced.step is RouteStep.Run && nextPlaced.yTopPx > y) {
                                    drawLine(
                                        color = dwellNeutralColor,
                                        start = Offset(x, y),
                                        end = Offset(x, nextPlaced.yTopPx),
                                        strokeWidth = 1.5.dp.toPx(),
                                        pathEffect = dash
                                    )
                                }
                                drawCircle(color = startGreenColor, radius = 4.dp.toPx(), center = Offset(x, y))
                            }
                            is RouteStep.End -> {
                                val x = xForStation(step.station)
                                val y = placed.yTopPx
                                drawCircle(color = endRedColor, radius = 4.dp.toPx(), center = Offset(x, y))
                            }
                            is RouteStep.Dwell -> {
                                val x = xForStation(step.station)
                                val strokeColor = if (step.isBlueCore) runColor else dwellNeutralColor
                                val effect = if (step.isBlueCore) null else dash
                                drawLine(
                                    color = strokeColor,
                                    start = Offset(x, placed.yTopPx),
                                    end = Offset(x, placed.yBottomPx),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = effect
                                )
                                if (step.isWatchOrPrepNode) {
                                    drawCircle(color = runColor, radius = 3.5.dp.toPx(), center = Offset(x, placed.yTopPx))
                                    drawCircle(color = runColor, radius = 3.5.dp.toPx(), center = Offset(x, placed.yBottomPx))
                                }
                            }
                            is RouteStep.Run -> {
                                val x1 = xForStation(step.fromStation)
                                val x2 = xForStation(step.toStation)
                                val y = placed.yTopPx
                                if (nextPlaced != null && (nextPlaced.step is RouteStep.Run || nextPlaced.step is RouteStep.End) && nextPlaced.yTopPx > y) {
                                    drawLine(
                                        color = dwellNeutralColor,
                                        start = Offset(x2, y),
                                        end = Offset(x2, nextPlaced.yTopPx),
                                        strokeWidth = 1.5.dp.toPx(),
                                        pathEffect = dash
                                    )
                                }
                                val lineColor = if (step.isDeadhead) deadheadColor else runColor
                                val strokeWidth = if (step.isDeadhead) 1.8.dp.toPx() else 2.dp.toPx()
                                val effect = if (step.isDeadhead) dash else null
                                drawLine(
                                    color = lineColor,
                                    start = Offset(x1, y),
                                    end = Offset(x2, y),
                                    strokeWidth = strokeWidth,
                                    pathEffect = effect
                                )
                                drawCircle(color = lineColor, radius = 3.2.dp.toPx(), center = Offset(x1, y))
                                drawCircle(color = lineColor, radius = 3.2.dp.toPx(), center = Offset(x2, y))
                            }
                        }
                    }
                }

                // 2. 상단 역명 헤더 (소속/기준 역 1개만 진한 남색 알약, 나머지는 무테두리 일반 Text)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topHeaderHeightDp)
                        .padding(horizontal = sidePadDp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    stations.forEach { stn ->
                        val isBaseStn = (stn == startStn)
                        Box(
                            modifier = Modifier.width(colWidthDp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBaseStn) {
                                Text(
                                    text = stn,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(baseStationPillBg)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            } else {
                                Text(
                                    text = stn,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // 3. 순수 무테두리 Text 배치 (2D 충돌 회피)
                data class OccupiedRect(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
                val occupiedRects = mutableListOf<OccupiedRect>()

                fun estimateTextW(txt: String, spVal: Float): Float {
                    var w = 0f
                    val pxPerSp = with(density) { spVal.sp.toPx() }
                    for (ch in txt) {
                        w += if (ch in '\u3131'..'\uD79D') pxPerSp * 0.95f else pxPerSp * 0.6f
                    }
                    return maxOf(with(density) { 16.dp.toPx() }, w + with(density) { 4.dp.toPx() })
                }

                fun makeRect(x: Float, topY: Float, alignEnd: Boolean, alignCenter: Boolean, txt: String, spVal: Float): OccupiedRect {
                    val w = estimateTextW(txt, spVal)
                    val h = with(density) { (spVal + 3f).sp.toPx() }
                    val x1 = when {
                        alignCenter -> x - w / 2f
                        alignEnd -> x - w
                        else -> x
                    }
                    return OccupiedRect(x1, topY, x1 + w, topY + h)
                }

                fun intersectsAny(r: OccupiedRect): Boolean {
                    val padX = with(density) { 3.dp.toPx() }
                    val padY = with(density) { 2.dp.toPx() }
                    return occupiedRects.any { o ->
                        !(r.x2 + padX <= o.x1 || r.x1 >= o.x2 + padX || r.y2 + padY <= o.y1 || r.y1 >= o.y2 + padY)
                    }
                }

                fun placeAvoidingY(x: Float, topY: Float, alignEnd: Boolean, txt: String, spVal: Float, minY: Float, maxY: Float): Float {
                    val offsets = listOf(0f, 12f, -12f, 22f, -22f, 32f, -32f)
                    for (dyDp in offsets) {
                        val candY = topY + with(density) { dyDp.dp.toPx() }
                        val r = makeRect(x, candY, alignEnd, false, txt, spVal)
                        if (r.y1 < minY || r.y2 > maxY) continue
                        if (!intersectsAny(r)) {
                            occupiedRects.add(r)
                            return candY
                        }
                    }
                    occupiedRects.add(makeRect(x, topY, alignEnd, false, txt, spVal))
                    return topY
                }

                // 1차 패스: 고정 위치 텍스트 점유 등록
                placedNodes.forEach { placed ->
                    when (val step = placed.step) {
                        is RouteStep.Start -> {
                            val x = xForStation(step.station)
                            val y = placed.yTopPx
                            val isLeftmost = (stationIndexMap[step.station] ?: 0) == 0
                            val timeStr = RouteDiagramBuilder.formatTimeDisplay(step.dptTm)
                            val xOff = if (isLeftmost) x + with(density) { 6.dp.toPx() } else x - with(density) { 6.dp.toPx() }
                            occupiedRects.add(makeRect(xOff, y - with(density) { 8.dp.toPx() }, !isLeftmost, false, "출근 $timeStr", 11.5f))
                        }
                        is RouteStep.Run -> {
                            val x1 = xForStation(step.fromStation)
                            val x2 = xForStation(step.toStation)
                            val y = placed.yTopPx
                            val isRightward = x2 >= x1
                            val midX = (x1 + x2) * 0.5f
                            occupiedRects.add(makeRect(midX, y - with(density) { 17.dp.toPx() }, false, true, "${step.trnNo} ▶", 11.5f))
                            val dptStr = RouteDiagramBuilder.formatTimeDisplay(step.dptTm)
                            val arvStr = RouteDiagramBuilder.formatTimeDisplay(step.arvTm)
                            if (dptStr.isNotEmpty()) {
                                val xOff = if (isRightward) x1 + with(density) { 4.dp.toPx() } else x1 - with(density) { 4.dp.toPx() }
                                occupiedRects.add(makeRect(xOff, y + with(density) { 4.dp.toPx() }, !isRightward, false, dptStr, 11.5f))
                            }
                            if (arvStr.isNotEmpty()) {
                                val xOff = if (isRightward) x2 - with(density) { 4.dp.toPx() } else x2 + with(density) { 4.dp.toPx() }
                                occupiedRects.add(makeRect(xOff, y + with(density) { 4.dp.toPx() }, isRightward, false, arvStr, 11.5f))
                            }
                        }
                        is RouteStep.End -> {
                            val x = xForStation(step.station)
                            val y = placed.yTopPx
                            val isRightmost = (stationIndexMap[step.station] ?: 0) == stations.lastIndex && stations.size > 1
                            val timeStr = RouteDiagramBuilder.formatTimeDisplay(step.arvTm)
                            val xOff = if (isRightmost) x - with(density) { 6.dp.toPx() } else x + with(density) { 6.dp.toPx() }
                            occupiedRects.add(makeRect(xOff, y - with(density) { 8.dp.toPx() }, isRightmost, false, "퇴근 $timeStr", 11.5f))
                        }
                        else -> {}
                    }
                }

                // 2차 패스: 실제 무테두리 Text 배치
                placedNodes.forEachIndexed { idx, placed ->
                    when (val step = placed.step) {
                        is RouteStep.Start -> {
                            val x = xForStation(step.station)
                            val y = placed.yTopPx
                            val isLeftmost = (stationIndexMap[step.station] ?: 0) == 0
                            val timeStr = RouteDiagramBuilder.formatTimeDisplay(step.dptTm)
                            val boxW = 90.dp
                            val boxWPx = with(density) { boxW.toPx() }
                            val xOff = if (isLeftmost) x + with(density) { 6.dp.toPx() } else x - boxWPx - with(density) { 6.dp.toPx() }
                            Box(
                                modifier = Modifier
                                    .width(boxW)
                                    .offset { IntOffset(xOff.roundToInt(), (y - 8.dp.toPx()).roundToInt()) },
                                contentAlignment = if (isLeftmost) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                Text(
                                    text = "출근 $timeStr",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = startGreenColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        is RouteStep.End -> {
                            val x = xForStation(step.station)
                            val y = placed.yTopPx
                            val isRightmost = (stationIndexMap[step.station] ?: 0) == stations.lastIndex && stations.size > 1
                            val timeStr = RouteDiagramBuilder.formatTimeDisplay(step.arvTm)
                            val boxW = 90.dp
                            val boxWPx = with(density) { boxW.toPx() }
                            val xOff = if (isRightmost) x - boxWPx - with(density) { 6.dp.toPx() } else x + with(density) { 6.dp.toPx() }
                            Box(
                                modifier = Modifier
                                    .width(boxW)
                                    .offset { IntOffset(xOff.roundToInt(), (y - 8.dp.toPx()).roundToInt()) },
                                contentAlignment = if (isRightmost) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Text(
                                    text = "퇴근 $timeStr",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = endRedColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        is RouteStep.Dwell -> {
                            val x = xForStation(step.station)
                            val stnIdx = stationIndexMap[step.station] ?: 0
                            val isLeftmost = stnIdx == 0
                            val isRightmost = stnIdx == stations.lastIndex && stations.size > 1
                            val prevRun = model.steps.subList(0, idx).lastOrNull { it is RouteStep.Run } as? RouteStep.Run
                            val nextRun = model.steps.subList(idx + 1, model.steps.size).firstOrNull { it is RouteStep.Run } as? RouteStep.Run
                            var hasLeftTrack = false
                            var hasRightTrack = false
                            if (prevRun != null && prevRun.toStation == step.station) {
                                val pX = xForStation(prevRun.fromStation)
                                if (pX < x) hasLeftTrack = true else if (pX > x) hasRightTrack = true
                            }
                            if (nextRun != null && nextRun.fromStation == step.station) {
                                val nX = xForStation(nextRun.toStation)
                                if (nX < x) hasLeftTrack = true else if (nX > x) hasRightTrack = true
                            }
                            val labelOnRight = when {
                                isRightmost -> false
                                isLeftmost -> true
                                hasLeftTrack && !hasRightTrack -> true
                                !hasLeftTrack && hasRightTrack -> false
                                hasLeftTrack && hasRightTrack -> (prevRun?.fromStation?.let(::xForStation) ?: 0f) <= x
                                else -> true
                            }

                            val safeTop = placed.yTopPx + placed.topBufPx
                            val safeBot = placed.yBottomPx - placed.botBufPx
                            val midSafeY = (safeTop + safeBot) * 0.5f
                            val labelSp = if (step.isBlueCore) 11.5f else 10f
                            val maxLabelW = 160.dp
                            val maxLabelWPx = with(density) { maxLabelW.toPx() }

                            if (step.label.isNotEmpty()) {
                                val anchorX = if (labelOnRight) x + with(density) { 6.dp.toPx() } else x - with(density) { 6.dp.toPx() }
                                val placedTopY = placeAvoidingY(
                                    x = anchorX,
                                    topY = midSafeY - with(density) { 7.dp.toPx() },
                                    alignEnd = !labelOnRight,
                                    txt = step.label,
                                    spVal = labelSp,
                                    minY = placed.yTopPx + placed.topBufPx,
                                    maxY = placed.yBottomPx - placed.botBufPx + with(density) { 8.dp.toPx() }
                                )
                                val xOff = if (labelOnRight) anchorX else anchorX - maxLabelWPx
                                Box(
                                    modifier = Modifier
                                        .width(maxLabelW)
                                        .offset { IntOffset(xOff.roundToInt(), placedTopY.roundToInt()) },
                                    contentAlignment = if (labelOnRight) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = step.label,
                                        fontSize = labelSp.sp,
                                        fontWeight = if (step.isBlueCore) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = if (step.isBlueCore) runColor else secondaryTextColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            if (step.displayStartTime.isNotEmpty()) {
                                val tmAnchorX = if (labelOnRight) x - with(density) { 6.dp.toPx() } else x + with(density) { 6.dp.toPx() }
                                val placedTmY = placeAvoidingY(
                                    x = tmAnchorX,
                                    topY = placed.yTopPx - with(density) { 7.dp.toPx() },
                                    alignEnd = labelOnRight,
                                    txt = step.displayStartTime,
                                    spVal = 11.5f,
                                    minY = placed.yTopPx - with(density) { 10.dp.toPx() },
                                    maxY = placed.yBottomPx + with(density) { 8.dp.toPx() }
                                )
                                val tmW = 44.dp
                                val tmWPx = with(density) { tmW.toPx() }
                                val xOff = if (labelOnRight) tmAnchorX - tmWPx else tmAnchorX
                                Box(
                                    modifier = Modifier
                                        .width(tmW)
                                        .offset { IntOffset(xOff.roundToInt(), placedTmY.roundToInt()) },
                                    contentAlignment = if (labelOnRight) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Text(
                                        text = step.displayStartTime,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            if (step.displayEndTime.isNotEmpty()) {
                                val tmAnchorX = if (labelOnRight) x - with(density) { 6.dp.toPx() } else x + with(density) { 6.dp.toPx() }
                                val placedTmY = placeAvoidingY(
                                    x = tmAnchorX,
                                    topY = placed.yBottomPx - with(density) { 7.dp.toPx() },
                                    alignEnd = labelOnRight,
                                    txt = step.displayEndTime,
                                    spVal = 11.5f,
                                    minY = placed.yTopPx - with(density) { 8.dp.toPx() },
                                    maxY = placed.yBottomPx + with(density) { 10.dp.toPx() }
                                )
                                val tmW = 44.dp
                                val tmWPx = with(density) { tmW.toPx() }
                                val xOff = if (labelOnRight) tmAnchorX - tmWPx else tmAnchorX
                                Box(
                                    modifier = Modifier
                                        .width(tmW)
                                        .offset { IntOffset(xOff.roundToInt(), placedTmY.roundToInt()) },
                                    contentAlignment = if (labelOnRight) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Text(
                                        text = step.displayEndTime,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        is RouteStep.Run -> {
                            val x1 = xForStation(step.fromStation)
                            val x2 = xForStation(step.toStation)
                            val y = placed.yTopPx
                            val isRightward = x2 >= x1
                            val arrow = if (isRightward) "▶" else "◀"
                            val textColor = if (step.isDeadhead) deadheadColor else runColor
                            val prefix = if (step.isDeadhead) "편) " else ""
                            val trainLabel = (if (isRightward) "$prefix${step.trnNo} $arrow" else "$arrow $prefix${step.trnNo}").trim()
                            val midX = (x1 + x2) * 0.5f
                            val labelW = 92.dp
                            val labelWPx = with(density) { labelW.toPx() }
                            Box(
                                modifier = Modifier
                                    .width(labelW)
                                    .offset { IntOffset((midX - labelWPx / 2f).roundToInt(), (y - 17.dp.toPx()).roundToInt()) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = trainLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            val dptStr = RouteDiagramBuilder.formatTimeDisplay(step.dptTm)
                            val arvStr = RouteDiagramBuilder.formatTimeDisplay(step.arvTm)
                            val timeW = 42.dp
                            val timeWPx = with(density) { timeW.toPx() }
                            if (dptStr.isNotEmpty()) {
                                val dptOff = if (isRightward) x1 + with(density) { 4.dp.toPx() } else x1 - timeWPx - with(density) { 4.dp.toPx() }
                                Box(
                                    modifier = Modifier
                                        .width(timeW)
                                        .offset { IntOffset(dptOff.roundToInt(), (y + 4.dp.toPx()).roundToInt()) },
                                    contentAlignment = if (isRightward) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = dptStr,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = runColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            if (arvStr.isNotEmpty()) {
                                val arvOff = if (isRightward) x2 - timeWPx - with(density) { 4.dp.toPx() } else x2 + with(density) { 4.dp.toPx() }
                                Box(
                                    modifier = Modifier
                                        .width(timeW)
                                        .offset { IntOffset(arvOff.roundToInt(), (y + 4.dp.toPx()).roundToInt()) },
                                    contentAlignment = if (isRightward) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Text(
                                        text = arvStr,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 하단 접이식 사업시간/거리 요약 보기 (타임라인과 동일 항목 전체 표출)
        HorizontalDivider(color = gridLineColor, thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSummaryDetails = !showSummaryDetails }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "사업시간 / 거리 요약 보기",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D9488)
            )
            Text(
                text = if (showSummaryDetails) "접기 ▲" else "펼치기 ▼",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(visible = showSummaryDetails) {
            val runDays = detail.extractRunDays()
            val effectiveTerm = detail.extractEffectiveTermStr()
            val prepTime = detail.summaries.find { it.pjtHrDvNm?.contains("준비") == true }?.pjtTnum ?: 0
            val driveTime = detail.summaries.filter { it.pjtHrDvNm?.contains("운전") == true || it.pjtHrDvNm?.contains("승무") == true }.sumOf { it.pjtTnum ?: 0 }
            val monitorTime = detail.summaries.find { it.pjtHrDvNm?.contains("감시") == true }?.pjtTnum ?: 0
            val restTime = detail.summaries.find { it.pjtHrDvNm?.contains("휴게") == true }?.pjtTnum ?: 0
            val transferTime = detail.summaries.find { it.pjtHrDvNm?.contains("승계") == true }?.pjtTnum ?: 0
            val totalTime = detail.summaries.firstOrNull()?.totWrkTnum ?: model.totalTimeMinutes
            val totalDist = detail.summaries.firstOrNull()?.totPjtDst ?: model.totalDistanceKm
            fun Int.toHHmm() = String.format(Locale.US, "%02d:%02d", this / 60, this % 60)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("운용요일", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(runDays, fontSize = 12.sp, color = secondaryTextColor)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("시행기간", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(effectiveTerm, fontSize = 12.sp, color = secondaryTextColor)
                }
                HorizontalDivider(color = gridLineColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                listOf(
                    "준비" to prepTime.toHHmm(),
                    "운전/승무" to driveTime.toHHmm(),
                    "감시" to monitorTime.toHHmm(),
                    "휴게" to restTime.toHHmm(),
                    "승계" to transferTime.toHHmm()
                ).forEach { (k, v) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, fontSize = 12.sp, color = secondaryTextColor)
                        Text(v, fontSize = 12.sp, color = secondaryTextColor)
                    }
                }
                HorizontalDivider(color = gridLineColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("총사업시간", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(totalTime.toHHmm(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("총사업거리", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(String.format(Locale.US, "%.1f km", totalDist), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
