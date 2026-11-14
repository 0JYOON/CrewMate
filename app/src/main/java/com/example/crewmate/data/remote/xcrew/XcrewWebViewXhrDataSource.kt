package com.example.crewmate.data.remote.xcrew

import android.util.Log
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

private const val TAG = "XcrewWebViewXHR"
private const val XCREW_XHR_URL =
    "https://xcrew.korail.com/extrCrewMg/searchExtrIndCrewWrk.do"
private const val XCREW_XHR_PATH = "/extrCrewMg/searchExtrIndCrewWrk.do"
private const val XCREW_DIA_DETAIL_URL = "https://xcrew.korail.com/extrCrewMg/searchExtrCrewDia.do"
private const val XCREW_DIA_DETAIL_PATH = "/extrCrewMg/searchExtrCrewDia.do"
private const val XCREW_CREW_WORK_STATUS_URL = "https://xcrew.korail.com/extrCrewMg/searchExtrCrewWrkPstt.do"
private const val XCREW_CREW_WORK_STATUS_PATH = "/extrCrewMg/searchExtrCrewWrkPstt.do"
private const val XCREW_ATTENDANCE_STATUS_URL = "https://xcrew.korail.com/extrCrewMg/searchExtrTrnQryItmtGtwc.do"
private const val XCREW_ATTENDANCE_STATUS_PATH = "/extrCrewMg/searchExtrTrnQryItmtGtwc.do"
private const val XCREW_IND_CREW_WRK_URL = "https://xcrew.korail.com/extrCrewMg/searchExtrIndCrewWrk.do"
private const val XCREW_IND_CREW_WRK_PATH = "/extrCrewMg/searchExtrIndCrewWrk.do"
private const val XCREW_NON_OP_URL = "https://xcrew.korail.com/extrCrewMg/searchExtrNonActvPstt.do"
private const val XCREW_NON_OP_PATH = "/extrCrewMg/searchExtrNonActvPstt.do"
private const val XCREW_IND_WRK_SPEC_URL = "https://xcrew.korail.com/extrCrewMg/searchExtrIndWrkSpec.do"
private const val XCREW_IND_WRK_SPEC_PATH = "/extrCrewMg/searchExtrIndWrkSpec.do"
private const val XCREW_XHR_ACCEPT = "application/json, text/javascript, */*; q=0.01"
private const val XHR_TIMEOUT_MILLIS = 40_000L

/** Retains the authenticated WebView across the login-to-schedule navigation. */
class XcrewWebViewSession {
    @Volatile
    var webView: WebView? = null
        private set
    private val recoveryGate = SessionRecoveryGate()
    private val authEpoch = AtomicLong(0L)
    private val authLock = Any()
    private val _authenticationExpired = MutableStateFlow(false)
    val authenticationExpired: StateFlow<Boolean> = _authenticationExpired.asStateFlow()
    
    private val _userInfo = MutableStateFlow<Pair<String, String>?>(null)
    val userInfo: StateFlow<Pair<String, String>?> = _userInfo.asStateFlow()

    @Volatile
    var isMockReviewSession: Boolean = false
        private set

    fun setMockReviewMode(enabled: Boolean) {
        isMockReviewSession = enabled
    }
    
    val requestMutex = kotlinx.coroutines.sync.Mutex()

    fun setUserInfo(name: String, affiliation: String) {
        _userInfo.value = Pair(name, affiliation)
    }

    fun authenticationEpoch(): Long = authEpoch.get()

    fun reportAuthenticationExpired(requestEpoch: Long) {
        synchronized(authLock) {
            if (requestEpoch != authEpoch.get()) return
            if (recoveryGate.beginRecovery()) _authenticationExpired.value = true
        }
    }

    /** Call after the login and authenticated schedule preflight both succeed. */
    fun markAuthenticated() {
        synchronized(authLock) {
            authEpoch.incrementAndGet()
            recoveryGate.completeRecovery()
            _authenticationExpired.value = false
        }
    }

    @Volatile
    private var pageReady = CompletableDeferred<Unit>()

    @Volatile
    private var expectedTargetPrefix: String? = null

    fun attach(webView: WebView) {
        this.webView = webView
    }

    fun markPageReady(loadedUrl: String? = null) {
        val prefix = expectedTargetPrefix
        if (prefix != null && loadedUrl != null && !loadedUrl.startsWith(prefix)) {
            return
        }
        pageReady.complete(Unit)
    }

    fun resetPageReady(targetPrefix: String? = null) {
        expectedTargetPrefix = targetPrefix
        pageReady = CompletableDeferred()
    }

