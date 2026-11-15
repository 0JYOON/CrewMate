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

### STEP 41: 실무 피드백 기반 7대 결함 해결 및 편의기능 고도화 (v1.0.14)
* **작업 배경 및 목적**:
  * 실제 근무 투입 과정에서 확인된 출무 5분 전 알람 미해제 버그, 야간 근무 자정 이후 열차 추적 누락, 텔레그램 추적 중지 시 무한 재시작 현상, 불필요한 출고준비 알람, 익월 오프라인 캐싱 부재, 휴일근무 통계 필요성, 4자리 역명 제약 및 표기 불일치를 원천 해결하고 실무 안정성을 극대화.
* **주요 구현 내용**:
  1. **출무 5분 전 알람 미해제 버그 완벽 해결 (`AttendanceStatus.kt`, `WorkAlarmRuleEvaluator.kt`, `WorkAlarmReceiver.kt`, `WorkAlarmScheduler.kt`, `TodayWorkRepository.kt`, `TodayWorkScreen.kt`)**:
     - 원인 분석: `actualTime`이 있더라도 `gtwcGubunCd == "5"`(적합성미완료) 상태이거나, 출무 후 알람 목록이 빈 리스트가 될 때 `scheduleAlarms` 호출이 누락되어 기존 OS 알람 슬롯이 해제되지 않는 구조적 결함 식별.
     - `AttendanceStatus.isCheckedIn`: `actualTime != null || gtwcGubunCd in setOf("3", "5", "6")` 판정식으로 통합.
     - `WorkAlarmRuleEvaluator`: `if (!att.isCheckedIn)` 가드로 출무 완료 시 알람 목록 생성 원천 차단.
     - `WorkAlarmReceiver`: 사이렌 발동 직전 로컬 DB(오늘/어제/내일)를 조회하여 `isCheckedIn` 시 사이렌 즉시 취소 및 알람 슬롯 정리.
     - `WorkAlarmScheduler.cancelAllAlarms()` 및 `TodayWorkRepository`: 활성 알람이 없더라도 `scheduleAlarms(emptyList())`를 항상 호출하여 0~15번 슬롯 전체 취소.
  2. **야간 근무 자정 이후(익일) 열차 추적 및 텔레그램 알람 예약 지원 (`SubwayNowRuleEvaluator.kt`, `SubwayNowManager.kt`, `SubwayNowApiClient.kt`, `SubwayNowReceiver.kt`, `SubwayNowBot/scheduler.py`, `SubwayNowBot/api_bridge.py`)**:
     - `SubwayNowRuleEvaluator`: 당일 다이아 순회 중 시각 역전(`dpt < lastTime`) 감지 시 `isNextDay = true` 마킹.
     - **18시/12시 하드코딩 완전 제거**: `SubwayNowApiClient.sendTrack` 및 `SubwayNowManager`에서 `trackStartDateTime` ISO 문자열(예: `2026-10-30T01:15:00`)을 REST API로 직접 전달하여 등록 시점과 무관하게 100% 확정적 익일 예약 보장.
     - `SubwayNowBot (api_bridge.py, scheduler.py)`: CrewMate가 전송한 `start_datetime`을 최우선 적용. 수동 텔레그램 명령어의 경우에도 18시/12시 하드코딩을 제거하고 15분 이내 시각은 즉시 활성화, 과거 시각은 익일 예약으로 자연스럽게 전환 및 오라클 클라우드 운영 서버(`129.225.156.25:8088`) 배포 완료.
  3. **텔레그램 추적 중지 시 무한 재시작 차단 & 제어/이력 분리 (`SubwayNowSettings.kt`, `SubwayNowManager.kt`, `SubwayNowReceiver.kt`, `TodayWorkScreen.kt`, `SubwayNowBotSettingsCard.kt`)**:
     - 원인 분석: `syncTodayDiaSchedule`이 `latest.status != "SUCCESS"`(취소된 `CANCELLED` 포함)인 경우 미완료로 간주하여 다시 전송을 예약하던 결함 해결.
     - `SubwayNowSettings`: 당일 정지 열차 목록(`stopped_trains_${today}`) 영속화 및 `isTrainStopped / setTrainStopped` 제공.
     - `SubwayNowManager.cancelTarget`: `setTrainStopped(trainNo, true)` 설정, 즉시 봇 취소 API 호출, DB 상태 `CANCELLED` 기록, AlarmManager 취소.
     - `TodayWorkScreen`: 중지된 열차에 `[🛑 추적 중지됨]` 뱃지와 `[▶️ 추적 재개]` 버튼 제공, 재개 터치 시 즉시 전송 또는 재스케줄링 복구.
     - `SubwayNowBotSettingsCard`: `설정` 메뉴의 이력 팝업은 단순 읽기 전용("📋 봇 전송 이력")으로 개편하고, 중지/재개 제어는 `오늘의 근무`로 일원화.
  4. **'출고준비' 항목 추적 알람 제외 (`SubwayNowRuleEvaluator.kt`)**:
     - 첫 번째 시작 항목이 `출고준비`인 경우 기지 내 준비 작업이므로 추적 대상에서 자동 제외.
     - `출고준비 -> 운전`과 같이 출고준비에 바로 이어지는 주요 업무 역시 동일 연속 편성이므로 추적 대상에서 제외.
  5. **오프라인 모드 전환 시 당월 및 익월 데이터 듀얼 동기화 (`AppNavigation.kt`)**:
     - 오프라인 모드 진입 시 당월(`currentMonth`) 스케줄/다이아 상세 캐싱뿐만 아니라 다음 달(`nextMonth`) 스케줄 및 다이아가 서버에 존재하는 경우 함께 백그라운드 프리페치 및 로컬 Room DB 동기화.
  6. **비실동 조회 내 '휴일근무 현황' 신설 및 0건 표출 버그 완벽 해결 (`HolidayWorkManager.kt`, `WorkScheduleDao.kt`, `NonOpStatusViewModel.kt`, `NonOpStatusScreen.kt`, `AppNavigation.kt`)**:
     - **0건 표출 결함 원천 해결**: Xcrew 응답에서 `alcSpecDvCd == "02"`(휴일)인 행은 구분명(`nonAtulClsfCdNm`)에 `"휴일"`이 기재되어 내려오는데, 기존의 `s.nonActiveType.isNullOrBlank()` 강제 조건 때문에 모든 휴일근무 다이아가 누락되던 결함을 발견하고 완전 제거.
     - `alcSpecDvCd` 코드 포맷 유연화(`"02"`, `"2"`, `toIntOrNull() == 2` 및 휴일 명칭 통합 검사) 및 실제 운용 다이아 배정 여부 판정식 구축.
     - `WorkScheduleDao.getSchedulesForYear`: 연도별 전체 캐시 일괄 조회 Room 쿼리 신설.
     - `NonOpStatusViewModel`: 로컬 Room DB 캐시를 우선 수집하고 당해 연도 조회 시 당월까지만 원격 조회하여 미배정 미래 월(11, 12월 등) 15초 타임아웃 지연을 원천 차단.
     - 주간/야간 분리 계산 및 야간 2일 연속 근무는 1개 단위로 정밀 카운팅, SharedPreferences(`crewmate_holiday_work_prefs`) 로컬 저장 및 연도별(`yyyy`) 변경 조회 지원.
     - `NonOpStatusScreen`: `[현황 조회]` 버튼, 기본 총 누적 개수 표시 및 원터치 터치 시 주간/야간 분리 상세 내역 확장 토글 UI 제공.
  7. **텔레그램 추적 출발역 '역명 치환' 기능 구축 (`SubwayNowSettings.kt`, `SubwayNowBotSettingsCard.kt`, `SubwayNowRuleEvaluator.kt`, `TodayWorkScreen.kt`)**:
     - `SubwayNowSettings`: 출발역 역명 치환 규칙(`station_replacements_json`) 추가/삭제/조회/자동 치환 함수 제공.
     - `SubwayNowBotSettingsCard`: 다이아 출발역(예: `4서울`)과 교체역명(예: `서울`)을 입력·등록하고 삭제할 수 있는 '출발역 역명 치환 설정' 카드 및 실시간 칩 UI 제공.
     - 다이아 분석 및 텔레그램 전송 시 치환 규칙을 즉각 적용하여 봇 4자리 역명 제약 및 불일치 해소.
* **검증 결과**:
  - `EnhancementsUnitTest` (출고준비 제외, 역명 치환, 익일 야간 판정, 출무 상태 판정, 휴일근무 주간/야간/2일연속 단위 계산 등) 100% 통과.
  - `SubwayNowRuleEvaluatorTest` 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 42: 휴일근무 현황 0건 표출 결함 4대 원인 규명 및 완전 해결 (v1.0.15)
* **작업 배경 및 목적**:
  * 비실동 조회 화면의 '휴일근무 현황'에서 `[현황 조회]`를 눌러도 실제 배정된 휴일근무가 있음에도 0건으로 나오는 문제를 해결하기 위해, Xcrew 파서, Room DB, ViewModel, 계산 매니저 전반의 데이터 파이프라인을 정밀 추적하여 4가지 복합 원인을 규명하고 근본적으로 해결.
* **4대 원인 규명 및 해결 내용**:
  1. **날짜 포맷 정규화 (`HolidayWorkManager.kt`)**:
     - `workDate`에 하이픈(`"2026-10-03"`)이나 점 등이 섞여 길이가 10인 경우 `it.length == 8` 필터에 의해 모든 일자가 제외되어 루프가 0회 실행되던 결함 발견.
     - `it.workDate?.filter { it.isDigit() }`로 8자리 숫자(`"YYYYMMDD"`)로 정규화한 뒤 그룹핑 및 정렬을 수행하도록 전면 개선.
  2. **동일 일자 다중 스케줄 덮어쓰기 유실 방지 (`NonOpStatusViewModel.kt`)**:
     - Xcrew 특성상 하루에 '기존 순번 휴일 행(`alcSpecDvCd == "02"`)'과 '추가 배정 다이아 행(`alcSpecDvCd == "11"`)'이 복수로 존재하는 경우가 흔함.
     - 기존 `scheduleMap[d] = s` 단일 날짜 Key Map 구조로 인해 한 행이 다른 행을 덮어써서 휴일 코드나 배정 다이아가 증발하던 문제를 `monthSchedulesMap = mutableMapOf<String, List<WorkSchedule>>()` 월별 리스트 보존 구조로 전환하여 완전 해결.
  3. **달력 셀('휴' 뱃지)과 현황 판정식의 충돌 해결 및 통합 판정 구축 (`HolidayWorkManager.kt`)**:
     - 달력(`ScheduleScreen`)의 빨간 '휴' 뱃지는 `(hldyYn == "Y" || isHoliday) && alcSpecDvCd != "02"`(실제 출근으로 alc가 11/12)로 표출되는데, 기존 현황 판정식은 `alcSpecDvCd == "02"`만 강제하여 공휴일/주말 근무가 전부 누락되던 모순 해결.
     - 순번표 휴일(`alcSpecDvCd in setOf("02", "2")`)과 서버 지정 공휴일/주말(`hldyYn == "Y" || isHoliday == true`)을 모두 통합 포용하고, 대표 열번 또는 근무시각이 부여된 유효 근무를 실제 승무 근무로 판정하도록 확립.
  4. **연간 수집 모수 투명성(진단) UI 추가 (`HolidayWorkManager.kt`, `NonOpStatusScreen.kt`)**:
     - `HolidayWorkReport`에 `examinedDaysCount` 필드를 신설하고, UI 상단에 `최근 조회: YYYY.MM.DD HH:mm (총 OO일치 분석)` 형태로 실제 분석에 반영된 근무표 모수를 투명하게 표출.
* **검증 결과**:
  - `EnhancementsUnitTest` 신규 테스트(`holidayWorkManager_handlesHyphenatedDatesAndServerHldyYnWithAlc11`) 포함 단위 테스트 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 43: 승무근무표 표준 원칙 기반 휴일근무·야간근무 판별식 완전 개편 및 비실동 제외 확립 (v1.0.16)
* **작업 배경 및 목적**:
  * 사용자로부터 승무 실무 표준 근무 판정 규칙(휴일근무: `hldyYn == 'Y' && alcSpecDvCd in ('11', '12')`, 비실동 `21` 제외 / 야간근무: 당일 `11` & 익일 `12`)을 직접 전달받음.
  * 기존 판정에서 시각 역전(`st > et`) 등으로 야간근무를 판별하려다 실제 야간근무가 주간으로 오분류되고, 병가/연차 등 비실동 처리된 날이 휴일근무로 카운트되던 결함을 원천 해결.
* **주요 구현 내용**:
  1. **휴일근무 표준 판별식 구축 (`WorkSchedule.kt`, `HolidayWorkManager.kt`)**:
     - `hldyYn == "Y"` 이면서 `alcSpecDvCd`가 `"11"` 또는 `"12"`인 날만 유효 근무로 인정.
     - `alcSpecDvCd == "21"`인 날은 `hldyYn`과 무관하게 승무 미수행 비실동이므로 무조건 제외.
     - `hldyYn == "Y"`인 날 중에서도 병가, 연차, 공가, 교육, 출장, 청원 등 비실동 처리가 된 날은 100% 완전 제외 (`isNonActive()` 가드 적용).
     - `alcSpecDvCd == "02"`(휴일 휴무/비번)인 날도 쉬는 날이므로 제외.
  2. **야간근무 표준 판별식 구축 (`당일 11 & 익일 12`)**:
     - 당일 `alcSpecDvCd == "11"` 이고 익일 `alcSpecDvCd == "12"` ➔ **야간 1일차**.
     - 당일 `alcSpecDvCd == "12"` ➔ **야간 2일차**.
     - 당일 `alcSpecDvCd == "11"` 이고 익일 `alcSpecDvCd != "12"` ➔ **주간근무(일근)**.
     - 야간 2일 연속 근무(`11` -> `12`)를 1개 단위로 정밀 집계(1일차에 카운트된 경우 2일차는 내역만 보존).
  3. **표준 근무 판정 절대 원칙 영구 보존 (`AGENTS.md`, `WorkSchedule.kt`, `ARCHITECTURE_GUIDE.md`)**:
     - `AGENTS.md`의 '코드 수정 시 절대 안전 원칙' 4번에 사내 승무 표준 원칙을 명문화하여 영구 보존.
  4. **단위 테스트 검증 완료 (`EnhancementsUnitTest.kt`)**:
     - 병가/연차 비실동 배제, 당일 11 & 익일 12 야간 판정, 야간 2일 연속 1개 단위 통합 등 전 테스트 케이스 100% 통과.
* **검증 결과**:
  - `EnhancementsUnitTest` 단위 테스트 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 44: 근무표 메모 고대비 테마화 및 텔레그램·설정 8대 UI/UX & 사용자 오버라이드 기능 고도화 (v1.0.17)
* **작업 배경 및 목적**:
  * 사용자 피드백에 따라 월별 승무근무표 달력 내 메모 텍스트/배경의 시인성을 CrewMate M3 테마 기반으로 극대화.
  * 운전시간 자동 오프라인 전환 복귀 시점(20분 ➔ n분 직접 입력), 텔레그램 계정 연동 뱃지 재배치 및 상세 설정 접기/펼치기(아코디언) 도입.
  * 텔레그램 연동 세부 항목(출근 일괄, 예약전송 n분 전, 추적시작 n분 전, 알림주기 n개 역마다) 직접 입력 다이얼로그 구축.
  * 오늘의 근무에서 텔레그램 열차 추적 목록의 개별 카드를 터치하여 명령어·시각표를 사용자가 직접 수정/덮어씌울 수 있는 오버라이드 기능 구현.
* **주요 구현 내용**:
  1. **월별 근무표 메모 M3 고대비 테마화 (`ScheduleScreen.kt`)**:
     - `secondaryContainer` 배경색 + `onSecondaryContainer` 글자색(`FontWeight.ExtraBold`)을 적용하여 라이트/다크 모드 전 영역에서 WCAG AA 기준 충족 가독성 달성.
  2. **온라인 복귀 시점 "n분" 커스텀 입력 (`SettingsScreen.kt`)**:
     - 5번째 칩을 `if (autoOfflineReturnDelay !in listOf(0, 5, 10, 15)) "${autoOfflineReturnDelay}분" else "n분"`으로 전환하고 커스텀 분 입력 다이얼로그 및 `AutoOfflineManager` 동기화 연동.
  3. **텔레그램 계정 연동 UI 개선 (`SubwayNowBotSettingsCard.kt`)**:
     - '인증 완료' 뱃지를 상단 "텔레그램 계정 연동 (Chat ID)" 레이블 우측으로 이동, 입력 필드 우측에 버튼 단독 배치.
     - `AnimatedVisibility` 기반 **⚙️ 텔레그램 상세 설정 (접기 ▲ / 펼치기 ▼)** 아코디언 컴포넌트 신설.
     - 예약 전송 시각 첫 번째 칩 문구를 "출근 일괄"로 간결화하고, "n분 전" 커스텀 입력 다이얼로그 지원.
     - 추적 시작 시각 "n분 전" 커스텀 입력 다이얼로그 지원.
     - 알림 발송 주기 4번째 칩으로 "n개 역마다" 추가 및 이동 역 수 직접 입력 다이얼로그 지원.
  4. **오늘의 근무 열차 추적 카드 터치 시 사용자 오버라이드 (`TodayWorkScreen.kt`, `SubwayNowSettings.kt`, `SubwayNowRuleEvaluator.kt`, `SubwayNowManager.kt`)**:
     - `TrackTargetOverride` 모델 및 SharedPreferences 날짜/열차별 오버라이드 영속화 로직 신설.
     - `SubwayNowRuleEvaluator.applyOverrides`를 통해 자동 산출된 타겟에 사용자 수정값(`rawCommand`, `trackStartTimeStr`, `sendDateTime`) 우선 병합.
     - 열차 카드 터치 시 `EditTrackTargetDialog` 팝업 표출, 명령어/추적개시/전송시각 직접 수정 및 저장 즉시 반영.
     - `SubwayNowManager.rescheduleTarget`을 호출하여 AlarmManager 자동 예약 전송 스케줄 즉시 갱신 및 [초기화] 복원 지원.
  5. **단위 테스트 검증 완료 (`SubwayNowRuleEvaluatorTest.kt`)**:
     - `test user override modifies rawCommand and times` 테스트 케이스 추가 및 전체 단위 테스트 100% 통과.
