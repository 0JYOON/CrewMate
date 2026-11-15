package com.example.crewmate.labs

/**
 * 실험실 기능의 현재 성숙도 단계.
 */
enum class LabStage(val label: String) {
    EXPERIMENTAL("실험 중"),
    BETA("베타"),
    GRADUATING("정식 승격 예정")
}

/**
 * 실험실 기능이 앱 UI에 진입하는 방식.
 */
enum class LabEntryType {
    /** 기존 화면(오늘의 근무, 달력 등) 내부에 카드/뱃지/뷰로 삽입 */
    INLINE_ENHANCEMENT,

    /** 햄버거 메뉴에 독립 메뉴 및 전용 화면으로 노출 */
    STANDALONE_SCREEN
}

/**
 * CrewMate 실험실(Labs) 기능 중앙 레지스트리.
 *
 * [id]는 Android SharedPreferences, 백업 JSON, CrewMate-Alpha 웹앱과
 * 1:1로 동일한 고정 키를 사용합니다.
 */
enum class LabFeature(
    val id: String,
    val title: String,
    val description: String,
    val stage: LabStage,
    val entryType: LabEntryType,
    val requiresOnline: Boolean,
    val route: String? = null,
    val defaultEnabled: Boolean = false,
    val isImplemented: Boolean = false
) {
    ROUTE_DIAGRAM(
        id = "route_diagram",
        title = "승무 행로도 시각화 뷰",
        description = "다이아 상세 구간을 노선 행로도 타임라인 형태로 시각화하여 보여줍니다.",
        stage = LabStage.BETA,
        entryType = LabEntryType.INLINE_ENHANCEMENT,
        requiresOnline = false,
        isImplemented = true
    ),
    PAY_STATEMENT(
        id = "pay_statement",
        title = "개인근무명세서 조회",
        description = "Xcrew 개인근무명세서(시간·거리·휴일·연장·야간) 합계 및 예상 승무여비·승무수당 충족 여부를 확인합니다.",
        stage = LabStage.EXPERIMENTAL,
        entryType = LabEntryType.STANDALONE_SCREEN,
        requiresOnline = true,
        route = "lab-pay-statement",
        isImplemented = true
    ),
    COLLEAGUE_COMPARE(
        id = "colleague_compare",
        title = "동료 근무 비교",
        description = "내 월별 근무표 아래에 동료 및 수동 추가(미배정 패턴) 근무표를 나란히 비교합니다.",
        stage = LabStage.EXPERIMENTAL,
        entryType = LabEntryType.STANDALONE_SCREEN,
        requiresOnline = false,
        route = "lab-colleague-compare",
        isImplemented = true
    ),
    HANDOVER_CREW(
        id = "handover_crew",
        title = "교대승무원 확인",
        description = "오늘의 근무 열번 뱃지 옆 버튼으로 직전 승무원 및 교대 승무원 정보를 팝업으로 조회합니다.",
        stage = LabStage.EXPERIMENTAL,
        entryType = LabEntryType.INLINE_ENHANCEMENT,
        requiresOnline = false,
        isImplemented = true
    ),
    DIA_RATING(
        id = "dia_rating",
        title = "다이아 평가",
        description = "경/중사업, 야간 취침역, 운전시간 등 조건에 따라 다이아를 뱃지·음영으로 구분 표시합니다.",
        stage = LabStage.EXPERIMENTAL,
        entryType = LabEntryType.INLINE_ENHANCEMENT,
        requiresOnline = false,
        isImplemented = true
    );

    companion object {
        fun fromId(id: String): LabFeature? = entries.find { it.id == id }
    }
}
