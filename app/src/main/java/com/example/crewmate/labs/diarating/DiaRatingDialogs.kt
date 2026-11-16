package com.example.crewmate.labs.diarating

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaRatingDetailDialog(
    diaNo: String,
    result: DiaRatingResult,
    onDismiss: () -> Unit,
    onOpenRules: () -> Unit
) {
    val context = LocalContext.current
    val cleanDia = diaNo.trim().removePrefix("~")
    val currentOverride = DiaRatingStorage.getRules(context).customOverrides[cleanDia]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("$cleanDia DIA 평가", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Surface(
                    color = Color(result.grade.colorHex).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${result.grade.shortBadge} · ${result.grade.label}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(result.grade.colorHex),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("판정 근거", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        result.reasons.forEach { reason ->
                            Text("• $reason", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider()
                Text("이 다이아 등급 수동 지정", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = currentOverride == null,
                        onClick = {
                            DiaRatingStorage.setCustomOverride(context, cleanDia, null)
                            onDismiss()
                        },
                        label = { Text("자동 판정", fontSize = 11.sp) }
                    )
                    DiaRatingGrade.entries.forEach { grade ->
                        FilterChip(
                            selected = currentOverride == grade,
                            onClick = {
                                DiaRatingStorage.setCustomOverride(context, cleanDia, grade)
                                onDismiss()
                            },
                            label = { Text("${grade.shortBadge} ${grade.label}", fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onOpenRules()
            }) {
                Text("⚙️ 평가 기준")
            }
        }
    )
}

@Composable
fun DiaRatingRulesDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val initial = remember { DiaRatingStorage.getRules(context) }

    var lightMaxDrive by remember { mutableIntStateOf(initial.lightMaxDriveMinutes) }
    var heavyMinDrive by remember { mutableIntStateOf(initial.heavyMinDriveMinutes) }
    var heavyMinTotal by remember { mutableIntStateOf(initial.heavyMinTotalMinutes) }
    var preferredStationsText by remember { mutableStateOf(initial.preferredSleepStations.joinToString(", ")) }
    var heavyStationsText by remember { mutableStateOf(initial.heavySleepStations.joinToString(", ")) }
    var showCellTint by remember { mutableStateOf(initial.showCellTint) }
    var overrides by remember { mutableStateOf(initial.customOverrides) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("⚙️ 다이아 평가 기준 설정", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MinuteStepperRow(
                    label = "경사업 최대 운전시간",
                    minutes = lightMaxDrive,
                    onChange = { lightMaxDrive = it.coerceIn(60, 600) }
                )
                MinuteStepperRow(
                    label = "중사업 최소 운전시간",
                    minutes = heavyMinDrive,
                    onChange = { heavyMinDrive = it.coerceIn(120, 720) }
                )
                MinuteStepperRow(
                    label = "중사업 최소 총사업시간",
                    minutes = heavyMinTotal,
                    onChange = { heavyMinTotal = it.coerceIn(240, 1440) }
                )

                OutlinedTextField(
                    value = preferredStationsText,
                    onValueChange = { preferredStationsText = it },
                    label = { Text("선호 야간 취침역 (쉼표 구분)", fontSize = 12.sp) },
                    placeholder = { Text("예: 대전, 부산") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = heavyStationsText,
                    onValueChange = { heavyStationsText = it },
                    label = { Text("비선호(중사업) 취침역 (쉼표 구분)", fontSize = 12.sp) },
                    placeholder = { Text("예: 동대구, 익산") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("달력 셀 배경 음영 표시", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = showCellTint, onCheckedChange = { showCellTint = it })
                }

                if (overrides.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("수동 지정 다이아 (${overrides.size}건)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { overrides = emptyMap() }) {
                            Text("전체 해제", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(
                    text = "편집기 평가 자료 (${initial.importedRatings.size}건 등록됨)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                var pasteJson by remember { mutableStateOf("") }
                var importStatus by remember { mutableStateOf<String?>(null) }
                OutlinedTextField(
                    value = pasteJson,
                    onValueChange = { pasteJson = it },
                    label = { Text("편집기 JSON 붙여넣기 (CREWMATE_DIA_RATING_PACK_V1)", fontSize = 11.sp) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            DiaRatingStorage.importPackJson(context, pasteJson)
                                .onSuccess { cnt ->
                                    importStatus = "✅ ${cnt}개 다이아 평가 자료 등록 완료"
                                    pasteJson = ""
                                }
                                .onFailure { err ->
                                    importStatus = "⚠️ ${err.message}"
                                }
                        },
                        enabled = pasteJson.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📥 평가 자료 등록", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://crewmate-alpha.pages.dev/dia-rating-editor.html")
                                )
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🛠️ 웹 편집기 열기", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (importStatus != null) {
                    Text(importStatus!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val latestImported = DiaRatingStorage.getRules(context).importedRatings
                val next = DiaRatingRules(
                    lightMaxDriveMinutes = lightMaxDrive,
                    heavyMinDriveMinutes = heavyMinDrive,
                    heavyMinTotalMinutes = heavyMinTotal,
                    preferredSleepStations = preferredStationsText.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    heavySleepStations = heavyStationsText.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    customOverrides = overrides,
                    showCellTint = showCellTint,
                    importedRatings = latestImported
                )
                DiaRatingStorage.saveRules(context, next)
                onDismiss()
            }) {
                Text("저장", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun MinuteStepperRow(
    label: String,
    minutes: Int,
    onChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DiaRatingEvaluator.formatMinutes(minutes), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { onChange(minutes - 30) },
                    shape = RoundedCornerShape(6.dp)
                ) { Text("-30m", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = { onChange(minutes + 30) },
                    shape = RoundedCornerShape(6.dp)
                ) { Text("+30m", fontSize = 11.sp) }
            }
        }
    }
}