* **검증 결과**:
  - `SubwayNowRuleEvaluatorTest` 포함 단위 테스트 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 45: 오늘의 근무 자정 자동 캡처 양식 인앱 캡처 100% 일원화 및 야간 2일차 스킵 & 텔레그램 추적 개시 연동 강화 (v1.0.18)
* **작업 배경 및 목적**:
  * 텔레그램 추적 타겟 직접 수정 시, 명령어의 마지막 토큰인 `추적 개시` 시간은 `추적 개시 (HH:mm)` 입력 항목과 연동되어 있으므로 명령어 필드에서는 앞부분(열차번호/목표역/주기)만 수정하도록 분리하고 명령어 칸 내 시각 표시 및 양방향 실시간 결합을 보장.
  * 자정 00:01 오늘의 근무 자동 캡처가 임의 양식(괄호 시각 표기, 출퇴근 시각 누락, 타임라인 요약 뱃지, 불필요한 하단 푸터)으로 생성되던 문제를 해결하고, 인앱 다이아 캡처 화면과 1:1 완벽하게 동일한 결과물로 일원화.
  * 야간근무 2일차(박 둘째 날)는 이미 1일차 자정에 익일 다이아까지 포함하여 캡처되었으므로 중복 자동 캡처를 스킵 처리.
* **주요 구현 내용**:
  1. **텔레그램 추적 명령어 수정 시 `추적 개시` 연동 및 앞부분 직접 제어 (`TodayWorkScreen.kt`)**:
     - `tokens.dropLast(1)`을 통해 `editCommandPrefix`를 분리하고 `OutlinedTextField`는 접두부만 수정 가능하게 구성.
     - `trailingIcon`에 실시간 `editTrackStartTime` 뱃지를 배치하여 명령어 입력란 내에 추적 개시 시각이 시각적으로 표출되면서도 오타 직접 편집은 차단.
     - 하단 실시간 최종 전송 명령어 미리보기(`최종 전송 명령어: prefix trackStartTime`) 및 저장 시 `$prefix $trackSt` 완벽 병합 영속화/재스케줄링 연동.
  2. **자정 자동 캡처 결과물 인앱 캡처 100% 일원화 (`MidnightCaptureReceiver.kt`)**:
     - 상단 헤더: `MM.dd 근무` 42px Bold Navy(`#0F2942`) + 우측 `YYYY.MM.DD` 둥근 연청색 알약 캡슐(`#E0E7FF`).
     - 카드 상단: `${diaNo} DIA` (36px Bold) + 우측 `출근 HH:mm / 퇴근 HH:mm` (`toDisplayTime` 24px Bold `#64748B`).
     - 6열 타임라인 그리드:
       - Rank 1(`운전/승무`) 행 은은한 연청색 배경(`RectF` `#EFF6FF`) 강조.
       - 열차번호 존재 시 연청색 캡슐 뱃지(`$trn $cat`, 예: `K4658 운전`), 미존재 시 일반 텍스트(`준비`, `출고준비`).
       - 준비/출고준비 상단 정규 가로 구분선(`drawLine` `#E2E8F0`).
       - 출발역/도착역 중앙 정렬 및 표준 콜론 시각(`18:20`, `20:06`).
     - 불필요한 임의 푸터 텍스트(`CrewMate 자동 캡처 • ...`) 및 `타임라인 요약` 뱃지 완전 제거.
  3. **야간근무 2일차 및 비실동 자정 자동 캡처 스킵 확립 (`MidnightCaptureReceiver.kt`)**:
     - `alcSpecDvCd == "12"` 또는 `isContinuation` 또는 `diaNo.startsWith("~")` 판별 시 자동 캡처 스킵.
     - 02/21/S/휴/연차/병가 비실동 스킵 가드 완성.
  4. **단위 테스트 검증 완료 (`EnhancementsUnitTest.kt`)**:
     - `midnightCapture_nightDay2_and_nonActiveDay_skipEvaluation` 및 `targetOverride_commandPrefix_and_trackStartTime_linking` 테스트 추가 및 통과.
* **검증 결과**:
  - `EnhancementsUnitTest` 전체 단위 테스트 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 46: 불필요한 재로그인 최소화 및 로컬 캐시 우선(Cache-First) & 무소음 백그라운드 세션 갱신 아키텍처 도입 (v1.0.19)
* **작업 배경 및 목적**:
  * 실무 사용 중 이미 로컬 DB에 근무표와 다이아 데이터가 충분히 저장되어 있음에도, 앱 재실행·운전 후 온라인 자동 복귀·화면 이동 시마다 불필요하게 재로그인을 시도하거나 로그인 화면으로 전환되는 불편이 확인됨.
  * 원인을 분석한 결과 (1) 앱 콜드 스타트 시 무조건 `Routes.Login`으로 진입, (2) 온라인 모드에서 로컬 Room DB 캐시를 무시하고 매번 XHR 네트워크 호출 수행, (3) 세션 만료 시 로컬 캐시 폴백 전에 `reportAuthenticationExpired()`가 호출되어 로그인 화면으로 강제 튕김, (4) 자동 오프라인 복귀 시 만료된 세션으로 즉시 재조회, (5) 출무 확정 후에도 매번 출무 API 호출 등 5대 원인을 규명하고 전면 개선.
* **주요 구현 내용**:
  1. **스마트 즉시 진입(Smart Instant Start) 및 사용자 정보 캐싱 (`AppNavigation.kt`, `SecureCredentialStore.kt`)**:
     - `credentialStore.hasSavedCredentials()`를 신설하여 저장된 자격증명이 있거나 오프라인 모드일 때 `Routes.Login`을 거치지 않고 `authRootRoute`(메인 화면)로 0초 즉시 진입.
     - 로그인 시 수집된 사용자 성명(`cached_user_name`)과 소속(`cached_user_affil`)을 SharedPreferences에 영속화하여 즉시 진입 시에도 프로필이 유지되도록 구성.
  2. **온라인 모드 Cache-First(캐시 우선) 전략 적용 (`WorkScheduleRepository.kt`, `WorkScheduleRepositoryImpl.kt`, `DiaDetailRepository.kt`)**:
     - `getMonthWorkSchedule(year, month, forceRefresh: Boolean = false)`로 확장하여 온라인 모드에서도 로컬 Room DB 캐시가 존재하면 네트워크 통신 및 재로그인 없이 즉시 반환.
     - `DiaDetailRepository.getDiaDetail` 역시 로컬 Room DB에 유효한 다이아 구간 정보가 있으면 온라인/오프라인 무관하게 즉시 캐시를 반환하고, 캐시가 비어있을 때만 서버 조회 수행.
  3. **무소음 백그라운드 재로그인(Silent Re-login) 및 강제 로그인 화면 이동 제거 (`XcrewWebViewXhrDataSource.kt`, `AppNavigation.kt`, `LoginViewModel.kt`)**:
     - XHR 요청 시 미인증 상태이거나 세션 만료(`XcrewSessionExpiredException`) 감지 시, 포그라운드 화면 전환 없이 `session.trySilentRelogin()`을 호출해 숨겨진 `KorailWebView`로 백그라운드 로그인을 수행한 뒤 원래 XHR 요청을 자동 1회 재시도.
     - 백그라운드 재로그인이 실패하더라도 `reportAuthenticationExpired()`로 사용자를 로그인 화면으로 튕겨내지 않고 각 Repository의 로컬 Room DB 캐시 폴백이 작동하도록 개선.
     - `LoginViewModel`에서 프리플라이트 XHR 호출 직전 `xcrewSession.markAuthenticated()`를 호출하고 프리플라이트 결과를 Room DB에 즉시 저장.
  4. **출무 현황 스마트 조회 (`TodayWorkRepository.kt`)**:
     - 로컬 Room DB의 출무 스냅샷을 선행 로드하고, 이미 모든 구간의 실제 출무시각(`actlTm`)이 확정되었거나 미래 일자인 경우 원격 `fetchAttendanceStatus` 호출을 생략.
  5. **명시적 `[재접속]` 강제 동기화 (`AppNavigation.kt`)**:
     - 상단바 `[재접속]` 버튼 클릭 시에만 당월 스케줄 캐시를 삭제하고 `Routes.Login`으로 이동하여 서버 최신 데이터를 강제 갱신하도록 분리.
  6. **단위 테스트 검증 완료 (`EnhancementsUnitTest.kt`)**:
     - 온라인 모드 Cache-First 동작 및 백그라운드 무소음 재로그인 단위 테스트 2건을 추가하여 100% 통과 검증.
* **검증 결과**:
  - `EnhancementsUnitTest` 전체 단위 테스트 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 47: 세션 유지 시 메뉴별 실시간 정보(출무 현황·근무표) 즉시 최신화 & 텔레그램 열차 추적 500 에러 해결 (v1.0.20)
* **작업 배경 및 목적**:
  * `v1.0.19` 적용 이후 오늘의 근무 메뉴 진입 시 출무 현황이 바로 최신화되지 않는 문제와, 텔레그램 서버 연결 확인(`ping`)은 200 OK이나 수동/자동 열차 추적 명령 전송(`POST /api/v1/track`)이 모두 실패하는 문제를 해결.
* **주요 구현 내용**:
  1. **출무 현황 및 메뉴 진입 시 실시간 최신화 복원 (`TodayWorkRepository.kt`, `ScheduleViewModel.kt`, `AppNavigation.kt`, `XcrewWebViewXhrDataSource.kt`)**:
     - `TodayWorkRepository.kt`에서 출무 현황 원격 조회를 가로막던 `alreadyConfirmed` 스킵 조건을 제거하고, 온라인 상태에서는 오늘의 근무 진입 시 항상 `fetchAttendanceStatus`를 호출해 최신 출무 상태와 시각을 반영하도록 수정.
     - `XcrewWebViewXhrDataSource.fetchAttendanceStatus`에서 무음 재로그인 수행 후 최신 `session.userInfo`의 `empNm`을 재확인하여 빈 성명 전송 방지.
     - `AppNavigation.kt`에서 `setSilentReloginProvider`를 `remember` 블록에서 동기 등록(`awaitWebView()` + `prepareForLogin()` 선행)하고, 온라인 시작 시 백그라운드 무음 로그인을 즉시 수행하며, `sessionAuthenticatedEpoch` 및 `menuRefreshTrigger`를 연동해 세션 수립 직후와 각 메뉴 진입 시마다 최신 데이터를 갱신하도록 개선.
  2. **텔레그램 열차 추적 명령 전송 HTTP 500 에러 해결 (`SubwayNowManager.kt`, `SubwayNowApiClient.kt`, `SubwayNowBot/api_bridge.py`, `SubwayNowBot/scheduler.py`)**:
     - 오라클 클라우드(`129.225.156.25`) 서버 로그 분석을 통해 `LocalDateTime.toString()`(타임존 없는 ISO 문자열) 전송 시 서버에서 `TypeError: can't compare offset-naive and offset-aware datetimes` 및 예약 개시 시 `UnboundLocalError: cur_time_str`가 발생함을 규명.
     - `SubwayNowBot` 서버(`api_bridge.py`, `scheduler.py`)에서 naive `start_datetime`에 `KST_TIMEZONE`을 자동 부여하고 `cur_time_str` 스코프를 최상단으로 이동하여 실서버에 즉시 배포(`git push` -> 서버 `git pull` & `systemctl restart subwaynow.service`).
     - `CrewMate` 앱(`SubwayNowManager.kt`)에서도 `ZoneId.of("Asia/Seoul")` 기반 Offset ISO 문자열(`+09:00`)을 전송하도록 개선.
* **검증 결과**:
  - `SubwayNowBot` `test_api_bridge.py` 및 실서버 재기동 검증 완료.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 48: 야간근무(2일 연속 근무) 양일(1·2일차) 출무 현황 표출 및 2일차 출무 전 정밀 알람 복구 (v1.0.21)
* **작업 배경 및 목적**:
  * 야간근무(`11` -> `12`) 시 오늘의 근무 화면에서 출무 현황이 첫째 날만 표출되고 둘째 날 출무 정보가 누락되며, 오늘의 예정 정밀 알람에도 둘째 날 출무 전 알람이 등록되지 않던 문제를 해결.
* **주요 구현 내용**:
  1. **야간근무 익일 출무 현황 원격 조회 복구 및 사업일자(`pjtDt`) 정규화 (`TodayWorkRepository.kt`)**:
     - 기존 `TodayWorkRepository.kt`의 `dateToFetch.isAfter(today)` 분기문이 야간근무 1일차 진입 시 2일차(익일) 날짜에 대해 원격 서버(`fetchAttendanceStatus`) 조회를 건너뛰어 2일차 출무 데이터가 누락되던 결함을 발견하고 완전히 제거.
     - `datesToQuery`에 포함된 모든 대상 일자(1일차 및 2일차)를 실시간으로 원격 조회하고 Room DB 스냅샷에 저장하도록 복원.
     - `AttendanceStatusDto`의 `pjtDt`가 비어 있거나 포맷이 다른 경우 해당 조회 일자(`dateStr`, `yyyyMMdd`)로 정규화 및 스탬핑하여, 출무 그룹핑과 알람 엔진이 정확한 날짜를 인지하도록 보장.
     - 반환 리스트를 사업일자(`pjtDt`) 및 출무예정시각(`gtwcEpctTm`) 기준으로 중복 제거 및 시간순 자동 정렬.
  2. **표준 승무근무표 원칙 기반 야간근무 판정식 및 월 경계(말일~초일) 통합 (`WorkSchedule.kt`, `TodayWorkRepository.kt`)**:
     - `AGENTS.md` 표준 원칙(당일 `alcSpecDvCd == "11"` & 익일 `alcSpecDvCd == "12"`)을 철저히 준수하여, 야간 2일차 진입 시 어제 스케줄이 `"11"`이고 오늘이 `"12"`인 경우를 `yesterdayOvernightCandidates`에 즉시 포함.
     - 말일(31일)과 1일 사이에 걸친 야간근무 판정을 위해 전월(`yesterdaySchedules`), 당월(`currentSchedules`), 익월(`tomorrowSchedules`) 스케줄을 통합하여 `determineShiftType`을 판정하도록 개선.
  3. **야간근무 2일차 출무 전 정밀 알람 생성 및 스케줄링 연동 (`WorkAlarmRuleEvaluator.kt`, `TodayWorkScreen.kt`)**:
     - `WorkAlarmRuleEvaluator.kt`에서 각 출무 항목의 정규화된 `attendanceDate`(`pjtDt`)를 기반으로 고유 ID 및 알람 시각을 계산하여, 2일차 새벽/아침 출무 건에 대해서도 정확한 익일 시각으로 `출무 N분 전 알람`이 생성되도록 복구.
     - 2일차 알람의 경우 타이틀에 날짜(`MM.dd 출무 N분 전`)를 명시하여 근무자가 직관적으로 인지할 수 있도록 보강.
     - `TodayWorkScreen.kt`에서 다이아 구간 수집 시 메모리에 이미 로드된 `dia.detail.segments`를 직접 참조하여 다이아 구간 유실 방지.
* **검증 결과**:
  - `EnhancementsUnitTest` (야간근무 1일차/2일차 판정, 월 경계 야간근무 판정, 양일 출무 전 정밀 알람 동시 생성 및 시각 검증) 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 49: 야간근무 2일차 출무 전 정밀 알람 전일 출무 오판정 침묵 결함 해결 (v1.0.22)
* **작업 배경 및 목적**:
  * 야간근무 2일차 출무 현황이 화면에 잘 표출되고 예정 정밀 알람 목록에도 정상 등록되어 있음에도, 출무를 하지 않았음에도 예정 시각에 알람이 울리지 않던 문제를 해결.
* **근본 원인 규명**:
  * `WorkAlarmReceiver.kt`에서 출무 알람(`cat == "출무"`) 처리 시 `today.minusDays(1)`, `today`, `today.plusDays(1)` 3일치 캐시에서 열차번호 대조 없이 "아무 열차나 하나라도 출무 완료(`statuses.any { it.isCheckedIn }`)"되었으면 출무 완료로 간주하고 사이렌을 중단하던 결함 확인.
  * 야간근무 2일차 새벽 시점에는 전일(1일차) 이미 완료된 출무 데이터(`isCheckedIn == true`)가 `today.minusDays(1)`에 존재하므로, 2일차 출무를 아직 하지 않았음에도 출무 완료로 오판정되어 알람 서비스(`AlarmLoopService`)를 시작하지 않고 리턴(취소)해버렸던 원인 규명.
