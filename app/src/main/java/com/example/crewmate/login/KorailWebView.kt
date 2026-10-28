package com.example.crewmate.login

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.crewmate.BuildConfig

internal const val XCREW_LOGIN_URL = "https://xcrew.korail.com/loginView.do"
private const val XCREW_HOST = "xcrew.korail.com"
private const val SUCCESS_PATH = "/extrCrewMg/extrItlCrewWrkPstt.do"
private const val SCHEDULE_PAGE_MARKER = "당월승무근무표"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KorailWebView(
    existingWebView: WebView?,
    modifier: Modifier = Modifier,
    onLoadingChanged: (Boolean) -> Unit,
    onLoginSuccess: () -> Unit,
    onNavigationBlocked: () -> Unit,
    onLoadError: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val isNewWebView = existingWebView == null
            (existingWebView ?: WebView(viewContext)).apply {
                if (isNewWebView) {
                    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    settings.savePassword = false
                    settings.saveFormData = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        settings.safeBrowsingEnabled = true
                    }

                    // Allow Xcrew's first-party cookies. Third-party cookies stay disabled by default.
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                }

                webViewClient = XcrewWebViewClient(
                    onLoadingChanged = onLoadingChanged,
                    onLoginSuccess = onLoginSuccess,
                    onNavigationBlocked = onNavigationBlocked,
                    onLoadError = onLoadError,
                )
                onWebViewCreated(this)
                if (isNewWebView) loadUrl(XCREW_LOGIN_URL)
            }
        },
        update = { /* Keep the existing WebView; recomposition must not reload the login page. */ },
    )
}

private class XcrewWebViewClient(
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onLoginSuccess: () -> Unit,
    private val onNavigationBlocked: () -> Unit,
    private val onLoadError: () -> Unit,
) : WebViewClient() {
    private var successReported = false
    private var failedMainFrameUrl: String? = null

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (!request.isForMainFrame) return false
        if (!isAllowedXcrewUrl(uri)) {
            onNavigationBlocked()
            return true
        }
        return false
    }

    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        val uri = Uri.parse(url)
        if (!isAllowedXcrewUrl(uri)) {
            onNavigationBlocked()
            return true
        }
        return false
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        failedMainFrameUrl = null
        onLoadingChanged(true)
    }

    override fun onPageFinished(view: WebView, url: String) {
        onLoadingChanged(false)
        if (url != failedMainFrameUrl) {
            reportLoginSuccessIfNeeded(view, Uri.parse(url))
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            failedMainFrameUrl = request.url.toString()
            onLoadingChanged(false)
            onLoadError()
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            failedMainFrameUrl = request.url.toString()
            onLoadingChanged(false)
            onLoadError()
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
        handler.cancel()
        failedMainFrameUrl = view.url
        onLoadingChanged(false)
        onLoadError()
    }

    private fun isAllowedXcrewUrl(uri: Uri): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(XCREW_HOST, ignoreCase = true)

    private fun reportLoginSuccessIfNeeded(view: WebView, uri: Uri) {
        if (successReported || !isAllowedXcrewUrl(uri)) return
        if (uri.path == SUCCESS_PATH) {
            reportSuccess()
            return
        }

        // Xcrew can render the signed-in schedule page at a different path. Detect that
        // page by its visible schedule heading and month label; never inspect or log form data.
        val schedulePageCheck = """(function() {
            var text = document.body ? document.body.innerText : '';
            return text.indexOf('$SCHEDULE_PAGE_MARKER') >= 0 && /\d{4}년\s*\d{1,2}월/.test(text);
        })()"""
        view.evaluateJavascript(schedulePageCheck) { result ->
            val currentUri = view.url?.let(Uri::parse) ?: uri
            if (!successReported && result == "true" && isAllowedXcrewUrl(currentUri)) {
                reportSuccess()
            }
        }
    }

    private fun reportSuccess() {
        if (successReported) return
        successReported = true
        onLoginSuccess()
    }
}
