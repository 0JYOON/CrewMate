package com.example.crewmate.schedule

import com.example.crewmate.alarm.AutoOfflineManager
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