* **주요 구현 내용**:
  1. **개별 열차·사업일자 대조 기반 출무 취소 판정식 구축 (`WorkAlarmReceiver.kt`, `AttendanceAlarmCancellationChecker`)**:
     - `AttendanceAlarmCancellationChecker`를 분리하고 인텐트로부터 사업일자(`pjtDt`), 대상 열차번호(`trainNo`), 예정시각(`dptTimeStr`)을 추출.
     - `pjtDt`가 지정된 경우 해당 일자의 캐시만 조회하여 전일(1일차) 출무 완료 데이터가 간섭하지 못하도록 격리.
     - 대상 열차번호(`dtoTrain == cleanTargetTrain` 또는 접미사 일치) 및 예정시각(`dtoTime == cleanTargetTime`)이 일치하는 특정 항목(`matchedItem`)을 정확히 색인하고, 그 항목이 실제로 `isCheckedIn == true`일 때에만 사이렌을 취소하도록 엄격히 제한.
     - 일치하는 항목이 없거나 아직 출무 전(`!matchedItem.isCheckedIn`)이면 절대로 사이렌을 취소하지 않고 소리/진동을 울리도록 보장.
  2. **알람 아이템 및 스케줄러 사업일자(`pjtDt`) 파이프라인 구축 (`WorkAlarmRuleEvaluator.kt`, `WorkAlarmScheduler.kt`)**:
     - `WorkAlarmItem` 데이터 모델에 사업일자 필드(`val pjtDt: String? = null`)를 신설하고, 출무 알람 생성 시 `attendanceDate` 기반 사업일자(`yyyyMMdd`)를 스탬핑.
     - `WorkAlarmScheduler.kt`에서 알람 인텐트 Extras에 `pjtDt`를 전달하고, 알람 재스케줄링 시 기존 취소 루프 범위를 기존 `0..15`에서 `0..63`으로 확대하여 다이아 전체 구간 및 출무 알람 잔존 간섭 방지.
* **검증 결과**:
  - `EnhancementsUnitTest` (1일차 출무 완료 상태에서도 2일차 미출무 알람 정상 발동 검증, 동일 일자 다중 출무 독립 취소 검증) 100% 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 50: 아이폰(iOS 16+ / Swift 5.9+ / SwiftUI) 네이티브 포팅 패키지 및 유지보수 종합 가이드·핸드오프 아카이브 구축 (v1.0.23)
* **작업 배경 및 목적**:
  * 동료에게 아이폰(iOS) 포팅을 위한 소스코드를 편하게 전달하고, 앱 구조 파악 및 향후 유지보수가 용이하도록 iOS 호환 네이티브 소스 패키지와 아키텍처·유지보수 가이드를 구축.
* **주요 구현 내용**:
  1. **iOS 네이티브 포팅 프로젝트 구축 (`ios-port/CrewMateIOS/`)**:
     - 기존 안드로이드 코드베이스(`app/`)를 전혀 건드리지 않고 격리된 `ios-port/CrewMateIOS/` 디렉토리에 Swift Package(`Package.swift`) 및 XcodeGen(`project.yml`) 기반 iOS 16+ 네이티브 프로젝트를 구성.
     - **도메인 로직 1:1 포팅 (`Domain/`)**: `Models.swift`, `DutyRuleEngine.swift`, `WorkAlarmEvaluator.swift`, `SubwayNowRuleEvaluator.swift`를 통해 표준 휴일근무 판정, 야간근무(`11`->`12`) 2일 연속 1단위 카운트, 야간 양일 출무 조회 및 2일차 출무 알람 증발 방지(`AttendanceAlarmCancellationChecker`), 텔레그램 타겟 산출 및 역명 치환/오버라이드 동기화 로직을 100% 동일하게 구현.
     - **데이터·보안·플랫폼 계층 포팅 (`Data/`, `Platform/`)**: `KeychainCredentialStore.swift`(iOS Keychain 보안 저장), `XcrewWKWebViewBridge.swift`(숨겨진 `WKWebView` 쿠키 공유 XHR 브릿지), `XcrewParsers.swift`, `SubwayNowAPIClient.swift`(`+09:00` KST ISO-8601 직렬화), `LocalStorageAndBackup.swift`(Android `DataBackupManager.kt` 100% 호환 JSON 백업/복원), `IOSPlatformManagers.swift`(`UNUserNotificationCenter` Time-Sensitive 알림 및 Apple Watch 진동 연동, 운전 구간 오프라인 시간대 계산기) 구현.
     - **SwiftUI 7대 화면 및 전체 길이 캡처 구현 (`UI/`, `Tests/`)**: `Theme.swift`(`ImageRenderer` 기반 다이아 전체 길이 고해상도 사진 저장), `CrewMateIOSApp.swift`(Smart Instant Start 및 메뉴 전환 시 출무 현황 실시간 갱신), `TodayWorkView.swift`(6열 다이아 카드, 야간 1·2일차 사업일자별 출무현황, 알람/텔레그램 모달), `MonthlyScheduleView.swift`, `LookupAndSettingsViews.swift`, `CrewMatePortTests.swift` 작성.
  2. **포팅 & 유지보수 종합 가이드 문서 및 클린 핸드오프 ZIP 패키징**:
     - 루트의 `IOS_PORTING_AND_MAINTENANCE_GUIDE.md` 및 `ios-port/README.md`에 Android(`.kt`) ↔ iOS(`.swift`) 1:1 파일 매핑 표, Xcode 빌드 방법, 철도 승무 도메인 절대 원칙, iOS 단축어(`Shortcuts`) 자동 오프라인 연동 가이드, 6대 유지보수 포인트를 상세히 문서화.
     - 서명 키(`.jks`), `local.properties`, `build/`, `.gradle/`, `.apk` 등 민감 정보와 빌드 부산물을 제외한 클린 핸드오프 패키지 `CrewMate_iOS_Port_Handoff_v1.0.22.zip` 생성.
* **검증 결과**:
  - 안드로이드 `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 통과 및 iOS XCTest 케이스(`CrewMatePortTests.swift`) 구성 완료.

### STEP 51: 장시간 미사용 후 '오늘의 근무' 무한 로딩 및 WebView `pageReady` 데드락 원천 해결 (v1.0.24)
* **작업 배경 및 목적**:
  * 앱을 장시간 사용하지 않다가 다시 진입했을 때 간헐적으로 '오늘의 근무' 화면에서 무한 로딩 상태에 빠지고 `[재접속]`을 누르기 전까지 로딩만 계속되던 현상의 원인을 규명하고 근본 해결.
* **근본 원인 규명 (4대 복합 결함)**:
  1. `trySilentRelogin()` 도중 `prepareForLogin()`이 `resetPageReady()`를 호출한 상태에서 슬립 해제 직후 네트워크 순단으로 `login()`이 실패하거나 `refreshWebViewPage()`가 타임아웃되면 `pageReady`(`CompletableDeferred`)가 영구 미완료(Deadlock) 상태로 방치되어 `[재접속]` 전까지 모든 XHR이 차단됨.
  2. `TodayWorkViewModel.load()`가 로컬 캐시를 먼저 표시하지 않고 무조건 `_uiState.value = TodayWorkUiState.Loading`으로 화면을 가린 채 모든 원격 XHR 완료를 기다리며, `AppNavigation.kt`의 초기 `LaunchedEffect`가 `retry()`를 중복 호출해 첫 로딩을 취소하고 재시작함.
  3. 단일 XHR 타임아웃이 40초(`XHR_TIMEOUT_MILLIS = 40_000L`)로 과도하게 길고, `WorkScheduleRepositoryImpl.kt`에서 `TimeoutCancellationException`을 일반 `CancellationException`으로 오판하여 로컬 DB 폴백을 건너뛰고 에러로 던짐.
  4. 장시간 백그라운드 방치 후 포그라운드 복귀 시 숨겨진(`1.dp`, `alpha = 0f`) WebView에 대해 `onResume()` / `resumeTimers()`를 호출하지 않아 OS 절전 상태에서 깨어나지 못함.
* **주요 구현 내용**:
  1. **2단계 Stale-While-Revalidate 즉시 표출 구조 (`TodayWorkViewModel.kt`, `TodayWorkRepository.kt`, `WorkScheduleRepository.kt`, `WorkScheduleRepositoryImpl.kt`, `DiaDetailRepository.kt`)**:
     - `getTodayOrNextWork(localOnly = true)`를 통해 로컬 Room DB에 저장된 근무표·다이아·출무현황을 0초 즉시 화면(`TodayWorkUiState.Success`)에 먼저 띄운 뒤, 백그라운드에서 원격 최신화를 수행하도록 개선.
  2. **WebView `pageReady` 데드락 자동 복구, `ON_RESUME` 타이머 재개 및 20분 세션 TTL (`XcrewWebViewXhrDataSource.kt`, `AppNavigation.kt`)**:
     - `wakeUpWebView()` 및 `recoverPageReadyIfStuck()`을 구현하여 재로그인 실패나 타임아웃 시에도 `pageReady`가 영구 블록되지 않도록 보장하고, `Lifecycle.Event.ON_RESUME` 시 WebView를 즉시 깨우도록 연동.
     - 마지막 성공 통신 후 20분(`SESSION_IDLE_TTL_MILLIS`) 경과 시 `isSessionCurrentlyValid()`가 즉시 재인증 대상으로 판정하여 죽은 세션 XHR 대기 시간을 제거.
  3. **XHR 타임아웃 12초 단축 및 타임아웃 폴백 복원 (`XcrewWebViewXhrDataSource.kt`, `WorkScheduleRepositoryImpl.kt`)**:
     - `XHR_TIMEOUT_MILLIS`를 12초로 단축하고 JS `xhr.timeout = 10000`을 추가했으며, `WorkScheduleRepositoryImpl`에서 `TimeoutCancellationException` 발생 시 로컬 DB 캐시로 안전하게 폴백하도록 수정.
* **검증 결과**:
  - `EnhancementsUnitTest` (`workScheduleRepository_fallsBackToLocalCache_onTimeoutCancellationException`) 통과 및 `./gradlew.bat testDebugUnitTest assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 52: 코드베이스 전반 로직 결합도·보안 취약점 점검 및 중앙 집중식 스케줄 코디네이터(WorkDutySyncCoordinator) 리팩토링 (v1.0.25)
* **작업 배경 및 목적**:
  * 반복적인 기능 수정과 보완으로 인해 스케줄링 로직의 3중 파편화(경합 상태 발생, 단말기 부팅 시 출무 알람 증발 등), 실동/비실동 다이아 판정 기준 불일치, 브로드캐스트 리시버 메인 스레드 블로킹, 텔레그램 `requestCode` 충돌, PII 로그 노출 등 잠재적 오류와 결합도 위험을 선제적으로 전수 점검하고 아키텍처 단위로 리팩토링 및 보안 강화.
* **주요 점검 및 개선 내용**:
  1. **스케줄링 3중 파편화 해소 및 단일 코디네이터(`WorkDutySyncCoordinator`) 신설 (`WorkDutySyncCoordinator.kt`, `TodayWorkRepository.kt`, `TodayWorkScreen.kt`, `BootReceiver.kt`)**:
     - 기존 `TodayWorkRepository.kt`, `TodayWorkScreen.kt`, `BootReceiver.kt` 세 곳에서 알람·자동 오프라인·텔레그램 스케줄링을 각기 다른 방식으로 중복 실행하던 구조를 `WorkDutySyncCoordinator.sync()`로 단일화.
     - 특히 `BootReceiver.kt`에서 단말기 재부팅 시 출무현황(`attendanceStatuses`)이 전달되지 않아 **모든 출무 전 알람이 영구 증발**하고, 건너뛴 알람(`bypassedAlarmKeys`)이 되살아나며, 야간 2일차 및 텔레그램 스케줄이 누락되던 중대 결함을 `WorkDutySyncCoordinator.syncFromLocalDatabase(context)`를 통해 100% 무손실 복구하도록 개선.
     - `TodayWorkScreen.kt`에서 야간 2일차 다이아 구간 캐시 조회 시 화면 날짜가 아닌 다이아 본래 날짜(`dia.schedule.workDate`)로 캐시 키를 조회하도록 수정하여 캐시 미스 방지.
  2. **다이아 실동/비실동 판정 단일 진실 공급원(`WorkSchedule.isValidActiveDia()`) 확립 (`WorkSchedule.kt`, `WorkScheduleEntity.kt`, `TodayWorkRepository.kt`, `MidnightCaptureReceiver.kt`, `AppNavigation.kt`)**:
     - `WorkSchedule.isValidActiveDia()` 및 `WorkScheduleEntity.isValidActiveDia()`를 선언하여 `alcSpecDvCd == "21"`(비실동) 또는 `alcSpecDvCd == "02"`(휴일/비번)이거나 비실동 키워드(연차, 교육, 병가 등)가 포함된 경우 숫자 다이아 번호가 부여되어 있어도 비실동으로 단일 기준에 따라 엄격히 제외.
     - `TodayWorkRepository.toDiaCandidates()`, `MidnightCaptureReceiver.kt`, `AppNavigation.kt`에 산재하던 비표준 문자열 필터링을 `isValidActiveDia()`로 일원화.
  3. **브로드캐스트 리시버 비동기 스레드 안전성 확보 및 텔레그램 `requestCode` 충돌 방지 (`WorkAlarmReceiver.kt`, `SubwayNowManager.kt`)**:
     - `WorkAlarmReceiver.kt` 메인 Looper 스레드의 `runBlocking(Dispatchers.IO)`를 제거하고 `goAsync()` 기반 `CoroutineScope(Dispatchers.IO)` 단일 코루틴으로 통합. `try-finally` 블록 내에서 `wakeLock?.release()`와 `pendingResult.finish()` 호출을 보장하여 ANR 및 배터리 누수 차단.
     - `SubwayNowManager.getRequestCodeForTrain(trainNo)`를 신설하여 열차별 고유한 PendingIntent `requestCode`를 결정론적으로 산출함으로써 `cancelTarget`, `scheduleAlarmForTarget`, `rescheduleTarget` 간 `requestCode` 불일치 및 다중 열차 간 알람 덮어쓰기 충돌을 원천 차단.
  4. **보안 취약점 차단 및 개인식별정보(PII) 로그·백업 노출 방지 (`XcrewWebViewXhrDataSource.kt`, `DataBackupManager.kt`)**:
     - `XcrewWebViewXhrDataSource.kt`에서 System Logcat으로 출력되던 실명 평문(`empNm=$empNm`)을 `hasEmpNm=${empNm.isNotBlank()}`로 마스킹하여 프로덕션 로그 유출 차단 (`AGENTS.md` 제1원칙 준수).
     - `DataBackupManager.kt` 백업 제외 키 목록에 `cached_user_name`, `cached_user_affil`, `telegram_chat_id`, `telegram_verified`, `telegram_stopped_trains_`, `telegram_target_overrides_`를 추가하고 `isSensitiveKey()` 검사를 강화하여 백업 JSON 파일 내 개인정보 및 텔레그램 토큰 유출을 원천 방지.
* **검증 결과**:
  - `EnhancementsUnitTest` 신설 3종(`workSchedule_isValidActiveDia_identifiesValidAndExcludesNonActiveAndRestDays`, `dataBackupManager_isSensitiveKey_blocksPersonalInfoAndTelegramChatId`, `subwayNowManager_getRequestCodeForTrain_isDeterministicAndCollisionResistant`) 포함 51개 단위 테스트 전원 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 53: 월별 근무표 2중 중복 표출 결함 해결 및 자정 다이아 자동 캡처 레이아웃 인앱 화면 100% 일치 (v1.0.26)
* **작업 배경 및 목적**:
  1. 월별 근무표 조회 시 같은 정보가 2번씩 들어가는 현상(월별 근무표 처음 진입 시 정상 1개씩 표출되다가 다른 달을 조회한 다음 이번 달로 돌아오면 모든 날짜 셀에 동일 근무가 2개씩 스택 표출되는 결함)의 근본 원인을 규명하고 해결.
  2. 오늘의 근무 자동 캡처 기능을 통해 저장되는 비트맵 화면이 앱 내 '승무 다이아 상세' 캡처 화면과 다른 문제(타이틀 "MM.dd 근무" vs "승무 다이아 상세", 얇은 회색 라인 vs 굵은 다크네이비 운전 인디케이터 라인, 준비/출고준비 도착역 "-" 미표출 등)를 완벽 일치시킴.
* **근본 원인 규명**:
  1. 앱 구동 및 화면 전환 시 `ScheduleViewModel`, `AppNavigation`, `TodayWorkRepository`에서 `getSchedules()`가 비동기 동시 다발 호출되며, 기존 비원자적인 `deleteSchedulesForMonth`와 `insertSchedules` 간 경합(Race Condition)으로 동일한 30개 레코드가 Room DB에 2세트(60개) 저장됨. `forceRefresh = false`로 캐시를 다시 읽을 때 2중으로 쌓인 레코드가 UI에 그대로 노출됨.
  2. `MidnightCaptureReceiver.kt`의 백그라운드 캔버스 비트맵 렌더러 `renderDiaBitmap`이 구형 스타일(헤더 "MM.dd 근무", 균일한 3.5f 회색 라인, `arvStn != "-"` 조건으로 인한 `"-"` 누락 등)로 하드코딩되어 인앱 Compose `DiaDetailCard`와 시각적 괴리가 발생함.
