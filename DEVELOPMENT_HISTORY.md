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

---

## 📊 4부: 개발 통계 요약 (Project Statistics)

### 1. 화면(Screen) 및 주요 UI 컴포넌트 목록 (총 8개 화면 + 2개 알람 액티비티)
| 구분 | 화면/컴포넌트 명 | 설명 및 주요 역할 |
| :--- | :--- | :--- |
| **화면 1** | `LoginScreen.kt` | 네이티브 로그인 화면 (사번/비밀번호 입력, 비밀번호 보기 토글 및 자동 로그인 설정) |
| **화면 2** | `ScheduleScreen.kt` | 월간 근무표 달력 화면 (M3 곡률 바텀시트, 다이아, 연차, 휴가, 날짜별 메모) |
| **화면 3** | `TodayWorkScreen.kt` | 오늘의 근무 화면 (당일 타임라인, 예정 알람 팝업 칩, 출무 현황, 가상 날짜 칩) |
| **화면 4** | `WorkAlarmSettingsScreen.kt` | 정밀 알람 전용 대시보드 (마스터 스위치, 테스트 카드, 6대 세부 규칙) |
| **화면 5** | `TrainCrewLookupV2Screen.kt` | 열차 승무원 조회 V2 (전수조사 DB 기반 1초 동료 검색 및 출퇴근 시각) |
| **화면 6** | `DiaLookupScreen.kt` | 승무 다이아 상세 조회 화면 (다이아 번호 기반 타임라인 및 분석) |
| **화면 7** | `CrewWorkStatusScreen.kt` | 소속 승무원 전체 근무 현황 조회 화면 |
| **화면 8** | `NonOpStatusScreen.kt` | 비실동 현황 및 개인 휴가 종합 조회 화면 |
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

### 3. 로컬 데이터베이스 테이블 요약 (Room DB 8개 테이블)
1. `work_schedules` - 월간 근무표 저장 테이블
2. `dia_details` - 다이아 상세 타임라인 JSON 캐시 테이블
3. `crew_assignments` - 소속 승무원 근무 배정 캐시 테이블
4. `attendance_statuses` - 오프라인 모드용 출무 현황 스냅샷 테이블
5. `dia_census` - 승무원 검색 V2용 월간 전수조사 인덱싱 테이블
6. `alarm_histories` - 알람 발동 작동 기록 저장 테이블 (최근 14일)
7. `memos` - 날짜별 개인 메모 저장 테이블
8. `leaves` - 개인 연차 및 휴가 저장 테이블
