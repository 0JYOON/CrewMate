# 📖 CrewMate (크루메이트) 전체 개발 연대기 (Development History)

이 문서는 안드로이드 앱 **CrewMate(크루메이트)**가 초기 구상 단계부터 완성, 고도화, 그리고 최종 배포 패키징에 이르기까지 거쳐온 모든 기술적 도전, 시행착오, 그리고 해결 과정을 정밀 기록한 개발 이력서입니다.

---

## 🏛️ 1부: 기초 공사 및 핵심 기능 개척기 (Phase 1: STEP 1 ~ STEP 10)

### STEP 1~2: 프로젝트 초기 생성 및 기본 UI 아키텍처 구축
* **목표**: Jetpack Compose 기반 Native Android 프로젝트 생성 및 기본 네비게이션 골격 형성.
* **진행 내용**:
  * 최신 안드로이드 권장 선언형 UI 프레임워크인 **Jetpack Compose**를 도입했습니다.
  * MVI / MVVM 아키텍처 패턴을 기반으로 `AppNavigation.kt` 및 `Routes` 개체를 설계하여 화면 전환 구조를 세웠습니다.
  * 월간 달력(`ScheduleScreen`) 및 로그인(`LoginScreen`) 화면의 기본 레이아웃과 뷰모델(ViewModel) Placeholder를 작성했습니다.

### STEP 3~4: 코레일 Xcrew 시스템 연동 및 'WebView XHR 브릿지' 기술 개발
* **난관 발생 (OkHttp 404 및 세션 거부)**:
  * 코레일 사내 근무 조회 시스템인 Xcrew(`xcrew.korail.com`)는 일반적인 REST API 형태가 아니며, SSO 세션 쿠키, CSRF 보안 토큰, 보안 브라우저 헤더를 철저히 검증합니다.
  * 초기 안드로이드 `OkHttp` 라이브러리로 서버 URL에 직접 HTTP POST/GET 요청을 시도했으나, 보안 검증에 걸려 계속해서 **HTTP 404 Not Found** 또는 세션 오류가 발생하는 큰 난관에 부딪혔습니다.
* **돌파구 (WebView 내부 XHR 인터셉트 파서 구현)**:
  * 앱 내부에 사용자 눈에 보이지 않는 **보안 WebView (`KorailWebView.kt`)**를 띄워 정식 로그인 절차를 진행하게 만들었습니다.
  * 로그인 성공 후 WebView의 자바스크립트(JS) 컨텍스트 내부에서 `XMLHttpRequest(XHR)` 구문을 실행하여 Xcrew 내부 API(`searchExtrRltmCrewWrkPstt.do`)를 호출하고 그 결과 JSON을 네이티브 코드로 수신하는 **`XcrewWebViewXhrDataSource.kt`** 인터셉터 구조를 최초 개발하여 보안 난관을 완벽히 돌파했습니다.

### STEP 5~7: 월간 근무표 파싱 및 달력 타임라인 매핑
* **진행 내용**:
  * Xcrew 근무표 API 응답 JSON 데이터를 해석하는 `WorkScheduleParser.kt` 및 `XcrewWorkScheduleParser.kt`를 작성했습니다.
  * 월간 달력 셀 위에 본인의 당일 다이아 번호(예: `16014`), 휴가, 연차, 입고, 교육 상태를 시각화했습니다.
  * 연속 근무 기호(`~(다이아번호)`) 및 변경 근무(`isChanged`) 파싱 로직을 구현하고, 날짜 클릭 시 하단 바텀시트(BottomSheet)로 당일 상세 스케줄을 표출했습니다.

### STEP 8: 네이티브 로그인 및 하드웨어 암호화 저장소 구축
* **목표**: 매번 WebView를 켜서 아이디/비밀번호를 치던 불편함을 제거하고 안전한 네이티브 자동 로그인 구축.
* **진행 내용**:
  * 안드로이드 하드웨어 키스토어(`AndroidKeyStore`)의 `AES/GCM/NoPadding` (256-bit) 암호화 알고리즘을 적용한 **`SecureCredentialStore.kt`**를 구현했습니다.
  * 사번과 비밀번호를 암호화하여 장치 내부 SharedPreferences에 안전하게 보관하며, 앱 실행 시 백그라운드 세션이 만료된 경우에도 자동으로 재로그인 및 세션을 복구하는 자가 치유(Self-Healing) 자동 로그인 시스템을 완성했습니다.

### STEP 9: '오늘의 근무 DIA' 자동 판별 및 타임라인 UI 구현
* **진행 내용**:
  * 현재 날짜 시각에 맞춰 당일(또는 다음 예정) 근무 다이아를 자동으로 감지하는 `TodayWorkRepository.kt`를 작성했습니다.
  * 승무 다이아 상세 API (`searchExtrCrewDia.do`)를 연동하여 열차번호, 출발역, 도착역, 출발시각, 도착시각, 작업구분(운전, 승무, 출고준비, 편승, 입환, 감시, 휴게 등)을 직관적인 타임라인 셀로 보여주는 `DiaDetailCard.kt` 및 `TodayWorkScreen.kt`를 개발했습니다.

### STEP 10: 동료 승무원 조회 V1 개발
* **진행 내용**:
  * 사업일자와 열차번호를 입력하여 같은 열차에 탑승하는 동료 승무원 이름을 탐색하는 `TrainCrewLookupScreen.kt` 및 `TrainCrewSearch.kt` (V1)를 첫 구현했습니다.

---

## 🚀 2부: 안드로이드 스튜디오 에이전트와 함께한 중·후기 고도화 및 완성기 (Phase 2)

초기 STEP 10 구축 이후, 안드로이드 스튜디오 에이전트와의 협업을 통해 실사용 환경에서 요구되는 강력한 고도화 기능, 오프라인 모드, 정밀 알람, 스마트워치 연동 및 보안 강화 작업이 수행되었습니다.

### STEP 11: 승무원 조회 V2 및 전수조사 백그라운드 엔진 구축
* **문제점 (V1의 한계)**:
  * V1 조회 방식은 개별 열차 검색 시마다 실시간 API를 다수 호출하여 속도가 느리고 수신 실패 위험이 있었습니다.
* **고도화 구현 (`DiaCensusManager.kt`, `TrainCrewLookupV2Screen.kt`, `DiaCensusEntity.kt`)**:
  * 해당 월 전체 승무원 다이아 배정을 백그라운드에서 한 번에 전수 조사하여 가져오는 **`DiaCensusManager`** 엔진을 구축했습니다.
  * 수집된 데이터를 Room DB 테이블(`dia_census`)에 캐싱한 뒤, V2 검색 엔진에서 대표열차번호(`repTrn1No`, `repTrn2No`) 및 구간 열차번호를 즉시 매핑하여 **1초 내에 동료 승무원 이름과 출퇴근 시각(`gwkTm`, `loiwTm`)을 정확히 찾아내는 V2 시스템**을 완성했습니다.
  * 자료 조사 수집 중에는 UI에 프로그레스 바가 표시되고 검색 및 진단 버튼이 안전하게 일시 잠기는 보호 장치를 연동했습니다.

### STEP 12: 출무 현황 조회 및 오프라인 출무 스냅샷 DB 연동
* **고도화 구현 (`AttendanceStatusDto`, `AttendanceStatusEntity`, `TodayWorkRepository`)**:
  * 사내 출무 현황 API를 연동하여 예정출무시각(`gtwcEpctTm`), 실제출무시각(`tnsmTm`), 출무구분 상태를 보여주는 출무 현황 접기/펼치기 UI를 개발했습니다.
  * **오프라인 모드 스냅샷 기술**: '오프라인 모드 전환' 시 네트워크 통신을 완전히 끄더라도 작동하도록, 전환 순간의 양일(1일 차/2일 차) 출무 현황 데이터를 **`AttendanceStatusEntity` 스냅샷으로 Room DB에 저장**하고, 비행기 모드에서도 완벽히 표출되도록 고쳤습니다.

### STEP 13: 비실동 현황 및 개인 휴가 종합 관리
* **고도화 구현 (`NonOpStatusScreen.kt`, `NonOpStatusViewModel.kt`, `LeaveRepository.kt`)**:
  * 비실동 현황 API(`searchNonActiveStatus.do`) 및 개인 휴가 API(`searchPersonalLeave.do`)를 연동했습니다.
  * 연차, 병가, 출장, 교육, 병가 등 비실동 항목을 월간 스케줄 및 별도 현황표 화면에서 종합 조회할 수 있도록 확장했습니다.

### STEP 14: 소속 승무원 근무 현황 전체 조회
* **고도화 구현 (`CrewWorkStatusScreen.kt`, `CrewWorkStatusRepository.kt`, `CrewAssignmentEntity.kt`)**:
  * 사업소 전체 승무원의 일자별 다이아 배정 현황 API(`searchCrewWorkStatus.do`)를 연동하고 Room DB 테이블(`crew_assignments`)에 캐싱하여, 동료들의 당일 다이아 배정을 일괄 조회할 수 있게 구축했습니다.

### STEP 15: 기기 모닝콜 급 정밀 알람 엔진 V1/V2 구축
* **고도화 구현 (`WorkAlarmScheduler.kt`, `AlarmLoopService.kt`, `WorkAlarmRuleEvaluator.kt`)**:
  * 안드로이드 정식 시계 알람 규격인 `AlarmManager.setAlarmClock()`을 정석 연동하여 초절전 모드(Doze Mode)에서도 오차 없이 명확히 발동하게 만들었습니다.
  * **6대 정밀 알람 규칙**: 출무(5분 전), 출근(60분 전), 운전(10분 전), 출고준비(10분 전), 편승(10분 전), 입환(10분 전) 세부 규칙 구축.
  * **지능형 예외 처리**: 직전/직후 N분 이내 연속 근무 시 알람 중복을 피하는 정밀 예외 로직(경계 시각 `arvDateTime` ~ `dptDateTime` 비교) 연동.
  * **실제 출무 완료 시 자동 스킵**: 실제 출무 시각(`actualTime`)이 확인되면 "출무 N분 전" 알람을 자동으로 무시/스킵하는 지능형 로직을 적용했습니다.

### STEP 16: 스마트워치(갤럭시 워치) 무한 연속 진동 및 Heavy Waveform 수술
* **스모킹건 1 (포그라운드 플래그 워치 차단 해제)**:
  * `Service.startForeground()` 호출 시 안드로이드 OS가 강제로 붙이던 `FLAG_FOREGROUND_SERVICE` 플래그로 인해 삼성 Galaxy Wearable 앱이 알림을 차단하던 원인을 발견하고, 백그라운드 서비스 ID(`8800`)와 워치 전송용 독립 알림 ID(`8899`)를 이원화 분리하여 수신을 100% 성공시켰습니다.
* **스모킹건 2 (Wear OS 동일 채널 진동 억제 우회)**:
  * Wear OS가 동일 알림 채널의 연속 갱신 시 진동을 억제(Mute)하는 정책을 우회하기 위해, 매 2초마다 일회용 동적 채널(`crewmate_alarm_watch_pulse_N`)을 생성/파기하는 헤비 파형(`0, 1200ms, 200ms...`) 무한 연속 진동 알고리즘을 완성했습니다.

### STEP 17: 자정 경과 연일/야간 근무 (`alcSpecDvCd == "12"`) 2일 차 표출 보정 및 실시간 강조
* **고도화 구현 (`DiaDetailCard.kt`, `TodayWorkRepository.kt`)**:
  * 야간 근무 2일 차에도 '오늘의 근무' 다이아 표출이 끊기지 않도록 양일 데이터를 연결했습니다.
  * 자정을 넘긴 2일 차 새벽 시각(00:00~12:00) 세그먼트의 `startDateTime` / `endDateTime` 날짜를 +1일 자동 보정하여 타임라인의 파란색 실시간 하이라이트 표시가 정상 작동하게 고쳤습니다.

### STEP 18: UX 단일화, 햄버거 메뉴 정리 및 비전공자 친화적 용어 변경
* **고도화 구현 (`WorkAlarmSettingsScreen.kt`, `TodayWorkScreen.kt`, `SettingsScreen.kt`)**:
  * 예정 알람 목록을 '오늘의 근무' 하단 알람 칩 팝업 다이얼로그로 일원화하고 날짜/요일(`MM.dd (E) HH:mm`) 오름차순 시각순으로 정렬했습니다.
  * 일반 설정 화면에 있던 중복 알람 카드를 정리하고, 햄버거 메뉴명을 **`정밀 알람`**으로 단일화했습니다.
  * 어려운 기술 용어인 '1순위 구간'을 **'주요 항목'**으로 다듬어 가독성을 높였습니다.

### STEP 19: 오프라인 모드 데이터 예비 수집 및 비행기 모드 완전 지원
* **고도화 구현 (`AppNavigation.kt`, `XcrewWebViewXhrDataSource.kt`)**:
  * 월간 스케줄, 상세 다이아, 출무 현황을 미리 기기 로컬 DB에 수집 후, 오프라인 모드 실행 시 네트워크 HTTP 요청을 100% 차단하고 로컬 DB 캐시만으로 모든 조회 기능을 안전하게 제공하도록 보완했습니다.

### STEP 20: 배포 전 종합 검증, 보안 강화 및 Release APK 패키징 (Phase 1 ~ Phase 4)
* **보안 강화**: `AndroidManifest.xml`에 `android:allowBackup="false"`, `exported="false"` 적용 및 KeyStore AES-GCM 암호화, 디버그 로그 개인정보 마스킹 전수 완료.
* **리팩토링**: `goAsync()` + `SupervisorJob()` 적용 및 Room DB 인덱스(`Index`) 지정, Compose `remember` 메모이제이션 적용.
* **최종 배포 패키징**: `proguard-rules.pro` 작성, 정식 버전(`versionCode = 1`, `versionName = "1.0.0"`) 부여 및 `./gradlew.bat assembleRelease` 성공으로 최종 배포용 APK 패키지 완성을 달성했습니다.

---

## 🛠️ 3부: 배포 후 보완 및 고도화 단계 (Phase 3: STEP 21 ~ STEP 26)

실제 APK 빌드 후 백신 오탐 수술, 서명 키 발급, 근무 알람 판별식 정밀화, 가상 날짜 디버그 모드, Material Design 3 (M3) UI/UX 전수 업그레이드 및 카카오톡 배포용 홍보 포스터 개발이 차례로 완성되었습니다.

### STEP 21: 백신 엔진(Google Play Protect / Ikarus) 정적 오탐 원인 규명 및 오탐 방지 3대 수술
* **오탐 원인 진단**:
  * VirusTotal 정밀 분석 결과 백신 엔진의 정적 머신러닝(Static ML)이 평판이 없는 자가서명 키 + 부팅 리시버 + 백그라운드 서비스 + **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 특수 권한** 및 테스트용 패키지명(`com.example.*`)을 스파이웨어/SMS 트로젠 패커 패턴으로 오인한 근거를 규명했습니다.
* **오탐 방지 3대 정밀 수술**:
  1. **`applicationId` 변경**: `app/build.gradle.kts`의 `applicationId`를 Play Protect 오탐 원인인 `com.example.*`에서 공식 **`"kr.crewmate.app"`**으로 정식 변경. (`namespace = "com.example.crewmate"`는 유지하여 코드 파괴 방지).
  2. **특수 권한 삭제 및 전화/SMS 명시적 차단**: `AndroidManifest.xml`에서 오탐 유발 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한을 삭제하고, `xmlns:tools`를 활용해 `<uses-permission android:name="android.permission.READ_PHONE_STATE" tools:node="remove" />`, `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS` 차단 노드를 명시.
  3. **R8 투명 빌드 세팅**: `proguard-rules.pro`에 **`-dontobfuscate`** 및 **`-dontshrink`** 선언을 추가하여 백신이 난독화 코드를 악성 패커로 오해하지 않도록 바이트코드를 투명하게 세팅.

### STEP 22: 정식 서명 키(Keystore) 생성 및 Signed Release APK 재생성
* **진행 내용**:
  * Android Studio JBR Keytool 명령어로 프로젝트 전용 정식 서명 키스토어 **`crewmate-release-key.jks`** (alias: `crewmate_key`, 유효기간: 10,000일) 생성.
  * `app/build.gradle.kts` 내 `signingConfigs.release` 및 `buildTypes.release` 연동 완료.
  * `./gradlew.bat assembleRelease`로 최종 서명이 완료된 정식 배포용 **`CrewMate_v1.0.0.apk`** (SHA-256: `12F02A784E911D71BC68863CCC7E68E151C5AF92267955DFBC5AA700258A608C`) 재생성 및 루트 폴더 배치 완수.

### STEP 23: `alcSpecDvCd` 절대 규칙 기반 근무 판별 엔진 구축 및 출무 알람/현황 정밀 수술
* **문제점 (기존 추측성 시각 조건문의 한계)**:
  * 기존 `hour in 0..12` 및 `gtwcGubunCd == "2"` 추측성 조건문 때문에 주간 근무임에도 전날 출무 기록이 노출되거나, 아침 출무 알람 시각에 +1일이 더해져 다음 날 아침으로 알람이 밀리는 치명적 오작동이 있었습니다.
* **수술 내용 (`WorkSchedule.kt`, `TodayWorkRepository.kt`, `WorkAlarmRuleEvaluator.kt`)**:
  * 추측성 조건문 전면 완전 삭제! Xcrew 응답의 **`alcSpecDvCd` 절대 규칙 코드** 기반 판별 헬퍼 함수 (`List<WorkSchedule>.determineShiftType()`) 구축:
    * **주간(일근) 근무**: 오늘 == `"11"` && 내일 != `"12"` (오직 오늘 하루치 출무 현황만 수집/표출, 당일 출무 5분 전 알람 정밀 수립).
    * **야간(박) 1일 차**: 오늘 == `"11"` && 내일 == `"12"` ([오늘, 내일] 이틀 치 출무 현황 수집 및 오프라인 스냅샷 저장).
    * **야간(박) 2일 차**: 오늘 == `"12"` ([어제, 오늘] 이틀 치 출무 현황 수집 및 오프라인 스냅샷 저장).
  * 주간 근무 전날 출무 혼입 및 아침 출무 알람 날짜 튐 버그를 100% 원천 해결했습니다.

### STEP 24: 가상 날짜 디버그 모드 (`AppDateProvider`) 구축
* **목표**: 스마트폰 시스템 시각을 바꾸지 않고 앱 내부에서 가상 날짜로 스케줄과 정밀 알람을 자유롭게 테스트할 수 있는 기능 구축.
* **진행 내용 (`AppDateProvider.kt`, `SettingsScreen.kt`, `TodayWorkScreen.kt`)**:
  * 싱글톤 `AppDateProvider` 구축 (`overrideDate: StateFlow<LocalDate?>`, `today`, `setCustomDate()`, `resetToRealToday()`).
  * `SettingsScreen.kt` 내 **`[앱 테마 설정]` 카드를 2초 내 5회 연속 탭**하면 관리자 인증 다이얼로그(`비밀번호: 5078`) 팝업 및 `DatePickerDialog` 연동.
  * 가상 날짜 모드 활성화 시 화면 상단에 빨간색 강조 칩 `[🧪 가상 날짜 모드: YYYY-MM-DD]` 표시 및 원터치 복구 기능 연동.

### STEP 25: Google Material Design 3 (M3) UI/UX 전수 업그레이드 완수 (1~3단계)
* **1단계**: M3 Tonal Color Palette (`Color.kt`, `Theme.kt`), M3 Shape (`Shape.kt`), M3 Typography (`Type.kt`) 스케일 구축, `DiaDetailCard` 입체 타임라인 카드 업그레이드.
* **2단계**: '주요항목 사이 감시 숨기기' 기본값을 **ON(`true`)**으로 변경, 햄버거 메뉴(`DropdownMenuItem` M3 Active Indicator), 달력 바텀시트(`ModalBottomSheet` M3 DragHandle & extraLarge 28.dp 곡률) 적용.
* **3단계**: `TodayWorkScreen` ➔ Compose `Crossfade` 애니메이션, `DiaDetailCard` ➔ `animateContentSize()`, `TrainCrewLookupV2Screen` ➔ M3 `LinearProgressIndicator` (StrokeCap.Round), 모든 `AlertDialog` ➔ M3 `extraLarge` (`28.dp`) 및 `tonalElevation = 6.dp` 규격 통일 완수.

### STEP 26: 카카오톡 배포용 고화질 카드뉴스 웹페이지 (`promo_poster.html`) 개발
* **진행 내용**:
  * 동료 승무원들에게 전달할 5장 세로형 카드뉴스 웹페이지 `promo_poster.html` 개발.
  * `html2canvas` 2x 스케일 연동으로 클릭 한 번에 `card_1.png` ~ `card_5.png` (1080x1350) 고해상도 PNG 즉시 다운로드 지원.

### STEP 27: 재로그인 세션 복구 수술, UI 가림 해결, 자동화 지침서 구축 및 v1.0.1 출시
* **세션 복구 수술 (`XcrewLoginRepository.kt`, `XcrewWebViewXhrDataSource.kt`, `LoginViewModel.kt`)**:
  * 로그인 시도 직전 `CookieManager.removeAllCookies(null)` 및 `flush()`를 실행하여 이전 만료 세션 쿠키 잔재를 완벽히 초기화.
  * 로그인 성공 직후 `xcrewSession.refreshWebViewPage()`를 연동하여 WebView 자바스크립트 엔진에 신규 쿠키를 100% 동기화한 상태에서 사전 근무표 XHR을 실행하도록 수술. 세션 만료 후 재로그인 시 앱 강제 종료 필요 없이 즉시 100% 로그인 성공 보장.
* **소프트 키보드 가림 및 비밀번호 토글 개선 (`AndroidManifest.xml`, `LoginScreen.kt`)**:
  * `MainActivity`에 `windowSoftInputMode="adjustResize"` 지정. `LoginScreen.kt` 메인 컨테이너에 `imePadding()` 및 `verticalScroll()` 적용.
  * 비밀번호 입력창 우측(`trailingIcon`)에 보기/숨기기 눈 모양 토글 버튼(`IconButton`)을 추가하여 원문 확인과 마스킹 전환 지원.
* **정밀 알람 규칙 미세 조정 (`WorkAlarmSettings.kt`, `WorkAlarmSettingsStore.kt`)**:
  * '편승 10분 전'의 서브 예외인 `직후 20분 이내 운전/입환/출고준비 시 예외` 기본값을 체크 해제(`false`)로 세팅 변경.
* **개발 관리 문서 자동화 구축 및 v1.0.1 Release APK 패키징 (`AGENTS.md`, `CHANGELOG.md`, `build.gradle.kts`)**:
  * AI 에이전트 작업 필수 지침서(`AGENTS.md`) 및 패키지노트(`CHANGELOG.md`) 전수 구축.
  * `versionCode = 2`, `versionName = "1.0.1"` 정식 출시 버전 지정 및 `CrewMate_v1.0.1.apk` 정식 서명 빌드 완성.

### STEP 28: 카카오톡 배포용 1:1 정사각형 Bento Grid 포스터 5종 전면 재설계 (`promo_poster.html`)
* **진행 내용**:
  * 카카오톡 썸네일 공유에 최적화된 1:1 정사각형 비율(`600x600` 디스플레이 ➔ `html2canvas` 2x 스케일 적용 `1200x1200` 고해상도 PNG) 포스터 5종(`poster_main_square.png` 및 `card_1_calendar_square.png` ~ `card_4_lookup_square.png`) 전면 재설계.
  * 빈 여백 제로(ZERO) 벤토 그리드(Bento Grid) 및 2분할 꽉 찬 카드 구성 적용.
  * 실제 캡처 화면 100% 고증: M3 라이트 테마 색상(`#F6F8FC`, `#0F2942`, `#D2F5EE`, `#E8F0FE`), M3 곡률, 상단 앱바(`김철도 (안산승소)`), `TodayWorkScreen`, `DiaDetailCard` 타임라인 세그먼트, `ScheduleScreen` M3 바텀시트, `WorkAlarmSettingsScreen` 스위치, `TrainCrewLookupV2Screen` 1초 동료 검색 카드 정밀 재현.
  * 전문용어 배제 및 원터치 PNG 다운로드 연동 완료.

### STEP 29: 배포 보안 정밀 진단, 무손실(Zero-Regression) 보안 보강 및 스토어 심사용 Mock 로그인 구축
* **목표**: 향후 대규모 임직원 배포 및 Google Play 비공개 테스트 트랙 전환을 대비하여, 기존 비즈니스 로직과 UI를 단 한 줄도 훼손하지 않고(Zero Regression) 네트워크/WebView/ProGuard 보안을 락다운하며 스토어 심사관 전용 Mock 로그인 파이프라인을 구축.
* **진행 내용 (MockReviewDataProvider.kt, 
etwork_security_config.xml, AndroidManifest.xml, proguard-rules.pro, KorailWebView.kt, AppNavigation.kt, XcrewWebViewXhrDataSource.kt, LoginViewModel.kt)**:
  1. **스토어 심사용 Mock 로그인 안전 분기 (MockReviewDataProvider.kt)**:
     * 외부 심사관에게 실제 사내 계정을 제공하지 않고도 모든 화면을 심사 통과시킬 수 있도록 전용 테스트 사번(
eview_crewmate / crewmatereview2026!) 입력 시에만 활성화되는 MockReviewDataProvider를 구현했습니다.
     * XcrewWebViewSession.isMockReviewSession 플래그를 통해 월간 근무표, 오늘의 근무 다이아 상세(16001 등), 출무 현황, 소속 승무원 현황, 비실동 현황, 승무원 조회 V2 전수조사까지 모두 로컬 Mock JSON/DTO로 즉시 응답하며, 일반 임직원 로그인 흐름에는 0%의 영향도 주지 않도록 격리했습니다.
  2. **네트워크 보안 구성 신설**: 
es/xml/network_security_config.xml을 생성하여 평문 HTTP 통신(cleartextTrafficPermitted="false")을 전역 차단하고 xcrew.korail.com 도메인에 시스템 인증서 기반 HTTPS 전용 통신을 강제했으며, AndroidManifest.xml에 ndroid:usesCleartextTraffic="false"와 함께 연동했습니다.
  3. **WebView 보안 플래그 락다운 및 세션 클리어 고도화**:
     * KorailWebView.kt와 백그라운드 XHR 통신을 수행하는 AppNavigation.kt의 HiddenXcrewWebView 양쪽 모두에 WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)를 적용해 Release APK에서의 WebView 인스펙터 디버깅/쿠키 탈취를 원천 차단했습니다.
     * llowFileAccess = false, llowContentAccess = false, llowFileAccessFromFileURLs = false, llowUniversalAccessFromFileURLs = false, MIXED_CONTENT_NEVER_ALLOW, 서드파티 쿠키 차단, savePassword = false, saveFormData = false를 양쪽 WebView에 동일하게 적용했습니다.
     * 로그아웃(handleLogout) 및 재로그인 초기화(prepareForLogin) 시 CookieManager 쿠키 삭제에 더해 WebStorage.getInstance().deleteAllData(), clearCache(true), clearHistory()를 호출하여 WebStorage 잔재까지 완벽히 파기하도록 보강했습니다.
  4. **정밀 ProGuard 룰 보강**: 백신 오탐 방지 설정(-dontobfuscate, -dontshrink)과 함께 Room @Entity/@Dao/@Database, Xcrew DTO/파서, 도메인 모델, @JavascriptInterface 및 WebViewClient 콜백 보존 룰을 명시하고, Release 빌드에서 Log.v/Log.d 로그를 제거하도록 구성했습니다.

---



---

### STEP 30: 현장 실무 피드백 기반 5대 핵심 편의기능 고도화 및 4종 캡처 정밀 보완
* **목표**: 일선 승무원 현장 실무 사용 경험을 바탕으로 도출된 5가지 핵심 요구사항(스크린샷 4종 범위 선택 및 전체 스크롤 롱캡처, 알람 당일 Skip 간소화, 운전 업무 자동 오프라인 전환, 미조회 월 순환 근무 패턴 빌더, 열차번호 직접 터치 레일블루 연동)을 기존 무손실/고밀도 설계 원칙에 맞추어 완벽히 구현.
* **진행 내용 (ScreenshotHelper.kt, MidnightCaptureReceiver.kt, WorkAlarmSettingsStore.kt, WorkAlarmRuleEvaluator.kt, AutoOfflineManager.kt, AutoOfflineReceiver.kt, WorkPatternSettings.kt, TodayWorkScreen.kt, DiaDetailCard.kt, SettingsScreen.kt, ScheduleScreen.kt)**:
  1. **오늘의 근무 스크린샷 4종 범위 선택 및 전체 스크롤 합성 엔진 (`ScreenshotHelper.kt`, `TodayWorkScreen.kt`, `MidnightCaptureReceiver.kt`)**:
     * `TodayWorkScreen`의 '📸 캡처' 터치 시 4가지 범위 선택 다이얼로그(1. 타임라인 요약만 / 2. 타임라인 전체 / 3. 타임라인 요약 + 출무현황 / 4. 타임라인 전체 + 출무현황) 제공.
     * `ScreenshotHelper.captureFullScrollableComposeView`를 신설하여 Compose 뷰포트에서 잘리는 부분 없이 스크롤 상단부터 하단까지 무손실 슬라이스 합성 롱스크린샷을 `Pictures/CrewMate` 앨범에 PNG로 보관.
     * `SettingsScreen`에 '오늘의 근무 자동 캡쳐 (자정)' 토글 스위치 및 `MidnightCaptureReceiver`(매일 00:01) 백그라운드 스케줄러 연동.
  2. **오늘의 예정 알람 1회성 스킵 ("Skip") 간소화 (`WorkAlarmSettingsStore.kt`, `TodayWorkScreen.kt`)**:
     * '오늘의 예정 알람' 팝업 내 버튼 문구를 `Bypass (스킵)`에서 `Skip` (스킵 상태 시 `Skip 해제`)으로 단순화하여 터치 가독성 극대화.
  3. **운전(1순위 업무) 시간 자동 오프라인 모드 전환 및 복귀 (`AutoOfflineManager.kt`, `AutoOfflineReceiver.kt`, `SettingsScreen.kt`)**:
     * `SettingsScreen`에 '운전 시간 자동 오프라인 모드' 스위치 신설.
     * 1순위 업무(운전, 승무, 출고준비, 입환) 시작 1분 전(단, 1순위 직전 '감시'가 인접해 있는 경우 감시 시작 1분 전)에 OS 알람(`AutoOfflineReceiver`)을 통해 자동으로 앱을 오프라인 모드로 전환.
     * 1순위 업무 이후 처음 등장하는 '정리' 또는 '입고정리' 시작 시각(미발견 시 당일 근무 종료 시각)에 온라인 모드로 자동 복귀하도록 스케줄러 연동.
  4. **미조회 달 순환 근무 패턴 빌더 및 달력 표시 (`WorkPatternSettings.kt`, `SettingsScreen.kt`, `ScheduleScreen.kt`)**:
     * `SettingsScreen`에 4개 핵심 뱃지(`[주간]`, `[야간]`, `[~]`, `[S]`)를 터치하여 원하는 순서와 횟수로 순환 주기를 구성하는 인터랙티브 뱃지 빌더와 기준일자(Anchor Date) 선택 UI 구현.
     * `ScheduleScreen`에서 Xcrew 데이터가 없는 날짜에 대해 모듈로(Modulo) 순환 연산으로 다이아 영역에 패턴 뱃지를 표시하여 교번 근무자가 미래 일정을 한눈에 파악 가능.
  5. **다이아 근무 상세 열차번호 직접 터치 Rail.blue(레일블루) 하이퍼링크 (`DiaDetailCard.kt`)**:
     * 가독성을 해치는 별도 아이콘 없이, 열차번호 뱃지(예: `K6543 운전`) 자체를 터치하면 `https://rail.blue/railroad/logis/Default.aspx?date={YYYYMMDD}&train={trainNo}#!` 웹페이지를 외부 브라우저로 바로 띄워 열차 편성 및 운행 상세 정보를 원터치 조회 가능하도록 구현.
  6. **단위 테스트 및 빌드 검증 완수**: `EnhancementsUnitTest.kt` 단위 테스트 100% 통과, `assembleDebug` 및 `assembleRelease` 0 에러 빌드 검증 완료.

---

### [STEP 31] 오늘의 근무 스크린샷 엔진 무결점(Zero-Artifact) 개편 및 한글 규격 파일명 반영 (v1.0.4)
* **작업 배경**:
  * 실기기 QA 테스트 중 스크린샷 상단 및 타임라인 중간에 공통 네비게이션 앱바(`[ ☰ CrewMate 윤영준(안산승소) [재접속] ]`)가 중복으로 찍히고 운행 세그먼트(`4503 편승` 등)를 덮어버리는 결함 제보.
  * 또한 저장되는 파일명이 영문 Enum 상수(`CrewMate_TIMELINE_SUMMARY_...`)로 저장되어 직관성이 떨어지고, '상세 구간 더보기/접기', '📸 캡처' 등 대화형 UI 버튼이 스크린샷에 잔재하는 문제 발견.
* **진행 내용 (`ScreenshotHelper.kt`, `TodayWorkScreen.kt`, `DiaDetailCard.kt`, `MidnightCaptureReceiver.kt`)**:
  1. **무결점 오프스크린 Compose 렌더러 (`ScreenshotHelper.captureComposableToBitmap`) 신설**:
     * 화면 뷰를 스크롤하며 조각내어 합성하던 기존 방식(Slicing)을 전면 폐기하고, Activity의 Window DecorView에 인비저블 상태로 일시 마운트하여 컨텐츠 전체 높이(`MeasureSpec.UNSPECIFIED`)를 단 1회의 Canvas Pass로 깨끗하게 드로우하는 엔진 구축.
     * 사용자 화면이 스크롤되거나 깜빡이지 않으며, 상단 앱바(`CrewMateAppBar`)가 원천 배제되어 중복 및 텍스트 가림 현상 0% 달성.
  2. **4종 캡처 범위별 직관적 한글 규격 파일명 적용**:
     * `WorkCaptureOption`에 `filePrefix` 속성을 정의하여 갤러리에 저장 시 선택한 한글 명칭이 명확히 반영되도록 규격화:
       * `CrewMate_타임라인요약_YYYYMMDD_HHmmss.png`
       * `CrewMate_타임라인전체_YYYYMMDD_HHmmss.png`
       * `CrewMate_타임라인요약_출무현황_YYYYMMDD_HHmmss.png`
       * `CrewMate_타임라인전체_출무현황_YYYYMMDD_HHmmss.png`
  3. **인쇄/공유용 전용 문서 레이아웃 (`TodayWorkCaptureLayout`, `DiaDetailCard.kt`)**:
     * `DiaDetailCard`에 `isCaptureMode = true` 파라미터를 추가하여, 캡처 시에는 '상세 구간 더보기/접기' 텍스트 버튼을 숨기고 순수 타임라인 목록과 하단 '사업시간/거리 요약표'가 기본 펼침 상태로 정돈되어 출력되도록 개선.
     * 3, 4번 옵션(출무현황 포함) 선택 시 '소속 승무원 출무현황' 표가 펼쳐진 상태로 타임라인 하단에 매끄럽게 연결.
  4. **자정 자동 캡처 리시버 (`MidnightCaptureReceiver.kt`) 정밀화**:
     * `cancelMidnightCapture` 호출 시 `PendingIntent`를 명시적으로 즉시 cancel하도록 보강.
  5. **빌드 검증**: `gradle test`, `assembleDebug`, `assembleRelease` 100% 무오류 통과.

---

### [STEP 32] 실무 승무 QA 3대 결함 해결: 자동 로그인 속도/안정성 복원, 운전 시간 자동 오프라인 복구 및 QA 패널, 타임라인 캡처 완성 (v1.0.5)
* **작업 배경**:
  * 실제 승무 중 테스트에서 (1) `"자동 로그인 중입니다..."` 문구에서 무한 대기하며 자동 로그인이 실패하는 현상, (2) 1순위 운전 시간 시작 시 자동 오프라인 모드 전환이 전혀 작동하지 않는 현상(및 설정 화면 내 전환 시각 QA 기록 확인 필요), (3) 캡처 시 타임라인 하단이 잘리거나 하단에 불필요한 정보(`사업시간/거리 요약`, `출무현황`)가 노출되는 3대 문제점이 확인됨.
* **진행 내용 (`XcrewWebViewXhrDataSource.kt`, `XcrewLoginRepository.kt`, `LoginViewModel.kt`, `AutoOfflineManager.kt`, `AutoOfflineReceiver.kt`, `TodayWorkRepository.kt`, `BootReceiver.kt`, `AppNavigation.kt`, `SettingsScreen.kt`, `ScreenshotHelper.kt`, `TodayWorkScreen.kt`, `DiaDetailCard.kt`)**:
  1. **자동 로그인 무한 대기(`"자동 로그인 중입니다..."`) 원천 해결**:
     * `prepareForLogin()`에서 매 로그인마다 캐시를 지우던 `clearCache(true)`, `WebStorage.deleteAllData()`와 경쟁 요청을 유발하던 `loadUrl("loginView.do")`를 제거하여 `OkHttpClient`가 저장한 인증 `JSESSIONID` 쿠키가 WebView의 뒤늦은 미인증 응답에 덮어쓰이던 레이스 컨디션 해결.
     * `refreshWebViewPage()`에서 `view.stopLoading()`을 `resetPageReady("https://xcrew.korail.com/extrCrewMg/")`보다 먼저 실행하고 `HiddenXcrewWebView.onPageFinished`에서 URL prefix 및 `view.progress >= 100`을 검증하여 취소된 이전 페이지가 `pageReady`를 조기 완료시키던 버그 차단.
     * `executeXhr()`에서 네비게이션 전환으로 JS 컨텍스트(`window.__crewMateXcrewXhrResults`)가 사라진 경우 15초 대기 없이 즉시 감지(`__NAV_RESET__`)해 페이지 로드 완료 후 1회 재요청하도록 개선.
  2. **운전 시간(1순위) 자동 오프라인 모드 작동 복구 및 설정 화면 QA 시각 기록 패널 구축**:
     * `SettingsScreen.kt`와 `AutoOfflineManager.kt` 간 SharedPreferences 키 불일치(`"auto_offline_mode"` vs `"auto_offline_enabled"`)를 `"auto_offline_mode"`로 통일.
     * `TodayWorkRepository.kt`에서 정밀 알람 스위치(`settings.alarmEnabled`)가 꺼져 있어도 자동 오프라인 스케줄링이 독립적으로 실행되도록 분리하고, 하루 다중 운전 구간(`calculateOfflineSchedules`) 및 이미 진입한 운전 구간 즉시 전환(`syncFromDatabase`)을 구현.
     * `AppNavigation.kt`에 `OnSharedPreferenceChangeListener`를 연동하여 백그라운드 전환 시 앱 상단 `OFFLINE` 배지 및 오프라인 상태가 실시간 동기화되도록 수정.
     * `SettingsScreen.kt`의 '운전 시간 자동 오프라인 모드' 카드 내부에 **예정 스케줄(OFF/ON 시각)**, **최근 오프라인 전환 시각**, **최근 온라인 복귀 시각** 및 `[🔄 스케줄 재계산]`, `[🗑️ 기록 초기화]` 버튼을 갖춘 QA 관리 패널 추가.
  3. **타임라인 요약 / 타임라인 전체 2종 무결점 캡처 완성**:
     * `WorkCaptureOption`을 `타임라인 요약`과 `타임라인 전체` 2종으로 단순화하고, 출무현황 및 `DiaDetailCard` 하단 `사업시간/거리 요약`을 캡처 모드(`isCaptureMode = true`)에서 완전히 배제.
     * `ScreenshotHelper.captureComposableToBitmap`에서 `ComposeView`를 `ScrollView`에 감싸 DecorView에 부착함으로써 안드로이드 `FrameLayout`의 화면 높이 제한(`AT_MOST(screenHeight)`)을 해제하고 긴 타임라인도 하단 잘림 없이 전체가 깔끔하게 캡처되도록 수정.
  4. **단위 테스트 및 빌드 검증**: `EnhancementsUnitTest.kt` 다중 운전 구간 테스트 추가 및 통과, `gradle test`, `assembleDebug`, `assembleRelease` 0 에러 검증 완료.

---

### [STEP 33] 전 화면 다이아 상세 조회 무결점 캡처(`타임라인 요약` / `타임라인 전체`) 전면 확장 (v1.0.6)
* **작업 배경**:
  * `TodayWorkScreen`(오늘의 근무)에서 완성된 무결점 캡처 엔진의 편의성이 검증됨에 따라, 크루메이트 앱 내에서 다이아 상세를 조회할 수 있는 모든 화면(월별 달력 상세, 승무 다이아 조회, 소속 승무원 현황 다이아 상세, 열차 승무원 조회 V2 다이아 상세)에서도 동일하게 캡처 기능을 사용할 수 있도록 전면 확장 요청.
* **진행 내용 (`DiaCaptureHelper.kt`, `ScheduleScreen.kt`, `DiaLookupScreen.kt`, `CrewWorkStatusScreen.kt`, `TrainCrewLookupV2Screen.kt`, `TodayWorkScreen.kt`)**:
  1. **공통 다이아 캡처 헬퍼 모듈 (`DiaCaptureHelper.kt`) 신설**:
     * `DiaCaptureOption`(`타임라인 요약`, `타임라인 전체`), 전용 인쇄용 헤더 문서 레이아웃(`DiaCaptureLayout`), 2선택 팝업 다이얼로그(`DiaCaptureOptionDialog`) 및 비트맵 생성/갤러리 저장 실행 엔진(`executeCapture`)을 공통화하여 화면별 코드 중복을 제거.
  2. **월별 승무근무표 (`ScheduleScreen.kt`) 연동**:
     * 달력에서 특정 날짜를 터치하면 열리는 상세 바텀시트(`ScheduleDetailSheet`) 헤더 우측에 `📸 캡처` 버튼을 배치하여, 다이아 정보 로드 완료 시 원터치로 요약/전체 캡처 가능하도록 구현.
  3. **승무 다이아 조회 (`DiaLookupScreen.kt`) 연동**:
     * 사업일자와 다이아 번호를 검색하여 상세 정보 로드 성공(`DiaDetailLoadState.Success`) 시 상단 헤더 우측에 `📸 캡처` 버튼 노출 및 저장 연동.
  4. **소속 승무원 근무 현황 (`CrewWorkStatusScreen.kt`) & 열차 승무원 조회 V2 (`TrainCrewLookupV2Screen.kt`) 연동**:
     * 항목 및 다이아 클릭 시 열리는 상세 바텀시트(`ScheduleDetailSheet`)에 공통 캡처 버튼이 자동 제공되어 승무원 검색 및 현황 확인 중에도 즉시 캡처 가능.
  5. **오늘의 근무 (`TodayWorkScreen.kt`) 리팩토링**:
     * 기존 화면에 중복 선언되어 있던 캡처 옵션과 레이아웃 코드를 공통 `DiaCaptureHelper` 기반으로 정돈하여 기능 100% 보존(Zero-Regression) 및 코드 경량화 달성.
  6. **빌드 검증**: `gradle testDebugUnitTest`, `assembleDebug`, `assembleRelease` 100% 무오류 완료.

---

### [STEP 34] 상세 다이아 바텀시트 캡처 버튼 가변 노출 및 상태 반응성 결함 수정 (v1.0.7)
* **작업 배경**:
  * 소속 승무원 근무 현황(`CrewWorkStatusScreen`) 및 열차 승무원 조회 V2(`TrainCrewLookupV2Screen`)에서 상세 다이아를 열었을 때, 세로 화면에서는 `📸 캡처` 버튼이 보이지 않다가 가로 화면으로 회전하면 비로소 나타나는 UI 상태 불일치 현상 제보.
* **진행 내용 (`ScheduleScreen.kt`)**:
  1. **원인 규명**:
     * 바텀시트(`ScheduleDetailSheet`) 헤더의 `captureItems`가 `remember(schedules, detailCache, dateKey)`로 감싸져 있어, 비동기 통신 전 초기 `emptyList()`가 고정된 채 `detailCache` 내용이 로드되어도 맵 인스턴스 주소 불변으로 인해 캐시 항목이 갱신되지 않는 결함 확인.
  2. **반응형 상태 구독 전환**:
     * `captureItems`를 컴포지션 시 실시간으로 계산하는 동적 프로퍼티로 전환하여, `detailCache`에 `DiaDetailLoadState.Success`가 들어오는 즉시 0.1초 만에 상단 `📸 캡처` 버튼이 활성화되도록 수정.
     * 날짜 텍스트에 `Modifier.weight(1f, fill = false)`를 부여하여 좁은 화면에서도 캡처 버튼 레이아웃이 찌그러지거나 밀려나지 않도록 방어.
  3. **빌드 검증**: `testDebugUnitTest` 100% 통과, `assembleDebug` 및 `assembleRelease` 0 에러 검증 완료.

---

### [STEP 35] 오늘의 근무 자정 자동 캡쳐 로직 전면 점검 및 타임라인 요약/전체 선택 옵션 신설 (v1.0.8)
* **작업 배경**:
  * 캡처 기능이 여러 차례 고도화됨에 따라 `설정` 화면의 `오늘의 근무 자동 캡쳐 (자정)` 기능이 현행 코드베이스 및 다이아 로직에서 안정적으로 동작하는지 점검하고, 수동 캡처와 마찬가지로 자정 자동 캡처 시에도 '타임라인 요약본'과 '타임라인 전체본' 중 원하는 형태를 선택할 수 있도록 옵션 제공 요청.
