package com.example.crewmate.login

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 특정 사번의 앱 진입을 비공개 해시 기반으로 차단하는 가드.
 * - 평문 사번은 코드·리소스·원격 파일 어디에도 저장하지 않고 Salted SHA-256 단방향 해시만 비교합니다.
 * - 개발자 외에는 해시값만으로 대상 사번을 역산하거나 열람할 수 없습니다.
 * - 최초(수동) 로그인, 매일 새벽 03:00 백그라운드 체크, 오프라인→온라인 전환 시 원격 목록을 확인합니다.
 */
object EmployeeBanGuard {

    private const val SALT = "crewmate_salt_2026_xcrew_guard"
    private const val REMOTE_HASH_LIST_URL =
        "https://raw.githubusercontent.com/0JYOON/CrewMate/main/config/blocked.json"

    private const val PREFS_BAN = "crewmate_ban_prefs"
    private const val KEY_LAST_CHECK_DATE = "last_remote_ban_check_date"
    private const val KEY_PENDING_ONLINE_CHECK = "pending_online_ban_check"
    private const val KEY_CACHED_BANNED = "is_cached_banned"

    const val DISGUISED_ERROR_MESSAGE =
        "Xcrew 로그인 서버 응답이 지연되고 있습니다. 네트워크 상태를 확인한 뒤 잠시 후 다시 시도해 주세요."

    // 로컬 내장 차단 해시 목록 (Salted SHA-256 hex, 소문자)
    private val LOCAL_BANNED_HASHES: Set<String> = emptySet()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 사번 문자열을 정규화(trim)한 뒤 고유 Salt와 함께 SHA-256 64자리 hex로 변환합니다.
     */
    fun hashEmployeeNumber(employeeNumber: String): String {
        val normalized = employeeNumber.trim()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$SALT:$normalized".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 원격 JSON({"hashes": ["..."]})에서 유효한 64자리 SHA-256 해시 목록을 파싱합니다.
     */
    fun parseRemoteHashes(jsonBody: String): Set<String> {
        if (jsonBody.isBlank()) return emptySet()
        return runCatching {
            val obj = JSONObject(jsonBody)
            val arr = obj.optJSONArray("hashes") ?: return@runCatching emptySet()
            buildSet {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i, "").trim().lowercase()
                    if (item.length == 64 && item.all { it in '0'..'9' || it in 'a'..'f' }) {
                        add(item)
                    }
                }
            }
        }.getOrDefault(emptySet())
    }

    fun isCachedBanned(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_BAN, Context.MODE_PRIVATE)
            .getBoolean(KEY_CACHED_BANNED, false)

    fun setPendingOnlineCheck(context: Context, pending: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_BAN, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_ONLINE_CHECK, pending)
            .apply()
    }

    fun hasPendingOrStaleDailyCheck(
        context: Context,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_BAN, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PENDING_ONLINE_CHECK, false)) return true
        val lastDate = prefs.getString(KEY_LAST_CHECK_DATE, null)
        return lastDate != today.toString()
    }

    private suspend fun fetchRemoteHashesOrNull(remoteUrl: String): Set<String>? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(remoteUrl)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) return@runCatching emptySet()
                if (!response.isSuccessful) return@runCatching null
                parseRemoteHashes(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    /**
     * 원격 해시 목록을 조회하고 캐시 상태를 갱신합니다.
     * 차단 사번으로 확인되면 저장된 자동 로그인 정보도 즉시 삭제합니다.
     */
    suspend fun checkAndCacheBanStatus(
        context: Context,
        employeeNumber: String?,
        remoteUrl: String = REMOTE_HASH_LIST_URL,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val appCtx = context.applicationContext
        val normalized = employeeNumber?.trim().orEmpty()
        if (normalized.isEmpty()) return isCachedBanned(appCtx)

        val targetHash = hashEmployeeNumber(normalized)
        val prefs = appCtx.getSharedPreferences(PREFS_BAN, Context.MODE_PRIVATE)
        if (targetHash in LOCAL_BANNED_HASHES) {
            prefs.edit()
                .putBoolean(KEY_CACHED_BANNED, true)
                .putBoolean(KEY_PENDING_ONLINE_CHECK, false)
                .putString(KEY_LAST_CHECK_DATE, today.toString())
                .apply()
            runCatching { SecureCredentialStore(appCtx).clear() }
            return true
        }

        val remoteHashes = fetchRemoteHashesOrNull(remoteUrl) ?: return isCachedBanned(appCtx)
        val isBanned = targetHash in remoteHashes
        prefs.edit()
            .putBoolean(KEY_CACHED_BANNED, isBanned)
            .putBoolean(KEY_PENDING_ONLINE_CHECK, false)
            .putString(KEY_LAST_CHECK_DATE, today.toString())
            .apply()
        if (isBanned) {
            runCatching { SecureCredentialStore(appCtx).clear() }
        }
        return isBanned
    }

    /**
     * 입력된 사번이 차단 대상인지 검사합니다.
     * @param fetchRemote true(최초/수동 로그인)일 때만 원격 해시 목록을 추가 조회합니다.
     */
    suspend fun isEmployeeBanned(
        employeeNumber: String,
        fetchRemote: Boolean,
        remoteUrl: String = REMOTE_HASH_LIST_URL
    ): Boolean {
        val normalized = employeeNumber.trim()
        if (normalized.isEmpty()) return false

        val targetHash = hashEmployeeNumber(normalized)
        if (targetHash in LOCAL_BANNED_HASHES) {
            return true
        }
        if (!fetchRemote) {
            return false
        }

        val remoteHashes = fetchRemoteHashesOrNull(remoteUrl).orEmpty()
        return targetHash in remoteHashes
    }
}
