package com.example.crewmate.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * '오늘의 근무' 승무 다이아 전용 홈 화면 위젯 프로바이더.
 */
class TodayDiaWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "TodayDiaWidgetProvider"
        const val ACTION_REFRESH_WIDGET = "kr.crewmate.app.action.REFRESH_WIDGET"
        const val ACTION_UPDATE_TODAY_WIDGET = "kr.crewmate.app.action.UPDATE_TODAY_WIDGET"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodayDiaWidgetUpdater.updateAllWidgets(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onUpdate", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodayDiaWidgetUpdater.updateWidget(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onAppWidgetOptionsChanged", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_WIDGET,
            ACTION_UPDATE_TODAY_WIDGET,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        TodayDiaWidgetUpdater.updateAllWidgets(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onReceive (${intent.action})", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
