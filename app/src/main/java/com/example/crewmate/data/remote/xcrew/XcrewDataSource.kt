package com.example.crewmate.data.remote.xcrew

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface XcrewDataSource {
    suspend fun getWorkSchedules(month: String): XcrewWorkScheduleResponse
}

fun interface XcrewCookieProvider {
    fun getXcrewCookie(): String?
}

class WebViewXcrewCookieProvider : XcrewCookieProvider {
    override fun getXcrewCookie(): String? = CookieManager.getInstance().getCookie(XCREW_COOKIE_URL)
}

class XcrewDataSourceImpl(
    private val api: XcrewApi,
    private val cookieProvider: XcrewCookieProvider,
    private val parser: XcrewWorkScheduleParser,
) : XcrewDataSource {
    override suspend fun getWorkSchedules(month: String): XcrewWorkScheduleResponse {
        val cookie = withContext(Dispatchers.IO) { cookieProvider.getXcrewCookie() }
            ?.takeIf(String::isNotBlank)
            ?: throw MissingXcrewCookieException()

        val json = api.fetchScheduleJson(month, cookie)
        return withContext(Dispatchers.Default) {
            try {
                parser.parse(json)
            } catch (error: XcrewResponseException) {
                throw error
            } catch (error: RuntimeException) {
                throw UnexpectedXcrewResponseException()
            }
        }
    }
}
