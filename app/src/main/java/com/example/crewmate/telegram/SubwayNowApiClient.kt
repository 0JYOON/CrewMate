package com.example.crewmate.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PinAuthResponse(
    val status: String,
    val expiresIn: Int
)

data class TrackResponse(
    val status: String,
    val trackingId: Int?,
    val trainNo: String?,
    val trackingStatus: String?
)

object SubwayNowApiClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun ping(serverUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${serverUrl.trimEnd('/')}/api/v1/ping"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        }
    }

    suspend fun requestPin(serverUrl: String, chatId: String, pin: String): Result<PinAuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${serverUrl.trimEnd('/')}/api/v1/auth/request"
            val json = JSONObject().apply {
                put("chat_id", chatId.trim().toLong())
                put("pin", pin.trim())
            }.toString()
            val req = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(bodyStr).optString("error").takeIf { it.isNotBlank() }
                    }.getOrNull()
                    throw IllegalStateException(errMsg ?: "서버 에러 (${resp.code})")
                }
                val obj = JSONObject(bodyStr)
                PinAuthResponse(
                    status = obj.optString("status", "ok"),
                    expiresIn = obj.optInt("expires_in", 300)
                )
            }
        }
    }

    suspend fun checkAuthStatus(serverUrl: String, chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${serverUrl.trimEnd('/')}/api/v1/auth/status?chat_id=${chatId.trim()}"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@runCatching false
                }
                val obj = JSONObject(bodyStr)
                obj.optBoolean("verified", false)
            }
        }
    }

    suspend fun sendTrack(
        serverUrl: String,
        chatId: String,
        command: String,
        startDateTime: String? = null
    ): Result<TrackResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${serverUrl.trimEnd('/')}/api/v1/track"
            val json = JSONObject().apply {
                put("chat_id", chatId.trim().toLong())
                put("command", command.trim())
                if (!startDateTime.isNullOrBlank()) {
                    put("start_datetime", startDateTime.trim())
                }
            }.toString()
            val req = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(bodyStr).optString("error").takeIf { it.isNotBlank() }
                    }.getOrNull()
                    throw IllegalStateException(errMsg ?: "전송 실패 (${resp.code})")
                }
                val obj = JSONObject(bodyStr)
                TrackResponse(
                    status = obj.optString("status", "SUCCESS"),
                    trackingId = if (obj.has("tracking_id")) obj.getInt("tracking_id") else null,
                    trainNo = obj.optString("train_no").takeIf { it.isNotEmpty() },
                    trackingStatus = obj.optString("tracking_status").takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    suspend fun cancelTrack(serverUrl: String, chatId: String, trainNo: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${serverUrl.trimEnd('/')}/api/v1/stop"
            val json = JSONObject().apply {
                put("chat_id", chatId.trim().toLong())
                if (!trainNo.isNullOrBlank()) {
                    put("train_no", trainNo.trim())
                }
            }.toString()
            val req = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(bodyStr).optString("error").takeIf { it.isNotBlank() }
                    }.getOrNull()
                    throw IllegalStateException(errMsg ?: "취소 실패 (${resp.code})")
                }
                val obj = JSONObject(bodyStr)
                obj.optBoolean("cancelled", true)
            }
        }
    }
}
