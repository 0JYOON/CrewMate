package com.example.crewmate.labs.colleaguecompare

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.YearMonth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrDayEntry(
    val day: Int,
    val diaNo: String,
    val startTime: String,
    val endTime: String
)

data class ScreenshotOcrResult(
    val name: String,
    val yearMonth: YearMonth,
    val entries: List<OcrDayEntry>,
    val rawText: String
)

/**
 * 온디바이스 ML Kit 한국어 OCR을 이용해 Xcrew 당월승무근무표 스크린샷에서
 * 연월·성명·일자별 다이아 및 출퇴근시각을 공간 좌표 기반으로 추출합니다.
 */
object ScreenshotScheduleOcr {
    private val YM_REGEX = Regex("""(20\d{2})\s*년\s*(\d{1,2})\s*월""")
    private val DAY_LABEL_REGEX = Regex("""^(\d{1,2})일$""")
    private val TIME_REGEX = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val DIA_REGEX = Regex("""~?\(?\d{3,5}\)?|연차|병가|공가|청원|교육|출장|휴가|휴무|휴일|비번|S""")

    suspend fun recognizeFromUri(
        context: Context,
        uri: Uri,
        fallbackMonth: YearMonth
    ): ScreenshotOcrResult {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val visionText = suspendCancellableCoroutine<Text> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { err ->
                    if (cont.isActive) cont.resumeWithException(err)
                }
        }
        return parseVisionText(visionText, fallbackMonth)
    }

    private data class BoxItem(val text: String, val rect: Rect) {
        val centerX: Int get() = (rect.left + rect.right) / 2
        val centerY: Int get() = (rect.top + rect.bottom) / 2
    }

    private fun parseVisionText(visionText: Text, fallbackMonth: YearMonth): ScreenshotOcrResult {
        val raw = visionText.text
        val ymMatch = YM_REGEX.find(raw)
        val ym = if (ymMatch != null) {
            val y = ymMatch.groupValues[1].toIntOrNull() ?: fallbackMonth.year
            val m = (ymMatch.groupValues[2].toIntOrNull() ?: fallbackMonth.monthValue).coerceIn(1, 12)
            YearMonth.of(y, m)
        } else {
            fallbackMonth
        }

        val lines = mutableListOf<BoxItem>()
        visionText.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val box = line.boundingBox ?: return@forEach
                val clean = line.text.trim()
                if (clean.isNotEmpty()) lines.add(BoxItem(clean, box))
            }
        }

        // 승무원 이름 추출 ('승무원' 라벨 오른쪽 동일 행에 있는 한글 이름)
        val crewLabel = lines.firstOrNull { it.text.contains("승무원") && !it.text.contains("당월") }
        val detectedName = if (crewLabel != null) {
            lines.filter {
                it !== crewLabel &&
                    kotlin.math.abs(it.centerY - crewLabel.centerY) <= crewLabel.rect.height() * 1.4 &&
                    it.rect.left >= crewLabel.rect.right - 20
            }.minByOrNull { it.rect.left }?.text?.trim()?.take(15).orEmpty()
        } else {
            ""
        }.ifEmpty { "수동 추가" }

        // '1일', '2일', ... 앵커 추출 (이전 달 끝/다음 달 시작 회색 날짜 제외)
        val rawDayAnchors = lines.mapNotNull { item ->
            val m = DAY_LABEL_REGEX.matchEntire(item.text.replace(" ", ""))
            val day = m?.groupValues?.get(1)?.toIntOrNull()
            if (day != null && day in 1..31) day to item else null
        }.sortedWith(compareBy({ it.second.centerY / 40 }, { it.second.centerX }))

        val monthLength = ym.lengthOfMonth()
        val orderedAnchors = mutableListOf<Pair<Int, BoxItem>>()
        var expectedNext = 1
        for ((day, item) in rawDayAnchors) {
            if (day == expectedNext && day <= monthLength) {
                orderedAnchors.add(day to item)
                expectedNext++
            } else if (expectedNext > 1 && day > expectedNext && day <= monthLength && day - expectedNext <= 3) {
                // 일부 날짜 숫자 OCR 누락 시 건너뛰고 계속 진행
                orderedAnchors.add(day to item)
                expectedNext = day + 1
            } else if (expectedNext > 20 && day < 10) {
                // 다음 달 1일..5일 회색 영역 도달 -> 중단
                break
            }
        }

        // 주(Row) Y좌표 간격 계산 및 열(Col) 너비 계산
        val distinctRowYs = orderedAnchors.map { it.second.centerY }.sorted()
        val rowGaps = distinctRowYs.zipWithNext { a, b -> b - a }.filter { it > 40 }
        val avgRowHeight = (rowGaps.minOrNull() ?: 160).coerceAtLeast(80)
        val colWidth = run {
            val minX = orderedAnchors.minOfOrNull { it.second.centerX } ?: 0
            val maxX = orderedAnchors.maxOfOrNull { it.second.centerX } ?: 700
            ((maxX - minX) / 6.0).toInt().coerceAtLeast(90)
        }

        val entries = orderedAnchors.map { (day, anchor) ->
            val minX = anchor.centerX - (colWidth * 0.65).toInt()
            val maxX = anchor.centerX + (colWidth * 0.55).toInt()
            val minY = anchor.rect.bottom - 5
            val maxY = anchor.centerY + (avgRowHeight * 0.88).toInt()

            val cellItems = lines.filter { item ->
                item !== anchor &&
                    item.centerX in minX..maxX &&
                    item.centerY in minY..maxY
            }.sortedBy { it.centerY }

            val joined = cellItems.joinToString(" ") { it.text }
            val diaMatch = DIA_REGEX.find(joined)?.value.orEmpty()
            val times = TIME_REGEX.findAll(joined).map { it.value }.toList()
            OcrDayEntry(
                day = day,
                diaNo = diaMatch.ifEmpty { "-" },
                startTime = times.getOrNull(0).orEmpty(),
                endTime = times.getOrNull(1).orEmpty()
            )
        }

        return ScreenshotOcrResult(
            name = detectedName,
            yearMonth = ym,
            entries = entries,
            rawText = raw
        )
    }
}
