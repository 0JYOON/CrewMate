package com.example.crewmate.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 홈 화면에 '월별 승무근무표' 달력을 표시하는 AppWidgetProvider.
 */
class MonthlyScheduleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_MONTHLY_WIDGET = "kr.crewmate.app.action.REFRESH_MONTHLY_WIDGET"
        const val ACTION_UPDATE_MONTHLY_WIDGET = "kr.crewmate.app.action.UPDATE_MONTHLY_WIDGET"
        const val ACTION_PREV_MONTH = "kr.crewmate.app.action.MONTHLY_WIDGET_PREV"
        const val ACTION_NEXT_MONTH = "kr.crewmate.app.action.MONTHLY_WIDGET_NEXT"
        const val ACTION_TODAY_MONTH = "kr.crewmate.app.action.MONTHLY_WIDGET_TODAY"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MonthlyScheduleWidgetUpdater.updateAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MonthlyScheduleWidgetUpdater.updateAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_MONTHLY_WIDGET,
            ACTION_UPDATE_MONTHLY_WIDGET,
            ACTION_PREV_MONTH,
            ACTION_NEXT_MONTH,
            ACTION_TODAY_MONTH,
            Intent.ACTION_CONFIGURATION_CHANGED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        when (intent.action) {
                            ACTION_PREV_MONTH -> MonthlyScheduleWidgetUpdater.shiftTargetMonth(context, -1)
                            ACTION_NEXT_MONTH -> MonthlyScheduleWidgetUpdater.shiftTargetMonth(context, 1)
                            ACTION_TODAY_MONTH -> MonthlyScheduleWidgetUpdater.resetTargetMonth(context)
                        }
                        MonthlyScheduleWidgetUpdater.updateAllWidgets(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
