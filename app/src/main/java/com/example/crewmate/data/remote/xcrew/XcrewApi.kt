package com.example.crewmate.data.remote.xcrew

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

const val XCREW_WORK_SCHEDULE_API_URL =
    "https://xcrew.korail.com/extrCrewMg/searchExtrRltmCrewWrkPstt.do"
const val XCREW_COOKIE_URL = XCREW_WORK_SCHEDULE_API_URL

private const val TAG = "XcrewApi"
private const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8"
private val FORM_MEDIA_TYPE = FORM_CONTENT_TYPE.toMediaType()
private const val ACCEPT_VALUE = "application/json, text/javascript, */*; q=0.01"
private const val XCREW_CHROME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
private const val XCREW_ORIGIN = "https://xcrew.korail.com"
private const val XCREW_SCHEDULE_REFERER =
    "https://xcrew.korail.com/extrCrewMg/extrItlCrewWrkPstt.do"

class XcrewApi(private val client: OkHttpClient) {
    suspend fun fetchScheduleJson(month: String, cookie: String): String = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("pjtYm", month)
            .build()
        val request = Request.Builder()
            .url(XCREW_WORK_SCHEDULE_API_URL)
            .header("Cookie", cookie)
            .header("Content-Type", FORM_CONTENT_TYPE)
            .header("Accept", ACCEPT_VALUE)
            .header("User-Agent", XCREW_CHROME_USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", XCREW_ORIGIN)
            .header("Referer", XCREW_SCHEDULE_REFERER)
            .post(Utf8FormBody(formBody))
            .build()

        // Never log the Cookie header or any request/response body.
        Log.i(
            TAG,
            "request url=$XCREW_WORK_SCHEDULE_API_URL month=$month " +
                "acceptConfigured=${request.header("Accept") == ACCEPT_VALUE} " +
                "userAgentConfigured=${request.header("User-Agent") == XCREW_CHROME_USER_AGENT}",
        )

        try {
            client.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                Log.i(TAG, "response status=${response.code} contentType=$contentType")
                when {
                    response.code == 401 || response.code == 403 ->
                        throw XcrewSessionExpiredException()

                    response.code in 300..399 -> {
                        val location = response.header("Location").orEmpty()
                        if (location.contains("login", ignoreCase = true)) {
                            throw XcrewSessionExpiredException()
                        }
                        throw XcrewHttpException(response.code)
                    }

                    !response.isSuccessful -> throw XcrewHttpException(response.code)
                }

                val responseBody = response.body?.string() ?: throw UnexpectedXcrewResponseException()
                if (contentType.contains("text/html", ignoreCase = true) ||
                    responseBody.trimStart().startsWith("<", ignoreCase = true)
                ) {
                    throw XcrewSessionExpiredException()
                }
                responseBody
            }
        } catch (error: IOException) {
            throw XcrewNetworkException()
        }
    }
}

/** Keeps FormBody's URL encoding while preserving Xcrew's charset parameter on the wire. */
private class Utf8FormBody(private val formBody: FormBody) : RequestBody() {
    override fun contentType(): MediaType = FORM_MEDIA_TYPE

    override fun contentLength(): Long = formBody.contentLength()

    override fun writeTo(sink: BufferedSink) = formBody.writeTo(sink)
}

class XcrewSessionExpiredException : XcrewResponseException("Xcrew 로그인 세션이 만료되었을 수 있습니다.")

class XcrewHttpException(val statusCode: Int) :
    XcrewResponseException("Xcrew API 요청에 실패했습니다. (HTTP $statusCode)")

class XcrewNetworkException : XcrewResponseException("네트워크 연결을 확인해 주세요.")

class MissingXcrewCookieException : XcrewResponseException("Xcrew 로그인 세션을 찾을 수 없습니다.")
