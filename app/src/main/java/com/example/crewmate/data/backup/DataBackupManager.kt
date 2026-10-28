package com.example.crewmate.data.backup

import android.content.Context
import android.util.Log
import com.example.crewmate.database.AnnualLeaveEntity
import com.example.crewmate.database.CarriedOverLeaveEntity
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.database.MemoEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class BackupRestoreResult(
    val memoCount: Int = 0,
    val annualLeaveCount: Int = 0,
    val carriedOverCount: Int = 0,
    val preferenceCount: Int = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

object DataBackupManager {
    private const val TAG = "DataBackupManager"
    private const val BACKUP_VERSION = 1
    private const val APP_IDENTIFIER = "CrewMate"

    // Sensitive preference keys that MUST NEVER be exported or overwritten via backup
    private val EXCLUDED_PREF_KEYS = setOf(
        "xcrew_password",
        "JSESSIONID",
        "cookie",
        "session",
        "credential",
        "token",
        "auto_offline_last_window_key"
    )

    private fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase()
        return EXCLUDED_PREF_KEYS.any { lower.contains(it) } ||
                lower.contains("password") ||
                lower.contains("secret") ||
                lower.contains("keystore")
    }

    suspend fun createBackupJson(context: Context): String {
        val db = CrewMateDatabase.getInstance(context)
        val root = JSONObject()

        // Metadata
        root.put("app", APP_IDENTIFIER)
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

        // 1. Memos
        val memos = db.memoDao().getAllMemos()
        val memoArray = JSONArray()
        memos.forEach { m ->
            val obj = JSONObject()
            obj.put("date", m.date)
            obj.put("content", m.content)
            memoArray.put(obj)
        }
        root.put("memos", memoArray)

        // 2. Annual Leaves
        val annualLeaves = db.leaveDao().getAllAnnualLeaves()
        val annualArray = JSONArray()
        annualLeaves.forEach { a ->
            val obj = JSONObject()
            obj.put("year", a.year)
            obj.put("totalDays", a.totalDays)
            annualArray.put(obj)
        }
        root.put("annualLeaves", annualArray)

        // 3. Carried Over Leaves
        val carriedOverLeaves = db.leaveDao().getAllCarriedOverLeaves()
        val carriedArray = JSONArray()
        carriedOverLeaves.forEach { c ->
            val obj = JSONObject()
            obj.put("days", c.days.toDouble())
            obj.put("endDate", c.endDate)
            carriedArray.put(obj)
        }
        root.put("carriedOverLeaves", carriedArray)

        // 4. SharedPreferences (Safe settings only)
        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val prefObj = JSONObject()
        prefs.all.forEach { (key, value) ->
            if (value != null && !isSensitiveKey(key)) {
                when (value) {
                    is Boolean -> prefObj.put(key, value)
                    is Int -> prefObj.put(key, value)
                    is Long -> prefObj.put(key, value)
                    is Float -> prefObj.put(key, value.toDouble())
                    is Double -> prefObj.put(key, value)
                    is String -> prefObj.put(key, value)
                }
            }
        }
        root.put("preferences", prefObj)

        Log.i(TAG, "Created backup JSON: ${memos.size} memos, ${annualLeaves.size} annual leaves, ${carriedOverLeaves.size} carried leaves, ${prefObj.length()} preferences.")
        return root.toString(2)
    }

    suspend fun restoreBackupJson(context: Context, jsonString: String): BackupRestoreResult {
        return try {
            val root = JSONObject(jsonString)
            val app = root.optString("app")
            if (app != APP_IDENTIFIER) {
                return BackupRestoreResult(isSuccess = false, errorMessage = "CrewMate 백업 파일 형식이 아닙니다.")
            }

            val db = CrewMateDatabase.getInstance(context)

            // 1. Memos
            var memoCount = 0
            if (root.has("memos")) {
                val memoArray = root.getJSONArray("memos")
                val memoList = mutableListOf<MemoEntity>()
                for (i in 0 until memoArray.length()) {
                    val obj = memoArray.getJSONObject(i)
                    val date = obj.getString("date")
                    val content = obj.getString("content")
                    memoList.add(MemoEntity(date = date, content = content))
                }
                if (memoList.isNotEmpty()) {
                    db.memoDao().insertMemos(memoList)
                    memoCount = memoList.size
                }
            }

            // 2. Annual Leaves
            var annualCount = 0
            if (root.has("annualLeaves")) {
                val annualArray = root.getJSONArray("annualLeaves")
                val annualList = mutableListOf<AnnualLeaveEntity>()
                for (i in 0 until annualArray.length()) {
                    val obj = annualArray.getJSONObject(i)
                    val year = obj.getInt("year")
                    val totalDays = obj.getInt("totalDays")
                    annualList.add(AnnualLeaveEntity(year = year, totalDays = totalDays))
                }
                if (annualList.isNotEmpty()) {
                    db.leaveDao().insertAnnualLeaves(annualList)
                    annualCount = annualList.size
                }
            }

            // 3. Carried Over Leaves
            var carriedCount = 0
            if (root.has("carriedOverLeaves")) {
                val carriedArray = root.getJSONArray("carriedOverLeaves")
                val carriedList = mutableListOf<CarriedOverLeaveEntity>()
                for (i in 0 until carriedArray.length()) {
                    val obj = carriedArray.getJSONObject(i)
                    val days = obj.getDouble("days").toFloat()
                    val endDate = obj.getString("endDate")
                    carriedList.add(CarriedOverLeaveEntity(days = days, endDate = endDate))
                }
                if (carriedList.isNotEmpty()) {
                    db.leaveDao().insertCarriedOverLeaves(carriedList)
                    carriedCount = carriedList.size
                }
            }

            // 4. SharedPreferences
            var prefCount = 0
            if (root.has("preferences")) {
                val prefObj = root.getJSONObject("preferences")
                val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                val keys = prefObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (isSensitiveKey(key)) continue

                    val value = prefObj.get(key)
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is String -> editor.putString(key, value)
                    }
                    prefCount++
                }
                editor.apply()
            }

            Log.i(TAG, "Restored backup JSON successfully: $memoCount memos, $annualCount annual, $carriedCount carried, $prefCount prefs.")
            BackupRestoreResult(
                memoCount = memoCount,
                annualLeaveCount = annualCount,
                carriedOverCount = carriedCount,
                preferenceCount = prefCount,
                isSuccess = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup", e)
            BackupRestoreResult(isSuccess = false, errorMessage = e.localizedMessage ?: "복원 중 알 수 없는 오류 발생")
        }
    }
}
