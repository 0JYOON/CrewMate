package com.example.crewmate.schedule

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

import com.example.crewmate.database.MemoEntity

private val TodayCellBorder = Color(0xFF0F2C59)

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    diaDetailDataSource: DiaDetailDataSource? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val memosByDate by viewModel.memos.collectAsState()

    ScheduleContent(
        uiState = uiState,
        memosByDate = memosByDate,
        diaDetailDataSource = diaDetailDataSource,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onRetry = viewModel::retrySelectedMonth,
        onAddMemo = viewModel::addMemo,
        onUpdateMemo = viewModel::updateMemo,
        onDeleteMemo = viewModel::deleteMemo,
    ) { newMonth -> viewModel.setMonth(newMonth) }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScheduleContent(
    uiState: ScheduleUiState,
    memosByDate: Map<String, List<MemoEntity>>,
    diaDetailDataSource: DiaDetailDataSource?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRetry: () -> Unit,
    onAddMemo: (String, String) -> Unit,
    onUpdateMemo: (MemoEntity, String) -> Unit,
    onDeleteMemo: (MemoEntity) -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
) {
    val context = LocalContext.current
    val month = YearMonth.parse(uiState.monthOrCurrent(), MONTH_FORMAT)
    val today = remember { LocalDate.now() }
    val schedules = (uiState as? ScheduleUiState.Success)?.schedules.orEmpty()
    val schedulesByDate = schedules.asSequence().mapNotNull { schedule ->
        val date = schedule.workDate?.toLocalDateOrNull() ?: return@mapNotNull null
        if (YearMonth.from(date) == month) date to schedule else null
    }.groupBy({ (date, _) -> date }, { (_, schedule) -> schedule })
    
    var selectedDate by remember(month) { mutableStateOf<LocalDate?>(null) }
    var showMonthPicker by remember { mutableStateOf(value = false) }
    val diaDetailCache = remember { mutableStateMapOf<String, DiaDetailLoadState>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val prefs = remember { context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE) }
    var isSyncEnabled by remember { mutableStateOf(prefs.getBoolean("calendar_sync", false)) }
    val hasCalPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    var deviceEvents by remember { mutableStateOf<Map<LocalDate, List<DeviceEvent>>>(emptyMap()) }
    var fontScale by remember { 
        mutableStateOf(
            when (prefs.getString("calendar_font_scale", "NORMAL")) {
                "SMALL" -> 0.85f
                "LARGE" -> 1.15f
                else -> 1.0f
            }
        )
    }

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "calendar_sync") {
                isSyncEnabled = sharedPreferences.getBoolean("calendar_sync", false)
            } else if (key == "calendar_font_scale") {
                fontScale = when (sharedPreferences.getString("calendar_font_scale", "NORMAL")) {
                    "SMALL" -> 0.85f
                    "LARGE" -> 1.15f
                    else -> 1.0f
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(month, isSyncEnabled, hasCalPermission) {
        deviceEvents = if (isSyncEnabled && hasCalPermission) {
            getDeviceCalendarEvents(context, month)
        } else {
            emptyMap()
        }
    }

    LaunchedEffect(month, schedules, diaDetailDataSource) {
        val source = diaDetailDataSource ?: return@LaunchedEffect
        val semaphore = Semaphore(4)
        schedules.forEach { schedule ->
            val date = schedule.workDate?.toLocalDateOrNull() ?: return@forEach
            if (YearMonth.from(date) != month) return@forEach
            val dia = schedule.diaNo?.trim().orEmpty()
            if (!dia.isDiaLookupCandidate()) return@forEach
            val dateKey = date.format(DATE_FORMAT)
            val key = "$dateKey|$dia"
            if (diaDetailCache[key] == null) {
                diaDetailCache[key] = DiaDetailLoadState.Loading
                launch {
                    semaphore.withPermit {
                        diaDetailCache[key] = try {
                            val primaryDetail = source.fetchDiaDetail(dateKey, dia)
                            val resolvedDetail = if (primaryDetail.segments.isEmpty() && schedule.isContinuation) {
                                val prevDateKey = date.minusDays(1).format(DATE_FORMAT)
                                runCatching { source.fetchDiaDetail(prevDateKey, dia) }.getOrDefault(primaryDetail)
                            } else {
                                primaryDetail
                            }
                            DiaDetailLoadState.Success(resolvedDetail)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            diaDetailCache.remove(key)
                            throw cancelled
                        } catch (_: Exception) {
                            DiaDetailLoadState.Error
                        }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        YearMonthPickerDialog(
            initialMonth = month,
            onDismiss = { showMonthPicker = false },
            onConfirm = { newMonth ->
                showMonthPicker = false
                if (newMonth != month) onMonthSelected(newMonth)
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onPreviousMonth) {
                    Text("〈 이전", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                
                Surface(
                    modifier = Modifier.clickable { showMonthPicker = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${month.year}년 ${month.monthValue.toString().padStart(2, '0')}월",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                TextButton(onClick = onNextMonth) {
                    Text("다음 〉", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (uiState) {
            is ScheduleUiState.Loading -> {
                Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("근무표를 불러오는 중입니다.", color = Color(0xFF475569))
                }
            }
            is ScheduleUiState.Success -> {
                if (schedules.isEmpty()) {
                    Text("근무 데이터가 없습니다.", color = Color(0xFF64748B), modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            is ScheduleUiState.Error -> {
                Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("다시 시도") }
                }
            }
        }

        CalendarGrid(
            month = month,
            today = today,
            schedulesByDate = schedulesByDate,
            deviceEventsByDate = deviceEvents,
            memosByDate = memosByDate,
            fontScale = fontScale,
            resolveSecondTrain = { date, schedule ->
                schedule.trainNo2?.trim()?.takeIf(String::isNotEmpty) ?: run {
                    val dateKey = date.format(DATE_FORMAT)
                    val dia = schedule.diaNo?.trim().orEmpty()
                    val detail = (diaDetailCache["$dateKey|$dia"] as? DiaDetailLoadState.Success)?.detail
                    detail?.resolveSecondTrainNo(schedule.trainNo)
                }
            },
            onDateClick = { date -> selectedDate = date },
        )
    }

    selectedDate?.let { date ->
        val daySchedules = schedulesByDate[date].orEmpty()
        val dayEvents = deviceEvents[date].orEmpty()
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dayMemos = memosByDate[dateStr].orEmpty()

        ModalBottomSheet(
            onDismissRequest = { selectedDate = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ScheduleDetailSheet(
                date = date,
                schedules = daySchedules,
                deviceEvents = dayEvents,
                diaDetailDataSource = diaDetailDataSource,
                detailCache = diaDetailCache,
                memosForDate = dayMemos,
                showTabs = true,
                onAddMemo = { content -> onAddMemo(dateStr, content) },
                onUpdateMemo = onUpdateMemo,
                onDeleteMemo = onDeleteMemo,
            )
        }
    }
}

@Composable
fun YearMonthPickerDialog(
    initialMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(initialMonth.year) }
    var selectedMonth by remember { mutableIntStateOf(initialMonth.monthValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("연월 선택", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    TextButton(onClick = { selectedYear-- }) { Text("◀", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary) }
                    Text("${selectedYear}년", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { selectedYear++ }) { Text("▶", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary) }
                }
                
                val months = (1..12).toList()
                months.chunked(4).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { m ->
                            val isSelected = m == selectedMonth
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { selectedMonth = m }.padding(2.dp)
                            ) {
                                Text(
                                    text = "${m}월",
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { onConfirm(YearMonth.now()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text("이번 달로 이동", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { 
            Button(
                onClick = { onConfirm(YearMonth.of(selectedYear, selectedMonth)) }, 
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { 
                Text("확인", fontWeight = FontWeight.Bold) 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant) 
            } 
        }
    )
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    schedulesByDate: Map<LocalDate, List<WorkSchedule>>,
    deviceEventsByDate: Map<LocalDate, List<DeviceEvent>>,
    memosByDate: Map<String, List<MemoEntity>>,
    fontScale: Float,
    resolveSecondTrain: (LocalDate, WorkSchedule) -> String?,
    onDateClick: (LocalDate) -> Unit
) {
    val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        weekdays.forEachIndexed { idx, day ->
            val dayColor = when (idx) {
                0 -> Color(0xFFEF4444)
                6 -> Color(0xFF38BDF8)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(day, modifier = Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dayColor)
        }
    }
    val leadingCells = month.atDay(1).dayOfWeek.value % 7
    val dates = List(leadingCells) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    dates.chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).heightIn(min = 100.dp)) {
            week.forEachIndexed { dayOfWeekIdx, date ->
                val daySchedules = date?.let { schedulesByDate[it] }.orEmpty()
                val dayEvents = date?.let { deviceEventsByDate[it] }.orEmpty()
                val dayMemos = date?.let { memosByDate[it.format(DateTimeFormatter.ISO_LOCAL_DATE)] }.orEmpty()
                ScheduleDateCell(
                    date = date,
                    dayOfWeekIndex = dayOfWeekIdx,
                    isToday = date == today,
                    schedules = daySchedules,
                    deviceEvents = dayEvents,
                    memos = dayMemos,
                    fontScale = fontScale,
                    resolveSecondTrain = { schedule -> date?.let { resolveSecondTrain(it, schedule) } },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { date?.let(onDateClick) }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ScheduleDateCell(
    date: LocalDate?,
    dayOfWeekIndex: Int,
    isToday: Boolean,
    schedules: List<WorkSchedule>,
    deviceEvents: List<DeviceEvent>,
    memos: List<MemoEntity>,
    fontScale: Float,
    resolveSecondTrain: (WorkSchedule) -> String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val hasWorkOrEvent = schedules.isNotEmpty() || deviceEvents.isNotEmpty() || memos.isNotEmpty()
    val backgroundColor = when {
        date == null -> Color.Transparent
        isToday -> MaterialTheme.colorScheme.primaryContainer
        hasWorkOrEvent -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderStroke = if (date != null) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant) else null

    Surface(
        modifier = modifier.then(if (date != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = backgroundColor,
        border = borderStroke,
        shape = androidx.compose.ui.graphics.RectangleShape
    ) {
        if (date == null) return@Surface
        val dayNumColor = when {
            isToday -> MaterialTheme.colorScheme.primary
            dayOfWeekIndex == 0 -> Color(0xFFEF4444)
            dayOfWeekIndex == 6 -> Color(0xFF38BDF8)
            else -> MaterialTheme.colorScheme.onSurface
        }

        // 1. 서버 전용 휴일근무 판단 공식: hldyYn == "Y" 이면서 alcSpecDvCd != "02" 인 경우
        val showHolidayBadge = schedules.any { schedule ->
            val isServerHoliday = schedule.hldyYn?.trim()?.uppercase() == "Y" || schedule.isHoliday
            val isNotOffCode = schedule.alcSpecDvCd?.trim() != "02"
            
            isServerHoliday && isNotOffCode
        }

        // 실제 근무가 지정된 경우 뱃지 리스트에서 중복되는 "S" 뱃지 제거
        val hasActualDuty = schedules.any {
            val d = it.diaNo?.trim().orEmpty()
            d.isNotEmpty() && d != "S" && d != "연차"
        }
        val displaySchedules = if (hasActualDuty) {
            schedules.filter { it.diaNo?.trim() != "S" && it.nonActiveType?.trim() != "S" }
        } else {
            schedules
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(date.dayOfMonth.toString().padStart(2, '0'), fontSize = 11.sp, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold, color = dayNumColor)
                    if (showHolidayBadge) { Spacer(Modifier.width(2.dp)); Text("휴", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD32F2F)) }
                }
            }
            Spacer(Modifier.height(2.dp))
            displaySchedules.forEachIndexed { index, schedule ->
                if (index > 0) Spacer(Modifier.height(3.dp))
                
                val st = schedule.startTime.toDisplayTime()
                val et = schedule.endTime.toDisplayTime()
                val isNight = schedule.isContinuation || (st != null && et != null && st > et)
                val badgeBg = if (isNight) Color(0xFF333333) else Color(0xFFFFD54F)
                val badgeFg = if (isNight) Color.White else Color.Black
                
                val dia = schedule.diaNo?.trim() ?: schedule.nonActiveType?.trim() ?: ""
                
                if (dia.isNotEmpty()) {
                    val hasTime = st != null || et != null
                    val isDutyLabel = hasTime && dia != "S" && dia != "연차"
                    val dText = if (schedule.isContinuation && isDutyLabel) "~$dia" else dia

                    if (isDutyLabel) {
                        Text(
                            text = dText,
                            fontSize = (9.5f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier
                                .wrapContentWidth()
                                .background(badgeBg, RoundedCornerShape(2.dp))
                                .padding(horizontal = 2.dp, vertical = 0.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = (9.5f * fontScale).sp
                        )
                    } else {
                        val textColor = when (dia) {
                            "연차" -> Color(0xFF0D9488)
                            "S" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = dText,
                            fontSize = (9.5f * fontScale).sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor,
                            modifier = Modifier.wrapContentWidth().padding(horizontal = 2.dp, vertical = 0.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = (9.5f * fontScale).sp
                        )
                    }
                }

                if (st != null) {
                    Text(st, fontSize = (8.5f * fontScale).sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, lineHeight = (9.5f * fontScale).sp, maxLines = 1)
                }
                if (et != null) {
                    Text(et, fontSize = (8.5f * fontScale).sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, lineHeight = (9.5f * fontScale).sp, maxLines = 1)
                }
                
                schedule.trainNo?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = (8.5f * fontScale).sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, lineHeight = (9.5f * fontScale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                resolveSecondTrain(schedule)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = (8.5f * fontScale).sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, lineHeight = (9.5f * fontScale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (memos.isNotEmpty()) {
                memos.forEach { memo ->
                    Text(
                        text = memo.content,
                        fontSize = (8.5f * fontScale).sp,
                        lineHeight = (8.5f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 1.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 2.dp, vertical = 0.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            if (deviceEvents.isNotEmpty()) {
                deviceEvents.forEach { evt ->
                    Text(
                        text = evt.title,
                        fontSize = 8.5.sp,
                        lineHeight = 8.5.sp, // ★ 핵심: 줄 높이를 폰트 크기에 강제로 꽉 맞춤 (기본 여백 제거 효과)
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 1.dp) // 라벨 사이의 간격만 아주 살짝 줌
                            .background(Color(evt.color), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 0.dp) // ★ 내부 위아래 여백 완전히 0
                    )
                }
            }
        }
    }
}



sealed interface DiaDetailLoadState { data object Loading : DiaDetailLoadState; data class Success(val detail: DiaDetailResponse) : DiaDetailLoadState; data object Error : DiaDetailLoadState }

@Composable
fun ScheduleDetailSheet(
    date: LocalDate,
    schedules: List<WorkSchedule>,
    deviceEvents: List<DeviceEvent>,
    diaDetailDataSource: DiaDetailDataSource?,
    detailCache: MutableMap<String, DiaDetailLoadState>,
    memosForDate: List<MemoEntity> = emptyList(),
    showTabs: Boolean = true,
    onAddMemo: (String) -> Unit = {},
    onUpdateMemo: (MemoEntity, String) -> Unit = { _, _ -> },
    onDeleteMemo: (MemoEntity) -> Unit = {},
) {
    val dateKey = date.format(DATE_FORMAT)
    var retryVersion by remember(dateKey) { mutableIntStateOf(0) }
    LaunchedEffect(dateKey, schedules, diaDetailDataSource, retryVersion) {
        val source = diaDetailDataSource ?: return@LaunchedEffect
        schedules.forEach { schedule ->
            val dia = schedule.diaNo?.trim().orEmpty()
            if (dia.isDiaLookupCandidate()) {
                val key = "$dateKey|$dia"
                if (detailCache[key] == null) {
                    detailCache[key] = DiaDetailLoadState.Loading
                    detailCache[key] = try {
                        val primaryDetail = source.fetchDiaDetail(dateKey, dia)
                        val resolvedDetail = if (primaryDetail.segments.isEmpty() && schedule.isContinuation) {
                            val prevDateKey = date.minusDays(1).format(DATE_FORMAT)
                            runCatching { source.fetchDiaDetail(prevDateKey, dia) }.getOrDefault(primaryDetail)
                        } else { primaryDetail }
                        DiaDetailLoadState.Success(resolvedDetail)
                    } catch (_: Exception) { DiaDetailLoadState.Error }
                }
            }
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 740.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        Text(date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        
        if (showTabs) {
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("다이아 정보", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("캘린더 정보", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("메모", fontWeight = FontWeight.Bold) })
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(12.dp))
        }

        when (if (showTabs) selectedTab else 0) {
            1 -> {
                if (deviceEvents.isNotEmpty()) {
                    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            deviceEvents.forEachIndexed { index, evt ->
                                if (index > 0) Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).background(Color(evt.color), RoundedCornerShape(5.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(evt.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Text("이 날짜에는 캘린더 일정이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            2 -> {
                var memoInputText by remember { mutableStateOf("") }
                var editingMemo by remember { mutableStateOf<MemoEntity?>(null) }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = memoInputText,
                                onValueChange = { memoInputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("메모를 입력하세요", fontSize = 14.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            Button(
                                onClick = {
                                    if (memoInputText.isNotBlank()) {
                                        val target = editingMemo
                                        if (target != null) {
                                            onUpdateMemo(target, memoInputText)
                                            editingMemo = null
                                        } else {
                                            onAddMemo(memoInputText)
                                        }
                                        memoInputText = ""
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Text(if (editingMemo != null) "수정" else "추가", fontWeight = FontWeight.Bold)
                            }
                            if (editingMemo != null) {
                                TextButton(onClick = {
                                    editingMemo = null
                                    memoInputText = ""
                                }) {
                                    Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (memosForDate.isNotEmpty()) {
                            memosForDate.forEachIndexed { index, memo ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = memo.content,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                editingMemo = memo
                                                memoInputText = memo.content
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("✎", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(
                                            onClick = { onDeleteMemo(memo) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "등록된 메모가 없습니다.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                schedules.forEachIndexed { index, schedule ->
                    if (index > 0) { Spacer(Modifier.height(12.dp)) }
                    val dia = schedule.diaNo?.trim().orEmpty()
                    when {
                        dia == "연차" -> Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) { Text("연차", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488)) }
                        dia.isDiaLookupCandidate() -> {
                            when (val loadState = detailCache["$dateKey|$dia"]) {
                                is DiaDetailLoadState.Success -> DiaDetailCard(schedule = schedule, detail = loadState.detail, workDate = date)
                                DiaDetailLoadState.Loading -> Row(modifier = Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("${dia} DIA 상세를 불러오는 중입니다.", color = MaterialTheme.colorScheme.onSurface) }
                                DiaDetailLoadState.Error -> Column { Text("${dia} DIA 상세를 불러오지 못했습니다.", color = MaterialTheme.colorScheme.error); TextButton(onClick = { detailCache.remove("$dateKey|$dia"); retryVersion++ }) { Text("다시 시도", color = MaterialTheme.colorScheme.primary) } }
                                null -> ScheduleDetails(schedule)
                            }
                        }
                        else -> ScheduleDetails(schedule)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun String.isDiaLookupCandidate(): Boolean = isNotEmpty() && this != "연차" && this != "S" && matches(Regex("[A-Za-z0-9]+"))

@Composable
private fun ScheduleDetails(schedule: WorkSchedule) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            val dia = schedule.diaNo?.trim().orEmpty()
            if (dia.isNotEmpty()) {
                if (dia == "S" || !dia.isDiaLookupCandidate()) { Text(dia, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)) }
                else { DetailField("다이아", dia); if (schedule.isContinuation) { Text("전일 계속", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            schedule.trainNo?.trim()?.takeIf(String::isNotEmpty)?.let { DetailField("대표 열번 1", it) }
            schedule.trainNo2?.trim()?.takeIf(String::isNotEmpty)?.let { DetailField("대표 열번 2", it) }
            schedule.startTime.toDisplayTime()?.let { DetailField("출근", it) }
            schedule.endTime.toDisplayTime()?.let { DetailField("퇴근", it) }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) { Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) } }

private fun String?.toDisplayTime(): String? {
    val value = this?.trim()?.replace(":", "") ?: return null
    if (value.length != 4 || !value.all(Char::isDigit)) return null
    val hour = value.substring(0, 2).toIntOrNull() ?: return null
    val minute = value.substring(2, 4).toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return "%02d:%02d".format(Locale.ROOT, hour, minute)
}

private fun String.toLocalDateOrNull(): LocalDate? = try { LocalDate.parse(this, DATE_FORMAT) } catch (_: DateTimeParseException) { null }

private fun ScheduleUiState.monthOrCurrent(): String = when (this) { is ScheduleUiState.Loading -> month; is ScheduleUiState.Success -> month; is ScheduleUiState.Error -> month }.let { value -> runCatching { YearMonth.parse(value, MONTH_FORMAT) }.getOrElse { YearMonth.now() }.format(MONTH_FORMAT) }

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT)