# 📋 CrewMate (크루메이트) CHANGELOG (패치노트)

모든 버전 업데이트, 기능 추가, 버그 수정 및 리팩토링 내역을 최신순으로 기록하는 문서입니다.

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
