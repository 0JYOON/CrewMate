package com.example.crewmate.alarm

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.crewmate.data.remote.xcrew.AttendanceStatusDto
import com.example.crewmate.data.remote.xcrew.MockReviewDataProvider
import com.example.crewmate.data.remote.xcrew.XcrewLoginRepository
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser
import com.example.crewmate.login.CredentialReadResult
import com.example.crewmate.login.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 화면 없는 백그라운드 출무 실시간 검증기 (Headless Attendance Verifier)
 * 
 * 앱이 완전히 종료되었거나 포그라운드 UI(AppNavigation) 세션이 없을 때,
 * 알람 발동 2분 전 또는 직전에 화면 없는 WebView를 임시 생성하여 안전하게 Xcrew에 로그인하고
 * 최신 출무 현황을 직접 조회하여 [AttendanceCheckInLatch] 및 [AttendanceSnapshotStore]에 반영합니다.
 */
object HeadlessAttendanceVerifier {
    private const val TAG = "HeadlessAttVerifier"
    private const val XCREW_ENTRY_URL = "https://xcrew.korail.com/extrCrewMg/extrRltmCrewWrkPstt.do"

    suspend fun verifyAndSync(context: Context, pjtDt: String): List<AttendanceStatusDto>? {
        val cleanDate = pjtDt.trim().replace("-", "").filter { it.isDigit() }
        if (cleanDate.length != 8) return null

        val credentialStore = SecureCredentialStore(context.applicationContext)
        val credResult = credentialStore.read()
        if (credResult !is CredentialReadResult.Available) {
            Log.d(TAG, "No stored credentials available for headless verification")
            return null
        }

        val empNo = credResult.credentials.employeeNumber
        val pwd = credResult.credentials.password

        // 리뷰(심사) 데모 계정인 경우
        if (MockReviewDataProvider.isReviewAccount(empNo, pwd)) {
            val list = MockReviewDataProvider.fetchAttendanceStatus(
                MockReviewDataProvider.REVIEW_USER_NAME,
                cleanDate
            )
            AttendanceSnapshotStore.save(context, cleanDate, list)
            return list
        }

        var headlessWebView: WebView? = null
        val session = XcrewWebViewSession()

        return try {
            withTimeoutOrNull(35_000L) {
                // 1. 메인 스레드에서 화면 없는 WebView 생성
                withContext(Dispatchers.Main.immediate) {
                    val view = WebView(context.applicationContext).apply {
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
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                session.markPageReady(url)
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    session.markPageReady(request.url.toString())
                                }
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                if (request.isForMainFrame) {
                                    session.markPageReady(request.url.toString())
                                }
                            }
                        }
                    }
                    headlessWebView = view
                    session.attach(view)
                }

                // 2. OkHttp 기반 무음 로그인 수행 (공용 CookieManager에 세션 쿠키 저장됨)
                val loginRepo = XcrewLoginRepository()
                val userInfo = loginRepo.login(empNo, pwd, diagnosticsEnabled = false)
                if (userInfo.first.isNotEmpty() || userInfo.second.isNotEmpty()) {
                    session.setUserInfo(userInfo.first, userInfo.second)
                }

                // 3. 웹뷰 페이지 로드 및 세션 동기화
                withContext(Dispatchers.Main.immediate) {
                    session.resetPageReady("https://xcrew.korail.com/extrCrewMg/")
                    headlessWebView?.loadUrl(XCREW_ENTRY_URL)
                }

                // 페이지 준비 대기
                session.awaitPageReady()
                session.markAuthenticated()

                // 4. 최신 출무 현황 XHR 조회
                val dataSource = XcrewWebViewXhrDataSource(session, XcrewWorkScheduleParser())
                val fetched = dataSource.fetchAttendanceStatus(userInfo.first, cleanDate)

                if (fetched.isNotEmpty()) {
                    // 5. 스냅샷 및 래치 자동 저장
                    AttendanceSnapshotStore.save(context, cleanDate, fetched)
                    Log.i(TAG, "Headless verification succeeded for date $cleanDate (${fetched.size} items)")
                }
                fetched
            }
        } catch (e: Exception) {
            Log.w(TAG, "Headless verification encountered error", e)
            null
        } finally {
            withContext(Dispatchers.Main.immediate) {
                runCatching {
                    session.destroy()
                    headlessWebView?.stopLoading()
                    headlessWebView?.removeAllViews()
                    headlessWebView?.destroy()
                }
            }
        }
    }
}
