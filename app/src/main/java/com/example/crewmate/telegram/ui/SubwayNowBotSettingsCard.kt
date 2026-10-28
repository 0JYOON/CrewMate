package com.example.crewmate.telegram.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.crewmate.database.BotCommandHistoryEntity
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.telegram.SubwayNowApiClient
import com.example.crewmate.telegram.SubwayNowManager
import com.example.crewmate.telegram.SubwayNowSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubwayNowBotSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(SubwayNowSettings.getConfig(context)) }
    var chatIdInput by remember { mutableStateOf(config.chatId) }
    var showPinDialog by remember { mutableStateOf(false) }
    var currentPin by remember { mutableStateOf("") }
    var pinRemainingSeconds by remember { mutableIntStateOf(300) }
    var isRequestingPin by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    fun refreshConfig() {
        config = SubwayNowSettings.getConfig(context)
        chatIdInput = config.chatId
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // 헤더 & 활성화 스위치
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🤖 SubwayNowBot 텔레그램 연동",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "당일 승무 열차의 실시간 위치를 출발 전부터 목표역 진입 시까지 텔레그램 메신저로 자동 안내받습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { checked ->
                        SubwayNowSettings.setEnabled(context, checked)
                        refreshConfig()
                        val msg = if (checked) "텔레그램 봇 연동이 활성화되었습니다." else "텔레그램 봇 연동이 비활성화되었습니다."
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (config.isEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Chat ID 및 상호 PIN 인증 섹션
                Text(
                    text = "텔레그램 계정 연동 (Chat ID)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chatIdInput,
                        onValueChange = { newVal ->
                            chatIdInput = newVal.filter { it.isDigit() }
                            SubwayNowSettings.setChatId(context, chatIdInput)
                            refreshConfig()
                        },
                        label = { Text("Chat ID (숫자)") },
                        placeholder = { Text("예: 62849182") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    // 인증 상태 뱃지 및 인증 버튼
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = if (config.isVerified) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (config.isVerified) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                        ) {
                            Text(
                                text = if (config.isVerified) "인증 완료" else "인증 필요",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (config.isVerified) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (chatIdInput.isBlank()) {
                                    Toast.makeText(context, "Chat ID를 먼저 입력해주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    isRequestingPin = true
                                    val genPin = (100000..999999).random().toString()
                                    currentPin = genPin
                                    pinRemainingSeconds = 300

                                    val res = SubwayNowApiClient.requestPin(
                                        serverUrl = config.serverUrl,
                                        chatId = chatIdInput,
                                        pin = genPin
                                    )
                                    isRequestingPin = false
                                    if (res.isSuccess) {
                                        showPinDialog = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "인증 요청 실패: ${res.exceptionOrNull()?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            enabled = !isRequestingPin && chatIdInput.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (config.isVerified) "재인증" else "인증 시작",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "* Chat ID는 텔레그램에서 @SubwayNowBot 또는 @userinfobot 에게 /start를 보내어 확인하실 수 있습니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // 1. 예약 전송 시각 (CrewMate -> 봇 전송)
                Text(
                    text = "예약 전송 시각 (CrewMate 발송)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sendLeadOptions = listOf(
                        -1 to "출근 시 일괄",
                        60 to "60분 전",
                        45 to "45분 전",
                        30 to "30분 전"
                    )
                    sendLeadOptions.forEach { (minutes, label) ->
                        val selected = config.sendLeadMinutes == minutes
                        FilterChip(
                            selected = selected,
                            onClick = {
                                SubwayNowSettings.setSendLeadMinutes(context, minutes)
                                refreshConfig()
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 2. 추적 시작 시각 (봇 위치 추적 개시)
                Text(
                    text = "추적 시작 시각 (SubwayNowBot 개시)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val trackLeadOptions = listOf(
                        30 to "30분 전",
                        20 to "20분 전",
                        15 to "15분 전",
                        10 to "10분 전"
                    )
                    trackLeadOptions.forEach { (minutes, label) ->
                        val selected = config.trackLeadMinutes == minutes
                        FilterChip(
                            selected = selected,
                            onClick = {
                                SubwayNowSettings.setTrackLeadMinutes(context, minutes)
                                refreshConfig()
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 3. 추적 알림 주기 (N개 역 이동 시)
                Text(
                    text = "알림 발송 주기 (역 간격)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val intervalOptions = listOf(
                        1 to "1개 역마다",
                        2 to "2개 역마다",
                        3 to "3개 역마다"
                    )
                    intervalOptions.forEach { (interval, label) ->
                        val selected = config.trackInterval == interval
                        FilterChip(
                            selected = selected,
                            onClick = {
                                SubwayNowSettings.setTrackInterval(context, interval)
                                refreshConfig()
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 하단 보조 액션 버튼들
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val pingRes = SubwayNowApiClient.ping(config.serverUrl)
                                if (pingRes.isSuccess && pingRes.getOrThrow()) {
                                    Toast.makeText(context, "✅ 서버 연결 정상 (200 OK)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "❌ 서버 응답 없음: ${pingRes.exceptionOrNull()?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📡 서버 연결 확인", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("📋 봇 전송 이력", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- 6자리 PIN 상호 인증 다이얼로그 ---
    if (showPinDialog) {
        // 3초 주기 자동 검증 폴링 및 타이머
        LaunchedEffect(showPinDialog) {
            while (pinRemainingSeconds > 0 && showPinDialog) {
                delay(3000)
                pinRemainingSeconds = (pinRemainingSeconds - 3).coerceAtLeast(0)
                val statusRes = SubwayNowApiClient.checkAuthStatus(config.serverUrl, chatIdInput)
                if (statusRes.isSuccess && statusRes.getOrThrow()) {
                    SubwayNowSettings.setVerified(context, true)
                    refreshConfig()
                    Toast.makeText(context, "✅ 텔레그램 연동 인증이 완료되었습니다!", Toast.LENGTH_LONG).show()
                    showPinDialog = false
                    break
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Text(
                    text = "🔐 텔레그램 연동 인증",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "승무원님의 텔레그램 앱에서 @SubwayNowBot 대화방으로 이동하여 아래 6자리 인증번호를 메시지로 입력해주세요.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = currentPin,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 6.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    val min = pinRemainingSeconds / 60
                    val sec = pinRemainingSeconds % 60
                    Text(
                        text = "남은 유효시간: %02d:%02d".format(min, sec),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pinRemainingSeconds < 60) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val statusRes = SubwayNowApiClient.checkAuthStatus(config.serverUrl, chatIdInput)
                            if (statusRes.isSuccess && statusRes.getOrThrow()) {
                                SubwayNowSettings.setVerified(context, true)
                                refreshConfig()
                                Toast.makeText(context, "✅ 텔레그램 연동 인증이 완료되었습니다!", Toast.LENGTH_LONG).show()
                                showPinDialog = false
                            } else {
                                Toast.makeText(
                                    context,
                                    "아직 텔레그램에서 PIN 번호가 입력되지 않았습니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("인증 확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // --- 봇 명령 전송 이력 다이얼로그 ---
    if (showHistoryDialog) {
        val db = remember { CrewMateDatabase.getInstance(context) }
        val histories by db.botCommandDao().getAllHistories().collectAsState(initial = emptyList())

        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋 봇 전송 이력 및 제어",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showHistoryDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "닫기")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (histories.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "전송된 봇 명령 이력이 없습니다.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(histories, key = { it.id }) { item ->
                                BotCommandHistoryRow(
                                    item = item,
                                    onCancelTrack = { trainNo ->
                                        scope.launch {
                                            val res = SubwayNowManager.cancelTarget(context, trainNo)
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "$trainNo 열차 추적이 취소되었습니다.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "취소 실패: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    db.botCommandDao().clearAllHistories()
                                    Toast.makeText(context, "이력이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("전체 삭제", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotCommandHistoryRow(
    item: BotCommandHistoryEntity,
    onCancelTrack: (String) -> Unit
) {
    val (statusBg, statusFg, statusText) = when (item.status) {
        "SUCCESS" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "전송완료")
        "FAILED" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "실패")
        "CANCELLED" -> Triple(Color(0xFFEEEEEE), Color(0xFF757575), "취소됨")
        else -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "대기중")
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = statusBg, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = statusText,
                            color = statusFg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = item.trainNo,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "(${item.targetStation}역)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.status == "SUCCESS") {
                    OutlinedButton(
                        onClick = { onCancelTrack(item.trainNo) },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color.Red)
                    ) {
                        Text("추적 중지", fontSize = 11.sp, color = Color.Red)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            val triggerLabel = if (item.triggerType == "MANUAL") "수동" else "자동"
            Text(
                text = "명령어: ${item.rawCommand}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "전송시각: ${item.sentAt} | 유형: $triggerLabel",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!item.responseMessage.isNullOrBlank()) {
                Text(
                    text = "응답: ${item.responseMessage}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
