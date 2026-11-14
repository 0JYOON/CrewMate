package com.example.crewmate.labs.colleaguecompare

import android.content.Context
import android.net.Uri
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 메모장(.txt), CSV(.csv), 엑셀(.xlsx) 파일에서 미배정 패턴(다이아번호 순서)을 추출하는 파서.
 */
object PatternFileParser {

    fun parseFromUri(context: Context, uri: Uri): Result<List<String>> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("파일을 열 수 없습니다.")

        val isZipOrXlsx = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val tokens = if (isZipOrXlsx) {
            parseXlsxStream(ByteArrayInputStream(bytes))
        } else {
            val utf8 = bytes.toString(Charsets.UTF_8)
            val text = if (utf8.contains('\uFFFD')) {
                bytes.toString(Charset.forName("EUC-KR"))
            } else {
                utf8
            }
            parseTextOrCsv(text)
        }
        if (tokens.isEmpty()) {
            error("인식된 다이아번호 또는 패턴 항목이 없습니다.")
        }
        tokens
    }

    fun parseTextOrCsv(text: String): List<String> {
        val cleaned = text.removePrefix("\uFEFF")
        return cleaned
            .split(Regex("[\\r\\n,;\\t]+"))
            .flatMap { segment ->
                segment.trim().split(Regex("\\s+"))
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun parseXlsxStream(inputStream: InputStream): List<String> {
        var sharedStringsXml: ByteArray? = null
        var firstSheetXml: ByteArray? = null

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.lowercase()
                    when {
                        name == "xl/sharedstrings.xml" -> sharedStringsXml = zis.readBytes()
                        name == "xl/worksheets/sheet1.xml" -> firstSheetXml = zis.readBytes()
                        firstSheetXml == null && name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                            firstSheetXml = zis.readBytes()
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sheetBytes = firstSheetXml ?: return emptyList()
        val sharedStrings = sharedStringsXml?.let { parseSharedStrings(it) } ?: emptyList()
        return parseWorksheetCells(sheetBytes, sharedStrings)
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))

        val siNodes = doc.getElementsByTagName("si")
        val list = ArrayList<String>(siNodes.length)
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i) as? Element ?: continue
            val tNodes = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until tNodes.length) {
                sb.append(tNodes.item(j).textContent.orEmpty())
            }
            list.add(sb.toString().trim())
        }
        return list
    }

    private fun parseWorksheetCells(xmlBytes: ByteArray, sharedStrings: List<String>): List<String> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))

        val cellNodes = doc.getElementsByTagName("c")
        val tokens = ArrayList<String>(cellNodes.length)
        for (i in 0 until cellNodes.length) {
            val cell = cellNodes.item(i) as? Element ?: continue
            val cellType = cell.getAttribute("t")
            val valueText = when (cellType) {
                "s" -> {
                    val idx = cell.getElementsByTagName("v").item(0)?.textContent?.trim()?.toIntOrNull()
                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                }
                "inlineStr" -> {
                    cell.getElementsByTagName("t").item(0)?.textContent?.trim().orEmpty()
                }
                else -> {
                    val raw = cell.getElementsByTagName("v").item(0)?.textContent?.trim().orEmpty()
                    // Excel stores integer train/dia numbers as "16035.0" or "16035"
                    if (raw.endsWith(".0")) raw.dropLast(2) else raw
                }
            }
            if (valueText.isNotBlank()) {
                tokens.add(valueText.trim())
            }
        }
        return tokens
    }

    fun resolveCyclicDia(
        targetDateIso: String,
        anchorDateIso: String,
        sequence: List<String>
    ): String? {
        if (sequence.isEmpty()) return null
        val target = runCatching { LocalDate.parse(targetDateIso) }.getOrNull() ?: return null
        val anchor = runCatching { LocalDate.parse(anchorDateIso) }.getOrNull() ?: return null
        val diff = ChronoUnit.DAYS.between(anchor, target).toInt()
        val size = sequence.size
        val idx = ((diff % size) + size) % size
        return sequence[idx]
    }
}
