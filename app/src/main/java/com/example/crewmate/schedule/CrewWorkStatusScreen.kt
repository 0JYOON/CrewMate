package com.example.crewmate.schedule

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crewmate.data.remote.xcrew.CrewAssignment
import com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CrewWorkStatusViewModel(private val repository: CrewWorkStatusRepository) : ViewModel() {
    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _uiState = MutableStateFlow<List<CrewAssignment>?>(null)
    val uiState: StateFlow<List<CrewAssignment>?> = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null

    fun setDate(newDate: LocalDate) {
        _date.value = newDate
    }

    fun search() {
        val currentDate = _date.value
        searchJob?.cancel()
        _isLoading.value = true
        _uiState.value = null
        searchJob = viewModelScope.launch {
            try {
                val dateText = currentDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                val assignments = repository.getCrewWorkStatus(dateText)
                _uiState.value = assignments
            } catch (_: Exception) {
                _uiState.value = emptyList() // or error handling
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class CrewWorkStatusViewModelFactory(private val repository: CrewWorkStatusRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CrewWorkStatusViewModel(repository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewWorkStatusScreen(
    crewWorkStatusRepository: CrewWorkStatusRepository,
    diaDetailDataSource: com.example.crewmate.data.remote.xcrew.DiaDetailDataSource,
    viewModel: CrewWorkStatusViewModel = viewModel(factory = CrewWorkStatusViewModelFactory(crewWorkStatusRepository))
) {
    val date by viewModel.date.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val context = LocalContext.current
    var currentPage by remember { mutableIntStateOf(0) }
    val itemsPerPage = 20

    var selectedAssignment by remember { mutableStateOf<CrewAssignment?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailCache = remember { mutableStateMapOf<String, DiaDetailLoadState>() }

    if (selectedAssignment != null) {
        val item = selectedAssignment!!
        ModalBottomSheet(
            onDismissRequest = { selectedAssignment = null },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            ScheduleDetailSheet(
                date = date,
                schedules = listOf(
                    WorkSchedule(
                        workDate = date.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                        diaNo = item.pdiaNo,
                        trainNo = item.repTrn1No,
                        trainNo2 = item.repTrn2No,
                        startTime = item.gwkTm,
                        endTime = item.loiwTm
                    )
                ),
                deviceEvents = emptyList(),
                diaDetailDataSource = diaDetailDataSource,
                detailCache = detailCache,
                showTabs = false
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "소속 승무원 근무 현황",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("사업일자") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day -> viewModel.setDate(LocalDate.of(year, month + 1, day)) },
                                        date.year,
                                        date.monthValue - 1,
                                        date.dayOfMonth,
                                    ).show()
                                },
                        )
                    }
                    Button(
                        onClick = { 
                            currentPage = 0
                            viewModel.search() 
                        },
                        modifier = Modifier.weight(1f).height(56.dp).padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        enabled = !isLoading
                    ) {
                        Text("조회", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Row(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState != null) {
            val assignments = uiState!!
            if (assignments.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "해당 일자의 근무 현황이 없습니다.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val totalPages = (assignments.size + itemsPerPage - 1) / itemsPerPage
                val pagedData = assignments.chunked(itemsPerPage).getOrElse(currentPage) { emptyList() }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        // Table Header
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("다이아", modifier = Modifier.weight(0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("열번", modifier = Modifier.weight(1.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("출/퇴근", modifier = Modifier.weight(1.45f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("승무원", modifier = Modifier.weight(1.35f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            pagedData.forEachIndexed { index, item ->
                                val trainNo = listOfNotNull(item.repTrn1No, item.repTrn2No).joinToString("/")
                                val st = item.gwkTm?.let { if (it.length == 4) "${it.substring(0,2)}:${it.substring(2,4)}" else it } ?: ""
                                val et = item.loiwTm?.let { if (it.length == 4) "${it.substring(0,2)}:${it.substring(2,4)}" else it } ?: ""
                                val timeStr = if (st.isNotEmpty() || et.isNotEmpty()) "$st~$et" else ""
                                val crewStr = item.crewMembers.joinToString("·")

                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { selectedAssignment = item }.padding(vertical = 12.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.pdiaNo, modifier = Modifier.weight(0.8f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(trainNo, modifier = Modifier.weight(1.4f), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(timeStr, modifier = Modifier.weight(1.45f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(4.dp))

                                    if (item.crewMembers.size <= 2) {
                                        Text(
                                            text = item.crewMembers.joinToString("·"),
                                            modifier = Modifier.weight(1.35f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Column(modifier = Modifier.weight(1.35f)) {
                                            item.crewMembers.chunked(2).forEach { rowCrew ->
                                                Text(
                                                    text = rowCrew.joinToString("·"),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                                if (index < pagedData.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }

                        // Pagination Footer
                        if (totalPages > 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { if (currentPage > 0) currentPage-- }, enabled = currentPage > 0) {
                                    Text("이전", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Text("${currentPage + 1} / $totalPages", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                TextButton(onClick = { if (currentPage < totalPages - 1) currentPage++ }, enabled = currentPage < totalPages - 1) {
                                    Text("다음", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}