* **진행 내용**:
  1. **현행 자정 캡처 로직 결함 해결 (`MidnightCaptureReceiver.kt`)**:
     * 고정 높이 캔버스(1440px) 및 `take(15)` 제한으로 인해 15개 초과 세그먼트나 복수 다이아가 잘리거나 누락되던 버그 수정.
     * 다이아 세그먼트 수에 따라 전체 높이를 동적으로 정밀 계산하는 **가변 높이(Dynamic-Height) 헤드리스 캔버스 렌더러**를 구축하여 아무리 긴 다이아라도 잘림 0% 무결점 저장 보장.
     * 전일 야간 연속 근무(Overnight) 다이아에 대한 fallback 탐색 로직을 보강하여 자정 실행 시 당일 다이아 조회의 견고성 향상.
     * 시그니처 네이비 헤더, 다이아 카드 테두리, 업무 순위별 뱃지(Rank 1 블루, Rank 2 앰버, Rank 3 그레이), 출발/도착역 및 화살표 시간 표기가 적용된 고품질 모던 카드 디자인 비트맵 렌더링.
  2. **설정 화면 내 자동 캡처 형태 선택 옵션 신설 (`SettingsScreen.kt`)**:
     * `오늘의 근무 자동 캡쳐 (자정)` 스위치가 켜지면 하단에 **`[타임라인 요약]`**과 **`[타임라인 전체]`**를 선택할 수 있는 직관적인 세그먼트 카드가 표출되도록 구현.
     * 선택값(`"midnight_auto_capture_option"`)을 SharedPreferences에 실시간 저장하고 `MidnightCaptureReceiver`와 동기화.
  3. **세그먼트 분류 로직 공용화 (`DiaDetailCard.kt`)**:
     * `DiaDetailCard.kt`의 `categorizeSegments`를 `internal`로 공개하여 `MidnightCaptureReceiver`에서도 수동 캡처와 동일한 1/2/3순위 및 샌드위치 감시 판단 알고리즘을 단일 소스 오브 트루스로 공유.
  4. **빌드 검증**: `testDebugUnitTest`, `assembleDebug`, `assembleRelease` 100% 무오류 완료.

---

### [STEP 36] 실무 운용 7대 버그 해결 및 편의 기능 고도화 패치 (v1.0.9)
* **작업 배경**:
  * 실제 근무 투입 및 현장 테스트에서 보고된 7대 버그 및 개선 요구사항을 원천 해결:
    1. 자동 오프라인 모드에서 온라인 복귀 시간 설정(정리/입고정리 시작 후 지연) 및 복귀 Skip 기능
    2. 앱 재설치/기기 변경 대비 사용자 데이터 백업 및 복원(메모, 연차/휴가, 비민감 설정)
    3. 오프라인 모드에서 오늘의 근무 외 화면의 상세 다이아 조회 불가 문제
    4. 배터리 최적화 제외 설정 시 CrewMate 앱 직통 원터치 허용 다이얼로그 연동
    5. '적합성미완료' 상태여도 실제 출무 시각 확인 시 출무 5분 전 알람 자동 해제
    6. 자정 자동 캡쳐 날짜 탐색 결함 해결 및 Compose UI 양식 일치화
    7. 캡처 모드에서 사업시간/거리 요약 표 배제 (순수 타임라인 전용 캡처)
* **진행 내용**:
  1. **오프라인 자동 전환 복귀 지연 및 Skip (`AutoOfflineManager.kt`, `AutoOfflineReceiver.kt`, `SettingsScreen.kt`, `TodayWorkScreen.kt`)**:
     * 정리/입고정리 시작 후 온라인 복귀 지연 시간(`0분`, `5분`, `10분(권장)`, `15분`, `20분`) 선택 UI 및 계산 로직 추가.
     * 열차/입고 지연 상황을 위해 온라인 복귀 알람을 취소하고 오프라인을 유지하는 `[온라인 복귀 Skip]` 기능 신설.
     * `SettingsScreen` 및 `TodayWorkScreen` 상단바에 오프라인 상태일 때 원터치 `[복귀 Skip]` 버튼 제공.
  2. **JSON 데이터 백업 및 복원 엔진 (`DataBackupManager.kt`, `MemoDao.kt`, `LeaveDao.kt`, `SettingsScreen.kt`)**:
     * Room DB 메모(`memos`), 연차/이월연차(`annual_leaves`, `carried_over_leaves`), SharedPreferences 비민감 설정을 포함하는 JSON 백업 생성 및 복원 엔진 구축.
     * 암호, 키스토어, 세션 토큰 등 보안 정보는 철저히 배제(Zero-Leak).
  3. **전 화면 오프라인 상세 다이아 캐시 연동 (`AppNavigation.kt`, `DiaDetailRepository.kt`)**:
     * `AppNavigation`에서 원시 네트워크 XHR 데이터소스를 직접 주입하던 결함을 수정하고, Room DB 캐시를 활용하는 공용 `DiaDetailRepository`를 주입하여 오프라인 모드에서도 캐시된 다이아를 안정적으로 조회 가능.
  4. **배터리 최적화 원터치 직통 다이얼로그 (`AndroidManifest.xml`, `WorkAlarmSettingsScreen.kt`)**:
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한 추가 및 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 패키지 Uri 직접 호출로 CrewMate 전용 팝업 직통 유도.
  5. **출무 알람 해제 로직 보완 (`AttendanceStatus.kt`, `WorkAlarmRuleEvaluator.kt`, `WorkAlarmReceiver.kt`)**:
     * 실제 출무 시각(`actlTm`, `tnsmTm` 등) 확인 시 출무 상태 코드와 무관하게 출무 5분 전 알람을 100% 자동 취소.
  6. **자정 자동 캡쳐 날짜 버그 수정 및 디자인 일치화 (`MidnightCaptureReceiver.kt`, `DiaDetailDao.kt`)**:
     * `|| true` 폴백 버그를 제거하고 당일 월별 근무표(`work_schedules`)를 먼저 조회하여 당일 배정 다이아를 정확히 캡처.
     * Compose `DiaCaptureLayout`과 1:1로 일치하는 클린 문서 헤더, 캡슐 알약, 화이트 카드, 테두리, 세로 타임라인 트랙/도트로 캔버스 렌더러 개편.
  7. **캡처 뷰 순수 타임라인 표출 (`DiaDetailCard.kt`)**:
     * `isCaptureMode == true`일 때 `사업시간/거리 요약` 테이블을 완전히 숨겨 타임라인 구간만 깔끔하게 저장.
  8. **빌드 검증**: `testDebugUnitTest` 100% 통과, `assembleDebug` 및 `assembleRelease` 0 에러 완수.

---

### [STEP 37] (2026-10-28) 알림 권한 허용 직통 연결 및 시스템 사전 준비 다이얼로그 고도화 (v1.0.10)
* **작업 배경**:
  - v1.0.9에서 배터리 최적화 설정을 CrewMate 전용 시스템 팝업(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)으로 바로 연결되도록 개선한 것과 마찬가지로, 알림 권한 허용 또한 애플리케이션 상세 정보 루트 화면으로 이동하여 사용자가 메뉴를 찾아 헤매지 않고 바로 허용할 수 있도록 직통 연결 요구 발생.
* **구현 및 개선 상세 내역**:
  1. **알림 권한 직통 연결 및 Android 13+ 1-탭 팝업 (WorkAlarmSettingsScreen.kt)**:
     - 
ememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())을 도입하여 Android 13+ 기기에서 앱 내 1-탭 시스템 팝업을 직접 트리거.
     - 설정 화면 이동 시에도 Settings.ACTION_APP_NOTIFICATION_SETTINGS와 Settings.EXTRA_APP_PACKAGE를 전달하여 CrewMate의 전용 알림 설정 토글창으로 직행.
  2. **마스터 알람 스위치 토글 시 권한 자동 유도 (WorkAlarmSettingsScreen.kt)**:
     - 마스터 스위치 활성화 시 알림 권한이 누락되어 있으면 백그라운드에서 바로 시스템 팝업을 띄우고, 다른 권한이 만족된 상태라면 원터치 승인 후 알람이 즉시 활성화되도록 개선.
  3. **알람 사전 준비 다이얼로그 개편 (WorkAlarmSettingsScreen.kt)**:
     - 알림 권한과 정확한 알람 권한 버튼을 각각 🔔 알림 권한 허용 (직통 연결)과 ⏰ 정확한 알람 권한 설정 (직통 연결)으로 분리.
  4. **알림 활성화 상태 검증 정밀화 (WorkAlarmSettingsScreen.kt, SettingsScreen.kt)**:
     - NotificationManagerCompat.from(context).areNotificationsEnabled() 및 POST_NOTIFICATIONS 런타임 권한을 교차 검증.
  5. **생명주기(ON_RESUME) 자동 감지 (WorkAlarmSettingsScreen.kt)**:
     - LocalLifecycleOwner 및 LifecycleEventObserver를 연동하여 사용자가 외부 설정 화면에서 권한을 켜고 돌아오면 즉시 재검증 및 마스터 알람 자동 활성화, 다이얼로그 자동 닫기 처리.
  6. **빌드 검증**: 	estDebugUnitTest 100% 통과, ssembleDebug 및 ssembleRelease 0 에러 완수.

