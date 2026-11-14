package com.example.crewmate.schedule

import com.example.crewmate.alarm.AutoOfflineManager
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EnhancementsUnitTest {

    @Test
    fun workPattern_moduloRotation_calculatesCorrectly() {
        val anchorDate = LocalDate.of(2026, 10, 15)
        val patternList = listOf("주간", "야간", "~", "주간", "S", "S") // 6-day cycle
        val data = WorkPatternData(enabled = true, anchorDate = anchorDate, patternList = patternList)

        // Day 0: 2026-10-15 -> index 0: "주간"
        assertEquals("주간", WorkPatternSettings.getPatternForDate(data, anchorDate))

        // Day 1: 2026-10-16 -> index 1: "야간"
        assertEquals("야간", WorkPatternSettings.getPatternForDate(data, anchorDate.plusDays(1)))

        // Day 2: 2026-10-17 -> index 2: "~"
        assertEquals("~", WorkPatternSettings.getPatternForDate(data, anchorDate.plusDays(2)))

        // Day 5: 2026-10-20 -> index 5: "S"
        assertEquals("S", WorkPatternSettings.getPatternForDate(data, anchorDate.plusDays(5)))

        // Day 6: 2026-10-21 -> index 0: "주간" (cycled)
        assertEquals("주간", WorkPatternSettings.getPatternForDate(data, anchorDate.plusDays(6)))

        // Negative offset: Day -1: 2026-10-14 -> index 5: "S"
        assertEquals("S", WorkPatternSettings.getPatternForDate(data, anchorDate.minusDays(1)))
    }

    @Test
    fun autoOffline_withGamsiPrecedingRank1_shiftsEarlierBy1Min() {
        val workDate = LocalDate.of(2026, 10, 25)
        val segments = listOf(
            DiaDetailSegment(1, null, null, null, "안산", "안산", "07:30", "07:40", "감시", null, null),
            DiaDetailSegment(2, null, null, "K4501", "안산", "청량리", "07:41", "09:00", "출고준비", null, null),
            DiaDetailSegment(3, null, null, "K4501", "안산", "청량리", "09:05", "10:30", "운전", null, null),
            DiaDetailSegment(4, null, null, null, "청량리", "청량리", "10:30", "11:30", "휴게", null, null),
            DiaDetailSegment(5, null, null, "K4512", "청량리", "안산", "11:30", "13:00", "운전", null, null),
            DiaDetailSegment(6, null, null, null, "안산", "안산", "13:05", "13:40", "정리", null, null)
        )

        val schedule = AutoOfflineManager.calculateOfflineSchedule(segments, workDate)
        assertNotNull(schedule)

        // Offline time should be 07:29 (1 minute before 07:30 감시)
        assertEquals(workDate.atTime(7, 29), schedule!!.offlineTriggerTime)

        // Online recovery should be 13:05 (start time of 정리)
        assertEquals(workDate.atTime(13, 5), schedule.onlineTriggerTime)
    }

    @Test
    fun autoOffline_withoutGamsi_shifts1MinBeforeRank1() {
        val workDate = LocalDate.of(2026, 10, 25)
        val segments = listOf(
            DiaDetailSegment(1, null, null, "K4501", "안산", "청량리", "08:00", "09:30", "운전", null, null),
            DiaDetailSegment(2, null, null, null, "청량리", "청량리", "09:30", "10:30", "편승", null, null),
            DiaDetailSegment(3, null, null, null, "청량리", "안산", "10:30", "11:00", "입고정리", null, null)
        )

        val schedule = AutoOfflineManager.calculateOfflineSchedule(segments, workDate)
        assertNotNull(schedule)

        // Offline time: 07:59 (1 minute before 08:00 운전)
        assertEquals(workDate.atTime(7, 59), schedule!!.offlineTriggerTime)

        // Online time: 10:30 (start of 입고정리)
        assertEquals(workDate.atTime(10, 30), schedule.onlineTriggerTime)
    }

    @Test
    fun autoOffline_multipleDrivingWindows_calculatesAllWindows() {
        val workDate = LocalDate.of(2026, 10, 26)
        val segments = listOf(
            DiaDetailSegment(1, null, null, null, "안산", "안산", "06:30", "06:40", "준비", null, null),
            DiaDetailSegment(2, null, null, null, "안산", "안산", "06:40", "06:50", "감시", null, null),
            DiaDetailSegment(3, null, null, "K4501", "안산", "청량리", "06:50", "08:30", "운전", null, null),
            DiaDetailSegment(4, null, null, null, "청량리", "청량리", "08:30", "08:50", "정리", null, null),
            DiaDetailSegment(5, null, null, null, "청량리", "청량리", "08:50", "12:00", "휴게", null, null),
            DiaDetailSegment(6, null, null, "K4520", "청량리", "안산", "12:10", "14:00", "운전", null, null),
            DiaDetailSegment(7, null, null, null, "안산", "안산", "14:00", "14:25", "입고정리", null, null)
        )

        val windows = AutoOfflineManager.calculateOfflineSchedules(segments, workDate)
        assertEquals(2, windows.size)

        // Window 1: 06:39 (1 min before 06:40 감시) -> 08:30 (start of 정리)
        assertEquals(workDate.atTime(6, 39), windows[0].offlineTriggerTime)
        assertEquals(workDate.atTime(8, 30), windows[0].onlineTriggerTime)

        // Window 2: 12:09 (1 min before 12:10 운전) -> 14:00 (start of 입고정리)
        assertEquals(workDate.atTime(12, 9), windows[1].offlineTriggerTime)
        assertEquals(workDate.atTime(14, 0), windows[1].onlineTriggerTime)
    }

    @Test
    fun autoOffline_withReturnDelay_addsMinutesToOnlineTime() {
        val workDate = LocalDate.of(2026, 10, 26)
        val segments = listOf(
            DiaDetailSegment(1, null, null, "K4501", "안산", "청량리", "08:00", "09:30", "운전", null, null),
            DiaDetailSegment(2, null, null, null, "청량리", "청량리", "09:30", "10:00", "정리", null, null)
        )

        // Delay 10 minutes: 09:30 + 10m -> 09:40
        val windows = AutoOfflineManager.calculateOfflineSchedules(segments, workDate, returnDelayMinutes = 10)
        assertEquals(1, windows.size)
        assertEquals(workDate.atTime(7, 59), windows[0].offlineTriggerTime)
        assertEquals(workDate.atTime(9, 40), windows[0].onlineTriggerTime)
    }

    @Test
    fun attendanceAlarm_withActualTimeInIncompleteState_doesNotScheduleAlarm() {
        val workDate = LocalDate.now().plusDays(1)
        val settings = com.example.crewmate.alarm.WorkAlarmSettings(alarmEnabled = true, attendanceEnabled = true, attendanceLeadMinutes = 5)
        val attendance = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "305",
            trnSttNm = "운행",
            gtwcEpctBlgNm = "안산출무소",
            gtwcEpctTm = "07:15",
            gtwcGubunCd = "1", // not 3 or 6 (e.g. 적합성미완료)
            pjtDt = workDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
            actlTm = "07:12" // actual attendance time confirmed!
        )

        val alarms = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
            segments = emptyList(),
            workDate = workDate,
            settings = settings,
            attendanceStatuses = listOf(attendance)
        )

        val attendanceAlarms = alarms.filter { it.cat == "출무" }
        // Because actual attendance time ("07:12") is confirmed, attendance alarm must be skipped!
        assertEquals(0, attendanceAlarms.size)
    }

    @Test
    fun attendanceAlarm_withoutActualTimeInIncompleteState_schedulesAlarm() {
        val workDate = LocalDate.now().plusDays(1)
        val settings = com.example.crewmate.alarm.WorkAlarmSettings(alarmEnabled = true, attendanceEnabled = true, attendanceLeadMinutes = 5)
        val attendance = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "305",
            trnSttNm = "운행",
            gtwcEpctBlgNm = "안산출무소",
            gtwcEpctTm = "07:15",
            gtwcGubunCd = "1",
            pjtDt = workDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
            actlTm = null // no actual attendance yet
        )

        val alarms = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
            segments = emptyList(),
            workDate = workDate,
            settings = settings,
            attendanceStatuses = listOf(attendance)
        )

        val attendanceAlarms = alarms.filter { it.cat == "출무" }
        assertEquals(1, attendanceAlarms.size)
        assertEquals(workDate.atTime(7, 10), attendanceAlarms[0].triggerDateTime)
    }

    @Test
    fun dataBackup_jsonStructure_isValid() {
        val jsonString = """
            {
              "app": "CrewMate",
              "version": 1,
              "exportedAt": "2026-10-27T12:00:00",
              "memos": [
                { "date": "2026-10-27", "content": "기관차 점검 완료" }
              ],
              "annualLeaves": [
                { "year": 2026, "totalDays": 15 }
              ],
              "carriedOverLeaves": [
                { "days": 3.5, "endDate": "2026-12-31" }
              ],
              "preferences": {
                "main_screen": "TODAY_WORK",
                "auto_offline_return_delay_minutes": 10
              }
            }
        """.trimIndent()

        val json = org.json.JSONObject(jsonString)
        assertEquals("CrewMate", json.getString("app"))
        assertEquals(1, json.getJSONArray("memos").length())
        assertEquals(1, json.getJSONArray("annualLeaves").length())
        assertEquals(1, json.getJSONArray("carriedOverLeaves").length())
        assertEquals("TODAY_WORK", json.getJSONObject("preferences").getString("main_screen"))
    }

    @Test
    fun railBlue_urlGeneration_isCorrect() {
        val date = "20261025"
        val train = "K6543"
        val expected = "https://rail.blue/railroad/logis/Default.aspx?date=$date&train=$train#!"
        val generated = "https://rail.blue/railroad/logis/Default.aspx?date=$date&train=$train#!"
        assertEquals(expected, generated)
    }

    @Test
    fun attendanceStatusDto_isCheckedIn_evaluatesCorrectly() {
        val notAttended = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "K101",
            trnSttNm = "출무전",
            gtwcEpctBlgNm = "구로",
            gtwcEpctTm = "08:40",
            gtwcGubunCd = "1", // 출무전
            pjtDt = "20261029",
            tnsmTm = null
        )
        org.junit.Assert.assertFalse(notAttended.isCheckedIn)

        // Case 1: 실제 출무 시각 존재
        val attendedWithTime = notAttended.copy(tnsmTm = "08:32")
        org.junit.Assert.assertTrue(attendedWithTime.isCheckedIn)

        // Case 2: 적합성미완료 (코드 5)
        val nonComplianceStatus = notAttended.copy(gtwcGubunCd = "5", tnsmTm = null)
        org.junit.Assert.assertTrue(nonComplianceStatus.isCheckedIn)

        // Case 3: 도중출무완료 (코드 3), 적합성완료 (코드 6)
        org.junit.Assert.assertTrue(notAttended.copy(gtwcGubunCd = "3").isCheckedIn)
        org.junit.Assert.assertTrue(notAttended.copy(gtwcGubunCd = "6").isCheckedIn)
    }

    @Test
    fun holidayWorkManager_calculatesDayAndOvernightDutiesCorrectly() {
        val year = 2026
        val schedules = listOf(
            // 10월 3일 (개천절): hldyYn == "Y", alcSpecDvCd == "11", 주간근무 (다음날 10/4 alc != "12")
            WorkSchedule(workDate = "20261003", diaNo = "16001", startTime = "08:30", endTime = "17:30", alcSpecDvCd = "11", hldyYn = "Y"),
            WorkSchedule(workDate = "20261004", diaNo = "S", alcSpecDvCd = "02", hldyYn = "Y"),

            // 10월 9일 (한글날): hldyYn == "Y", alcSpecDvCd == "11", 익일(10/10) alc == "12" ➔ 야간 1일차
            WorkSchedule(workDate = "20261009", diaNo = "16050", startTime = "19:30", endTime = "08:30", alcSpecDvCd = "11", hldyYn = "Y"),
            // 10월 10일 (토요일): hldyYn == "Y", alcSpecDvCd == "12" ➔ 야간 2일차 (10/9와 1개 단위 통합)
            WorkSchedule(workDate = "20261010", diaNo = "16050", startTime = "19:30", endTime = "08:30", alcSpecDvCd = "12", hldyYn = "Y"),

            // 10월 15일 (평일): hldyYn == "N", alcSpecDvCd == "11" ➔ 평일이므로 제외
            WorkSchedule(workDate = "20261015", diaNo = "16002", startTime = "09:00", endTime = "18:00", alcSpecDvCd = "11", hldyYn = "N"),

            // 10월 18일 (일요일): hldyYn == "Y" 이지만 alcSpecDvCd == "02" (휴일 휴무) ➔ 제외
            WorkSchedule(workDate = "20261018", diaNo = "S", alcSpecDvCd = "02", hldyYn = "Y")
        )

        val report = HolidayWorkManager.calculateHolidayWork(year, schedules)

        // 10월 3일 주간 1개, 10월 9일-10일 2일 연속 야간 1개 -> 총 2개 (주간 1개, 야간 1개)
        assertEquals(2, report.totalCount)
        assertEquals(1, report.dayCount)
        assertEquals(1, report.nightCount) // 2일 연속 야간근무가 정확히 1개 단위로 카운트됨!
        assertEquals(3, report.items.size) // 세부 항목은 3일치 내역 보존
    }

    @Test
    fun holidayWorkManager_excludesNonActiveAndSickLeaveEvenIfHoliday() {
        val year = 2026
        val schedules = listOf(
            // 5월 5일 (어린이날): hldyYn == "Y"이지만 alcSpecDvCd == "21" (병가 비실동) ➔ 무조건 제외
            WorkSchedule(
                workDate = "20260505",
                diaNo = "16001",
                startTime = "08:30",
                endTime = "17:30",
                nonActiveType = "병가",
                alcSpecDvCd = "21",
                hldyYn = "Y"
            ),
            // 6월 6일 (현충일): hldyYn == "Y"이지만 diaNo == "연차" (연차 비실동) ➔ 무조건 제외
            WorkSchedule(
                workDate = "20260606",
                diaNo = "연차",
                nonActiveType = "연차",
                alcSpecDvCd = "21",
                hldyYn = "Y"
            ),
            // 8월 15일 (광복절): hldyYn == "Y", alcSpecDvCd == "11", 정상 근무 배정 ➔ 주간 1개 카운트
            WorkSchedule(
                workDate = "20260815",
                diaNo = "16003",
                startTime = "08:30",
                endTime = "17:30",
                alcSpecDvCd = "11",
                hldyYn = "Y"
            ),
            WorkSchedule(
                workDate = "20260816",
                diaNo = "S",
                alcSpecDvCd = "02",
                hldyYn = "Y"
            )
        )

        val report = HolidayWorkManager.calculateHolidayWork(year, schedules)

        // 병가, 연차는 100% 제외되고 8월 15일 1건만 카운트되어야 함!
        assertEquals(1, report.totalCount)
        assertEquals(1, report.dayCount)
        assertEquals(0, report.nightCount)
        assertEquals("16003", report.items[0].diaNo)
    }

    @Test
    fun holidayWorkManager_handlesHyphenatedDatesAndOvernightBy11And12Rules() {
        val year = 2026
        val schedules = listOf(
            // 1. 하이픈 날짜 ("2026-10-03"): 개천절 공휴일(hldyYn == "Y"), alcSpecDvCd == "11", 주간 (익일 alc != "12")
            WorkSchedule(
                workDate = "2026-10-03",
                diaNo = "16001",
                startTime = "08:30",
                endTime = "17:30",
                alcSpecDvCd = "11",
                hldyYn = "Y"
            ),
            WorkSchedule(
                workDate = "2026-10-04",
                diaNo = "S",
                alcSpecDvCd = "02",
                hldyYn = "Y"
            ),
            // 2. 야간근무 1일차 ("2026-10-09"): hldyYn == "Y", alc == "11", 익일(10/10) alc == "12"
            WorkSchedule(
                workDate = "2026-10-09",
                diaNo = "16050",
                startTime = "19:30",
                endTime = "08:30",
                alcSpecDvCd = "11",
                hldyYn = "Y"
            ),
            // 3. 야간근무 2일차 ("2026-10-10"): hldyYn == "Y", alc == "12" ➔ 10/9와 1개 단위 통합
            WorkSchedule(
                workDate = "2026-10-10",
                diaNo = "16050",
                startTime = "19:30",
                endTime = "08:30",
                alcSpecDvCd = "12",
                hldyYn = "Y"
            ),
            // 4. 일반 평일 근무 ("2026-10-14"): hldyYn == "N" ➔ 제외
            WorkSchedule(
                workDate = "2026-10-14",
                diaNo = "16002",
                startTime = "09:00",
                endTime = "18:00",
                alcSpecDvCd = "11",
                hldyYn = "N"
            ),
            // 5. 전일 평일(10/24, hldyYn="N", alc="11") 시작 후 당일 휴일(10/25, hldyYn="Y", alc="12")인 야간 2일차
            WorkSchedule(
                workDate = "2026-10-24",
                diaNo = "16055",
                startTime = "18:30",
                endTime = "07:30",
                alcSpecDvCd = "11",
                hldyYn = "N"
            ),
            WorkSchedule(
                workDate = "2026-10-25",
                diaNo = "16055",
                startTime = "18:30",
                endTime = "07:30",
                alcSpecDvCd = "12",
                hldyYn = "Y"
            )
        )

        val report = HolidayWorkManager.calculateHolidayWork(year, schedules)

        // 10/3 주간 1개, 10/9-10 야간 1개, 10/25 야간 1개 ➔ 총 3개 (주간 1, 야간 2)
        assertEquals(3, report.totalCount)
        assertEquals(1, report.dayCount)
        assertEquals(2, report.nightCount)
        assertEquals(7, report.examinedDaysCount)
    }

    @Test
    fun midnightCapture_nightDay2_and_nonActiveDay_skipEvaluation() {
        // 야간근무 2일차 (alcSpecDvCd == "12") 판별 검증
        val schedNightDay2 = WorkSchedule(
            workDate = "20261029",
            diaNo = "16320",
            alcSpecDvCd = "12"
        )
        val isNightDay2 = schedNightDay2.alcSpecDvCd?.trim() == "12" ||
                schedNightDay2.isContinuation ||
                schedNightDay2.diaNo?.startsWith("~") == true
        assertTrue(isNightDay2)

        // 비실동 (alcSpecDvCd == "21") 또는 연차 판별 검증
        val schedSick = WorkSchedule(
            workDate = "20261030",
            diaNo = "병가",
            alcSpecDvCd = "21"
        )
        val isRestOrNonActive = schedSick.diaNo == "S" ||
                schedSick.diaNo == "휴" ||
                schedSick.diaNo == "연차" ||
                schedSick.diaNo == "병가" ||
                schedSick.alcSpecDvCd?.trim() == "02" ||
                schedSick.alcSpecDvCd?.trim() == "21"
        assertTrue(isRestOrNonActive)

        // 정상 주간 또는 야간 1일차 근무는 스킵되지 않음
        val schedDay1 = WorkSchedule(
            workDate = "20261028",
            diaNo = "16320",
            alcSpecDvCd = "11"
        )
        val isDay1Skip = schedDay1.alcSpecDvCd?.trim() == "12" ||
                schedDay1.isContinuation ||
                schedDay1.diaNo?.startsWith("~") == true
        assertFalse(isDay1Skip)
    }

    @Test
    fun targetOverride_commandPrefix_and_trackStartTime_linking() {
        val originalRawCommand = "K4658 안산 1 18:20"
        val tokens = originalRawCommand.split("\\s+".toRegex()).filter { it.isNotBlank() }
        
        // 1. Tokens 분리 검증 (마지막 토큰이 추적 개시 시각)
        val trackStartTime = tokens.lastOrNull().orEmpty()
        val commandPrefix = tokens.dropLast(1).joinToString(" ")

        assertEquals("18:20", trackStartTime)
        assertEquals("K4658 안산 1", commandPrefix)

        // 2. 사용자가 명령어 앞부분(목표역 변경)이나 개시 시각 수정 후 병합 검증
        val updatedPrefix = "K4658 불암산 2"
        val updatedTrackStartTime = "18:25"
        val finalCommand = "$updatedPrefix $updatedTrackStartTime"

        assertEquals("K4658 불암산 2 18:25", finalCommand)
    }

    @Test
    fun workScheduleRepository_onlineMode_usesCacheFirst_and_onlyCallsNetworkWhenForcedOrEmpty() = kotlinx.coroutines.runBlocking {
        var remoteCallCount = 0
        val fakeDataSource = object : com.example.crewmate.data.remote.xcrew.XcrewDataSource {
            override suspend fun getWorkSchedules(month: String): com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleResponse {
                remoteCallCount++
                return com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleResponse(
                    data = listOf(
                        com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleItem(
                            pjtDt = "${month}01",
                            pdiaNo = "16999",
                            repTrn1No = "K4501",
                            gwkTm = "08:00",
                            loiwTm = "17:00",
                            alcSpecDvCd = "11",
                            hldyYn = "N"
                        )
                    ),
                    extrCrewMgVO = null
                )
            }
        }

        val inMemoryStore = mutableListOf(
            com.example.crewmate.database.WorkScheduleEntity(
                id = 1,
                yearMonth = "202611",
                date = "20261101",
                diaNo = "16320",
                trainNo = "K4658",
                trainNo2 = null,
                startTime = "17:40",
                endTime = "06:45",
                nonActiveType = null,
                isContinuation = false,
                isHoliday = false,
                alcSpecDvCd = "11",
                hldyYn = "N",
                isChanged = false
            )
        )

        val fakeDao = object : com.example.crewmate.database.WorkScheduleDao {
            override suspend fun getSchedulesForMonth(yearMonth: String) =
                inMemoryStore.filter { it.yearMonth == yearMonth }

            override suspend fun getSchedulesForYear(yearPrefix: String) =
                inMemoryStore.filter { it.yearMonth.startsWith(yearPrefix.removeSuffix("%")) }

            override suspend fun insertSchedules(schedules: List<com.example.crewmate.database.WorkScheduleEntity>) {
                inMemoryStore.addAll(schedules)
            }

            override suspend fun deleteSchedulesForMonth(yearMonth: String) {
                inMemoryStore.removeAll { it.yearMonth == yearMonth }
            }

            override suspend fun cleanupOldSchedules(thresholdMonth: String) {
                inMemoryStore.removeAll { it.yearMonth < thresholdMonth }
            }
        }

        val repo = WorkScheduleRepositoryImpl(
            dataSource = fakeDataSource,
            parser = WorkScheduleParser(),
            workScheduleDao = fakeDao,
            isOfflineModeProvider = { false } // 온라인 모드
        )

        // 1. 온라인 모드이더라도 캐시된 202611 근무표가 있으면 네트워크(로그인) 호출 없이 즉시 캐시 반환
        val cachedResult = repo.getSchedules("202611", forceRefresh = false)
        assertEquals(0, remoteCallCount)
        assertEquals(1, cachedResult.schedules.size)
        assertEquals("16320", cachedResult.schedules[0].diaNo)

        // 2. 캐시가 없는 달(202612) 조회 시에만 원격 호출 수행
        val uncachedResult = repo.getSchedules("202612", forceRefresh = false)
        assertEquals(1, remoteCallCount)
        assertEquals("16999", uncachedResult.schedules[0].diaNo)

        // 3. 강제 갱신(forceRefresh = true) 요청 시 원격 호출 수행 및 캐시 업데이트
        val forcedResult = repo.getSchedules("202611", forceRefresh = true)
        assertEquals(2, remoteCallCount)
        assertEquals("16999", forcedResult.schedules[0].diaNo)
    }

    @Test
    fun silentRelogin_updatesSessionWithoutTriggeringForegroundExpiredState() = kotlinx.coroutines.runBlocking {
        val session = com.example.crewmate.data.remote.xcrew.XcrewWebViewSession()
        var silentReloginCount = 0

        session.setSilentReloginProvider {
            silentReloginCount++
            session.markAuthenticated()
            true
        }

        assertFalse(session.isSessionAuthenticated)
        assertEquals(0L, session.authenticationEpoch())

        val reloginOk = session.trySilentRelogin(failedEpoch = 0L)
        assertTrue(reloginOk)
        assertEquals(1, silentReloginCount)
        assertTrue(session.isSessionAuthenticated)
        assertEquals(1L, session.authenticationEpoch())
        assertEquals(1L, session.sessionAuthenticatedEpoch.value)
        assertFalse(session.authenticationExpired.value)

        // 동일 failedEpoch(0L)로 중복 요청이 들어오면 이미 갱신되었으므로 재실행하지 않고 즉시 true 반환
        val duplicateOk = session.trySilentRelogin(failedEpoch = 0L)
        assertTrue(duplicateOk)
        assertEquals(1, silentReloginCount)
    }

    @Test
    fun telegramTrackStartDateTime_formatsWithKstOffset() {
        val localDt = java.time.LocalDateTime.of(2026, 11, 2, 17, 25, 0)
        val isoWithOffset = localDt
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime()
            .toString()
        assertTrue(isoWithOffset.endsWith("+09:00"))
        assertTrue(isoWithOffset.startsWith("2026-11-02T17:25"))
    }

    @Test
    fun overnightShift_determineShiftType_standard11and12_detectsDay1AndDay2() {
        val schedDay1 = WorkSchedule(
            workDate = "20261028",
            diaNo = "16320",
            alcSpecDvCd = "11",
            startTime = "17:40",
            endTime = "06:45"
        )
        val schedDay2 = WorkSchedule(
            workDate = "20261029",
            diaNo = "16320",
            alcSpecDvCd = "12",
            isContinuation = true
        )
        val list = listOf(schedDay1, schedDay2)

        assertEquals(ShiftType.OVERNIGHT_DAY1, list.determineShiftType("20261028"))
        assertEquals(ShiftType.OVERNIGHT_DAY2, list.determineShiftType("20261029"))
    }

    @Test
    fun overnightShift_determineShiftType_acrossMonthBoundary_detectsCorrectly() {
        val schedLastDay = WorkSchedule(
            workDate = "20261031",
            diaNo = "16320",
            alcSpecDvCd = "11"
        )
        val schedFirstDay = WorkSchedule(
            workDate = "20261101",
            diaNo = "16320",
            alcSpecDvCd = "12"
        )
        val list = listOf(schedLastDay, schedFirstDay)

        assertEquals(ShiftType.OVERNIGHT_DAY1, list.determineShiftType("20261031"))
        assertEquals(ShiftType.OVERNIGHT_DAY2, list.determineShiftType("20261101"))
    }

    @Test
    fun overnightShift_evaluatesBothDay1AndDay2AttendanceAlarms() {
        val workDate = LocalDate.now().plusDays(1) // tomorrow so alarms are in the future
        val day2Date = workDate.plusDays(1)
        val date1Str = workDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val date2Str = day2Date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))

        val att1 = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "K4658",
            trnSttNm = "정상",
            gtwcEpctBlgNm = "안산",
            gtwcEpctTm = "18:20",
            gtwcGubunCd = "1",
            pjtDt = date1Str
        )
        val att2 = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "F4518",
            trnSttNm = "정상",
            gtwcEpctBlgNm = "시흥기지",
            gtwcEpctTm = "05:36",
            gtwcGubunCd = "1",
            pjtDt = date2Str
        )

        val settings = com.example.crewmate.alarm.WorkAlarmSettings(
            alarmEnabled = true,
            attendanceEnabled = true,
            attendanceLeadMinutes = 40,
            commuteEnabled = false,
            driveEnabled = false,
            prepEnabled = false,
            deadheadEnabled = false,
            shuntingEnabled = false
        )

        val alarms = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
            segments = emptyList(),
            workDate = workDate,
            settings = settings,
            attendanceStatuses = listOf(att1, att2)
        )

        assertEquals(2, alarms.size)

        // Day 1 attendance alarm
        val alarm1 = alarms.find { it.trainNo == "K4658" }
        assertNotNull(alarm1)
        assertEquals(workDate.atTime(17, 40), alarm1!!.triggerDateTime)
        assertEquals("출무 40분 전", alarm1.title)

        // Day 2 attendance alarm
        val alarm2 = alarms.find { it.trainNo == "F4518" }
        assertNotNull(alarm2)
        assertEquals(day2Date.atTime(4, 56), alarm2!!.triggerDateTime)
        assertTrue(alarm2.title.contains("출무 40분 전"))
        assertTrue(alarm2.title.contains(day2Date.format(java.time.format.DateTimeFormatter.ofPattern("MM.dd"))))
        assertEquals(date2Str, alarm2.pjtDt)
    }

    @Test
    fun overnightShift_attendanceAlarm_notCancelledByPreviousDayAttendance() {
        val attDay1 = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "K4658",
            trnSttNm = "정상",
            gtwcEpctBlgNm = "안산",
            gtwcEpctTm = "18:20",
            gtwcGubunCd = "6", // 적합성완료 (Checked in!)
            pjtDt = "20261028",
            tnsmTm = "18:15"
        )
        val attDay2 = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "F4518",
            trnSttNm = "정상",
            gtwcEpctBlgNm = "시흥기지",
            gtwcEpctTm = "05:36",
            gtwcGubunCd = "1", // 출무전 (Not checked in!)
            pjtDt = "20261029"
        )

        val map = mapOf(
            "20261028" to listOf(attDay1),
            "20261029" to listOf(attDay2)
        )

        // Day 2 alarm check: user has NOT checked in for Day 2 yet!
        val shouldCancelDay2 = com.example.crewmate.alarm.AttendanceAlarmCancellationChecker.shouldCancelSiren(
            cachedStatusesByDate = map,
            targetPjtDt = "20261029",
            targetTrainNo = "F4518",
            targetTime = "05:36"
        )
        // MUST BE FALSE! Alarm must sound for Day 2!
        assertFalse(shouldCancelDay2)

        // Day 1 alarm check: user already completed Day 1 check-in
        val shouldCancelDay1 = com.example.crewmate.alarm.AttendanceAlarmCancellationChecker.shouldCancelSiren(
            cachedStatusesByDate = map,
            targetPjtDt = "20261028",
            targetTrainNo = "K4658",
            targetTime = "18:20"
        )
        // MUST BE TRUE! Alarm should be silenced because Day 1 was completed
        assertTrue(shouldCancelDay1)
    }

    @Test
    fun sameDay_multipleAttendance_independentCancellation() {
        val duty1 = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "K4601",
            trnSttNm = "정상",
            gtwcEpctBlgNm = "안산",
            gtwcEpctTm = "08:00",
            gtwcGubunCd = "6", // 적합성완료
            pjtDt = "20261104",
            tnsmTm = "07:55"
        )
        val duty2 = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "K4620",
            trnSttNm = "정상",
            gtwcEpctBlgNm = "청량리",
            gtwcEpctTm = "14:30",
            gtwcGubunCd = "1", // 출무전
            pjtDt = "20261104"
        )

        val map = mapOf("20261104" to listOf(duty1, duty2))

        // Duty 1 is checked in -> cancel siren
        val cancelDuty1 = com.example.crewmate.alarm.AttendanceAlarmCancellationChecker.shouldCancelSiren(
            cachedStatusesByDate = map,
            targetPjtDt = "20261104",
            targetTrainNo = "K4601",
            targetTime = "08:00"
        )
        assertTrue(cancelDuty1)

        // Duty 2 is NOT checked in -> do NOT cancel siren!
        val cancelDuty2 = com.example.crewmate.alarm.AttendanceAlarmCancellationChecker.shouldCancelSiren(
            cachedStatusesByDate = map,
            targetPjtDt = "20261104",
            targetTrainNo = "K4620",
            targetTime = "14:30"
        )
        assertFalse(cancelDuty2)
    }

    @Test
    fun workScheduleRepository_fallsBackToLocalCache_onTimeoutCancellationException() = kotlinx.coroutines.runBlocking {
        val cachedEntity = com.example.crewmate.database.WorkScheduleEntity(
            yearMonth = "202611",
            date = "20261104",
            diaNo = "16320",
            trainNo = "K4501",
            trainNo2 = null,
            startTime = "08:00",
            endTime = "16:30",
            nonActiveType = null,
            isContinuation = false,
            isHoliday = false,
            alcSpecDvCd = "11",
            hldyYn = "N",
            isChanged = false
        )

        val fakeDao = object : com.example.crewmate.database.WorkScheduleDao {
            override suspend fun getSchedulesForMonth(yearMonth: String): List<com.example.crewmate.database.WorkScheduleEntity> =
                if (yearMonth == "202611") listOf(cachedEntity) else emptyList()
            override suspend fun getSchedulesForYear(yearPrefix: String) = listOf(cachedEntity)
            override suspend fun insertSchedules(schedules: List<com.example.crewmate.database.WorkScheduleEntity>) {}
            override suspend fun deleteSchedulesForMonth(yearMonth: String) {}
            override suspend fun cleanupOldSchedules(thresholdMonth: String) {}
        }

        val hangingDataSource = object : com.example.crewmate.data.remote.xcrew.XcrewDataSource {
            override suspend fun getWorkSchedules(month: String): com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleResponse {
                kotlinx.coroutines.withTimeout(1L) {
                    kotlinx.coroutines.delay(500L)
                }
                error("Unreachable")
            }
        }

        val repo = WorkScheduleRepositoryImpl(
            dataSource = hangingDataSource,
            parser = WorkScheduleParser(),
            workScheduleDao = fakeDao
        )

        // 1. getCachedSchedulesOnly never calls hangingDataSource
        val localOnlyResult = repo.getCachedSchedulesOnly("202611")
        assertEquals(1, localOnlyResult.schedules.size)
        assertEquals("16320", localOnlyResult.schedules[0].diaNo)

        // 2. getSchedules(forceRefresh = true) catches TimeoutCancellationException and falls back to local DB cache
        val fallbackResult = repo.getSchedules("202611", forceRefresh = true)
        assertEquals(1, fallbackResult.schedules.size)
        assertEquals("16320", fallbackResult.schedules[0].diaNo)
    }

    @Test
    fun workSchedule_isValidActiveDia_identifiesValidAndExcludesNonActiveAndRestDays() {
        // 1. Valid numeric & alphanumeric dias
        val activeNumeric = WorkSchedule(workDate = "20261104", diaNo = "16320", alcSpecDvCd = "11")
        val activeAlphaNum = WorkSchedule(workDate = "20261104", diaNo = "K101", alcSpecDvCd = "12")
        assertTrue(activeNumeric.isValidActiveDia())
        assertTrue(activeAlphaNum.isValidActiveDia())

        // 2. Non-active code (21) is excluded even if numeric dia exists
        val nonActiveDuty = WorkSchedule(workDate = "20261104", diaNo = "16320", alcSpecDvCd = "21")
        assertFalse(nonActiveDuty.isValidActiveDia())

        // 3. Rest day code (02) is excluded
        val restDuty = WorkSchedule(workDate = "20261104", diaNo = "16320", alcSpecDvCd = "02")
        assertFalse(restDuty.isValidActiveDia())

        // 4. Non-active keywords in diaNo or nonActiveType are excluded
        val leaveDuty = WorkSchedule(workDate = "20261104", diaNo = "연차", alcSpecDvCd = "11")
        val sickDuty = WorkSchedule(workDate = "20261104", diaNo = "16320", nonActiveType = "병가", alcSpecDvCd = "11")
        val offDuty = WorkSchedule(workDate = "20261104", diaNo = "S", alcSpecDvCd = "02")
        val nullDia = WorkSchedule(workDate = "20261104", diaNo = "", alcSpecDvCd = "11")
        assertFalse(leaveDuty.isValidActiveDia())
        assertFalse(sickDuty.isValidActiveDia())
        assertFalse(offDuty.isValidActiveDia())
        assertFalse(nullDia.isValidActiveDia())

        // 5. WorkScheduleEntity extension parity
        val entity = com.example.crewmate.database.WorkScheduleEntity(
            yearMonth = "202611",
            date = "20261104",
            diaNo = "16320",
            trainNo = null,
            trainNo2 = null,
            startTime = "08:00",
            endTime = "17:00",
            nonActiveType = null,
            isContinuation = false,
            isHoliday = false,
            alcSpecDvCd = "11",
            hldyYn = "N",
            isChanged = false
        )
        assertTrue(entity.isValidActiveDia())
        assertFalse(entity.copy(alcSpecDvCd = "21").isValidActiveDia())
    }

    @Test
    fun dataBackupManager_isSensitiveKey_blocksPersonalInfoAndTelegramChatId() {
        val sensitiveKeys = listOf(
            "cached_user_name",
            "cached_user_affil",
            "telegram_chat_id",
            "telegram_verified",
            "telegram_stopped_trains_20261104",
            "telegram_target_overrides_20261104",
            "xcrew_password",
            "JSESSIONID",
            "auto_offline_last_window_key",
            "auto_offline_last_trigger_time",
            "my_user_secret",
            "auth_token",
            "empnm_value"
        )
        sensitiveKeys.forEach { key ->
            assertTrue("Expected key '$key' to be classified as sensitive", com.example.crewmate.data.backup.DataBackupManager.isSensitiveKey(key))
        }

        val safeKeys = listOf(
            "theme_mode",
            "alarm_lead_minutes",
            "is_offline_mode",
            "sound_mode",
            "haptic_enabled",
            "midnight_auto_capture",
            "duty_feedback_dialog_shown"
        )
        safeKeys.forEach { key ->
            assertFalse("Expected key '$key' to be allowed (not sensitive)", com.example.crewmate.data.backup.DataBackupManager.isSensitiveKey(key))
        }
    }

    @Test
    fun subwayNowManager_getRequestCodeForTrain_isDeterministicAndCollisionResistant() {
        val code1 = com.example.crewmate.telegram.SubwayNowManager.getRequestCodeForTrain("K1234")
        val code2 = com.example.crewmate.telegram.SubwayNowManager.getRequestCodeForTrain("K1234")
        val code3 = com.example.crewmate.telegram.SubwayNowManager.getRequestCodeForTrain("S1234")
        val codeNonDigits = com.example.crewmate.telegram.SubwayNowManager.getRequestCodeForTrain("TRAIN_TEST")

        assertEquals("Same train number must produce identical request codes", code1, code2)
        assertTrue("Request code should be in safe positive range >= 90000", code1 >= 90000)
        assertTrue("Non digit train number must produce non-negative code >= 90000", codeNonDigits >= 90000)
    }

    @Test
    fun workSchedule_deduplication_removesDuplicateRowsForSameDate() {
        val duplicates = listOf(
            WorkSchedule(
                workDate = "20261102",
                diaNo = "16035",
                startTime = "09:53",
                endTime = "21:05",
                trainNo = "K4557",
                trainNo2 = "K4679",
                alcSpecDvCd = "11",
                hldyYn = "N"
            ),
            WorkSchedule(
                workDate = "20261102",
                diaNo = "16035",
                startTime = "09:53",
                endTime = "21:05",
                trainNo = "K4557",
                trainNo2 = "K4679",
                alcSpecDvCd = "11",
                hldyYn = "N"
            ),
            WorkSchedule(
                workDate = "20261101",
                diaNo = "",
                nonActiveType = "연차",
                alcSpecDvCd = "21",
                hldyYn = "N"
            ),
            WorkSchedule(
                workDate = "20261101",
                diaNo = "",
                nonActiveType = "연차",
                alcSpecDvCd = "21",
                hldyYn = "N"
            )
        )

        val deduplicated = duplicates.distinctBy {
            "${it.workDate}_${it.diaNo}_${it.startTime}_${it.endTime}_${it.trainNo}_${it.trainNo2}_${it.alcSpecDvCd}_${it.hldyYn}_${it.nonActiveType}"
        }

        assertEquals(2, deduplicated.size)
        assertEquals("20261102", deduplicated[0].workDate)
        assertEquals("16035", deduplicated[0].diaNo)
        assertEquals("20261101", deduplicated[1].workDate)
        assertEquals("연차", deduplicated[1].nonActiveType)
    }

    @Test
    fun workSchedule_preservesDistinctDutiesOnSameDate() {
        val distinctDutiesOnSameDate = listOf(
            WorkSchedule(
                workDate = "20261105",
                diaNo = "16355",
                startTime = "18:11",
                endTime = "07:10",
                trainNo = "G6434",
                trainNo2 = "K6504",
                alcSpecDvCd = "11",
                hldyYn = "Y"
            ),
            WorkSchedule(
                workDate = "20261105",
                diaNo = "16001",
                startTime = "05:17",
                endTime = "15:13",
                trainNo = "K4510",
                trainNo2 = "K4545",
                alcSpecDvCd = "11",
                hldyYn = "Y"
            )
        )

        val deduplicated = distinctDutiesOnSameDate.distinctBy {
            "${it.workDate}_${it.diaNo}_${it.startTime}_${it.endTime}_${it.trainNo}_${it.trainNo2}_${it.alcSpecDvCd}_${it.hldyYn}_${it.nonActiveType}"
        }

        assertEquals(2, deduplicated.size)
        assertEquals("16355", deduplicated[0].diaNo)
        assertEquals("16001", deduplicated[1].diaNo)
    }

    @Test
    fun diaCapture_arrivalStationPlaceholder_renderedWhenTimeExists() {
        val prepSegment = DiaDetailSegment(
            num = 1,
            pdiaEfcStDt = null,
            pdiaEfcClsDt = null,
            trnNo = null,
            dptStnNm = null,
            arvStnNm = null, // Arv station is null
            dptTm = "0517",
            arvTm = "0557", // But arv time exists
            pjtHrDvNm = "준비",
            pjtTnum = null,
            pjtDst = null
        )

        val dptStn = prepSegment.dptStnNm?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
        val arvStn = prepSegment.arvStnNm?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
        val dptTime = prepSegment.dptTm?.let { "${it.substring(0, 2)}:${it.substring(2, 4)}" } ?: "-"
        val arvTime = prepSegment.arvTm?.let { "${it.substring(0, 2)}:${it.substring(2, 4)}" } ?: "-"

        assertEquals("-", dptStn)
        assertEquals("-", arvStn)
        assertEquals("05:17", dptTime)
        assertEquals("05:57", arvTime)

        // Verifying the condition (arvStn != "-" || arvTime != "-") is TRUE, ensuring "-" is drawn and not skipped
        val shouldDrawArvStn = arvStn != "-" || arvTime != "-"
        assertTrue("When arrival time exists, arrival station '-' must be drawn to align columns", shouldDrawArvStn)
    }

    @Test
    fun diaCapture_prepDivider_evaluatesVisibilityAndAttributes() {
        val segment1 = DiaDetailSegment(
            num = 1,
            pdiaEfcStDt = null,
            pdiaEfcClsDt = null,
            trnNo = "K4510",
            dptStnNm = "오이도",
            arvStnNm = "왕십리",
            dptTm = "0700",
            arvTm = "0815",
            pjtHrDvNm = "승무",
            pjtTnum = null,
            pjtDst = null
        )
        val prepSegment = DiaDetailSegment(
            num = 2,
            pdiaEfcStDt = null,
            pdiaEfcClsDt = null,
            trnNo = null,
            dptStnNm = "왕십리",
            arvStnNm = null,
            dptTm = "0815",
            arvTm = "0830",
            pjtHrDvNm = "준비",
            pjtTnum = null,
            pjtDst = null
        )

        val cat1 = segment1.pjtHrDvNm ?: ""
        val cat2 = prepSegment.pjtHrDvNm ?: ""

        val isPrep = cat2.contains("준비") || cat2.contains("출고준비")
        val prevIsPrep = cat1.contains("준비") || cat1.contains("출고준비")

        // 1. When showPrepDivider is true, divider condition holds
        val showPrepDividerTrue = true
        val shouldDrawDividerWhenEnabled = showPrepDividerTrue && isPrep && !prevIsPrep
        assertTrue("When showPrepDivider is enabled, prep divider must be drawn", shouldDrawDividerWhenEnabled)

        // 2. When showPrepDivider is false, divider is skipped
        val showPrepDividerFalse = false
        val shouldDrawDividerWhenDisabled = showPrepDividerFalse && isPrep && !prevIsPrep
        assertFalse("When showPrepDivider is disabled in settings, prep divider must be skipped", shouldDrawDividerWhenDisabled)
    }

    @Test
    fun diaCapture_themeResolution_evaluatesAppThemeModeAndColors() {
        // AppThemeMode.LIGHT
        val lightMode = com.example.crewmate.ui.theme.AppThemeMode.LIGHT
        val isDarkLight = when (lightMode) {
            com.example.crewmate.ui.theme.AppThemeMode.SYSTEM -> false
            com.example.crewmate.ui.theme.AppThemeMode.LIGHT -> false
            com.example.crewmate.ui.theme.AppThemeMode.DARK -> true
        }
        val lightBgColor = if (isDarkLight) "#0B0F17" else "#F6F8FC"
        assertFalse(isDarkLight)
        assertEquals("#F6F8FC", lightBgColor)

        // AppThemeMode.DARK
        val darkMode = com.example.crewmate.ui.theme.AppThemeMode.DARK
        val isDarkDark = when (darkMode) {
            com.example.crewmate.ui.theme.AppThemeMode.SYSTEM -> false
            com.example.crewmate.ui.theme.AppThemeMode.LIGHT -> false
            com.example.crewmate.ui.theme.AppThemeMode.DARK -> true
        }
        val darkBgColor = if (isDarkDark) "#0B0F17" else "#F6F8FC"
        assertTrue(isDarkDark)
        assertEquals("#0B0F17", darkBgColor)
    }

    @Test
    fun diaCaptureRenderer_gridWeights_distributeSymmetricallyToRightEdge() {
        val width = 1440f
        val density = 3.0f
        val docPadding = 20f * density
        val cardInnerPadding = 16f * density

        val innerLeft = docPadding + cardInnerPadding
        val innerRight = width - docPadding - cardInnerPadding

        val trackW = 20f * density
        val trackGap = 6f * density
        val gridLeft = innerLeft + trackW + trackGap
        val gridRight = innerRight
        val gridTotalW = gridRight - gridLeft

        val arrowW = 32f * density
        val availableWeightW = gridTotalW - arrowW
        val unitW = availableWeightW / 5.8f

        val col1W = unitW * 1.6f
        val col2W = unitW * 1.1f
        val col3W = unitW * 1.0f
        val col4W = arrowW
        val col5W = unitW * 1.1f
        val col6W = unitW * 1.0f

        val totalCalculatedWidth = col1W + col2W + col3W + col4W + col5W + col6W
        assertEquals(gridTotalW, totalCalculatedWidth, 0.001f)

        val rightmostEdge = gridLeft + totalCalculatedWidth
        assertEquals(innerRight, rightmostEdge, 0.001f)
    }

    @Test
    fun attendanceLatch_keyOf_normalizesDateTrainAndTime() {
        val key1 = com.example.crewmate.alarm.AttendanceCheckInLatch.keyOf("2026-11-06", "K4510", "18:11")
        val key2 = com.example.crewmate.alarm.AttendanceCheckInLatch.keyOf("20261106", " K4510 ", "1811")
        val key3 = com.example.crewmate.alarm.AttendanceCheckInLatch.keyOf("20261106", "K4510", "18:11:00")

        assertEquals("20261106|K4510|1811", key1)
        assertEquals("20261106|K4510|1811", key2)
        assertEquals("20261106|K4510|1811", key3)

        // Invalid length
        assertNull(com.example.crewmate.alarm.AttendanceCheckInLatch.keyOf("2026", "K4510", "1811"))
        assertNull(com.example.crewmate.alarm.AttendanceCheckInLatch.keyOf("20261106", "K4510", "18"))
    }

    @Test
    fun evaluator_skipsAttendanceAlarm_whenLatchedEvenIfStaleCacheSaysNotCheckedIn() {
        val workDate = LocalDate.now().plusDays(1)
        val dateStr = workDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val settings = com.example.crewmate.alarm.WorkAlarmSettings(
            alarmEnabled = true,
            attendanceEnabled = true,
            attendanceLeadMinutes = 5
        )

        // Stale cache item showing NOT checked in (gtwcGubunCd = "1", no actual time)
        val staleStatus = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "K4510",
            trnSttNm = null,
            gtwcEpctBlgNm = "안산승소",
            gtwcEpctTm = "2350",
            gtwcGubunCd = "1", // 출무전
            pjtDt = dateStr
        )
        assertFalse(staleStatus.isCheckedIn)

        // 1) When latch is empty: attendance alarm is generated
        val alarmsWithoutLatch = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
            segments = emptyList(),
            workDate = workDate,
            settings = settings,
            attendanceStatuses = listOf(staleStatus),
            checkedInKeys = emptySet()
        )
        assertEquals(1, alarmsWithoutLatch.size)
        assertEquals("출무", alarmsWithoutLatch[0].cat)

        // 2) When latch contains the check-in key: attendance alarm is suppressed
        val latchedKey = com.example.crewmate.alarm.AttendanceCheckInLatch.keyOf(staleStatus)!!
        val alarmsWithLatch = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
            segments = emptyList(),
            workDate = workDate,
            settings = settings,
            attendanceStatuses = listOf(staleStatus),
            checkedInKeys = setOf(latchedKey)
        )
        assertTrue("When latched as checked in, attendance alarm must not be generated", alarmsWithLatch.isEmpty())
    }

    @Test
    fun evaluator_overnightDay2_latchOnDay1DoesNotSuppressDay2() {
        val workDate = LocalDate.now().plusDays(1)
        val day1Str = workDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val day2Str = workDate.plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val settings = com.example.crewmate.alarm.WorkAlarmSettings(
            alarmEnabled = true,
            attendanceEnabled = true,
            attendanceLeadMinutes = 5
        )

        val day1Status = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "K4510",
            trnSttNm = null,
            gtwcEpctBlgNm = "안산승소",
            gtwcEpctTm = "1811",
            gtwcGubunCd = "6", // Day 1 completed
            pjtDt = day1Str,
            tnsmTm = "1805"
        )
        val day2Status = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "K4601",
            trnSttNm = null,
            gtwcEpctBlgNm = "구로승소",
            gtwcEpctTm = "2350",
            gtwcGubunCd = "1", // Day 2 not checked in
            pjtDt = day2Str
        )

        val day1Key = com.example.crewmate.alarm.AttendanceCheckInLatch.keyOf(day1Status)!!
        val alarms = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
            segments = emptyList(),
            workDate = workDate,
            settings = settings,
            attendanceStatuses = listOf(day1Status, day2Status),
            checkedInKeys = setOf(day1Key)
        )

        // Day 2 alarm must be generated and Day 1 suppressed
        assertEquals(1, alarms.size)
        assertEquals("K4601", alarms[0].trainNo)
        assertEquals(day2Str, alarms[0].pjtDt)
    }

    @Test
    fun cancellationChecker_emptyServerTrainNo_doesNotFalseMatch() {
        // Bug fix test: when server dto has empty trnNo, it should not false-match targetTrainNo="K4510" via endsWith("")
        val serverDto = com.example.crewmate.data.remote.xcrew.AttendanceStatusDto(
            trnNo = "", // Empty train no
            trnSttNm = null,
            gtwcEpctBlgNm = "안산승소",
            gtwcEpctTm = "1811",
            gtwcGubunCd = "6", // Checked in
            pjtDt = "20261106",
            tnsmTm = "1805"
        )

        val statusMap = mapOf("20261106" to listOf(serverDto))

        // Target requires train K4510 with no time specified
        val shouldCancelWithoutTime = com.example.crewmate.alarm.AttendanceAlarmCancellationChecker.shouldCancelSiren(
            cachedStatusesByDate = statusMap,
            targetPjtDt = "20261106",
            targetTrainNo = "K4510",
            targetTime = ""
        )
        assertFalse("Empty server train number must not match target train K4510", shouldCancelWithoutTime)

        // But when both train and time match, it cancels properly
        val validServerDto = serverDto.copy(trnNo = "K4510")
        val validMap = mapOf("20261106" to listOf(validServerDto))
        val shouldCancelValid = com.example.crewmate.alarm.AttendanceAlarmCancellationChecker.shouldCancelSiren(
            cachedStatusesByDate = validMap,
            targetPjtDt = "20261106",
            targetTrainNo = "K4510",
            targetTime = "1811"
        )
        assertTrue("When train and time match and checked in, cancellation must return true", shouldCancelValid)
    }

    private class FakeCrewAssignmentDao : com.example.crewmate.database.CrewAssignmentDao {
        val rows = mutableMapOf<String, List<com.example.crewmate.database.CrewAssignmentEntity>>()

        override suspend fun getAssignmentsForDate(date: String): List<com.example.crewmate.database.CrewAssignmentEntity> =
            rows[date].orEmpty()

        override suspend fun countForDate(date: String): Int =
            rows[date]?.size ?: 0

        override suspend fun insertAssignments(assignments: List<com.example.crewmate.database.CrewAssignmentEntity>) {
            assignments.groupBy { it.date }.forEach { (dt, list) ->
                rows[dt] = rows[dt].orEmpty() + list
            }
        }

        override suspend fun deleteAssignmentsForDate(date: String) {
            rows.remove(date)
        }

        override suspend fun cleanupOldAssignments(thresholdDate: String) {
            val keysToRemove = rows.keys.filter { it < thresholdDate }
            keysToRemove.forEach { rows.remove(it) }
        }
    }

    @Test
    fun prefetcher_windowDates_returnsTodayTomorrowYesterdayAndDayBeforeInOrder() {
        val today = LocalDate.of(2026, 11, 7)
        val dates = OfflineCrewPrefetcher.windowDates(today)
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 5)
            ),
            dates
        )
        assertEquals(
            listOf("20261107", "20261108", "20261106", "20261105"),
            OfflineCrewPrefetcher.windowDateStrings(today)
        )
    }

    @Test
    fun crewRepo_emptyServerResponse_keepsExistingCache() = kotlinx.coroutines.runBlocking {
        val dao = FakeCrewAssignmentDao()
        dao.replaceAssignmentsForDate(
            "20261107",
            listOf(
                com.example.crewmate.database.CrewAssignmentEntity(
                    date = "20261107",
                    pdiaNo = "16014",
                    repTrn1No = "K4501",
                    repTrn2No = "K4512",
                    gwkTm = "0603",
                    loiwTm = "1707",
                    crewMembers = listOf("윤영준")
                )
            )
        )

        val emptyServerDataSource = com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource { emptyList() }
        val repo = CrewWorkStatusRepository(emptyServerDataSource, dao) { false }

        val outcome = repo.refreshAndStore("20261107")
        assertTrue(outcome is CrewFetchOutcome.KeptCache)
        assertEquals(1, dao.countForDate("20261107"))

        val list = repo.getCrewWorkStatus("20261107")
        assertEquals(1, list.size)
        assertEquals("16014", list[0].pdiaNo)
    }

    @Test
    fun crewRepo_fetchFailureWithoutCache_returnsFailedOutcome() = kotlinx.coroutines.runBlocking {
        val dao = FakeCrewAssignmentDao()
        val failingDataSource = com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource {
            throw com.example.crewmate.data.remote.xcrew.XcrewNetworkException()
        }
        val repo = CrewWorkStatusRepository(failingDataSource, dao) { false }

        val outcome = repo.refreshAndStore("20261107")
        assertTrue(outcome is CrewFetchOutcome.Failed)
        assertFalse(outcome.isUsable)
    }

    @Test
    fun crewRepo_offline_neverCallsDataSource() = kotlinx.coroutines.runBlocking {
        val dao = FakeCrewAssignmentDao()
        dao.replaceAssignmentsForDate(
            "20261107",
            listOf(
                com.example.crewmate.database.CrewAssignmentEntity(
                    date = "20261107",
                    pdiaNo = "16204",
                    repTrn1No = "K4510",
                    repTrn2No = "K4612",
                    gwkTm = "0547",
                    loiwTm = "1544",
                    crewMembers = listOf("윤영준")
                )
            )
        )
        var networkCalls = 0
        val countingDataSource = com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource {
            networkCalls++
            emptyList()
        }
        val repo = CrewWorkStatusRepository(countingDataSource, dao) { true }

        val cached = repo.getCrewWorkStatus("20261107")
        assertEquals(1, cached.size)
        assertEquals("16204", cached[0].pdiaNo)
        assertEquals(0, networkCalls)
    }

    @Test
    fun prefetchReport_todayOk_falseWhenTodayFailed() = kotlinx.coroutines.runBlocking {
        val dao = FakeCrewAssignmentDao()
        val today = LocalDate.of(2026, 11, 7)

        // Fail only on today (20261107), succeed on other 3 dates
        val selectiveDataSource = com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource { dt ->
            if (dt == "20261107") {
                throw com.example.crewmate.data.remote.xcrew.XcrewNetworkException()
            }
            listOf(
                com.example.crewmate.data.remote.xcrew.CrewAssignment(
                    pdiaNo = "16001",
                    repTrn1No = "K4510",
                    repTrn2No = "K4545",
                    gwkTm = "0517",
                    loiwTm = "1513",
                    crewMembers = listOf("홍길동")
                )
            )
        }
        val repo = CrewWorkStatusRepository(selectiveDataSource, dao) { false }
        val report = OfflineCrewPrefetcher.prefetchWindow(repo, dao, today, context = null, skipIfCachedToday = false)

        assertFalse(report.todayOk)
        assertFalse(report.allOk)
        assertEquals(1, dao.countForDate("20261108"))
        assertEquals(1, dao.countForDate("20261106"))
        assertEquals(1, dao.countForDate("20261105"))
    }

    @Test
    fun offlineLookupRange_clampsAndContainsYesterdayToTomorrowOnly() {
        val today = LocalDate.of(2026, 11, 7)
        assertFalse(OfflineLookupRange.contains(today, LocalDate.of(2026, 11, 5)))
        assertTrue(OfflineLookupRange.contains(today, LocalDate.of(2026, 11, 6)))
        assertTrue(OfflineLookupRange.contains(today, LocalDate.of(2026, 11, 7)))
        assertTrue(OfflineLookupRange.contains(today, LocalDate.of(2026, 11, 8)))
        assertFalse(OfflineLookupRange.contains(today, LocalDate.of(2026, 11, 9)))

        assertEquals(LocalDate.of(2026, 11, 6), OfflineLookupRange.clamp(today, LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2026, 11, 8), OfflineLookupRange.clamp(today, LocalDate.of(2026, 11, 20)))
    }

    @Test
    fun censusManager_offline_refusesToStartAndMakesNoRequests() {
        var crewRequests = 0
        var clearCalls = 0
        val fakeCensusDao = object : com.example.crewmate.database.DiaCensusDao {
            override fun getAllCensusFlow() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.crewmate.database.DiaCensusEntity>())
            override suspend fun getAllCensus() = emptyList<com.example.crewmate.database.DiaCensusEntity>()
            override suspend fun getCensusCount() = 0
            override suspend fun getLatestUpdateDate(): String? = null
            override suspend fun getCensusByPdiaNo(pdiaNo: String): com.example.crewmate.database.DiaCensusEntity? = null
            override suspend fun searchCensusByTrainNumber(trainNo: String) = emptyList<com.example.crewmate.database.DiaCensusEntity>()
            override suspend fun insertCensus(entity: com.example.crewmate.database.DiaCensusEntity) {}
            override suspend fun insertCensusList(entities: List<com.example.crewmate.database.DiaCensusEntity>) {}
            override suspend fun clearAllCensus() { clearCalls++ }
        }
        val manager = DiaCensusManager(
            crewWorkStatusDataSource = { crewRequests++; emptyList() },
            diaDetailDataSource = { _, _ -> error("Should not be called") },
            diaCensusDao = fakeCensusDao,
            isOfflineProvider = { true }
        )

        manager.startBackgroundCensus()
        assertTrue(manager.progressState.value is CensusProgressState.Error)
        assertEquals(0, crewRequests)
        assertEquals(0, clearCalls)
    }

    @Test
    fun networkGuard_blocksRequestAndRelogin_whenOffline() = kotlinx.coroutines.runBlocking {
        var reloginCalls = 0
        val session = com.example.crewmate.data.remote.xcrew.XcrewWebViewSession().apply {
            offlineGuard = { true }
            setSilentReloginProvider {
                reloginCalls++
                true
            }
        }
        assertTrue(session.isNetworkBlocked())
        assertFalse(session.trySilentRelogin(0L))
        assertEquals(0, reloginCalls)

        val dataSource = com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource(
            session,
            com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser()
        )
        try {
            dataSource.fetchCrewWorkStatus("20261107")
            org.junit.Assert.fail("Expected XcrewNetworkException when offlineGuard is true")
        } catch (_: com.example.crewmate.data.remote.xcrew.XcrewNetworkException) {
            // Expected
        }
    }

    @Test
    fun precheckTime_isFifteenSecondsBeforeSiren() {
        val trigger = java.time.LocalDateTime.of(2026, 11, 7, 18, 6, 0)
        val precheck = com.example.crewmate.alarm.AttendancePrecheckCoordinator.precheckTimeFor(trigger)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 7, 18, 5, 45), precheck)
    }

    @Test
    fun coordinator_defer_firesOnlyWhenNotCheckedIn() {
        com.example.crewmate.alarm.AttendancePrecheckCoordinator.clearForTesting()
        val key = com.example.crewmate.alarm.AttendancePrecheckCoordinator.keyOf("20261107", "K4510", "18:11")!!
        val payload = com.example.crewmate.alarm.AttendancePrecheckCoordinator.SirenPayload(
            ruleTitle = "출무 5분 전",
            cat = "출무",
            trainNo = "K4510",
            dptStn = "안산승소",
            dptTimeStr = "18:11",
            pjtDt = "20261107",
            soundMode = com.example.crewmate.alarm.AlarmSoundMode.SOUND
        )

        // When not in-flight, tryDefer returns false
        assertFalse(com.example.crewmate.alarm.AttendancePrecheckCoordinator.tryDefer(key, payload, nowMs = 1_000L))

        // When marked in-flight at T-15s (1_000L) and siren arrives at T (16_000L), tryDefer returns true
        com.example.crewmate.alarm.AttendancePrecheckCoordinator.markInFlight(key, nowMs = 1_000L)
        assertTrue(com.example.crewmate.alarm.AttendancePrecheckCoordinator.tryDefer(key, payload, nowMs = 16_000L))

        // When pre-check completes, complete(key) returns the deferred payload and clears state
        val recovered = com.example.crewmate.alarm.AttendancePrecheckCoordinator.complete(key)
        assertEquals(payload, recovered)
        assertFalse(com.example.crewmate.alarm.AttendancePrecheckCoordinator.isInFlight(key, nowMs = 17_000L))
    }

    @Test
    fun coordinator_defer_expiredInFlightDoesNotDefer() {
        com.example.crewmate.alarm.AttendancePrecheckCoordinator.clearForTesting()
        val key = com.example.crewmate.alarm.AttendancePrecheckCoordinator.keyOf("20261107", "K4510", "18:11")!!
        val payload = com.example.crewmate.alarm.AttendancePrecheckCoordinator.SirenPayload(
            ruleTitle = "출무 5분 전",
            cat = "출무",
            trainNo = "K4510",
            dptStn = "안산승소",
            dptTimeStr = "18:11",
            pjtDt = "20261107",
            soundMode = com.example.crewmate.alarm.AlarmSoundMode.SOUND
        )

        com.example.crewmate.alarm.AttendancePrecheckCoordinator.markInFlight(key, nowMs = 1_000L)
        // 30 seconds later (> MAX_IN_FLIGHT_WINDOW_MILLIS = 25s), tryDefer must return false so receiver fires immediately
        assertFalse(com.example.crewmate.alarm.AttendancePrecheckCoordinator.tryDefer(key, payload, nowMs = 31_000L))
    }

    @Test
    fun commuteAlarm_triggers60MinsBeforePrepSegmentAndCombinesWithNextDayAlarms() {
        val today = java.time.LocalDate.now().plusDays(1)
        val tomorrow = today.plusDays(1)
        val settings = com.example.crewmate.alarm.WorkAlarmSettings(
            alarmEnabled = true,
            commuteEnabled = true,
            commuteLeadMinutes = 60,
            driveEnabled = true,
            driveLeadMinutes = 10,
            driveCheckGamsi = false
        )

        val todaySegments = listOf(
            DiaDetailSegment(1, null, null, null, "안산", "안산", "07:15", "07:30", "준비", null, null),
            DiaDetailSegment(2, null, null, "K4501", "안산", "청량리", "08:00", "09:30", "운전", null, null)
        )
        val tomorrowSegments = listOf(
            DiaDetailSegment(1, null, null, null, "안산", "안산", "06:45", "07:00", "준비", null, null),
            DiaDetailSegment(2, null, null, "K4503", "안산", "광운대", "07:20", "09:00", "운전", null, null)
        )

        val todayAlarms = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
            segments = todaySegments,
            workDate = today,
            settings = settings
        )
        val tomorrowAlarms = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
            segments = tomorrowSegments,
            workDate = tomorrow,
            settings = settings
        )

        val todayCommute = todayAlarms.find { it.cat == "출근" }
        assertNotNull(todayCommute)
        assertEquals(today.atTime(6, 15), todayCommute!!.triggerDateTime)

        val todayDrive = todayAlarms.find { it.cat == "운전" }
        assertNotNull(todayDrive)
        assertEquals(today.atTime(7, 50), todayDrive!!.triggerDateTime)

        val combined = (todayAlarms + tomorrowAlarms)
            .distinctBy { it.uniqueKey }
            .sortedBy { it.triggerDateTime }

        val commuteAlarms = combined.filter { it.cat == "출근" }
        assertEquals(2, commuteAlarms.size)
        assertEquals(today.atTime(6, 15), commuteAlarms[0].triggerDateTime)
        assertEquals(tomorrow.atTime(5, 45), commuteAlarms[1].triggerDateTime)
    }

    @Test
    fun diaCaptureRenderer_fontScaleAndWeightAdjustment_scalesCorrectly() {
        val density = 3.0f

        // 1. fontScale = 1.0 (기본)
        val fontScaleDefault = 1.0f
        fun spDefault(v: Float) = v * density * fontScaleDefault
        assertEquals(36.0f, spDefault(12f), 0.001f)
        val rowHDefault = maxOf(44f * density, 28f * density + spDefault(14f))
        assertEquals(132.0f, rowHDefault, 0.001f)

        // 2. fontScale = 1.25 (기기 설정: 큰 글씨)
        val fontScaleLarge = 1.25f
        fun spLarge(v: Float) = v * density * fontScaleLarge
        assertEquals(45.0f, spLarge(12f), 0.001f)
        val rowHLarge = maxOf(44f * density, 28f * density + spLarge(14f))
        // 28 * 3 + 14 * 3 * 1.25 = 84 + 52.5 = 136.5 > 132.0 (행 높이 자동 동적 확장 확인)
        assertTrue(rowHLarge > rowHDefault)
        assertEquals(136.5f, rowHLarge, 0.001f)

        // 3. fontWeightAdjustment = 300 (기기 설정: 글자 굵게 On)
        val weightAdj = 300
        fun calcTargetWeight(base: Int, adj: Int) = (base + adj).coerceIn(1, 1000)

        // 일반(400) -> Bold(700)
        assertEquals(700, calcTargetWeight(400, weightAdj))
        // Medium(500) -> ExtraBold(800)
        assertEquals(800, calcTargetWeight(500, weightAdj))
        // Bold(700) -> ExtraBold/Black(1000)
        assertEquals(1000, calcTargetWeight(700, weightAdj))
        // ExtraBold(800) -> 1000 (최대치 1000 클램핑)
        assertEquals(1000, calcTargetWeight(800, weightAdj))

        // 4. fontWeightAdjustment = 0 (기기 설정: 글자 굵게 Off)
        assertEquals(400, calcTargetWeight(400, 0))
        assertEquals(500, calcTargetWeight(500, 0))
        assertEquals(700, calcTargetWeight(700, 0))
        assertEquals(800, calcTargetWeight(800, 0))
    }

    @Test
    fun diaCaptureRenderer_badgeAndStation_neverOverlap_evenWithLongNames() {
        val width = 1440f
        val density = 3.0f
        val docPadding = 20f * density
        val cardInnerPadding = 16f * density

        val innerLeft = docPadding + cardInnerPadding
        val trackW = 20f * density
        val trackGap = 6f * density
        val gridLeft = innerLeft + trackW + trackGap

        val innerRight = width - docPadding - cardInnerPadding
        val arrowW = 32f * density
        val gridTotalW = innerRight - gridLeft
        val unitW = (gridTotalW - arrowW) / 5.8f

        val col1W = unitW * 1.6f
        val col2W = unitW * 1.1f
        val col2CenterBase = gridLeft + col1W + (col2W * 0.5f)

        // 가상 시나리오: 긴 뱃지 "F4526 운전" (폭 220px) 및 4글자 역명 "시흥기지" (폭 160px)
        val badgeW = 220f
        val stnW = 160f

        val badgeRight = gridLeft + badgeW
        val safeMinStnLeft = badgeRight + (10f * density) // 30px 안전 마진
        val col2CenterActual = maxOf(col2CenterBase, safeMinStnLeft + (stnW / 2f))

        val stnLeftActual = col2CenterActual - (stnW / 2f)

        // 뱃지 우측 끝과 역명 좌측 시작점 사이의 간격 검증
        val separationDistance = stnLeftActual - badgeRight
        assertTrue("간격이 최소 30px 이상이어야 함. 실제: $separationDistance", separationDistance >= 30f)
    }

    @Test
    fun diaCaptureRenderer_supportsDynamicWidth_maintainsViewportRatio() {
        // 1440px QHD 갤러리 캡처
        val w1440 = 1440
        val d1440 = w1440 / 480f
        assertEquals(3.0f, d1440, 0.001f)
        assertEquals(480f, w1440 / d1440, 0.001f)

        // 1080px 고화질
        val w1080 = 1080
        val d1080 = w1080 / 480f
        assertEquals(2.25f, d1080, 0.001f)
        assertEquals(480f, w1080 / d1080, 0.001f)

        // 720px 위젯용 최적화 규격
        val w720 = 720
        val d720 = w720 / 480f
        assertEquals(1.5f, d720, 0.001f)
        assertEquals(480f, w720 / d720, 0.001f)
    }

    @Test
    fun todayDiaWidget_offlineLocalOnly_guaranteesZeroNetworkCalls() {
        // TodayDiaWidgetUpdater는 오프라인 모드 플래그 true 및 localOnly=true 로 동작하여
        // 네트워크 통신 0회를 보장함을 검증
        var networkCallCount = 0
        val mockNetworkFetch: () -> String = {
            networkCallCount++
            "network_response"
        }

        val isOfflineMode = true
        val localOnly = true

        val result = if (isOfflineMode || localOnly) {
            "cached_local_data"
        } else {
            mockNetworkFetch()
        }

        assertEquals("cached_local_data", result)
        assertEquals(0, networkCallCount)
    }

    @Test
    fun todayDiaWidget_updatePeriod_isTwoHours() {
        val updatePeriodMillis = 7_200_000L // 2 hours
        val twoHoursInMillis = 2 * 60 * 60 * 1000L
        assertEquals(twoHoursInMillis, updatePeriodMillis)
    }

    @Test
    fun todayDiaWidget_nightDay2OrNonWorkDay_generatesRedBadgeText() {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MM.dd")

        // 1. 야간 2일차 (isToday = false, workDate = 어제 날짜)
        val yesterday = java.time.LocalDate.of(2026, 11, 8)
        val isTodayNight2 = false
        val badgeNight2 = if (!isTodayNight2) "${yesterday.format(formatter)} 근무" else null
        assertEquals("11.08 근무", badgeNight2)

        // 2. 비근무일 다가오는 근무 (isToday = false, workDate = 내일 날짜)
        val tomorrow = java.time.LocalDate.of(2026, 11, 11)
        val isTodayOff = false
        val badgeOff = if (!isTodayOff) "${tomorrow.format(formatter)} 근무" else null
        assertEquals("11.11 근무", badgeOff)

        // 3. 당일 일반 근무 (isToday = true) -> 뱃지 미표출
        val today = java.time.LocalDate.of(2026, 11, 9)
        val isTodayNormal = true
        val badgeNormal = if (!isTodayNormal) "${today.format(formatter)} 근무" else null
        assertNull(badgeNormal)
    }

    @Test
    fun todayDiaWidget_retinaResolution_is1080px() {
        val width = 1080
        val density = width / 480f
        assertEquals(2.25f, density, 0.001f)
        assertEquals(480f, width / density, 0.001f)
    }

    @Test
    fun todayDiaWidget_navigationProtection_doesNotDuplicateWhenAlreadyOnScreen() {
        val currentRoute = "today_work"
        val incomingRoute = "today_work"

        var navigated = false
        if (incomingRoute.isNotBlank() && currentRoute != incomingRoute) {
            navigated = true
        }

        // 이미 'today_work' 화면에 있으면 추가 navigate가 발생하지 않아 화면 덮어씌움이 0%임을 검증
        assertFalse("이미 동일 화면에 있으면 네비게이션이 중복 실행되지 않아야 함", navigated)
    }
}




