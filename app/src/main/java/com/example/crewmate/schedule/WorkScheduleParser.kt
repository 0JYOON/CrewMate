package com.example.crewmate.schedule

import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleItem

class WorkScheduleParser {
    fun parse(item: XcrewWorkScheduleItem): WorkSchedule {
        val rawDia = item.pdiaNo?.trim().orEmpty()
        val contMatch = Regex("^~\\(?(.+?)\\)?$").find(rawDia)
        
        val parsedDia = contMatch?.groupValues?.get(1)?.trim() ?: rawDia.takeIf { it.isNotEmpty() }
        
        // Ensure that diaNo is null if it's missing, rather than empty
        var finalDia = parsedDia?.takeIf { it.isNotEmpty() && it != "null" }
        
        // Use nonAtulClsfCdNm (e.g. "연차", "교육") as diaNo if diaNo is "S" or empty but non-active type exists
        val nonActive = item.nonAtulClsfCdNm?.trim()
        if (nonActive?.isNotEmpty() == true && (finalDia == null || finalDia == "S")) {
            finalDia = nonActive
        }

        return WorkSchedule(
            workDate = item.pjtDt?.trim(),
            diaNo = finalDia,
            trainNo = item.repTrn1No?.trim(),
            trainNo2 = item.repTrn2No?.trim(),
            startTime = item.gwkTm?.trim(),
            endTime = item.loiwTm?.trim(),
            nonActiveType = item.nonAtulClsfCdNm?.trim(),
            isContinuation = contMatch != null,
            isHoliday = (item.hldyYn?.trim()?.uppercase() == "Y") || (item.wkdyDvCd?.trim()?.uppercase() == "S") || (item.hldyAt?.trim()?.uppercase() == "Y")
        )
    }

    fun toDomain(items: List<XcrewWorkScheduleItem>): List<WorkSchedule> {
        return items.map { parse(it) }
    }
}