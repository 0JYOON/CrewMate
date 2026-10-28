package com.example.crewmate.data.remote.xcrew

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Provides realistic local Mock data ONLY when the dedicated Google Play Store review account
 * (`000000` / `crewmatereview2026!`) is signed in.
 * Normal employee login and Xcrew XHR pipeline remain 100% untouched.
 */
object MockReviewDataProvider {
    const val REVIEW_EMPLOYEE_NO = "000000"
    const val REVIEW_PASSWORD = "crewmatereview2026!"
    const val REVIEW_USER_NAME = "김철도"
    const val REVIEW_USER_AFFIL = "안산승소"

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
    private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT)

    fun isReviewAccount(employeeNumber: String, password: String): Boolean {
        return employeeNumber.trim().equals(REVIEW_EMPLOYEE_NO, ignoreCase = true) &&
            password == REVIEW_PASSWORD
    }

    fun getWorkSchedules(month: String, parser: XcrewWorkScheduleParser): XcrewWorkScheduleResponse {
        val json = buildMonthlyScheduleJson(month)
        return parser.parse(json)
    }

    fun fetchDiaDetail(pjtDt: String, pdiaNo: String): DiaDetailResponse {
        val json = buildDiaDetailJson(pjtDt, pdiaNo)
        return DiaDetailParser().parse(json)
    }

    fun fetchCrewWorkStatus(pjtDt: String): List<CrewAssignment> {
        return listOf(
            CrewAssignment(
                pdiaNo = "16001",
                repTrn1No = "K4501",
                repTrn2No = "K4512",
                gwkTm = "07:40",
                loiwTm = "16:20",
                crewMembers = listOf("김철도", "이열차")
            ),
            CrewAssignment(
                pdiaNo = "16002",
                repTrn1No = "K4503",
                repTrn2No = "K4514",
                gwkTm = "08:15",
                loiwTm = "17:05",
                crewMembers = listOf("박기관", "최승무")
            ),
            CrewAssignment(
                pdiaNo = "16003",
                repTrn1No = "K4505",
                repTrn2No = "K4516",
                gwkTm = "09:00",
                loiwTm = "18:10",
                crewMembers = listOf("정안전", "강운행")
            ),
            CrewAssignment(
                pdiaNo = "16014",
                repTrn1No = "K4601",
                repTrn2No = "K4610",
                gwkTm = "18:30",
                loiwTm = "08:40",
                crewMembers = listOf("윤정시", "한궤도")
            )
        )
    }

    fun fetchNonOpStatus(year: String): List<NonOpData> {
        return listOf(
            NonOpData(
                startDate = "${year}0312",
                endDate = "${year}0312",
                name = "연차",
                days = 1.0f,
                reason = "개인 용무"
            ),
            NonOpData(
                startDate = "${year}0520",
                endDate = "${year}0521",
                name = "정기교육",
                days = 2.0f,
                reason = "직무 안전 정기교육"
            ),
            NonOpData(
                startDate = "${year}0810",
                endDate = "${year}0811",
                name = "연차",
                days = 2.0f,
                reason = "하계 휴가"
            )
        )
    }

    fun fetchPersonalWorkSchedule(pjtDt: String, empNm: String): List<PersonalWorkScheduleDto> {
        return listOf(
            PersonalWorkScheduleDto(
                pjtDt = pjtDt,
                pdiaNo = "16001",
                repTrn1No = "K4501",
                repTrn2No = "K4512",
                gwkTm = "07:40",
                loiwTm = "16:20",
                hldyYn = "N",
                nonAtulClsfCdNm = null
            )
        )
    }

    fun fetchAttendanceStatus(empNm: String, pjtDt: String): List<AttendanceStatusDto> {
        return listOf(
            AttendanceStatusDto(
                trnNo = "K4501",
                trnSttNm = "정상운행",
                gtwcEpctBlgNm = REVIEW_USER_AFFIL,
                gtwcEpctTm = "07:40",
                gtwcGubunCd = "6",
                pjtDt = pjtDt,
                gtwcTpDvCd = "1",
                flg = "Y",
                tnsmTm = "07:32",
                gtwcTm = "07:32"
            )
        )
    }

    private fun buildMonthlyScheduleJson(month: String): String {
        val yearMonth = runCatching { YearMonth.parse(month, MONTH_FORMAT) }
            .getOrElse { YearMonth.now() }
        val today = LocalDate.now()
        val dataArray = JSONArray()

        val cycleDias = listOf("16001", "16002", "S", "16003", "연차", "16001", "S")
        val cycleTrn1 = listOf("K4501", "K4503", "", "K4505", "", "K4501", "")
        val cycleTrn2 = listOf("K4512", "K4514", "", "K4516", "", "K4512", "")
        val cycleGwk = listOf("07:40", "08:15", "", "09:00", "", "07:40", "")
        val cycleLoiw = listOf("16:20", "17:05", "", "18:10", "", "16:20", "")

        for (day in 1..yearMonth.lengthOfMonth()) {
            val date = yearMonth.atDay(day)
            val dateStr = date.format(DATE_FORMAT)
            val idx = if (date == today) 0 else (day - 1) % cycleDias.size
            val pdiaNo = cycleDias[idx]
            val isRest = pdiaNo == "S"
            val isLeave = pdiaNo == "연차"
            val isWeekend = date.dayOfWeek.value >= 6

            val item = JSONObject().apply {
                put("pjtDt", dateStr)
                put("pdiaNo", if (isLeave) "" else pdiaNo)
                put("repTrn1No", cycleTrn1[idx])
                put("repTrn2No", cycleTrn2[idx])
                put("gwkTm", cycleGwk[idx])
                put("loiwTm", cycleLoiw[idx])
                put("alcSpecDvCd", when {
                    isRest -> "02"
                    isLeave -> "03"
                    else -> "11"
                })
                put("nonAtulClsfCdNm", if (isLeave) "연차" else "")
                put("totWrkTnum", if (!isRest && !isLeave) "460" else "0")
                put("hldyYn", if (isWeekend) "Y" else "N")
                put("wkdyDvCd", date.dayOfWeek.value.toString())
                put("pjtChgPrsFlg", "N")
            }
            dataArray.put(item)
        }

        val vo = JSONObject().apply {
            put("empNm", REVIEW_USER_NAME)
            put("blngStnNm", REVIEW_USER_AFFIL)
            put("pjtYm", month)
        }

        return JSONObject().apply {
            put("data", dataArray)
            put("extrCrewMgVO", vo)
        }.toString()
    }

    private fun buildDiaDetailJson(pjtDt: String, pdiaNo: String): String {
        val normalizedDia = pdiaNo.ifBlank { "16001" }
        val trn1 = when (normalizedDia) {
            "16002" -> "K4503"
            "16003" -> "K4505"
            "16014" -> "K4601"
            else -> "K4501"
        }
        val trn2 = when (normalizedDia) {
            "16002" -> "K4514"
            "16003" -> "K4516"
            "16014" -> "K4610"
            else -> "K4512"
        }

        val segments = JSONArray().apply {
            put(JSONObject().apply {
                put("num", 1)
                put("pdiaEfcStDt", "20260101")
                put("pdiaEfcClsDt", "99991231")
                put("trnNo", trn1)
                put("dptStnNm", "안산")
                put("arvStnNm", "안산")
                put("dptTm", "07:40")
                put("arvTm", "08:05")
                put("pjtHrDvNm", "출고준비")
                put("pjtTnum", 25)
                put("pjtDst", "0.0")
            })
            put(JSONObject().apply {
                put("num", 2)
                put("pdiaEfcStDt", "20260101")
                put("pdiaEfcClsDt", "99991231")
                put("trnNo", trn1)
                put("dptStnNm", "안산")
                put("arvStnNm", "청량리")
                put("dptTm", "08:10")
                put("arvTm", "09:45")
                put("pjtHrDvNm", "운전")
                put("pjtTnum", 95)
                put("pjtDst", "54.2")
            })
            put(JSONObject().apply {
                put("num", 3)
                put("pdiaEfcStDt", "20260101")
                put("pdiaEfcClsDt", "99991231")
                put("trnNo", "")
                put("dptStnNm", "청량리")
                put("arvStnNm", "청량리")
                put("dptTm", "09:45")
                put("arvTm", "11:15")
                put("pjtHrDvNm", "휴게")
                put("pjtTnum", 90)
                put("pjtDst", "0.0")
            })
            put(JSONObject().apply {
                put("num", 4)
                put("pdiaEfcStDt", "20260101")
                put("pdiaEfcClsDt", "99991231")
                put("trnNo", trn2)
                put("dptStnNm", "청량리")
                put("arvStnNm", "오이도")
                put("dptTm", "11:20")
                put("arvTm", "13:05")
                put("pjtHrDvNm", "운전")
                put("pjtTnum", 105)
                put("pjtDst", "61.8")
            })
            put(JSONObject().apply {
                put("num", 5)
                put("pdiaEfcStDt", "20260101")
                put("pdiaEfcClsDt", "99991231")
                put("trnNo", "K4520")
                put("dptStnNm", "오이도")
                put("arvStnNm", "안산")
                put("dptTm", "13:20")
                put("arvTm", "13:35")
                put("pjtHrDvNm", "편승")
                put("pjtTnum", 15)
                put("pjtDst", "8.7")
            })
            put(JSONObject().apply {
                put("num", 6)
                put("pdiaEfcStDt", "20260101")
                put("pdiaEfcClsDt", "99991231")
                put("trnNo", trn2)
                put("dptStnNm", "안산")
                put("arvStnNm", "안산기지")
                put("dptTm", "15:40")
                put("arvTm", "16:20")
                put("pjtHrDvNm", "입환")
                put("pjtTnum", 40)
                put("pjtDst", "3.2")
            })
        }

        val summaries = JSONArray().apply {
            put(JSONObject().apply {
                put("repPjtHrDvCd", "01")
                put("pjtHrDvNm", "운전")
                put("pjtTnum", 200)
                put("pjtDst", 116.0)
                put("totWrkTnum", 370)
                put("totPjtDst", 127.9)
            })
            put(JSONObject().apply {
                put("repPjtHrDvCd", "02")
                put("pjtHrDvNm", "출고준비/입환")
                put("pjtTnum", 65)
                put("pjtDst", 3.2)
                put("totWrkTnum", 370)
                put("totPjtDst", 127.9)
            })
        }

        val efcTrm = JSONObject().apply {
            put("pdiaEfcStDt", "20260101")
            put("pdiaEfcClsDt", "99991231")
            put("pdiaDvCd", "1")
            put("pdiaNo", normalizedDia)
        }

        val runDtDv = JSONArray().apply {
            put(JSONObject().apply {
                put("diaRunDtDvCd00", 0)
                put("diaRunDtDvCd10", 1)
                put("diaRunDtDvCd16", 1)
                put("diaRunDtDvCd17", 1)
            })
        }

        val crewMeta = JSONObject().apply {
            put("pjtDt", pjtDt)
            put("pdiaNo", normalizedDia)
            put("pdiaEfcStDt", "20260101")
            put("pdiaDvCd", "1")
            put("repTrn1No", trn1)
            put("repTrn2No", trn2)
            put("empNm", REVIEW_USER_NAME)
            put("blngStnNm", REVIEW_USER_AFFIL)
        }

        return JSONObject().apply {
            put("data", segments)
            put("dtlData", summaries)
            put("efcTrm", efcTrm)
            put("runDtDv", runDtDv)
            put("extrCrewMgVO", crewMeta)
        }.toString()
    }
}