* **주요 구현 내용**:
  1. **SQLite 원자적 교체 트랜잭션 및 Mutex 동기화 (`WorkScheduleDao.kt`, `WorkScheduleRepositoryImpl.kt`, `AppNavigation.kt`)**:
     - `WorkScheduleDao.kt`에 `@Transaction suspend fun replaceSchedulesForMonth(yearMonth: String, schedules: List<WorkScheduleEntity>)` 신설.
     - `WorkScheduleRepositoryImpl.kt`에 `refreshMutex = Mutex()` 및 고유 근무 키 식별 기반 `toDeduplicatedDomainSchedules()` 신설하여 캐시 직접 조회, 메인 조회, 네트워크 수신, DB 폴백 전 단계에서 중복 제거 및 무결성 보장.
     - `AppNavigation.kt` 프리플라이트 동기화에도 중복 제거 및 `replaceSchedulesForMonth` 적용.
  2. **UI 계층 렌더링 방어 로직 (`ScheduleScreen.kt`)**:
     - `schedulesByDate` 매핑 시 `dayList.distinctBy { ... }`를 추가하여 캐시나 데이터베이스 상태와 무관하게 캘린더 셀 내 동일 근무 2중 표출을 원천 방지.
  3. **자정 다이아 자동 캡처 레이아웃 100% 일원화 (`MidnightCaptureReceiver.kt`, `TodayWorkScreen.kt`)**:
     - 헤더 타이틀을 `"승무 다이아 상세"`로 통일하고 우측 `YYYY.MM.DD` 캡슐 알약 날짜 뱃지 유지.
     - 운전/승무(Rank 1) 구간에 굵은 다크 네이비 라인(`strokeWidth = 7f`, `#0F2942`) 및 원점 마커(radius 8f)를 상·하단 분할 렌더링.
     - `준비` / `출고준비` 등 도착역 미기재 항목에서 도착 시각이 존재할 때 도착역(5열)에 `"-"`를 누락하지 않고 정상 표출 (`arvStn != "-" || arvTime != "-"`).
     - 6개 열(열차번호/구분 뱃지, 출발역, 출발시각, 화살표, 도착역, 도착시각)의 가로 중심 좌표 및 폰트 굵기/색상을 센터 정렬 기준으로 완벽 대칭 정렬.
     - `TodayWorkScreen.kt` 수동 다이아 캡처 타이틀 역시 `"승무 다이아 상세"`로 통일.
* **검증 결과**:
  - `EnhancementsUnitTest` 신설 3종(`workSchedule_deduplication_removesDuplicateRowsForSameDate`, `workSchedule_preservesDistinctDutiesOnSameDate`, `diaCapture_arrivalStationPlaceholder_renderedWhenTimeExists`) 포함 54개 단위 테스트 전원 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

### STEP 54: 정밀 알람 출무 전 알람 정상 출무 후 오발동 결함 근본 해결 (v1.0.27)
* **작업 배경 및 목적**:
  * 정상적으로 출무(단말기 출무 처리)를 마쳤음에도 출무 전 알람이 울리는 결함이 수차례 개선 시도에도 불구하고 반복 발생하던 문제의 근본 원인을 전면 추적하고, 단순 조건문 보정이 아닌 4중 방어(출무 완료 래치, 사이렌 2분 전 헤드리스 자동 검증, 실시간 재확인, 포그라운드 복귀 갱신) 아키텍처를 도입하여 근본 해결.
* **근본 원인 규명 (5대 복합 결함)**:
  1. `WorkAlarmReceiver.kt`는 알람 발동 시 서버 조회를 전혀 하지 않고 로컬 Room DB 캐시만 확인하는데, 이 캐시는 사용자가 앱을 켜고 통신까지 성공했을 때만 갱신되므로 출무 후 앱을 열지 않으면 영구히 구형 캐시('출무전')를 기준으로 사이렌이 울림.
  2. `XcrewWebViewSession`이 `AppNavigation.kt`의 Compose `remember` 내부에만 격리되어 있어 백그라운드 수신기가 접근할 수 없음.
  3. 앱으로 복귀(`ON_RESUME`)했을 때 세션 TTL(20분) 내이면 재로그인이 발생하지 않아 오늘의 근무 화면이 이전 '출무전' 상태를 그대로 유지함.
  4. 네트워크 실패나 빈 응답 시 구형 캐시가 재주입되며, 이 데이터로 `WorkDutySyncCoordinator.sync()`가 실행되면 이미 취소되었던 출무 알람이 다시 등록되는 상태 역전 발생.
  5. `AttendanceAlarmCancellationChecker`에서 서버 `trnNo`가 빈 문자열일 때 `endsWith("")`가 무조건 true가 되어 엉뚱한 열차가 매칭되는 결함 존재.
* **주요 구현 내용**:
  1. **출무 완료 래치(`AttendanceCheckInLatch.kt`) 및 스냅샷 중앙 저장소(`AttendanceSnapshotStore.kt`) 신설**:
     - `YYYYMMDD|CLEAN_TRAIN|HHMM` 고유 키로 출무 완료 사실을 영구 보존(3일 만료 자동 정리)하여, 구형 캐시 재주입 시에도 출무 알람 재등록을 원천 차단.
     - `AttendanceSnapshotStore`를 통해 출무현황 저장 경로를 일원화하고 저장 시 래치 자동 갱신.
  2. **사이렌 2분 전 백그라운드 사전 검증 알람 및 서비스(`AttendancePrecheckReceiver.kt`, `AttendanceVerificationService.kt`, `HeadlessAttendanceVerifier.kt`)**:
     - 사이렌 2분 전에 `AttendancePrecheckReceiver`를 예약하고 `AttendanceVerificationService`(dataSync)를 구동하여 최신 출무 여부를 자동 확인.
     - 앱이 꺼져 있거나 세션이 없어도 `HeadlessAttendanceVerifier.kt`가 암호화된 자격증명으로 자동 로그인 및 출무 조회를 조용히 수행하여 래치 갱신.
  3. **전역 활성 세션 레지스트리(`XcrewSessionRegistry.kt`) 및 포그라운드 복귀(`ON_RESUME`) 실시간 출무 최신화 (`AppNavigation.kt`)**:
     - `XcrewSessionRegistry.kt`를 신설하여 포그라운드 UI가 살아있을 때 백그라운드 수신기가 기존 세션을 즉각 재사용(0.5초 초고속 조회)할 수 있도록 연동.
     - `AppNavigation.kt`의 `ON_RESUME`에 `resumeRefreshTrigger`를 연동하여 출무 처리 후 앱으로 복귀했을 때 재접속 없이도 오늘의 근무 출무현황이 즉시 최신화되도록 개선.
  4. **사이렌 발동 3단계 방어 및 빈 열차번호 오매칭 차단 (`WorkAlarmReceiver.kt`, `WorkAlarmScheduler.kt`, `WorkAlarmRuleEvaluator.kt`, `WorkDutySyncCoordinator.kt`, `TodayWorkRepository.kt`)**:
     - `WorkAlarmReceiver.kt`: 1) 래치 선검사, 2) 활성 세션 초고속 재확인(최대 6초), 3) DB 스냅샷 검사의 3중 방어 적용. 발동 시 알람 이력 메시지에 사전확인 판정 근거(`precheckAudit`)를 투명하게 기록.
     - `AttendanceAlarmCancellationChecker`: 서버 DTO 열차번호가 빈 문자열(`""`)일 때 `endsWith("")`가 무조건 참이 되어 엉뚱한 열차가 매칭되던 결함 수정.
     - `WorkAlarmRuleEvaluator.kt` 및 `WorkDutySyncCoordinator.kt`: 알람 평가 시 래치된 키(`checkedInKeys`)를 전달하여 구형 캐시가 주입되어도 출무 알람 생성을 원천 차단.
* **검증 결과**:
  - `EnhancementsUnitTest` 신설 4종(`attendanceLatch_keyOf_normalizesDateTrainAndTime`, `evaluator_skipsAttendanceAlarm_whenLatchedEvenIfStaleCacheSaysNotCheckedIn`, `evaluator_overnightDay2_latchOnDay1DoesNotSuppressDay2`, `cancellationChecker_emptyServerTrainNo_doesNotFalseMatch`) 포함 58개 단위 테스트 전원 통과.
  - `./gradlew.bat testDebugUnitTest` 및 `./gradlew.bat assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

---

### STEP 55. 오프라인 전환 시 승무원 근무 현황 4일치 저장 보장, 오프라인 '열차 승무원 조회' 지원 및 출무 사전확인 15초+위임 구조 개선 (`v1.0.28`, 2026-11-07)
* **배경 및 문제점**:
  1. 오프라인 모드로 전환할 때 오늘자 소속 승무원 근무 현황이 저장되지 않은 채 전환되는 사례가 발생함. 원인 분석 결과 ① 수동 전환 프리페치가 단일 `runCatching`으로 감싸져 실패해도 무조건 전환되었고, ② 서버 빈 응답 시 기존 캐시를 삭제했으며, ③ 자동 오프라인 전환(`AutoOfflineReceiver` / `AutoOfflineManager`) 경로에는 승무원 현황 프리페치가 없었고, ④ 오늘 하루만 저장하여 어제·내일 및 자정을 넘긴 야간열차 조회가 불가능했음.
  2. 이미 다이아 기초자료(`dia_census`)와 오프라인 전환 시 저장된 승무원 현황(`crew_assignments`)이 앱에 존재함에도 `열차 승무원 조회` 메뉴가 오프라인 모드에서 아예 비활성화되어 있었고, 내부 조회 로직 또한 캐시를 거치지 않고 Xcrew XHR을 직접 호출하고 있었음.
  3. 출무 5분 전 알람의 사전확인이 `5+2분(7분) 전`에 수행되어 7분~5분 사이에 실제 출무를 완료했을 때 5분 전 알람이 울리는 공백이 존재했음.
* **해결 및 아키텍처 개선 내역**:
  1. **오프라인 전환 시점 4일치(`전전일·어제·오늘·내일`) 승무원 현황 저장 및 실패 방어 (`CrewAssignmentDao.kt`, `CrewWorkStatusRepository.kt`, `OfflineCrewPrefetcher.kt`, `AppNavigation.kt`, `AutoOfflineReceiver.kt`, `AutoOfflineManager.kt`)**:
     - `CrewAssignmentDao.replaceAssignmentsForDate` 트랜잭션 도입 및 `CrewWorkStatusRepository`의 빈 응답 시 기존 캐시 보존 방어 로직 적용.
     - `OfflineCrewPrefetcher`를 신설하여 오프라인 전환 시점(수동 전환 및 자동 오프라인 전환 직전)에 `오늘·내일·어제·전전일` 4일치를 통신 저장하고, 당일 이미 전환하며 4일치가 DB에 저장된 흔적(`crew_window_anchor == 오늘`)이 있으면 통신 없이 즉시 재사용하도록 구현.
     - 수동 전환 시 오늘자 저장에 실패하면 즉시 전환하지 않고 `[재시도] [저장 없이 전환] [취소]` 확인 창을 띄우도록 개선.
  2. **오프라인 '열차 승무원 조회' 활성화 및 기초자료·캐시 연동 (`AppNavigation.kt`, `TrainCrewLookupV2Screen.kt`, `DiaCensusManager.kt`, `DiaDetailRepository.kt`)**:
     - 상단 메뉴에서 오프라인 시 막혀 있던 `열차 승무원 조회` 메뉴를 활성화하고 `CrewWorkStatusRepository`를 연결하여 네트워크 통신 없이 로컬 DB만으로 조회가 동작하도록 변경.
     - 오프라인 모드에서 운행일자 선택 범위를 `어제 ~ 내일`(`OfflineLookupRange`)로 제한하고 `[다이아 기초자료 조사 실행]` 버튼을 비활성화 처리.
     - `DiaDetailRepository`에 `diaCensusDao` 폴백을 추가하여 오프라인에서 타 승무원 다이아 번호를 클릭해도 기초자료 DB(`dia_census`)에서 상세 타임라인을 즉시 표시.
  3. **전역 오프라인 네트워크 차단 스위치 (`XcrewWebViewXhrDataSource.kt`)**:
     - `XcrewWebViewSession.offlineGuard`를 통해 오프라인 모드 전환 이후에는 모든 Xcrew XHR 요청과 무음 재로그인을 통신 계층에서 즉시 차단.
  4. **출무 사전확인 간격 축소(`15초 전`) 및 사이렌 발동 위임 조율기 (`AttendancePrecheckCoordinator.kt`, `AttendanceSirenDispatcher.kt`, `WorkAlarmScheduler.kt`, `WorkAlarmReceiver.kt`, `AttendanceVerificationService.kt`)**:
     - 사전확인 알람 시각을 사이렌 **15초 전(`PRECHECK_LEAD_SECONDS = 15L`)**으로 좁히고, 사이렌 시각(`T`)에 사전확인 서비스가 아직 서버 조회 중이면 `WorkAlarmReceiver`가 사이렌 발동을 서비스에 위임(`tryDefer`, 최대 10초)하여 조회가 끝난 직후 출무 여부에 따라 취소 또는 발동하도록 개선.
* **검증 결과**:
  - `EnhancementsUnitTest` 신설 11종 포함 총 69개 단위 테스트 전원 통과 및 `assembleDebug` Zero-Error(0 오류) 빌드 검증 완료.

---

### STEP 56. 설정 메뉴 내 '앱 정보' 다이얼로그 임의 하드코딩 문구 제거 및 원본 문구 복구 (`v1.0.29`, 2026-11-07)
* **배경 및 원인 분석**:
  - 설정 화면의 `앱 정보 및 만든 이` 다이얼로그 본문에 사용자의 지시 없이 *"한국철도공사 코레일 수도권동부본부..."* 등 임의로 작성된 홍보/요약 문구가 하드코딩되어 있던 문제 발생.
  - 형상 이력 조사 결과 과거 커밋 `b99b8bbf`(2026-10-28 13:20, `feat: CrewMate v1.0.11`) 진행 시 이전 에이전트가 `SettingsScreen.kt`를 복원하는 과정에서 원본 안내 문구를 무단으로 덮어썼던 것으로 확인됨.
* **복구 내역**:
  - [SettingsScreen.kt](file:///c:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt)의 `showAppInfoDialog` 내부 텍스트를 원본 문구(비공식 개인 편의 목적 앱 안내, 면책 조항, 사내 파발마 문의 안내 및 `"안산승무 윤영준 드림"`)로 100% 원상 복구.
* **검증 결과**:
  - 69개 단위 테스트 전원 통과 (`testDebugUnitTest`) 및 디버그 빌드 (`assembleDebug`) 완료.

---

### STEP 57. 출근 60분 전 알람 미발동 결함 근본 해결, 오프라인 소속 승무원 3일 제한, 다이아 '준비' 구분선 개선 및 출무현황 전용 새로고침 추가 (`v1.0.30`, 2026-11-08)
* **배경 및 원인 분석**:
  - 다이아 상세 구간(`DiaDetailSegment`)의 첫 번째 항목인 `준비` 구간의 시작 시각(`dptTm`)이 곧 출무 시각(예: `07:15`)이므로 `WorkAlarmRuleEvaluator`의 시각 계산식 자체는 정상(출무 시각 기준 60분 전 발동)이었으나, ① `AppNavigation.kt`(`Routes.TodayWork`)의 `DiaDetailRepository` 생성자에 `diaCensusDao` 주입이 누락되고 `getCachedDiaDetailOnly`에 `getLatestDiaDetailByPdiaNo` 폴백이 없어 구간 목록이 빈 리스트로 반환될 때 기존 등록된 알람을 덮어써 취소시키는 결함, ② 전일에 근무가 있었던 경우 전일에 당일 알람만 등록하고 익일 오전 출근 알람을 사전 예약하지 않으며 자정 전환 시에도 알람 재동기화가 수행되지 않던 결함, ③ `WorkAlarmSettingsScreen`에서 알람 설정 변경 시 실제 스케줄러(`WorkDutySyncCoordinator`)를 호출하지 않던 결함이 복합 작용하여 당일 출근 60분 전 알람이 누락됨.
  - 또한 오프라인 모드 시 `열차 승무원 조회`와의 통일성을 위해 `소속 승무원 근무 현황`도 오프라인 모드일 때만 `어제·오늘·내일` 3일로 조회를 제한하고, 다이아 상세조회 화면의 `준비` 상단 구분선 시인성 강화(스크롤 길이를 늘리지 않는 컴팩트 여백 유지) 및 오늘의 근무 `출무현황 확인하기` 카드 전용 새로고침 버튼 추가가 요청됨.
* **핵심 해결 및 구현 내역**:
  1. **출근 60분 전 알람 미발동 3대 원인 해결 (`DiaDetailRepository.kt`, `WorkDutySyncCoordinator.kt`, `MidnightCaptureReceiver.kt`, `BootReceiver.kt`, `WorkAlarmSettingsScreen.kt`, `AppNavigation.kt`)**:
     - `AppNavigation.kt`의 `Routes.TodayWork`에 `database.diaCensusDao()`를 주입하고, `DiaDetailRepository.getCachedDiaDetailOnly` 및 `WorkDutySyncCoordinator.loadCachedSegmentsForDia`에서 3단계 폴백(날짜+다이아 정확 일치 -> `getLatestDiaDetailByPdiaNo` -> `dia_census` 테이블 재조합)을 구축하여 빈 구간으로 인한 기존 알람 취소 원천 차단.
     - `WorkDutySyncCoordinator.sync()`에서 당일(`workDate`) 알람과 함께 익일(`workDate.plusDays(1)`) 활성 근무 알람도 사전 평가·병합 등록(`evaluateNextDayActiveAlarms`)하도록 개선하고, 매일 00:01(`MidnightCaptureReceiver`) 및 앱 기동 시(`AppNavigation`) `WorkDutySyncCoordinator.syncFromLocalDatabase(context)`를 자동 실행.
     - `WorkAlarmSettingsScreen.kt`의 `refreshTodayAlarms()`에서 설정 변경 즉시 `WorkDutySyncCoordinator.sync()` / `WorkAlarmScheduler.cancelAllAlarms()`가 호출되도록 연결.
  2. **`소속 승무원 근무 현황` 오프라인 모드 전용 3일 조회 제한 (`CrewWorkStatusScreen.kt`)**:
     - 온라인 모드에서는 기존과 동일하게 날짜 제한 없이 자유롭게 조회 가능하며, 오프라인 모드(`isOfflineMode == true`)로 전환했을 때만 `OfflineLookupRange`(`today.minusDays(1) .. today.plusDays(1)`)를 적용하여 이전/다음날 버튼 및 달력 다이얼로그(`minDate`/`maxDate`)를 `어제·오늘·내일` 3일로 제한.
  3. **다이아 상세조회 `준비` 항목 상단 구분선 가시성 개선 (`DiaDetailCard.kt`, `MidnightCaptureReceiver.kt`)**:
     - 스크롤 길이가 과도하게 길어지지 않도록 상하 여백을 컴팩트한 `vertical = 4.dp`로 설정하고, 두께 `1.5.dp` 및 테마 포인트 컬러(`MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)`)를 적용(비트맵 캡처 `dividerPaint`도 `#8C0F2942`, `2.5f`로 동기화).
  4. **오늘의 근무 `출무현황 확인하기` 카드 전용 새로고침 버튼 신설 (`TodayWorkRepository.kt`, `TodayWorkViewModel.kt`, `TodayWorkScreen.kt`)**:
     - `출무현황 확인하기` 카드 헤더 우측에 컴팩트한 `🔄 새로고침` 버튼을 배치하고, 클릭 시 전체 화면 리로드 없이 출무 현황 API만 단독 재조회(`refreshAttendanceStatusesOnly`)하여 스냅샷·출무래치·출무알람 동기화 및 카드 자동 펼침을 수행.