---

### STEP 38: 설정 화면(SettingsScreen) 4대 핵심 기능 원상 복구 및 시스템 완비
* **배경 및 원인 규명**:
  * Windows 인코딩 정규화 과정에서 실행된 `git checkout`으로 인해 `SettingsScreen.kt`가 v1.0.1 구버전으로 롤백되어 최근 추가된 4대 핵심 기능 카드가 일시 소실됨.
  * 백엔드 엔진(`AutoOfflineManager`, `WorkPatternSettings`, `DataBackupManager`, `MidnightCaptureReceiver`)은 워킹 트리에 온전히 보존되어 있었으며, 에이전트 상세 로그로부터 소실된 UI 코드 및 상태 변수를 100% 무결점으로 추출 복구함.
* **진행 내용 (`SettingsScreen.kt`, `DiaCaptureHelper.kt`, `AppDateProvider.kt`)**:
  1. **설정 화면 4대 카드 100% 복구**:
     - `오늘의 근무 자동 캡쳐 (자정)` 및 2종 캡처 옵션(`타임라인 요약` / `타임라인 전체`) 카드
     - `운전 시간 자동 오프라인 전환` 스위치, QA 관리 패널, 0분~20분 지연 칩, `온라인 복귀 Skip` 버튼
     - `미배정 달 순환 패턴 표시 (근무패턴 만들기)` 스위치, 기준일자 선택, 4대 뱃지 팔레트, 순환 패턴 목록
     - `💾 데이터 백업 및 복원` (`[💾 백업 내보내기]`, `[📂 복원 가져오기]`)
  2. **구버전 손상 문자열 클린 복구**:
     - 캘린더 연동, 테마 설정, 감시 숨기기, 앱 정보, 가상 날짜 디버그 다이얼로그의 한글 문자열을 100% 정상 한글로 정규화.
  3. **컴파일 및 타입 정밀화**:
     - `DiaCaptureOption.fromName(name: String?)` 및 `AppDateProvider.setOverrideDate(date: LocalDate?)` 신설.
     - `FlowRow` 레이아웃용 `@OptIn(ExperimentalLayoutApi::class)` 명시.
  4. **빌드 검증**:
     - `compileDebugKotlin` 0 에러, `testDebugUnitTest` 100% 통과, `assembleDebug` 및 `assembleRelease` 0 에러 완수.

---

### STEP 39: 세부 UI/UX 디자인, 이모지 및 문구 정리 6대 개선 완비
* **배경 및 목적**:
  * 실무 운용 관점에서 승무원 사용자의 직관성과 화면 공간 효율성을 극대화하기 위해 다이아 캡처 이모지, 복귀 Skip 버튼 위치, 연차 수정 잠금, 순환 패턴 뱃지, 오프라인 설정의 세부 디자인 및 안내 문구를 정밀 정돈함.
* **진행 내용**:
  1. **다이아 캡처 버튼 이모지화 (`TodayWorkScreen`, `ScheduleScreen`, `DiaLookupScreen`, `DiaCaptureHelper`)**:
     - 캡처 버튼의 텍스트("캡처")를 제거하고 `✂️` 단독 아이콘 버튼으로 컴팩트화. 다이얼로그 타이틀도 `✂️ 스크린샷 저장 범위 선택`으로 통일.
  2. **오프라인 모드 복귀 Skip 버튼 최상단바 단일화 (`AppNavigation`, `TodayWorkScreen`, `SettingsScreen`)**:
     - `CrewMateTopBar`의 `[OFFLINE]` 뱃지 바로 오른쪽에 `[복귀 Skip]` / `[복귀 활성]` 버튼을 배치.
     - `TodayWorkScreen` 및 `SettingsScreen`의 중복 버튼을 제거하여 상단바로 완전히 일원화.
  3. **나의 연차 관리 올해 총 연차 [수정]/[저장] 상태 제어 (`NonOpStatusScreen`)**:
     - 저장 완료 시 TextField를 읽기전용으로 잠그고 버튼 문구를 `[수정]`으로 전환. `[수정]` 클릭 시 다시 활성화되어 `[저장]`으로 토글.
  4. **순환 패턴 뱃지 번호 제거 (`SettingsScreen`)**:
     - `[1.주간][2.주간]` 대신 `[주간][주간][야간]` 등 순수 뱃지만 표출.
  5. **오늘의 근무 자동 캡쳐 문구 간결화 (`SettingsScreen`)**:
     - `오늘의 근무 자동 캡쳐 (자정)` -> `오늘의 근무 자동 캡쳐`.
  6. **운전 시간 자동 오프라인 전환 설정 정돈 (`SettingsScreen`, `AutoOfflineManager`, `AutoOfflineReceiver`)**:
     - "1순위 업무"를 "주요 업무"로 통일.
     - "(QA 관리)" 삭제 및 `전환 시각 기록` 카드를 기본 접힘 상태로 개편.
     - 온라인 복귀 시점에서 `(권장)` 문구 삭제 (`10분`).
     - 예정 스케줄을 줄바꿈하여 아래에 배치하고 `1구간, 2구간` -> `1. 2.` 표기로 개선.
  7. **온라인 복귀 시점 칩 가운데 정렬 및 캡처 버튼 컴팩트화 (`SettingsScreen`, `TodayWorkScreen`, `ScheduleScreen`, `DiaLookupScreen`)**:
     - 복귀 시점 `FilterChip` 내부 라벨 `TextAlign.Center` 적용으로 가운데 정렬 완료.
     - 캡처 버튼의 M3 최소 가로폭(58dp) 제한을 해소하고 정사각 `size(28.dp)` 아이콘 버튼으로 교체하여 여백 최적화.
  7. **빌드 검증**:
     - `compileDebugKotlin` 0 에러, `testDebugUnitTest` 100% 통과, `assembleDebug` 및 `assembleRelease` 0 에러 완수.

### STEP 40: SubwayNowBot 텔레그램 열차 추적 자동 연동 및 다이아 기반 관제 시스템 구축
* **작업 배경 및 목적**:
  * 승무원이 당일 승무할 열차를 위해 출발 예정 시각 전부터 목표역 진입 시까지 실시간 현위치를 텔레그램 메신저로 자동 안내해 주는 **SubwayNowBot** 연동 자동화 시스템을 구축.
  * 예약 전송 시각($T_{send}$)과 추적 시작 시각($T_{start}$) 분리, 실수로 전체가 해제되는 사고를 방지하기 위한 개별 열차 취소(`/stop [열번]`) 지원, 6자리 PIN 상호 교차 검증(Cross-Checking), 다이아 4대 필터링 규칙(1순위 업무, Sandwiched 감시 연속업무 제외, K-prefix 정규화, 선행 감시 출발시각 간주)을 구현.
* **주요 구현 내용**:
  1. **SubwayNowBot REST API 브릿지 및 개별 열차 취소 (`api_bridge.py`, `bot.py`, `scheduler.py`)**:
     - Python `aiohttp.web` 기반 비동기 경량 HTTP 서버 구축 (`129.225.156.25:8088`).
     - `/stop [열번]` 및 `중지 [열번]` 명령어를 통한 개별 열차 지정 취소 메커니즘 구축.
     - `POST /api/v1/auth/request`, `GET /api/v1/auth/status`, `POST /api/v1/track`, `POST /api/v1/stop`, `GET /api/v1/ping` 엔드포인트 완비.
  2. **6자리 PIN 기반 상호 인증 (Cross-Checking)**:
     - CrewMate 설정에서 Chat ID 입력 후 `[인증 시작]` 터치 시 6자리 일회용 난수 PIN 발급.
     - 텔레그램 `@SubwayNowBot` 대화창에 5분 내 PIN 전송 시 검증 및 SQLite DB(`crew_auth`) 영속화.
     - 3초 주기 자동 폴링을 통한 즉각적인 녹색 `[인증 완료]` 뱃지 전환.
  3. **오늘의 다이아 4대 필터링 엔진 (`SubwayNowRuleEvaluator.kt`)**:
     - 1순위 업무(운전/승무/출고준비/입환) 열차번호, 시작역(`dptStnNm`), 출발시각 추출.
     - `1순위 -> 감시 -> 1순위` 패턴 시 두 번째 1순위 업무(Sandwiched Duty) 제외.
     - 열차번호 숫자 추출 후 무조건 `K` Prefix로 정규화 (`1601` -> `K1601`, `S507` -> `K507`).
     - 1순위 직전에 감시가 있는 경우(`감시 -> 1순위`), 감시의 시작시각(`depTm`)을 기준시각으로 간주.
  4. **전송 설정 및 이력 관리 (`SubwayNowSettings.kt`, `SubwayNowBotSettingsCard.kt`)**:
     - 예약 전송 시각 $T_{send}$ (출근 시 일괄, 60분 전, 45분 전, 30분 전) 및 추적 시작 시각 $T_{start}$ (30분 전, 20분 전, 15분 전, 10분 전) 독립 설정.
     - 알림 발송 주기 (1~3개 역), 서버 Ping 테스트, 전송 이력 다이얼로그(원터치 개별 추적 중지) 제공.
  5. **오늘의 근무 화면 연동 및 자동 스케줄러 (`TodayWorkScreen.kt`, `SubwayNowManager.kt`, `SubwayNowReceiver.kt`)**:
     - 다이아 로드 시 AlarmManager를 통해 각 열차의 전송 시각에 자동 발송 스케줄링.
     - `TodayWorkScreen`에 텔레그램 열차 추적 뱃지 및 열차별 `[🚀 즉시 전송]` / `[🛑 추적 취소]` 제어 다이얼로그 완비.
  6. **Room DB 엔티티 및 네트워크 보안 (`BotCommandHistoryEntity.kt`, `CrewMateDatabase.kt`, `network_security_config.xml`)**:
     - `bot_command_histories` 테이블 신설(Room DB v8).
     - `network_security_config.xml`: SubwayNowBot 서버 IP(`129.225.156.25`) HTTP 통신 전용 허용 및 Xcrew 도메인 100% 엄격한 HTTPS 유지.
