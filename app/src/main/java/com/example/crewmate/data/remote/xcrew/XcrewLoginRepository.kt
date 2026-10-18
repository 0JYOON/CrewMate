package com.example.crewmate.data.remote.xcrew

import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "XcrewLogin"
private const val XCREW_ORIGIN = "https://xcrew.korail.com"
private const val LOGIN_PAGE_URL = "$XCREW_ORIGIN/loginView.do"
private const val LOGIN_POST_URL = "$XCREW_ORIGIN/login.do"
private const val SCHEDULE_PATH = "/extrCrewMg/extrRltmCrewWrkPstt.do"
private const val SCHEDULE_API_URL = "$XCREW_ORIGIN/extrCrewMg/searchExtrRltmCrewWrkPstt.do"

class XcrewLoginRepository(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) {
    suspend fun login(
        employeeNumber: String,
        password: String,
        diagnosticsEnabled: Boolean = true,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        // Clear stale session cookies before starting login attempt
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { cont ->
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }

        // Each attempt starts without cookies left by an earlier success or failed attempt.
        // Its temporary cookies are committed to WebView only after the confirmed success redirect.
        val attemptCookies = LoginAttemptCookieJar()
        val receivedCookies = mutableListOf<SetCookieRecord>()
        val client = OkHttpClient.Builder()
            .cookieJar(attemptCookies)
            .followRedirects(false)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.request.url.host == "xcrew.korail.com") {
                    response.headers("Set-Cookie").forEach { header ->
                        synchronized(receivedCookies) {
                            receivedCookies += SetCookieRecord(response.request.url, header)
                        }
                    }
                }
                response
            }
            .build()

        val message = loadLoginMessage(client, diagnosticsEnabled)
        val request = Request.Builder()
            .url(LOGIN_POST_URL)
            .header("Origin", XCREW_ORIGIN)
            .header("Referer", LOGIN_PAGE_URL)
            .post(
                FormBody.Builder()
                    .add("message", message)
                    .add("epno", employeeNumber)
                    .add("pwd", password)
                    .build(),
            )
            .build()

        if (diagnosticsEnabled) Log.i(TAG, "LOGIN_POST_START")
        val response = try {
            client.newCall(request).execute()
        } catch (_: IOException) {
            if (diagnosticsEnabled) Log.w(TAG, "LOGIN_POST_FAILURE=HTTP_REQUEST_FAILED")
            throw XcrewLoginNetworkException()
        }
        response.use {
            val location = response.header("Location")
            val destination = location?.let(response.request.url::resolve)
            if (diagnosticsEnabled) {
                Log.i(TAG, "LOGIN_POST_HTTP_STATUS=${response.code}")
                Log.i(TAG, "LOGIN_REDIRECT_PATH=${destination?.encodedPath ?: "missing"}")
            }
            if (response.code in 500..599) throw XcrewLoginNetworkException()
            if (response.code !in 200..399) {
                throw XcrewLoginUnexpectedResponseException()
            }
            val successfulRedirect = response.code == 302 &&
                destination?.host == "xcrew.korail.com" &&
                destination.encodedPath == SCHEDULE_PATH
            if (!successfulRedirect) throw XcrewLoginFailureException()

            val cookieRecords = synchronized(receivedCookies) { receivedCookies.toList() }
            val hasScheduleCookie = withContext(Dispatchers.Main.immediate) {
                cookieRecords.forEach { record -> saveCookie(record.url, record.header) }
                val hasCookie = !cookieManager.getCookie(SCHEDULE_API_URL).isNullOrBlank()
                cookieManager.flush()
                hasCookie
            }
            if (!hasScheduleCookie) {
                if (diagnosticsEnabled) Log.w(TAG, "LOGIN_FAILURE=COOKIE_NOT_AVAILABLE")
                throw XcrewLoginSessionException()
            }

            // --- 이름/소속 스크래핑 ---
            var userName = ""
            var userAffil = ""
            try {
                if (destination != null) {
                    val htmlRequest = Request.Builder()
                        .url(destination)
                        .header("Cookie", cookieRecords.joinToString("; ") { it.header.substringBefore(";") })
                        .get()
                        .build()
                    val htmlResponse = client.newCall(htmlRequest).execute()
                    val html = htmlResponse.body?.string() ?: ""

                    // 공백이나 불필요한 태그를 정리한 텍스트
                    val plainText = html.replace(Regex("<[^>]*>"), " ")
                                        .replace("&nbsp;", " ")
                                        .replace(Regex("\\s+"), " ")

                    // 1. 왼쪽 상단 프로필 영역 매칭 (가장 확실함)
                    // 사진 예시: "안산승소 윤영준 최근 접속 일시..."
                    val profileMatch = Regex("([가-힣a-zA-Z0-9]+(?:본부|지사|사업소|센터|역|소|승소|승무원))\\s+([가-힣*○O]{2,6})\\s+최근\\s*접속").find(plainText)
                    
                    if (profileMatch != null) {
                        userAffil = profileMatch.groupValues[1].trim()
                        userName = profileMatch.groupValues[2].trim()
                    } else {
                        // 2. 검색 폼의 Input 필드 매칭 ("소속 [ 안산승소 ]", "승무원 [ 윤영준 ]")
                        val inputNameMatch = Regex("승무원[\\s\\S]{0,100}?value=['\"]([가-힣*○O]{2,6})['\"]").find(html)
                        if (inputNameMatch != null) userName = inputNameMatch.groupValues[1].trim()

                        val inputAffilMatch = Regex("소속[\\s\\S]{0,100}?value=['\"]([^'\"]+)['\"]").find(html)
                        if (inputAffilMatch != null) userAffil = inputAffilMatch.groupValues[1].trim()

                        // 3. Fallback (기존 방식들)
                        if (userName.isEmpty()) {
                            val nameMatch = Regex("(?:empNm|usrNm)\\s*[:=]\\s*['\"]([^'\"]+)['\"]").find(html)
                                ?: Regex("value=['\"]([^'\"]+)['\"][^>]*name=['\"](?:empNm|usrNm)['\"]").find(html)
                                ?: Regex("([가-힣*○O]{2,6})\\s*님").find(plainText)
                                ?: Regex("(?:성명|이름)\\s*[:|：]?\\s*([가-힣*○O]{2,6})").find(plainText)
                            if (nameMatch != null) userName = nameMatch.groupValues[1].trim()
                        }

                        if (userAffil.isEmpty()) {
                            val affilMatch = Regex("([가-힣0-9a-zA-Z]+(?:본부|지사|사업소|센터|역|소|승소|승무원))").findAll(plainText)
                                .map { it.groupValues[1] }.maxByOrNull { it.length }
                            if (affilMatch != null) userAffil = affilMatch.trim()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scrape user info", e)
            }
            
            Pair(userName, userAffil)
        }
    }

    private fun loadLoginMessage(client: OkHttpClient, diagnosticsEnabled: Boolean): String {
        if (diagnosticsEnabled) Log.i(TAG, "LOGIN_PAGE_REQUEST_START")
        val response = try {
            client.newCall(Request.Builder().url(LOGIN_PAGE_URL).get().build()).execute()
        } catch (_: IOException) {
            if (diagnosticsEnabled) Log.w(TAG, "LOGIN_PAGE_FAILURE=HTTP_REQUEST_FAILED")
            throw XcrewLoginNetworkException()
        }

        response.use {
            if (diagnosticsEnabled) Log.i(TAG, "LOGIN_PAGE_HTTP_STATUS=${response.code}")
            if (response.code != 200) {
                if (diagnosticsEnabled) Log.w(TAG, "LOGIN_PAGE_FAILURE=HTTP_STATUS_UNEXPECTED")
                if (response.code in 500..599) throw XcrewLoginNetworkException()
                throw XcrewLoginPageException(LoginPageFailureReason.HTTP_STATUS_UNEXPECTED)
            }
            val html = response.body?.string()
            if (html == null) {
                if (diagnosticsEnabled) Log.w(TAG, "LOGIN_PAGE_FAILURE=HTML_EMPTY")
                throw XcrewLoginPageException(LoginPageFailureReason.HTML_EMPTY)
            }
            if (html.isBlank()) {
                if (diagnosticsEnabled) Log.w(TAG, "LOGIN_PAGE_FAILURE=HTML_EMPTY")
                throw XcrewLoginPageException(LoginPageFailureReason.HTML_EMPTY)
            }

            return when (val result = html.findHiddenMessage()) {
                HiddenMessageResult.ElementMissing -> {
                    if (diagnosticsEnabled) Log.w(TAG, "LOGIN_PAGE_FAILURE=MESSAGE_ELEMENT_MISSING")
                    throw XcrewLoginPageException(LoginPageFailureReason.MESSAGE_ELEMENT_MISSING)
                }
                HiddenMessageResult.ValueMissing -> {
                    if (diagnosticsEnabled) Log.w(TAG, "LOGIN_PAGE_FAILURE=MESSAGE_VALUE_MISSING")
                    throw XcrewLoginPageException(LoginPageFailureReason.MESSAGE_VALUE_MISSING)
                }
                is HiddenMessageResult.Found -> {
                    if (diagnosticsEnabled) Log.i(TAG, "LOGIN_PAGE_MESSAGE_FOUND=true")
                    result.value
                }
            }
        }
    }

    private suspend fun saveCookie(url: HttpUrl, value: String) {
        suspendCancellableCoroutine { continuation ->
            cookieManager.setCookie(url.toString(), value) { success ->
                if (!continuation.isActive) return@setCookie
                if (success) continuation.resume(Unit)
                else continuation.resumeWithException(IOException("Unable to save Xcrew session cookie"))
            }
        }
    }

    private fun String.findHiddenMessage(): HiddenMessageResult {
        var searchFrom = 0
        while (true) {
            val start = indexOf("<input", startIndex = searchFrom, ignoreCase = true)
            if (start < 0) return HiddenMessageResult.ElementMissing
            val end = findTagEnd(start) ?: return HiddenMessageResult.ElementMissing
            val tag = substring(start, end + 1)
            val attributes = parseAttributes(tag)
            if (attributes["id"].equals("message", ignoreCase = true) &&
                attributes["name"].equals("message", ignoreCase = true) &&
                attributes["type"].equals("hidden", ignoreCase = true)
            ) {
                val value = attributes["value"]?.decodeHtmlEntities()
                return if (value == null) HiddenMessageResult.ValueMissing else HiddenMessageResult.Found(value)
            }
            searchFrom = end + 1
        }
    }

    private fun String.findTagEnd(start: Int): Int? {
        var quote: Char? = null
        for (index in start until length) {
            val char = this[index]
            if (quote == null && (char == '\'' || char == '"')) quote = char
            else if (quote != null && quote == char) quote = null
            else if (quote == null && char == '>') return index
        }
        return null
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        val attributePattern = Regex("""(?is)([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s/>]+))""")
        return attributePattern.findAll(tag).associate { match ->
            val value = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value.orEmpty()
            match.groupValues[1].lowercase() to value
        }
    }

    private fun String.decodeHtmlEntities(): String = ENTITY_PATTERN.replace(this) { match ->
        val entity = match.groupValues[1]
        val decoded = when {
            entity.equals("amp", ignoreCase = true) -> "&"
            entity.equals("lt", ignoreCase = true) -> "<"
            entity.equals("gt", ignoreCase = true) -> ">"
            entity.equals("quot", ignoreCase = true) -> "\""
            entity.equals("apos", ignoreCase = true) || entity == "#39" -> "'"
            entity.equals("nbsp", ignoreCase = true) -> "\u00a0"
            entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(16)?.toChar()?.toString()
            entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.toChar()?.toString()
            else -> null
        }
        decoded ?: match.value
    }

    private companion object {
        val ENTITY_PATTERN = Regex("&([A-Za-z]+|#[0-9]+|#x[0-9A-Fa-f]+);")
    }
}

private class LoginAttemptCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            this.cookies.removeAll { existing ->
                existing.name == cookie.name && existing.domain == cookie.domain && existing.path == cookie.path
            }
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies += cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }
}

private data class SetCookieRecord(val url: HttpUrl, val header: String)

private sealed interface HiddenMessageResult {
    data object ElementMissing : HiddenMessageResult
    data object ValueMissing : HiddenMessageResult
    data class Found(val value: String) : HiddenMessageResult
}

enum class LoginPageFailureReason {
    HTTP_REQUEST_FAILED,
    HTTP_STATUS_UNEXPECTED,
    HTML_EMPTY,
    MESSAGE_ELEMENT_MISSING,
    MESSAGE_VALUE_MISSING,
}

class XcrewLoginFailureException : Exception()
class XcrewLoginPageException(val reason: LoginPageFailureReason) : Exception()
class XcrewLoginSessionException : Exception()
class XcrewLoginNetworkException : Exception()
class XcrewLoginUnexpectedResponseException : Exception()
