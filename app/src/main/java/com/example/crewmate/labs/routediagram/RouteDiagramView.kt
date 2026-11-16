package com.example.crewmate.labs.routediagram

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.schedule.WorkSchedule
import com.example.crewmate.ui.theme.getAppThemeMode
import com.example.crewmate.ui.theme.isDarkThemeActive
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private class DiagramJsBridge(
    private val onHeight: (Int) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onHeightMeasured(heightCssPx: Int) {
        if (heightCssPx > 0) {
            mainHandler.post { onHeight(heightCssPx) }
        }
    }
}

private fun RouteDiagramModel.toDiagramJson(): String {
    val stepsArr = JSONArray()
    steps.forEach { step ->
        val obj = JSONObject()
        when (step) {
            is RouteStep.Start -> {
                obj.put("type", "START")
                obj.put("id", step.id)
                obj.put("station", step.station)
                obj.put("dptTm", step.dptTm)
            }
            is RouteStep.Run -> {
                obj.put("type", "RUN")
                obj.put("id", step.id)
                obj.put("trnNo", step.trnNo)
                obj.put("fromStation", step.fromStation)
                obj.put("toStation", step.toStation)
                obj.put("dptTm", step.dptTm)
                obj.put("arvTm", step.arvTm)
                obj.put("isDeadhead", step.isDeadhead)
                obj.put("isWatch", step.isWatch)
                obj.put("isShunt", step.isShunt)
            }
            is RouteStep.Dwell -> {
                obj.put("type", "DWELL")
                obj.put("id", step.id)
                obj.put("station", step.station)
                obj.put("label", step.label)
                obj.put("isBlueCore", step.isBlueCore)
                obj.put("dptTm", step.dptTm)
                obj.put("arvTm", step.arvTm)
                obj.put("isWatchOrPrepNode", step.isWatchOrPrepNode)
                obj.put("displayStartTime", step.displayStartTime)
                obj.put("displayEndTime", step.displayEndTime)
            }
            is RouteStep.End -> {
                obj.put("type", "END")
                obj.put("id", step.id)
                obj.put("station", step.station)
                obj.put("arvTm", step.arvTm)
            }
        }
        stepsArr.put(obj)
    }
    return JSONObject().apply {
        put("diaNo", diaNo)
        put("startTime", startTime)
        put("endTime", endTime)
        put("stations", JSONArray(stations))
        put("steps", stepsArr)
    }.toString()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouteDiagramView(
    schedule: WorkSchedule,
    detail: DiaDetailResponse,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkMode = context.isDarkThemeActive(context.getAppThemeMode())
    var showSummaryDetails by remember { mutableStateOf(false) }

    val master = remember { RailwayMasterRepository.loadMergedMaster(context) }
    val model = remember(detail, schedule, master) {
        RouteDiagramBuilder.build(detail, schedule, master)
    }

    if (model.stations.isEmpty()) {
        Text(
            text = "행로도를 그릴 역 구간 정보가 없습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }

    val diagramJsonData = remember(model) { model.toDiagramJson() }
    var measuredHeightDp by remember { mutableIntStateOf(240) }
    var isPageReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(diagramJsonData, isDarkMode, isPageReady) {
        val wv = webViewRef
        if (wv != null && isPageReady) {
            wv.evaluateJavascript("renderDiagram($diagramJsonData, $isDarkMode)", null)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(measuredHeightDp.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    @Suppress("DEPRECATION")
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                    }
                    addJavascriptInterface(
                        DiagramJsBridge { h -> measuredHeightDp = h.coerceAtLeast(120) },
                        "AndroidDiagramBridge"
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isPageReady = true
                            view?.evaluateJavascript("renderDiagram($diagramJsonData, $isDarkMode)", null)
                        }
                    }
                    webViewRef = this
                    loadUrl("file:///android_asset/diagram.html")
                }
            },
            update = { view ->
                webViewRef = view
            }
        )

        // 하단 접이식 사업시간/거리 요약 보기 (타임라인과 동일 항목 전체 표출)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSummaryDetails = !showSummaryDetails }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "사업시간 / 거리 요약 보기",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D9488)
            )
            Text(
                text = if (showSummaryDetails) "접기 ▲" else "펼치기 ▼",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(visible = showSummaryDetails) {
            val runDays = detail.extractRunDays()
            val effectiveTerm = detail.extractEffectiveTermStr()
            val prepTime = detail.summaries.find { it.pjtHrDvNm?.contains("준비") == true }?.pjtTnum ?: 0
            val driveTime = detail.summaries.filter { it.pjtHrDvNm?.contains("운전") == true || it.pjtHrDvNm?.contains("승무") == true }.sumOf { it.pjtTnum ?: 0 }
            val monitorTime = detail.summaries.find { it.pjtHrDvNm?.contains("감시") == true }?.pjtTnum ?: 0
            val restTime = detail.summaries.find { it.pjtHrDvNm?.contains("휴게") == true }?.pjtTnum ?: 0
            val transferTime = detail.summaries.find { it.pjtHrDvNm?.contains("승계") == true }?.pjtTnum ?: 0
            val totalTime = detail.summaries.firstOrNull()?.totWrkTnum ?: model.totalTimeMinutes
            val totalDist = detail.summaries.firstOrNull()?.totPjtDst ?: model.totalDistanceKm
            val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
            fun Int.toHHmm() = String.format(Locale.US, "%02d:%02d", this / 60, this % 60)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("운용요일", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(runDays, fontSize = 12.sp, color = secondaryColor)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("시행기간", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(effectiveTerm, fontSize = 12.sp, color = secondaryColor)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                listOf(
                    "준비" to prepTime.toHHmm(),
                    "운전/승무" to driveTime.toHHmm(),
                    "감시" to monitorTime.toHHmm(),
                    "휴게" to restTime.toHHmm(),
                    "승계" to transferTime.toHHmm()
                ).forEach { (k, v) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, fontSize = 12.sp, color = secondaryColor)
                        Text(v, fontSize = 12.sp, color = secondaryColor)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("총사업시간", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(totalTime.toHHmm(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("총사업거리", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(String.format(Locale.US, "%.1f km", totalDist), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
