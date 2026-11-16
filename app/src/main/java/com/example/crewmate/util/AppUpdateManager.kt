package com.example.crewmate.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.crewmate.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * CrewMate GitHub Releases 연동 자체 인앱 업데이트(Self-Update) 매니저.
 *
 * 공식 저장소: https://github.com/0JYOON/CrewMate
 * - GitHub Releases API 기반 최신 버전 감지
 * - 앱 기동 시 12시간 주기 백그라운드 자동 체크 및 '오늘 하루 보지 않기' 지원
 * - 인앱 APK 스트리밍 다운로드 (0% ~ 100% 진행률 콜백)
 * - FileProvider 기반 Android 8.0+ 원터치 패키지 인스톨러 트리거
 * - 외부 웹 브라우저 다운로드 Fallback 지원
 */
object AppUpdateManager {

    const val GITHUB_OWNER = "0JYOON"
    const val GITHUB_REPO = "CrewMate"
    const val GITHUB_RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val GITHUB_REPO_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"

    private const val PREFS_NAME = "crewmate_update_prefs"
    private const val KEY_LAST_AUTO_CHECK_TIME = "last_auto_check_timestamp"
    private const val KEY_LAST_AUTO_CHECK_DATE = "last_auto_check_date"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"
    private const val KEY_DISMISSED_DATE = "dismissed_date"

    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6시간 (또는 날짜 변경 시 즉시 체크)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    sealed class UpdateCheckResult {
        data class UpdateAvailable(
            val latestVersion: String,
            val currentVersion: String,
            val releaseTitle: String,
            val releaseNotes: String,
            val downloadUrl: String,
            val hasDirectApk: Boolean
        ) : UpdateCheckResult()

        data class Latest(
            val currentVersion: String
        ) : UpdateCheckResult()

        data class Error(
            val message: String
        ) : UpdateCheckResult()
    }

    /**
     * 시맨틱 버전 문자열 비교 (예: "1.0.37" vs "1.0.38").
     * @return target이 current보다 높으면 > 0, 같으면 0, 낮으면 < 0
     */
    fun compareVersions(current: String, target: String): Int {
        val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")
        val cleanTarget = target.trim().removePrefix("v").removePrefix("V")

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val targetParts = cleanTarget.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currentParts.size, targetParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val t = targetParts.getOrElse(i) { 0 }
            if (t != c) {
                return t.compareTo(c)
            }
        }
        return 0
    }

    /**
     * GitHub Releases API를 조회하여 최신 업데이트 여부 확인.
     * @param apiUrl 테스트용으로 대체 가능한 API 주소 (기본값: GitHub 공식 Releases API)
     */
    suspend fun checkForUpdate(
        apiUrl: String = GITHUB_RELEASES_API_URL,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "CrewMate-Android/$currentVersion")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext UpdateCheckResult.Latest(currentVersion)
                }
                return@withContext UpdateCheckResult.Error("버전 정보를 가져올 수 없습니다 (HTTP ${response.code})")
            }

            val responseBody = response.body?.string().orEmpty()
            if (responseBody.isBlank()) {
                return@withContext UpdateCheckResult.Error("서버 응답이 비어있습니다.")
            }

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", tagName)
            val body = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", GITHUB_REPO_URL)

            val latestVersion = tagName.removePrefix("v").removePrefix("V")
            if (latestVersion.isBlank()) {
                return@withContext UpdateCheckResult.Error("버전 태그를 확인할 수 없습니다.")
            }

            // APK 에셋 추출
            var apkDownloadUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        val rawUrl = asset.optString("browser_download_url", "")
                        if (rawUrl.isNotBlank()) {
                            apkDownloadUrl = rawUrl
                            break
                        }
                    }
                }
            }

            val finalDownloadUrl = apkDownloadUrl ?: htmlUrl
            val hasDirectApk = apkDownloadUrl != null

            val isNewer = compareVersions(currentVersion, latestVersion) > 0
            if (isNewer) {
                UpdateCheckResult.UpdateAvailable(
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    releaseTitle = releaseTitle,
                    releaseNotes = body,
                    downloadUrl = finalDownloadUrl,
                    hasDirectApk = hasDirectApk
                )
            } else {
                UpdateCheckResult.Latest(currentVersion)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("네트워크 오류: ${e.localizedMessage ?: "연결 실패"}")
        }
    }

    /**
     * 앱 시작/포그라운드 복귀 시 자동 업데이트 체크가 필요한지 판정.
     * - 오프라인 모드인 경우 false
     * - 날짜가 바뀌었거나 마지막 성공 체크 후 6시간 경과 시 true
     */
    fun shouldCheckAutoUpdate(context: Context, isOfflineMode: Boolean): Boolean {
        if (isOfflineMode) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        val lastDate = prefs.getString(KEY_LAST_AUTO_CHECK_DATE, null)
        if (lastDate != todayStr) return true
        val lastCheck = prefs.getLong(KEY_LAST_AUTO_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()
        return (now - lastCheck) >= AUTO_CHECK_INTERVAL_MS
    }

    /**
     * 자동 업데이트 체크 성공 시각 및 날짜 기록.
     */
    fun recordAutoUpdateCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        prefs.edit()
            .putLong(KEY_LAST_AUTO_CHECK_TIME, System.currentTimeMillis())
            .putString(KEY_LAST_AUTO_CHECK_DATE, todayStr)
            .apply()
    }

    /**
     * 사용자가 '오늘 하루 보지 않기'를 선택한 버전인지 확인.
     */
    fun isDismissedToday(context: Context, targetVersion: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissedVer = prefs.getString(KEY_DISMISSED_VERSION, null) ?: return false
        val dismissedDate = prefs.getString(KEY_DISMISSED_DATE, null) ?: return false
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        return dismissedVer == targetVersion && dismissedDate == todayStr
    }

    /**
     * 해당 버전에 대해 '오늘 하루 보지 않기' 등록.
     */
    fun dismissForToday(context: Context, targetVersion: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        prefs.edit()
            .putString(KEY_DISMISSED_VERSION, targetVersion)
            .putString(KEY_DISMISSED_DATE, todayStr)
            .apply()
    }

    /**
     * APK 파일 스트리밍 다운로드.
     * @param onProgress (진행률 0.0f ~ 1.0f, 다운로드된 바이트, 전체 바이트) 콜백
     * @return 다운로드된 File 객체
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetVersion: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(downloadUrl).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body ?: return@withContext null
            val totalBytes = body.contentLength()

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "CrewMate_v${targetVersion}.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val progress = if (totalBytes > 0) {
                            (bytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f)
                        } else {
                            -1f // 전체 크기 불명
                        }
                        withContext(Dispatchers.Main) {
                            onProgress(progress, bytesCopied, totalBytes)
                        }
                    }
                    output.flush()
                }
            }
            apkFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 외부 출처 앱 설치 권한(Android 8.0+) 보유 여부 확인.
     */
    fun canInstallPackage(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Android 8.0+ 외부 출처 앱 설치 허용 설정 화면 열기.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * FileProvider 기반 시스템 패키지 인스톨러 호출.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 외부 웹 브라우저로 다운로드 URL 또는 GitHub 페이지 열기.
     */
    fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
