package com.example.crewmate.labs.handovercrew

/**
 * 동일 열차번호의 1개 승무 릴레이 구간 및 담당 승무원 정보.
 */
data class HandoverRelayItem(
    val pjtDt: String,
    val pdiaNo: String,
    val dptStnNm: String,
    val dptTm: String,
    val arvStnNm: String,
    val arvTm: String,
    val crewMembers: List<String>,
    val isCurrentDia: Boolean
)

/**
 * 교대승무원 조회 결과 리포트.
 */
data class HandoverCrewReport(
    val trainNumber: String,
    val targetDate: String,
    val currentDiaNo: String,
    val currentDptStnNm: String,
    val currentDptTm: String,
    val currentArvStnNm: String,
    val currentArvTm: String,
    val previousCrew: HandoverRelayItem?,
    val nextCrew: HandoverRelayItem?,
    val allRelays: List<HandoverRelayItem>,
    val fromOfflineCache: Boolean
)
