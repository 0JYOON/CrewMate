package com.example.crewmate.schedule

import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.database.DiaDetailDao
import com.example.crewmate.database.DiaDetailEntity

class DiaDetailRepository(
    private val diaDetailDataSource: DiaDetailDataSource,
    private val diaDetailDao: DiaDetailDao,
    private val isOfflineModeProvider: () -> Boolean = { false }
) {
    private val parser = DiaDetailParser()

    suspend fun fetchDiaDetail(pjtDt: String, pdiaNo: String): DiaDetailResponse {
        val key = "$pjtDt|$pdiaNo"
        if (isOfflineModeProvider()) {
            val cached = diaDetailDao.getDiaDetail(key)
            if (cached != null && cached.detailJson.isNotBlank()) {
                return runCatching { parser.parse(cached.detailJson) }.getOrElse { emptyFallbackDetail() }
            }
            return emptyFallbackDetail()
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
            val cached = diaDetailDao.getDiaDetail(key)
            if (cached != null && cached.detailJson.isNotBlank()) {
                runCatching { parser.parse(cached.detailJson) }.getOrElse { emptyFallbackDetail() }
            } else {
                emptyFallbackDetail()
            }
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
