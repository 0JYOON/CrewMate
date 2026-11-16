package com.example.crewmate.util

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.schedule.DiaDetailCard
import com.example.crewmate.schedule.WorkSchedule
import com.example.crewmate.ui.theme.CrewMateTheme
import com.example.crewmate.ui.theme.getAppThemeMode
import com.example.crewmate.ui.theme.isDarkThemeActive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DiaCaptureOption(
    val title: String,
    val filePrefix: String,
    val showAll: Boolean
) {
    TIMELINE_SUMMARY("타임라인 요약", "CrewMate_타임라인요약", showAll = false),
    TIMELINE_FULL("타임라인 전체", "CrewMate_타임라인전체", showAll = true),
    ROUTE_DIAGRAM("🧪 승무 행로도 이미지 저장", "CrewMate_행로도", showAll = false);

    companion object {
        fun fromName(name: String?): DiaCaptureOption = entries.find { it.name == name } ?: TIMELINE_SUMMARY
    }
}

data class DiaCaptureItem(
    val schedule: WorkSchedule,
    val detail: DiaDetailResponse
)

object DiaCaptureHelper {

    suspend fun executeCapture(
        context: Context,
        headerTitle: String,
        workDate: LocalDate,
        dias: List<DiaCaptureItem>,
        option: DiaCaptureOption,
        badgeText: String? = null
    ): Boolean {
        if (dias.isEmpty()) {
            Toast.makeText(context, "캡처할 다이아 상세 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val appThemeMode = context.getAppThemeMode()
            val isDark = context.isDarkThemeActive(appThemeMode)
            val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
            val showPrepDivider = prefs.getBoolean("show_prep_divider", true)

            val bitmap = DiaCaptureRenderer.renderToBitmap(
                context = context,
                headerTitle = headerTitle,
                workDate = workDate,
                dias = dias,
                option = option,
                isDark = isDark,
                showPrepDivider = showPrepDivider,
                badgeText = badgeText
            )

            if (bitmap != null) {
                val saved = ScreenshotHelper.saveBitmapToGallery(
                    context = context,
                    bitmap = bitmap,
                    prefix = option.filePrefix
                )
                if (saved) {
                    Toast.makeText(context, "${option.title} 스크린샷이 갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    true
                } else {
                    Toast.makeText(context, "스크린샷 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                Toast.makeText(context, "스크린샷 이미지 생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("DiaCaptureHelper", "Failed to capture DIA schedule", e)
            Toast.makeText(context, "스크린샷 캡처 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

@Composable
fun DiaCaptureLayout(
    headerTitle: String,
    workDate: LocalDate,
    dias: List<DiaCaptureItem>,
    option: DiaCaptureOption
) {
    val displayDate = workDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT))

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Clean Document Header (No interactive buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = displayDate,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Clean DIA cards (isCaptureMode = true)
            dias.forEachIndexed { index, item ->
                DiaDetailCard(
                    schedule = item.schedule,
                    detail = item.detail,
                    workDate = workDate,
                    forceShowAllDetails = option.showAll,
                    isCaptureMode = true
                )
                if (index < dias.lastIndex) Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun DiaCaptureOptionDialog(
    onDismissRequest: () -> Unit,
    onOptionSelected: (DiaCaptureOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "📷 스크린샷 저장 범위 선택",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp
            )
        },
        text = {
            val routeDiagramLabEnabled = com.example.crewmate.labs.rememberLabEnabled(com.example.crewmate.labs.LabFeature.ROUTE_DIAGRAM)
            val options = DiaCaptureOption.entries.filter { it != DiaCaptureOption.ROUTE_DIAGRAM || routeDiagramLabEnabled }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    OutlinedButton(
                        onClick = { onOptionSelected(option) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = option.title,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("취소", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun DiaCaptureProgressDialog() {
    Dialog(onDismissRequest = { }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("스크린샷 생성 중...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
