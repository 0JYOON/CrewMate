package com.example.crewmate.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.crewmate.MainActivity
import com.example.crewmate.R
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.schedule.CrewWorkStatusRepository
import com.example.crewmate.schedule.DiaDetailRepository
import com.example.crewmate.schedule.TodayWorkRepository
import com.example.crewmate.schedule.WorkScheduleParser
import com.example.crewmate.schedule.WorkScheduleRepositoryImpl
import com.example.crewmate.ui.theme.isDarkThemeActive
import com.example.crewmate.util.DiaCaptureItem
import com.example.crewmate.util.DiaCaptureOption
import com.example.crewmate.util.DiaCaptureRenderer
import com.example.crewmate.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * '오늘의 근무' 다이아 홈 화면 위젯 갱신 전용 엔진.
 *
 * [절대 안전 원칙]
 * 1. Xcrew 네트워크 통신이나 웹뷰 세션을 일체 호출하지 않습니다 (100% 로컬 Room DB 캐시 전용).
 * 2. 오프라인 모드(is_offline_mode == true) 또는 네트워크 단절 상태에서도 안전하게 로컬 다이아를 렌더링합니다.
 * 3. 1080px FHD+ 레티나 고화질 해상도로 선명하게 렌더링합니다.
 * 4. 야간 2일차 또는 비근무일 다이아의 경우 [MM.dd 근무] 빨간색 뱃지를 표출합니다.
 */
object TodayDiaWidgetUpdater {

    private const val TAG = "TodayDiaWidgetUpdater"

    private data class LocalDiaLoadResult(
        val items: List<DiaCaptureItem>,
        val isToday: Boolean,
        val workDate: LocalDate
    )

    suspend fun updateAllWidgets(context: Context) = withContext(Dispatchers.IO) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, TodayDiaWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isEmpty()) return@withContext

            val remoteViews = buildRemoteViews(context)
            for (widgetId in appWidgetIds) {
                appWidgetManager.updateAppWidget(widgetId, remoteViews)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widgets", e)
        }
    }

    suspend fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) = withContext(Dispatchers.IO) {
        try {
            val remoteViews = buildRemoteViews(context)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widget $appWidgetId", e)
        }
    }

    private suspend fun buildRemoteViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today_dia)
        val isDark = context.isDarkThemeActive()

        // 앱 내 테마 설정에 맞춰 배경/아이콘/텍스트 색상 명시 적용 (시스템 야간 모드 불일치 방지)
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (isDark) R.drawable.widget_card_bg_dark else R.drawable.widget_card_bg_light
        )
        views.setImageViewResource(
            R.id.widget_refresh_button,
            if (isDark) R.drawable.ic_widget_refresh_dark else R.drawable.ic_widget_refresh_light
        )
        views.setTextColor(
            R.id.widget_empty_title,
            android.graphics.Color.parseColor(if (isDark) "#F8FAFC" else "#0F172A")
        )
        views.setTextColor(
            R.id.widget_empty_message,
            android.graphics.Color.parseColor(if (isDark) "#94A3B8" else "#64748B")
        )

        // 1. 클릭 인텐트 바인딩
        // 1-1. 위젯 본체 클릭 시 메인 앱(TodayWork) 실행 (singleTask 및 SINGLE_TOP으로 중복 덮어씌움 원천 차단)
        val appIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("target_route", Routes.TodayWork)
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_dia_image, appPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_empty_view, appPendingIntent)

        // 1-2. 우측 상단 새로고침 버튼 터치 시 로컬 캐시 즉시 재조회
        val refreshIntent = Intent(context, TodayDiaWidgetProvider::class.java).apply {
            action = TodayDiaWidgetProvider.ACTION_REFRESH_WIDGET
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

        // 2. 순수 로컬 Room DB에서 다이아 조회 (Local-Only, 외부 네트워크 0회)
        val loadResult = loadLocalDiaItems(context)

        if (loadResult == null || loadResult.items.isEmpty()) {
            // 오늘 근무가 없거나 승무 다이아가 없는 날 (휴일, 연차, 비번 등)
            views.setViewVisibility(R.id.widget_dia_image, View.GONE)
            views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty_message, "오늘 예정된 승무 다이아가 없습니다.")
            return views
        }

        // 3. DiaCaptureRenderer를 통한 1080px 고화질 레티나 비트맵 렌더링
        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val showPrepDivider = prefs.getBoolean("show_prep_divider", true)

        // 야간 2일차 또는 비근무일인 경우 "MM.dd 근무" 붉은색 뱃지 텍스트 생성
        val badgeText = if (!loadResult.isToday) {
            "${loadResult.workDate.format(DateTimeFormatter.ofPattern("MM.dd"))} 근무"
        } else null

        // 위젯용 가로폭: 1080px (FHD+ 1:1 레티나 고화질 규격, 글씨/선명도 극대화)
        val bitmap = DiaCaptureRenderer.renderToBitmap(
            context = context,
            headerTitle = "승무 다이아 상세",
            workDate = LocalDate.now(),
            dias = loadResult.items,
            option = DiaCaptureOption.TIMELINE_SUMMARY,
            width = 1080,
            isDark = isDark,
            showPrepDivider = showPrepDivider,
            badgeText = badgeText,
            reserveTopEndSpaceDp = 38f
        )

        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_dia_image, bitmap)
            views.setViewVisibility(R.id.widget_dia_image, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_view, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_dia_image, View.GONE)
            views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty_message, "다이아 정보를 표시할 수 없습니다.")
        }

        return views
    }

    /**
     * 100% 로컬 Room DB에서만 오늘/익일 유효 다이아를 조회합니다.
     * Xcrew 원격 통신은 완전히 격리(차단)됩니다.
     */
    private suspend fun loadLocalDiaItems(context: Context): LocalDiaLoadResult? {
        return try {
            val db = CrewMateDatabase.getInstance(context)
            val session = XcrewWebViewSession()
            val dummyDataSource = XcrewWebViewXhrDataSource(session, XcrewWorkScheduleParser())
            val scheduleRepo = WorkScheduleRepositoryImpl(
                dummyDataSource,
                WorkScheduleParser(),
                db.workScheduleDao(),
                isOfflineModeProvider = { true }
            )
            val diaDetailRepo = DiaDetailRepository(
                dummyDataSource,
                db.diaDetailDao(),
                db.diaCensusDao(),
                isOfflineModeProvider = { true }
            )
            val crewWorkStatusRepo = CrewWorkStatusRepository(
                dummyDataSource,
                db.crewAssignmentDao(),
                isOfflineModeProvider = { true }
            )
            val todayRepo = TodayWorkRepository(
                workScheduleRepository = scheduleRepo,
                diaDetailRepository = diaDetailRepo,
                dataSource = dummyDataSource,
                crewWorkStatusRepository = crewWorkStatusRepo,
                xcrewSession = session,
                appContext = context.applicationContext,
                isOfflineModeProvider = { true }
            )

            val todayResult = todayRepo.getTodayOrNextWork(localOnly = true) ?: return null
            if (todayResult.dias.isEmpty()) return null

            // 세그먼트가 실제로 존재하는 다이아만 필터링
            val validDias = todayResult.dias.filter { it.detail.segments.isNotEmpty() }
            if (validDias.isEmpty()) return null

            val items = validDias.map { DiaCaptureItem(it.schedule, it.detail) }
            LocalDiaLoadResult(
                items = items,
                isToday = todayResult.isToday,
                workDate = todayResult.workDate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load local dia items for widget", e)
            null
        }
    }
}
