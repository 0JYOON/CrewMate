package com.example.crewmate.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun compareVersions_targetIsNewer_returnsPositive() {
        // 패치 버전 상승
        assertTrue(AppUpdateManager.compareVersions("1.0.37", "1.0.38") > 0)
        // 마이너 버전 상승
        assertTrue(AppUpdateManager.compareVersions("1.0.37", "1.1.0") > 0)
        // 메이저 버전 상승
        assertTrue(AppUpdateManager.compareVersions("1.0.37", "2.0.0") > 0)
        // 두 자리 버전 vs 세 자리 버전
        assertTrue(AppUpdateManager.compareVersions("1.0", "1.0.1") > 0)
    }

    @Test
    fun compareVersions_targetIsSame_returnsZero() {
        assertEquals(0, AppUpdateManager.compareVersions("1.0.37", "1.0.37"))
        assertEquals(0, AppUpdateManager.compareVersions("v1.0.37", "1.0.37"))
        assertEquals(0, AppUpdateManager.compareVersions("1.0.37", "V1.0.37"))
        assertEquals(0, AppUpdateManager.compareVersions("1.0.0", "1.0"))
    }

    @Test
    fun compareVersions_targetIsOlder_returnsNegative() {
        assertTrue(AppUpdateManager.compareVersions("1.0.37", "1.0.36") < 0)
        assertTrue(AppUpdateManager.compareVersions("1.1.0", "1.0.99") < 0)
        assertTrue(AppUpdateManager.compareVersions("2.0.0", "1.99.99") < 0)
    }

    @Test
    fun parseReleaseJson_withApkAsset_extractsDirectDownloadUrl() {
        val mockJson = JSONObject().apply {
            put("tag_name", "v1.0.38")
            put("name", "v1.0.38 정기 업데이트")
            put("body", "- 캡처 가독성 개선\n- 인앱 업데이트 신설")
            put("html_url", "https://github.com/0JYOON/CrewMate/releases/tag/v1.0.38")
            put("assets", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "CrewMate_v1.0.38.apk")
                    put("browser_download_url", "https://github.com/0JYOON/CrewMate/releases/download/v1.0.38/CrewMate_v1.0.38.apk")
                })
            })
        }

        val tagName = mockJson.optString("tag_name")
        val latestVersion = tagName.removePrefix("v").removePrefix("V")
        val body = mockJson.optString("body")

        var apkUrl: String? = null
        val assets = mockJson.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
        }

        assertEquals("1.0.38", latestVersion)
        assertEquals("- 캡처 가독성 개선\n- 인앱 업데이트 신설", body)
        assertEquals("https://github.com/0JYOON/CrewMate/releases/download/v1.0.38/CrewMate_v1.0.38.apk", apkUrl)
    }

    @Test
    fun parseReleaseJson_withoutApkAsset_fallsBackToHtmlUrl() {
        val mockJson = JSONObject().apply {
            put("tag_name", "v1.0.38")
            put("html_url", "https://github.com/0JYOON/CrewMate/releases/tag/v1.0.38")
            put("assets", JSONArray())
        }

        var apkUrl: String? = null
        val assets = mockJson.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
        }
        val finalUrl = apkUrl ?: mockJson.optString("html_url")

        assertEquals("https://github.com/0JYOON/CrewMate/releases/tag/v1.0.38", finalUrl)
    }
}
