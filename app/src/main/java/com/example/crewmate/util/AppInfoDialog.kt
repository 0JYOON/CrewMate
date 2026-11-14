package com.example.crewmate.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 설정 화면 및 최초 로그인 시 공용으로 표시되는 '앱 정보 및 이용 유의사항' 모달 다이얼로그.
 */
@Composable
fun AppInfoAndTermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("ℹ️", fontSize = 20.sp)
                Text(
                    text = "앱 정보 및 이용 유의사항",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. 비공식 개발 안내 카드
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📖", fontSize = 14.sp)
                            Text(
                                text = "비공식 개발 안내",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "이 어플리케이션은 제작자 개인의 업무 편의를 목적으로 개발된 비공식 앱입니다. 기능상 다소 오류가 있거나 예고 없이 서비스가 중단될 수 있으니 너른 양해 부탁드립니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // 2. 면책 및 공식 시스템 확인 공지 (웹앱 스타일 앰버 경고 박스)
                val isDark = MaterialTheme.colorScheme.surface == Color(0xFF161E2E)
                val warningBg = if (isDark) Color(0xFF2C2214) else Color(0xFFFFFBEB)
                val warningBorder = if (isDark) Color(0xFF5A4016) else Color(0xFFFDE68A)
                val warningTitleColor = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                val warningTextColor = if (isDark) Color(0xFFF6E5B8) else Color(0xFF78350F)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = warningBg,
                    border = BorderStroke(1.dp, warningBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Text(
                                text = "[필독] 데이터 참고 및 면책 공지",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = warningTitleColor
                            )
                        }
                        Text(
                            text = "본 앱에서 제공하는 모든 조회 결과 및 데이터는 참고용일 뿐입니다. 앱의  정보 오류로 인해 발생한 근태 문제나 불이익 등에 대해서도 제작자는 책임지지 않습니다.\n\n정확한 근무 내역은 반드시 공식 시스템을 통해 확인하시기 바랍니다.",
                            fontSize = 12.sp,
                            color = warningTextColor,
                            lineHeight = 18.sp
                        )
                    }
                }

                // 3. 문의사항 및 제작자 카드
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("✉️", fontSize = 14.sp)
                            Text(
                                text = "문의 및 제작자",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "문의사항이 있다면 이메일(studio.yj.lab@gmail.com)로 전달 부탁드립니다. 다만, 일일이 답장을 드리거나 즉각적인 조치는 어려울 수 있는 점 미리 사과드립니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "만든이 :",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "안산승무사업소 윤영준",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
