package com.example.crewmate.login

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XcrewLoginScreen(
    session: XcrewWebViewSession,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val webViewState = remember(session.webView) { mutableStateOf<WebView?>(session.webView) }
    val isLoading = remember { mutableStateOf(true) }
    val statusMessage = remember { mutableStateOf<String?>(null) }

    val handleBack = {
        val webView = webViewState.value
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onBack()
        }
    }
    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("코레일 로그인") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Text("뒤로가기", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isLoading.value) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            statusMessage.value?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            KorailWebView(
                existingWebView = session.webView,
                modifier = Modifier.weight(1f),
                onLoadingChanged = {
                    isLoading.value = it
                    if (it) statusMessage.value = null
                },
                onLoginSuccess = onLoginSuccess,
                onNavigationBlocked = {
                    statusMessage.value = "허용되지 않은 외부 페이지 이동을 차단했습니다."
                },
                onLoadError = { statusMessage.value = "Xcrew 페이지를 불러오지 못했습니다." },
                onWebViewCreated = {
                    webViewState.value = it
                    session.attach(it)
                },
            )
        }
    }
}
