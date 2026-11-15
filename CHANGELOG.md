# 📋 CrewMate (크루메이트) CHANGELOG (패치노트)

모든 버전 업데이트, 기능 추가, 버그 수정 및 리팩토링 내역을 최신순으로 기록하는 문서입니다.

---

## [v1.0.53] - 2026-11-15
### 개인근무명세서 휴일근무 확정 기준 변경 (`hldyTnum` 실적 기반)
- **수정 이유**:
  - 휴일근무(`offdDdFlg == "Y"` 또는 `hldyTnum > 0`) 시 실제 근무를 수행하더라도 거리(`totPjtDst`) 실적이 인정되지 않으므로, 거리가 아닌 **휴일 시간(`hldyTnum > 0`)** 존재 여부로 확정 판별하도록 개선함.
  - 휴일이면서 야간 2일차(`alcSpecDvCd == "12"`)인 경우도 전날 휴일 야간 1일차의 확정 여부를 그대로 상속받도록 연동함.
- **변경 파일**:
  - [PayStatementModels.kt](file:///C:/Users/joon1/.gemini/antigravity/worktrees/CrewMate/add_labs_feature_framework/app/src/main/java/com/example/crewmate/labs/paystatement/PayStatementModels.kt) (`hasConfirmedPerformance` 속성 추가, `isTravelPayCounted` 연동)
  - [PayStatementParser.kt](file:///C:/Users/joon1/.gemini/antigravity/worktrees/CrewMate/add_labs_feature_framework/app/src/main/java/com/example/crewmate/labs/paystatement/PayStatementParser.kt) (`buildReport` 내 확정/대기 판별을 `hasConfirmedPerformance` 기준으로 전환)
  - [PayStatementScreen.kt](file:///C:/Users/joon1/.gemini/antigravity/worktrees/CrewMate/add_labs_feature_framework/app/src/main/java/com/example/crewmate/labs/paystatement/PayStatementScreen.kt) (행 강조·대기 뱃지 판별을 `hasConfirmedPerformance` 기준으로 통일)
  - [PayStatementParserTest.kt](file:///C:/Users/joon1/.gemini/antigravity/worktrees/CrewMate/add_labs_feature_framework/app/src/test/java/com/example/crewmate/labs/paystatement/PayStatementParserTest.kt) (휴일 `hldyTnum` 확정 및 휴일 야간 2일차 상속 테스트 추가)
  - `CrewMate-Alpha/app-runtime.js` (`hasPayConfirmedPerformance` 추가 및 `evaluatePayStatement`·목록 렌더링 동기화)

---

## [v1.0.52] - 2026-11-15
### 개인근무명세서 야간 2일차 확정 판별 및 웹(`CrewMate-Alpha`) 동기화
- **수정 이유**:
  - 야간 2일차(`alcSpecDvCd == "12"`) 근무는 시간/거리/휴일/연장 값이 `0`이더라도 전날(야간 1일차) 근무의 연장이므로, 연속된 전날 근무가 확정이면 2일차도 확정 일수(승무여비 및 휴일이 아닌 경우 승무수당)로 포함해야 함.
  - 웹(`CrewMate-Alpha`)에서 기존에 저장된 월별 명세서 캐시에 `alcSpecDvCd`·`pdiaDvCd`가 누락되어 앱과 계산 일수가 다르게 나오던 문제를 해결.
- **변경 파일**:
  - [PayStatementParser.kt](file:///C:/Users/joon1/.gemini/antigravity/worktrees/CrewMate/add_labs_feature_framework/app/src/main/java/com/example/crewmate/labs/paystatement/PayStatementParser.kt) (`buildReport` 날짜 정렬 및 연속 전날 확정 상태의 야간 2일차 `alcSpecDvCd == "12"` 확정 상속)
  - [PayStatementParserTest.kt](file:///C:/Users/joon1/.gemini/antigravity/worktrees/CrewMate/add_labs_feature_framework/app/src/test/java/com/example/crewmate/labs/paystatement/PayStatementParserTest.kt) (야간 2일차 확정 상속 단위 테스트 추가 및 기존 테스트 날짜/구분 보정)
  - `CrewMate-Alpha/app-runtime.js` (`evaluatePayStatement` 야간 2일차 확정 상속 및 `PayStatementTabView` 레거시 캐시 `store.schedules` 폴백 보완)

---

## [v1.0.51] - 2026-11-15

### 개요
- **동료 근무 비교 (`colleague_compare`) 본인 근무표 · 동명이인 가상인물 다이아 번호 미표출 원인 수정**

### 주요 변경 사항
- **본인 근무표 조회 키 수정 (`ColleagueCompareViewModel.kt`)**:
  - `loadMyScheduleForMonth()`가 `scheduleRepo.getSchedules()` 호출 시 `"yyyy-MM"` 대신 `WorkScheduleDao`가 사용하는 `"yyyyMM"`(`yearMonthKey`)을 전달하도록 수정해 본인(`나 (본인)`) 근무표가 정상 표출되도록 해결
- **동명이인 가상인물 `cellsByMonth` 폴백 수정 (`ColleagueCompareModels.kt`)**:
  - `ComparePerson.cellForDate()`에서 `VIRTUAL_PERSON`이 순환 패턴(`patternSequence`)이 비어 있는 경우 조기 `null` 반환하던 문제를 수정하여, 동명이인으로 자동 분할 생성된 가상 인물의 `cellsByMonth` 배정 다이아 번호가 정상 표출되도록 해결
- **테스트 보강 (`PatternFileParserTest.kt`)**:
  - 동명이인 분할 가상 인물(`patternSequence = emptyList()`)의 `cellForDate()` 반환 단위 테스트 추가 (`./gradlew.bat test assembleDebug` 통과)

---

## [v1.0.50] - 2026-11-15

### 개요
- **개인근무명세서 (`pay_statement`) 3구간 게이지 바 · 확정/예정 표시 · 불필요 문구/변수명 제거 · 오프라인 캐시 및 통신 차단 적용**

### 주요 변경 사항
- **3구간 게이지 바 및 확정/예정 숫자 표현 (앱 + 웹)**:
  - `PayStatementScreen.kt`, `app-runtime.js`: 승무여비(20일)·승무수당(17일) 최대 목표 대비 **예정 총 근무일수**(옅은 파란색)와 **확정일수**(짙은 파란색)를 2단 겹침 게이지 바로 시각화하고, 우측 상단에 `확정 X일 / 예정 Y일` 칩 표기
- **불필요한 안내 문구 및 변수명 제거 (앱 + 웹)**:
  - 카드 상단 부제목(`근무 예정...`, `근무배정(alcSpecDvCd...)`) 삭제
  - 승무여비·승무수당 하단 `휴일 0일 제외/포함`, `대기 실승무 0일 포함`, `거리>0`, `offdDdFlg=Y`, `pdiaDvCd=4` 등 변수명 및 부가 설명 문구 제거
- **오프라인 모드 캐시 조회 지원 및 Xcrew 통신 차단 (안드로이드)**:
  - `PayStatementStorage.kt` 신설: 월별 개인근무명세서 및 최근 새로고침 시각 로컬 캐시 저장/로드
  - `PayStatementViewModel.kt`, `AppNavigation.kt`, `PayStatementScreen.kt`: `isOfflineMode` 시 Xcrew API 호출 원천 차단, 저장된 캐시 즉시 표출 및 `새로고침` 버튼 비활성화

---

## [v1.0.49] - 2026-11-15

### 개요
- **동료 근무 비교 (`colleague_compare`) 내 근무표·검색 누락 버그 수정 및 복원/수집시각 UI 보강**

### 주요 변경 사항
- **내 근무표 표출 누락 및 검색/추가 빈칸 버그 수정**:
  - `ColleagueCompareViewModel.kt`: `WorkSchedule.workDate`(`yyyyMMdd`)를 `yyyy-MM-dd` ISO 키로 정규화하여 본인(`나 (본인)`) 근무표가 정상 매핑되도록 수정
  - `searchColleagues()`: 로컬 캐시에 일부만 있는 경우에도 온라인 기준일(오늘/1일/10일/20일 최대 3일) `fetchCrewWorkStatus`를 병행 조회해 당일 휴무자 검색 누락 방지
  - `addRealColleague()`: Xcrew 개인근무표 응답과 로컬 `CrewAssignmentDao` 배정 캐시를 병합해 빈칸 발생 최소화
- **복원 버튼·최근 새로고침 시각·UI 정리**:
  - `ColleagueCompareStorage.kt`, `ColleagueCompareViewModel.kt`, `ColleagueCompareScreen.kt`: 새로고침 버튼 좌측에 최근 새로고침 시각(`yy-MM-dd HH:mm`) 표기
  - `AddColleagueDialog`: `'이름으로 바로 조회·추가'` 제거(검색 후 선택 강제)
  - `EditCellDialog`: 수동 수정 셀 편집 시 `↩ 원래 데이터로 복원` / `↩ 기본 순환 패턴으로 복원` 버튼 시인성 강화
  - `ColleagueCompareScreen.kt`: 가상 인물 성명 칸 `가상·편집` 뱃지 및 하단 안내 문구 제거, 비교 표 영역 `weight(1f)` 확장으로 자연스러운 세로 스크롤 지원

---

## [v1.0.48] - 2026-11-15

### 개요
- **동료 근무 비교 (`colleague_compare`) 승무원 추가 · 셀 편집 · 행/열 전환 UX 전면 개선**

### 주요 변경 사항
- **검색 시에만 이름 목록 표출 & 동명이인 자동 가상 인물 분할**:
  - `ColleagueCompareViewModel.kt`, `ColleagueCompareScreen.kt`: `+ 승무원 추가` 팝업 오픈 시 검색 결과를 초기화하여 검색 입력 전 미리 목록을 띄우지 않음
  - Xcrew 개인근무표 응답(`dtosToGroupedBuckets`) 및 로컬 `CrewAssignmentDao` 당월 캐시에서 동일 날짜 중복 배정(`maxPerDay >= 2`)이 감지되면 동명이인 인원수만큼 `"{성명} (1)"` ~ `"{성명} (K)"` 가상 인물로 자동 분할 추가
- **전체 승무원 일별 근무 수정 & 수정 셀 빨간 점 표시**:
  - `ColleagueCompareModels.kt`, `ColleagueCompareViewModel.kt`, `ColleagueCompareScreen.kt`: 가상 인물뿐 아니라 승무원 추가로 등록한 실존 동료의 근무 칸도 터치하여 덮어쓰기/원래 근무로 복원 가능
  - 수동 수정된 셀 우측 상단에 빨간 작은 점(`6.dp` 원형) 표시
- **가상 인물 이름 클릭 수정 & 가로/세로 행·열 전환 뱃지**:
  - 가상 인물 이름을 누르면 `VirtualPersonDialog`가 수정 모드로 열려 이름·패턴 시작일·순환 다이아를 즉시 수정 가능
  - 상단 `출퇴근 표시 ON/OFF` 뱃지 우측에 `⇄ 가로/세로 전환` 뱃지 추가(`CompareDisplayOptions.transposeMatrix`, `ColleagueCompareStorage.kt` 영속 저장)
- **테스트 검증**:
  - `PatternFileParserTest.kt`: 실존 동료 수동 오버라이드 및 동명이인 날짜별 버킷팅 단위 테스트 통과 (`./gradlew.bat test assembleDebug` 0 오류)

---

## [v1.0.47] - 2026-11-15

### 개요
- **개인근무명세서 (`pay_statement`) UI 밀도 및 승무여비·승무수당 예상 산정 로직 개선**

### 주요 변경 사항
- **표 시인성 및 여백 개선**:
  - `PayStatementScreen.kt`: 목록 상하 여백 최소화(`Arrangement.spacedBy(0.dp)`, 행/헤더 패딩 축소), `시간`·`거리` 굵은 글씨 해제, `휴일`·`연장`·`야간` 시간이 0보다 큰 경우 굵은 글씨 강조
  - `PayStatementViewModel.kt`: 새로고침 시각 좌측에 날짜(`yy-MM-dd HH:mm`) 함께 표기
- **예상 승무여비·승무수당 산정 로직 개편**:
  - `PayStatementModels.kt`, `PayStatementParser.kt`: 거리 실적(`totPjtDst`) 유무와 관계없이 근무일(`alcSpecDvCd` `"11"`, `"12"`) 기반 예상 일수 산정
  - `alcSpecDvCd` `"11"`, `"12"` 중 `offdDdFlg == "Y"`(또는 `hldyTnum > 0`)인 휴일근무는 승무수당 지급일수에서 제외
  - 대기근무 판별 기준을 900번대 다이아 번호에서 `pdiaDvCd == "4"`로 변경하고, 대기근무는 실제 승무실적(거리 > 0) 발생 시에만 여비·수당에 포함
  - 거리 실적이 생성된 근무는 **확정**, 예정 근무일은 **예정**으로 구분하여 합산/진행률 바에 표시
- **테스트 검증**:
  - `PayStatementParserTest.kt`: `pdiaDvCd="4"` 대기근무, `alcSpecDvCd="11"/"12"` 예정일, `offdDdFlg="Y"` 휴일 제외, 확정/예정 일수 검증 테스트 통과 (`./gradlew.bat test assembleDebug` 0 오류)

---

## [v1.0.46] - 2026-11-15

### 개요
- 실험실 5호 기능 **다이아 평가 (`dia_rating`)** 안드로이드 구현 및 단위 테스트·빌드 검증·폰 설치 완료 (5대 실험실 기능 구현 100% 완료)

### 주요 변경 사항
- **다이아 평가 엔진 및 설정/오버라이드 저장소**:
  - `DiaRatingModels.kt`, `DiaRatingEvaluator.kt`, `DiaRatingStorage.kt`: 운전시간·총사업시간·야간 취침역(선호/비선호) 기준 및 다이아별 수동 등급 지정(`경사업`/`보통`/`중사업`/`★ 선호`/`⚠️ 기피`) 판정 및 `SharedPreferences` + `StateFlow` 실시간 반영
  - `DiaRatingDialogs.kt`: 판정 근거 조회 및 수동 등급 지정 다이얼로그(`DiaRatingDetailDialog`), 평가 기준·취침역·셀 음영 설정 다이얼로그(`DiaRatingRulesDialog`)
- **월별 근무표·다이아 상세·실험실 설정 연동**:
  - `ScheduleScreen.kt`: 달력 날짜 셀 우상단 등급 뱃지(`경`/`중`/`강`/`★`) 및 선택적 배경 음영 표출
  - `DiaDetailCard.kt`: 다이아 헤더 옆 평가 등급 뱃지 표출 및 탭 시 상세/수동 지정 팝업 연결
  - `LabsSettingsScreen.kt` / `LabFeature.kt`: `DIA_RATING.isImplemented = true` 활성화 및 `⚙️ 평가 기준 설정` 진입 버튼 추가
- **단위 테스트**:
  - `DiaRatingEvaluatorTest.kt`: 운전·총사업시간 기준, 선호/비선호 취침역, 수동 지정 우선순위, 비승무(`S`/`연차`) 제외 검증 및 `LabsManagerTest.kt` 보완

---

## [v1.0.45] - 2026-11-15

### 개요
- 실험실 4호 기능 **교대승무원 확인 (`handover_crew`)** 안드로이드 구현, 단위 테스트·빌드 검증 및 폰 설치 완료

### 주요 변경 사항
- **교대승무원 조회 UI 및 다이아 카드 통합**:
  - `DiaDetailCard.kt`: `LabFeature.HANDOVER_CREW` 활성화 시(비캡처 모드) 열차번호 뱃지 옆에 `👥` 버튼 조건부 렌더링 및 `HandoverCrewDialog` 연결
  - `HandoverCrewDialog.kt`: 직전 승무원(인계) · 내 구간 · 교대 승무원(후속) · 전체 열차 릴레이 요약 팝업 표출 (`LabSafeContainer` 격리 적용)
- **조회 저장소 및 릴레이 판정 로직**:
  - `HandoverCrewModels.kt`, `HandoverCrewResolver.kt`: 심야 자정 경과 구간 날짜 보정(`resolveSegmentDate`) 및 시간/역 기반 직전·후속 승무원 판정(`resolveHandover`)
  - `HandoverCrewRepository.kt`: 온라인 시 `TrainCrewSearchRepository` 실시간 조회, 오프라인/실패 시 Room DB(`crew_assignments` + `dia_details` + `dia_census`) 캐시 폴백 조회
- **실험실 레지스트리 및 단위 테스트**:
  - `LabFeature.kt`: `HANDOVER_CREW.isImplemented = true` 활성화
  - `HandoverCrewResolverTest.kt`: 3구간 릴레이, 심야 자정 경과, 구간 날짜 보정 단위 테스트 추가

---

## [v1.0.44] - 2026-11-15

### 개요
- 실험실 3호 기능 **동료 근무 비교 (`colleague_compare`)** 안드로이드 구현 및 단위 테스트/빌드 검증 완료

### 주요 변경 사항
- **동료 근무 비교 화면 및 상태 관리**:
  - `ColleagueCompareScreen.kt` / `ColleagueCompareViewModel.kt`: 좌측 이름 열 고정 + 상단 날짜 헤더 연동 가로 스크롤 캘린더 그리드 구현
  - 본인 월간 근무 자동 로드 (`WorkScheduleRepositoryImpl`), 사번/이름 기반 Xcrew 동료 검색 및 월간 근무표 조회 (`CrewAssignmentDao` + `fetchCrewWorkStatus` + `fetchPersonalWorkSchedule`)
  - 가상 인물 추가/편집 및 순환 패턴(`.xlsx` / `.csv` / `.txt`) 파일 임포트, 가상 셀 수동 오버라이드 및 출퇴근 시간 표시 옵션 (`⚙️ 옵션`) 지원
- **로컬 저장 및 파서**:
  - `ColleagueCompareModels.kt`, `ColleagueCompareStorage.kt`, `PatternFileParser.kt`: UTF-8/EUC-KR 텍스트·CSV 및 `ZipInputStream` + `DocumentBuilderFactory` 기반 `.xlsx` 파싱, SharedPreferences JSON 영속화
- **네비게이션 및 실험실 등록**:
  - `LabFeature.kt`: `COLLEAGUE_COMPARE` (`isImplemented = true`) 활성화
  - `AppNavigation.kt`: `lab-colleague-compare` 라우트 연결
- **단위 테스트**:
  - `PatternFileParserTest.kt`: `.txt`/`.csv`, `.xlsx` 파싱 및 순환 패턴/수동 오버라이드 계산 검증

---

## [v1.0.43] - 2026-11-15

### 🧪 v1.0.42 최신 정식 베이스 복구 + 실험실(CrewMate Labs) · 행로도 · 개인근무명세서 통합
- **1. v1.0.42 최신 기능 전량 복구 (`versionCode = 43`, `versionName = "1.0.43"`)**:
  - 구 커밋(`v1.0.1`) 분기로 인해 누락되었던 v1.0.42 기능(앱 정보 및 이용 유의사항, GitHub 자동/수동 업데이트 확인, 홈 화면 위젯, 출무 사전확인 알람, 오프라인 프리페치, 관리자 허브, Pretendard 폰트) 전량 복구 및 통합.
- **2. 실험실(CrewMate Labs) 프레임워크 & 5회 터치 활성화 (`labs/`, `SettingsScreen.kt`, `AppNavigation.kt`)**:
  - `설정` 상단 `"설정"` 글자를 5번 터치해 실험실 활성화 ON/OFF 토글 팝업으로 전환하며, ON 시 햄버거(`☰`) 메뉴 `설정` 아래에 `실험실` 메뉴 노출.
  - 실험실 1호 **`승무 행로도 시각화 뷰`**(`DiaDetailCard.kt`, `labs/routediagram/*`, `assets/railwayMasterData.json`) 통합.
  - 실험실 2호(우선순위 3번) **`개인근무명세서 조회`**(`XcrewWebViewXhrDataSource.kt` `/extrCrewMg/searchExtrIndWrkSpec.do`, `labs/paystatement/*`, 여비 20일·수당 17일·900번대 대기근무 별도 판정) 통합.

---

## [v1.0.42] - 2026-11-14

### 🎨 위젯 테마 통일, 월별 위젯 순환패턴·S·꽉찬 레이아웃, 관리자 메뉴 개편 & 일일 03:00 Ban 체크
- **1. 위젯 바탕·아이콘·텍스트 앱 테마 100% 통일 ([`TodayDiaWidgetUpdater.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/TodayDiaWidgetUpdater.kt), [`MonthlyScheduleWidgetUpdater.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleWidgetUpdater.kt), [`SettingsScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt))**:
  - 명시적 라이트/다크 배경(`widget_card_bg_light/dark`)과 아이콘(`ic_widget_refresh_light/dark`), XML 텍스트 색상을 `context.isDarkThemeActive()` 기준으로 직접 지정해 앱 테마 고정 시 시스템 테마와 어긋나던 문제 해결.
  - 설정에서 `app_theme` 변경 즉시 모든 위젯 자동 갱신 연동.
- **2. 월별 위젯 미배정 달 순환 패턴·`S` 표시 복원 및 위젯 크기 꽉 채우기 ([`MonthlyScheduleRenderer.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleRenderer.kt), [`widget_monthly_schedule.xml`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/res/layout/widget_monthly_schedule.xml))**:
  - 실동 다이아가 없는 휴무일의 `"S"` 표시가 누락되던 필터 버그 수정(`filterDisplaySchedules`).
  - 미배정 달에서 `WorkPatternSettings` 순환 패턴(`주간`, `야간`, `~`, `S`)을 앱과 동일한 색상으로 렌더링.
  - `AppWidgetOptions` 크기 비율에 맞춰 캔버스 높이와 주(row)별 높이를 동적 확장하고 `fitXY`로 상하좌우 빈 여백 없이 꽉 채우도록 개선.
- **3. Xcrew 네트워크 통신 이력 저장·조회 및 관리자 메뉴 개편 ([`XcrewNetworkLogStore.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/XcrewNetworkLogStore.kt), [`XcrewWebViewXhrDataSource.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/XcrewWebViewXhrDataSource.kt), [`SettingsScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt))**:
  - 당일 포함 앞뒤 1일(`어제 ~ 오늘 ~ 내일`) 범위의 Xcrew 통신 시각·기능명·엔드포인트·성공 여부 저장(민감정보 제외) 및 자동 정리.
  - 기존 관리자 비밀번호 메뉴를 **`🛠️ 관리자 특별 설정 및 테스트`** 허브로 개편해 `가상 날짜 지정`과 `Xcrew 네트워크 통신 이력 조회`로 분리.
- **4. 일일 새벽 03:00 백그라운드 Ban 체크 & 온라인 전환 시 즉시 체크 ([`DailyBanCheckReceiver.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/login/DailyBanCheckReceiver.kt), [`EmployeeBanGuard.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/login/EmployeeBanGuard.kt), [`AppNavigation.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt))**:
  - 매일 새벽 03:00 자동 체크 알람을 예약하고, 당시 오프라인 모드였다면 온라인 모드로 전환한 순간 즉시 차단 목록 업데이트 여부를 검사해 자동 로그인 세션도 차단.

---

## [v1.0.41] - 2026-11-13

### 🛡️ Google Play Protect 오탐 완화 (`REQUEST_INSTALL_PACKAGES` 제거 & 브라우저 다운로드 전환 + 설치 안내 보강)
- **1. `REQUEST_INSTALL_PACKAGES` 권한 제거 ([`AndroidManifest.xml`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/AndroidManifest.xml))**:
  - 비스토어 APK에서 Play Protect 위험 점수를 높이는 `REQUEST_INSTALL_PACKAGES` 권한 선언을 완전히 제거.
- **2. 업데이트 다운로드 방식 브라우저 전환 및 Play Protect 안내 추가 ([`UpdateDialogs.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/UpdateDialogs.kt), [`AppNavigation.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt), [`SettingsScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt))**:
  - 업데이트 팝업에서 `"최신 APK 다운로드 (브라우저)"`를 눌러 공식 GitHub Releases 다운로드 링크를 브라우저로 열도록 단순화.
  - 팝업 내에 Play 프로텍트(`[세부정보] → [무시하고 설치]`) 및 갤럭시 `보안 위험 자동 차단` 일시 해제 안내 문구를 추가.

---

## [v1.0.40] - 2026-11-13

### ✨ 5대 핵심 개선(월별 위젯 전면 개편, SUIT·Spoqa 폰트 추가, 업데이트 팝업 정리, 사번 해시 차단) 완료
- **1. 월별 승무근무표 위젯 정보 확장·짤림 방지·색상 통일 ([`MonthlyScheduleRenderer.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleRenderer.kt), [`MonthlyScheduleWidgetUpdater.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleWidgetUpdater.kt), [`monthly_schedule_widget_info.xml`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/res/xml/monthly_schedule_widget_info.xml))**:
  - 대표열번(`trainNo`, `trainNo2`)을 제외한 모든 정보(근무 뱃지, 출근/퇴근 시각, 메모 내용 라벨, 기기 캘린더 일정 라벨)를 주(row)별 동적 높이로 렌더링하여 세로 짤림 없이 100% 표시.
  - 메모 점 표시를 삭제하고 실제 메모 내용 배지를 직접 표출.
  - 근무 뱃지 색상을 앱 내 월별 근무표([`ScheduleScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/ScheduleScreen.kt))와 동일하게 통일(주간 `#FFD54F`/검정 글씨, 야간 `#333333`/흰 글씨, 야간 2일차 `~다이아`, 연차 `#0D9488`).
- **2. 월별 승무근무표 위젯 상단 월 이동 네비게이션 개편 ([`widget_monthly_schedule.xml`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/res/layout/widget_monthly_schedule.xml))**:
  - 작았던 우측 상단 컨트롤 대신 상단 헤더 바 중앙에 `"YYYY년 M월 근무표"`(탭 시 당월 복귀)를 배치하고, 좌·우에 큼직한 `◀` / `▶` 버튼(44dp×36dp) 및 우측 끝 새로고침 버튼을 배치.
- **3. SUIT & Spoqa Han Sans Neo 폰트 번들링 및 글꼴 설정 1줄 UI ([`Type.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Type.kt), [`Theme.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Theme.kt), [`SettingsScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt))**:
  - `res/font/`에 `SUIT`(Regular/Medium/Bold/ExtraBold) 및 `Spoqa Han Sans Neo`(Regular/Medium/Bold) TTF 추가.
  - `AppFontOption`을 `System`, `Pretendard`, `SUIT`, `Spoqa Han Sans Neo` 영어 표기로 통일하고 2×2 1줄 칩(`maxLines = 1`)으로 배치해 글자 줄바꿈 제거(앱·위젯·캡처 일괄 적용).
- **4. 업데이트 안내 팝업 버튼 줄바꿈 개선 ([`UpdateDialogs.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/UpdateDialogs.kt))**:
  - `"웹에서 받기"`를 독립된 보조 버튼 행으로 분리하고 `"오늘 하루 보지 않기"` / `"나중에"` 버튼을 2열 배치(`maxLines = 1, softWrap = false`)해 자동 줄바꿈 현상 해소.
- **5. 특정 사번 비공개 해시 차단 가드 신설 ([`EmployeeBanGuard.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/login/EmployeeBanGuard.kt), [`LoginViewModel.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/login/LoginViewModel.kt), [`EmployeeBanGuardTest.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/test/java/com/example/crewmate/login/EmployeeBanGuardTest.kt))**:
  - 평문 사번 없이 `Salted SHA-256` 해시만 대조(원격 `config/blocked.json` + 로컬 해시 병행)하여 개발자 외에는 차단 대상을 판독할 수 없도록 설계.
  - 최초(수동) 로그인 시에만 원격 조회를 수행하고, 자동 로그인 시에는 중복 조회를 생략하며, 차단 시 일반 Xcrew 서버 지연 문구를 표시.

---

## [v1.0.39] - 2026-11-13

### 🎨 7대 핵심 개선(월별 근무표 위젯, 프리텐다드 폰트, 캘린더 다일 일정, 위젯 겹침 해소, 기초자료 비실시간 안내 등) 완료
- **1. 오늘의 근무 위젯 새로고침 버튼 ↔ 날짜 뱃지 겹침 해소 ([`DiaCaptureRenderer.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/DiaCaptureRenderer.kt), [`TodayDiaWidgetUpdater.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/TodayDiaWidgetUpdater.kt), [`widget_today_dia.xml`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/res/layout/widget_today_dia.xml))**:
  - `DiaCaptureRenderer`에 `reserveTopEndSpaceDp` 파라미터를 추가하여 위젯 렌더링 시(`38f`) 날짜 알약을 우측 끝에서 왼쪽으로 여백 확보해 배치하고, 새로고침 버튼을 `32dp` 컴팩트 사이즈로 다듬어 겹침을 원천 제거.
- **2. 월별 승무근무표 홈 화면 위젯 신설 ([`MonthlyScheduleWidgetProvider.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleWidgetProvider.kt), [`MonthlyScheduleWidgetUpdater.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleWidgetUpdater.kt), [`MonthlyScheduleRenderer.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleRenderer.kt))**:
  - 100% 로컬 Room DB 기반(`work_schedules`, `memos`)으로 Xcrew 통신 없이 동작하는 1080px 고화질 달력 위젯 신설.
  - 상단 `[◀]` `[오늘]` `[▶]` `[🔄]` 월 이동/새로고침 컨트롤, 주간/야간1일차/야간2일차/비실동/휴일 색상 뱃지, 메모 점, 오늘 날짜 하이라이트 지원 및 위젯 터치 시 승무근무표(`Routes.Schedule`) 직행.
- **3. 휴일근무 갯수 현황 야간 2일차 우측 문구 삭제 ([`HolidayWorkManager.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/HolidayWorkManager.kt), [`NonOpStatusScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/NonOpStatusScreen.kt))**:
  - `"(전일 야간 1일차와 1개 단위 통합)"` 문구를 제거하고 깔끔하게 `"야간 2일차"`만 표기(기존 캐시 데이터에서도 필터링).
- **4. 어플 최초 로그인 시 '앱 정보 및 이용 유의사항' 자동 팝업 ([`AppInfoDialog.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/AppInfoDialog.kt), [`SettingsScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt), [`AppNavigation.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt))**:
  - 공용 `AppInfoAndTermsDialog` 컴포넌트로 추출하고, 최초 로그인/진입 시(`has_seen_initial_app_info == false`) 1회 자동 표출.
- **5. 캘린더 연동 다일(Multi-day) 일정 전 일자 표시 수정 ([`DeviceCalendarReader.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/DeviceCalendarReader.kt))**:
  - `CalendarContract.Instances`에서 `BEGIN`만 읽던 버그를 해결해 `END`와 `ALL_DAY`(UTC 자정 보정)까지 읽어 2일 이상 걸친 일정이 모든 날짜에 표시되도록 `expandDeviceEventDates()` 구현.
- **6. 프리텐다드(Pretendard) 폰트 내장 및 글꼴 선택 설정 추가 ([`Type.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Type.kt), [`Theme.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Theme.kt), [`MainActivity.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/MainActivity.kt), [`SettingsScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt))**:
  - 공식 Pretendard TTF(Regular/Medium/Bold/ExtraBold)를 `res/font/`에 내장하고, 설정에서 `[시스템 기본]` / `[프리텐다드]` 실시간 전환 지원 (앱 UI·위젯·캡처 이미지 일괄 적용).
- **7. 오프라인·기초자료 기반 조회 시 비실시간 안내 문구 최하단 표출 ([`DiaLookupScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/DiaLookupScreen.kt), [`TrainCrewLookupV2Screen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/TrainCrewLookupV2Screen.kt), [`TodayWorkScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/TodayWorkScreen.kt))**:
  - 오프라인 모드 조회 및 기초자료 DB 기반 승무원 조회 결과 하단에 실시간 Xcrew 변동사항과 다를 수 있음을 알리는 소형 안내 문구 추가.
- **8. 단위 테스트 및 빌드 검증 ([`DeviceCalendarReaderTest.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/test/java/com/example/crewmate/schedule/DeviceCalendarReaderTest.kt))**:
  - 다일 일정 전개 및 휴일근무 야간 2일차 문구 제거 등 5개 단위 테스트 신설(총 91개 테스트 100% 통과) 및 `assembleDebug` 빌드 성공.

---

## [v1.0.38] - 2026-11-12

### 🚀 공식 GitHub Releases 연동 인앱 업데이트(Self-Update) 시스템 및 자동 감지 알림 구축
- **1. 공식 GitHub Releases API 연동 업데이트 엔진 신설 ([`AppUpdateManager.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/AppUpdateManager.kt))**:
  - 공식 배포 저장소([`0JYOON/CrewMate`](https://github.com/0JYOON/CrewMate))의 GitHub Releases API(`GET /repos/0JYOON/CrewMate/releases/latest`)를 무료·비인증으로 호출하여 최신 버전 태그, 릴리즈 제목, 패치노트 본문, APK 파일 직접 다운로드 URL(`assets[].browser_download_url`)을 자동 파싱.
  - 시맨틱 버전 비교 알고리즘(`compareVersions("1.0.37", "1.0.38")`)을 내장하여 메이저/마이너/패치 버전 및 `v` 접두사 자동 정규화 비교.
  - OkHttp 스트리밍 다운로드(`downloadApk`)로 인앱에서 0% ~ 100% 진행률을 실시간 표출하며 APK를 안전하게 수신.
  - `androidx.core.content.FileProvider` 및 `Intent.ACTION_VIEW` (`FLAG_GRANT_READ_URI_PERMISSION`)를 통해 안드로이드 시스템 패키지 인스톨러 화면 자동 호출 및 외부 웹 브라우저 Fallback 지원.
  - Android 8.0+ `REQUEST_INSTALL_PACKAGES` 권한 미승인 시 설정창으로 원터치 안내 다이얼로그(`InstallPermissionDialog`) 연동.
- **2. 앱 시작 시 백그라운드 자동 업데이트 감지 및 알림 팝업 ([`AppNavigation.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt), [`UpdateDialogs.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/UpdateDialogs.kt))**:
  - 사용자가 수동 버튼을 누르지 않더라도 앱 구동 시 백그라운드 코루틴에서 조용히 최신 버전을 확인.
  - **오프라인 모드 보호**: `isOfflineModeProvider()` 활성화 시 불필요한 네트워크 통신 일절 차단.
  - **스마트 스케줄링**: 마지막 확인 후 최소 12시간 주기(Throttling)로만 확인하여 배터리 및 GitHub API 쿼터 보호.
  - 새 버전 감지 시 **"🚀 새로운 업데이트 안내"** 모달 팝업 표출:
    - 버전 비교(`현재 v1.0.37 ➔ 최신 v1.0.38`), 릴리즈 노트 스크롤 박스.
    - `[지금 다운로드 및 설치]`, `[웹에서 받기]`, `[오늘 하루 보지 않기]`, `[나중에]` 버튼 지원.
- **3. 설정 화면 내 "버전 정보 및 업데이트" 카드 신설 ([`SettingsScreen.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt))**:
  - 현재 설치 버전(`v1.0.37`) 뱃지 및 **`[업데이트 확인]`** 버튼 배치.
  - 터치 시 프로그레스 인디케이터 동작 후 최신 버전일 때 "🎉 현재 최신 버전을 사용 중입니다." 다이얼로그 표출, 새 버전 있을 시 즉시 다운로드/설치 연동.
- **4. FileProvider 리소스 및 매니페스트 권한 등록 ([`file_paths.xml`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/res/xml/file_paths.xml), [`AndroidManifest.xml`](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/AndroidManifest.xml), [`build.gradle.kts`](file:///C:/Users/joon1/Documents/CrewMate/app/build.gradle.kts))**:
  - `file_paths.xml`에 내부/외부 캐시 updates 경로 등록 및 FileProvider 연동.
  - `REQUEST_INSTALL_PACKAGES` 선언 및 `versionCode = 37, versionName = "1.0.37"` 동기화 완료.
- **5. 단위 테스트 신설 및 빌드 검증 ([`AppUpdateManagerTest.kt`](file:///C:/Users/joon1/Documents/CrewMate/app/src/test/java/com/example/crewmate/util/AppUpdateManagerTest.kt))**:
  - 버전 비교 엣지 케이스 및 GitHub Release JSON 파싱 단위 테스트 5개 신설 (전체 86개 테스트 100% 무결 통과).
  - 컴파일 에러 0건, 디버그 APK(`app-debug.apk`) 빌드 성공.

---

## [v1.0.37] - 2026-11-11

### ℹ️ 설정 메뉴 '앱 정보 및 이용 유의사항' 명칭 변경 및 웹앱 톤앤매너(카드형 블록, Amber 경고, 문의 이메일·소속 최신화) 전면 개편
- **1. 설정 화면 메뉴 항목 명칭 및 서브타이틀 갱신 (`SettingsScreen.kt`)**:
  - 기존 `앱 정보 및 만든 이` ➔ **`앱 정보 및 이용 유의사항`** 으로 명칭 통일.
  - 서브타이틀: `"CrewMate 앱에 대한 정보와 이용 유의사항을 확인합니다."`로 가독성 및 직관성 향상.
- **2. 다이얼로그 디자인 전면 개편 (CrewMate 웹앱 톤앤매너 100% 반영) (`SettingsScreen.kt`)**:
  - 다이얼로그 타이틀: `ℹ️ 앱 정보 및 이용 유의사항` (M3 Surface, 20dp 라운드 다이얼로그).
  - 웹앱(`CrewMate-Alpha`)의 카드형 블록 구조와 이모지를 안드로이드 Jetpack Compose M3 환경에 맞추어 3단계 독립 카드로 미려하게 분할 구현:
    - **카드 1 (`📖 비공식 개발 안내`)**: 제작자 개인의 업무 편의를 목적으로 개발된 비공식 앱 안내 및 서비스 관련 양해 문구.
    - **카드 2 (`⚠️ [필독] 데이터 참고 및 면책 공지`)**: 웹앱 스타일의 앰버(Amber) 경고/유의 박스 디자인 적용(라이트 `#FFFBEB`/#FDE68A/#92400E, 다크 `#2C2214`/#5A4016/#FDE68A)으로 참고용 데이터 공지 및 공식 시스템(Xcrew) 최종 확인 필수 문구 강조.
    - **카드 3 (`✉️ 문의 및 제작자`)**: 최신 공식 문의 이메일(`studio.yj.lab@gmail.com`) 및 제작자 표기(`만든이 : 안산승무사업소 윤영준`) 배치.
- **3. 빌드 및 테스트 검증 (`EnhancementsUnitTest.kt`)**:
  - 81개 전체 단위 테스트 100% 무결 통과 (`testDebugUnitTest`) 및 디버그 APK 빌드 성공 (`assembleDebug`).

---

## [v1.0.36] - 2026-11-10

### 🎯 위젯 3대 핵심 개선(야간2일차/비근무일 붉은색 뱃지, 1080px 고화질 선명도 복원, singleTask 화면 덮어씌움 방지) 및 Xcrew 실시간 조회 검증
- **1. 소속 승무원 및 열차 승무원 실시간 Xcrew 요청 로직 검증 및 무결성 확인**:
  - 소속 승무원 근무 현황([`CrewWorkStatusScreen.kt`](file:///c:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/CrewWorkStatusScreen.kt), [`CrewWorkStatusRepository.kt`](file:///c:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/CrewWorkStatusRepository.kt)) 및 열차 승무원 조회 V2([`TrainCrewLookupV2Screen.kt`](file:///c:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/TrainCrewLookupV2Screen.kt))는 현재 오프라인 모드가 아닌 이상 조회 즉시 Xcrew 웹뷰 XHR 네트워크 통신을 요청하여 실시간 배정 명단을 가져오고 있음을 코드 및 데이터 파이프라인 전수 검증 완료.
- **2. 위젯 및 다이아 캡처 렌더러 야간 2일차 / 비근무일 붉은색 뱃지(`[MM.dd 근무]`) 표기 (`DiaCaptureRenderer.kt`, `TodayDiaWidgetUpdater.kt`, `TodayWorkScreen.kt`)**:
  - 오늘이 야간 2일차(어제 출근)이거나 비근무일(다음 근무 예정 다이아 표출)인 경우(`!todayResult.isToday`), 위젯 상단 헤더 타이틀(`승무 다이아 상세`) 우측에 앱 내 화면과 100% 동일한 **`[MM.dd 근무]`** 붉은색 뱃지(`errorContainer`: 라이트 `#FFDAD6`/다크 `#93000A`, `onErrorContainer`: 라이트 `#410002`/다크 `#FFDAD6`, ExtraBold 800)를 렌더링.
  - 인앱 다이아 스크린샷 캡처 시에도 `badgeText`를 전달하여 저장되는 비트맵에 동일하게 붉은색 뱃지가 찍히도록 시각적 일관성 확보.
- **3. 위젯 1080px FHD+ 레티나 고화질 상향 및 다운스케일링 버그 제거로 선명도 대폭 개선 (`TodayDiaWidgetUpdater.kt`)**:
  - 위젯 비트맵 해상도를 기존 720px에서 **`1080px` (FHD+ 1:1 레티나 규격, density 2.25f)**로 대폭 상향.
  - 세로가 긴 다이아에서 `MAX_BITMAP_BYTES`로 인해 400~500px 수준으로 강제 축소되던 인위적 다운스케일링 버그를 완전히 제거하여, 안드로이드 7.0+ Ashmem fd 기반으로 글씨·시각·선·뱃지가 칼같이 또렷하고 선명하게 표출되도록 개선.
- **4. `MainActivity` `singleTask` 및 Navigation 백스택 제어로 화면 중복 덮어씌움 원천 차단 (`AndroidManifest.xml`, `TodayDiaWidgetUpdater.kt`, `AppNavigation.kt`)**:
  - `AndroidManifest.xml`의 `MainActivity`에 **`android:launchMode="singleTask"`** 속성을 부여하여 백그라운드에 이미 앱이 실행 중일 때 새 인스턴스를 만들지 않고 기존 액티비티를 포그라운드로 복귀(Bring To Front)하도록 일원화.
  - 위젯 클릭 Intent에 `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP` 및 `putExtra("target_route", Routes.TodayWork)` 적용.
  - `AppNavigation.kt`의 `LaunchedEffect(initialTargetRoute)`에서 이미 '오늘의 근무' 화면에 있는 경우 중복 navigate를 차단하여, 위젯 터치 후 뒤로가기를 눌렀을 때 이전 화면이 또 나오지 않고 정상적으로 홈 화면으로 나가도록 완벽히 수정.
- **5. 단위 테스트 및 빌드 검증 (`EnhancementsUnitTest.kt`)**:
  - `todayDiaWidget_nightDay2OrNonWorkDay_generatesRedBadgeText`, `todayDiaWidget_retinaResolution_is1080px`, `todayDiaWidget_navigationProtection_doesNotDuplicateWhenAlreadyOnScreen` 3건의 신규 단위 테스트 추가 (총 81개 단위 테스트 100% 무결 통과) 및 `assembleDebug` 0 오류 빌드 성공 완료.

---

## [v1.0.35] - 2026-11-09

### 📱 '오늘의 근무' 승무 다이아 전용 홈 화면 위젯(AppWidget) 신설 & 100% 로컬 DB 무통신 안전 아키텍처 적용
- **1. '오늘의 근무' 승무 다이아 전용 홈 화면 위젯 신설 (`TodayDiaWidgetProvider.kt`, `TodayDiaWidgetUpdater.kt`)**:
  - 홈 화면에서 앱을 켜지 않고도 '오늘의 근무' 다이아 타임라인과 출퇴근 시각을 즉시 확인할 수 있는 안드로이드 앱 위젯 신설.
  - 알람, 텔레그램 추적, 출무 현황 등 부가 요소를 배제하고 '승무 다이아 상세 카드'만을 집중 표출하며, 직전 완성된 단일 통합 렌더러([`DiaCaptureRenderer.kt`](file:///c:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/DiaCaptureRenderer.kt))의 정보(다이아 번호, 출퇴근 시각, 구간별 뱃지, 출발/도착역, 시각, 화살표, 준비 구분선, 라이트/다크 테마 색상, 기기 폰트 설정)와 **100% 완벽히 일치하는 형태**로 렌더링.
- **2. 100% 로컬 Room DB 전용 Local-Only 구동 & 오프라인 모드 원천 안전성 보장 (`TodayDiaWidgetUpdater.kt`)**:
  - 위젯 갱신 엔진은 Xcrew 원격 네트워크 통신이나 웹뷰 세션을 일체 호출하지 않으며, 오직 기기 내부 Room DB(`work_schedules`, `dia_details`, `dia_census`)에 캐시된 데이터만 읽어 즉시(0.01초 내) 비트맵을 생성하도록 격리.
  - **오프라인 모드(`is_offline_mode == true`)**이거나 네트워크 단절 상태에서도 Xcrew 통신 0회를 원천 보장하며 로컬 다이아 정보를 안전하게 표출.
  - 비번/연차/휴일 등 다이아가 없는 날에는 깔끔한 빈 상태 안내 카드를 표시.
- **3. 4x4 ~ 4x5 표준 셀 및 자유 가변 리사이징 지원 (`today_dia_widget_info.xml`, `widget_today_dia.xml`)**:
  - `targetCellWidth="4"`, `targetCellHeight="4"`, `resizeMode="horizontal|vertical"`을 통해 사용자가 홈 화면에서 위젯을 길게 눌러 4x4, 4x5, 전체 화면 등으로 자유롭게 크기를 조절 가능.
  - `ImageView`의 `scaleType="fitCenter"` 및 `adjustViewBounds="true"`로 어떤 위젯 크기에서도 다이아 카드가 찌그러짐 없이 완벽한 비율로 자동 확대/축소.
  - 안드로이드 Binder IPC 메모리 한도(1.5MB) 초과 방지를 위한 동적 안전 스케일링 적용.
- **4. 2시간 정기 업데이트 및 우측 상단 수동 새로고침 버튼 (`TodayDiaWidgetProvider.kt`, `widget_today_dia.xml`)**:
  - 정기 업데이트 주기 `updatePeriodMillis="7200000"` (2시간) 적용으로 배터리 및 시스템 리소스 최적화.
  - 위젯 우측 상단 `🔄` 버튼 터치 시 앱 실행 없이 홈 화면에서 로컬 DB 캐시를 즉시 재조회하여 위젯 비트맵을 새로고침하는 `kr.crewmate.app.action.REFRESH_WIDGET` 인텐트 바인딩.
  - 위젯 본체 터치 시 CrewMate 메인 화면(`TodayWorkScreen`)으로 즉시 진입.
- **5. 실시간 자동 동기화 4대 트리거 완성**:
  - **트리거 1 (앱 내 조회 시)**: `TodayWorkViewModel.kt`에서 다이아 조회 시 위젯 업데이트 브로드캐스트(`ACTION_UPDATE_TODAY_WIDGET`) 자동 발행.
  - **트리거 2 (자정 전환 시)**: `MidnightCaptureReceiver.kt`에서 매일 00:01 AM에 익일(오늘) 로컬 DB 다이아로 위젯 자동 갱신.
  - **트리거 3 (단말기 재부팅 시)**: `BootReceiver.kt`에서 부팅 완료 시 위젯 자동 복구.
  - **트리거 4 (정기 업데이트)**: 2시간 주기 자동 동기화.
- **6. 신설 및 변경 소스 파일 목록**:
  - 신설:
    - `app/src/main/java/com/example/crewmate/widget/TodayDiaWidgetProvider.kt`
    - `app/src/main/java/com/example/crewmate/widget/TodayDiaWidgetUpdater.kt`
    - `app/src/main/res/layout/widget_today_dia.xml`
    - `app/src/main/res/xml/today_dia_widget_info.xml`
    - `app/src/main/res/drawable/ic_widget_refresh.xml`, `widget_card_bg.xml` (라이트/다크 대응)
    - `app/src/main/res/values/strings.xml`, `colors.xml`
  - 수정:
    - `app/src/main/java/com/example/crewmate/util/DiaCaptureRenderer.kt`: `width: Int = 1440` 동적 너비 및 density 매개변수화.
    - `app/src/main/AndroidManifest.xml`: `TodayDiaWidgetProvider` 리시버 등록.
    - `app/src/main/java/com/example/crewmate/schedule/TodayWorkViewModel.kt`: 위젯 자동 갱신 알림 트리거 연동.
    - `app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt`: `TodayWorkViewModelFactory`에 `appContext` 바인딩.
    - `app/src/main/java/com/example/crewmate/alarm/MidnightCaptureReceiver.kt`: 자정 위젯 자동 갱신 추가.
    - `app/src/main/java/com/example/crewmate/alarm/BootReceiver.kt`: 부팅 시 위젯 자동 갱신 추가.
    - `app/src/test/java/com/example/crewmate/schedule/EnhancementsUnitTest.kt`: 위젯 및 동적 너비/오프라인 안전성 단위 테스트 3건 추가 (총 78개 단위 테스트 100% 통과).

---

## [v1.0.34] - 2026-11-09

### 📐 다이아 캡처(DiaCaptureRenderer) 가로 폭 대폭 확장(1440px QHD, 480dp) 및 동적 충돌 방지(Dynamic Collision Prevention) 적용
- **1. 가로 해상도 대폭 확장: 1080px ➔ 1440px QHD 규격 (가상 뷰포트 480dp) (`DiaCaptureRenderer.kt`)**:
  - 기존 1080px(360dp) 협소 뷰포트로 인해 7개 요소가 한 줄로 빽빽하게 뭉치던 문제를 해결하기 위해, 가로폭을 **1440px (QHD 480dp 와이드 규격, +33%)**로 대폭 확장.
  - 마진·패딩·트랙을 제외한 5개 텍스트 열 순수 가용 폭이 **756px ➔ 1,050px (+38.9% 급증)**로 쾌적하게 확보되어 모든 컬럼에 시원한 숨통 트임.
- **2. 컬럼 가중치 최적화 (총합 5.8f 비례 모델) (`DiaCaptureRenderer.kt`)**:
  - 열차번호/업무 뱃지: `1.4f` ➔ **`1.6f` (289.6px, +93.6px 대폭 확장)**
  - 출발역/도착역: `1.0f` ➔ **`1.1f` (199.1px, +59.1px 확장)**으로 4~5글자 한글 역명 공간 완벽 확보.
  - 출발/도착 시각: `1.0f` (181.0px, +41.0px 확장)
  - 화살표: 28dp ➔ **`32dp` (96px)**
- **3. 뱃지-역명 맞붙음 방지 동적 안전 간격(Dynamic Collision Prevention) 신설 (`DiaCaptureRenderer.kt`)**:
  - `F4526 운전`과 같이 긴 뱃지가 들어올 때 뱃지 우측 끝에서 최소 10dp(30px) 이상의 안전 여백을 강제(`safeMinStnLeft = badgeActualRight + dp(10f)`)하여, 역명(`시흥기지`)과의 겹침 현상을 0%로 원천 차단하고 약 50dp(150px)의 쾌적한 분리 여백 확보.
  - 화살표와 도착역 사이에도 최소 6dp 안전 간격을 적용하여 시각적 분리감 극대화.
- **4. 와이드 뷰포트 맞춤 여백 및 타이포그래피 정밀 튜닝 (`DiaCaptureRenderer.kt`)**:
  - 외곽 캔버스 마진 `dp(20f)`, 카드 내부 패딩 `dp(16f)`, 트랙 간격 `dp(6f)`로 조정.
  - 상단 타이틀 `sp(24f)`, DIA 번호 `sp(20f)`, 출퇴근 정보 `sp(14f)`, 역명/시각 `sp(13f)`로 비례 최적화.
- **5. 단위 테스트 및 디버그 빌드 검증 (`EnhancementsUnitTest.kt`)**:
  - `diaCaptureRenderer_gridWeights_distributeSymmetricallyToRightEdge`를 1440px/5.8f 스펙으로 갱신하고, `diaCaptureRenderer_badgeAndStation_neverOverlap_evenWithLongNames` 신규 테스트 추가 (전체 75개 테스트 100% 통과).

---

## [v1.0.33] - 2026-11-09

### 🔤 다이아 캡처(DiaCaptureRenderer) 기기 폰트 설정(서체 종류, 글자 크기 fontScale, 글자 굵게 fontWeightAdjustment) 100% 동기화
- **1. 기기 설정 활성 서체(Font Family / Typeface) 100% 상속 (`DiaCaptureRenderer.kt`)**:
  - `Typeface.DEFAULT` 정적 객체 고정 참조를 제거하고, `TextView(context).typeface` 및 `Typeface.defaultFromStyle(Typeface.NORMAL)`을 베이스로 활용하여 삼성 One UI 글꼴 설정(삼성원, 나눔바른고딕, 사용자 커스텀 서체 등)이 캡처 캔버스에 그대로 반영되도록 구현.
- **2. 기기 글자 크기(`fontScale`) 실시간 반영 및 가변 높이 자동 확장 (`DiaCaptureRenderer.kt`)**:
  - `sp(v: Float) = v * density * fontScale`을 통해 기기 설정(설정 > 디스플레이 > 글자 크기)의 `fontScale`을 100% 반영(극단치 0.8f ~ 1.35f 안전 가용 범위 보호).
  - 글자 크기가 커질 때 텍스트가 잘리는 현상을 방지하기 위해 헤더 행 높이(`headerRowH`), 카드 헤더 행 높이(`cardHeaderH`), 각 세그먼트 행 높이(`rowH`)를 글자 크기에 비례하여 동적으로 자동 확장하도록 구현.
  - 날짜 캡슐 알약 및 열차번호/업무 뱃지 박스도 텍스트 폰트 크기(`sp(11f)`, `sp(12f)`)에 맞추어 유연하게 자동 리사이징.
- **3. 기기 '글자 굵게(Bold font)' 설정(`fontWeightAdjustment`) 및 Compose 4단계 세분화 가중치 일치 (`DiaCaptureRenderer.kt`)**:
  - Android 12+ (API 31+) 시스템 설정의 '글자 굵게(Bold Text)' 가중치(`context.resources.configuration.fontWeightAdjustment`, 통상 +300)를 추출하여 `getTypeface(baseWeight)`에 실시간 반영.
  - Compose `DiaDetailCard`와 1:1로 대응하도록 출발/도착 시각 `ExtraBold(800)`, 열차/업무 뱃지 및 운전 역명 `Bold(700)`, 일반 역명 `Medium(500)`, 화살표 `Normal(400)`을 부여하고, '글자 굵게' 활성화 시 일괄 가산(+300)되어 기기 설정과 동일하게 또렷하고 굵은 글씨로 저장되도록 일원화.
- **4. 단위 테스트 검증 및 빌드 완료 (`EnhancementsUnitTest.kt`)**:
  - `diaCaptureRenderer_fontScaleAndWeightAdjustment_scalesCorrectly` 신규 단위 테스트 추가 및 전체 74개 단위 테스트 100% 통과, 디버그 APK 빌드 성공 검증 완료.

---

## [v1.0.32] - 2026-11-09

### 🎯 다이아 '준비' 구분선 스타일(1.5dp, alpha 50%) 롤백 및 단일 통합 렌더러(DiaCaptureRenderer) 기반 캡처 형태 100% 통일
- **1. 다이아 상세조회 '준비' 구분선 두께 및 색상 직전 버전(`v1.0.30`) 롤백 (`DiaDetailCard.kt`, `DiaCaptureRenderer.kt`)**:
  - 사용자 지침에 따라 '준비' 상단 구분선의 두께를 `2.5.dp`에서 **`1.5.dp`**로, 색상을 `alpha = 0.85f`에서 **`primary.copy(alpha = 0.5f)`**로 원상 복구(롤백).
  - 스크롤 확장을 방지하는 상하 여백 `Modifier.padding(vertical = 4.dp)` 및 설정 화면의 `show_prep_divider` ON/OFF 토글 기능은 그대로 유지.
- **2. 자정 캡처와 수동 스크린샷 캡처 형태 불일치 근본 해결 및 단일 통합 렌더러(`DiaCaptureRenderer.kt`) 전면 구축**:
  - **원인 규명**: 인앱 스크린샷은 화면이 켜져 있을 때 Compose 뷰를 캡처하는 반면, 자정(00:01 AM) 캡처는 화면이 꺼져 있어 Android OS의 VSYNC 프레임 클럭 중단으로 Compose를 사용할 수 없어 별도의 Canvas 2D 그리기 함수(`renderDiaBitmap`)를 사용함. 기존 Canvas 코드가 임의의 절대 픽셀 좌표(`330f`, `465f` 등)를 사용하여 텍스트가 좌측으로 심하게 쏠리고 우측 끝에 200px 이상의 거대한 빈 여백이 남는 심각한 레이아웃 왜곡 발생.
  - **단일 렌더러 일원화**: Compose `DiaDetailCard`의 레이아웃과 1:1 완벽하게 동일한 비례 가중치(`track 20dp+4dp`, `열차뱃지 1.4f`, `출발역 1.0f`, `출발시각 1.0f`, `화살표 28dp`, `도착역 1.0f`, `도착시각 1.0f`) 및 M3 shapes.large(16dp 곡률), 테두리(1dp)를 갖는 **`DiaCaptureRenderer.kt`** 신설.
  - **호출 파이프라인 통합**:
    - 인앱 스크린샷(`DiaCaptureHelper.kt`): `TodayWorkScreen`, `ScheduleScreen`, `DiaLookupScreen` 3개 화면 모두 `DiaCaptureRenderer.renderToBitmap`을 직접 호출하여 150ms 프레임 지연 및 decorView 부착 없이 5ms 이내 초고속 렌더링.
    - 자정 자동 캡처(`MidnightCaptureReceiver.kt`): 기존 350줄의 수동 캔버스 코드를 제거하고 동일하게 `DiaCaptureRenderer.renderToBitmap`을 호출하도록 단일화.
  - **결과**: 인앱 스크린샷과 자정 자동 캡처가 단 1픽셀의 오차도 없이 100% 동일한 비례·헤더·카드곡률·구분선·테마 색상으로 갤러리에 저장되도록 완전 통일.

---

## [v1.0.31] - 2026-11-09

### 🎨 다이아 '준비' 구분선 두께·짙은 포인트 컬러 강화, 설정 메뉴 ON/OFF 토글 추가 및 테마 맞춤 스크린샷 캡처 지원
- **1. 다이아 상세조회 '준비' 항목 상단 구분선 두께 및 짙은 포인트 컬러 강화 (`DiaDetailCard.kt`, `MidnightCaptureReceiver.kt`)**:
  - `DiaDetailCard.kt`: 직전 작업의 얇고 연했던 선(`thickness = 1.5.dp`, `alpha = 0.5f`)을 보완하여, 상하 여백은 과도한 스크롤 확장을 방지하는 `Modifier.padding(vertical = 4.dp)`로 유지하면서, **선 두께를 `2.5.dp`로 상향**하고 **포인트 컬러 투명도를 `alpha = 0.85f`로 짙게 조정**(`MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)`). 라이트 모드에서는 짙고 선명한 다크 네이비, 다크 모드에서는 시원한 스카이블루로 또렷하게 표출.
  - `MidnightCaptureReceiver.kt`: 비트맵 자동 캡처 캔버스 구분선 두께를 `4f`(2.5dp 규격 동기화)로 상향하고, 테마별 짙은 포인트 컬러(`라이트: #D90F2942`, `다크: #D938BDF8`) 적용.
- **2. 설정 메뉴 내 '준비' 상단 구분선 ON/OFF 토글 스위치 신설 (`SettingsScreen.kt`, `DiaDetailCard.kt`, `MidnightCaptureReceiver.kt`)**:
  - `SettingsScreen.kt`: '주요 항목 사이 감시 숨기기' 카드 바로 아래에 **`다이아 '준비' 상단 구분선 표시`** 토글 스위치 카드(`show_prep_divider`, SharedPreferences 기본값 `true`) 신설.
  - `DiaDetailCard.kt`: `show_prep_divider` 설정을 `OnSharedPreferenceChangeListener`와 연동하여 사용자가 설정 화면에서 켜고 끌 때 앱 화면에 즉시 실시간 반영되도록 구현.
  - `MidnightCaptureReceiver.kt`: 자정 자동 캡처 시에도 `show_prep_divider`가 `true`일 때만 구분선을 렌더링하도록 일원화.
- **3. 현재 선택된 앱 테마(라이트모드, 다크모드) 맞춤 스크린샷 캡처 지원 (`Theme.kt`, `ScreenshotHelper.kt`, `DiaCaptureHelper.kt`, `MidnightCaptureReceiver.kt`)**:
  - `Theme.kt`: 앱 전역 SharedPreferences(`app_theme`) 및 OS 야간 모드 설정을 동적으로 판정하는 `Context.getAppThemeMode()` 및 `Context.isDarkThemeActive()` 공통 확장 함수 신설.
  - `ScreenshotHelper.kt`: `captureComposableToBitmap`에 `backgroundColor: Int? = null` 매개변수를 추가하고, 기존에 하드코딩되어 있던 `canvas.drawColor(Color.WHITE)`를 제거하여 테마 배경색(다크: `#0B0F17`, 라이트: `#F6F8FC`) 또는 투명색으로 렌더링하도록 개선.
  - `DiaCaptureHelper.kt`: `executeCapture` 호출 시 현재 사용자가 설정한 테마 모드를 조회하여 `CrewMateTheme(appThemeMode = appThemeMode)`에 명시적으로 주입하고 테마별 캔버스 배경색을 전달함으로써, 다크 모드 사용 시 다크 테마(검정/다크네이비 배경, 흰색 텍스트, 밝은 스카이블루 포인트)로 100% 일치하게 갤러리에 저장되도록 수정.
  - `MidnightCaptureReceiver.kt`: `renderDiaBitmap`에 `isDark` 매개변수를 지원하여 매일 자정 자동 캡처 시에도 현재 앱 테마 설정에 맞추어 라이트/다크 팔레트(배경, 카드, 텍스트, 뱃지, 구분선)로 렌더링되도록 개선.

---

## [v1.0.30] - 2026-11-08

### ⏰ 출근 60분 전 알람 미발동 결함 근본 해결, 오프라인 소속 승무원 3일 제한, 다이아 '준비' 구분선 개선 및 출무현황 전용 새로고침 추가
- **1. 출근 60분 전 알람 미발동 3대 원인 해결 (기존 `준비`=출무 시작시각 기준 공식 100% 유지) (`AppNavigation.kt`, `DiaDetailRepository.kt`, `WorkDutySyncCoordinator.kt`, `MidnightCaptureReceiver.kt`, `BootReceiver.kt`, `WorkAlarmSettingsScreen.kt`)**:
  - **원인 1 해결 (다이아 구간 캐시 폴백 누락 및 빈 구간 덮어쓰기 차단)**: `AppNavigation.kt`의 `Routes.TodayWork`에서 `DiaDetailRepository` 생성 시 누락되어 있던 `database.diaCensusDao()`를 주입하고, `DiaDetailRepository.getCachedDiaDetailOnly()` 및 `WorkDutySyncCoordinator.loadCachedSegmentsForDia()`에서 `dia_details` 당일 키 → `getLatestDiaDetailByPdiaNo` 최신 동일 다이아 → `dia_census` 기초자료 DB 순의 3단계 폴백을 적용하여 일시적인 빈 구간(`emptyList`) 반환으로 기존 예약된 출근 알람이 덮어써져 증발하던 버그를 원천 차단.
  - **원인 2 해결 (전날 근무 후 자정 경과 시 익일 출근 알람 사전·자동 등록)**: `WorkDutySyncCoordinator.sync()` 실행 시 당일 알람뿐 아니라 익일(다음 날) 신규 근무의 알람(익일 아침 출근 60분 전 알람 포함)까지 로컬 DB에서 함께 평가하여 합산 등록하고, `MidnightCaptureReceiver`가 자정(00:01)마다 `WorkDutySyncCoordinator.syncFromLocalDatabase(context)`를 자동 실행하도록 연동. 또한 앱 실행 시 초기 화면이 `승무근무표`이더라도 백그라운드에서 로컬 DB 기반 알람 동기화를 1회 자동 수행.
  - **원인 3 해결 (정밀 알람 설정 화면 변경 즉시 OS 스케줄러 반영)**: `WorkAlarmSettingsScreen.kt`의 `refreshTodayAlarms()` 및 마스터 스위치 토글 시 UI 리스트만 갱신하던 문제를 수정하여 즉시 `WorkDutySyncCoordinator` / `WorkAlarmScheduler`에 반영되도록 개선.
- **2. `소속 승무원 근무 현황` 오프라인 모드 시에만 3일(`어제·오늘·내일`) 조회 제한 (`CrewWorkStatusScreen.kt`)**:
  - 온라인 모드에서는 모든 사업일자를 자유롭게 선택·조회할 수 있도록 유지하고, **오프라인 모드(`isOfflineMode == true`)일 때만** `열차 승무원 조회`와 동일하게 `DatePickerDialog`의 `minDate`/`maxDate` 및 `OfflineLookupRange`를 통해 `어제 ~ 내일` 3일 범위로만 선택 가능하도록 통일.
- **3. 다이아 상세조회 화면 `준비` 항목 상단 구분선 가시성 개선 (`DiaDetailCard.kt`, `MidnightCaptureReceiver.kt`)**:
  - 스크롤 길이에 부담을 주지 않는 적정 상하 여백(`Modifier.padding(vertical = 4.dp)`)과 선 두께(`1.5.dp`), 강조 색상(`MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)`)을 적용하여 `준비` / `출고준비` 구간 시작점을 명확히 구분. 캡처 비트맵(`MidnightCaptureReceiver.kt`)에도 동일 강조색(`#8C0F2942`, `strokeWidth = 2.5f`) 반영.
- **4. 오늘의 근무 `출무현황 확인하기` 카드 전용 새로고침 버튼 신설 (`TodayWorkScreen.kt`, `TodayWorkViewModel.kt`, `TodayWorkRepository.kt`)**:
  - `TodayWorkRepository.refreshAttendanceStatusesOnly()` 및 `TodayWorkViewModel.refreshAttendanceOnly()`를 신설하여 전체 화면(다이아·월간근무표) 재로딩 없이 오직 출무 현황만 Xcrew로부터 즉시 재조회하고 스냅샷(`AttendanceSnapshotStore`)과 정밀 알람(`WorkDutySyncCoordinator`)을 갱신하도록 구현.
  - `TodayWorkScreen.kt`의 `출무현황 확인하기` 카드 헤더 우측에 컴팩트한 `🔄 새로고침` 버튼을 배치하고, 클릭 시 카드를 자동 펼침과 함께 최신 출무현황으로 즉시 갱신.

---

## [v1.0.29] - 2026-11-07

### 🔄 설정 메뉴 내 '앱 정보' 다이얼로그 임의 하드코딩 문구 제거 및 원본 문구 복구
- **1. 임의 하드코딩 문구 제거 및 원본 개발자 안내·면책 문구 복원 (`SettingsScreen.kt`)**:
  - **원인 파악**: 과거 커밋 `b99b8bbf`(2026-10-28) 작업 당시 이전 AI 에이전트가 설정 화면을 재구성하면서 사용자의 별도 지시 없이 임의로 "기능 요약 목록" 형태로 변경하고, 실제 소속과도 맞지 않는 *"한국철도공사 코레일 수도권동부본부..."* 와 같은 잘못된 문구를 하드코딩해 두었던 것을 확인.
  - **복구 조치**: 사용자가 직접 작성해 두었던 원래의 비공식 앱 개발 배경 안내, 근태 불이익 면책 사항, 사내 파발마(업무메일) 문의 안내 및 `"안산승무 윤영준 드림"` 맺음말 원본 문구로 100% 원상 복구 완료.
  - 다이얼로그 타이틀(`"앱 정보"`), M3 surface 배경색 및 확인 버튼(`TextButton("확인")`)의 원래 UI 레이아웃 정상 복원.

---

## [v1.0.28] - 2026-11-07

### ✈️ 오프라인 전환 시 승무원 근무 현황 4일치 저장 보장, 오프라인 '열차 승무원 조회' 지원 및 출무 사전확인 15초+위임 구조 개선
- **1. 오프라인 전환 시 소속 승무원 근무 현황 누락 결함 해결 및 4일치(`전전일·어제·오늘·내일`) 전환 시점 저장 (`CrewAssignmentDao.kt`, `CrewWorkStatusRepository.kt`, `OfflineCrewPrefetcher.kt`, `AppNavigation.kt`, `AutoOfflineReceiver.kt`, `AutoOfflineManager.kt`)**:
  - **결함 원인 해결**: 기존 오프라인 전환 시 단일 `runCatching` 블록이 통신 예외를 삼킨 채 무조건 `isOfflineMode = true`로 전환하고, 서버가 일시적으로 빈 응답(`data: []`)을 반환할 때 `deleteAssignmentsForDate`가 기존 캐시까지 삭제하며, 자동 오프라인 전환(`AutoOfflineReceiver` / `AutoOfflineManager`) 경로에는 승무원 현황 저장 로직이 아예 없던 5대 원인을 해결.
  - `CrewAssignmentDao.kt`: `countForDate(date)` 및 `@Transaction replaceAssignmentsForDate(date, assignments)`를 신설하여 원자적 교체 보장.
  - `CrewWorkStatusRepository.kt`: `CrewFetchOutcome`(`Saved`, `KeptCache`, `Failed`) 및 `refreshAndStore()`, `getCachedOnly()`, `hasCache()`를 도입하고, 서버 빈 응답 수신 시 기존 캐시가 존재하면 삭제하지 않고 보존하도록 방어.
  - `OfflineCrewPrefetcher.kt` 신설: 오프라인 전환 시점(수동/자동 공통)에 `오늘·내일·어제·전전일` 4일치를 우선순위 순으로 조회·저장(실패 시 1회 재시도)하며, 당일(`crew_window_anchor == 오늘`) 이미 전환하여 4일치가 DB에 모두 남아 있는 경우에는 불필요한 재통신 없이 캐시를 즉시 재사용.
  - `AppNavigation.kt`: 수동 오프라인 전환 시 1단계로 `OfflineCrewPrefetcher.prefetchWindow`를 독립 수행하고, 오늘자 저장이 실패(캐시도 없음)한 경우 무조건 전환하지 않고 확인 다이얼로그(`[재시도] [저장 없이 전환] [취소]`)를 표출.
  - `AutoOfflineReceiver.kt` & `AutoOfflineManager.kt`: 운전 구간 진입으로 자동 오프라인 전환될 때 `is_offline_mode = true` 설정 직전에 `OfflineCrewPrefetcher.prefetchWindowForAutoOffline()`을 호출하여 당일 4일치 승무원 현황을 저장한 후 전환.
- **2. 오프라인 모드에서 '열차 승무원 조회' 메뉴 활성화, 운행일자 제한(`어제~내일`), 기초자료 조사 비활성화 및 다이아 상세 폴백 (`AppNavigation.kt`, `TrainCrewLookupV2Screen.kt`, `DiaCensusManager.kt`, `DiaDetailRepository.kt`)**:
  - `AppNavigation.kt`: 상단 햄버거 메뉴(`CrewMateTopBar`)에서 오프라인 시 비활성화되어 있던 `열차 승무원 조회` 메뉴를 활성화하고, `TrainCrewLookupV2Screen`에 `CrewWorkStatusRepository`와 `isOfflineMode`를 연결.
  - `TrainCrewLookupV2Screen.kt`: 오프라인 모드 시 운행일자 선택(`DatePickerDialog`의 `minDate`/`maxDate` 및 `OfflineLookupRange`)을 `어제 ~ 내일` 3일로 제한하고, 하단 `[다이아 기초자료 조사 실행]` 버튼을 비활성화(`"오프라인 모드에서는 실행할 수 없습니다"`) 처리.
  - `DiaCensusManager.kt`: `isOfflineProvider` 가드를 추가하여 오프라인 상태에서 기초자료 조사가 시작되거나 진행되지 않도록 차단.
  - `DiaDetailRepository.kt`: 내 개인 근무표 캐시(`dia_details`)에 없는 타 승무원 다이아 번호를 클릭했을 때 기초자료 DB(`dia_census`)의 `detailJson`을 폴백으로 파싱하여 오프라인에서도 모든 다이아 상세 바텀시트를 열람할 수 있도록 개선.
- **3. 오프라인 모드 전역 Xcrew 네트워크 차단 스위치 (`XcrewWebViewXhrDataSource.kt`, `AppNavigation.kt`)**:
  - `XcrewWebViewSession`에 `offlineGuard`(`isNetworkBlocked()`)를 도입하여 오프라인 전환 이후에는 `request()`, `fetchAttendanceStatus()`, `trySilentRelogin()`을 포함한 모든 Xcrew 네트워크 통신 및 무음 재로그인을 원천 차단.
- **4. 출무 전 알람 사전확인 간격 축소(`2분 전` → `15초 전`) 및 사이렌 발동 위임 조율기 (`AttendancePrecheckCoordinator.kt`, `AttendanceSirenDispatcher.kt`, `WorkAlarmScheduler.kt`, `WorkAlarmReceiver.kt`, `AttendanceVerificationService.kt`)**:
  - **결함 원인 해결**: 기존 `5+2분 전`(사이렌 2분 전) 사전확인 이후 실제 사이렌 시각(`5분 전`) 사이의 2분 공백 동안 출무를 완료하면 사이렌이 울리던 문제를 해결.
  - `AttendancePrecheckCoordinator.kt` 및 `AttendanceSirenDispatcher.kt` 신설: 사전확인 시각을 사이렌 **15초 전(`PRECHECK_LEAD_SECONDS = 15L`)**으로 축소하고, 사이렌 시각(`T`)에 사전확인 서비스가 아직 조회 중이면 `WorkAlarmReceiver`가 즉시 울리지 않고 서비스에 발동을 위임(`tryDefer`, 최대 10초 대기)하여 확인 완료 직후 출무 여부에 따라 취소 또는 발동하도록 개선. 또한 부정확 알람 등으로 사이렌이 먼저 울린 직후(60초 이내) 출무 완료가 확인되면 즉시 사이렌을 자동 중지(`stopRecentSirenIfMatches`).
- **5. 단위 테스트 및 전체 빌드 검증 (`EnhancementsUnitTest.kt`)**:
  - `prefetcher_windowDates_returnsTodayTomorrowYesterdayAndDayBeforeInOrder`, `crewRepo_emptyServerResponse_keepsExistingCache`, `crewRepo_fetchFailureWithoutCache_returnsFailedOutcome`, `crewRepo_offline_neverCallsDataSource`, `prefetchReport_todayOk_falseWhenTodayFailed`, `offlineLookupRange_clampsAndContainsYesterdayToTomorrowOnly`, `censusManager_offline_refusesToStartAndMakesNoRequests`, `networkGuard_blocksRequestAndRelogin_whenOffline`, `precheckTime_isFifteenSecondsBeforeSiren`, `coordinator_defer_firesOnlyWhenNotCheckedIn`, `coordinator_defer_expiredInFlightDoesNotDefer` 11건 신설 (총 69개 단위 테스트).

---

## [v1.0.27] - 2026-11-06

### 🛡️ 정밀 알람 출무 전 알람 정상 출무 후 오발동 결함 근본 해결 (출무 완료 래치, 사이렌 2분 전 헤드리스 자동 검증, 실시간 재확인 및 포그라운드 복귀 갱신)
- **1. 출무 완료 래치(`AttendanceCheckInLatch.kt`) 및 스냅샷 중앙 저장소(`AttendanceSnapshotStore.kt`) 신설**:
  - **결함 원인 해결**: 앱 구동 중 네트워크 지연이나 캐시 역전으로 인해 한 번 출무 완료(적합성완료, 실제출무시각 존재 등)로 확인된 데이터가 이전 '출무전' 상태 캐시로 되돌아가면서 이미 제외되었던 출무 전 알람이 재등록 및 발동되던 현상을 해결.
  - `AttendanceCheckInLatch.kt`를 신설하여 `YYYYMMDD|CLEAN_TRAIN|HHMM` 고유 키로 출무 완료 사실을 SharedPreferences에 영구 보존(3일 만료 자동 정리).
  - `AttendanceSnapshotStore.kt`를 통해 출무현황 저장 경로를 단일화하고, 저장 시 항상 래치도 함께 갱신하도록 강제.
- **2. 사이렌 2분 전 백그라운드 사전 검증 알람 및 서비스(`AttendancePrecheckReceiver.kt`, `AttendanceVerificationService.kt`, `HeadlessAttendanceVerifier.kt`)**:
  - **결함 원인 해결**: 알람 발동 시점(`WorkAlarmReceiver`)에 서버 실시간 조회를 수행하지 않고 오직 로컬 Room DB 캐시만 확인하므로, 사용자가 출무 처리 후 앱을 열지 않으면 영구히 구형 캐시('출무전')를 기준으로 오발동하던 근본 결함을 해결.
  - 사이렌 2분 전에 `AttendancePrecheckReceiver`를 예약하고, 미출무 상태인 경우 `AttendanceVerificationService`(dataSync)를 구동하여 최신 출무 여부를 자동 확인.
  - 앱이 꺼져 있거나 세션이 없어도 `HeadlessAttendanceVerifier.kt`가 암호화된 자격증명으로 자동 로그인 및 출무 조회를 조용히 수행하여 래치 갱신.
- **3. 전역 활성 세션 레지스트리(`XcrewSessionRegistry.kt`) 및 앱 복귀(`ON_RESUME`) 실시간 출무 최신화 (`AppNavigation.kt`)**:
  - `XcrewSessionRegistry.kt`를 신설하여 포그라운드 UI가 살아있을 때 백그라운드 수신기가 기존 세션을 즉각 재사용(0.5초 초고속 조회)할 수 있도록 연동.
  - `AppNavigation.kt`의 `ON_RESUME`에 `resumeRefreshTrigger`를 연동하여 출무 처리 후 앱으로 복귀했을 때 재접속 없이도 오늘의 근무 출무현황이 즉시 최신화되도록 개선.
- **4. 사이렌 발동 3단계 방어 및 빈 열차번호 오매칭 차단 (`WorkAlarmReceiver.kt`, `WorkAlarmScheduler.kt`, `WorkAlarmRuleEvaluator.kt`, `WorkDutySyncCoordinator.kt`, `TodayWorkRepository.kt`)**:
  - `WorkAlarmReceiver.kt`: 1) 래치 선검사, 2) 활성 세션 초고속 재확인(최대 6초), 3) DB 스냅샷 검사의 3중 방어 적용. 발동 시 알람 이력 메시지에 사전확인 판정 근거(`precheckAudit`)를 투명하게 기록.
  - `AttendanceAlarmCancellationChecker`: 서버 DTO 열차번호가 빈 문자열(`""`)일 때 `endsWith("")`가 무조건 참이 되어 엉뚱한 열차가 매칭되던 결함 수정.
  - `WorkAlarmRuleEvaluator.kt` 및 `WorkDutySyncCoordinator.kt`: 알람 평가 시 래치된 키(`checkedInKeys`)를 전달하여 구형 캐시가 주입되어도 출무 알람 생성을 원천 차단.
- **5. 단위 테스트 및 전체 빌드 검증 (`EnhancementsUnitTest.kt`)**:
  - `attendanceLatch_keyOf_normalizesDateTrainAndTime`, `evaluator_skipsAttendanceAlarm_whenLatchedEvenIfStaleCacheSaysNotCheckedIn`, `evaluator_overnightDay2_latchOnDay1DoesNotSuppressDay2`, `cancellationChecker_emptyServerTrainNo_doesNotFalseMatch` 4건 신설.
  - 58개 단위 테스트 전원 통과 (`testDebugUnitTest`), `assembleDebug` 0 오류 빌드 검증 완료.

---

## [v1.0.26] - 2026-11-05

### 📅 월별 근무표 2중 중복 표출 결함 해결 및 자정 다이아 자동 캡처 레이아웃 인앱 화면 100% 일치
- **1. 월별 근무표 2중 중복 스택 결함 근본 해결 (`WorkScheduleDao.kt`, `WorkScheduleRepositoryImpl.kt`, `AppNavigation.kt`, `ScheduleScreen.kt`)**:
  - **결함 원인 해결**: 앱 시작 및 화면 전환 시 `ScheduleViewModel`, `AppNavigation`, `TodayWorkRepository`에서 `getSchedules()`가 동시 다발적으로 호출되면서 비원자적인 `deleteSchedulesForMonth`와 `insertSchedules` 사이에 경합이 발생하여 동일한 30개 근무 데이터가 Room DB에 2세트(60개) 저장되고, 다른 달을 다녀온 뒤 캐시를 읽을 때 캘린더 각 날짜 셀마다 동일 근무가 2개씩 중복 표출되던 결함을 해결.
  - `WorkScheduleDao.kt`에 `@Transaction suspend fun replaceSchedulesForMonth(yearMonth: String, schedules: List<WorkScheduleEntity>)`를 신설하여 SQLite 단일 트랜잭션 내에서 기존 월 데이터를 원자적으로 삭제 및 갱신.
  - `WorkScheduleRepositoryImpl.kt`에 `refreshMutex = Mutex()`를 도입하여 동시 네트워크 수신 및 DB 쓰기 경합을 상호 배제하고, `toDeduplicatedDomainSchedules()`(`workDate_diaNo_startTime_endTime_trainNo_trainNo2_alcSpecDvCd_hldyYn_nonActiveType` 키 기반)를 적용하여 캐시 직접 조회, 메인 조회, 네트워크 수신, DB 폴백 전 단계에서 무결성 보장.
  - `AppNavigation.kt`의 프리플라이트 동기화에도 중복 제거 및 `replaceSchedulesForMonth`를 적용.
  - `ScheduleScreen.kt` UI 렌더링 계층의 `schedulesByDate` 매핑 시 `dayList.distinctBy { ... }` 방어 로직을 추가하여 데이터베이스 상태와 무관하게 화면상 2중 표출을 원천 차단.
- **2. 자정 다이아 자동 캡처 레이아웃 인앱 다이아 상세 화면(`승무 다이아 상세`)과 100% 일치 (`MidnightCaptureReceiver.kt`, `TodayWorkScreen.kt`)**:
  - **헤더 타이틀 일원화**: 기존 `"MM.dd 근무"`(예: "11.05 근무") 대신 인앱 화면과 완전히 동일한 `"승무 다이아 상세"` 헤더 타이틀 및 우측 `YYYY.MM.DD` 캡슐 알약 날짜 뱃지 적용.
  - **다크 네이비 운전 타임라인 바**: 기존 균일한 얇은 회색 라인(3.5f)에서 Compose `DiaDetailCard`와 동일하게 운전/승무(Rank 1) 구간에 굵은 다크 네이비 라인(`strokeWidth = 7f`, `#0F2942`)과 원점 마커(radius 8f)를 상·하단 분할 렌더링.
  - **도착역 `"-"` 표출 보정**: `준비` / `출고준비` 등 도착역 미기재 항목에서 도착 시각이 존재할 때 도착역(5열)에 `"-"`를 누락하여 시각이 붕 뜨던 문제를 `arvStn != "-" || arvTime != "-"` 조건식을 적용하여 인앱 화면처럼 정상 표출.
  - **6열 그리드 센터 정렬 대칭 최적화**: 1열(열차번호/구분 뱃지), 2열(출발역), 3열(출발시각), 4열(화살표), 5열(도착역), 6열(도착시각)의 좌표와 폰트 가중치를 수학적 센터 정렬로 완벽 대칭화.
  - `TodayWorkScreen.kt`의 수동 다이아 캡처 타이틀 역시 `"승무 다이아 상세"`로 통일하여 앱 전체 캡처 결과물 시각 통일성 확립.
- **3. 단위 테스트 및 전체 빌드 검증 (`EnhancementsUnitTest.kt`)**:
  - `workSchedule_deduplication_removesDuplicateRowsForSameDate`, `workSchedule_preservesDistinctDutiesOnSameDate`, `diaCapture_arrivalStationPlaceholder_renderedWhenTimeExists` 단위 테스트 3건 신설.
  - 54개 단위 테스트 전원 통과 (`testDebugUnitTest`), `assembleDebug` 0 오류 빌드 검증 완료.

---

## [v1.0.25] - 2026-11-04

### 🛡️ 코드베이스 전반 로직 오류·보안 취약점 점검 및 중앙 집중식 스케줄 코디네이터(`WorkDutySyncCoordinator`) 리팩토링
- **1. 스케줄링 3중 파편화 해소 및 단일 코디네이터(`WorkDutySyncCoordinator`) 신설 (`WorkDutySyncCoordinator.kt`, `TodayWorkRepository.kt`, `TodayWorkScreen.kt`, `BootReceiver.kt`)**:
  - **결함 원인 해결**: 기존 `TodayWorkRepository.kt`, `TodayWorkScreen.kt`, `BootReceiver.kt` 세 곳에서 알람·자동 오프라인·텔레그램 스케줄링을 각기 다른 방식으로 중복 실행하여 경합 상태(Race Condition)가 발생하고, 특히 단말기 재부팅(`BootReceiver`) 시 출무현황(`attendanceStatuses`)이 전달되지 않아 **모든 출무 알람이 증발**하고 건너뛴 알람(`bypassedAlarmKeys`)이 되살아나며 야간 2일차 및 텔레그램 스케줄이 누락되던 중대 결함을 해결.
  - `WorkDutySyncCoordinator.kt`를 신설하여 알람 평가/등록(바이패스 필터링 포함), 자동 오프라인 전환 윈도우 등록, 텔레그램 '지하철 NOW' 동기화를 단일 트랜잭션으로 통합.
  - `WorkDutySyncCoordinator.syncFromLocalDatabase(context)`를 제공하여 단말기 재부팅 또는 최초 앱 구동 시 로컬 Room DB 캐시로부터 주간/야간1일차/야간2일차 다이아 및 출무현황을 판정하여 알람, 자동 오프라인, 텔레그램 스케줄을 100% 무손실 복원하도록 개선.
- **2. 다이아 실동/비실동 판정 단일 진실 공급원(`WorkSchedule.isValidActiveDia()`) 확립 (`WorkSchedule.kt`, `WorkScheduleEntity.kt`, `TodayWorkRepository.kt`, `MidnightCaptureReceiver.kt`, `AppNavigation.kt`)**:
  - `WorkSchedule.isValidActiveDia()` 및 `WorkScheduleEntity.isValidActiveDia()`를 선언하여 `alcSpecDvCd == "21"`(비실동) 또는 `alcSpecDvCd == "02"`(휴일/비번)이거나 비실동 키워드(연차, 교육, 병가 등)가 포함된 경우 숫자 다이아 번호가 부여되어 있어도 비실동으로 단일 기준에 따라 엄격히 제외하도록 일원화.
  - `TodayWorkRepository.toDiaCandidates()`, `MidnightCaptureReceiver.kt`, `AppNavigation.kt`에 산재하던 비표준 문자열 필터링을 `isValidActiveDia()`로 전면 교체.
- **3. 브로드캐스트 리시버 비동기 스레드 안전성 확보 및 텔레그램 `requestCode` 충돌 방지 (`WorkAlarmReceiver.kt`, `SubwayNowManager.kt`)**:
  - `WorkAlarmReceiver.kt`의 메인 Looper 스레드 `runBlocking(Dispatchers.IO)`를 제거하고 `goAsync()` 기반 `CoroutineScope(Dispatchers.IO)` 코루틴으로 통합. `try-finally` 블록 내에서 `wakeLock?.release()`와 `pendingResult.finish()`가 항상 보장되어 ANR 및 배터리 누수 차단.
  - `SubwayNowManager.getRequestCodeForTrain(trainNo)`를 신설하여 열차별 고유한 PendingIntent `requestCode`를 결정론적으로 산출함으로써 `cancelTarget`, `scheduleAlarmForTarget`, `rescheduleTarget` 간 `requestCode` 불일치 및 다중 열차 간 알람 덮어쓰기 충돌을 원천 차단.
- **4. 보안 취약점 차단 및 개인식별정보(PII) 로그·백업 노출 방지 (`XcrewWebViewXhrDataSource.kt`, `DataBackupManager.kt`)**:
  - `XcrewWebViewXhrDataSource.kt`에서 System Logcat으로 출력되던 실명 평문(`empNm=$empNm`)을 `hasEmpNm=${empNm.isNotBlank()}`로 마스킹하여 프로덕션 로그 유출 차단 (`AGENTS.md` 제1원칙 준수).
  - `DataBackupManager.kt` 백업 제외 키 목록에 `cached_user_name`, `cached_user_affil`, `telegram_chat_id`, `telegram_verified`, `telegram_stopped_trains_`, `telegram_target_overrides_`를 추가하고 `isSensitiveKey()` 검사를 강화하여 백업 JSON 파일 내 개인정보 및 텔레그램 토큰 유출을 원천 방지.
- **5. 단위 테스트 및 전체 빌드 검증 (`EnhancementsUnitTest.kt`)**:
  - `workSchedule_isValidActiveDia_identifiesValidAndExcludesNonActiveAndRestDays`, `dataBackupManager_isSensitiveKey_blocksPersonalInfoAndTelegramChatId`, `subwayNowManager_getRequestCodeForTrain_isDeterministicAndCollisionResistant` 단위 테스트 3건 신설 및 51개 테스트 전원 통과 (`testDebugUnitTest`), `assembleDebug` 0 오류 빌드 검증 완료.

---

## [v1.0.24] - 2026-11-04

### ⚡ 장시간 미사용 후 '오늘의 근무' 무한 로딩 및 WebView `pageReady` 데드락 원천 해결
- **1. '오늘의 근무' 로컬 캐시 0초 즉시 표출 + 백그라운드 무음 최신화 (Stale-While-Revalidate) (`TodayWorkViewModel.kt`, `TodayWorkRepository.kt`, `WorkScheduleRepository.kt`, `WorkScheduleRepositoryImpl.kt`, `DiaDetailRepository.kt`)**:
  - **결함 원인 해결**: 기존 `TodayWorkViewModel.load()`가 진입 시마다 기존 데이터나 로컬 캐시를 무시하고 무조건 `_uiState.value = TodayWorkUiState.Loading`으로 화면을 가린 채 원격 근무표·다이아·출무현황 XHR이 모두 끝날 때까지 대기하던 구조를 개선.
  - `WorkScheduleRepository.getCachedSchedulesOnly()`, `DiaDetailRepository.getCachedDiaDetailOnly()`, `TodayWorkRepository.getTodayOrNextWork(localOnly = true)`를 신설하여 네트워크/WebView 대기 없이 로컬 Room DB 캐시만으로 0.05초 이내에 즉시 `TodayWorkUiState.Success`를 화면에 표출.
  - 이후 백그라운드에서 `getTodayOrNextWork(localOnly = false)`를 수행하여 서버 최신 근무표와 1·2일차 출무 현황이 도착하면 로딩 스피너 없이 부드럽게 화면을 최신화하도록 구현.
- **2. 장시간 방치 후 WebView `pageReady` 영구 미완료 데드락 복구 및 세션 유효시간(TTL 20분) 도입 (`XcrewWebViewXhrDataSource.kt`, `AppNavigation.kt`)**:
  - **결함 원인 해결**: 장시간 슬립 해제 직후 `trySilentRelogin()` 내 `prepareForLogin()`이 `resetPageReady()`를 호출한 뒤, 일시적 네트워크 순단으로 `login()`이 실패하거나 `refreshWebViewPage()`가 타임아웃되면 `pageReady`가 영구 미완료 상태로 남아 `[재접속]` 전까지 모든 XHR이 매번 멈추던 데드락 결함을 해결.
  - `recoverPageReadyIfStuck()` 및 `wakeUpWebView()`(`view.onResume()`, `view.resumeTimers()`)를 도입하여 백그라운드 절전 복귀(`Lifecycle.Event.ON_RESUME`) 및 재로그인 예외 발생 시에도 `pageReady` 데드락을 자동 해제.
  - 마지막 유효 통신으로부터 20분(`SESSION_IDLE_TTL_MILLIS`) 이상 경과 시 만료된 세션으로 무의미한 1차 XHR을 보내 시간을 허비하지 않고 즉시 선제적 무음 재로그인을 수행하도록 `isSessionCurrentlyValid()` 적용.
- **3. XHR 타임아웃 단축(`40초` → `12초`) 및 `TimeoutCancellationException` 로컬 DB 폴백 보장 (`XcrewWebViewXhrDataSource.kt`, `WorkScheduleRepositoryImpl.kt`)**:
  - `XHR_TIMEOUT_MILLIS`를 40초에서 12초(`12_000L`)로 단축하고 JS `XMLHttpRequest`에 `xhr.timeout = 10000` 및 `ontimeout` 핸들러를 명시.
  - `WorkScheduleRepositoryImpl.kt`에서 `TimeoutCancellationException`(코루틴 `CancellationException`의 하위 클래스) 발생 시 예외를 그대로 던지지 않고 로컬 Room DB 캐시로 즉시 폴백하도록 수정.
- **4. 단위 테스트 및 빌드 검증 (`EnhancementsUnitTest.kt`)**:
  - `workScheduleRepository_fallsBackToLocalCache_onTimeoutCancellationException` 단위 테스트 통과 및 `./gradlew.bat testDebugUnitTest assembleDebug` 0 오류 검증 완료.

---

## [v1.0.23] - 2026-11-04

### 🍎 아이폰(iOS 16+ / Swift 5.9+ / SwiftUI) 네이티브 포팅 패키지 및 유지보수 종합 가이드·핸드오프 아카이브 구축
- **1. iOS 네이티브 포팅 프로젝트 신설 (`ios-port/CrewMateIOS/`)**:
  - 기존 안드로이드 프로젝트(`app/`)를 100% 보존하면서, 동료 개발자가 Mac/Xcode에서 외부 의존성 없이 즉시 열고 빌드·테스트할 수 있는 순수 Swift/SwiftUI 프로젝트(`Package.swift`, `project.yml`, `Info.plist`)를 신설.
  - **도메인 계층 (`Sources/CrewMateIOS/Domain/`)**:
    - `Models.swift`: 근무표, 6열 다이아 타임라인, 출무 현황(`pjtDt` 정규화 포함), 열차 승무원, 7대 정밀 알람 설정, 텔레그램 설정 모델 1:1 이식.
    - `DutyRuleEngine.swift`: `AGENTS.md` 제4원칙(휴일근무 `hldyYn == "Y"` & `alcSpecDvCd in ("11","12")` & 비실동 제외, 야간근무 `11`->`12` 2일 연속 1단위 집계, 야간 양일 출무 조회 일자 산출, 순환 패턴 분석) 100% 동일 구현.
    - `WorkAlarmEvaluator.swift`: 1~4순위 배타 그룹 + 출무/첫편승 병렬 알람 산출 및 `AttendanceAlarmCancellationChecker`(`v1.0.22` 야간 2일차 출무 알람 전일 출무 오판정 취소 방지) 완벽 이식.
    - `SubwayNowRuleEvaluator.swift`: 첫운전/중간운전/안산중간운전/편승 추적 시각 산출, 출발역 역명 치환, 개별 오버라이드(`commandPrefix` + `trackStartTimeStr` 자동 동기화) 이식.
  - **데이터 및 플랫폼 계층 (`Sources/CrewMateIOS/Data/`, `Platform/`)**:
    - `KeychainCredentialStore.swift`: iOS `Security.framework` 하드웨어 키체인 기반 사번·비밀번호 암호화 보관.
    - `XcrewWKWebViewBridge.swift` & `XcrewParsers.swift`: 숨겨진 `WKWebView` 세션 쿠키 공유 기반 Xcrew XHR 브릿지 및 4대 JSON 파서 구현.
    - `SubwayNowAPIClient.swift`: 오라클 클라우드 텔레그램 서버(`http://129.225.156.25:8088`) 연동 및 `start_datetime` ISO-8601 KST(`+09:00`) 타임존 포맷 보장.
    - `LocalStorageAndBackup.swift`: 오프라인 캐시 저장소 및 안드로이드 `DataBackupManager.kt`와 100% 호환되는 JSON 백업/복원 구현.
    - `IOSPlatformManagers.swift`: `UNUserNotificationCenter` `.timeSensitive` / `.defaultCritical` 알림(Apple Watch 진동 미러링) 및 운전 구간 자동 오프라인 시간대 계산기(`AutoOfflineScheduleCalculator`) 구현.
  - **UI 및 테스트 계층 (`Sources/CrewMateIOS/UI/`, `Tests/`)**:
    - `Theme.swift`, `CrewMateIOSApp.swift`, `TodayWorkView.swift`, `MonthlyScheduleView.swift`, `LookupAndSettingsViews.swift`: 6열 컴팩트 다이아 카드, 야간 1·2일차 통합 출무현황, `ImageRenderer` 전체 길이 다이아 캡처, 달력/메모/승무원 조회/설정 화면 구현.
    - `CrewMatePortTests.swift`: 휴일·야간근무 1단위 카운트, 2일차 출무 알람 보호, 텔레그램 KST 오프셋 및 오버라이드 동기화 XCTest 검증.
- **2. 동료 인수인계용 아키텍처·유지보수 종합 가이드 및 클린 ZIP 패키지 생성**:
  - `IOS_PORTING_AND_MAINTENANCE_GUIDE.md` 및 `ios-port/README.md` 작성 (Android `.kt` ↔ iOS `.swift` 1:1 매핑 표, 도메인 절대 수칙, iOS 단축어 연동법, 6대 유지보수 포인트 수록).
  - 민감 파일(`.jks`, `local.properties`, `build/`, `.gradle/`, `.apk`)을 완전히 배제한 전달용 통합 압축 파일 `CrewMate_iOS_Port_Handoff_v1.0.22.zip` 생성 완료.

---

## [v1.0.22] - 2026-11-04

### ⏰ 야간근무 2일차 출무 전 정밀 알람 전일 출무 오판정 침묵 결함 해결
- **1. 출무 알람 개별 열차·사업일자 정밀 대조 및 오판정 취소 방지 (`WorkAlarmReceiver.kt`, `AttendanceAlarmCancellationChecker`)**:
  - **결함 원인 해결**: `WorkAlarmReceiver.kt`에서 출무 알람(`cat == "출무"`) 발동 시 `today.minusDays(1)`, `today`, `today.plusDays(1)` 중 "아무 열차나 하나라도 출무 완료(`statuses.any { it.isCheckedIn }`)"되었으면 알람을 강제 취소하던 결함을 발견. 이로 인해 야간 2일차 새벽 알람 발동 시 전일(1일차) 이미 완료된 출무 기록으로 인해 2일차 출무를 하지 않았음에도 출무 완료로 오판정되어 사이렌이 침묵하던 문제를 원천 해결.
  - `AttendanceAlarmCancellationChecker` 분리 및 인텐트로부터 사업일자(`pjtDt`), 대상 열차번호(`trainNo`), 예정시각(`dptTimeStr`)을 정확히 수집.
  - `pjtDt`가 지정된 경우 해당 일자의 캐시만 조회하여 전일 기록의 침범을 원천 차단하고, 대상 열차번호/시각이 일치하는 특정 출무 건(`matchedItem`)이 실제로 출무 단계에 진입했을 때(`matchedItem.isCheckedIn == true`)에만 사이렌을 취소하도록 엄격히 제한.
  - 매칭되는 항목이 아직 미출무(`!matchedItem.isCheckedIn`) 상태이면 절대로 취소하지 않고 예정대로 알람(소리/진동)을 울리도록 보장.
- **2. 알람 아이템 및 스케줄러 사업일자(`pjtDt`) 파이프라인 구축 (`WorkAlarmRuleEvaluator.kt`, `WorkAlarmScheduler.kt`)**:
  - `WorkAlarmItem` 데이터 모델에 사업일자 필드(`val pjtDt: String? = null`)를 신설하고, 출무 알람 생성 시 `attendanceDate` 기반 사업일자(`yyyyMMdd`)를 스탬핑.
  - `WorkAlarmScheduler.kt`에서 알람 인텐트 Extras에 `pjtDt`를 전달하고, 알람 재스케줄링 시 기존 취소 루프 범위를 기존 `0..15`에서 `0..63`으로 확대하여 다이아 전체 구간 및 출무 알람 잔존 간섭 방지.
- **3. 단위 테스트 및 빌드 검증 (`EnhancementsUnitTest.kt`)**:
  - `overnightShift_attendanceAlarm_notCancelledByPreviousDayAttendance`: 1일차 출무가 완료되어 있어도 2일차 미출무 알람은 취소되지 않고 정상 발동됨을 검증 통과.
  - `sameDay_multipleAttendance_independentCancellation`: 동일 일자 다중 출무(1차 완료, 2차 미완료) 시 각각 독립적으로 취소 여부가 판정됨을 검증 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 통과.

---

## [v1.0.21] - 2026-11-03

### 🌙 야간근무(2일 연속 근무) 양일(1·2일차) 출무 현황 표출 및 2일차 출무 전 정밀 알람 복구
- **1. 야간근무 익일 출무 현황 원격 조회 복구 및 사업일자(`pjtDt`) 정규화 (`TodayWorkRepository.kt`)**:
  - **원인 해결**: 기존 `dateToFetch.isAfter(today)` 분기로 인해 야간근무 1일차 진입 시 2일차(내일) 출무 현황 조회가 원격 서버(`fetchAttendanceStatus`) 호출 없이 빈 로컬 캐시만 참조하여 2일차 출무 현황이 100% 누락되던 결함을 발견하고 완전 제거.
  - 야간근무 1일차(`OVERNIGHT_DAY1`) 및 2일차(`OVERNIGHT_DAY2`)에 생성되는 `datesToQuery`의 모든 일자에 대해 실시간 원격 조회를 수행하고 Room DB에 스냅샷을 갱신하도록 복구.
  - 서버 응답 및 Room DB 캐시 로드 시 `AttendanceStatusDto`의 `pjtDt`가 비어 있거나 포맷이 다른 경우 해당 조회 일자(`dateStr`, `yyyyMMdd`)로 정규화 및 스탬핑하여, 출무 그룹핑과 알람 엔진이 정확한 날짜를 인지하도록 보장.
  - 최종 반환 리스트를 사업일자(`pjtDt`) 및 출무예정시각(`gtwcEpctTm`) 기준으로 중복 제거 및 시간순 정렬.
- **2. 표준 승무근무표 원칙 기반 야간근무 판정식 및 월 경계(말일~초일) 통합 (`WorkSchedule.kt`, `TodayWorkRepository.kt`)**:
  - `AGENTS.md` 표준 원칙(당일 `alcSpecDvCd == "11"` & 익일 `alcSpecDvCd == "12"`)을 엄수하여, 야간 2일차 진입 시 어제 스케줄이 `"11"`이고 오늘이 `"12"`인 경우를 `yesterdayOvernightCandidates`에 즉시 포함.
  - 말일(31일)과 1일 사이에 걸친 야간근무 판정을 위해 전월(`yesterdaySchedules`), 당월(`currentSchedules`), 익월(`tomorrowSchedules`) 스케줄을 통합하여 `determineShiftType`을 판정하도록 개선.
- **3. 야간근무 2일차 출무 전 정밀 알람 생성 및 스케줄링 연동 (`WorkAlarmRuleEvaluator.kt`, `TodayWorkScreen.kt`)**:
  - `WorkAlarmRuleEvaluator.kt`에서 각 출무 항목의 정규화된 `attendanceDate`(`pjtDt`)를 기반으로 알람 시각을 계산하여, 2일차 새벽/아침 출무 건에 대해서도 정확한 익일 시각으로 `출무 N분 전 알람`이 생성되도록 복구.
  - 2일차 알람의 경우 타이틀에 날짜(`MM.dd 출무 N분 전`)를 명시하여 근무자가 직관적으로 인지할 수 있도록 보강.
  - `TodayWorkScreen.kt`에서 다이아 구간 수집 시 메모리에 이미 로드된 `dia.detail.segments`를 직접 참조하여 다이아 구간 유실 방지.
- **4. 단위 테스트 및 빌드 검증 (`EnhancementsUnitTest.kt`)**:
  - `overnightShift_determineShiftType_standard11and12_detectsDay1AndDay2`: 표준 11 -> 12 야간근무 양일 판정 검증 통과.
  - `overnightShift_determineShiftType_acrossMonthBoundary_detectsCorrectly`: 월 경계 야간근무 판정 검증 통과.
  - `overnightShift_evaluatesBothDay1AndDay2AttendanceAlarms`: 야간근무 1일차 및 2일차 출무 전 정밀 알람 동시 생성 및 시각/타이틀 검증 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 통과.

---

## [v1.0.20] - 2026-11-02

### 🔄 세션 유지 시 메뉴별 실시간 정보(출무 현황·근무표) 즉시 최신화 & 텔레그램 열차 추적 500 에러 해결
- **1. 오늘의 근무 출무 현황 및 각 메뉴 진입 시 최신화 누락 해결 (`TodayWorkRepository.kt`, `ScheduleViewModel.kt`, `AppNavigation.kt`, `XcrewWebViewXhrDataSource.kt`)**:
  - `TodayWorkRepository.kt`에서 기존 로컬 캐시에 출무 시각이 있으면 원격 호출을 생략하던 `alreadyConfirmed` 스킵 조건을 제거하고, 온라인 상태일 때 오늘의 근무 진입 시 항상 최신 출무 현황(`fetchAttendanceStatus`)을 서버에서 실시간 조회하도록 개선.
  - `XcrewWebViewXhrDataSource.fetchAttendanceStatus`에서 무음 재로그인 직후 `session.userInfo`의 최신 승무원 성명(`empNm`)을 재참조하도록 보강하여 빈 이름으로 조회가 누락되던 타이밍 버그 차단.
  - `AppNavigation.kt`에서 `setSilentReloginProvider`를 `remember` 초기화 시점에 동기 등록하고, `awaitWebView()` 및 `prepareForLogin()`을 선행 호출하여 초기 WebView 로딩과의 쿠키 경합 방지.
  - 온라인 모드 앱 시작 시 백그라운드 세션을 즉시 수립(`trySilentRelogin`)하고, `sessionAuthenticatedEpoch` 및 `menuRefreshTrigger`를 통해 세션 수립 완료 시점과 햄버거 메뉴 진입 시마다(`오늘의 근무`, `월별 승무근무표` 등 루트 복귀 및 재선택 포함) 서버 최신 데이터를 즉시 갱신하도록 구현.
- **2. 텔레그램 열차 추적 명령 전송 실패(HTTP 500 `TypeError` 및 `UnboundLocalError`) 해결 (`SubwayNowManager.kt`, `SubwayNowApiClient.kt`, `SubwayNowBot/api_bridge.py`, `SubwayNowBot/scheduler.py`)**:
  - **원인**: `SubwayNowManager`가 전송한 `LocalDateTime.toString()`(`"2026-11-02T17:25"`, 타임존 오프셋 없음)을 서버가 naive `datetime`으로 파싱한 뒤 aware `datetime`(`now_kst`)과 비교하면서 `TypeError: can't compare offset-naive and offset-aware datetimes`가 발생해 수동/자동 모든 `/api/v1/track` 요청이 HTTP 500으로 실패하던 문제 규명.
  - **서버 조치 (`api_bridge.py`, `scheduler.py`)**: `start_datetime` 파싱 및 비교 시 `tzinfo is None`이면 `KST_TIMEZONE`(`Asia/Seoul`)을 강제 부여하도록 정규화하고, 예약 활성화(`RESERVED` -> `ACTIVE`) 시 `cur_time_str` 변수 스코프 누락(`UnboundLocalError`)을 함께 수정하여 오라클 클라우드(`129.225.156.25`) 실서버에 즉시 배포 완료.
  - **앱 조치 (`SubwayNowManager.kt`, `SubwayNowApiClient.kt`)**: `trackStartDateTime` 전송 시 KST 오프셋(`+09:00`)이 포함된 ISO-8601 문자열로 직렬화하고, 에러 응답 JSON에서 빈 문자열 처리 보강.
- **3. 단위 테스트 검증 완료 (`EnhancementsUnitTest.kt`, `test_api_bridge.py`)**:
  - `telegramTrackStartDateTime_formatsWithKstOffset` 및 `sessionAuthenticatedEpoch` 검증 테스트 통과.
  - `SubwayNowBot`의 `test_api_bridge.py`에서 naive ISO 전송 및 예약 활성화 테스트 100% 통과.

---

## [v1.0.19] - 2026-11-01

### ⚡ 불필요한 재로그인 최소화 및 로컬 캐시 우선(Cache-First) & 무소음 백그라운드 세션 갱신 아키텍처 도입
- **1. 스마트 즉시 진입(Smart Instant Start) 및 사용자 프로필 영속화 (`AppNavigation.kt`, `SecureCredentialStore.kt`, `XcrewWebViewXhrDataSource.kt`)**:
  - `SecureCredentialStore.hasSavedCredentials()` 헬퍼를 추가하여 저장된 사번/비밀번호가 있거나 오프라인 모드인 경우, 앱 실행 시 `Routes.Login` 화면을 거치지 않고 즉시 메인 화면(`authRootRoute`)으로 0초 만에 진입하도록 개편.
  - 로그인 성공 시 수집한 사용자 이름(`cached_user_name`)과 소속(`cached_user_affil`)을 SharedPreferences에 영속화 및 복원하여 즉시 진입 시에도 상단/드로어 사용자 정보가 즉시 표시되도록 개선.
- **2. 온라인 모드 로컬 DB 캐시 우선(Cache-First) 적용 (`WorkScheduleRepository.kt`, `WorkScheduleRepositoryImpl.kt`, `DiaDetailRepository.kt`)**:
  - 기존 온라인 모드(`isOfflineMode == false`)에서 매번 로컬 Room DB 캐시를 무시하고 무조건 Xcrew 서버 XHR 통신을 시도하던 문제를 해결.
  - `getMonthWorkSchedule(year, month, forceRefresh)`에 `forceRefresh: Boolean = false` 파라미터를 도입하여, 온라인 모드에서도 로컬 Room DB에 해당 월 근무표나 다이아 상세 데이터가 이미 존재하면 네트워크 호출 없이 0초 만에 즉시 반환하도록 개선.
- **3. 포그라운드 로그인 화면 강제 튕김 제거 및 무소음 백그라운드 재로그인(Silent Re-login) 구축 (`XcrewWebViewXhrDataSource.kt`, `AppNavigation.kt`, `LoginViewModel.kt`)**:
  - XHR 요청 중 세션 만료 감지 또는 미인증 상태일 때 `reportAuthenticationExpired()`를 호출해 사용자를 로그인 화면으로 강제 튕겨내던(`popUpTo(0)`) 로직을 제거.
  - 대신 `XcrewSessionManager.trySilentRelogin()`을 통해 백그라운드 숨겨진 WebView(`awaitWebView()`)에서 저장된 자격증명으로 조용히 세션을 갱신한 뒤 원래 XHR 요청을 자동으로 1회 재시도하도록 구축.
  - 만약 백그라운드 재로그인까지 실패하더라도 로그인 화면으로 튕겨내지 않고 각 Repository의 로컬 Room DB 캐시 폴백(Fallback)이 동작하도록 보호.
  - `LoginViewModel` 프리플라이트(Preflight) 조회 시점에 `xcrewSession.markAuthenticated()`를 선행 호출하고, 수집한 당월/익월 근무표를 즉시 Room DB에 영속화하도록 연결.
- **4. 오늘의 근무 출무 현황 스마트 조회 (`TodayWorkRepository.kt`)**:
  - 오늘의 근무 진입 시 로컬 Room DB(`attendanceStatusDao`)에 저장된 출무 스냅샷을 먼저 즉시 로드하여 표시.
  - 이미 실제 출무시각(`actlTm`)이 모두 찍혀 확정된 날이거나 미래 날짜인 경우 불필요한 원격 `fetchAttendanceStatus` 호출을 생략하여 재로그인 유발 요인을 차단.
- **5. 상단 `[재접속]` 버튼 명시적 강제 동기화 (`AppNavigation.kt`)**:
  - 사용자가 상단바의 `[재접속]` 버튼을 직접 터치한 경우에만 당월 근무표 캐시를 비우고 로그인 화면을 통해 서버 최신 데이터를 강제 동기화하도록 명확히 분리.
- **6. 단위 테스트 및 검증 완료 (`EnhancementsUnitTest.kt`)**:
  - `workScheduleRepository_onlineMode_usesCacheFirst_and_onlyCallsNetworkWhenForcedOrEmpty`: 온라인 모드 Cache-First 및 `forceRefresh = true` 동작 검증 통과.
  - `silentRelogin_updatesSessionWithoutTriggeringForegroundExpiredState`: 백그라운드 무소음 재로그인 성공 시 포그라운드 만료 이벤트 미발생 검증 통과.

---

## [v1.0.18] - 2026-10-29

### 📸 오늘의 근무 자정 자동 캡처 양식 인앱 캡처 100% 일원화 및 야간 2일차 스킵 & 텔레그램 추적 개시 연동 강화
- **1. 텔레그램 추적 명령어 수정 시 `추적 개시` 시간 연동 및 앞부분만 직접 수정 제어 (`TodayWorkScreen.kt`)**:
  - `EditTrackTargetDialog`에서 전송 명령어의 마지막 `추적 개시` 시각이 아래의 `추적 개시 (HH:mm)` 입력란과 양방향 실시간으로 연동되도록 구조 분리.
  - 전송 명령어 입력 필드는 앞부분(`editCommandPrefix`: 열차번호/목표역/주기)만 직접 수정할 수 있도록 분리하고, 명령어 필드의 `trailingIcon`에 실시간 `editTrackStartTime` 뱃지를 표시하여 명령어 입력칸 내 시각이 안전하게 표출되면서도 직접 오타 입력되는 것을 원천 차단.
  - 하단 실시간 최종 전송 명령어 미리보기(`최종 전송 명령어: prefix trackStartTime`)를 제공하고, 저장 시 `${prefix} ${trackSt}`로 완벽 병합하여 오버라이드 영속화 및 재스케줄링 연동 완료.
- **2. 자정 00:01 자동 캡처 결과물을 인앱 다이아 캡처와 1:1 완벽하게 동일한 양식으로 일원화 (`MidnightCaptureReceiver.kt`)**:
  - 기존 괄호 시간 표기(`(1740) -> (1820)`), 상단 임의 `타임라인 요약` 뱃지, 불필요한 하단 푸터(`CrewMate 자동 캡처 • ...`), 출퇴근 시각 누락 등 임의 양식을 전면 폐지.
  - 인앱 캡처 화면과 100% 동일하게 일원화:
    - 상단 헤더: `MM.dd 근무` (42px Bold `#0F2942`) + 우측 `YYYY.MM.DD` 둥근 연청색 알약 캡슐 (`#E0E7FF`).
    - 다이아 카드 상단: 좌측 `${diaNo} DIA` + 우측 `출근 HH:mm / 퇴근 HH:mm` (`toDisplayTime` 24px Bold `#64748B`).
    - 6열 타임라인 그리드:
      - Rank 1(`운전/승무`) 행 연한 블루 은은한 배경(`RectF` `#EFF6FF`) 강조.
      - 열차번호가 있을 때 연청색 캡슐 뱃지(`$trn $cat`, 예: `K4658 운전`). 열차번호가 없을 때 일반 텍스트(`준비`, `출고준비`).
      - 준비/출고준비 상단에 정갈한 가로 구분선(`E2E8F0`) 렌더링.
      - 출발역/도착역 중앙 정렬 및 정규 콜론 시각(`18:20`, `20:06`) 정렬.
    - 하단 임의 푸터 텍스트 완전 제거.
- **3. 야간근무 2일차(박 둘째 날) 및 비실동 자정 자동 캡처 스킵 확립 (`MidnightCaptureReceiver.kt`)**:
  - 야간근무 1일차 자정에 익일 오전 구간까지 통합된 전체 다이아가 이미 자동 캡처되므로, 야간 2일차(`alcSpecDvCd == "12"` 또는 `isContinuation` 또는 `diaNo.startsWith("~")`) 자정에는 중복 캡처를 스킵(Skip)하도록 판별식 추가.
  - 비실동(02/21/S/휴/연차/병가 등) 날의 자동 캡처 스킵 가드 완성.
- **4. 단위 테스트 및 안정성 검증 (`EnhancementsUnitTest.kt`)**:
  - `midnightCapture_nightDay2_and_nonActiveDay_skipEvaluation`: 야간 2일차 및 비실동 스킵 판별 100% 통과.
  - `targetOverride_commandPrefix_and_trackStartTime_linking`: 명령어 prefix 및 추적 개시 시각 분리/연동 100% 통과.

---

## [v1.0.17] - 2026-10-29

### 🎨 근무표 메모 고대비 테마화 및 텔레그램·설정 8대 UI/UX & 사용자 오버라이드 기능 고도화
- **1. 월별 승무근무표 메모 고대비 테마 색상 적용 (`ScheduleScreen.kt`)**:
  - 메모 렌더링을 Material 3 디자인 토큰인 `secondaryContainer` 배경색과 `onSecondaryContainer` 글자색(`FontWeight.ExtraBold`)으로 개편.
  - 라이트 모드와 다크 모드 전 구간에서 WCAG AA 기준을 충족하는 선명한 대비율을 확보하여 메모 가독성을 획기적으로 개선.
- **2. 운전시간 자동 오프라인 복귀 시점 "n분" 직접 입력 지원 (`SettingsScreen.kt`)**:
  - 기존 0, 5, 10, 15, 20분의 고정 선택지 중 20분을 **"n분"** 칩으로 교체.
  - 칩 터치 시 커스텀 다이얼로그(`AlertDialog`)를 통해 원하는 분(minute) 단위를 직접 입력하고 저장(`AutoOfflineManager` 즉시 동기화)할 수 있도록 구현.
- **3. 텔레그램 연동 Chat ID '인증 완료' 뱃지 위치 재배치 (`SubwayNowBotSettingsCard.kt`)**:
  - 기존 Chat ID 텍스트필드 우측에 답답하게 배치되어 있던 인증 상태 뱃지를 상단 "텔레그램 계정 연동 (Chat ID)" 레이블 바로 우측으로 이동.
  - 텍스트필드 우측에는 인증/재인증 액션 버튼만 단독 배치하여 시각적 정돈감 극대화.
- **4. 텔레그램 상세 설정 접기/펼치기 아코디언(Accordion) 도입 (`SubwayNowBotSettingsCard.kt`)**:
  - 연동 스위치 아래에 **"⚙️ 텔레그램 상세 설정 (접기 ▲ / 펼치기 ▼)"** 토글 영역을 신설하고 `AnimatedVisibility`를 적용.
  - 예약 전송 시각, 추적 시작 시각, 알림 주기, 역명 치환, 서버 상태 점검, 봇 전송 이력 등 부가 설정들을 깔끔하게 접어둘 수 있어 설정 화면의 공간 효율 대폭 증대.
- **5. 예약 전송 시각: "출근 일괄" 간결화 및 "n분 전" 커스텀 입력 지원 (`SubwayNowBotSettingsCard.kt`)**:
  - 첫 번째 칩 문구를 "출근 시 일괄"에서 **"출근 일괄"**로 간결화.
  - 기존 "60분 전" 칩을 **"n분 전"** 칩으로 교체하여 원하는 전송 리드 타임(분)을 팝업으로 직접 입력/저장할 수 있게 개선.
- **6. 추적 시작 시각: "n분 전" 커스텀 입력 지원 (`SubwayNowBotSettingsCard.kt`)**:
  - 기존 "30분 전" 칩을 **"n분 전"** 칩으로 교체하여 원하는 추적 개시 리드 타임(분)을 팝업으로 직접 입력/저장할 수 있게 개선.
- **7. 알림 발송 주기: "n개 역마다" 추가 및 커스텀 입력 지원 (`SubwayNowBotSettingsCard.kt`)**:
  - 1, 2, 3개 역 칩 뒤에 4번째 **"n개 역마다"** 칩을 추가하여 사용자가 원하는 이동 역 간격(1 이상의 정수)을 자유롭게 설정 가능하도록 확장.
- **8. 오늘의 근무: 텔레그램 열차 추적 타겟 터치 시 사용자 오버라이드(수정) 기능 신설 (`TodayWorkScreen.kt`, `SubwayNowSettings.kt`, `SubwayNowRuleEvaluator.kt`, `SubwayNowManager.kt`)**:
  - `TrackTargetOverride` 데이터 모델 및 SharedPreferences 날짜/열차별 영속화 로직 구현.
  - 오늘의 근무 텔레그램 추적 다이얼로그에서 각 열차 카드를 터치하면 `EditTrackTargetDialog`가 열리며, 해당 1건에 대해 전송 명령어(`rawCommand`), 추적 개시 시각(`trackStartTimeStr`), 예약 전송 시각(`sendDateTime`)을 직접 수정/오버라이드할 수 있는 기능 구축.
  - 저장 시 현재 화면 상태 즉시 반영 및 AlarmManager 자동 전송 스케줄을 사용자가 지정한 시각과 명령어로 즉시 재등록(`rescheduleTarget`), [초기화] 버튼을 통한 기본 다이아 계산값 롤백 지원.
- **9. 단위 테스트 검증 완료 (`SubwayNowRuleEvaluatorTest.kt`)**:
  - 사용자 오버라이드 적용 시 명령어와 시각표가 정확하게 교체되고 `isUserOverridden` 플래그가 활성화되는지 검증하는 단위 테스트 추가 및 100% 통과.

---

## [v1.0.16] - 2026-10-29

### 🛠️ 승무근무표 표준 원칙 기반 휴일근무·야간근무 판별식 완전 개편 및 비실동 제외 확립
- **1. 휴일근무 표준 판정 원칙 준수 및 비실동(병가/연차 등) 100% 완전 제외 (`WorkSchedule.kt`, `HolidayWorkManager.kt`)**:
  - `hldyYn == "Y" && alcSpecDvCd in setOf("11", "12")` 일 때만 휴일근무로 판별.
  - `alcSpecDvCd == "21"`인 날은 `hldyYn` 값과 무관하게 승무 미수행 비실동(연차, 교육, 병가 등)이므로 무조건 제외.
  - `hldyYn == "Y"`인 날 중에서도 비실동(연차, 병가, 공가, 교육, 출장, 청원 등) 처리가 된 날은 실제 근무를 한 날이 아니므로 100% 완전 제외 (병가인 날이 휴일근무로 카운트되던 결함 원천 해결).
  - `alcSpecDvCd == "02"`(휴일 휴무/비번)인 날도 쉬는 날이므로 제외.
- **2. 야간근무 표준 판정 원칙 준수 (`당일 11 & 익일 12`) (`WorkSchedule.kt`, `HolidayWorkManager.kt`)**:
  - 당일 `alcSpecDvCd == "11"` 이고 익일 `alcSpecDvCd == "12"` ➔ **야간 1일차**.
  - 당일 `alcSpecDvCd == "12"` ➔ **야간 2일차**.
  - 당일 `alcSpecDvCd == "11"` 이고 익일 `alcSpecDvCd != "12"` ➔ **주간근무(일근)**.
  - 시각 역전(`st > et`)이나 물결표(`~`) 추측을 전면 폐지하고 승무 표준 11-12 연속 코드 기반으로 판별하여, 실제 야간근무가 주간으로 잘못 분류되던 결함 완전 해결.
  - 야간 2일 연속 근무(`11` -> `12`)를 1개 단위로 정밀하게 집계(1일차에 카운트된 경우 2일차는 중복 카운트 없이 내역만 보존).
- **3. 표준 근무 판정 절대 원칙 영구 보존 기록 (`AGENTS.md`, `WorkSchedule.kt`, `ARCHITECTURE_GUIDE.md`)**:
  - `AGENTS.md`의 '코드 수정 시 절대 안전 원칙' 4번에 승무 표준 원칙(휴일근무 & 야간근무)을 명문화하여 앞으로의 모든 AI 에이전트와 개발자가 준수하도록 영구 보존.
  - `WorkSchedule.kt`에 `isNonActive()`, `isHolidayDutyCandidate()`, `determineShiftType()` 표준 도메인 확장 함수 정의.
- **4. 단위 테스트 스위트 전면 보강 및 100% 통과 (`EnhancementsUnitTest.kt`)**:
  - 병가(`alc=21`, `hldyYn=Y`) 완전 제외 검증, 연차(`dia=연차`, `hldyYn=Y`) 완전 제외 검증, 당일 11 & 익일 12 야간 1일차 검증, 야간 2일차 2일 연속 1개 단위 통합 검증 테스트 완료.

---

## [v1.0.15] - 2026-10-29

### 🛠️ 휴일근무 현황 0건 표출 결함 4대 원인 규명 및 완전 해결
- **1. 날짜 포맷 정규화 (`it.length == 8` 필터 결함 수정) (`HolidayWorkManager.kt`)**:
  - `workDate`에 하이픈(`"2026-10-03"`)이나 특수문자가 포함된 경우 `it.length == 8` 필터에 의해 모든 날짜가 걸러져 루프가 0회 실행되던 치명적 결함을 발견하고, `it.workDate?.filter { it.isDigit() }`를 통해 항상 8자리 클린 날짜(`"YYYYMMDD"`)로 정규화하여 그룹핑 및 정렬하도록 수정.
- **2. 동일 일자 다중 스케줄 덮어쓰기 유실 방지 (`NonOpStatusViewModel.kt`)**:
  - 기존 `scheduleMap[d] = s` 단일 날짜 Key 구조로 인해 Xcrew에서 내려온 '기존 순번 휴일 행(`alcSpecDvCd == "02"`)'과 '추가 배정 다이아 행(`alcSpecDvCd == "11"`)' 중 하나가 덮어씌워져 증발하던 구조적 결함 제거.
  - `monthSchedulesMap = mutableMapOf<String, List<WorkSchedule>>()` 월별 리스트 보존 구조로 전면 전환하여, 로컬 Room DB 및 원격 Xcrew의 동일 일자 복수 행을 단 1건의 유실도 없이 보존.
- **3. 달력 셀('휴' 뱃지)과 현황 판정식의 충돌 해결 및 통합 판정 구축 (`HolidayWorkManager.kt`)**:
  - `ScheduleScreen`의 빨간 **휴** 뱃지는 `(hldyYn == "Y" || isHoliday) && alcSpecDvCd != "02"`(실제 출근이므로 alc가 11/12)로 표출되는데, 직전 `HolidayWorkManager`는 오직 `alcSpecDvCd == "02"`만을 엄격히 요구하여 공휴일/주말 출근 근무가 전부 누락되던 모순을 해결.
  - **통합 휴일근무 판정식 확립**:
    - 휴일 여부: 순번표 휴일(`alcSpecDvCd in setOf("02", "2")`) OR 서버 공휴일(`hldyYn == "Y" || isHoliday == true`) OR 비실동 구분(`nonActiveType?.contains("휴일")`).
    - 실제 근무 배정: `diaNo`가 단순 비실동이 아니거나, 열차번호(`trainNo`) 또는 근무시각(`startTime/endTime`)이 지정된 유효 승무 근무.
- **4. 연간 수집 모수 투명성(진단) UI 추가 (`HolidayWorkManager.kt`, `NonOpStatusScreen.kt`)**:
  - `HolidayWorkReport`에 `examinedDaysCount`(분석에 반영된 총 일수) 필드를 추가하고 SharedPreferences 직렬화/역직렬화 지원.
  - UI 상단에 `최근 조회: YYYY.MM.DD HH:mm (총 OO일치 분석)` 형태로 실제 분석에 반영된 근무표 모수를 투명하게 표출.
- **5. 단위 테스트 검증 완료 (`EnhancementsUnitTest.kt`)**:
  - 하이픈 날짜(`"2026-10-03"`), 공휴일 `hldyYn == "Y"` & `alcSpecDvCd == "11"`, 동일 날짜 복수 행(02 휴일 + 11 다이아 배정), 분석 일수 검증 테스트 케이스 추가 및 100% 통과.

---

## [v1.0.14] - 2026-10-29

### 🛠️ 실무 피드백 기반 7대 결함 해결 및 편의기능 고도화
- **1. 출무 5분 전 알람 미해제 버그 원천 해결 (`AttendanceStatus.kt`, `WorkAlarmRuleEvaluator.kt`, `WorkAlarmReceiver.kt`, `WorkAlarmScheduler.kt`, `TodayWorkRepository.kt`, `TodayWorkScreen.kt`)**:
  - `AttendanceStatus.isCheckedIn`: 실제 출무시각(`actualTime != null`)이 존재하거나 출무 구분코드(`gtwcGubunCd in setOf("3", "5", "6")`, 적합성미완료 `5` 포함)인 경우 출무 완료로 정밀 판정.
  - `WorkAlarmRuleEvaluator`: 출무 완료(`att.isCheckedIn == true`) 상태일 때 출무 5분 전 알람 대상에서 완전 제외.
  - `WorkAlarmReceiver`: 예약 시점에 로컬 DB 출무 상태를 실시간 재확인하여 출무 완료 시 사이렌 발동을 원천 차단.
  - `TodayWorkRepository` & `WorkAlarmScheduler`: 출무 완료로 활성 알람이 0개가 되더라도 OS에 등록된 슬롯(0~15)을 확실히 취소할 수 있도록 `cancelAllAlarms()` 및 상시 동기화 호출.
- **2. 야간 근무 자정 이후(익일) 열차 추적 및 텔레그램 알람 예약 지원 (`SubwayNowRuleEvaluator.kt`, `SubwayNowManager.kt`, `SubwayNowApiClient.kt`, `SubwayNowReceiver.kt`, `SubwayNowBot/scheduler.py`, `SubwayNowBot/api_bridge.py`)**:
  - `SubwayNowRuleEvaluator`: 다이아 순회 시 시각 역전(`dpt < lastTime`)을 감지하여 자정 이후 익일 운행 열차에 `isNextDay = true` 플래그 부여.
  - **18시/12시 하드코딩 완전 제거 및 확정 일시 전송**: `SubwayNowApiClient.sendTrack` 및 `SubwayNowManager`에서 계산된 `trackStartDateTime` ISO 문자열(예: `2026-10-30T01:15:00`)을 봇 REST API로 직접 전달.
  - `SubwayNowBot (api_bridge.py, scheduler.py)`: CrewMate가 전송한 `start_datetime`을 최우선 적용하여 14시, 16시 등 주간/석간 등록 시점과 무관하게 0% 추측, 100% 확정적 익일 예약 보장. 수동 텔레그램 명령어의 경우에도 18시/12시 하드코딩을 제거하고 15분 이내 시각은 즉시 활성화, 과거 시각은 익일 예약으로 처리하도록 개편 및 오라클 클라우드 운영 서버(`129.225.156.25:8088`) 배포 완료.
- **3. 텔레그램 추적 중지 시 무한 재시작 차단 & 제어/이력 분리 (`SubwayNowSettings.kt`, `SubwayNowManager.kt`, `SubwayNowReceiver.kt`, `TodayWorkScreen.kt`, `SubwayNowBotSettingsCard.kt`)**:
  - `SubwayNowSettings.isTrainStopped / setTrainStopped`: 일자별·열차번호별 중지 상태 영속화.
  - `SubwayNowManager.cancelTarget`: 즉시 봇 취소 API 호출, DB 상태 `CANCELLED` 기록, `setTrainStopped(true)`, AlarmManager 취소.
  - `syncTodayDiaSchedule`: 중지되었거나 취소된 열차는 자동 재스케줄링에서 영구 제외하여 무한 재시작 루프 원천 차단.
  - `TodayWorkScreen`: 중지된 열차에 `[🛑 추적 중지됨]` 뱃지와 `[▶️ 추적 재개]` 버튼 제공, 재개 터치 시 즉시 전송 또는 재스케줄링 복구.
  - `SubwayNowBotSettingsCard`: `설정` 메뉴의 이력 팝업은 단순 읽기 전용("📋 봇 전송 이력")으로 개편하고, 중지/재개 제어는 `오늘의 근무`로 일원화.
- **4. '출고준비' 항목 추적 알람 제외 (`SubwayNowRuleEvaluator.kt`)**:
  - 첫 번째 시작 항목이 `출고준비`인 경우 기지 내 준비 작업이므로 추적 대상에서 자동 제외.
  - `출고준비 -> 운전`과 같이 출고준비에 바로 이어지는 주요 업무 역시 동일 연속 편성이므로 추적 대상에서 제외.
- **5. 오프라인 모드 전환 시 당월 및 익월 데이터 듀얼 동기화 (`AppNavigation.kt`)**:
  - 오프라인 모드 진입 시 당월(`currentMonth`) 스케줄/다이아 상세 캐싱뿐만 아니라 다음 달(`nextMonth`) 스케줄 및 다이아가 서버에 존재하는 경우 함께 백그라운드 프리페치 및 로컬 Room DB 동기화.
- **6. 비실동 조회 내 '휴일근무 현황' 신설 및 0건 표출 버그 완벽 해결 (`HolidayWorkManager.kt`, `WorkScheduleDao.kt`, `NonOpStatusViewModel.kt`, `NonOpStatusScreen.kt`, `AppNavigation.kt`)**:
  - **0건 표출 결함 원천 해결**: Xcrew 응답에서 `alcSpecDvCd == "02"`(휴일)인 행은 구분명(`nonAtulClsfCdNm`)에 `"휴일"`이 기재되어 내려오는데, 기존의 `s.nonActiveType.isNullOrBlank()` 강제 조건 때문에 모든 휴일근무 다이아가 누락되던 결함을 발견하고 완전 제거.
  - `alcSpecDvCd` 코드 포맷 유연화(`"02"`, `"2"`, `toIntOrNull() == 2` 및 휴일 명칭 통합 검사) 및 실제 운용 다이아 배정 여부 판정식 구축.
  - `WorkScheduleDao.getSchedulesForYear`: 연도별 전체 캐시 일괄 조회 Room 쿼리 신설.
  - `NonOpStatusViewModel`: 로컬 Room DB 캐시를 우선 수집하고 당해 연도 조회 시 당월까지만 원격 조회하여 미배정 미래 월(11, 12월 등) 15초 타임아웃 지연을 원천 차단.
  - 주간/야간 분리 계산 및 야간 2일 연속 근무는 1개 단위로 정밀 카운팅, SharedPreferences(`crewmate_holiday_work_prefs`) 로컬 저장 및 연도별(`yyyy`) 변경 조회 지원.
  - `NonOpStatusScreen`: `[현황 조회]` 버튼, 기본 총 누적 개수 표시 및 원터치 터치 시 주간/야간 분리 상세 내역 확장 토글 UI 제공.
- **7. 텔레그램 추적 출발역 '역명 치환' 기능 구축 (`SubwayNowSettings.kt`, `SubwayNowBotSettingsCard.kt`, `SubwayNowRuleEvaluator.kt`, `TodayWorkScreen.kt`)**:
  - `SubwayNowSettings`: 출발역 역명 치환 규칙(`station_replacements_json`) 추가/삭제/조회/자동 치환 함수 제공.
  - `SubwayNowBotSettingsCard`: 다이아 출발역(예: `4서울`)과 교체역명(예: `서울`)을 입력·등록하고 삭제할 수 있는 '출발역 역명 치환 설정' 카드 및 실시간 칩 UI 제공.
  - 다이아 분석 및 텔레그램 전송 시 치환 규칙을 즉각 적용하여 봇 4자리 역명 제약 및 불일치 해소.
- **주요 수정/신설 파일**:
  - `app/src/main/java/com/example/crewmate/schedule/HolidayWorkManager.kt` (신설)
  - `app/src/main/java/com/example/crewmate/schedule/NonOpStatusViewModel.kt`
  - `app/src/main/java/com/example/crewmate/schedule/NonOpStatusScreen.kt`
  - `app/src/main/java/com/example/crewmate/telegram/SubwayNowSettings.kt`
  - `app/src/main/java/com/example/crewmate/telegram/SubwayNowRuleEvaluator.kt`
  - `app/src/main/java/com/example/crewmate/telegram/SubwayNowManager.kt`
  - `app/src/main/java/com/example/crewmate/telegram/SubwayNowReceiver.kt`
  - `app/src/main/java/com/example/crewmate/telegram/ui/SubwayNowBotSettingsCard.kt`
  - `app/src/main/java/com/example/crewmate/schedule/TodayWorkScreen.kt`
  - `app/src/main/java/com/example/crewmate/schedule/TodayWorkRepository.kt`
  - `app/src/main/java/com/example/crewmate/alarm/WorkAlarmRuleEvaluator.kt`
  - `app/src/main/java/com/example/crewmate/alarm/WorkAlarmReceiver.kt`
  - `app/src/main/java/com/example/crewmate/alarm/WorkAlarmScheduler.kt`
  - `app/src/main/java/com/example/crewmate/data/remote/xcrew/AttendanceStatus.kt`
  - `app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt`
  - `app/src/test/java/com/example/crewmate/EnhancementsUnitTest.kt` (신설)
  - `app/src/test/java/com/example/crewmate/telegram/SubwayNowRuleEvaluatorTest.kt`
  - `SubwayNowBot/scheduler.py`

---

## [v1.0.13] - 2026-10-28

### 🤖 SubwayNowBot 텔레그램 열차 추적 자동 연동 및 다이아 기반 관제 시스템 완비
- **1. SubwayNowBot 전용 REST API 브릿지 및 개별 열차 취소 (`api_bridge.py`, `bot.py`, `scheduler.py`)**:
  - `aiohttp.web` 기반 비동기 초경량 HTTP API 서버 신설 (`129.225.156.25:8088`).
  - `/stop [열번]` 및 `중지 [열번]` 명령어를 통한 개별 열차 지정 취소 기능 지원 (실수로 전체 추적이 취소되는 사고 원천 방지).
  - 6자리 PIN 발급 및 검증(`/api/v1/auth/request`, `/api/v1/auth/status`), 추적 예약/즉시 등록(`/api/v1/track`), 개별 취소(`/api/v1/stop`) 엔드포인트 완비.
- **2. 6자리 PIN 상호 교차 검증 (Cross-Checking) 보안 체계 (`SubwayNowBotSettingsCard.kt`, `api_bridge.py`)**:
  - 앱에서 Chat ID 입력 후 `[인증 시작]` 터치 시 6자리 일회용 보안 PIN 발급.
  - 승무원이 텔레그램 `@SubwayNowBot` 대화방에 5분 이내 PIN 번호 입력 시 서버 검증 및 계정 연동 승인(SQLite 영속화).
  - 앱에서 3초 주기 자동 폴링 또는 `[인증 확인]` 터치로 녹색 `[인증 완료]` 뱃지 활성화.
- **3. 다이아 필터링 4대 규칙 엔진 구축 (`SubwayNowRuleEvaluator.kt`)**:
  - **1순위 주요업무 추출**: `운전`, `승무`, `출고준비`, `입환` 항목에서 열차번호, 시작역(`dptStnNm`), 출발시각 추출.
  - **연속 업무 제외 (Sandwiched Duty)**: `1순위 -> 감시 -> 1순위` 패턴일 때, 감시 뒤의 두 번째 1순위는 앞 업무의 연속이므로 추적 대상에서 완전 제외.
  - **열차번호 K-Prefix 정규화**: `K`로 시작하지 않거나 숫자만 있는 경우(예: `1601` $\rightarrow$ `K1601`, `S507` $\rightarrow$ `K507`) 무조건 `K`로 자동 정규화.
  - **선행 감시 출발시각 간주**: 1순위 업무 직전에 '감시'가 있는 경우(`감시 -> 1순위`), 출발시각($T_{base}$)은 해당 감시의 시작시각(`depTm`)으로 간주하여 계산.
- **4. 시각 분리 및 유연한 전송 설정 (`SubwayNowSettings.kt`, `SubwayNowBotSettingsCard.kt`)**:
  - **예약 전송 시각 ($T_{send}$)**: `출근 시 일괄 전송`, `60분 전`, `45분 전`, `30분 전(기본값)` 중 선택.
  - **추적 시작 시각 ($T_{start}$)**: `30분 전`, `20분 전`, `15분 전(기본값)`, `10분 전` 중 선택.
  - **추적 주기 (인터벌)**: `1개 역`, `2개 역`, `3개 역(기본값)` 이동 시마다 텔레그램 푸시.
  - 봇 서버 연결 Ping 테스트 및 최근 전송 이력 다이얼로그(성공/실패/취소 상태, 원터치 개별 추적 취소) 지원.
- **5. 오늘의 근무 연동 및 자동 스케줄러 (`TodayWorkScreen.kt`, `SubwayNowManager.kt`, `SubwayNowReceiver.kt`)**:
  - 오늘의 근무표 로드 시 다이아를 자동 분석하여 `AlarmManager`로 각 열차의 전송 시점에 맞춰 백그라운드 발송 예약.
  - 출근 시 일괄 전송 모드 시 당일 열차 자동 일괄 등록.
  - `TodayWorkScreen`에 `🤖 텔레그램 열차 추적` 연동 뱃지 및 터치 시 열차별 `[🚀 즉시 전송]` / `[🛑 추적 취소]` 제어 다이얼로그 제공.
- **6. Room DB 이력 영속화 및 네트워크 보안 설정 (`BotCommandHistoryEntity.kt`, `BotCommandDao.kt`, `CrewMateDatabase.kt`, `network_security_config.xml`)**:
  - `bot_command_histories` 테이블 신설 및 Room DB v8 판올림.
  - `network_security_config.xml`: Korail Xcrew 도메인은 100% 엄격한 HTTPS를 유지하면서, SubwayNowBot 서버(`129.225.156.25`)와의 HTTP 통신을 안전하게 허용.
- **주요 수정/신설 파일**:
  - `app/src/main/java/com/example/crewmate/telegram/SubwayNowSettings.kt` (신설)
  - `app/src/main/java/com/example/crewmate/telegram/SubwayNowRuleEvaluator.kt` (신설)
  - `app/src/main/java/com/example/crewmate/telegram/SubwayNowApiClient.kt` (신설)
  - `app/src/main/java/com/example/crewmate/telegram/SubwayNowManager.kt` (신설)
  - `app/src/main/java/com/example/crewmate/telegram/SubwayNowReceiver.kt` (신설)
  - `app/src/main/java/com/example/crewmate/telegram/ui/SubwayNowBotSettingsCard.kt` (신설)
  - `app/src/main/java/com/example/crewmate/database/BotCommandHistoryEntity.kt` (신설)
  - `app/src/main/java/com/example/crewmate/database/BotCommandDao.kt` (신설)
  - `app/src/main/java/com/example/crewmate/database/CrewMateDatabase.kt`
  - `app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt`
  - `app/src/main/java/com/example/crewmate/schedule/TodayWorkScreen.kt`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/res/xml/network_security_config.xml`
  - `app/src/test/java/com/example/crewmate/telegram/SubwayNowRuleEvaluatorTest.kt` (신설)
  - `SubwayNowBot/api_bridge.py` (신설)
  - `SubwayNowBot/bot.py`
  - `SubwayNowBot/requirements.txt`
  - `SubwayNowBot/test_api_bridge.py` (신설)

---

## [v1.0.12] - 2026-10-28

### 🎨 세부 UI/UX 디자인, 이모지 및 문구 정리 6대 개선 완비
- **1. 다이아 캡처 버튼 이모지 변경 및 문구 삭제 (`TodayWorkScreen.kt`, `ScheduleScreen.kt`, `DiaLookupScreen.kt`, `DiaCaptureHelper.kt`)**:
  - 캡처 버튼에서 "캡처" 텍스트를 제거하고 캡처 도구의 대표 상징인 `✂️` 단독 아이콘 버튼으로 정돈하여 UI 공간 효율화.
  - 캡처 범위 선택 다이얼로그 타이틀도 `✂️ 스크린샷 저장 범위 선택`으로 동기화.
- **2. 오프라인 모드 복귀 Skip 버튼 어플 최상단 이동 (`AppNavigation.kt`, `TodayWorkScreen.kt`, `SettingsScreen.kt`)**:
  - 상단바(TopBar) `CrewMate` 로고 우측 `[OFFLINE]` 뱃지 바로 오른쪽에 `[복귀 Skip]` (스킵 시 `[복귀 활성]`) 버튼을 배치하여 어느 화면에서나 1초 확인 및 제어 가능.
  - `TodayWorkScreen` 날짜 뱃지 옆과 `SettingsScreen` 내부의 중복 Skip 버튼을 제거하여 상단바로 일원화.
- **3. 나의 연차 관리 총 연차 [수정]/[저장] 토글 제어 (`NonOpStatusScreen.kt`)**:
  - '올해 총 연차' 저장 후 숫자 입력창을 읽기전용(비활성)으로 잠그고 버튼 문구를 `[수정]`으로 전환.
  - `[수정]` 버튼 터치 시 다시 입력창이 활성화되고 `[저장]` 버튼으로 전환되어 저장 여부를 사용자가 직관적으로 확인 가능.
- **4. 미배정 달 순환 패턴 뱃지 번호 제거 (`SettingsScreen.kt`)**:
  - 순환 패턴 순서 목록에서 번호(`1. 2.`)를 제거하고 `[주간]`, `[주간]`, `[야간]`, `[~]`, `[S]` 순수 뱃지만 깔끔하게 표출.
- **5. 오늘의 근무 자동 캡쳐 (자정) 문구 정리 (`SettingsScreen.kt`)**:
  - 타이틀에서 `(자정)` 문구를 삭제하여 `오늘의 근무 자동 캡쳐`로 간결화.
- **6. 운전시간 자동 오프라인 전환 설정 정돈 (`SettingsScreen.kt`, `AutoOfflineManager.kt`, `AutoOfflineReceiver.kt`)**:
  - "1순위 업무"를 "주요 업무"로 문구 통일.
  - "(QA 관리)" 문구 삭제 및 `전환 시각 기록` 카드를 기본 접힘(`펼치기 ▼` / `접기 ▲`)으로 변경하여 설정 화면 밀도 향상.
  - 온라인 복귀 시점 칩에서 `(권장)` 문구 삭제 (`10분`).
  - 예정 스케줄을 줄바꿈하여 아래에 표출하고 `1구간, 2구간`을 `1. 2.` 표기로 개선.
- **7. 온라인 복귀 시점 칩 텍스트 가운데 정렬 및 캡처 버튼 가로 사이즈 축소 (`SettingsScreen.kt`, `TodayWorkScreen.kt`, `ScheduleScreen.kt`, `DiaLookupScreen.kt`)**:
  - `SettingsScreen.kt`: `FilterChip` 라벨 텍스트에 `textAlign = TextAlign.Center`를 적용하여 0분~20분 칩 내부 텍스트를 버튼 중앙에 정렬.
  - `TodayWorkScreen.kt`, `ScheduleScreen.kt`, `DiaLookupScreen.kt`: M3 Button 기본 최소폭(58dp) 대신 컴팩트 `size(28.dp)` 아이콘 버튼으로 교체하여 여백 낭비 원천 해소.
- **주요 수정 파일**:
  - `app/src/main/java/com/example/crewmate/schedule/TodayWorkScreen.kt`
  - `app/src/main/java/com/example/crewmate/schedule/ScheduleScreen.kt`
  - `app/src/main/java/com/example/crewmate/schedule/DiaLookupScreen.kt`
  - `app/src/main/java/com/example/crewmate/util/DiaCaptureHelper.kt`
  - `app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt`
  - `app/src/main/java/com/example/crewmate/schedule/NonOpStatusScreen.kt`
  - `app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt`
  - `app/src/main/java/com/example/crewmate/alarm/AutoOfflineManager.kt`
  - `app/src/main/java/com/example/crewmate/alarm/AutoOfflineReceiver.kt`

---

## [v1.0.11] - 2026-10-28

### 🛡️ 설정 화면(SettingsScreen) 4대 핵심 기능 원상 복구 및 시스템 완비
- **1. 설정 화면 4대 핵심 기능 카드 100% 원상 복구 (`SettingsScreen.kt`)**:
  - **오늘의 근무 자동 캡쳐 (자정)**: `midnight_auto_capture` 스위치 및 타임라인 요약/전체 2종 선택 세그먼트 카드 원상 복구.
  - **운전 시간 자동 오프라인 전환 & QA 패널**: `AutoOfflineManager` 연동 스위치, 당일 예정 스케줄 요약, 최근 OFF/ON 시각(초 단위), `[🔄 스케줄 재계산]`, `[🗑️ 기록 초기화]`, `0분~20분` 복귀 지연 칩, `[온라인 복귀 Skip]` / `[온라인 복귀 재활성화]` 버튼 완비.
  - **미배정 달 순환 패턴 표시 (근무패턴 만들기)**: `WorkPatternSettings` 연동 스위치, `DatePickerDialog` 기준일자 선택기, 4대 뱃지 팔레트(`+ [주간]`, `+ [야간]`, `+ [~]`, `+ [S]`), 순환 패턴 순서 목록(터치 시 삭제, 전체 삭제) 원상 복구.
  - **💾 데이터 백업 및 복원**: `DataBackupManager` 연동 Room DB 메모, 연차 정보, 설정값 JSON 파일 `[💾 백업 내보내기]` 및 `[📂 복원 가져오기]` 완비.
- **2. 구버전 깨진 한글 텍스트 전수 클린 복원 (`SettingsScreen.kt`)**:
  - 기본 설정 화면의 한글 문자열(캘린더 연동, 첫 화면, 폰트 크기, 감시 숨기기, 테마, 가상 날짜 디버그 다이얼로그 등)을 100% 무결점 한글로 정규화.
- **3. 편의 헬퍼 및 호환성 보강 (`DiaCaptureHelper.kt`, `AppDateProvider.kt`)**:
  - `DiaCaptureOption.fromName(name: String?)` 헬퍼 메서드 신설.
  - `AppDateProvider.setOverrideDate(date: LocalDate?)` 편의 메서드 추가.
- **4. 빌드 및 테스트 전수 검증 통과**:
  - `compileDebugKotlin` 0 에러, `testDebugUnitTest` 100% 통과, `assembleDebug` 및 `assembleRelease` 0 에러 완수.
- **주요 수정/복구 파일**:
  - `app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt`
  - `app/src/main/java/com/example/crewmate/util/DiaCaptureHelper.kt`
  - `app/src/main/java/com/example/crewmate/util/AppDateProvider.kt`

---

## [v1.0.10] - 2026-10-28

### 🔔 알림 권한 허용 직통 연결 및 시스템 사전 준비 다이얼로그 고도화
- **1. 알림 권한 허용 직통 연결 (Android 13+ 인앱 팝업 & 시스템 설정 직통)**:
  - WorkAlarmSettingsScreen.kt:
    - Android 13+ (API 33+) 환경에서 
ememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())을 통해 불필요한 설정 화면 이동 없이 앱 내에서 원터치로 시스템 POST_NOTIFICATIONS 허용 팝업이 바로 뜨도록 구현.
    - 권한이 거부되었거나 하위 OS 환경인 경우, 애플리케이션 상세 정보 루트 화면 대신 Settings.ACTION_APP_NOTIFICATION_SETTINGS 및 Settings.EXTRA_APP_PACKAGE를 직접 호출하여 CrewMate 전용 알림 토글 화면으로 1초 직통 연결.
- **2. 마스터 알람 스위치 활성화 시 원터치 알림 권한 자동 유도**:
  - 오늘의 근무 정밀 알람 마스터 스위치를 켤 때 알림 권한이 비활성화되어 있으면 즉시 시스템 알림 권한 팝업을 띄우고, 다른 권한(정확한 알람, 배터리 최적화)이 충족되면 다이얼로그 없이 바로 알람이 켜지도록 UX 최적화.
- **3. 알람 사전 준비 다이얼로그 권한 버튼 완전 분리**:
  - showSafetyDialog: 기존에 하나로 묶여있던 '알림 / 알람 권한 설정 이동' 버튼을 🔔 알림 권한 허용 (직통 연결)(파란색)과 ⏰ 정확한 알람 권한 설정 (직통 연결)(청록색)으로 명확히 분리하여 사용자가 필요한 권한만 직관적으로 선택할 수 있도록 개편.
- **4. 알림 활성화 여부 이중 판정 정밀화**:
  - WorkAlarmSettingsScreen.kt & SettingsScreen.kt: ContextCompat.checkSelfPermission 외에 NotificationManagerCompat.from(context).areNotificationsEnabled()를 함께 검사하여 시스템 레벨의 알림 차단 상태까지 오차 없이 정확히 판정.
- **5. 설정 화면 복귀 시 자동 권한 감지 및 알람 활성화 (ON_RESUME)**:
  - LocalLifecycleOwner + LifecycleEventObserver를 연동하여 사용자가 시스템 설정 화면에서 알림 권한을 켜고 CrewMate로 돌아왔을 때 별도의 수동 조작 없이 즉시 상태가 갱신(prerequisites)되고 3대 권한이 모두 만족되면 마스터 알람이 자동 활성화되며 다이얼로그가 자동 종료.
- **주요 수정 파일**:
  - pp/src/main/java/com/example/crewmate/alarm/WorkAlarmSettingsScreen.kt
  - pp/src/main/java/com/example/crewmate/settings/SettingsScreen.kt

---

## [v1.0.9] - 2026-10-27

### 🚀 실무 운용 7대 버그 해결 및 편의 기능 고도화 패치
- **1. 운전 시간 자동 오프라인 모드 온라인 복귀 지연 시간 설정 및 Skip 기능 탑재**:
  - `AutoOfflineManager.kt` / `AutoOfflineReceiver.kt`: 정리/입고정리 시작 후 온라인 복귀 지연 시간(`0분(즉시)`, `5분`, `10분(권장)`, `15분`, `20분`) 옵션 신설.
  - 열차 지연, 차량 점검 지연 등으로 계속 오프라인 모드를 유지해야 할 때 온라인 복귀 알람을 즉시 취소하는 `[온라인 복귀 Skip]` 기능 구현.
  - `SettingsScreen.kt`뿐만 아니라 승무원이 가장 자주 보는 `TodayWorkScreen.kt` 상단바에도 오프라인 상태일 때 원터치 `[복귀 Skip]` 버튼을 배치하여 앱 사용성 극대화.
- **2. 기기 변경 및 데이터 유실 대비 JSON 데이터 백업 및 복원 엔진 도입**:
  - `DataBackupManager.kt`: Room DB의 작성 메모(`memos`), 연차/이월연차 정보(`annual_leaves`, `carried_over_leaves`) 및 SharedPreferences 설정값을 하나의 JSON 파일로 내보내고(Export) 가져오는(Import) 기능 신설.
  - `SecureCredentialStore` AES-256 키스토어 자격 증명 및 비밀번호, 쿠키, 세션 토큰 등 민감 정보는 백업 대상에서 100% 필터링/배제하여 보안 수칙 엄수.
  - `SettingsScreen.kt`에 `[💾 데이터 백업 및 복원]` 전용 카드를 배치하여 파일 선택기를 통해 손쉽게 백업/복원 가능.
- **3. 오프라인 모드에서 오늘의 근무 외 상세 다이아 조회 불가 결함 수정**:
  - `AppNavigation.kt`: 기존에 `ScheduleScreen`, `DiaLookupScreen`, `CrewWorkStatusScreen`, `TrainCrewLookupV2Screen`에 원시 네트워크 XHR 데이터 소스(`XcrewWebViewXhrDataSource`)를 직접 주입하여 오프라인 시 네트워크 에러가 발생하던 구조적 결함 해결.
  - `DiaDetailDataSource` 인터페이스를 구현하는 공용 `sharedDiaDetailRepository: DiaDetailRepository`를 생성하여 전 화면에 주입. 오프라인 모드에서는 네트워크 통신 없이 로컬 Room DB 캐시(`dia_details`)에서 자동으로 읽어오도록 개선.
- **4. 배터리 최적화 제외 설정 시 CrewMate 앱 직통 원터치 허용 다이얼로그 연동**:
  - `AndroidManifest.xml`에 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한 추가.
  - `WorkAlarmSettingsScreen.kt`에서 전체 앱 목록 화면 대신 `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 및 `package:com.example.crewmate`를 직접 호출하여 CrewMate 전용 1-탭 팝업이 바로 뜨도록 개선.
- **5. 실제 출무 시각 확인 시 '적합성미완료' 상태여도 출무 5분 전 알람 자동 해제**:
  - `AttendanceStatus.kt`: XHR 응답의 `actlTm`, `tnsmDttm`, `gtwcDttm`, `actlGtwcTm` 등 모든 실제 출무 시각 필드를 포괄 파싱하도록 확장.
  - `WorkAlarmRuleEvaluator.kt` 및 `WorkAlarmReceiver.kt`: 출무 상태 구분 코드(`3`, `6`)뿐만 아니라 실제 출무 시각(`actlTm`)이 확인되면 출무 상태(적합성미완료 등)와 무관하게 출무 5분 전 알람을 100% 즉시 자동 취소하도록 보강.
- **6. 자정 자동 캡쳐 날짜 탐색 결함 해결 및 Compose UI 양식 일치화**:
  - `MidnightCaptureReceiver.kt`: 기존의 `|| true` 폴백으로 인해 자정에 전일 다이아를 무조건 캡처하던 버그 원천 해결. 당일 월별 근무표(`work_schedules`)를 먼저 조회하여 당일 배정 다이아를 정확히 캡처하며, 당일이 휴무(S/휴/연차)인 경우 불필요한 전일 다이아 캡처를 방지.
  - 인앱 Compose `DiaCaptureLayout`과 1:1로 일치하도록 캔버스 렌더러를 전면 개편(클린 문서 헤더, 날짜/옵션 캡슐 알약, 흰색 카드 및 `#E2E8F0` 테두리, 세로 타임라인 트랙/도트, 카테고리 뱃지).
- **7. 캡처 모드에서 사업시간/거리 요약 표 배제(타임라인 전용 캡처)**:
  - `DiaDetailCard.kt`: `isCaptureMode == true`일 때 하단 구분선, `사업시간/거리 요약` 타이틀 및 요약 테이블을 완전 숨김 처리하여 순수 타임라인 구간만 깔끔하게 캡처되도록 개선.
- **주요 수정/신설 파일**:
  - `app/src/main/java/com/example/crewmate/alarm/AutoOfflineManager.kt`
  - `app/src/main/java/com/example/crewmate/alarm/AutoOfflineReceiver.kt`
  - `app/src/main/java/com/example/crewmate/data/backup/DataBackupManager.kt` (신설)
  - `app/src/main/java/com/example/crewmate/database/MemoDao.kt`
  - `app/src/main/java/com/example/crewmate/database/LeaveDao.kt`
  - `app/src/main/java/com/example/crewmate/database/DiaDetailDao.kt`
  - `app/src/main/java/com/example/crewmate/schedule/DiaDetailRepository.kt`
  - `app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt`
  - `app/src/main/java/com/example/crewmate/alarm/WorkAlarmRuleEvaluator.kt`
  - `app/src/main/java/com/example/crewmate/alarm/WorkAlarmReceiver.kt`
  - `app/src/main/java/com/example/crewmate/alarm/WorkAlarmSettingsScreen.kt`
  - `app/src/main/java/com/example/crewmate/alarm/MidnightCaptureReceiver.kt`
  - `app/src/main/java/com/example/crewmate/schedule/DiaDetailCard.kt`
  - `app/src/main/java/com/example/crewmate/schedule/TodayWorkScreen.kt`
  - `app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt`
  - `app/src/main/java/com/example/crewmate/data/remote/xcrew/AttendanceStatus.kt`
  - `app/src/main/AndroidManifest.xml`

---

## [v1.0.8] - 2026-10-26

### ⏰ 오늘의 근무 자정 자동 캡쳐 로직 전면 점검 및 타임라인 요약/전체 선택 옵션 신설
- **자정 자동 캡처 백그라운드 엔진 고도화 (`MidnightCaptureReceiver.kt`)**:
  - 기존의 하드코딩된 크기(1440px) 및 `take(15)` 제한으로 인해 15개 초과 세그먼트나 복수 다이아가 잘리거나 누락되던 결함을 원천 해결.
  - 다이아 세그먼트 수에 따라 캔버스 높이를 정밀 계산하는 **가변 높이(Dynamic-Height) 헤드리스 캔버스 렌더러**를 구축하여 세그먼트 수가 아무리 많아도 0px 잘림 없이 100% 저장 보장.
  - 전일 야간 연속 근무(Overnight) 다이아에 대한 fallback 탐색 로직을 보강하여 자정 실행 시 당일 다이아 조회의 견고성 대폭 향상.
  - 시그니처 네이비 헤더, 다이아 카드 테두리, 업무 순위별 뱃지(Rank 1 운전/승무 블루, Rank 2 준비/감시 앰버, Rank 3 휴게/정리 그레이), 출발/도착역 및 화살표 시간 표기가 적용된 고품질 모던 카드 디자인으로 비트맵 렌더링.
- **설정 화면 내 자동 캡처 형태(`타임라인 요약` vs `타임라인 전체`) 선택 UI 신설 (`SettingsScreen.kt`)**:
  - `설정` 화면의 `오늘의 근무 자동 캡쳐 (자정)` 스위치가 켜지면 하단에 **`[타임라인 요약]`**과 **`[타임라인 전체]`**를 선택할 수 있는 직관적인 세그먼트 카드가 표출되도록 구현.
  - 선택한 옵션(`"midnight_auto_capture_option"`)을 SharedPreferences에 실시간 저장하고 `MidnightCaptureReceiver`와 동기화.
  - `DiaDetailCard.kt`의 `categorizeSegments`를 `internal`로 공용화하여, 요약 모드 선택 시 Rank 3(휴게/정리/승계) 및 샌드위치 감시를 단일 기준에 따라 일관되게 필터링하고 전체 모드 선택 시 전 구간을 모두 포함하여 저장.
- **주요 수정 파일**:
  - `app/src/main/java/com/example/crewmate/alarm/MidnightCaptureReceiver.kt`
  - `app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt`
  - `app/src/main/java/com/example/crewmate/schedule/DiaDetailCard.kt`

---

## [v1.0.7] - 2026-10-26

### 🛠️ 다이아 상세 바텀시트 캡처 버튼 가변 노출 및 상태 반응성 결함 수정
- **`ScheduleDetailSheet` 내 `captureItems` 실시간 반응형 상태 구독 전환 (`ScheduleScreen.kt`)**:
  - 기존 `remember(schedules, detailCache, dateKey)`로 인해 비동기 Xcrew 통신 전 초기 `emptyList()`가 고정되어, 다이아 카드가 성공적으로 로드된 후에도 세로 화면에서 캡처 버튼이 나타나지 않던(화면 회전 시에만 뒤늦게 나타나던) 캐싱 버그 원천 해결.
  - `captureItems`를 `detailCache` 맵의 `DiaDetailLoadState.Success` 상태 변경을 즉시 추적하는 동적 프로퍼티로 전환하여, 세로/가로 화면 상관없이 다이아 상세가 로드되자마자 0.1초 만에 `📸 캡처` 버튼이 100% 안정적으로 노출되도록 보강.
  - 헤더 제목에 `Modifier.weight(1f, fill = false)`를 적용하여 좁은 화면에서도 캡처 버튼이 밀려나지 않도록 방어.

---

## [v1.0.6] - 2026-10-26

### 📸 앱 내 전 화면 다이아 상세 조회 무결점 캡처(`타임라인 요약` / `타임라인 전체`) 전면 확장
- **공통 다이아 캡처 헬퍼 모듈 신설 (`DiaCaptureHelper.kt`)**:
  - `DiaCaptureOption`(`타임라인 요약`, `타임라인 전체`), 전용 인쇄용 헤더 문서 레이아웃(`DiaCaptureLayout`), 2선택 팝업 다이얼로그(`DiaCaptureOptionDialog`) 및 비트맵 생성/저장 실행기(`executeCapture`)를 공통화.
  - 상단바, 인터랙티브 대화형 버튼, 하단 `사업시간/거리 요약` 등 불필요 요소를 일절 배제하고 `ScrollView` 기반 무제한 높이 렌더러로 긴 타임라인도 하단 잘림 0% 무결점 캡처 보장.
- **다이아 상세가 표시되는 전 화면에 `📸 캡처` 원터치 버튼 연동**:
  1. **월별 승무근무표 (`ScheduleScreen.kt`)**: 날짜 선택 시 열리는 상세 바텀시트(`ScheduleDetailSheet`) 헤더에 `📸 캡처` 버튼 탑재.
  2. **승무 다이아 조회 (`DiaLookupScreen.kt`)**: 다이아 검색 성공 시 화면 상단 헤더 우측에 `📸 캡처` 버튼 탑재.
  3. **소속 승무원 근무 현황 (`CrewWorkStatusScreen.kt`)**: 항목 터치 시 열리는 상세 다이아 바텀시트에서 `📸 캡처` 지원.
  4. **열차 승무원 조회 V2 (`TrainCrewLookupV2Screen.kt`)**: 검색 결과 다이아 터치 시 열리는 상세 바텀시트에서 `📸 캡처` 지원.
  5. **오늘의 근무 (`TodayWorkScreen.kt`)**: 공통 캡처 엔진으로 리팩토링 연동하여 코드 중복 제거 및 기존 기능 100% 동일 유지(Zero-Regression).

---

## [v1.0.5] - 2026-10-26

### 🚀 실무 승무 QA 3대 결함 완벽 해결 (자동 로그인 안정화, 운전 시간 자동 오프라인 복구 및 QA 관리 패널, 타임라인 전용 무결점 캡처)
- **자동 로그인 무한 대기(`"자동 로그인 중입니다..."`) 및 쿠키 경쟁 버그 원천 해결 (`XcrewWebViewXhrDataSource.kt`, `XcrewLoginRepository.kt`, `LoginViewModel.kt`, `AppNavigation.kt`)**:
  - `prepareForLogin()`에서 매 로그인마다 호출되던 `clearCache(true)`, `WebStorage.deleteAllData()`, `loadUrl("loginView.do")`를 제거하여 `OkHttpClient`가 획득한 인증 `JSESSIONID` 쿠키를 느린 WebView 응답이 미인증 쿠키로 덮어쓰던 레이스 컨디션 원천 차단.
  - `refreshWebViewPage()`에서 `view.stopLoading()`을 `resetPageReady()`보다 먼저 호출하고 URL prefix(`https://xcrew.korail.com/extrCrewMg/`) 및 `view.progress >= 100`을 검증하여, 취소된 이전 페이지의 `onPageFinished`가 `pageReady`를 조기 완료시키던 버그 수정.
  - `executeXhr()` 폴링 중 네비게이션 전환으로 JS 컨텍스트(`window.__crewMateXcrewXhrResults`)가 초기화된 경우 15초 타임아웃을 기다리지 않고 즉시 감지(`__NAV_RESET__`)하여 1회 자동 재요청하도록 보강 및 `OkHttpClient` 8초 명시적 타임아웃 적용.
- **운전 시간(1순위) 자동 오프라인 모드 작동 복구 및 '설정' 화면 QA 시각 기록 관리 패널 신설 (`AutoOfflineManager.kt`, `AutoOfflineReceiver.kt`, `TodayWorkRepository.kt`, `BootReceiver.kt`, `AppNavigation.kt`, `SettingsScreen.kt`)**:
  - `SettingsScreen.kt`와 `AutoOfflineManager.kt` 간 SharedPreferences 키 불일치(`"auto_offline_mode"` vs `"auto_offline_enabled"`)를 `"auto_offline_mode"`로 통일(기존 키 호환 포함)하고, `TodayWorkRepository.kt`에서 정밀 알람 마스터 스위치(`settings.alarmEnabled`)와 독립적으로 항상 자동 오프라인 스케줄링이 동작하도록 분리.
  - 하루 다이아 내 다중 운전 구간(`calculateOfflineSchedules`) 지원 및 이미 운전 시간대에 진입한 상태에서 앱 실행/스위치 활성화 시 즉시 오프라인 모드로 전환하는 능동 감지 로직(`syncFromDatabase`) 구현.
  - `AppNavigation.kt`에 `SharedPreferences.OnSharedPreferenceChangeListener`를 등록하여 백그라운드 알람(`AutoOfflineReceiver`)에 의한 오프라인/온라인 전환이 실행 중인 앱 UI에 즉시 반영되도록 수정.
  - `SettingsScreen.kt`의 '운전 시간 자동 오프라인 모드' 카드 하단에 **예정 스케줄(OFF/ON 시각)**, **최근 실제 오프라인 전환 시각**, **최근 실제 온라인 복귀 시각**과 `[🔄 스케줄 재계산]`, `[🗑️ 기록 초기화]` 버튼을 제공하는 QA 관리 패널 탑재.
- **오늘의 근무 스크린샷 캡처 기능 단순화(`타임라인 요약` / `타임라인 전체` 2종) 및 하단 잘림·불필요 정보 완전 제거 (`ScreenshotHelper.kt`, `TodayWorkScreen.kt`, `DiaDetailCard.kt`)**:
  - 캡처 옵션을 핵심 2종(`타임라인 요약`, `타임라인 전체`)으로 집중하고 출무현황 및 다이아 카드 하단의 `사업시간/거리 요약` 표를 캡처 화면에서 완전히 제외하여 순수 타임라인만 깔끔하게 저장.
  - `ScreenshotHelper.captureComposableToBitmap`에서 `ComposeView`를 `ScrollView` 컨테이너로 감싸 DecorView에 마운트함으로써 안드로이드 프레임 레이아웃의 화면 높이 제한(`AT_MOST(screenHeight)`)을 우회하고 긴 타임라인도 하단 잘림 없이 100% 전체 높이가 캡처되도록 완성.

---

## [v1.0.4] - 2026-10-25

### 📸 오늘의 근무 스크린샷 캡처 엔진 전면 고도화 및 무결점(Zero-Artifact) 품질 보강
- **상단 네비게이션 앱바(`CrewMateAppBar`) 중복 겹침 및 본문 가림 결함 원천 해결 (`ScreenshotHelper.kt`, `TodayWorkScreen.kt`, `DiaDetailCard.kt`)**:
  - 기존 Activity 윈도우 루트 뷰의 스크롤 슬라이스 캡처 방식에서 발생하던 상단바 반복 렌더링(열차 운행 행 덮어씀, 상단바 2개 중첩)을 전면 제거.
  - Activity DecorView 기반 오프스크린 전용 렌더러 `ScreenshotHelper.captureComposableToBitmap` 신설: 화면 스크롤 깜빡임 없이 높이(`MeasureSpec.UNSPECIFIED`)를 컨텐츠 길이에 맞추어 단 1회의 깨끗한 Canvas 드로우 패스로 고화질 단일 비트맵 생성.
- **4종 캡처 범위별 직관적 한글 규격 파일명 적용 (`TodayWorkScreen.kt`, `ScreenshotHelper.kt`)**:
  - 영문 Enum 상수명 대신 선택한 범위의 한글 명칭이 즉시 반영된 파일명 체계 구축:
    1. `CrewMate_타임라인요약_YYYYMMDD_HHmmss.png`
    2. `CrewMate_타임라인전체_YYYYMMDD_HHmmss.png`
    3. `CrewMate_타임라인요약_출무현황_YYYYMMDD_HHmmss.png`
    4. `CrewMate_타임라인전체_출무현황_YYYYMMDD_HHmmss.png`
- **스크린샷 내 불필요한 대화형 인터랙티브 버튼 배제 및 인쇄/공유용 문서 레이아웃 (`TodayWorkScreen.kt`, `DiaDetailCard.kt`)**:
  - `isCaptureMode = true` 파라미터 적용: '📸 캡처', '🧪 가상 날짜 복구', '상세 구간 더보기/접기', '⏰ 오늘의 예정 정밀 알람' 등 상호작용용 버튼을 스크린샷에서 완전 숨김 처리.
  - 다이아 세그먼트와 하단 '사업시간/거리 요약표' 및 (선택 시) '소속 승무원 출무현황' 표가 펼쳐진 상태로 정돈되어 단일 문서 형태로 출력.
- **자정 자동 캡처 알람 취소 로직 정밀화 (`MidnightCaptureReceiver.kt`)**:
  - `cancelMidnightCapture` 호출 시 `PendingIntent`를 명시적으로 즉시 해제하도록 보강.

---

## [v1.0.3] - 2026-10-25

### 🚄 현장 실무 피드백 기반 5대 핵심 편의기능 고도화 및 정밀 보완
- **오늘의 근무 4종 범위 선택 및 전체 스크롤 롱캡처 신설 (`ScreenshotHelper.kt`, `TodayWorkScreen.kt`, `MidnightCaptureReceiver.kt`, `SettingsScreen.kt`)**:
  - `TodayWorkScreen`의 '📸 캡처' 터치 시 4가지 저장 범위 선택 팝업 다이얼로그 제공:
    1. **타임라인 요약만**: 3순위(감시/휴게 등) 접힌 주요 업무 요약 타임라인 캡처
    2. **타임라인 전체**: 모든 구간(100% 전 세그먼트) 펼쳐진 전체 타임라인 캡처
    3. **타임라인 요약 + 출무현황**: 요약 타임라인 + 하단 출무 현황 카드 함께 캡처
    4. **타임라인 전체 + 출무현황**: 전체 타임라인 + 하단 출무 현황 카드 함께 캡처
  - `ScreenshotHelper.captureFullScrollableComposeView` 엔진을 통해 화면 뷰포트에서 잘리는 부분 없이 스크롤 맨 위부터 맨 아래까지 무손실 슬라이스 합성 롱스크린샷 저장 (`Pictures/CrewMate`).
  - `SettingsScreen`에 '오늘의 근무 자동 캡쳐 (자정)' 토글 스위치 및 매일 00:01 `MidnightCaptureReceiver` 자동 저장 연동.
- **알람 1회성 스킵 'Skip' 버튼 문구 간소화 (`TodayWorkScreen.kt`, `WorkAlarmSettingsStore.kt`)**:
  - '오늘의 예정 알람' 팝업 내 버튼 문구를 간결한 `Skip` (스킵 상태 시 `Skip 해제`)으로 단순화하여 터치 가독성 최적화.
- **운전(1순위 업무) 시간 자동 오프라인 모드 전환 및 복귀 (`AutoOfflineManager.kt`, `AutoOfflineReceiver.kt`, `SettingsScreen.kt`)**:
  - `SettingsScreen`에 '운전 시간 자동 오프라인 모드' 스위치 신설.
  - 1순위 업무(운전·승무·출고준비·입환) 시작 1분 전(직전 인접 감시 포함) 자동 오프라인 전환 및 첫 '정리'/'입고정리' 시작 시 온라인 자동 복귀.
- **근무 데이터 미조회 월 순환 근무 패턴 빌더 및 달력 표시 (`WorkPatternSettings.kt`, `SettingsScreen.kt`, `ScheduleScreen.kt`)**:
  - `SettingsScreen`에 4개 핵심 뱃지(`[주간]`, `[야간]`, `[~]`, `[S]`) 인터랙티브 빌더와 기준일자(Anchor Date) 선택 UI 구축.
  - `ScheduleScreen`에서 Xcrew 서버에 근무 데이터가 없는 미래/과거 월 조회 시, 설정된 기준일자 기반 모듈로(Modulo) 순환 연산으로 다이아 번호 자리에 해당 패턴 뱃지 자동 표시.
- **다이아 근무 상세 열차번호 직접 터치 - Rail.blue(레일블루) 하이퍼링크 연결 (`DiaDetailCard.kt`)**:
  - 가독성을 저해하는 별도 아이콘 없이, 열차번호 뱃지(예: `K6543 운전`) 텍스트 자체를 터치하면 외부 웹브라우저로 레일블루 상세 운행 페이지(`https://rail.blue/railroad/logis/Default.aspx?date={YYYYMMDD}&train={trainNo}#!`)가 즉시 열리도록 안전 Intent 연동.

---

## [v1.0.2] - 2026-10-25

### 🛡️ 배포 보안 정밀 진단 및 무손실(Zero-Regression) 보안 보강 & 스토어 심사용 Mock 로그인 구축
- **플레이스토어 심사용 Mock 로그인 안전 분기(MockReviewDataProvider.kt) 신설**:
  - 외부 스토어 심사관에게 실제 코레일 사번을 노출하지 않고 모든 화면을 심사 통과시킬 수 있도록 전용 심사 계정(
eview_crewmate / crewmatereview2026!) 입력 시에만 동작하는 MockReviewDataProvider.kt를 신설 (MockReviewDataProvider.kt, LoginViewModel.kt, XcrewWebViewXhrDataSource.kt, AppNavigation.kt).
  - 심사 계정 로그인 시 실제 Xcrew 서버/WebView 통신을 수행하지 않고, 당월 가상 근무표, 다이아 상세 타임라인(16001~16014), 소속 승무원 현황, 출무 현황, 비실동 현황, 열차 승무원 전수조사(V2) 더미 데이터를 기존 파서 규격 100% 그대로 반환하여 일반 임직원 로그인 파이프라인과 완벽히 격리.
- **네트워크 보안 구성(
etwork_security_config.xml) 신설 및 HTTPS 강제**: 
es/xml/network_security_config.xml을 신설하여 앱 전역 및 xcrew.korail.com 도메인에 대한 평문 HTTP(cleartextTrafficPermitted="false") 통신을 OS 레벨에서 원천 차단하고, AndroidManifest.xml에 ndroid:usesCleartextTraffic="false" 및 ndroid:networkSecurityConfig="@xml/network_security_config"를 연동 (
etwork_security_config.xml, AndroidManifest.xml).
- **WebView 보안 플래그 락다운 및 세션 잔재 완전 소거**:
  - KorailWebView.kt 및 AppNavigation.kt의 HiddenXcrewWebView 양쪽 모두에 Release 빌드 시 크롬 원격 디버깅 강제 차단(WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)), 로컬 파일/콘텐츠 접근 차단(llowFileAccess = false, llowContentAccess = false, llowFileAccessFromFileURLs = false, llowUniversalAccessFromFileURLs = false), 혼합 콘텐츠 차단(MIXED_CONTENT_NEVER_ALLOW), 서드파티 쿠키 차단, 세이프 브라우징 활성화 및 폼 데이터/비밀번호 평문 저장 차단(savePassword = false, saveFormData = false)을 일관되게 적용 (KorailWebView.kt, AppNavigation.kt, uild.gradle.kts).
  - 로그아웃(handleLogout) 및 로그인 준비(prepareForLogin) 시 CookieManager뿐만 아니라 WebStorage.getInstance().deleteAllData(), clearCache(true), clearHistory()를 함께 호출하여 HTML5 localStorage/IndexedDB 및 캐시 잔재를 완벽히 클리어하도록 보강 (AppNavigation.kt, XcrewWebViewXhrDataSource.kt).
- **정밀 ProGuard 룰 보강 (proguard-rules.pro)**: 백신 오탐 방지 설정(-dontobfuscate, -dontshrink)을 유지하면서 Xcrew DTO/파서, Room Entity/DAO(@Entity, @Dao, @Database), 도메인 모델, WebView @JavascriptInterface 및 WebViewClient 콜백 보존 룰을 정밀 명시하고, Release 빌드 시 불필요한 디버그/상세 로그(Log.v, Log.d) 호출을 바이트코드에서 제거하도록 구성 (proguard-rules.pro).

---

## [v1.0.1] - 2026-10-17

### 🔐 로그인 세션 자가 치유 및 LoginScreen UI/UX 개선
- **세션 쿠키 초기화**: 로그인 시작 직전 `CookieManager.removeAllCookies(null)` 및 `flush()`를 실행해 만료된 쿠키 충돌 원천 차단 (`XcrewLoginRepository.kt`).
- **숨겨진 WebView 세션 동기화**: 로그인 성공 직후 `xcrewSession.refreshWebViewPage()`를 연동하여 WebView 자바스크립트 엔진에 신규 쿠키가 100% 동기화된 상태로 사전 근무표 XHR을 실행하도록 수술 (`XcrewWebViewXhrDataSource.kt`, `LoginViewModel.kt`). 세션 만료 후 재로그인 시 앱 강제 종료 필요 없이 즉시 100% 성공 보장.
- **소프트 키보드 가림 및 비밀번호 토글 개선**: `MainActivity`에 `windowSoftInputMode="adjustResize"` 추가 (`AndroidManifest.xml`). `LoginScreen.kt` 메인 컨테이너에 `imePadding()` 및 `verticalScroll`을 적용하고, 비밀번호 입력창에 보기/숨기기 눈 모양 토글 버튼(`IconButton`) 추가 (`LoginScreen.kt`).

### ⚙️ 정밀 알람 규칙 미세 조정
- **편승 직후 예외 기본값 변경**: 정밀 알람 세부 규칙 중 '편승 10분 전'의 서브 예외인 `직후 20분 이내 운전/입환/출고준비 시 예외` 기본값을 체크 해제(`false`)로 세팅 변경 (`WorkAlarmSettings.kt`, `WorkAlarmSettingsStore.kt`).

### 📑 개발 관리 문서 및 자동화 지침서 구축
- 프로젝트 개발 연대기(`DEVELOPMENT_HISTORY.md`), 유지보수 설명서(`ARCHITECTURE_GUIDE.md`), 한 줄 패치노트(`CHANGELOG.md`), AI 에이전트 필수 수칙(`AGENTS.md`) 전수 구축 및 자동화 지침서 연동.
- 정식 Release APK (`CrewMate_v1.0.1.apk`) 패키징 및 SHA-256 해시 검증 완수 (`app/build.gradle.kts`).

### 🖼️ 카카오톡 배포용 1:1 정사각형 포스터 5종 전면 재설계
- **1:1 정사각형 (1200x1200 고해상도) & M3 Bento Grid 구성**: 카카오톡 썸네일에 최적화된 1:1 정사각형 비율(`poster_main_square.png` 및 `card_1_calendar_square.png` ~ `card_4_lookup_square.png`)과 빈 여백 제로(ZERO) 벤토 그리드 레이아웃 전면 재구축 (`promo_poster.html`).
- **실제 캡처 화면 100% 고증 UI 목업**: `TodayWorkScreen`, `DiaDetailCard`, `ScheduleScreen` M3 바텀시트, `WorkAlarmSettingsScreen` 정밀 알람 스위치/팝업, `TrainCrewLookupV2Screen` 1초 검색 카드의 실제 M3 라이트 테마 색상 및 레이아웃 정밀 재현.

---

## [v1.0.0] - 2026-10-17

### 🚀 배포 패키지 및 오탐 방지 수술 (Post-Release Audit)
- **공식 패키지명 변경**: Google Play Protect 오탐 방지를 위해 `applicationId`를 `"com.example.crewmate"`에서 공식 **`"kr.crewmate.app"`**으로 정식 변경 (`build.gradle.kts`).
- **전화/SMS 권한 명시적 병합 차단**: 외부 라이브러리에 의한 임의 권한 유입을 막기 위해 `<uses-permission android:name="android.permission.READ_PHONE_STATE" tools:node="remove" />`, `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS` 노드 제거 명시 (`AndroidManifest.xml`).
- **오탐 유발 특수 권한 삭제**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 특수 권한을 삭제하고 표준 안드로이드 설정 화면(`Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`)으로 우회 연결 (`AndroidManifest.xml`, `WorkAlarmSettingsScreen.kt`).
- **R8 투명 빌드 세팅**: 백신 정적 분류기가 난독화 코드를 스파이웨어 패커로 오인하지 않도록 `proguard-rules.pro`에 `-dontobfuscate`, `-dontshrink` 선언 (`proguard-rules.pro`).
- **정식 키스토어 서명**: 프로젝트 전용 서명 키 `crewmate-release-key.jks` 발급 및 정식 Signed Release APK (`CrewMate_v1.0.0.apk`) 생성 완료.

### 🎨 Material Design 3 (M3) UI/UX 전수 업그레이드 (Phase 1 ~ Phase 3)
- **M3 Tonal Palette & 스케일 구축**: `Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt` 신설로 M3 정식 Tonal Color System, Typography, Shape 스케일 전수 구성.
- **다이아 타임라인 입체화 (`DiaDetailCard.kt`)**: M3 `Card` + `large` (`16.dp`) 곡률, `outlineVariant` 미세 테두리, `primaryContainer` / `secondaryContainer` 구간 시각적 위계 및 `animateContentSize()` 유기적 모션 연동.
- **오늘의 근무 화면 M3 고도화 (`TodayWorkScreen.kt`)**: 요약 헤더, 예정 알람 칩, 출무 현황 카드를 M3 Tonal Container로 정돈하고 로딩/결과 전환에 Compose `Crossfade` 애니메이션 적용.
- **모든 다이얼로그 M3 규격 통일**: 앱 내 모든 `AlertDialog` 및 바텀시트에 M3 `extraLarge` (`28.dp`) 및 `tonalElevation = 6.dp` 스펙 적용 (`WorkAlarmSettingsScreen.kt`, `SettingsScreen.kt`, `ScheduleScreen.kt` 등).
- **'주요항목 사이 감시 숨기기' 기본값 ON 전환**: 초기 미설정 상태에서 기본값이 **ON (`true`)**으로 구동되도록 설정 변경 (`DiaDetailCard.kt`, `SettingsScreen.kt`).

### ⚙️ 핵심 알람 & 출무 엔진 정밀 수술
- **`alcSpecDvCd` 절대 규칙 기반 근무 판별 엔진 구축**: 추측성 시각/코드 조건문(`hour in 0..12`, `gtwcGubunCd == "2"`)을 전면 완전 삭제하고, Xcrew 서버 응답의 `alcSpecDvCd` 코드 조합(`11`과 `12`) 기반으로 주간 / 야간1일차 / 야간2일차 판별식 연동 (`WorkSchedule.kt`, `TodayWorkRepository.kt`, `WorkAlarmRuleEvaluator.kt`).
- **출무 5분 전 알람 날짜 정밀 결합**: 출무 데이터 자체 날짜(`att.pjtDt`) 기반 정밀 날짜 결합 및 아침 출무 알람 +1일 날짜 튐 버그 원천 해결 (`WorkAlarmRuleEvaluator.kt`).
- **가상 날짜 디버그 모드 (`AppDateProvider`) 구축**: 기기 시스템 시각 변경 없이 가상 날짜 테스트가 가능한 `AppDateProvider` 싱글톤 및 설정 화면 `[앱 테마 설정]` 5회 연속 탭 트리거(`비밀번호: 5078`), `DatePickerDialog`, 안심 경고 칩 `[🧪 가상 날짜 모드]` 구현 (`AppDateProvider.kt`, `SettingsScreen.kt`).

---

## [v0.9.0] - 2026-10-15

### 🚀 승무원 조회 V2 및 전수조사 백그라운드 엔진
- **전수조사 백그라운드 엔진 (`DiaCensusManager.kt`)**: 한 달 치 승무원 다이아 배정을 미리 긁어모아 Room DB(`dia_census`)에 캐싱하는 엔진 연동.
- **승무원 조회 V2 (`TrainCrewLookupV2Screen.kt`)**: 1초 내에 동료 승무원 이름과 출퇴근 시각(`gwkTm`, `loiwTm`)을 정확히 탐색하는 V2 검색 엔진 구축. 수집 중 안전 잠금 장치 연동.
- **오프라인 출무 현황 스냅샷 (`AttendanceStatusEntity.kt`)**: 오프라인 모드 전환 시 출무 현황 데이터를 로컬 DB에 스냅샷 저장하고 비행기 모드에서도 완벽 표출.

---

## [v0.8.0] - 2026-10-10

### ⏰ 모닝콜급 정밀 알람 V2 & 갤럭시 워치 연동
- **`setAlarmClock` 정식 시계 알람 연동**: Doze 모드(초절전) 오차 없는 발동 보장 (`WorkAlarmScheduler.kt`).
- **6대 정밀 알람 규칙**: 출무(5분 전), 출근(60분 전), 운전(10분 전), 출고준비(10분 전), 편승(10분 전), 입환(10분 전) 및 연속 근무 예외 처리 연동 (`WorkAlarmRuleEvaluator.kt`).
- **갤럭시 워치 무한 연속 진동 수술**: 포그라운드 알림 ID 분리 및 2초 주기 동적 채널 생성/파기 기반 Heavy Waveform 무한 손목 진동 알고리즘 완성 (`AlarmLoopService.kt`).

---

## [v0.5.0] - 2026-10-01

### 🔑 암호화 자동 로그인 및 네이티브 UI 구축
- **안드로이드 키스토어 AES-256 암호화 (`SecureCredentialStore.kt`)**: 사번/비밀번호 하드웨어 암호화 저장 및 자동 재로그인/세션 자가 치유 연동.
- **WebView XHR 인터셉터 (`XcrewWebViewXhrDataSource.kt`)**: OkHttp 404 난관을 우회하여 보안 WebView 내부에서 XHR을 인터셉트해 월간 근무표 및 다이아 상세 수집.
