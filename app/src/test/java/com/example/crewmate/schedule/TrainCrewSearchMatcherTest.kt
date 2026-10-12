package com.example.crewmate.schedule

import com.example.crewmate.data.remote.xcrew.CrewAssignment
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.data.remote.xcrew.XcrewCrewWorkStatusParser
import com.example.crewmate.data.remote.xcrew.normalizeCrewMembers
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainCrewSearchMatcherTest {
    @Test
    fun normalizesPrefixedTrainNumbers() {
        listOf("K4501", "F4501", "G4501", " 4501 ", "k4501").forEach {
            assertEquals("4501", TrainNumberNormalizer.normalizeTrainNumber(it))
        }
    }

    @Test
    fun trimsAndFiltersCrewNamesWithoutRemovingMaskedNames() {
        assertEquals(listOf("*지웅", "*예솔"), normalizeCrewMembers("*지웅   ", "*예솔   ", "", ""))
    }

    @Test
    fun parsesAllFourCrewFieldsAndTrimsEmptyNames() {
        val response = XcrewCrewWorkStatusParser().parse(
            """{"data":[
                {"pdiaNo":"16201","empNm1":"*지웅   ","empNm2":"","empNm3":"","empNm4":""},
                {"pdiaNo":"16202","empNm1":"*지웅   ","empNm2":"*예솔   ","empNm3":"","empNm4":""},
                {"pdiaNo":"16203","empNm1":"*가","empNm2":"*나","empNm3":"*다","empNm4":"*라"}
            ],"dtlData":[]}""",
        )
        assertEquals(listOf(listOf("*지웅"), listOf("*지웅", "*예솔"), listOf("*가", "*나", "*다", "*라")),
            response.map(CrewAssignment::crewMembers))
    }

    @Test
    fun matchesEveryDetailSegmentAndKeepsMatchingDiaAssignmentsAndOrder() {
        val first = assignment("16810", listOf("*지웅"))
        val duplicate = assignment("16810", listOf("*예솔"))
        val secondDia = assignment("16811", listOf("*은기"))
        val details = listOf(
            "16810" to detail(
                segment(1, "K4501", "A", "B", "0900", "1000"),
                segment(2, "", "B", "C"),
                segment(3, "F4501", "C", "D", "1100", "1200"),
            ),
            "16811" to detail(segment(1, "G4501", "D", "E")),
        )

        val matches = TrainCrewSearchMatcher.match(
            sourceDt = "20261005",
            targetDt = "20261005",
            searchTrainNumber = "K4501",
            diaDetailsInApiOrder = details,
            assignmentsByDia = mapOf("16810" to listOf(first, duplicate), "16811" to listOf(secondDia)),
        )

        assertEquals(listOf("16810", "16810", "16811"), matches.map(TrainCrewSearchResult::pdiaNo))
        assertEquals(listOf("A", "C", "D"), matches.map { it.segment.dptStnNm })
        assertEquals(listOf("0900", "1100", null), matches.map { it.segment.dptTm })
        assertEquals(listOf("*지웅", "*예솔"), matches.first().crewMembers)
        assertEquals(listOf("*은기"), matches.last().crewMembers)
    }

    @Test
    fun doesNotAttachAssignmentsFromDifferentDia() {
        val matches = TrainCrewSearchMatcher.match(
            sourceDt = "20261005",
            targetDt = "20261005",
            searchTrainNumber = "4501",
            diaDetailsInApiOrder = listOf("16810" to detail(segment(1, "K4501", null, null))),
            assignmentsByDia = mapOf("16811" to listOf(assignment("16811", listOf("다른 사람")))),
        )
        assertEquals(emptyList<String>(), matches.single().crewMembers)
    }

    private fun detail(vararg segments: DiaDetailSegment) = DiaDetailResponse(
        effectiveTerm = null,
        runDtDv = null,
        segments = segments.toList(),
        summaries = emptyList(),
        crewMeta = null,
    )

    private fun segment(
        num: Int,
        train: String?,
        departure: String?,
        arrival: String?,
        departureTime: String? = null,
        arrivalTime: String? = null,
    ) = DiaDetailSegment(
        num = num,
        pdiaEfcStDt = null,
        pdiaEfcClsDt = null,
        trnNo = train,
        dptStnNm = departure,
        arvStnNm = arrival,
        dptTm = departureTime,
        arvTm = arrivalTime,
        pjtHrDvNm = null,
        pjtTnum = null,
        pjtDst = null,
    )

    private fun assignment(dia: String, crew: List<String>) = CrewAssignment(dia, null, null, null, null, crew)
}