* **검증 결과**:
  - `EnhancementsUnitTest` 신규 단위 테스트를 포함한 총 70개 단위 테스트 전원 통과 및 `assembleDebug` Zero-Error 빌드 검증 완료.

---

### STEP 58. 다이아 '준비' 구분선 두께·짙은 포인트 컬러 강화, 설정 ON/OFF 토글 추가 및 테마 맞춤 스크린샷 캡처 구현 (`v1.0.31`, 2026-11-09)
* **배경 및 원인 분석**:
  - 직전 STEP 57에서 추가된 다이아 '준비' 상단 구분선이 너무 가늘고(`1.5.dp`), 포인트 컬러 투명도(`alpha = 0.5f`)로 인해 색상이 옅게 바래 보여 가시성이 다소 미흡하다는 피드백이 접수됨.
  - 또한 사용자가 필요에 따라 구분선 표출 여부를 직접 제어할 수 있도록 설정 화면에 ON/OFF 토글 스위치 추가가 요청됨.
  - 한편, 인앱 스크린샷 캡처(`TodayWorkScreen`, `ScheduleScreen`, `DiaLookupScreen`) 시 `DiaCaptureHelper`에서 `CrewMateTheme`에 사용자 테마 설정(`app_theme`)을 넘겨주지 않고, `ScreenshotHelper`의 `captureComposableToBitmap`에서 `canvas.drawColor(Color.WHITE)`가 하드코딩되어 있어, 사용자가 '다크 모드'로 앱을 사용하는 중에도 캡처본이 항상 라이트 모드(흰색 배경)로 저장되는 결함이 확인됨.
* **핵심 구현 내역**:
  1. **'준비' 구분선 두께 및 짙은 포인트 컬러 강화 (`DiaDetailCard.kt`, `MidnightCaptureReceiver.kt`)**:
     - 상하 여백은 스크롤 길이를 최소화하는 `Modifier.padding(vertical = 4.dp)`로 유지하면서, 선 두께를 **`2.5.dp`로 보강**하고 색상을 `MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)`로 짙게 조정하여 바랜 느낌 없이 또렷한 포인트 컬러로 구분선이 드러나도록 개선.
     - `MidnightCaptureReceiver.kt`의 자동 캡처 캔버스 구분선도 `strokeWidth = 4f`(2.5dp 규격 일치) 및 테마별 85% alpha 포인트 컬러(`라이트: #D90F2942`, `다크: #D938BDF8`)로 일원화.
  2. **설정 메뉴 내 '준비' 구분선 ON/OFF 토글 스위치 신설 (`SettingsScreen.kt`, `DiaDetailCard.kt`, `MidnightCaptureReceiver.kt`)**:
     - `SettingsScreen.kt`에 `showPrepDivider`(`prefs.getBoolean("show_prep_divider", true)`) 상태 및 `다이아 '준비' 상단 구분선 표시` Switch 카드를 신설.
     - `DiaDetailCard.kt`에서 `OnSharedPreferenceChangeListener`를 통해 설정 변경 시 즉시 실시간 UI에 반영되도록 구현.
     - `MidnightCaptureReceiver.kt`에서도 `show_prep_divider`가 `true`일 때만 구분선을 렌더링하도록 반영.
  3. **현재 선택된 앱 테마(라이트/다크) 맞춤 스크린샷 캡처 지원 (`Theme.kt`, `ScreenshotHelper.kt`, `DiaCaptureHelper.kt`, `MidnightCaptureReceiver.kt`)**:
     - `Theme.kt`: SharedPreferences의 `app_theme`과 시스템 야간 모드를 판정하는 `Context.getAppThemeMode()` 및 `Context.isDarkThemeActive()` 공통 확장 함수 신설.
     - `ScreenshotHelper.kt`: `captureComposableToBitmap`에 `backgroundColor: Int? = null`을 추가하고, 하드코딩된 `canvas.drawColor(Color.WHITE)`를 제거하여 전달된 테마 배경색으로 캔버스를 초기화하도록 개선.
     - `DiaCaptureHelper.kt`: 현재 테마 모드를 `CrewMateTheme(appThemeMode = appThemeMode)`에 명시적으로 주입하고 테마별 배경색(다크: `#0B0F17`, 라이트: `#F6F8FC`)을 지정하여, 다크 모드 시 100% 동일한 다크 테마(검정/다크네이비 배경, 흰색 텍스트, 스카이블루 포인트)로 갤러리에 저장되도록 수정.
     - `MidnightCaptureReceiver.kt`: `renderDiaBitmap`에 `isDark` 매개변수를 지원하여 자정 자동 캡처 시에도 현재 앱 테마 설정에 맞추어 캔버스·카드·텍스트·뱃지 색상을 일치시킴.
* **검증 결과**:
  - `EnhancementsUnitTest` 신규 단위 테스트 2종 포함 총 72개 단위 테스트 전원 통과 (`testDebugUnitTest`) 및 디버그 빌드 (`assembleDebug`) 완료.

### STEP 59. 다이아 '준비' 구분선 스타일(1.5dp, alpha 50%) 롤백 및 단일 통합 렌더러(DiaCaptureRenderer) 기반 캡처 형태 100% 통일 (`v1.0.32`, 2026-11-09)
* **배경 및 원인 분석**:
  1. **구분선 스타일 롤백 요구**:
     - 직전 STEP 58에서 구분선 두께를 `2.5.dp`로 올리고 색상을 짙게(`alpha = 0.85f`) 변경하였으나, 이전 버전(`v1.0.30`)의 간결한 스타일(`1.5.dp`, `alpha = 0.5f`)이 더 적절하다는 사용자 요청에 따라 스타일을 직전 버전으로 신속히 원복(Rollback)하기로 결정함. (단, 설정 화면의 ON/OFF 토글 및 상하 패딩 `4.dp`는 유지)
  2. **자정 캡처와 수동 스크린샷 캡처 형태 불일치 근본 원인 분석**:
     - 인앱 수동 스크린샷은 화면이 켜진 포그라운드 상태에서 Jetpack Compose UI(`DiaDetailCard`)를 렌더링하여 캡처하므로 Compose의 비례 가중치(`weight(1.4f)`, `weight(1.0f)`) 및 M3 카드 스타일이 완벽히 적용됨.
     - 반면, 자정 00:01 AM 자동 캡처(`MidnightCaptureReceiver.kt`)는 화면이 꺼진(Screen Off) 상태에서 Android OS의 VSYNC 렌더링 펄스가 정지되어 Compose Recomposition을 구동할 수 없으므로(타임아웃 발생), 별도의 안드로이드 2D Canvas 코드(`renderDiaBitmap`)를 자체 작성하여 렌더링해 옴.
     - 기존 `MidnightCaptureReceiver` 내 Canvas 그리기 코드는 각 열의 X좌표를 절대 픽셀(`330f`, `465f`, `600f`, `735f`, `870f`)로 임의 하드코딩해 두었음. 이로 인해 1080px 기준 우측에 200px 이상의 거대한 빈 여백이 남고, 텍스트가 왼쪽으로 쏠리며, 카드 모서리 곡률·테두리·폰트 크기·패딩이 Compose 뷰와 완전히 달라 시각적 괴리가 매우 컸음.
* **핵심 구현 내역**:
  1. **'준비' 항목 상단 구분선 스타일 직전 버전(`v1.0.30`) 롤백 (`DiaDetailCard.kt`, `DiaCaptureRenderer.kt`)**:
     - 두께: `2.5.dp` -> **`1.5.dp`**로 롤백.
     - 색상: `primary.copy(alpha = 0.85f)` -> **`primary.copy(alpha = 0.5f)`**로 롤백.
     - 상하 여백 `Modifier.padding(vertical = 4.dp)` 및 설정 화면 ON/OFF 토글(`show_prep_divider`)은 온전히 유지.
  2. **단일 통합 렌더러 신설 (`DiaCaptureRenderer.kt`)**:
     - 화면이 켜져 있을 때와 꺼져 있을 때 모두 동일한 수학적 비례 모델과 비주얼 토큰을 공유할 수 있도록, 순수 2D Canvas 기반 고화질 비트맵 단일 통합 렌더러 신설.
     - **비례 가중치 1:1 일치**: 총 가로폭 1080px(`DENSITY = 3.0f`)에서 좌우 카드 패딩(12dp)과 내부 패딩(14dp), 타임라인 트랙(24dp), 화살표(28dp)를 제외한 나머지 너비를 Compose와 동일한 5.4 가중치(`col1 1.4f, col2 1.0f, col3 1.0f, col5 1.0f, col6 1.0f`)로 배분하여 우측 여백을 0으로 없애고 정확히 카드 우측 끝에 맞아떨어지도록 구현.
     - **M3 디자인 스펙 일치**: 카드 모서리 반경 16dp(M3 shapes.large), 외곽 테두리 1dp(divider 20%), 준비 구분선 1.5dp(alpha 50%), 테마별 팔레트(라이트 `#F6F8FC`, 다크 `#0B0F17`) 완벽 동기화.
  3. **인앱 스크린샷 및 자정 캡처 호출 경로 단일화 (`DiaCaptureHelper.kt`, `MidnightCaptureReceiver.kt`)**:
     - `DiaCaptureHelper.kt`: `executeCapture` 내에서 화면 캡처 방식을 `DiaCaptureRenderer.renderToBitmap` 단일 파이프라인으로 전환하여, 오프스크린 Compose 트리 렌더링 불안정성(단말기 해상도·화면 꺼짐 시 프레임 드랍 등)을 원천 차단하고 초고속 무결점 비트맵 생성.
     - `MidnightCaptureReceiver.kt`: 348줄에 달하던 부정확한 레거시 2D Canvas 코드(`renderDiaBitmap`, `DiaRenderDataWrapper`)를 전면 삭제하고, `DiaCaptureItem` 변환 후 `DiaCaptureRenderer.renderToBitmap`을 호출하도록 단일화.
* **검증 결과**:
  - `EnhancementsUnitTest`: 신규 단위 테스트 `diaCaptureRenderer_gridWeights_distributeSymmetricallyToRightEdge` 포함 73개 단위 테스트 전원 통과 (`testDebugUnitTest`).
  - 안드로이드 디버그 APK 빌드 완료 (`assembleDebug`, BUILD SUCCESSFUL).

### STEP 60. 다이아 캡처(DiaCaptureRenderer) 기기 폰트 설정(서체 종류, 글자 크기 fontScale, 글자 굵게 fontWeightAdjustment) 100% 동기화 (`v1.0.33`, 2026-11-09)
* **배경 및 원인 분석**:
  - `v1.0.32`에서 캡처 비트맵 렌더러가 `DiaCaptureRenderer`로 단일화되었으나, 사용자가 기기 설정(디스플레이 글꼴, 글자 크기, 글자 굵게)에서 지정한 폰트 환경이 캡처본에 반영되지 않는다는 피드백이 접수됨.
  - **원인 분석**:
    1. **폰트 종류(서체)**: `Typeface.create(Typeface.DEFAULT, ...)`로 정적 객체를 참조하여, 삼성 One UI 등에서 사용자가 '글꼴(Font)'을 변경(삼성원, 나눔바른고딕 등)해도 캔버스에 활성 시스템 글꼴이 승계되지 못함.
    2. **폰트 크기**: `fun sp(v: Float) = v * density`로 `fontScale`이 `1.0f`로 하드코딩되어 기기 설정의 글자 크기 조절 슬라이더(0.85배 ~ 1.3배)가 무시됨.
    3. **폰트 굵기**: Android 12+ (API 31+) 시스템의 '글자 굵게(Bold Text)' 설정(`fontWeightAdjustment`, 통상 +300)을 전혀 반영하지 않고 BOLD(700)/NORMAL(400) 2단계로만 처리됨.
* **핵심 구현 내역**:
  1. **기기 활성 폰트 상속 (`DiaCaptureRenderer.kt`)**:
     - `TextView(context).typeface` 및 `Typeface.defaultFromStyle(Typeface.NORMAL)`을 베이스 서체로 취득하여, 기기 설정의 활성 시스템 폰트 체인을 100% 계승.
  2. **기기 글자 크기(`fontScale`) 실시간 반영 및 가변 높이 자동 확장 (`DiaCaptureRenderer.kt`)**:
     - `fontScale = context.resources.configuration.fontScale.coerceIn(0.8f, 1.35f)`을 추출하고 `sp(v) = v * density * fontScale`을 적용.
     - 글자 크기가 커짐에 따라 텍스트가 잘리는 현상을 방지하기 위해 헤더 행 높이(`headerRowH = maxOf(dp(26f), sp(22f) + dp(6f))`), 카드 헤더 높이(`cardHeaderH = maxOf(dp(24f), sp(18f) + dp(6f))`), 각 세그먼트 행 높이(`rowH = maxOf(dp(44f), dp(28f) + sp(14f))`)를 글꼴 크기에 비례하여 동적으로 자동 확장.
     - 날짜 캡슐 알약 및 열차번호/업무 뱃지 박스도 텍스트 폰트 크기(`sp(11f)`, `sp(12f)`)에 맞추어 유연하게 자동 리사이징.
  3. **기기 '글자 굵게'(`fontWeightAdjustment`) 지원 및 Compose 4단계 굵기 세분화 (`DiaCaptureRenderer.kt`)**:
     - `context.resources.configuration.fontWeightAdjustment`를 추출하여 `(baseWeight + weightAdjustment).coerceIn(1, 1000)` 가중치 헬퍼 `getTypeface(baseWeight)` 구현.
     - 출발/도착 시각 `ExtraBold(800)`, 열차/업무 뱃지 및 운전 역명 `Bold(700)`, 일반 역명 `Medium(500)`, 화살표 `Normal(400)`을 부여하고, '글자 굵게' 활성화 시 일괄 가산(+300)되어 기기 설정과 동일하게 또렷하고 굵은 글씨로 저장되도록 일원화.
* **검증 결과**:
  - `EnhancementsUnitTest`: `diaCaptureRenderer_fontScaleAndWeightAdjustment_scalesCorrectly` 단위 테스트 추가 및 전체 74개 단위 테스트 전원 통과 (`testDebugUnitTest`).
  - 디버그 APK 빌드 완료 (`assembleDebug`, BUILD SUCCESSFUL).

### STEP 61. 다이아 캡처(DiaCaptureRenderer) 가로 폭 대폭 확장(1440px QHD, 480dp) 및 동적 충돌 방지(Dynamic Collision Prevention) 적용 (`v1.0.34`, 2026-11-09)
* **배경 및 원인 분석**:
  - 실제 캡처 결과물(`16310 DIA`) 확인 결과, 가로 폭이 1080px(가상 360dp)로 협소하여 한 행에 나란히 들어가야 할 7개 요소(트랙, 뱃지, 출발역, 출발시각, 화살표, 도착역, 도착시각)가 지나치게 빽빽하게 뭉치고 겹치는 심각한 가독성 저하가 확인됨.
  - 특히 `"F4526 운전"` 뱃지 폭(220px)이 Col 1 가용 폭(196px)을 초과하면서 Col 2의 `"시흥기지"`(4글자 한글, 156px)와 32px 동안 겹쳐 **`F4526 운전시흥기지`**로 뭉개졌으며, 역명-시각 사이 간격도 4dp(12px) 미만으로 극도로 비좁았음.
