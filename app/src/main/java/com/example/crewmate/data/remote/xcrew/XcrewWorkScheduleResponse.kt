package com.example.crewmate.data.remote.xcrew

import org.json.JSONObject

/** DTO fields retain the names used by Xcrew's JSON response. */
data class XcrewWorkScheduleResponse(
    val data: List<XcrewWorkScheduleItem>,
    val extrCrewMgVO: JSONObject?,
    val pjtChgPrsFlg: String? = null,
)

data class XcrewWorkScheduleItem(
    val pjtDt: String? = null,
    val pdiaNo: String? = null,
    val repTrn1No: String? = null,
    val gwkTm: String? = null,
    val loiwTm: String? = null,
    val alcSpecDvCd: String? = null,
    val nonAtulClsfCdNm: String? = null,
    val totWrkTnum: String? = null,
    val repTrn2No: String? = null,
    val hldyYn: String? = null,
    val wkdyDvCd: String? = null,
    val hldyAt: String? = null,
    val pjtChgPrsFlg: String? = null // ← 이 줄을 맨 끝에 추가!
)