* **검증 결과**:
  - `SubwayNowRuleEvaluatorTest` 4대 필터링 규칙 단위 테스트 100% 통과.
  - `test_api_bridge.py` Python REST API 서버 단위 테스트 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

---

## 📊 4부: 개발 통계 요약 (Project Statistics)

### 1. 화면(Screen) 및 주요 UI 컴포넌트 목록 (총 8개 화면 + 2개 알람 액티비티)
| 구분 | 화면/컴포넌트 명 | 설명 및 주요 역할 |
| :--- | :--- | :--- |
| **화면 1** | `LoginScreen.kt` | 네이티브 로그인 화면 (사번/비밀번호 입력, 비밀번호 보기 토글 및 자동 로그인 설정) |
| **화면 2** | `ScheduleScreen.kt` | 월간 근무표 달력 화면 (M3 곡률 바텀시트, 다이아, 연차, 휴가, 날짜별 메모) |
| **화면 3** | `TodayWorkScreen.kt` | 오늘의 근무 화면 (당일 타임라인, 예정 알람 팝업 칩, 출무 현황, 텔레그램 열차 추적 제어) |
| **화면 4** | `WorkAlarmSettingsScreen.kt` | 정밀 알람 전용 대시보드 (마스터 스위치, 테스트 카드, 6대 세부 규칙) |
| **화면 5** | `TrainCrewLookupV2Screen.kt` | 열차 승무원 조회 V2 (전수조사 DB 기반 1초 동료 검색 및 출퇴근 시각) |
| **화면 6** | `DiaLookupScreen.kt` | 승무 다이아 상세 조회 화면 (다이아 번호 기반 타임라인 및 분석) |
| **화면 7** | `CrewWorkStatusScreen.kt` | 소속 승무원 전체 근무 현황 조회 화면 |
| **화면 8** | `NonOpStatusScreen.kt` | 비실동 현황 및 개인 휴가 종합 조회 화면 |
| **설정 보조**| `SubwayNowBotSettingsCard.kt` | SubwayNowBot 텔레그램 연동, PIN 상호 인증 및 시각/주기 설정 카드 |
| **알람 팝업**| `PopupAlarmActivity.kt` | 알람 발동 시 뜨는 대화상자형 팝업 액티비티 |
| **전체알람**| `FullScreenAlarmActivity.kt` | 화면이 꺼져있을 때 켜지는 잠금화면 전체화면 알람 액티비티 |

### 2. 연동된 Xcrew 사내 API 목록 (총 7개 핵심 API)
1. `searchExtrRltmCrewWrkPstt.do` - 월간 실시간 승무원 근무 현황 조회 API
2. `searchExtrCrewDia.do` - 승무 다이아 상세 구간 타임라인 조회 API
3. `searchExtrCrewWrkPstt.do` - 열차별 승무원 다이아 배정 조회 API
4. `searchCrewWorkStatus.do` - 소속 승무원 일자별 근무 현황 조회 API
5. `searchNonActiveStatus.do` - 사내 비실동 현황 조회 API
6. `searchPersonalLeave.do` - 개인 휴가 및 연차 현황 조회 API
7. `fetchAttendanceStatus` - 당일 예정/실제 출무 현황 데이터 API

### 3. 로컬 데이터베이스 테이블 요약 (Room DB 9개 테이블)
1. `work_schedules` - 월간 근무표 저장 테이블
2. `dia_details` - 다이아 상세 타임라인 JSON 캐시 테이블
3. `crew_assignments` - 소속 승무원 근무 배정 캐시 테이블
4. `attendance_statuses` - 오프라인 모드용 출무 현황 스냅샷 테이블
5. `dia_census` - 승무원 검색 V2용 월간 전수조사 인덱싱 테이블
6. `alarm_histories` - 알람 발동 작동 기록 저장 테이블 (최근 14일)
7. `memos` - 날짜별 개인 메모 저장 테이블
8. `leaves` - 개인 연차 및 휴가 저장 테이블
9. `bot_command_histories` - SubwayNowBot 텔레그램 명령어 전송 이력 및 상태 관리 테이블 (v8 신설)


### STEP 41: 실험실(CrewMate Labs) 프레임워크 기반 구축 및 키스토어 보안 정비 (2026-11-14)
1. **보안 및 브랜치 정비**:
   - `crewmate-release-key.jks`를 Git 인덱스에서 분리(`git rm --cached`)하고 `.gitignore`에 `*.jks`, `*.keystore`, `.env` 규칙을 추가하여 키스토어 유출 위험을 차단함.
2. **실험실(Labs) 아키텍처 기반 구축 (`labs/` 패키지)**:
   - `LabFeature.kt`: Android와 CrewMate-Alpha가 동일한 `id` 키를 공유하는 중앙 기능 레지스트리 신설 (`route_diagram`, `pay_statement`, `colleague_compare`, `handover_crew`, `dia_rating`).
   - `LabsManager.kt`: `crewmate_prefs` 기반 실시간 관찰(`rememberLabEnabled`, `rememberAllLabStates`) 및 `runLabSafely` 안전 실행 래퍼 구현.
   - `LabSafeContainer.kt`: 실험실 UI 예외/오류 시 앱 본체 영향을 차단하고 원클릭 비활성화를 제공하는 폴백 컨테이너 구현.
   - `LabsSettingsScreen.kt`, `SettingsScreen.kt`, `AppNavigation.kt`: 설정 진입 카드, 전용 실험실 관리 화면(`Routes.Labs`), 독립 화면형 실험실의 햄버거 메뉴 동적 노출 구조 연동.
3. **클라우드 CI/CD 및 Private 소스 저장소 연동 (`.github/workflows/android-ci.yml`, `app/build.gradle.kts`)**:
   - `0JYOON/CrewMate-Source`를 `source` 원격 저장소로 연결하고, 푸시/PR마다 `test assembleDebug` 및 Debug APK 아티팩트를 생성하는 GitHub Actions 워크플로 추가.
   - `app/build.gradle.kts` 서명 설정이 환경 변수(`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)를 우선 참조하도록 개선해 GitHub Secrets 기반 Release APK 빌드 지원.


### STEP 42: 실험실 1호 — 승무 행로도 시각화 뷰 및 노선 마스터 통합 관리 (2026-11-14)
1. **노선/화물역/기지 마스터 통합 관리 (`RailwayMasterModels.kt`, `RailwayMasterRepository.kt`)**:
   - 내장 `assets/railwayMasterData.json` + GitHub(`0JYOON/CrewMate`)·Web(`crewmate-alpha.pages.dev`) 버전 크로스체크 동기화 + 로컬 커스텀 오버라이드 3계층 구조 구축.
2. **승무 행로도 시각화 뷰 (`RouteDiagramBuilder.kt`, `RouteDiagramView.kt`, `DiaDetailCard.kt`)**:
   - 피드백 기반 5대 개선 적용(시각 강조, 감시 파란 얇은 선, 정리·승계·준비·휴게·입고정리 단일 구간 통합, 겹침 방지 간격, 하단 접이식 요약 기본 접힘).


---

## STEP 43: 실험실 1호(승무 행로도) 시각적 디테일 6종 개선 및 웹/안드로이드 동기화 (2026-11-14)
- **작업 내용**:
  1. `RouteDiagramBuilder.kt`: `RouteStep.Dwell`에 `isBlueCore` 플래그를 추가하여 `감시`, `출고준비`, `입환`을 파란색 실선 핵심 작업으로 분류하고, `출고준비` 표기를 `"출고"`로, `편승` 열차 표기를 `"편) <열번>"` 형식으로 정규화.
  2. `RouteDiagramView.kt`: 좌측 텍스트 잘림 방지를 위해 `sidePadDp = 28.dp` 대칭 여백을 적용하고, 열차번호 뱃지 배경을 제거한 뒤 시각과 동일한 `12.sp` `ExtraBold` 폰트로 수평선 상단에 충분한 간격을 두고 배치.
  3. `RouteDiagramBuilderTest.kt`: `출고`, `편)`, `isBlueCore`(`출고준비`, `입환`, `감시` vs `정리·승계·준비·휴게·입고정리`) 검증 단위 테스트 추가.
  4. `CrewMate-Alpha` (`app-runtime.js`, `dist/app-runtime.js`)에 동일 로직 반영 후 `origin main` 푸시(`4fefc64`) 완료.
- **검증**: `./gradlew.bat test assembleDebug` 0 오류 통과 (`BUILD SUCCESSFUL`).
