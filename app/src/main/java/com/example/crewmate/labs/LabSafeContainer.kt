package com.example.crewmate.labs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 실험실 UI 영역을 감싸는 안전 격리 컨테이너.
 *
 * [errorMessage]가 전달되면 정상 UI 대신 안전한 폴백 카드와
 * [이 실험실 기능 끄기] 버튼을 표시해 앱 본체의 사용성을 보호합니다.
 */
@Composable
fun LabSafeContainer(
    feature: LabFeature,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    onDisable: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    if (errorMessage == null) {
        content()
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🧪", fontSize = 16.sp)
                Text(
                    text = "${feature.title} 오류 격리",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = "실험실 기능 실행 중 문제가 발생했습니다. 기존 정식 기능에는 영향을 주지 않습니다.\n사유: $errorMessage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(2.dp))
            OutlinedButton(
                onClick = {
                    LabsManager.setEnabled(context, feature, false)
                    onDisable?.invoke()
                }
            ) {
                Text("이 실험실 기능 끄기", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
