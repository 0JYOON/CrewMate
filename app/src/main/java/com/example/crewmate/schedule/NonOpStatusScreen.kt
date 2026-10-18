package com.example.crewmate.schedule

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crewmate.database.AnnualLeaveEntity
import com.example.crewmate.database.CarriedOverLeaveEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun NonOpStatusScreen(
    viewModel: NonOpStatusViewModel
) {
    val year by viewModel.year.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val nonOpData by viewModel.nonOpData.collectAsState()
    val annualLeave by viewModel.annualLeave.collectAsState()
    val carriedOverLeaves by viewModel.carriedOverLeaves.collectAsState()

    var showYearPicker by remember { mutableStateOf(false) }

    if (showYearPicker) {
        YearPickerDialog(
            initialYear = year,
            onDismiss = { showYearPicker = false },
            onConfirm = { 
                viewModel.setYear(it)
                showYearPicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 상단: 연도 컨트롤러 (가운데 정렬)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { showYearPicker = true }
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${year}년 비실동 현황",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 중단: 비실동 현황 조회 결과
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            } else if (nonOpData.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("해당 연도의 비실동 현황이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val sortedData = nonOpData.sortedByDescending { it.startDate }
                val groupedData = sortedData.groupBy { it.name }
                val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    groupedData.forEach { (name, items) ->
                        val totalDays = items.sumOf { it.days.toDouble() }.toFloat()
                        val isExpanded = expandedGroups[name] == true
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedGroups[name] = !isExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "[$name : ${totalDays}일]",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isExpanded) "▲" else "▼",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                items.forEach { item ->
                                    fun formatDt(dt: String): String = if (dt.length == 8) "${dt.substring(2,4)}.${dt.substring(4,6)}.${dt.substring(6,8)}" else dt
                                    val st = formatDt(item.startDate)
                                    val ed = formatDt(item.endDate)
                                    val dateStr = if (st == ed) st else "$st ~ $ed"
                                    val reasonStr = if (item.reason.isNotBlank()) " (${item.reason})" else ""
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) {
                                        Box(modifier = Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.5.dp)))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "$dateStr / ${item.days}일$reasonStr",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 하단: 연차 관리 영역
        LeaveManagementSection(
            annualLeave = annualLeave,
            carriedOverLeaves = carriedOverLeaves,
            onSaveAnnualLeave = viewModel::saveAnnualLeave,
            onAddCarriedOverLeave = viewModel::addCarriedOverLeave,
            onUpdateCarriedOverLeave = viewModel::updateCarriedOverLeave,
            onDeleteCarriedOverLeave = viewModel::deleteCarriedOverLeave
        )
    }
}

@Composable
fun LeaveManagementSection(
    annualLeave: AnnualLeaveEntity?,
    carriedOverLeaves: List<CarriedOverLeaveEntity>,
    onSaveAnnualLeave: (Int) -> Unit,
    onAddCarriedOverLeave: (Float, String) -> Unit,
    onUpdateCarriedOverLeave: (CarriedOverLeaveEntity, Float, String) -> Unit,
    onDeleteCarriedOverLeave: (CarriedOverLeaveEntity) -> Unit
) {
    val context = LocalContext.current
    var annualInput by remember(annualLeave) { mutableStateOf(annualLeave?.totalDays?.toString() ?: "") }
    var coDaysInput by remember { mutableStateOf("") }
    var coDateInput by remember { mutableStateOf("") }
    var editingLeave by remember { mutableStateOf<CarriedOverLeaveEntity?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("나의 연차 관리", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 1. 올해 총 연차 갯수
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("올해 총 연차:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(90.dp))
                TextField(
                    value = annualInput,
                    onValueChange = { annualInput = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("일", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { annualInput.toIntOrNull()?.let { onSaveAnnualLeave(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("저장", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(18.dp))

            // 2. 이월 연차 추가
            Text("이월 연차 추가:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                TextField(
                    value = coDaysInput,
                    onValueChange = { coDaysInput = it },
                    modifier = Modifier.width(70.dp),
                    singleLine = true,
                    placeholder = { Text("일수") },
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    TextField(
                        value = coDateInput,
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("종료일 선택") },
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Box(
                        modifier = Modifier.matchParentSize().clickable {
                            val now = LocalDate.now()
                            val datePicker = DatePickerDialog(context, { _, y, m, d ->
                                coDateInput = LocalDate.of(y, m + 1, d).format(DateTimeFormatter.ISO_LOCAL_DATE)
                            }, now.year, now.monthValue - 1, now.dayOfMonth)
                            datePicker.datePicker.minDate = System.currentTimeMillis() - 1000
                            datePicker.show()
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val days = coDaysInput.toFloatOrNull()
                        if (days != null && coDateInput.isNotBlank()) {
                            val target = editingLeave
                            if (target != null) {
                                onUpdateCarriedOverLeave(target, days, coDateInput)
                                editingLeave = null
                            } else {
                                onAddCarriedOverLeave(days, coDateInput)
                            }
                            coDaysInput = ""
                            coDateInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E6FB8)),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(if (editingLeave != null) "수정" else "추가", fontWeight = FontWeight.Bold)
                }
                if (editingLeave != null) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        editingLeave = null
                        coDaysInput = ""
                        coDateInput = ""
                    }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("취소", color = Color(0xFF64748B))
                    }
                }
            }

            // 3. 이월 연차 목록
            if (carriedOverLeaves.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                carriedOverLeaves.forEach { leave ->
                    val isExpired = leave.endDate < todayStr
                    val textColor = if (isExpired) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                    val textWeight = if (isExpired) FontWeight.Bold else FontWeight.Medium

                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp).padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isExpired) "${leave.days}일 (종료: ${leave.endDate} - 만료)" else "${leave.days}일 (종료: ${leave.endDate})",
                            fontSize = 14.sp,
                            fontWeight = textWeight,
                            color = textColor
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    editingLeave = leave
                                    coDaysInput = leave.days.toString()
                                    coDateInput = leave.endDate
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("✎", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E56A0))
                            }
                            IconButton(onClick = { onDeleteCarriedOverLeave(leave) }, modifier = Modifier.size(32.dp)) {
                                Text("✕", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YearPickerDialog(
    initialYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("연도 선택", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
        text = {
            val currentYear = LocalDate.now().year
            val years = (currentYear - 5..currentYear + 5).toList()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(years) { y ->
                    val isSelected = y == initialYear
                    Text(
                        text = "${y}년",
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(y) }
                            .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalDate.now().year) }) {
                Text("올해로 이동", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}
