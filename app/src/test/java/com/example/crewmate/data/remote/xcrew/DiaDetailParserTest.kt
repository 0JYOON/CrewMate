package com.example.crewmate.data.remote.xcrew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiaDetailParserTest {

    @Test
    fun parse_validJson_extractsSummariesAndRunDays() {
        val json = """
            {
                "data": [
                    {
                        "num": 1,
                        "pjtHrDvNm": "운전",
                        "trnNo": "1601",
                        "dptStnNm": "서울",
                        "arvStnNm": "대전",
                        "dptTm": "08:00",
                        "arvTm": "09:30"
                    }
                ],
                "dtlData": [
                    {
                        "pjtHrDvNm": "운전",
                        "pjtTnum": 90,
                        "pjtDst": 150.5,
                        "totWrkTnum": 300,
                        "totPjtDst": 250.0
                    }
                ],
                "efcTrm": {
                    "pdiaEfcStDt": "20260901",
                    "pdiaEfcClsDt": "99991231",
                    "runWkdy9": "Y",
                    "runWkdy14": "Y",
                    "runWkdy15": "Y"
                }
            }
        """.trimIndent()

        val parser = DiaDetailParser()
        val response = parser.parse(json)

        assertNotNull(response)
        assertEquals(1, response.segments.size)
        assertEquals(1, response.summaries.size)
        assertEquals("2026.09.01 ~ 9999.12.31", response.extractEffectiveTermStr())
        assertEquals("평휴, 평평, 평토", response.extractRunDays())
    }

    @Test
    fun parse_commaSeparatedRunDays_extractsCorrectly() {
        val json = """
            {
                "data": [],
                "dtlData": [],
                "extrCrewMgVO": {
                    "runWkdyNm": "평휴, 토휴, 휴휴"
                }
            }
        """.trimIndent()

        val parser = DiaDetailParser()
        val response = parser.parse(json)

        assertEquals("평휴, 휴휴, 토휴", response.extractRunDays())
    }

    @Test
    fun parse_officialXcrewRunDtDv_extractsCorrectly() {
        val json = """
            {
                "data": [],
                "dtlData": [],
                "runDtDv": [
                    {
                        "diaRunDtDvCd00": 0,
                        "diaRunDtDvCd01": 0,
                        "diaRunDtDvCd02": 0,
                        "diaRunDtDvCd03": 0,
                        "diaRunDtDvCd04": 0,
                        "diaRunDtDvCd05": 0,
                        "diaRunDtDvCd06": 0,
                        "diaRunDtDvCd07": 0,
                        "diaRunDtDvCd10": 1,
                        "diaRunDtDvCd11": 0,
                        "diaRunDtDvCd12": 0,
                        "diaRunDtDvCd14": 0,
                        "diaRunDtDvCd15": 0,
                        "diaRunDtDvCd16": 1,
                        "diaRunDtDvCd17": 1
                    }
                ]
            }
        """.trimIndent()

        val parser = DiaDetailParser()
        val response = parser.parse(json)

        assertEquals("평휴, 평평, 평토", response.extractRunDays())
    }

    @Test
    fun parse_emptyOrNullJson_handlesGracefullyWithoutCrash() {
        val json = """
            {
                "data": [],
                "dtlData": []
            }
        """.trimIndent()

        val parser = DiaDetailParser()
        val response = parser.parse(json)

        assertEquals("-", response.extractRunDays())
        assertEquals("-", response.extractEffectiveTermStr())
    }
}