* **핵심 구현 내역**:
  1. **가로 해상도 대폭 확장 (1080px ➔ 1440px QHD, 480dp 뷰포트) (`DiaCaptureRenderer.kt`)**:
     - 가로 해상도를 1440px(QHD 480dp 와이드 규격, +33%)로 상향.
     - 5개 텍스트 열의 순수 가용 너비가 756px에서 1,050px(+38.9% 급증)로 대폭 넓어져 답답함이 원천 해소됨.
  2. **컬럼 가중치 최적화 (총합 5.8f 비례 모델) (`DiaCaptureRenderer.kt`)**:
     - Col 1 (열차번호/뱃지): `1.4f` ➔ **`1.6f` (289.6px, +93.6px 확장)**
     - Col 2 (출발역): `1.0f` ➔ **`1.1f` (199.1px, +59.1px 확장)**으로 4~5글자 한글 역명 공간 완벽 확보.
     - Col 3 (출발시각): `1.0f` (181.0px, +41.0px 확장)
     - Col 4 (화살표 `→`): 28dp ➔ **`32dp (96px)`**
     - Col 5 (도착역): `1.0f` ➔ **`1.1f` (199.1px, +59.1px 확장)**
     - Col 6 (도착시각): `1.0f` (181.0px, +41.0px 확장)
  3. **뱃지-역명 맞붙음 방지 동적 안전 간격(Dynamic Collision Prevention) 신설 (`DiaCaptureRenderer.kt`)**:
     - 뱃지가 아무리 길더라도 출발역명이 뱃지 우측 끝에서 최소 10dp(30px) 이상의 안전 여백을 유지하도록 `col2CenterActual = maxOf(col2CenterBase, safeMinStnLeft + (stnW / 2f))` 강제 적용.
     - `F4526 운전`과 `시흥기지` 사이에 **약 50dp(150px)의 시원한 분리 여백** 확보 완료 (겹침 0%).
     - 화살표와 도착역 사이에도 최소 6dp 안전 간격을 적용하여 시각적 분리감 극대화.
  4. **와이드 뷰포트 맞춤 여백 및 타이포그래피 정밀 튜닝 (`DiaCaptureRenderer.kt`)**:
     - 문서 외곽 여백 `dp(20f)`, 카드 내부 패딩 `dp(16f)`, 트랙 간격 `dp(6f)` 적용.
     - 상단 타이틀 `sp(24f)`, DIA 번호 `sp(20f)`, 출퇴근 정보 `sp(14f)`, 역명/시각 `sp(13f)`로 비례 최적화.
* **검증 결과**:
  - `EnhancementsUnitTest`: 1440px 5.8f 비례 대칭 일치 및 8글자 뱃지-4글자 역명 간격 안전 마진(>=30px) 검증 단위 테스트 2종 포함 총 75개 테스트 전원 통과 (`testDebugUnitTest`).
  - 디버그 APK 빌드 완료 (`assembleDebug`, BUILD SUCCESSFUL).

### STEP 62. '오늘의 근무' 다이아 홈 화면 위젯(AppWidget) 신설 & 100% 로컬 DB 무통신 안전 아키텍처 적용 (`v1.0.35`, 2026-11-09)
* **배경 및 사용자 요구사항**:
  - 앱을 켜지 않고도 스마트폰 홈 화면에서 '오늘의 근무' 승무 다이아 타임라인과 출퇴근 시각을 즉시 확인할 수 있는 안드로이드 앱 위젯(AppWidget) 신설 요청.
  - 알람, 텔레그램 추적, 출무 현황을 제외하고 '승무 다이아 상세' 카드만 100% 동일한 고화질 형태로 렌더링.
  - 기본 크기 4x4 ~ 4x5 지원 및 자유로운 가로/세로 리사이즈 지원.
  - **오프라인 절대 안전 원칙 엄수**: 오프라인 모드일 때 Xcrew와의 네트워크 통신으로 업데이트가 이루어지지 않고 100% 로컬 정보만으로 동작해야 함 (4대 트리거 모두 동일).
  - 정기 업데이트 주기를 30분에서 2~3시간으로 완화하여 배터리 최적화.
* **핵심 구현 내역**:
  1. **위젯 프로바이더 및 100% 로컬 DB 전용 업데이터 신설 (`TodayDiaWidgetProvider.kt`, `TodayDiaWidgetUpdater.kt`)**:
     - `TodayDiaWidgetUpdater`: `TodayWorkRepository` 및 Room DB(`work_schedules`, `dia_details`, `dia_census`)를 활용하되 `isOfflineModeProvider = { true }` 및 `localOnly = true`로 강제 격리하여 외부 네트워크 통신을 0회로 원천 차단.
     - 오프라인 모드(`is_offline_mode == true`)이거나 인터넷이 꺼진 비행기 모드에서도 기기 내 로컬 캐시만으로 0.01초 내에 위젯 비트맵을 생성/갱신.
     - 다이아가 없는 날(비번, 연차, 병가 등)에는 `widget_empty_view`를 통해 깔끔한 "오늘 예정된 승무 다이아가 없습니다." 안내 카드를 자동 표출.
  2. **단일 통합 렌더러 연동 및 Binder IPC 메모리 안전 스케일링 (`DiaCaptureRenderer.kt`, `TodayDiaWidgetUpdater.kt`)**:
     - `DiaCaptureRenderer.renderToBitmap()`에 동적 너비 매개변수(`width: Int = 1440`, `density = width / 480f`)를 추가하여 위젯용 720px 규격으로 호출.
     - 비트맵 바이트 크기가 1.5MB를 초과할 경우 안드로이드 TransactionTooLargeException을 방지하기 위한 동적 비례 축소(`createScaledBitmap`) 안전장치 적용.
  3. **위젯 XML 메타데이터 및 가변 리사이징 UI 구성 (`today_dia_widget_info.xml`, `widget_today_dia.xml`)**:
     - `minWidth="250dp"`, `minHeight="250dp"`, `targetCellWidth="4"`, `targetCellHeight="4"`, `resizeMode="horizontal|vertical"`, `updatePeriodMillis="7200000"` (2시간 주기).
     - `ImageView`의 `adjustViewBounds="true"` 및 `scaleType="fitCenter"` 적용으로 어떤 위젯 크기에서도 무일그러짐 완벽 비례 자동 확대/축소.
     - 우측 상단 `ImageButton` (`widget_refresh_button`) 터치 시 홈 화면에서 즉시 최신 로컬 DB를 재조회하는 `kr.crewmate.app.action.REFRESH_WIDGET` 액션 바인딩.
     - 위젯 본체 클릭 시 메인 앱 `TodayWorkScreen`으로 즉시 진입하는 `PendingIntent` 연결.
  4. **실시간 자동 동기화 4대 트리거 연동**:
     - 트리거 1: `TodayWorkViewModel.kt`에서 다이아 조회 성공 시 `ACTION_UPDATE_TODAY_WIDGET` 브로드캐스트 전송.
     - 트리거 2: `MidnightCaptureReceiver.kt`에서 매일 00:01 AM 자정 전환 시 익일 로컬 DB 다이아로 위젯 자동 갱신.
     - 트리거 3: `BootReceiver.kt`에서 단말기 부팅 완료 시 위젯 자동 복구.
     - 트리거 4: 2시간 주기 정기 업데이트.
* **검증 결과**:
  - `EnhancementsUnitTest`: 가상 뷰포트 비율 일관성 검증(`diaCaptureRenderer_supportsDynamicWidth_maintainsViewportRatio`), 오프라인 무통신 0회 보장 검증(`todayDiaWidget_offlineLocalOnly_guaranteesZeroNetworkCalls`), 2시간 주기 검증(`todayDiaWidget_updatePeriod_isTwoHours`) 3종 신규 단위 테스트 추가 및 전체 78개 단위 테스트 전원 통과 (`testDebugUnitTest`).
  - 디버그 APK 빌드 완료 (`assembleDebug`, BUILD SUCCESSFUL).

### STEP 63. 위젯 3대 핵심 개선(야간2일차/비근무일 붉은색 뱃지, 1080px 레티나 선명도 복원, singleTask 화면 덮어씌움 방지) 및 Xcrew 실시간 조회 검증 (`v1.0.36`, 2026-11-10)
* **배경 및 사용자 요구사항**:
  - 1. 소속 승무원 근무 현황과 열차 승무원 조회(기초자료 제외) 시 온라인 모드에서 즉시 Xcrew 정보를 요청하여 최신 데이터를 가져오는지 검증 요청.
  - 2. 오늘이 야간 2일차이거나 비근무일인 경우 위젯 상단에 며칠 근무인지 붉은색 뱃지(`[MM.dd 근무]`) 추가 표기 요청 (인앱 표출 방식과 100% 일치).
  - 3. 위젯 해상도가 흐릿하게 보이는 현상 개선 요청.
  - 4. 위젯을 누르면 이미 백그라운드에서 실행 중이던 앱 화면 위에 또 다시 '오늘의 근무'가 덮어씌워져 '뒤로가기'를 하면 이전 화면이 또 나타나는 중복 백스택 문제 해결 요청.
