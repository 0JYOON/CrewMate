package com.example.crewmate.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.crewmate.MainActivity
import com.example.crewmate.R
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.navigation.Routes
import com.example.crewmate.schedule.DeviceEvent
import com.example.crewmate.schedule.WorkPatternSettings
import com.example.crewmate.schedule.getDeviceCalendarEvents
import com.example.crewmate.schedule.toDomain
import com.example.crewmate.ui.theme.isDarkThemeActive
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 월별 승무근무표 위젯 전용 로컬 DB 업데이터.
 * Xcrew 원격 통신 없이 100% 로컬 Room DB 데이터만 사용하여 오프라인에서도 0.01초 만에 갱신합니다.
 */
object MonthlyScheduleWidgetUpdater {

    private const val PREFS_WIDGET = "crewmate_widget_prefs"
    private const val KEY_TARGET_YM = "monthly_widget_ym"

    fun getTargetMonth(context: Context): YearMonth {
        val prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TARGET_YM, null)
        return runCatching {
            if (!raw.isNullOrBlank()) YearMonth.parse(raw) else YearMonth.now()
        }.getOrDefault(YearMonth.now())
    }

    fun shiftTargetMonth(context: Context, offset: Long) {
        val next = getTargetMonth(context).plusMonths(offset)
        context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
            .edit().putString(KEY_TARGET_YM, next.toString()).apply()
    }

    fun resetTargetMonth(context: Context) {
        context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
            .edit().putString(KEY_TARGET_YM, YearMonth.now().toString()).apply()
    }

    suspend fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val componentName = ComponentName(appContext, MonthlyScheduleWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) return

        appWidgetIds.forEach { appWidgetId ->
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceAtLeast(200)
            val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 360).coerceAtLeast(220)
            val bodyHDp = (hDp - 46).coerceAtLeast(160)
            val targetHeight = (1080f * bodyHDp / wDp).toInt().coerceIn(720, 2600)

            val views = buildRemoteViews(appContext, targetHeight)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    suspend fun buildRemoteViews(context: Context, minTotalHeight: Int = 0): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_monthly_schedule)
        val isDark = context.isDarkThemeActive()

        // 앱 내 테마 설정에 맞춰 배경/아이콘/헤더 텍스트 색상 명시 적용 (시스템 야간 모드 불일치 방지)
        views.setInt(
            R.id.widget_monthly_root,
            "setBackgroundResource",
            if (isDark) R.drawable.widget_card_bg_dark else R.drawable.widget_card_bg_light
        )
        views.setImageViewResource(
            R.id.widget_monthly_refresh_btn,
            if (isDark) R.drawable.ic_widget_refresh_dark else R.drawable.ic_widget_refresh_light
        )
        val primaryTextColor = Color.parseColor(if (isDark) "#F8FAFC" else "#0F172A")
        val secondaryTextColor = Color.parseColor(if (isDark) "#94A3B8" else "#64748B")
        views.setTextColor(R.id.widget_monthly_title, primaryTextColor)
        views.setTextColor(R.id.widget_monthly_prev_btn, primaryTextColor)
        views.setTextColor(R.id.widget_monthly_next_btn, primaryTextColor)
        views.setTextColor(R.id.widget_monthly_empty_message, secondaryTextColor)

        // 1. 본체 터치 시 승무근무표(Routes.Schedule) 화면으로 전환 (singleTask 중복 방지)
        val appIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("target_route", Routes.Schedule)
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            10,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_monthly_root, appPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_monthly_image, appPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_monthly_empty_view, appPendingIntent)

        // 2. 전월 / 오늘 / 익월 / 새로고침 버튼 바인딩
        fun makeActionPendingIntent(actionStr: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, MonthlyScheduleWidgetProvider::class.java).apply {
                action = actionStr
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        views.setOnClickPendingIntent(
            R.id.widget_monthly_prev_btn,
            makeActionPendingIntent(MonthlyScheduleWidgetProvider.ACTION_PREV_MONTH, 11)
        )
        views.setOnClickPendingIntent(
            R.id.widget_monthly_title,
            makeActionPendingIntent(MonthlyScheduleWidgetProvider.ACTION_TODAY_MONTH, 12)
        )
        views.setOnClickPendingIntent(
            R.id.widget_monthly_next_btn,
            makeActionPendingIntent(MonthlyScheduleWidgetProvider.ACTION_NEXT_MONTH, 13)
        )
        views.setOnClickPendingIntent(
            R.id.widget_monthly_refresh_btn,
            makeActionPendingIntent(MonthlyScheduleWidgetProvider.ACTION_REFRESH_MONTHLY_WIDGET, 14)
        )

        // 3. 로컬 DB에서 대상 월 및 인접 월 근무표 + 메모 + 기기 캘린더 일정 조회
        val targetYm = getTargetMonth(context)
        views.setTextViewText(R.id.widget_monthly_title, "${targetYm.year}년 ${targetYm.monthValue}월 근무표")

        val ymFmt = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT)
        val curYmStr = targetYm.format(ymFmt)
        val prevYmStr = targetYm.minusMonths(1).format(ymFmt)
        val nextYmStr = targetYm.plusMonths(1).format(ymFmt)

        val db = CrewMateDatabase.getInstance(context)
        val curEntities = db.workScheduleDao().getSchedulesForMonth(curYmStr)
        val prevEntities = db.workScheduleDao().getSchedulesForMonth(prevYmStr)
        val nextEntities = db.workScheduleDao().getSchedulesForMonth(nextYmStr)

        val allDomainSchedules = (prevEntities + curEntities + nextEntities).map { it.toDomain() }

        val memosByDate: Map<LocalDate, List<String>> = runCatching {
            val allMemos = db.memoDao().getAllMemos()
            val prefixDash = targetYm.format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT))
            val grouped = mutableMapOf<LocalDate, MutableList<String>>()
            allMemos.forEach { m ->
                if (m.date.startsWith(prefixDash) && m.content.isNotBlank()) {
                    val d = runCatching { LocalDate.parse(m.date) }.getOrNull()
                    if (d != null) {
                        grouped.getOrPut(d) { mutableListOf() }.add(m.content.trim())
                    }
                }
            }
            grouped
        }.getOrDefault(emptyMap())

        val deviceEventsByDate: Map<LocalDate, List<DeviceEvent>> = runCatching {
            val hasCalendarPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCalendarPerm) getDeviceCalendarEvents(context, targetYm) else emptyMap()
        }.getOrDefault(emptyMap())

        // 4. 1080px 고화질 달력 비트맵 렌더링 (근무표가 비어 있어도 달력 프레임 + 순환패턴 표시)
        val patternData = WorkPatternSettings.load(context)
        val bitmap = MonthlyScheduleRenderer.renderToBitmap(
            context = context,
            yearMonth = targetYm,
            schedules = allDomainSchedules,
            memosByDate = memosByDate,
            deviceEventsByDate = deviceEventsByDate,
            patternData = patternData,
            width = 1080,
            minTotalHeight = minTotalHeight,
            isDark = isDark
        )

        views.setImageViewBitmap(R.id.widget_monthly_image, bitmap)
        views.setViewVisibility(R.id.widget_monthly_image, View.VISIBLE)
        views.setViewVisibility(R.id.widget_monthly_empty_view, View.GONE)

        return views
    }
}
