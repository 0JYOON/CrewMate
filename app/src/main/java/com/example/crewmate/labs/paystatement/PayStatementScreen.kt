package com.example.crewmate.labs.paystatement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TabularStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun PayStatementScreen(
    viewModel: PayStatementViewModel
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        MonthSelectorBar(
            monthLabel = "${state.selectedMonth.year}년 ${state.selectedMonth.monthValue}월",
            isLoading = state.isLoading,
            lastUpdatedText = state.lastUpdatedText,
            onPrevMonth = { viewModel.previousMonth() },
            onNextMonth = { viewModel.nextMonth() },
            onRefresh = { viewModel.refresh() }
        )

        Spacer(Modifier.height(8.dp))

        when {
            state.isLoading && state.report == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Xcrew 개인근무명세서를 불러오는 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            state.errorMessage != null && state.report == null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "조회 오류",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.errorMessage ?: "알 수 없는 오류가 발생했습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("다시 조회")
                        }
                    }
                }
            }

            else -> {
                val report = state.report
                if (report == null || report.rows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "해당 월의 개인근무명세서 데이터가 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    if (state.errorMessage != null) {
                        Text(
                            text = "새로고침 오류: ${state.errorMessage}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item(key = "table_header") {
                            Surface(
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                PayStatementHeaderRow()
                            }
                        }

                        itemsIndexed(
                            items = report.rows,
                            key = { idx, row -> "${row.pjtDt}_${row.pdiaNo}_$idx" }
                        ) { index, row ->
                            val bgColor = if (index % 2 == 0) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor)
                            ) {
                                PayStatementDataRow(row = row)
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        item(key = "table_totals") {
                            Surface(
                                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                            ) {
                                PayStatementTotalsRow(
                                    rowCount = report.rows.size,
                                    totals = report.totals
                                )
                            }
                        }

                        item(key = "eligibility_card") {
                            PayStatementEligibilityCard(eligibility = report.eligibility)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelectorBar(
    monthLabel: String,
    isLoading: Boolean,
    lastUpdatedText: String?,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevMonth, modifier = Modifier.size(34.dp)) {
                    Text("◀", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = monthLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(34.dp)) {
                    Text("▶", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lastUpdatedText != null) {
                    Text(
                        text = lastUpdatedText,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("새로고침", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PayStatementHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("일자", Modifier.weight(1.35f), TextAlign.Start)
        HeaderCell("다이아", Modifier.weight(1.3f), TextAlign.Center)
        HeaderCell("시간", Modifier.weight(1.0f), TextAlign.End)
        HeaderCell("거리", Modifier.weight(1.0f), TextAlign.End)
        HeaderCell("휴일", Modifier.weight(0.9f), TextAlign.End)
        HeaderCell("연장", Modifier.weight(0.9f), TextAlign.End)
        HeaderCell("야간", Modifier.weight(0.9f), TextAlign.End)
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1
    )
}

@Composable
private fun PayStatementDataRow(row: PayStatementRow) {
    val parsedDate = runCatching {
        LocalDate.parse(row.pjtDt, DateTimeFormatter.BASIC_ISO_DATE)
    }.getOrNull()

    val dateText = if (parsedDate != null) {
        val dow = when (parsedDate.dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
        }
        String.format("%02d-%02d(%s)", parsedDate.monthValue, parsedDate.dayOfMonth, dow)
    } else {
        row.pjtDt
    }

    val dateColor = when (parsedDate?.dayOfWeek) {
        DayOfWeek.SATURDAY -> Color(0xFF1E64D8)
        DayOfWeek.SUNDAY -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val isZeroWork = row.totPjtTnum == 0 && !row.hasOperatingDistance

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateText,
            modifier = Modifier.weight(1.35f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isZeroWork) dateColor.copy(alpha = 0.65f) else dateColor,
            style = TabularStyle,
            maxLines = 1
        )

        Box(
            modifier = Modifier.weight(1.3f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = row.pdiaNo.ifEmpty { "-" },
                    fontSize = 11.5.sp,
                    fontWeight = if (row.hasOperatingDistance) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        row.isStandbyDia && !row.hasOperatingDistance -> Color(0xFFB45309)
                        row.isHolidayWork -> Color(0xFF1565C0)
                        isZeroWork -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.isStandbyDia && !row.hasOperatingDistance) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "대기",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }

        ValueCell(
            text = PayStatementFormatter.formatMinutes(row.totPjtTnum, blankIfZero = true),
            modifier = Modifier.weight(1.0f),
            isBold = row.totPjtTnum > 0
        )
        ValueCell(
            text = PayStatementFormatter.formatDistance(row.totPjtDst, blankIfZero = false),
            modifier = Modifier.weight(1.0f),
            isBold = row.hasOperatingDistance,
            color = if (row.hasOperatingDistance) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        ValueCell(
            text = PayStatementFormatter.formatMinutes(row.hldyTnum, blankIfZero = true),
            modifier = Modifier.weight(0.9f),
            color = if (row.hldyTnum > 0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        ValueCell(
            text = PayStatementFormatter.formatMinutes(row.extnTnum, blankIfZero = true),
            modifier = Modifier.weight(0.9f)
        )
        ValueCell(
            text = PayStatementFormatter.formatMinutes(row.nihtTnum, blankIfZero = true),
            modifier = Modifier.weight(0.9f)
        )
    }
}

@Composable
private fun ValueCell(
    text: String,
    modifier: Modifier,
    isBold: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
        color = color,
        textAlign = TextAlign.End,
        style = TabularStyle,
        maxLines = 1
    )
}

@Composable
private fun PayStatementTotalsRow(
    rowCount: Int,
    totals: PayStatementTotals
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "합계",
            modifier = Modifier.weight(1.35f),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = "${rowCount}일",
            modifier = Modifier.weight(1.3f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        TotalValueCell(PayStatementFormatter.formatMinutes(totals.totalMinutes, blankIfZero = false), Modifier.weight(1.0f))
        TotalValueCell(PayStatementFormatter.formatDistance(totals.totalDistance, blankIfZero = false), Modifier.weight(1.0f))
        TotalValueCell(PayStatementFormatter.formatMinutes(totals.holidayMinutes, blankIfZero = false), Modifier.weight(0.9f))
        TotalValueCell(PayStatementFormatter.formatMinutes(totals.overtimeMinutes, blankIfZero = false), Modifier.weight(0.9f))
        TotalValueCell(PayStatementFormatter.formatMinutes(totals.nightMinutes, blankIfZero = false), Modifier.weight(0.9f))
    }
}

@Composable
private fun TotalValueCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        textAlign = TextAlign.End,
        style = TabularStyle,
        maxLines = 1
    )
}

@Composable
private fun PayStatementEligibilityCard(eligibility: PayStatementEligibilitySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "예상 승무여비 · 승무수당 충족 여부",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "실제 승무실적(거리 > 0) 기준으로 산정합니다.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            EligibilityProgressBlock(
                title = "승무여비",
                ruleDescription = "휴일근무 포함 근무일수 20일 이상 시 전액 지급",
                currentDays = eligibility.operatingDaysIncludingHoliday,
                targetDays = eligibility.travelPayTargetDays,
                isEligible = eligibility.isTravelPayEligible,
                remainingDays = eligibility.travelPayRemainingDays,
                subNote = buildString {
                    if (eligibility.holidayOperatingDays > 0) {
                        append("휴일 승무 ${eligibility.holidayOperatingDays}일 포함")
                    }
                    if (eligibility.standbyWithOperatingDays > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("900번대 중 실승무 ${eligibility.standbyWithOperatingDays}일 포함")
                    }
                }.takeIf { it.isNotEmpty() }
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))

            EligibilityProgressBlock(
                title = "승무수당",
                ruleDescription = "휴일근무 제외 근무일수 17일 이상 + 승무실적(거리 > 0) 시 전액 지급",
                currentDays = eligibility.operatingDaysExcludingHoliday,
                targetDays = eligibility.allowanceTargetDays,
                isEligible = eligibility.isAllowanceEligible,
                remainingDays = eligibility.allowanceRemainingDays,
                subNote = if (eligibility.holidayOperatingDays > 0) {
                    "휴일 승무 ${eligibility.holidayOperatingDays}일은 승무수당 일수에서 제외됨"
                } else null
            )

            if (eligibility.hasStandbyZeroDistance) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEF3C7))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "⚠️ 900번대 대기근무(거리 0) ${eligibility.standbyZeroDistanceDays}일 별도 안내",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF92400E)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "• 미승무 대기 제외(실제 지급 기준): 여비 ${eligibility.operatingDaysIncludingHoliday}일/20일 · 수당 ${eligibility.operatingDaysExcludingHoliday}일/17일",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF78350F)
                    )
                    Text(
                        text = "• 대기근무 포함 단순 출근일수: 여비 ${eligibility.travelDaysIfStandbyIncluded}일 · 수당 ${eligibility.allowanceDaysIfStandbyIncluded}일",
                        fontSize = 11.sp,
                        color = Color(0xFF78350F)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "900번대 대기근무는 실제 승무(거리 > 0)를 하지 않으면 승무여비 및 승무수당 지급 일수에서 제외되므로 위 두 수치를 비교해 판단하세요.",
                        fontSize = 10.5.sp,
                        color = Color(0xFF92400E),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EligibilityProgressBlock(
    title: String,
    ruleDescription: String,
    currentDays: Int,
    targetDays: Int,
    isEligible: Boolean,
    remainingDays: Int,
    subNote: String?
) {
    val statusBg = if (isEligible) Color(0xFFDCFCE7) else Color(0xFFEFF6FF)
    val statusFg = if (isEligible) Color(0xFF166534) else Color(0xFF1D4ED8)
    val progressColor = if (isEligible) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusBg
            ) {
                Text(
                    text = if (isEligible) "✅ 전액 지급 충족 (${currentDays}/${targetDays}일)" else "⏳ ${remainingDays}일 부족 (${currentDays}/${targetDays}일)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusFg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = ruleDescription,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (currentDays.toFloat() / targetDays.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (!subNote.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subNote,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
