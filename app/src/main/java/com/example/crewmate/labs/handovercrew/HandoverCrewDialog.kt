package com.example.crewmate.labs.handovercrew

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.labs.LabFeature
import com.example.crewmate.labs.LabSafeContainer
import java.time.LocalDate

private sealed interface HandoverLookupState {
    data object Loading : HandoverLookupState
    data class Success(val report: HandoverCrewReport) : HandoverLookupState
    data class Error(val message: String) : HandoverLookupState
}

@Composable
fun HandoverCrewDialog(
    workDate: LocalDate,
    currentDiaNo: String,
    allSegments: List<DiaDetailSegment>,
    targetSegment: DiaDetailSegment,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { HandoverCrewRepository(context) }
    var retryCount by remember { mutableIntStateOf(0) }
    var state by remember(targetSegment, retryCount) { mutableStateOf<HandoverLookupState>(HandoverLookupState.Loading) }

    LaunchedEffect(workDate, currentDiaNo, targetSegment, retryCount) {
        state = HandoverLookupState.Loading
        state = runCatching {
            repository.lookupHandover(
                workDate = workDate,
                currentDiaNo = currentDiaNo,
                allSegments = allSegments,
                targetSegment = targetSegment
            )
        }.fold(
            onSuccess = { HandoverLookupState.Success(it) },
            onFailure = { err ->
                HandoverLookupState.Error(
                    err.message?.takeIf { it.isNotBlank() }
                        ?: "교대승무원 정보를 불러오지 못했습니다."
                )
            }
        )
    }

    val trainLabel = targetSegment.trnNo?.trim().orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "👥 $trainLabel 교대승무원",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "🧪 실험실",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            LabSafeContainer(
                feature = LabFeature.HANDOVER_CREW,
                onDisable = onDismiss
            ) {
                when (val current = state) {
                    is HandoverLookupState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "직전·교대 승무원을 조회하는 중...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is HandoverLookupState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = current.message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "오프라인 상태이거나 Xcrew 세션이 만료된 경우 저장된 캐시 데이터가 없으면 조회가 제한될 수 있습니다.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is HandoverLookupState.Success -> {
                        val report = current.report
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "운행일 ${formatDisplayDate(report.targetDate)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = if (report.fromOfflineCache) {
                                        Color(0xFFFEF3C7)
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (report.fromOfflineCache) "오프라인 캐시" else "실시간 조회",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (report.fromOfflineCache) {
                                            Color(0xFF92400E)
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // 내 승무 구간
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "내 구간 · ${report.currentDiaNo} DIA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${report.currentDptStnNm} ${formatTime(report.currentDptTm)} → ${report.currentArvStnNm} ${formatTime(report.currentArvTm)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 직전 승무원 · 교대 승무원 한 줄(2열) 배치
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RelayPersonCard(
                                    badge = "직전 승무원",
                                    badgeColor = Color(0xFF0369A1),
                                    item = report.previousCrew,
                                    emptyMessage = "없음 (시발/미조회)",
                                    modifier = Modifier.weight(1f)
                                )
                                RelayPersonCard(
                                    badge = "교대 승무원",
                                    badgeColor = Color(0xFF047857),
                                    item = report.nextCrew,
                                    emptyMessage = "없음 (종착/미조회)",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // 전체 릴레이가 2개 이상이면 기본 접기 + 펼쳐보기 지원
                            if (report.allRelays.size > 1) {
                                var showAllRelays by remember(report) { mutableStateOf(false) }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAllRelays = !showAllRelays }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "전체 열차 릴레이 (${report.allRelays.size}개 구간)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (showAllRelays) "접기 ▲" else "펼쳐보기 ▼",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (showAllRelays) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        report.allRelays.forEach { relay ->
                                            val crewText = if (relay.crewMembers.isNotEmpty()) {
                                                relay.crewMembers.joinToString(", ")
                                            } else {
                                                "미배정"
                                            }
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = if (relay.isCurrentDia) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "${relay.pdiaNo} DIA · $crewText",
                                                            fontSize = 11.sp,
                                                            fontWeight = if (relay.isCurrentDia) FontWeight.ExtraBold else FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            text = "${relay.dptStnNm} ${formatTime(relay.dptTm)} → ${relay.arvStnNm} ${formatTime(relay.arvTm)}",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (relay.isCurrentDia) {
                                                        Text(
                                                            text = "내 근무",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state is HandoverLookupState.Error) {
                TextButton(onClick = { retryCount++ }) {
                    Text("다시 조회", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("닫기", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (state is HandoverLookupState.Error) {
                TextButton(onClick = onDismiss) {
                    Text("닫기")
                }
            }
        }
    )
}

@Composable
private fun RelayPersonCard(
    badge: String,
    badgeColor: Color,
    item: HandoverRelayItem?,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
                if (item != null) {
                    Text(
                        text = item.pdiaNo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            if (item == null) {
                Text(
                    text = emptyMessage,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val crewText = if (item.crewMembers.isNotEmpty()) {
                    item.crewMembers.joinToString(", ")
                } else {
                    "배정 정보 없음"
                }
                Text(
                    text = crewText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${item.dptStnNm} ${formatTime(item.dptTm)}→${item.arvStnNm} ${formatTime(item.arvTm)}",
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(raw: String): String {
    val clean = raw.trim().filter(Char::isDigit)
    return if (clean.length == 4) "${clean.substring(0, 2)}:${clean.substring(2, 4)}" else "-"
}

private fun formatDisplayDate(yyyyMmDd: String): String {
    return if (yyyyMmDd.length == 8) {
        "${yyyyMmDd.substring(0, 4)}-${yyyyMmDd.substring(4, 6)}-${yyyyMmDd.substring(6, 8)}"
    } else {
        yyyyMmDd
    }
}
