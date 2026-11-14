package com.example.crewmate.schedule

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.crewmate.data.remote.xcrew.MockReviewDataProvider
import com.example.crewmate.data.remote.xcrew.XcrewLoginRepository
import com.example.crewmate.data.remote.xcrew.XcrewSessionRegistry
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser
import com.example.crewmate.database.CrewAssignmentDao
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.login.CredentialReadResult
import com.example.crewmate.login.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object OfflineCrewPrefetcher {
    private const val TAG = "OfflineCrewPrefetcher"
    private const val PREFS_NAME = "crewmate_prefs"
    const val KEY_WINDOW_SYNCED_AT = "crew_window_synced_at"
    const val KEY_WINDOW_ANCHOR = "crew_window_anchor"
    const val KEY_WINDOW_LAST_REPORT = "crew_window_last_report"
    private const val XCREW_ENTRY_URL = "https://xcrew.korail.com/extrCrewMg/extrRltmCrewWrkPstt.do"

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)

    /**
     * 오프라인 전환 시 저장할 4일 범위: 오늘(최우선) -> 내일 -> 어제 -> 전전일(어제 새벽 걸친 야간열차 대응)
     */
    fun windowDates(today: LocalDate): List<LocalDate> = listOf(
        today,
        today.plusDays(1),
        today.minusDays(1),
        today.minusDays(2)
    )

    fun windowDateStrings(today: LocalDate): List<String> =
        windowDates(today).map { it.format(DATE_FMT) }

    data class Report(
        val todayDateStr: String,
        val results: Map<String, CrewFetchOutcome>,
        val reusedExistingWindow: Boolean = false
    ) {
        val todayOk: Boolean
            get() = results[todayDateStr]?.isUsable == true

        val allOk: Boolean
            get() = results.isNotEmpty() && results.values.all { it.isUsable }

        fun summaryForLog(): String {
            return results.entries.joinToString(" | ") { (dt, outcome) ->
                val tag = when (outcome) {
                    is CrewFetchOutcome.Saved -> "S:${outcome.count}"
                    is CrewFetchOutcome.KeptCache -> "K:${outcome.count}(${outcome.reason})"
                    is CrewFetchOutcome.Failed -> "F(${outcome.reason})"
                }
                "$dt=$tag"
            }
        }
    }

    /**
     * 당일 이미 오프라인 전환을 수행하여 4일치 데이터가 DB에 모두 남아 있는지 확인합니다.
     */
    suspend fun isWindowAlreadyCachedForToday(
        context: Context,
        dao: CrewAssignmentDao,
        today: LocalDate
    ): Boolean {
        val todayStr = today.format(DATE_FMT)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val anchor = prefs.getString(KEY_WINDOW_ANCHOR, null)
        if (anchor != todayStr) return false

        for (dt in windowDateStrings(today)) {
            if (dao.countForDate(dt) <= 0) return false
        }
        return true
    }

    /**
     * 오프라인 전환 시점에 4일치(오늘·내일·어제·전전일) 승무원 근무 현황을 저장합니다.
     * 당일 전환 이력과 4일치 캐시가 모두 존재하면(skipIfCachedToday=true) 통신 없이 즉시 재사용합니다.
     */
    suspend fun prefetchWindow(
        repo: CrewWorkStatusRepository,
        dao: CrewAssignmentDao,
        today: LocalDate,
        context: Context? = null,
        skipIfCachedToday: Boolean = true
    ): Report {
        val todayStr = today.format(DATE_FMT)
        val dates = windowDateStrings(today)

        if (skipIfCachedToday && context != null && isWindowAlreadyCachedForToday(context, dao, today)) {
            val cachedResults = linkedMapOf<String, CrewFetchOutcome>()
            for (dt in dates) {
                cachedResults[dt] = CrewFetchOutcome.KeptCache(dao.countForDate(dt), "cached_today")
            }
            val report = Report(todayStr, cachedResults, reusedExistingWindow = true)
            runCatching {
                Log.i(TAG, "Reusing today's cached 4-day crew window: ${report.summaryForLog()}")
            }
            return report
        }

        val results = linkedMapOf<String, CrewFetchOutcome>()
        for (dt in dates) {
            var outcome = repo.refreshAndStore(dt)
            if (!outcome.isUsable) {
                // 일시적 통신 지연을 대비해 날짜별 1회 재시도
                outcome = repo.refreshAndStore(dt)
            }
            results[dt] = outcome
        }

        // 전전일 이전(today - 3일 미만) 오래된 승무원 배정 캐시 정리
        val cleanupThreshold = today.minusDays(2).format(DATE_FMT)
        runCatching {
            dao.cleanupOldAssignments(cleanupThreshold)
        }

        val report = Report(todayStr, results, reusedExistingWindow = false)
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
                .putLong(KEY_WINDOW_SYNCED_AT, System.currentTimeMillis())
                .putString(KEY_WINDOW_LAST_REPORT, report.summaryForLog())
            if (report.allOk) {
                editor.putString(KEY_WINDOW_ANCHOR, todayStr)
            }
            editor.apply()
        }

        runCatching {
            Log.i(TAG, "Prefetched 4-day crew window: ${report.summaryForLog()}")
        }
        return report
    }

    /**
     * 자동 오프라인 전환 시점 전용:
     * 당일 이미 오프라인 전환으로 4일치가 저장되어 있으면 통신하지 않고 통과하며,
     * 저장되어 있지 않으면 활성 세션(또는 임시 헤드리스 세션)으로 4일치를 통신 저장한 뒤 반환합니다.
     */
    suspend fun prefetchWindowForAutoOffline(
        context: Context,
        today: LocalDate = LocalDate.now()
    ): Report {
        val appContext = context.applicationContext
        val database = CrewMateDatabase.getInstance(appContext)
        val dao = database.crewAssignmentDao()
        val todayStr = today.format(DATE_FMT)

        if (isWindowAlreadyCachedForToday(appContext, dao, today)) {
            val cachedResults = linkedMapOf<String, CrewFetchOutcome>()
            for (dt in windowDateStrings(today)) {
                cachedResults[dt] = CrewFetchOutcome.KeptCache(dao.countForDate(dt), "cached_today")
            }
            return Report(todayStr, cachedResults, reusedExistingWindow = true)
        }

        val activeSession = XcrewSessionRegistry.current()
        if (activeSession != null) {
            val dataSource = XcrewWebViewXhrDataSource(activeSession, XcrewWorkScheduleParser())
            val repo = CrewWorkStatusRepository(dataSource, dao) { false }
            return prefetchWindow(repo, dao, today, appContext, skipIfCachedToday = false)
        }

        // 활성 세션이 없는 경우(백그라운드 알람에 의한 기동) 헤드리스 세션으로 4일치 조회 시도
        val credentialStore = SecureCredentialStore(appContext)
        val credResult = credentialStore.read()
        if (credResult !is CredentialReadResult.Available) {
            val fallback = linkedMapOf<String, CrewFetchOutcome>()
            for (dt in windowDateStrings(today)) {
                val count = dao.countForDate(dt)
                fallback[dt] = if (count > 0) CrewFetchOutcome.KeptCache(count, "no_credentials") else CrewFetchOutcome.Failed("no_credentials")
            }
            return Report(todayStr, fallback, reusedExistingWindow = false)
        }

        val empNo = credResult.credentials.employeeNumber
        val pwd = credResult.credentials.password

        if (MockReviewDataProvider.isReviewAccount(empNo, pwd)) {
            val mockSession = XcrewWebViewSession().apply {
                setMockReviewMode(true)
                setUserInfo(MockReviewDataProvider.REVIEW_USER_NAME, MockReviewDataProvider.REVIEW_USER_AFFIL)
                markAuthenticated()
            }
            val dataSource = XcrewWebViewXhrDataSource(mockSession, XcrewWorkScheduleParser())
            val repo = CrewWorkStatusRepository(dataSource, dao) { false }
            return prefetchWindow(repo, dao, today, appContext, skipIfCachedToday = false)
        }

        var headlessWebView: WebView? = null
        val session = XcrewWebViewSession()

        return try {
            withTimeoutOrNull(25_000L) {
                withContext(Dispatchers.Main.immediate) {
                    val view = WebView(appContext).apply {
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
                                if (request.isForMainFrame) session.markPageReady(request.url.toString())
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                if (request.isForMainFrame) session.markPageReady(request.url.toString())
                            }
                        }
                    }
                    headlessWebView = view
                    session.attach(view)
                }

                val userInfo = XcrewLoginRepository().login(empNo, pwd, diagnosticsEnabled = false)
                if (userInfo.first.isNotEmpty() || userInfo.second.isNotEmpty()) {
                    session.setUserInfo(userInfo.first, userInfo.second)
                }

                withContext(Dispatchers.Main.immediate) {
                    session.resetPageReady("https://xcrew.korail.com/extrCrewMg/")
                    headlessWebView?.loadUrl(XCREW_ENTRY_URL)
                }

                session.awaitPageReady()
                session.markAuthenticated()

                val dataSource = XcrewWebViewXhrDataSource(session, XcrewWorkScheduleParser())
                val repo = CrewWorkStatusRepository(dataSource, dao) { false }
                prefetchWindow(repo, dao, today, appContext, skipIfCachedToday = false)
            } ?: run {
                val fallback = linkedMapOf<String, CrewFetchOutcome>()
                for (dt in windowDateStrings(today)) {
                    val count = dao.countForDate(dt)
                    fallback[dt] = if (count > 0) CrewFetchOutcome.KeptCache(count, "timeout") else CrewFetchOutcome.Failed("timeout")
                }
                Report(todayStr, fallback, reusedExistingWindow = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-offline headless prefetch failed", e)
            val fallback = linkedMapOf<String, CrewFetchOutcome>()
            for (dt in windowDateStrings(today)) {
                val count = dao.countForDate(dt)
                fallback[dt] = if (count > 0) CrewFetchOutcome.KeptCache(count, "error") else CrewFetchOutcome.Failed("error")
            }
            Report(todayStr, fallback, reusedExistingWindow = false)
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
