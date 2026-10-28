package com.example.crewmate.alarm

import android.content.Context
import android.content.SharedPreferences

class WorkAlarmSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("crewmate_alarm_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): WorkAlarmSettings {
        val displayModeName = prefs.getString("display_mode", AlarmDisplayMode.POPUP.name)
        val soundModeName = prefs.getString("sound_mode", AlarmSoundMode.SOUND.name)

        val displayMode = AlarmDisplayMode.entries.find { it.name == displayModeName } ?: AlarmDisplayMode.POPUP
        val soundMode = AlarmSoundMode.entries.find { it.name == soundModeName } ?: AlarmSoundMode.SOUND

        return WorkAlarmSettings(
            alarmEnabled = prefs.getBoolean("alarm_enabled", false),
            displayMode = displayMode,
            soundMode = soundMode,
            attendanceEnabled = prefs.getBoolean("attendance_enabled", true),
            attendanceLeadMinutes = prefs.getInt("attendance_lead_min", 5),
            commuteEnabled = prefs.getBoolean("commute_enabled", true),
            commuteLeadMinutes = prefs.getInt("commute_lead_min", 60),
            driveEnabled = prefs.getBoolean("drive_enabled", true),
            driveLeadMinutes = prefs.getInt("drive_lead_min", 10),
            driveCheckGamsi = prefs.getBoolean("drive_check_gamsi", true),
            driveSkipIfPrepWithinMinutes = prefs.getBoolean("drive_skip_prep", true),
            drivePrepThresholdMinutes = prefs.getInt("drive_prep_thresh_min", 30),
            driveSkipIfDeadheadWithinMinutes = prefs.getBoolean("drive_skip_deadhead", true),
            driveDeadheadThresholdMinutes = prefs.getInt("drive_deadhead_thresh_min", 10),
            prepEnabled = prefs.getBoolean("prep_enabled", true),
            prepLeadMinutes = prefs.getInt("prep_lead_min", 10),
            deadheadEnabled = prefs.getBoolean("deadhead_enabled", true),
            deadheadLeadMinutes = prefs.getInt("deadhead_lead_min", 10),
            deadheadSkipIfPrevDuty = prefs.getBoolean("deadhead_skip_prev", true),
            deadheadPrevThresholdMinutes = prefs.getInt("deadhead_prev_thresh_min", 30),
            deadheadSkipIfNextDuty = prefs.getBoolean("deadhead_skip_next", false),
            deadheadNextThresholdMinutes = prefs.getInt("deadhead_next_thresh_min", 20),
            shuntingEnabled = prefs.getBoolean("shunting_enabled", true),
            shuntingLeadMinutes = prefs.getInt("shunting_lead_min", 10),
            shuntingSkipIfPrevDuty = prefs.getBoolean("shunting_skip_prev", true),
            shuntingPrevThresholdMinutes = prefs.getInt("shunting_prev_thresh_min", 10),
            skipSandwichedDrive = prefs.getBoolean("skip_sandwiched_drive", true)
        )
    }

    fun saveSettings(settings: WorkAlarmSettings) {
        prefs.edit().apply {
            putBoolean("alarm_enabled", settings.alarmEnabled)
            putString("display_mode", settings.displayMode.name)
            putString("sound_mode", settings.soundMode.name)
            putBoolean("attendance_enabled", settings.attendanceEnabled)
            putInt("attendance_lead_min", settings.attendanceLeadMinutes)
            putBoolean("commute_enabled", settings.commuteEnabled)
            putInt("commute_lead_min", settings.commuteLeadMinutes)
            putBoolean("drive_enabled", settings.driveEnabled)
            putInt("drive_lead_min", settings.driveLeadMinutes)
            putBoolean("drive_check_gamsi", settings.driveCheckGamsi)
            putBoolean("drive_skip_prep", settings.driveSkipIfPrepWithinMinutes)
            putInt("drive_prep_thresh_min", settings.drivePrepThresholdMinutes)
            putBoolean("drive_skip_deadhead", settings.driveSkipIfDeadheadWithinMinutes)
            putInt("drive_deadhead_thresh_min", settings.driveDeadheadThresholdMinutes)
            putBoolean("prep_enabled", settings.prepEnabled)
            putInt("prep_lead_min", settings.prepLeadMinutes)
            putBoolean("deadhead_enabled", settings.deadheadEnabled)
            putInt("deadhead_lead_min", settings.deadheadLeadMinutes)
            putBoolean("deadhead_skip_prev", settings.deadheadSkipIfPrevDuty)
            putInt("deadhead_prev_thresh_min", settings.deadheadPrevThresholdMinutes)
            putBoolean("deadhead_skip_next", settings.deadheadSkipIfNextDuty)
            putInt("deadhead_next_thresh_min", settings.deadheadNextThresholdMinutes)
            putBoolean("shunting_enabled", settings.shuntingEnabled)
            putInt("shunting_lead_min", settings.shuntingLeadMinutes)
            putBoolean("shunting_skip_prev", settings.shuntingSkipIfPrevDuty)
            putInt("shunting_prev_thresh_min", settings.shuntingPrevThresholdMinutes)
            putBoolean("skip_sandwiched_drive", settings.skipSandwichedDrive)
            apply()
        }
    }

    fun getBypassedAlarmKeys(dateStr: String): Set<String> {
        return prefs.getStringSet("bypassed_alarms_$dateStr", emptySet()) ?: emptySet()
    }

    fun setAlarmBypassed(dateStr: String, alarmKey: String, bypassed: Boolean) {
        val current = prefs.getStringSet("bypassed_alarms_$dateStr", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (bypassed) {
            current.add(alarmKey)
        } else {
            current.remove(alarmKey)
        }
        prefs.edit().putStringSet("bypassed_alarms_$dateStr", current).apply()
    }
}