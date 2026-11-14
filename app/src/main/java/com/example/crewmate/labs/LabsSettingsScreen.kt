package com.example.crewmate.labs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabsSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val labStates = rememberAllLabStates()
    val scrollState = rememberScrollState()
    var showDiaRatingRulesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "🧪 실험실 (CrewMate Labs)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "정식 출시 전 새로운 기능을 미리 체험해 볼 수 있습니다.",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "오류나 불편이 발생하면 언제든 스위치를 꺼서 기존 화면으로 되돌릴 수 있으며, 검증된 기능은 정식 기능으로 편입됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        LabFeature.entries.forEach { feature ->
            val enabled = labStates[feature] == true
            LabFeatureCard(
                feature = feature,
                enabled = enabled,
                onToggle = { next ->
                    LabsManager.setEnabled(context, feature, next)
                    val stateMsg = if (next) "활성화" else "비활성화"
                    Toast.makeText(context, "${feature.title} $stateMsg", Toast.LENGTH_SHORT).show()
                },
                extraAction = if (feature == LabFeature.DIA_RATING && enabled) {
                    {
                        TextButton(onClick = { showDiaRatingRulesDialog = true }) {
                            Text("⚙️ 평가 기준 설정", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else null
            )
        }

        if (showDiaRatingRulesDialog) {
            com.example.crewmate.labs.diarating.DiaRatingRulesDialog(
                onDismiss = { showDiaRatingRulesDialog = false }
            )
        }

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = {
                LabsManager.resetAll(context)
                Toast.makeText(context, "모든 실험실 설정을 기본값으로 초기화했습니다.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("모든 실험실 기능 초기화", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabFeatureCard(
    feature: LabFeature,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    extraAction: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = feature.title,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LabBadgeChip(
                        text = if (feature.isImplemented) feature.stage.label else "도입 준비 중",
                        isHighlight = feature.isImplemented
                    )
                    if (feature.requiresOnline) {
                        LabBadgeChip(text = "온라인 필요", isHighlight = false)
                    }
                    LabBadgeChip(
                        text = if (feature.entryType == LabEntryType.STANDALONE_SCREEN) "독립 메뉴" else "화면 확장",
                        isHighlight = false
                    )
                }
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                extraAction?.invoke()
            }
            Switch(
                checked = enabled,
                enabled = feature.isImplemented,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun LabBadgeChip(
    text: String,
    isHighlight: Boolean
) {
    val bgColor = if (isHighlight) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isHighlight) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
