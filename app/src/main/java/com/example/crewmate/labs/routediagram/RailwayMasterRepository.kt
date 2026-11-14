package com.example.crewmate.labs.routediagram

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 노선/화물역/기지/별칭 마스터 데이터의 3계층 통합 저장소.
 *
 * 1) 앱 내장 `assets/railwayMasterData.json` (완전 오프라인 폴백)
 * 2) GitHub(`0JYOON/CrewMate`) ↔ CrewMate-Alpha(`crewmate-alpha.pages.dev`) 크로스체크 동기화 캐시
 * 3) 사용자 로컬 커스텀 오버라이드 (`SharedPreferences`)
 */
object RailwayMasterRepository {
    private const val TAG = "RailwayMasterRepo"
    private const val ASSET_FILE = "railwayMasterData.json"
    private const val CACHE_FILE = "railway_master_cache.json"
    private const val PREFS_NAME = "crewmate_prefs"
    private const val PREF_CUSTOM_MASTER_JSON = "lab_railway_master_custom_json"
    private const val PREF_LAST_SYNC_META = "lab_railway_master_sync_meta"

    const val GITHUB_MASTER_URL =
        "https://raw.githubusercontent.com/0JYOON/CrewMate/main/railwayMasterData.json"
    const val ALPHA_MASTER_URL =
        "https://crewmate-alpha.pages.dev/railwayMasterData.json"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun loadBaseMaster(context: Context): RailwayMasterData {
        val assetMaster = runCatching {
            context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use {
                RailwayMasterEngine.parseJson(it.readText())
            }
        }.getOrElse {
            Log.w(TAG, "Failed to load asset master: ${it.javaClass.simpleName}")
            RailwayMasterData()
        }

        val cacheFile = File(context.filesDir, CACHE_FILE)
        val cachedMaster = if (cacheFile.exists()) {
            runCatching {
                RailwayMasterEngine.parseJson(cacheFile.readText(Charsets.UTF_8))
            }.getOrNull()
        } else null

        return if (cachedMaster != null && RailwayMasterEngine.isNewerThan(cachedMaster, assetMaster)) {
            cachedMaster
        } else {
            assetMaster
        }
    }

    fun loadCustomMaster(context: Context): RailwayMasterData? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(PREF_CUSTOM_MASTER_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { RailwayMasterEngine.parseJson(raw) }.getOrNull()
    }

    fun saveCustomMaster(context: Context, custom: RailwayMasterData) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_CUSTOM_MASTER_JSON, RailwayMasterEngine.toJsonString(custom)).apply()
    }

    fun addCustomDepot(context: Context, depotName: String, parentStation: String, position: String) {
        val current = loadCustomMaster(context) ?: RailwayMasterData(version = 0)
        val updatedDepots = current.depots.toMutableMap().apply {
            put(depotName.trim(), DepotPlacement(parent = parentStation.trim(), position = position))
        }
        saveCustomMaster(context, current.copy(depots = updatedDepots))
    }

    fun addCustomAlias(context: Context, fromName: String, toName: String) {
        val current = loadCustomMaster(context) ?: RailwayMasterData(version = 0)
        val updatedAliases = current.aliases.toMutableMap().apply {
            put(fromName.trim(), toName.trim())
        }
        saveCustomMaster(context, current.copy(aliases = updatedAliases))
    }

    fun clearCustomMaster(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(PREF_CUSTOM_MASTER_JSON).apply()
    }

    fun loadMergedMaster(context: Context): RailwayMasterData {
        val base = loadBaseMaster(context)
        val custom = loadCustomMaster(context)
        return RailwayMasterEngine.merge(base, custom)
    }

    fun getLastSyncMeta(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LAST_SYNC_META, "") ?: ""
    }

    suspend fun syncFromRemotes(context: Context): MasterSyncResult = withContext(Dispatchers.IO) {
        val currentBase = loadBaseMaster(context)
        val candidates = mutableListOf<Pair<String, RailwayMasterData>>()

        listOf(
            "GitHub (CrewMate)" to GITHUB_MASTER_URL,
            "Web (CrewMate-Alpha)" to ALPHA_MASTER_URL
        ).forEach { (label, url) ->
            runCatching {
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = RailwayMasterEngine.parseJson(body)
                            if (parsed.lines.isNotEmpty()) {
                                candidates.add(label to parsed)
                            }
                        }
                    }
                }
            }.onFailure {
                Log.w(TAG, "Master fetch failed for $label: ${it.javaClass.simpleName}")
            }
        }

        if (candidates.isEmpty()) {
            return@withContext MasterSyncResult(
                success = false,
                activeVersion = currentBase.version,
                activeUpdatedAt = currentBase.updatedAt,
                message = "원격 저장소에 연결할 수 없어 로컬 마스터를 유지합니다."
            )
        }

        val best = candidates.maxWithOrNull { a, b ->
            when {
                a.second.version != b.second.version -> a.second.version.compareTo(b.second.version)
                else -> a.second.updatedAt.compareTo(b.second.updatedAt)
            }
        }!!

        val updated = RailwayMasterEngine.isNewerThan(best.second, currentBase)
        if (updated) {
            File(context.filesDir, CACHE_FILE).writeText(
                RailwayMasterEngine.toJsonString(best.second),
                Charsets.UTF_8
            )
        }

        val syncSummary = "${best.first} · v${best.second.version} (${best.second.updatedAt})"
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LAST_SYNC_META, syncSummary)
            .apply()

        MasterSyncResult(
            success = true,
            updated = updated,
            activeVersion = maxOf(currentBase.version, best.second.version),
            activeUpdatedAt = if (updated) best.second.updatedAt else currentBase.updatedAt,
            selectedSource = best.first,
            message = if (updated) {
                "최신 노선 마스터(v${best.second.version})로 업데이트되었습니다. [${best.first}]"
            } else {
                "이미 최신 노선 마스터(v${currentBase.version})입니다. [크로스체크 완료]"
            }
        )
    }
}