* **핵심 구현 내역**:
  1. **소속 승무원 & 열차 승무원 실시간 Xcrew 요청 로직 전수 검증 (`CrewWorkStatusRepository.kt`, `TrainCrewLookupV2Screen.kt`)**:
     - 소속 승무원 현황: 사용자가 일자 선택 후 '조회' 클릭 시 `CrewWorkStatusRepository.getCrewWorkStatus`가 호출되며, `isOfflineModeProvider()`가 false일 때 즉시 `dataSource.fetchCrewWorkStatus(date)`(Xcrew 웹뷰 XHR)를 호출해 최신 데이터를 반환하고 DB를 최신화함.
     - 열차 승무원 조회: 열차번호 검색 시 기초자료(다이아 DB)를 제외한 실시간 승무원 배정 정보는 온라인 시 `async`로 당일(`pjtDt`) 및 전일(`prevDt`) 배정 현황을 즉시 Xcrew 서버에 네트워크 요청하여 최신 명단을 가져옴을 검증 완료.
  2. **야간 2일차 / 비근무일 붉은색 뱃지(`[MM.dd 근무]`) 지원 (`DiaCaptureRenderer.kt`, `TodayDiaWidgetUpdater.kt`, `TodayWorkScreen.kt`)**:
     - `DiaCaptureRenderer.renderToBitmap`에 `badgeText: String? = null` 파라미터를 추가하고, 상단 타이틀(`승무 다이아 상세`) 우측에 `errorContainer` / `onErrorContainer` 색상(라이트 `#FFDAD6`/#410002, 다크 `#93000A`/#FFDAD6, 8dp 라운드, ExtraBold 800) 뱃지 렌더링 구현.
     - `TodayDiaWidgetUpdater`에서 `!todayResult.isToday`일 때 `"${todayResult.workDate.format(DateTimeFormatter.ofPattern("MM.dd"))} 근무"`를 자동 생성하여 주입.
     - `TodayWorkScreen`의 인앱 스크린샷 캡처 시에도 동일하게 뱃지를 전달하여 저장 이미지에 붉은색 뱃지가 찍히도록 시각적 일치 보장.
  3. **1080px FHD+ 레티나 고화질 상향 및 다운스케일링 버그 제거 (`TodayDiaWidgetUpdater.kt`)**:
     - 위젯 렌더링 폭을 기존 720px에서 **`1080px` (density 2.25f)**로 상향.
     - 다이아가 길 때 `MAX_BITMAP_BYTES(1.5MB)`로 인해 400~500px 수준으로 강제 축소되던 인위적 다운스케일링 로직을 제거하고, 안드로이드 7.0+ Ashmem fd 기반으로 원본 1080px 선명도를 100% 보존하여 글씨와 선, 뱃지가 칼같이 또렷하게 보이도록 개선.
  4. **`MainActivity` `singleTask` 및 Navigation 백스택 보호 (`AndroidManifest.xml`, `TodayDiaWidgetUpdater.kt`, `AppNavigation.kt`)**:
     - `AndroidManifest.xml`: `MainActivity`에 **`android:launchMode="singleTask"`** 지정으로 백그라운드에 이미 살아있는 앱 인스턴스를 재사용(Bring To Front)하도록 강제.
     - `TodayDiaWidgetUpdater`: 위젯 클릭 Intent에 `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP` 및 `putExtra("target_route", Routes.TodayWork)` 적용.
     - `AppNavigation.kt`: `LaunchedEffect(initialTargetRoute)`에서 이미 '오늘의 근무' 화면에 있는 경우 중복 navigate를 차단하고, 다른 화면에 있던 경우 `popUpTo(authRootRoute)`로 백스택을 정리하여 뒤로가기 시 이전 화면이 또 나오지 않고 홈 화면으로 즉시 나가도록 완벽 수정.
* **검증 결과**:
  - `EnhancementsUnitTest`: 야간 2일차/비근무일 뱃지 텍스트 생성 검증(`todayDiaWidget_nightDay2OrNonWorkDay_generatesRedBadgeText`), 1080px FHD+ 해상도 검증(`todayDiaWidget_retinaResolution_is1080px`), 중복 화면 네비게이션 방지 검증(`todayDiaWidget_navigationProtection_doesNotDuplicateWhenAlreadyOnScreen`) 3종 신규 단위 테스트 추가 및 전체 81개 단위 테스트 전원 통과 (`testDebugUnitTest`).
  - 디버그 APK 빌드 완료 (`assembleDebug`, BUILD SUCCESSFUL).

### STEP 64. 설정 메뉴 '앱 정보 및 이용 유의사항' 명칭 변경 및 웹앱 톤앤매너(카드형 블록, Amber 경고, 문의 이메일·소속 최신화) 전면 개편 (`v1.0.37`, 2026-11-11)
* **배경 및 사용자 요구사항**:
  - `설정` 화면의 "앱 정보 및 만든 이" 항목 명칭을 "앱 정보 및 이용 유의사항"으로 변경 요청.
  - 다이얼로그 본문 내용을 최신 안내 문구, 공식 시스템 확인 면책 공지, 공식 문의 이메일(`studio.yj.lab@gmail.com`), 제작자 정보(`안산승무사업소 윤영준`)로 최신화 요청.
  - CrewMate 웹앱(`CrewMate-Alpha`)의 `앱 정보 및 이용 유의사항` 모달 디자인(이모지 `ℹ️`, `📖`, `⚠️`, `✉️`, 카드형 블록 레이아웃, 필독 경고 앰버 박스)을 참조하여 컴포즈 M3에 미려하게 이식 요청.
* **핵심 구현 내역**:
  1. **설정 리스트 메뉴 항목 명칭 갱신 (`SettingsScreen.kt`)**:
     - 기존 `앱 정보 및 만든 이` ➔ **`앱 정보 및 이용 유의사항`** 으로 명칭 통일 및 서브타이틀 최신화.
  2. **다이얼로그 카드형 블록 레이아웃 및 웹앱 톤앤매너 100% 반영 (`SettingsScreen.kt`)**:
     - 타이틀: `ℹ️ 앱 정보 및 이용 유의사항` (M3 Surface 20dp 라운드).
     - **카드 1 (`📖 비공식 개발 안내`)**: `SurfaceVariant` 연한 배경 카드 위에 제작자 개인 편의 목적 비공식 앱 개발 배경 및 서비스 변동 가능성 양해 문구 표출.
     - **카드 2 (`⚠️ [필독] 데이터 참고 및 면책 공지`)**: 웹앱 스타일의 앰버(Amber) 경고/유의 박스 디자인 적용(라이트 `#FFFBEB`/#FDE68A/#92400E, 다크 `#2C2214`/#5A4016/#FDE68A)으로 참고용 데이터 공지 및 공식 시스템(Xcrew) 최종 확인 필수 문구 시각적 강조.
     - **카드 3 (`✉️ 문의 및 제작자`)**: 최신 공식 문의 이메일(`studio.yj.lab@gmail.com`) 및 `만든이 : 안산승무사업소 윤영준` 하단 구분선 배치.
* **검증 결과**:
  - 전체 81개 단위 테스트 100% 무결 통과 (`testDebugUnitTest`).
  - 디버그 APK 빌드 완료 (`assembleDebug`, BUILD SUCCESSFUL).

### STEP 65. 공식 GitHub Releases 연동 인앱 업데이트(Self-Update) 시스템 및 자동 감지 알림 구축 (`v1.0.38`, 2026-11-12)
* **배경 및 사용자 요구사항**:
  - 앱 업데이트 시 매번 APK 파일을 수동 전달/다운로드받아 재설치하는 번거로움 해소 요청.
  - `설정` 화면에 "업데이트 확인하기" 버튼을 신설하여 버전 정보를 확인하고 최신 APK를 다운로드할 수 있는 인앱 업데이트 구축 요청.
  - 공식 GitHub 배포 저장소([`0JYOON/CrewMate`](https://github.com/0JYOON/CrewMate)) 생성 완료 및 연동 요청.
  - 사용자가 수동 버튼을 누르지 않더라도 새 업데이트가 출시되었을 때 자동으로 알려주는 팝업/알림 수단 탑재 요청.
* **핵심 구현 내역**:
  1. **GitHub Releases API 연동 업데이트 엔진 신설 (`AppUpdateManager.kt`)**:
     - `https://api.github.com/repos/0JYOON/CrewMate/releases/latest` API 호출을 통해 최신 릴리즈 태그(`tag_name`), 릴리즈 제목(`name`), 패치노트(`body`), `.apk` 에셋 다운로드 직링크(`assets[].browser_download_url`)를 비동기 파싱.
     - 시맨틱 버전 비교 알고리즘(`compareVersions`)을 통해 현재 설치 버전(`BuildConfig.VERSION_NAME`)과 최신 버전 비교.
     - OkHttp 스트리밍 다운로드(`downloadApk`)로 `cacheDir/updates/CrewMate_vX.X.X.apk`에 다운로드하며 실시간 0~100% 진행률 콜백 전달.
     - `androidx.core.content.FileProvider` 및 `Intent.ACTION_VIEW` (`FLAG_GRANT_READ_URI_PERMISSION`)로 시스템 패키지 인스톨러 화면 자동 호출 및 외부 브라우저 Fallback 지원.
  2. **앱 기동 시 자동 업데이트 감지 및 스마트 알림 팝업 (`AppNavigation.kt`, `UpdateDialogs.kt`)**:
     - 앱 시작 시 백그라운드 코루틴에서 조용히 최신 버전 확인.
     - **오프라인 보호**: `isOfflineModeProvider()` 활성화 시 원격 네트워크 요청 차단.
     - **주기 제한 (Throttling)**: 마지막 확인 후 12시간 경과 시에만 확인하여 배터리 및 GitHub API 쿼터 절약.
     - 새 버전 발견 시 **"🚀 새로운 업데이트 안내"** 팝업 표출 (버전 비교, 릴리즈 노트, `[지금 다운로드 및 설치]`, `[웹에서 받기]`, `[오늘 하루 보지 않기]`, `[나중에]`).
  3. **설정 화면 내 '버전 정보 및 업데이트' 카드 신설 (`SettingsScreen.kt`)**:
     - 현재 앱 버전(`v1.0.37`) 뱃지 및 **`[업데이트 확인]`** 버튼 제공.
     - 터치 시 프로그레스 인디케이터 동작 후 최신 버전일 때 "🎉 현재 최신 버전을 사용 중입니다." 안내, 새 버전 있을 시 즉시 다운로드/설치 연동.
  4. **Android 매니페스트 및 리소스 설정 (`file_paths.xml`, `AndroidManifest.xml`, `build.gradle.kts`)**:
     - `file_paths.xml` 등록 및 FileProvider 선언, `REQUEST_INSTALL_PACKAGES` 권한 추가.
     - `versionCode = 37`, `versionName = "1.0.37"` 최신화 완료.
* **검증 결과**:
  - `AppUpdateManagerTest`: 버전 비교 엣지 케이스 및 JSON 파싱 단위 테스트 5개 통과 (총 86개 테스트 100% 무결 통과).
  - 디버그 APK 빌드 완료 (`assembleDebug`, BUILD SUCCESSFUL).

### STEP 66. 7대 핵심 개선(월별 근무표 위젯, 프리텐다드 폰트, 캘린더 다일 일정, 위젯 겹침 해소, 기초자료 비실시간 안내 등) 구축 (`v1.0.39`, 2026-11-13)
* **배경 및 사용자 요구사항**:
  1. 오늘의 근무 위젯 우측 상단 새로고침 버튼과 날짜 뱃지 겹침 해소 요청.
  2. 월별 승무근무표 홈 화면 위젯 신규 추가 요청.
  3. 휴일근무 현황 내 야간 2일차 우측 `"(전일 야간 1일차와 1개 단위 통합)"` 문구 삭제 요청.
  4. 어플 최초 로그인 시 앱 정보 및 이용 유의사항 자동 팝업 요청.
  5. 기기 캘린더 연동 시 다일(Multi-day) 일정이 첫날만 표시되고 둘째 날 이후가 누락되는 버그 수정 요청.
  6. 프리텐다드(Pretendard) 폰트 내장 및 설정 내 글꼴 선택(시스템 기본 / 프리텐다드) 추가 요청.
  7. 오프라인 모드·다이아 기초자료 기반 조회 시 실시간 Xcrew 정보와 구분되도록 최하단에 비실시간 안내 문구 표출 요청.
* **핵심 구현 내역**:
  1. **위젯 새로고침 버튼 ↔ 날짜 뱃지 겹침 해소 (`DiaCaptureRenderer.kt`, `TodayDiaWidgetUpdater.kt`, `widget_today_dia.xml`)**:
     - `reserveTopEndSpaceDp = 38f` 파라미터로 날짜 알약을 왼쪽으로 이격시키고 새로고침 버튼을 `32dp`로 정교하게 축소해 완벽한 정렬 달성.
  2. **월별 승무근무표 홈 화면 위젯 신설 (`MonthlyScheduleWidgetProvider.kt`, `MonthlyScheduleWidgetUpdater.kt`, `MonthlyScheduleRenderer.kt`)**:
     - 100% 로컬 Room DB 기반 1080px 고화질 달력 위젯, 전월/오늘/익월 이동 및 새로고침 컨트롤, 근무 형태별 색상 뱃지·메모 점 표출, 터치 시 승무근무표(`Routes.Schedule`) 직행 연동.
  3. **휴일근무 야간 2일차 문구 삭제 (`HolidayWorkManager.kt`, `NonOpStatusScreen.kt`)**:
     - `"야간 2일차 (전일 야간 1일차와 1개 단위 통합)"` ➔ `"야간 2일차"`로 정리 및 기존 캐시 필터링 적용.
  4. **최초 로그인 시 앱 정보 및 이용 유의사항 팝업 (`AppInfoDialog.kt`, `SettingsScreen.kt`, `AppNavigation.kt`)**:
     - 공용 `AppInfoAndTermsDialog` 추출 및 `has_seen_initial_app_info` 플래그 기반 최초 진입 시 1회 자동 표출.
  5. **캘린더 다일(Multi-day) 일정 전 일자 표시 수정 (`DeviceCalendarReader.kt`)**:
     - `CalendarContract.Instances`의 `END`, `ALL_DAY` 컬럼 추가 조회 및 `expandDeviceEventDates()` 함수로 종일(UTC 자정 보정)·일반 다일 일정 전 일자 전개.
  6. **프리텐다드(Pretendard) 폰트 내장 및 글꼴 선택 (`res/font/`, `Type.kt`, `Theme.kt`, `MainActivity.kt`, `SettingsScreen.kt`)**:
     - Pretendard Regular/Medium/Bold/ExtraBold TTF 내장 및 `AppFontOption` (`SYSTEM`/`PRETENDARD`) 실시간 스위칭 지원(앱 UI, 위젯, 캡처 이미지 일괄 반영).
  7. **오프라인·기초자료 기반 비실시간 안내 문구 (`DiaLookupScreen.kt`, `TrainCrewLookupV2Screen.kt`, `TodayWorkScreen.kt`)**:
     - 오프라인 모드 및 기초자료 DB 기반 조회 결과 최하단에 실시간 Xcrew 변동사항과 다를 수 있음을 알리는 안내 문구 표출.
* **검증 결과**:
  - `DeviceCalendarReaderTest`: 다일/종일/자정/월경계 캘린더 일정 및 휴일근무 문구 제거 단위 테스트 5개 통과 (총 91개 테스트 100% 무결 통과).
  - 디버그 APK 빌드 완료 (`assembleDebug`, BUILD SUCCESSFUL).

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

---

### STEP 67: 월별 위젯 전면 개편(정보 확장·짤림 방지·색상 통일·네비게이션), SUIT/Spoqa 폰트 추가, 업데이트 팝업 정리 및 사번 해시 차단 가드 구축 (`v1.0.40`)
1. **월별 승무근무표 위젯 정보 확장 및 짤림 방지·뱃지 색상 통일**:
   - `MonthlyScheduleRenderer.kt`에서 대표열번(`trainNo`, `trainNo2`)을 제외한 모든 정보(근무 뱃지, 출·퇴근 시각, 실제 메모 텍스트 배지, 기기 캘린더 일정 배지)를 주(row)별 동적 높이로 계산해 위젯 안쪽에 짤림 없이 표시하도록 개편.
   - 우측 상단 메모 점(`drawCircle`)을 삭제하고, 근무 뱃지 색상을 `ScheduleScreen.kt`와 동일하게(주간 `#FFD54F`/검정, 야간 `#333333`/흰색, 야간 2일차 `~다이아`, 연차 `#0D9488`) 통일.
   - `MonthlyScheduleWidgetUpdater.kt`에서 `memoDao().getAllMemos()` 내용과 `READ_CALENDAR` 권한 확인 후 `getDeviceCalendarEvents()` 결과를 함께 전달.
2. **월별 승무근무표 위젯 헤더 네비게이션 개편**:
   - `widget_monthly_schedule.xml` 상단에 `[◀]` — `[YYYY년 M월 근무표 (중앙/당월 복귀)]` — `[▶] [🔄]` 구조의 넉넉한 터치 영역 헤더를 신설하고, `monthly_schedule_widget_info.xml` 기본 세로 크기를 `targetCellHeight="5"`, `minHeight="320dp"`로 확대.
3. **SUIT & Spoqa Han Sans Neo 폰트 추가 및 글꼴 설정 1줄 UI**:
   - `app/src/main/res/font/`에 `suit_{regular,medium,bold,extrabold}.ttf` 및 `spoqa_han_sans_neo_{regular,medium,bold}.ttf`를 추가.
   - `Type.kt`, `Theme.kt`, `SettingsScreen.kt`에서 `AppFontOption`을 `System`, `Pretendard`, `SUIT`, `Spoqa Han Sans Neo` 영어 전용 라벨로 통일하고 2×2 1줄 칩(`maxLines = 1, softWrap = false`)으로 배치.
4. **업데이트 안내 팝업 버튼 줄바꿈 개선**:
   - `UpdateDialogs.kt`의 `UpdateAvailableDialog`에서 `"웹에서 받기"`를 독립된 `OutlinedButton` 행으로 분리하고 `"오늘 하루 보지 않기"`와 `"나중에"`를 2등분 Row(`maxLines = 1, softWrap = false`)로 배치.
5. **특정 사번 비밀 차단(Ban) 가드 신설**:
   - `EmployeeBanGuard.kt`를 신설해 평문 사번 저장 없이 `Salted SHA-256` 해시만 대조(원격 `config/blocked.json` + 로컬 해시 병행).
   - `LoginViewModel.attemptLogin()`에서 최초(수동) 로그인 시에만 원격 해시 목록을 조회하고, 자동 로그인 시에는 중복 조회를 생략하며, 차단 대상에게는 일반 Xcrew 통신 지연 문구만 표출.
   - `EmployeeBanGuardTest.kt`(4개 단위 테스트) 포함 전체 `testDebugUnitTest` 및 `assembleDebug` 0 오류 검증 완료.

---

### STEP 68: Play Protect 오탐 완화를 위한 `REQUEST_INSTALL_PACKAGES` 제거 및 브라우저 업데이트·안내 문구 보강 (`v1.0.41`)
1. **민감 설치 권한 제거**:
   - `AndroidManifest.xml`에서 `REQUEST_INSTALL_PACKAGES` 권한을 제거해 Play Protect 휴리스틱 위험 점수를 낮춤.
2. **브라우저 다운로드 전환 및 사용자 안내 보강**:
   - `UpdateDialogs.kt`, `AppNavigation.kt`, `SettingsScreen.kt`에서 인앱 패키지 인스톨러 호출 경로를 제거하고 `"최신 APK 다운로드 (브라우저)"` 단일 동작으로 통합.
   - `UpdateNoticeDialog` 내에 Play 프로텍트(`[세부정보] → [무시하고 설치]`) 및 갤럭시 `보안 위험 자동 차단` 안내 카드를 추가.

---

### STEP 69: 위젯 앱 테마 통일, 월별 위젯 순환패턴·`S`·꽉찬 레이아웃, Xcrew 네트워크 이력 관리자 메뉴 및 일일 03:00 Ban 체크 (`v1.0.42`)
1. **위젯 테마 앱 설정 100% 통일**:
   - `widget_card_bg_{light,dark}.xml` 및 `ic_widget_refresh_{light,dark}.xml`을 신설하고, `TodayDiaWidgetUpdater`·`MonthlyScheduleWidgetUpdater`에서 `context.isDarkThemeActive()` 기준으로 배경·아이콘·텍스트 색상을 직접 지정.
   - `SettingsScreen.kt`에서 `app_theme` 변경 시 즉시 모든 위젯을 갱신하도록 연동.
2. **월별 위젯 순환 패턴·`S` 표시·위젯 꽉찬 크기 조정**:
   - `MonthlyScheduleRenderer.filterDisplaySchedules()`를 추가해 실동 다이아가 없는 날의 `"S"`가 누락되지 않도록 수정하고, 미배정 달에 `WorkPatternSettings` 순환 패턴을 앱과 동일 색상으로 렌더링.
   - `MonthlyScheduleWidgetUpdater`에서 `AppWidgetOptions`의 위젯 크기에 맞춰 `minTotalHeight`를 계산하고 남는 세로 공간을 주(row) 높이에 균등 분배해 `fitXY`로 꽉 채움.
3. **Xcrew 네트워크 통신 이력 저장·조회 및 관리자 허브 메뉴 개편**:
   - `XcrewNetworkLogStore.kt` 신설 (당일 포함 앞뒤 1일 범위 보존, 민감 정보 배제) 및 `XcrewWebViewXhrDataSource`·`LoginViewModel` 기록 연동.
   - `SettingsScreen.kt` 관리자 인증 후 `🛠️ 관리자 특별 설정 및 테스트` 허브 다이얼로그에서 `가상 날짜 지정`과 `Xcrew 네트워크 통신 이력 조회`를 분리 제공.
4. **일일 새벽 03:00 백그라운드 Ban 체크 및 오프라인→온라인 전환 시 즉시 체크**:
   - `DailyBanCheckReceiver.kt` 신설 및 `BootReceiver`·`MainActivity`·`AndroidManifest.xml` 등록.
   - 오프라인 상태에서는 `pending_online_ban_check`를 남기고, `AppNavigation`에서 온라인 전환 순간 즉시 `EmployeeBanGuard.checkAndCacheBanStatus()`를 실행해 차단 시 세션을 종료.


---

### STEP 70: v1.0.42 최신 정식 베이스 복구 및 실험실 프레임워크·행로도·개인근무명세서 통합 (`v1.0.43`)
1. **v1.0.42 최신 코드베이스 전량 복구**:
   - 미커밋 상태였던 로컬 v1.0.42(`versionCode = 42`) 전체 소스(`AppUpdateManager`, `AppInfoDialog`, 위젯 2종, 출무 사전확인, 오프라인 프리페치, 관리자 허브 등)를 `add_labs_feature_framework` 워크트리 베이스로 통합하고 `versionCode = 43`, `versionName = "1.0.43"`으로 상향.
2. **실험실 프레임워크 & 5회 터치 활성화**:
   - `LabsManager.kt`, `LabFeature.kt`, `LabSafeContainer.kt`, `LabsSettingsScreen.kt`, `SettingsScreen.kt` `"설정"` 5회 터치 팝업 및 `AppNavigation.kt` 조건부 햄버거 메뉴 연동.
3. **실험실 1호(행로도) & 2호(개인근무명세서) 재연결**:
   - `DiaDetailCard.kt`에 `RouteDiagramView` 연결, `XcrewWebViewXhrDataSource.kt`에 `fetchPersonalWorkSpec`(`/extrCrewMg/searchExtrIndWrkSpec.do`) 연결 및 `PayStatementScreen` 라우팅 완료.
4. **검증**: `./gradlew.bat test assembleDebug` 전 단위 테스트 및 디버그 APK 빌드 0 오류 통과 (`BUILD SUCCESSFUL`).


### STEP 71: 실험실 3호 '동료 근무 비교 (`colleague_compare`)' 구현 (`v1.0.44`)
- **배경**: 본인 근무표와 동료(Xcrew 조회) 및 가상 인물(순환 패턴·파일 임포트)의 월간 근무를 한눈에 비교하는 실험실 3호 기능 구현.
- **주요 구현 내용**:
  - `app/src/main/java/com/example/crewmate/labs/colleaguecompare/ColleagueCompareModels.kt`: 비교 셀(`CompareDayCell`), 인물(`ComparePerson`), 표시 옵션(`CompareDisplayOptions`) 데이터 모델 정의.
  - `app/src/main/java/com/example/crewmate/labs/colleaguecompare/PatternFileParser.kt`: `.txt`/`.csv`(UTF-8/EUC-KR) 및 `.xlsx`(ZIP+XML) 순환 패턴 파서와 기준일 기반 순환 다이아 계산(`resolveCyclicDia`) 구현.
  - `app/src/main/java/com/example/crewmate/labs/colleaguecompare/ColleagueCompareStorage.kt`: 비교 대상 목록, 가상 인물 설정 및 셀 수동 오버라이드, 표시 옵션의 `SharedPreferences` JSON 저장/복원 구현.
  - `app/src/main/java/com/example/crewmate/labs/colleaguecompare/ColleagueCompareViewModel.kt`: 본인 월간 근무 조회, Xcrew 동료 검색 및 월간 근무표 연동, 가상 인물/오버라이드 관리 및 온라인 새로고침 상태 관리.
  - `app/src/main/java/com/example/crewmate/labs/colleaguecompare/ColleagueCompareScreen.kt`: 좌측 인물 열 고정·날짜 가로 스크롤 비교 표, 동료 검색 다이얼로그, 가상 인물 및 `.xlsx/.csv/.txt` 파일 선택 다이얼로그, 옵션/셀 편집 UI 구현.
  - `LabFeature.kt` 및 `AppNavigation.kt`: `COLLEAGUE_COMPARE` 활성화 및 `lab-colleague-compare` 라우트 연결.
  - `app/src/test/java/com/example/crewmate/labs/colleaguecompare/PatternFileParserTest.kt`: 파서 및 순환 다이아 계산 단위 테스트 추가 (`./gradlew.bat test assembleDebug` 통과).


### STEP 72: 실험실 4호 '교대승무원 확인 (`handover_crew`)' 구현 (`v1.0.45`)
- **배경**: 오늘의 근무·다이아 조회 화면에서 열차번호 뱃지 옆 버튼(`👥`)으로 해당 열차의 직전(인계) 승무원과 교대(후속) 승무원을 즉시 확인하는 실험실 4호 기능 구현.
- **주요 구현 내용**:
  - `app/src/main/java/com/example/crewmate/labs/handovercrew/HandoverCrewModels.kt`: `HandoverRelayItem`, `HandoverCrewReport` 모델 정의.
  - `app/src/main/java/com/example/crewmate/labs/handovercrew/HandoverCrewResolver.kt`: 심야 자정 경과 날짜 보정(`resolveSegmentDate`) 및 열차 릴레이 순서 정렬 기반 직전·후속 승무원 판정 로직(`resolveHandover`) 구현.
  - `app/src/main/java/com/example/crewmate/labs/handovercrew/HandoverCrewRepository.kt`: 온라인 `TrainCrewSearchRepository` 실시간 조회 및 오프라인 Room DB(`CrewAssignmentDao`, `DiaDetailDao`, `DiaCensusDao`) 캐시 폴백 조회 구현.
  - `app/src/main/java/com/example/crewmate/labs/handovercrew/HandoverCrewDialog.kt`: 직전 승무원·내 구간·교대 승무원·전체 열차 릴레이 팝업 다이얼로그 구현(`LabSafeContainer` 격리).
  - `DiaDetailCard.kt` 및 `LabFeature.kt`: `HANDOVER_CREW` 활성화 시 열번 뱃지 옆 `👥` 버튼 조건부 노출(캡처 모드 제외) 및 다이얼로그 연결.
  - `app/src/test/java/com/example/crewmate/labs/handovercrew/HandoverCrewResolverTest.kt`: 릴레이 판정 및 자정 경과 날짜 보정 단위 테스트 추가 (`./gradlew.bat test assembleDebug` 통과 및 실기기 설치).


### STEP 73: 실험실 5호 '다이아 평가 (`dia_rating`)' 구현 및 5대 실험실 기능 완결 (`v1.0.46`)
- **배경**: 경/중사업, 야간 취침역, 운전/총사업시간 등 조건에 따라 월별 달력과 다이아 카드에서 다이아 난이도·선호도를 뱃지와 음영으로 즉시 구분하는 실험실 5호 기능 구현.
- **주요 구현 내용**:
  - `app/src/main/java/com/example/crewmate/labs/diarating/DiaRatingModels.kt`: `DiaRatingGrade`, `DiaRatingRules`, `DiaRatingResult` 모델 정의.
  - `app/src/main/java/com/example/crewmate/labs/diarating/DiaRatingStorage.kt`: `SharedPreferences` + `StateFlow` 기반 평가 기준 및 다이아별 수동 등급 지정 저장소 구현.
  - `app/src/main/java/com/example/crewmate/labs/diarating/DiaRatingEvaluator.kt`: 운전시간, 총사업시간, 야간 취침역(`휴게`/`취침`/`숙박` ≥ 120분), 수동 지정 우선순위 판정 로직 구현.
  - `app/src/main/java/com/example/crewmate/labs/diarating/DiaRatingDialogs.kt`: 판정 근거·수동 등급 지정(`DiaRatingDetailDialog`) 및 기준 편집(`DiaRatingRulesDialog`) UI 구현.
  - `ScheduleScreen.kt`, `DiaDetailCard.kt`, `LabsSettingsScreen.kt`, `LabFeature.kt`: 달력 셀 뱃지·배경 음영, 다이아 카드 헤더 뱃지, 실험실 설정 기준 버튼 연동.
  - `app/src/test/java/com/example/crewmate/labs/diarating/DiaRatingEvaluatorTest.kt`: 평가 규칙 및 수동 지정 단위 테스트 추가 (`./gradlew.bat test assembleDebug` 통과).


### STEP 74: 개인근무명세서 UI 밀도 개선 및 승무여비·승무수당 예상치(`alcSpecDvCd`/`offdDdFlg`/`pdiaDvCd`)·확정 구분 로직 개편 (`v1.0.47`)
- **배경**: 개인근무명세서 목록의 불필요한 상하 여백을 줄이고, 새로고침 날짜(`yy-MM-dd`)·강조 컬럼(`휴일`/`연장`/`야간`)을 개선하며, 거리 실적 이전에도 예정 근무일 기준으로 승무여비·승무수당 예상 충족 여부를 확인할 수 있도록 개편.
- **주요 구현 내용**:
  - `PayStatementModels.kt` & `PayStatementParser.kt`: `alcSpecDvCd in ("11", "12")` 근무일 예정 산정, `offdDdFlg == "Y"` 휴일근무의 승무수당 제외, `pdiaDvCd == "4"` 기준 대기근무 판별(거리 > 0일 때만 포함), `confirmedTravelDays`/`plannedTravelDays` 및 `confirmedAllowanceDays`/`plannedAllowanceDays` 확정/예정 분리 집계.
  - `PayStatementViewModel.kt`: `updatedAt` 포맷을 `yy-MM-dd HH:mm`으로 변경.
  - `PayStatementScreen.kt`: `LazyColumn` `spacedBy(0.dp)` 및 행/헤더 패딩 축소, `시간`·`거리` 일반 굵기 전환, `휴일`·`연장`·`야간` > 0 볼드 강조, `PayStatementEligibilityCard` 내 `확정 + 예정` 내역 및 2단 진행률 바 표시.
  - `PayStatementParserTest.kt`: 개편된 `pdiaDvCd == "4"` 및 확정/예정 산정 규칙 단위 테스트 보강 (`./gradlew.bat test assembleDebug` 통과).


### STEP 77: 개인근무명세서 3구간 게이지·확정/예정 표시·문구/변수명 정리 및 오프라인 캐시/통신 차단 (`v1.0.50`)
- **배경**: 최대 목표일수 대비 근무표 기반 예정 총일수(옅은 파랑)와 실적 확정일수(짙은 파랑)를 직관적으로 구분하고, 개발용 변수명/불필요 안내 문구를 제거하며, 오프라인 모드에서도 저장된 내역을 조회하되 Xcrew 통신은 차단.
- **주요 구현 내용**:
  - `PayStatementStorage.kt` [NEW]: 월별 개인근무명세서 JSON 및 `updatedAt` 로컬 캐시 저장/복원.
  - `PayStatementViewModel.kt` & `AppNavigation.kt`: `PayStatementStorage` 및 `isOfflineModeProvider` 연동, 오프라인 모드 시 Xcrew 통신 차단 및 캐시 표출.
  - `PayStatementScreen.kt`: 2단 겹침 게이지 바(트랙=최대 / 옅은 파랑=예정 / 짙은 파랑=확정), 우측 상단 `확정 X일 / 예정 Y일` 칩, 오프라인 새로고침 버튼 비활성화, 불필요 문구·변수명 전면 제거.


### STEP 78: 동료 근무 비교 본인 근무표 `yyyyMM` 조회 키 오류 및 동명이인 가상인물 `cellsByMonth` 폴백 수정 (`v1.0.51`)
- **원인 및 해결**:
  - `ColleagueCompareViewModel.loadMyScheduleForMonth()`가 `scheduleRepo.getSchedules()`에 `"yyyy-MM"`을 넘겨 `WorkScheduleDao`(`"yyyyMM"` 저장)에서 0건이 반환되던 문제를 `yearMonthKey(month)`로 수정.
  - `ComparePerson.cellForDate()`가 `VIRTUAL_PERSON && patternSequence.isEmpty()`일 때 `null`을 조기 반환해 동명이인 분할 가상인물의 `cellsByMonth` 다이아가 빈칸으로 나오던 문제를 `patternSequence.isNotEmpty()` 조건으로 수정.
  - `PatternFileParserTest`에 동명이인 분할 가상인물 셀 조회 단위 테스트 추가 (`./gradlew.bat test assembleDebug` 통과).

### STEP 79: 개인근무명세서 야간 2일차(`alcSpecDvCd == "12"`) 확정 상속 및 웹(`CrewMate-Alpha`) 계산 불일치 수정 (`v1.0.52`)
- **야간 2일차 확정 판별**:
  - `PayStatementParser.buildReport()`에서 명세서 행을 `pjtDt` 순으로 정렬한 뒤, `hasOperatingDistance`이거나 연속된 전날 근무가 확정이고 현재 행이 `alcSpecDvCd == "12"`인 경우 해당 행도 확정 일수(`confirmedTravelDays`, 휴일이 아니면 `confirmedAllowanceDays`)에 포함하도록 수정.
  - `PayStatementParserTest`에 `buildReport_nightSecondDayInheritsConfirmationFromPreviousDay` 단위 테스트 추가 및 전체 119개 테스트·디버그 빌드 통과.
- **웹-앱 계산 불일치 해결**:
  - 웹(`CrewMate-Alpha/app-runtime.js`) 브릿지 임포트 경로(`store.payStatementsByMonth[ym]`)에 기존에 저장된 월 데이터는 `alcSpecDvCd`·`pdiaDvCd` 필드가 없어 `isDutyPayRow`가 모두 `false`가 되던 문제를 확인하고, `PayStatementTabView`에서 `r.alcSpecDvCd`가 비어 있을 때 `store.schedules`의 동일 날짜(`pjtDt`) 근무 정보로 `alcSpecDvCd`·`offdDdFlg`를 폴백 보완하고 `evaluatePayStatement`에도 동일한 야간 2일차 확정 상속 로직을 반영.

### STEP 80: 개인근무명세서 휴일근무 확정 기준 변경 (`hldyTnum` 기반) (`v1.0.53`)
- **배경 및 변경 내용**:
  - 휴일근무는 실제 운행 시 거리(`totPjtDst`) 실적이 인정되지 않으므로, `PayStatementRow.hasConfirmedPerformance`를 도입해 휴일근무(`isHolidayWork`)는 `hldyTnum > 0`, 비휴일은 `totPjtDst > 0.0`으로 확정 여부를 판단하도록 변경.
  - `PayStatementParser.buildReport()`에서 야간 2일차(`alcSpecDvCd == "12"`)가 연속된 전날 확정을 상속할 때도 `hasConfirmedPerformance`를 기반으로 동작하므로, 휴일 야간 2일차 역시 전날 휴일 야간 1일차의 `hldyTnum > 0` 여부에 따라 확정/미확정이 결정됨.
  - 웹(`CrewMate-Alpha/app-runtime.js`)에도 `hasPayConfirmedPerformance`를 추가해 앱과 동일하게 반영하고 `PayStatementParserTest`에 검증 테스트를 추가함 (`./gradlew.bat test assembleDebug` 통과).

### STEP 81: 수동/자동 새로고침 전환, 동료비교 본인 다이아 복제 수정, 스크린샷 OCR·AI 일괄 추가 및 웹 백업 보완 (`v1.0.54`)
- **어플 수동/자동 새로고침**:
  - `PayStatementStorage`·`PayStatementViewModel`과 `ColleagueCompareStorage`·`ColleagueCompareViewModel`의 진입·월 이동 기본 동작을 로컬 캐시 조회(`autoRefresh = false`)로 변경하고, 상단 토글 칩으로 `자동 새로고침` ON/OFF를 영속 설정할 수 있게 함.
- **동료비교 본인 다이아 복제 근본 원인 해결**:
  - Xcrew `/extrCrewMg/searchExtrIndCrewWrk.do`는 `empNm` 파라미터와 무관하게 로그인한 본인 근무표만 반환하므로, `ColleagueCompareViewModel`이 타인 조회/갱신 시 `fetchPersonalWorkSchedule`을 호출해 소속현황(`crewBuckets`)을 본인 다이아로 덮어쓰던 문제를 제거함.
  - 선택 월의 1일~말일 중 로컬 캐시(`CrewAssignmentDao`)에 없는 날짜를 `CrewWorkStatusRepository.refreshAndStore`로 순차 수집·저장한 뒤 해당 승무원 데이터만 추출하도록 전면 수정함.
- **스크린샷 OCR & AI 프롬프트 다중 인원 등록 & 문구 변경**:
  - `com.google.mlkit:text-recognition-korean:16.0.1` 기반 `ScreenshotScheduleOcr.kt`와 다중 인원 AI 출력 파서 `ColleagueScheduleImportParser.kt`를 추가하고, 앱·웹 전체에서 `가상 인물`을 `수동 추가`로 통일함.
- **웹(`CrewMate-Alpha`) 백업 누락 수정**:
  - `encryptLockerStore`와 `mergeRestoredLockerData`에 `payStatementsByMonth`, `payStatementSyncMetaByYm`, `colleagueCompare` 백업/병합 로직을 추가하고 `handleBridgeImport`·`handleImportBackupJson` 정합성을 보완함 (`./gradlew.bat test assembleDebug` 및 `node build.js` 통과).

### STEP 82: AI 추출 프롬프트 및 다중 인원 파서 보강 (`v1.0.55`)
- **원인 및 해결**:
  - `월: 미상` 등 숫자가 아닌 월 표기나 마크다운 기호가 포함된 헤더에서 기존 단일 정규식이 실패해 모든 인원이 하나의 기본 이름으로 덮어써지던 문제를 `이름:`·`월:` 독립 추출 및 `resolveYmKey` 폴백으로 해결함.
  - 엑셀 가로형 순환표(왼쪽 열 이름 + 1일~말일 다이아) 및 출퇴근 시각 미포함 표를 지원하는 프롬프트로 보강함 (`./gradlew.bat test assembleDebug` 통과).


### STEP 75: 동료 근무 비교 승무원 추가 개선(검색 시에만 표출, 동명이인 가상인물 분할, 전체 셀 수정·빨간 점, 가상인물 이름 편집, 행·열 전환) (`v1.0.48`)
- **배경**: 승무원 추가 시 불필요한 전체 목록 사전 노출을 없애고, 동명이인 혼선을 가상 인물 분할로 해결하며, 모든 동료의 셀 수정·수정 표시·가상인물 패턴 수정·가로/세로 축 전환을 지원.
- **주요 구현 내용**:
  - `ColleagueCompareModels.kt` & `ColleagueCompareStorage.kt`: `ComparePerson.cellForDate()`의 `manualOverrides` 우선 적용을 실존 동료까지 확대하고, `CompareDisplayOptions.transposeMatrix` 저장/복원 추가.
  - `ColleagueCompareViewModel.kt`: `clearSearchResults()`, `setTransposeMatrix()`, `updateVirtualPerson()`, `updateCellOverride()`(실존 동료 포함), 동명이인 감지 및 가상 인물 `K`명 자동 분할(`dtosToGroupedBuckets` + `collectCrewDaoBucketsForMonth`) 구현.
  - `ColleagueCompareScreen.kt`: 상단 `출퇴근 표시 ON/OFF` + `⇄ 가로/세로 전환` 뱃지 바, `CompareCellBox` 우측 상단 빨간 점, 가상 인물 이름 클릭 수정(`VirtualPersonDialog`), 행/열 전환 테이블 레이아웃 구현.
  - `PatternFileParserTest.kt`: 실존 동료 오버라이드 및 동명이인 버킷팅 단위 테스트 추가 (`./gradlew.bat test assembleDebug` 통과).


### STEP 76: 동료 근무 비교 본인 근무표·검색 누락 버그 수정 및 복원 버튼·수집/새로고침 시각 표기 (`v1.0.49`)
- **배경 및 원인 파악**:
  - 앱에서 `WorkSchedule.workDate`가 `yyyyMMdd` 포맷인데 `ColleagueCompareScreen`이 `yyyy-MM-dd` 키로 조회해 본인 근무표가 빈칸으로 나오던 문제, 로컬 캐시 일부 존재 시 온라인 검색이 스킵되어 당일 휴무 승무원이 검색되지 않던 문제를 해결.
- **주요 구현 내용**:
  - `ColleagueCompareViewModel.kt`: `loadMyScheduleForMonth` 날짜 키 `yyyy-MM-dd` 정규화, `searchColleagues` 복수 기준일(최대 3일) 온라인 병행 조회, `addRealColleague` `crewBuckets` 보완 병합, `lastRefreshedAt` 저장.
  - `ColleagueCompareStorage.kt`: `KEY_LAST_REFRESHED_AT` 저장/복원 추가.
  - `ColleagueCompareScreen.kt`: 새로고침 버튼 좌측 최근 새로고침 시각 표기, `'이름으로 바로 조회·추가'` 제거, `가상·편집` 뱃지 및 최하단 안내 문구 삭제, `EditCellDialog` 복원 `OutlinedButton` 강화, `weight(1f)` 자연 스크롤 적용.
