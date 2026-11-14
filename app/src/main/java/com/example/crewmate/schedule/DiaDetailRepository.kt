package com.example.crewmate.schedule

import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.database.DiaCensusDao
import com.example.crewmate.database.DiaDetailDao
import com.example.crewmate.database.DiaDetailEntity

class DiaDetailRepository(
    private val diaDetailDataSource: DiaDetailDataSource,
    private val diaDetailDao: DiaDetailDao,
    private val diaCensusDao: DiaCensusDao? = null,
    private val isOfflineModeProvider: () -> Boolean = { false }
) : DiaDetailDataSource {
    constructor(
        diaDetailDataSource: DiaDetailDataSource,
        diaDetailDao: DiaDetailDao,
        isOfflineModeProvider: () -> Boolean
    ) : this(diaDetailDataSource, diaDetailDao, null, isOfflineModeProvider)

    private val parser = DiaDetailParser()

    suspend fun getCachedDiaDetailOnly(pjtDt: String, pdiaNo: String): DiaDetailResponse {
        val cleanDia = pdiaNo.trim().removePrefix("~")
        val key = "$pjtDt|$pdiaNo"
        val altKey = "$pjtDt|$cleanDia"

        val cached = diaDetailDao.getDiaDetail(key)
            ?: (if (key != altKey) diaDetailDao.getDiaDetail(altKey) else null)
            ?: diaDetailDao.getDiaDetailsForDate(pjtDt).find {
                it.pdiaNo.trim() == pdiaNo.trim() || it.pdiaNo.trim() == cleanDia
            }
            ?: diaDetailDao.getLatestDiaDetailByPdiaNo(cleanDia)
            ?: diaDetailDao.getLatestDiaDetailByPdiaNo(pdiaNo.trim())

        if (cached != null && cached.detailJson.isNotBlank()) {
            val parsed = runCatching { parser.parse(cached.detailJson) }.getOrNull()
            if (parsed != null && parsed.segments.isNotEmpty()) {
                return parsed
            }
        }

        // Fallback to dia_census table (collected during 기초자료 조사)
        val census = diaCensusDao?.getCensusByPdiaNo(cleanDia)
            ?: diaCensusDao?.getCensusByPdiaNo(pdiaNo.trim())
        if (census != null && census.detailJson.isNotBlank()) {
            val parsedCensus = runCatching { parser.parse(census.detailJson) }.getOrNull()
            if (parsedCensus != null && parsedCensus.segments.isNotEmpty()) {
                return parsedCensus
            }
        }

        return emptyFallbackDetail()
    }

    override suspend fun fetchDiaDetail(pjtDt: String, pdiaNo: String): DiaDetailResponse {
        val cleanDia = pdiaNo.trim().removePrefix("~")
        val key = "$pjtDt|$pdiaNo"
        val altKey = "$pjtDt|$cleanDia"

        val cached = diaDetailDao.getDiaDetail(key)
            ?: (if (key != altKey) diaDetailDao.getDiaDetail(altKey) else null)
            ?: diaDetailDao.getDiaDetailsForDate(pjtDt).find { 
                it.pdiaNo.trim() == pdiaNo.trim() || it.pdiaNo.trim() == cleanDia 
            }

        // 1. 이미 로컬 Room DB에 유효한 다이아 상세 구간이 캐싱되어 있으면 온라인/오프라인 불문 즉시 반환 (Cache-First)
        if (cached != null && cached.detailJson.isNotBlank()) {
            val parsedCached = runCatching { parser.parse(cached.detailJson) }.getOrNull()
            if (parsedCached != null && parsedCached.segments.isNotEmpty()) {
                return parsedCached
            }
        }

        if (isOfflineModeProvider()) {
            return getCachedDiaDetailOnly(pjtDt, pdiaNo)
        }

        return try {
            val response = diaDetailDataSource.fetchDiaDetail(pjtDt, pdiaNo)
            val jsonString = response.rawRootJson?.toString().orEmpty()
            if (jsonString.isNotBlank()) {
                diaDetailDao.insertDiaDetail(
                    DiaDetailEntity(
                        diaKey = key,
                        pjtDt = pjtDt,
                        pdiaNo = pdiaNo,
                        detailJson = jsonString
                    )
                )
            }
            response
        } catch (e: Exception) {
            getCachedDiaDetailOnly(pjtDt, pdiaNo)
        }
    }

    private fun emptyFallbackDetail() = DiaDetailResponse(
        effectiveTerm = null,
        runDtDv = null,
        segments = emptyList(),
        summaries = emptyList(),
        crewMeta = null
    )
}
