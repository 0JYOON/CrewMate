package com.example.crewmate.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crewmate.ui.theme.CrewMateLogo
import com.example.crewmate.ui.theme.CrewMateTheme

@Composable
fun LoginScreen(
    isLoading: Boolean,
    isAutoLogin: Boolean,
    isCheckingSavedCredentials: Boolean,
    employeeNumber: String,
    password: String = "",
    rememberLogin: Boolean,
    errorMessage: String?,
    canRetryAutoLogin: Boolean,
    onEmployeeNumberChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit = {},
    onRememberLoginChange: (Boolean) -> Unit,
    onLogin: (employeeNumber: String, password: String) -> Unit,
    onRetryAutoLogin: () -> Unit,
    onSwitchToManualLogin: () -> Unit,
    onDeleteSavedLogin: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        if (isCheckingSavedCredentials || (isAutoLogin && isLoading)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CrewMateLogo(size = 84.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "CrewMate",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isCheckingSavedCredentials) "저장된 로그인 정보를 확인하고 있습니다." else "자동 로그인 중입니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isCheckingSavedCredentials) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onSwitchToManualLogin) {
                        Text("다른 계정으로 로그인", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            var passwordVisible by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CrewMateLogo(size = 80.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "CrewMate",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "코레일 승무원 스마트 근무 파트너",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(28.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    ) {
                        OutlinedTextField(
                            value = employeeNumber,
                            onValueChange = { onEmployeeNumberChange(it.take(6)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("사번 (6자리)") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            enabled = !isLoading,
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("비밀번호") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            enabled = !isLoading,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Text(
                                        text = if (passwordVisible) "🙈" else "👁",
                                        fontSize = 18.sp
                                    )
                                }
                            },
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLoading) {
                                    onRememberLoginChange(!rememberLogin)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = rememberLogin,
                                onCheckedChange = null,
                                enabled = !isLoading,
                            )
                            Text(
                                text = "  로그인 정보 안전하게 저장",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        errorMessage?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (canRetryAutoLogin && !isLoading) {
                            TextButton(
                                onClick = onRetryAutoLogin,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("저장된 정보로 다시 시도", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Button(
                            onClick = { onLogin(employeeNumber, password) },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text("  로그인 중...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (rememberLogin && !isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onDeleteSavedLogin) {
                        Text(
                            text = "저장된 로그인 정보 삭제",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    CrewMateTheme {
        LoginScreen(
            isLoading = false,
            isAutoLogin = false,
            isCheckingSavedCredentials = false,
            employeeNumber = "",
            password = "",
            rememberLogin = true,
            errorMessage = null,
            canRetryAutoLogin = false,
            onEmployeeNumberChange = {},
            onPasswordChange = {},
            onRememberLoginChange = {},
            onLogin = { _, _ -> },
            onRetryAutoLogin = {},
            onSwitchToManualLogin = {},
            onDeleteSavedLogin = {},
        )
    }
}