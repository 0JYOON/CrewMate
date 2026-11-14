package com.example.crewmate.alarm

enum class AlarmDisplayMode(val title: String) {
    NOTIFICATION_ONLY("알림창"),
    POPUP("팝업"),
    FULL_SCREEN("전체 화면")
}

enum class AlarmSoundMode(val title: String) {
    SOUND("소리"),
    VIBRATION("진동"),
    SILENT("무음")
}

data class WorkAlarmSettings(
    val alarmEnabled: Boolean = false,
    val displayMode: AlarmDisplayMode = AlarmDisplayMode.NOTIFICATION_ONLY,
    val soundMode: AlarmSoundMode = AlarmSoundMode.SOUND,

    // Rule 0-A: Attendance (출무 N분 전)
    val attendanceEnabled: Boolean = true,
    val attendanceLeadMinutes: Int = 5,

    // Rule 0: Commute Start (출근 N분 전)
    val commuteEnabled: Boolean = true,
    val commuteLeadMinutes: Int = 60,

    // Rule 1: Driving (운전 N분 전)
    val driveEnabled: Boolean = true,
    val driveLeadMinutes: Int = 10,
    val driveCheckGamsi: Boolean = true,
    val driveSkipIfPrepWithinMinutes: Boolean = true,
    val drivePrepThresholdMinutes: Int = 30,
    val driveSkipIfDeadheadWithinMinutes: Boolean = true,
    val driveDeadheadThresholdMinutes: Int = 10,

    // Rule 2: Prep (출고준비 N분 전)
    val prepEnabled: Boolean = true,
    val prepLeadMinutes: Int = 10,

    // Rule 3: Deadhead (편승 N분 전)
    val deadheadEnabled: Boolean = true,
    val deadheadLeadMinutes: Int = 10,
    val deadheadSkipIfPrevDuty: Boolean = true,
    val deadheadPrevThresholdMinutes: Int = 30,
    val deadheadSkipIfNextDuty: Boolean = false,
    val deadheadNextThresholdMinutes: Int = 20,

    // Rule 4: Shunting (입환 N분 전)
    val shuntingEnabled: Boolean = true,
    val shuntingLeadMinutes: Int = 10,
    val shuntingSkipIfPrevDuty: Boolean = true,
    val shuntingPrevThresholdMinutes: Int = 10,

    // Rule 5: Sandwiched Drive exception (운전-감시-운전 예외)
    val skipSandwichedDrive: Boolean = true
)