    suspend fun awaitPageReady() {
        val current = pageReady
        if (current.isCompleted) return
        if (withTimeoutOrNull(10_000L) { current.await() } == null) {
            throw MissingXcrewWebViewException()
        }
    }

    suspend fun prepareForLogin() {
        withContext(Dispatchers.Main.immediate) {
            isMockReviewSession = false
            _userInfo.value = null
            _authenticationExpired.value = false
            webView?.let { view ->
                view.stopLoading()
                resetPageReady()
            }
        }
    }

    suspend fun refreshWebViewPage(targetUrl: String = "https://xcrew.korail.com/extrCrewMg/extrRltmCrewWrkPstt.do") {
        withContext(Dispatchers.Main.immediate) {
            val view = webView ?: return@withContext
            view.stopLoading()
            resetPageReady("https://xcrew.korail.com/extrCrewMg/")
            view.loadUrl(targetUrl)
        }
        runCatching {
            withTimeout(12_000L) {
                awaitPageReady()
            }
        }
    }

    fun destroy() {
        webView?.let { view ->
            view.stopLoading()
            view.removeAllViews()
            view.destroy()
        }
        webView = null
    }
}

class XcrewWebViewXhrDataSource(
    private val session: XcrewWebViewSession,
    private val parser: XcrewWorkScheduleParser,
) : XcrewDataSource, DiaDetailDataSource, CrewWorkStatusDataSource {
    override suspend fun getWorkSchedules(month: String): XcrewWorkScheduleResponse {
        require(month.matches(Regex("\\d{6}"))) { "Month must be yyyyMM" }
        if (session.isMockReviewSession) {
            return withContext(Dispatchers.Default) {
                MockReviewDataProvider.getWorkSchedules(month, parser)
            }
        }
        val webView = session.webView ?: throw MissingXcrewWebViewException()
        session.awaitPageReady()

        val empNm = session.userInfo.value?.first?.trim().orEmpty()
        val (path, url, fields) = if (empNm.isNotEmpty()) {
            Triple(
                XCREW_IND_CREW_WRK_PATH,
                XCREW_IND_CREW_WRK_URL,
                listOf("pjtYm" to month, "pjtDt" to "${month}01", "empNm" to empNm)
            )
        } else {
            Triple(
                XCREW_XHR_PATH,
                XCREW_XHR_URL,
                listOf("pjtYm" to month)
            )
        }

        val xhrResponse = try {
            request(
                webView = webView,
                path = path,
                url = url,
                fields = fields,
                logLabel = "month=$month empNm=$empNm",
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            if (empNm.isNotEmpty()) {
                request(
                    webView = webView,
                    path = XCREW_XHR_PATH,
                    url = XCREW_XHR_URL,
                    fields = listOf("pjtYm" to month),
                    logLabel = "fallback month=$month",
                )
            } else {
                throw e
            }
        }

        var responseText = xhrResponse.requireJsonResponse(url)

        var parsedResponse = withContext(Dispatchers.Default) {
            try {
                parser.parse(responseText)
            } catch (error: XcrewResponseException) {
                if (path != XCREW_XHR_PATH) null else throw error
            } catch (error: RuntimeException) {
                if (path != XCREW_XHR_PATH) null else throw UnexpectedXcrewResponseException()
            }
        }

        if (parsedResponse == null) {
            val fallbackXhr = request(
                webView = webView,
                path = XCREW_XHR_PATH,
                url = XCREW_XHR_URL,
                fields = listOf("pjtYm" to month),
                logLabel = "fallback month=$month",
            )
            responseText = fallbackXhr.requireJsonResponse(XCREW_XHR_URL)
            parsedResponse = withContext(Dispatchers.Default) {
                try {
                    parser.parse(responseText)
                } catch (error: XcrewResponseException) {
                    throw error
                } catch (error: RuntimeException) {
                    throw UnexpectedXcrewResponseException()
                }
            }
        }

        if (session.userInfo.value == null) {
            val firstValidDia = parsedResponse.data.firstOrNull { 
                val dia = it.pdiaNo?.trim().orEmpty()
                dia.isNotEmpty() && dia != "연차" && dia != "S" && dia.matches(Regex("[A-Za-z0-9]+")) 
            }
            if (firstValidDia != null && firstValidDia.pjtDt != null && firstValidDia.pdiaNo != null) {
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        fetchDiaDetail(firstValidDia.pjtDt, firstValidDia.pdiaNo)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to prefetch dia detail for user info", e)
                    }
                }
            }
        }

        return parsedResponse
    }

    override suspend fun fetchDiaDetail(pjtDt: String, pdiaNo: String): DiaDetailResponse {
        require(pjtDt.matches(Regex("\\d{8}"))) { "Project date must be yyyyMMdd" }
        require(pdiaNo.matches(Regex("[A-Za-z0-9]+"))) { "Dia number has an invalid format" }
        if (session.isMockReviewSession) {
            return withContext(Dispatchers.Default) {
                MockReviewDataProvider.fetchDiaDetail(pjtDt, pdiaNo)
            }
        }
        val webView = session.webView ?: throw MissingXcrewWebViewException()
        session.awaitPageReady()
        val xhrResponse = request(
            webView = webView,
            path = XCREW_DIA_DETAIL_PATH,
            url = XCREW_DIA_DETAIL_URL,
            fields = listOf("pdiaNo" to pdiaNo, "pjtDt" to pjtDt),
            logLabel = "dia detail",
        )
        val responseText = xhrResponse.requireJsonResponse(XCREW_DIA_DETAIL_URL)
        return withContext(Dispatchers.Default) {
            val parsed = DiaDetailParser().parse(responseText)
            if (session.userInfo.value == null && parsed.rawCrewMeta != null) {
                val vo = parsed.rawCrewMeta
                val name = vo.optString("empNm", "").trim().ifEmpty { vo.optString("usrNm", "").trim() }
                var affil = vo.optString("blngStnNm", "").trim().ifEmpty { vo.optString("blngDptNm", "").trim() }.ifEmpty { vo.optString("deptNm", "").trim() }
                
                if (affil.isEmpty()) affil = vo.optString("empNo", "").trim()
                if (name.isNotEmpty() || affil.isNotEmpty()) session.setUserInfo(name, affil)
            }
            parsed
        }
    }

    override suspend fun fetchCrewWorkStatus(pjtDt: String): List<CrewAssignment> {
        require(pjtDt.matches(Regex("\\d{8}"))) { "Project date must be yyyyMMdd" }
        if (session.isMockReviewSession) {
            return withContext(Dispatchers.Default) {
                MockReviewDataProvider.fetchCrewWorkStatus(pjtDt)
            }
        }
        val webView = session.webView ?: throw MissingXcrewWebViewException()
        session.awaitPageReady()
        val xhrResponse = request(
            webView = webView,
            path = XCREW_CREW_WORK_STATUS_PATH,
            url = XCREW_CREW_WORK_STATUS_URL,
            fields = listOf("pjtDt" to pjtDt),
            logLabel = "date=$pjtDt",
        )
        val responseText = xhrResponse.requireJsonResponse(XCREW_CREW_WORK_STATUS_URL)
        return withContext(Dispatchers.Default) {
            XcrewCrewWorkStatusParser().parse(responseText)
        }
    }

    suspend fun fetchNonOpStatus(year: String): List<NonOpData> {
        require(year.matches(Regex("\\d{4}"))) { "Year must be yyyy" }
        if (session.isMockReviewSession) {
            return withContext(Dispatchers.Default) {
                MockReviewDataProvider.fetchNonOpStatus(year)
            }
        }
        val webView = session.webView ?: throw MissingXcrewWebViewException()
        session.awaitPageReady()

        val path = "/extrCrewMg/searchExtrNonAtulPstt.do"
        val url = "https://xcrew.korail.com$path"
        
        val xhrResponse = request(
            webView = webView,
            path = path,
            url = url,
            fields = listOf(
                "strtDt" to "${year}0101",
                "endDt" to "${year}1231"
            ),
            logLabel = "year=$year",
        )
        
        if (xhrResponse.status == 200) {
            val responseText = xhrResponse.requireJsonResponse(url)
            return withContext(Dispatchers.Default) {
                NonOpStatusParser().parse(responseText)
            }
        } else {
            throw XcrewHttpException(xhrResponse.status)
        }
    }

    private suspend fun request(
        webView: WebView,
        path: String,
        url: String,
        fields: List<Pair<String, String>>,
        logLabel: String,
    ): XcrewWebViewXhrResponse {
        val requestAuthEpoch = session.authenticationEpoch()
        val response = withContext(Dispatchers.Main.immediate) {
            executeXhr(webView, path, fields)
        }
        Log.i(TAG, "response status=${response.status} contentType=${response.contentType} url=$url $logLabel")
        return response.copy(authenticationEpoch = requestAuthEpoch)
    }

    suspend fun fetchPersonalWorkSchedule(pjtDt: String, empNm: String): List<PersonalWorkScheduleDto> {
        require(pjtDt.matches(Regex("\\d{8}"))) { "pjtDt must be yyyyMMdd" }
        if (session.isMockReviewSession) {
            return withContext(Dispatchers.Default) {
                MockReviewDataProvider.fetchPersonalWorkSchedule(pjtDt, empNm)
            }
        }
        val webView = session.webView ?: throw MissingXcrewWebViewException()
        session.awaitPageReady()
        
        val xhrResponse = request(
            webView = webView,
            path = XCREW_IND_CREW_WRK_PATH,
            url = XCREW_IND_CREW_WRK_URL,
            fields = listOf(
                "pjtDt" to pjtDt,
                "empNm" to empNm
            ),
            logLabel = "pjtDt=$pjtDt",
        )
        val responseText = xhrResponse.requireJsonResponse(XCREW_IND_CREW_WRK_URL)

        return withContext(Dispatchers.Default) {
            PersonalWorkScheduleParser().parse(responseText)
        }
    }

    suspend fun fetchAttendanceStatus(empNm: String, pjtDt: String): List<AttendanceStatusDto> {
        require(pjtDt.matches(Regex("\\d{8}"))) { "pjtDt must be yyyyMMdd" }
        if (session.isMockReviewSession) {
            return withContext(Dispatchers.Default) {
                MockReviewDataProvider.fetchAttendanceStatus(empNm, pjtDt)
            }
        }
        val webView = session.webView ?: throw MissingXcrewWebViewException()
        session.awaitPageReady()

        val xhrResponse = request(
            webView = webView,
            path = XCREW_ATTENDANCE_STATUS_PATH,
            url = XCREW_ATTENDANCE_STATUS_URL,
            fields = listOf(
                "empNm" to empNm,
                "pjtDt" to pjtDt
            ),
            logLabel = "empNm=$empNm pjtDt=$pjtDt",
        )
        val responseText = xhrResponse.requireJsonResponse(XCREW_ATTENDANCE_STATUS_URL)

        return withContext(Dispatchers.Default) {
            AttendanceStatusParser().parse(responseText)
        }
    }

    /**
     * 개인근무명세서(/extrCrewMg/searchExtrIndWrkSpec.do) 조회.
     */
    suspend fun fetchPersonalWorkSpec(pjtDt: String): List<com.example.crewmate.labs.paystatement.PayStatementRow> {
        require(pjtDt.matches(Regex("\\d{8}"))) { "pjtDt must be yyyyMMdd" }
        val webView = session.webView ?: throw MissingXcrewWebViewException()
        session.awaitPageReady()

        val xhrResponse = request(
            webView = webView,
            path = XCREW_IND_WRK_SPEC_PATH,
            url = XCREW_IND_WRK_SPEC_URL,
            fields = listOf("pjtDt" to pjtDt),
            logLabel = "pjtDt=$pjtDt",
        )
        val responseText = xhrResponse.requireJsonResponse(XCREW_IND_WRK_SPEC_URL)

        return withContext(Dispatchers.Default) {
            com.example.crewmate.labs.paystatement.PayStatementParser.parse(responseText)
        }
    }

    private fun XcrewWebViewXhrResponse.requireJsonResponse(url: String): String {
        if (XcrewAuthenticationResponseClassifier.isExpired(status, contentType, responseText)) {
            session.reportAuthenticationExpired(authenticationEpoch)
            throw XcrewSessionExpiredException()
        }
        
        // Sometimes Xcrew API returns 200 OK with a non-HTML body (or JSON error message)
        // when the session is actually invalid or corrupted.
        if (status == 200 && responseText.contains("\"data\"") == false && responseText.contains("error", ignoreCase = true)) {
            session.reportAuthenticationExpired(authenticationEpoch)
            throw XcrewSessionExpiredException()
        }

        if (status != 200) {
            if (status == 0) throw XcrewNetworkException()
            throw XcrewHttpException(status)
        }
        val isJson = contentType.contains("application/json", ignoreCase = true) &&
            responseText.trimStart().let { it.startsWith("{") || it.startsWith("[") }
        Log.i(TAG, "responseIsJson=$isJson url=$url")
        if (!isJson) throw InvalidXcrewJsonException()
        return responseText
    }

    private suspend fun executeXhr(
        webView: WebView,
        path: String,
        fields: List<Pair<String, String>>,
    ): XcrewWebViewXhrResponse {
        val url = "https://xcrew.korail.com$path"
        val safeLabel = if (path == XCREW_XHR_PATH) fields.firstOrNull()?.second?.let { " month=$it" }.orEmpty() else ""
        Log.i(TAG, "request start url=$url$safeLabel")
        val formBody = fields.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8.name())}=" +
                URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }

        val requestId = UUID.randomUUID().toString()
        val quotedId = JSONObject.quote(requestId)
        val quotedPath = JSONObject.quote(path)
        val quotedBody = JSONObject.quote(formBody)
        val startScript = """(function() {
            var requestId = $quotedId;
            var resultStore = window.__crewMateXcrewXhrResults ||
                (window.__crewMateXcrewXhrResults = Object.create(null));
            var xhrStore = window.__crewMateXcrewXhrs ||
                (window.__crewMateXcrewXhrs = Object.create(null));
            var xhr = new XMLHttpRequest();
            xhrStore[requestId] = xhr;
            function finish(status, contentType, responseText) {
                resultStore[requestId] = {
                    status: status,
                    contentType: contentType || '',
                    responseText: status === 200 ? (responseText || '') : ''
                };
                delete xhrStore[requestId];
            }
            try {
                xhr.open('POST', $quotedPath, true);
                xhr.withCredentials = true;
                xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
                xhr.setRequestHeader('Accept', '$XCREW_XHR_ACCEPT');
                xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                xhr.onreadystatechange = function() {
                    if (xhr.readyState === XMLHttpRequest.DONE) {
                        finish(xhr.status, xhr.getResponseHeader('Content-Type'), xhr.responseText);
                    }
                };
                xhr.onerror = function() { finish(xhr.status || 0, xhr.getResponseHeader('Content-Type'), ''); };
                xhr.send($quotedBody);
            } catch (error) {
                finish(0, '', '');
            }
            return true;
        })()"""

        evaluateJavascript(webView, startScript)
        var reDispatchedAfterNav = false
        return try {
            withTimeout(XHR_TIMEOUT_MILLIS) {
                while (true) {
                    val result = evaluateJavascript(
                        webView,
                        """(function() {
                            var store = window.__crewMateXcrewXhrResults;
                            var xhrStore = window.__crewMateXcrewXhrs;
                            if (!store && !xhrStore) return "__NAV_RESET__";
                            if (store && store[$quotedId]) {
                                var result = store[$quotedId];
                                delete store[$quotedId];
                                return result;
                            }
                            if (xhrStore && xhrStore[$quotedId]) return null;
                            return "__NAV_RESET__";
                        })()""",
                    )
                    if (result == "\"__NAV_RESET__\"") {
                        if (!reDispatchedAfterNav) {
                            reDispatchedAfterNav = true
                            Log.w(TAG, "WebView navigated during XHR, awaiting page ready and re-dispatching url=$url")
                            session.awaitPageReady()
                            evaluateJavascript(webView, startScript)
                            delay(100)
                            continue
                        } else {
                            return@withTimeout XcrewWebViewXhrResponse(0, "", "")
                        }
                    }
                    if (result != null && result != "null") {
                        return@withTimeout result.toXcrewWebViewXhrResponse()
                    }
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE")
                XcrewWebViewXhrResponse(0, "", "")
            }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "request timeout url=$url$safeLabel")
            throw timeout
        } finally {
            val cleanupScript = """(function() {
                    var xhrStore = window.__crewMateXcrewXhrs;
                    if (xhrStore && xhrStore[$quotedId]) {
                        try { xhrStore[$quotedId].abort(); } catch (error) {}
                        delete xhrStore[$quotedId];
                    }
                    var store = window.__crewMateXcrewXhrResults;
                    if (store) delete store[$quotedId];
                })()"""
            // Cleanup must not suspend here: after cancellation/timeout the page may
            // no longer deliver evaluateJavascript's callback, leaving the load job stuck.
            webView.post { webView.evaluateJavascript(cleanupScript, null) }
        }
    }

    private suspend fun evaluateJavascript(webView: WebView, script: String): String? =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    private fun String.toXcrewWebViewXhrResponse(): XcrewWebViewXhrResponse {
        val json = try {
            JSONTokener(this).nextValue() as? JSONObject
                ?: throw UnexpectedXcrewResponseException()
        } catch (error: org.json.JSONException) {
            throw UnexpectedXcrewResponseException()
        }
        return XcrewWebViewXhrResponse(
            status = json.optInt("status", 0),
            contentType = json.optString("contentType"),
            responseText = json.optString("responseText"),
        )
    }
}

private data class XcrewWebViewXhrResponse(
    val status: Int,
    val contentType: String,
    val responseText: String,
    val authenticationEpoch: Long = 0L,
)

class MissingXcrewWebViewException : XcrewResponseException("로그인된 Xcrew WebView를 찾을 수 없습니다.")
