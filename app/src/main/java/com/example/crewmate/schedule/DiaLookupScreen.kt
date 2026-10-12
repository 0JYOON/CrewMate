package com.example.crewmate.schedule

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.sp
import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DiaLookupViewModel(
    private val diaDetailDataSource: DiaDetailDataSource,
    defaultDiaPrefix: String
) : ViewModel() {
    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _diaNo = MutableStateFlow(defaultDiaPrefix)
    val diaNo: StateFlow<String> = _diaNo.asStateFlow()

    private val _uiState = MutableStateFlow<DiaDetailLoadState?>(null)
    val uiState: StateFlow<DiaDetailLoadState?> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun setDate(newDate: LocalDate) {
        _date.value = newDate
    }

    fun setDiaNo(newDia: String) {
        _diaNo.value = newDia
    }

    fun search() {
        val currentDate = _date.value
        val currentDia = _diaNo.value.trim()
        if (currentDia.isEmpty()) return

        searchJob?.cancel()
        _uiState.value = DiaDetailLoadState.Loading
        searchJob = viewModelScope.launch {
            try {
                val dateText = currentDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                val detail = diaDetailDataSource.fetchDiaDetail(dateText, currentDia)
                _uiState.value = DiaDetailLoadState.Success(detail)
            } catch (_: Exception) {
                _uiState.value = DiaDetailLoadState.Error
            }
        }
    }
}

class DiaLookupViewModelFactory(
    private val diaDetailDataSource: DiaDetailDataSource,
    private val defaultDiaPrefix: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DiaLookupViewModel(diaDetailDataSource, defaultDiaPrefix) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaLookupScreen(
    diaDetailDataSource: DiaDetailDataSource,
    defaultDiaPrefix: String = "",
    viewModel: DiaLookupViewModel = viewModel(factory = DiaLookupViewModelFactory(diaDetailDataSource, defaultDiaPrefix))
) {
    val date by viewModel.date.collectAsState()
    val diaNo by viewModel.diaNo.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "승무 다이아 조회",
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
                    OutlinedTextField(
                        value = diaNo,
                        onValueChange = viewModel::setDiaNo,
                        modifier = Modifier.weight(1f),
                        label = { Text("다이아 번호") },
                        placeholder = { Text("예: 16301") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { viewModel.search() },
                            onDone = { viewModel.search() },
                        ),
                        singleLine = true,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::search,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Text("조회", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        when (val state = uiState) {
            is DiaDetailLoadState.Loading -> {
                Row(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is DiaDetailLoadState.Success -> {
                DiaDetailCard(
                    schedule = WorkSchedule(diaNo = diaNo),
                    detail = state.detail,
                    workDate = date
                )
            }
            is DiaDetailLoadState.Error -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "다이아 상세 정보를 불러오지 못했습니다.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            null -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "사업일자와 다이아 번호를 입력하여 조회하세요.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}