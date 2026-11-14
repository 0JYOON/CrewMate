package com.example.crewmate.labs.colleaguecompare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PatternFileParserTest {

    @Test
    fun parseTextOrCsv_handlesNewlinesCommasAndSpaces() {
        val raw = "\uFEFF16035, 16315\n~   S\t16038\r\n연차"
        val parsed = PatternFileParser.parseTextOrCsv(raw)
        assertEquals(listOf("16035", "16315", "~", "S", "16038", "연차"), parsed)
    }

    @Test
    fun parseXlsxStream_extractsSharedStringsAndNumericCells() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>~</t></si>
                  <si><t>S</t></si>
                </sst>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1"><v>16035.0</v></c>
                      <c r="B1" t="s"><v>0</v></c>
                      <c r="C1" t="s"><v>1</v></c>
                      <c r="D1"><v>16315</v></c>
                    </row>
                  </sheetData>
                </worksheet>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }

        val tokens = PatternFileParser.parseXlsxStream(ByteArrayInputStream(baos.toByteArray()))
        assertEquals(listOf("16035", "~", "S", "16315"), tokens)
    }

    @Test
    fun virtualPerson_resolvesCyclicPatternAndManualOverride() {
        val person = ComparePerson(
            id = "vir_1",
            name = "미배정 A조",
            type = CompareEntryType.VIRTUAL_PERSON,
            patternSequence = listOf("16035", "~", "S"),
            patternAnchorDate = "2026-11-01",
            manualOverrides = mapOf(
                "2026-11-02" to CompareDayCell(
                    date = "2026-11-02",
                    diaNo = "901",
                    startTime = "08:00",
                    endTime = "17:00",
                    isOverridden = true
                )
            )
        )

        val d1 = person.cellForDate("202611", "2026-11-01")
        assertEquals("16035", d1?.diaNo)
        assertFalse(d1?.isOverridden == true)

        val d2 = person.cellForDate("202611", "2026-11-02")
        assertEquals("901", d2?.diaNo)
        assertEquals("08:00~17:00", d2?.formattedCommuteTime())
        assertTrue(d2?.isOverridden == true)

        val d3 = person.cellForDate("202611", "2026-11-03")
        assertEquals("S", d3?.diaNo)

        val d4 = person.cellForDate("202611", "2026-11-04")
        assertEquals("16035", d4?.diaNo)
    }
}
