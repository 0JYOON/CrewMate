# 📋 CrewMate (크루메이트) CHANGELOG (패치노트)

모든 버전 업데이트, 기능 추가, 버그 수정 및 리팩토링 내역을 최신순으로 기록하는 문서입니다.

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
