# 🏗️ ARCHITECTURE_GUIDE.md
## 비전공자를 위한 CrewMate 앱 구조 및 유지보수 설명서

이 설명서는 코딩 및 안드로이드 개발 지식이 없는 비전공자도 **CrewMate(크루메이트)** 앱의 내부 소스코드 구조를 직관적으로 이해하고, 향후 코레일 사이트 주소 변경이나 디자인 수정이 필요할 때 **어느 파일의 어느 부분을 고쳐야 하는지 쉽게 찾을 수 있도록 가이드**하는 유지보수 지침서입니다.

---

## 📂 1장: 폴더 및 소스파일 지도 (File System Map)

CrewMate 소스코드는 역할별로 깔끔하게 폴더(패키지)가 나누어져 있습니다.

```
com.example.crewmate
 ├── MainActivity.kt               [앱의 주 대문] 앱이 처음 켜지는 입구 파일
 ├── alarm/                        [정밀 알람 엔진] 모닝콜 소리, 진동, 워치 연동, 알람 규칙
 ├── data/remote/xcrew/            [서버 통신 장비] Xcrew 웹사이트 로그인 및 데이터 긁어오기
 ├── database/                     [로컬 저장소] 인터넷이 안 돼도 작동하게 도와주는 내장 DB
 ├── login/                        [로그인 담당] 암호화 비밀번호 저장 및 WebView 자동 로그인
 ├── navigation/                   [안내원] 화면 전환(오늘의 근무, 달력, 설정 등) 및 햄버거 메뉴
 ├── schedule/                     [근무표 및 조회] 달력, 오늘의 근무, 승무원 조회 V2, 다이아 조회
 ├── settings/                     [일반 설정] 앱 테마(다크모드), 폰트 크기, 감시 숨기기 옵션
 ├── ui/theme/                     [디자인 옷장] M3 색상 팔레트, Typography, Shapes, CrewMate 로고
 └── util/                         [유틸리티] 가상 날짜 디버그 모드 공급자(AppDateProvider)
```

---

### 📋 소스파일 역할 일람표

| 폴더 위치 | 파일명 | 비유로 설명하는 역할 | 비고 / 상태 |
| :--- | :--- | :--- | :--- |
| `root` | [MainActivity.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/MainActivity.kt) | **앱의 메인 대문**: 앱이 시작할 때 가장 먼저 실행되어 테마와 화면 네비게이션을 띄워주는 파일 | **활성 (핵심)** |
| `util/` | [AppDateProvider.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/util/AppDateProvider.kt) | **가상 날짜 공급자**: 시스템 시각 변경 없이 앱 내부 날짜를 임의 변경해 테스트할 수 있게 해주는 싱글톤 | **활성 (신규)** |
| `alarm/` | [WorkAlarmScheduler.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/WorkAlarmScheduler.kt) | **알람 예약 시계**: 안드로이드 OS에 알람 시각을 정확히 등록하는 시계 기사 | **활성 (핵심)** |
| `alarm/` | [AlarmLoopService.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/AlarmLoopService.kt) | **무한 알람 사이렌**: 해제 버튼을 누를 때까지 소리와 워치 손목 진동을 계속 울려주는 서비스 | **활성 (핵심)** |
| `alarm/` | [WorkAlarmRuleEvaluator.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/WorkAlarmRuleEvaluator.kt) | **알람 판정 두뇌**: 출근, 출무, 운전, 편승 등 6대 규칙에 따라 몇 시에 알람을 울릴지 정밀 계산 | **활성 (핵심)** |
| `alarm/` | [AlarmPlayerManager.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/AlarmPlayerManager.kt) | **오디오/진동 스피커**: 모닝콜 알람 음원 재생과 스마트폰 진동 모터를 제어하는 기사 | **활성 (핵심)** |
| `alarm/` | [WorkAlarmReceiver.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/WorkAlarmReceiver.kt) | **알람 시각 신호 수신기**: 예약된 시각이 되었을 때 OS로부터 신호를 받아 사이렌을 켬 | **활성 (핵심)** |
| `alarm/` | [BootReceiver.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/BootReceiver.kt) | **재부팅 복구원**: 스마트폰 전원을 껐다 켰을 때 오늘 지워진 알람을 자동으로 재등록 | **활성 (핵심)** |
| `alarm/` | [WorkAlarmSettingsScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/WorkAlarmSettingsScreen.kt) | **정밀 알람 설정 화면**: 햄버거 메뉴의 '정밀 알람' 화면 (규칙 스위치, 테스트 카드, 강제 해제) | **활성 (핵심)** |
| `alarm/` | [WorkAlarmSettings.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/WorkAlarmSettings.kt) | **알람 설정 데이터 상자**: 알람 분 전 값과 스위치 상태를 담고 있는 데이터 규격 | **활성 (핵심)** |
| `alarm/` | [WorkAlarmSettingsStore.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/WorkAlarmSettingsStore.kt) | **알람 설정 금고**: 사용자가 바꾼 알람 분 전 설정을 스마트폰에 영구 저장/불러오기 | **활성 (핵심)** |
| `alarm/` | [PopupAlarmActivity.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/PopupAlarmActivity.kt) | **팝업 알람창**: 알람이 울릴 때 화면 위에 뜨는 대화상자 팝업창 | **활성** |
| `alarm/` | [FullScreenAlarmActivity.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/FullScreenAlarmActivity.kt) | **전체화면 알람창**: 화면이 꺼져있을 때 화면을 깨우며 켜지는 전체화면 모닝콜 화면 | **활성** |
| `data/remote/` | [XcrewWebViewXhrDataSource.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/XcrewWebViewXhrDataSource.kt) | **Xcrew 데이터 채굴기**: 숨겨진 WebView 내부에서 코레일 Xcrew API를 호출해 데이터 수집 | **활성 (핵심)** |
| `data/remote/` | [XcrewLoginRepository.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/XcrewLoginRepository.kt) | **Xcrew 로그인 처리기**: 사번과 비밀번호로 코레일 Xcrew 웹사이트에 접속해 세션을 생성 | **활성 (핵심)** |
| `data/remote/` | [DiaDetail.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/DiaDetail.kt) | **다이아 상세 번역기**: Xcrew에서 받아온 다이아 구간 정보 JSON을 해석하는 파서 | **활성 (핵심)** |
| `data/remote/` | [AttendanceStatus.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/AttendanceStatus.kt) | **출무 현황 번역기**: 예정출무시각과 실제출무시각 JSON 데이터를 해석하는 파서 | **활성 (핵심)** |
| `database/` | [CrewMateDatabase.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/database/CrewMateDatabase.kt) | **앱 내장 오프라인 데이터베이스**: 근무표, 다이아, 출무 스냅샷을 보관하는 Room DB 마스터 | **활성 (핵심)** |
| `database/` | [AttendanceStatusEntity.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/database/AttendanceStatusEntity.kt) | **오프라인 출무 스냅샷 장부**: 비행기 모드에서도 출무를 확인할 수 있게 저장하는 DB 테이블 | **활성 (핵심)** |
| `database/` | [DiaCensusEntity.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/database/DiaCensusEntity.kt) | **전수조사 승무원 장부**: 1초 승무원 조회를 위해 한 달 다이아를 미리 수집해둔 DB 테이블 | **활성 (핵심)** |
| `login/` | [SecureCredentialStore.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/login/SecureCredentialStore.kt) | **암호화 비밀번호 금고**: 사번과 비밀번호를 안드로이드 키스토어(AES-256)로 암호화 보관 | **활성 (핵심)** |
| `login/` | [LoginScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/login/LoginScreen.kt) | **네이티브 로그인 화면**: 사번과 비밀번호를 치고 로그인하는 첫 화면 | **활성 (핵심)** |
| `login/` | [KorailWebView.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/login/KorailWebView.kt) | **숨겨진 웹브라우저**: 안보이는 곳에서 코레일 웹사이트 세션을 유지해주는 보이지 않는 창 | **활성 (핵심)** |
| `navigation/` | [AppNavigation.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt) | **화면 안내원 및 햄버거 메뉴**: 화면 이동(오늘의 근무, 달력, 정밀 알람 등) 및 메뉴 조율 | **활성 (핵심)** |
| `schedule/` | [TodayWorkScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/schedule/TodayWorkScreen.kt) | **오늘의 근무 화면**: 앱을 켜면 보이는 메인 화면 (당일 타임라인, 예정 알람 팝업 칩, 출무 현황) | **활성 (핵심)** |
| `schedule/` | [DiaDetailCard.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/schedule/DiaDetailCard.kt) | **다이아 타임라인 카드**: 운전/승무/출고준비/편승/입환 세그먼트를 M3 디자인으로 그려주는 카드 | **활성 (핵심)** |
| `schedule/` | [DiaCensusManager.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/schedule/DiaCensusManager.kt) | **승무원 전수조사 백그라운드 엔진**: 한 달 승무원 다이아를 미리 긁어모아 1초 조회를 가능케 함 | **활성 (핵심)** |
| `schedule/` | [TrainCrewLookupV2Screen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/schedule/TrainCrewLookupV2Screen.kt) | **열차 승무원 조회 V2 화면**: 열차번호를 치면 동료 승무원 이름과 출퇴근 시각을 찾는 화면 | **활성 (핵심)** |
| `schedule/` | [DiaLookupScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/schedule/DiaLookupScreen.kt) | **승무 다이아 조회 화면**: 다이아 번호(예: 16301)를 치면 타임라인을 검색해주는 화면 | **활성 (핵심)** |
| `schedule/` | [ScheduleScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/schedule/ScheduleScreen.kt) | **월간 달력 화면**: 한 달 근무표 달력 및 바텀시트 상세 화면 | **활성 (핵심)** |
| `schedule/` | [CrewWorkStatusScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/schedule/CrewWorkStatusScreen.kt) | **소속 승무원 근무 현황 화면**: 사업소 승무원들의 일자별 근무 현황표 화면 | **활성 (핵심)** |
| `schedule/` | [NonOpStatusScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/schedule/NonOpStatusScreen.kt) | **비실동 현황 화면**: 교육, 출장, 연차, 병가 등 비실동 종합 현황표 화면 | **활성 (핵심)** |
| `schedule/` | [WorkSchedule.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/schedule/WorkSchedule.kt) | **근무 스케줄 모델 및 판정 엔진**: `alcSpecDvCd` 절대 규칙 기반 `determineShiftType()` 판정식 | **활성 (핵심)** |
| `settings/` | [SettingsScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt) | **일반 설정 화면**: 앱 테마, 주요 항목 사이 감시 숨기기, 가상 날짜 디버그 진입 | **활성 (핵심)** |
| `ui/theme/` | [Color.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Color.kt) | **M3 색상 팔레트**: M3 Tonal Color System 라이트/다크 모드 색상 정의 | **활성 (핵심)** |
| `ui/theme/` | [Type.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Type.kt) | **M3 타이포그래피 스케일**: 숫자, 시각, 열차번호 가독성 최적화 M3 서체 스케일 | **활성 (신규)** |
| `ui/theme/` | [Shape.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Shape.kt) | **M3 곡률 스케일**: 4.dp / 8.dp / 12.dp / 16.dp / 28.dp 정식 M3 곡률 스케일 | **활성 (신규)** |
| `ui/theme/` | [Theme.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Theme.kt) | **M3 앱 테마 마스터**: `CrewMateTheme` 라이트/다크 모드 통합 세팅 | **활성 (핵심)** |
| `ui/theme/` | [CrewMateLogo.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/CrewMateLogo.kt) | **CrewMate 로고 벡터 그래픽**: M3 테마와 조화되는 열차 로고 아이콘 | **활성 (핵심)** |
| `root` | [promo_poster.html](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/promo_poster.html) | **카카오톡 홍보 카드뉴스 웹페이지**: html2canvas 기반 1080x1350 PNG 원터치 다운로드 | **배포용 웹** |
| `root` | [CrewMate_v1.0.1.apk](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/CrewMate_v1.0.1.apk) | **정식 서명 완료 Release APK (v1.0.1)**: `kr.crewmate.app` 패키지명 서명 완료 배포 파일 | **배포 바이너리** |
| `root` | [CHANGELOG.md](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/CHANGELOG.md) | **버전별 한 줄 패치노트**: 버전 업데이트 및 변경 내역 기록 문서 | **관리 문서** |
| `root` | [AGENTS.md](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/AGENTS.md) | **AI 에이전트 작업 필수 수칙**: 코드 변경 후 빌드/테스트 및 문서 최적화 자동화 수칙 | **지침 문서** |
| `root` | `crewmate-release-key.jks` | **프로젝트 정식 서명 키스토어**: 유효기간 10,000일 release 서명 키 | **보안 키스토어** |
| `network/` | `KorailClient.kt`, `KorailEndpoints.kt` | 초기 직접 연결 시도용 보존 파일 (현재 XHR 데이터 소스로 우회 사용 중) | *구버전 보존* |
| `schedule/` | `TrainCrewLookupScreen.kt`, `TrainCrewSearch.kt` | 승무원 조회 V1 보존 파일 (현재 V2 전수조사 엔진 사용 중) | *구버전 보존* |
| `database/` | `AppDatabase.kt` | 초기 임시 데이터베이스 보존 파일 (현재 `CrewMateDatabase.kt` 사용 중) | *구버전 보존* |

---

## 🔄 2장: 데이터 흐름도 (Data Flow Diagram)

앱이 실행되어 화면에 정보가 나오기까지 데이터가 지나가는 길을 화살표(`->`)로 시각화했습니다.

### 1. 자동 로그인 및 데이터 수집 흐름
```text
[앱 실행 (MainActivity)]
   │
   ▼
[SecureCredentialStore]  ──(암호화된 사번/비밀번호 복호화)──► [KorailWebView]
                                                                  │
                                                        (Xcrew 로그인 진행)
                                                                  │
                                                                  ▼
[XcrewWebViewXhrDataSource] ◄──(인증 쿠키 및 XHR 인터셉트)─────────┘
   │
   ├─► [WorkScheduleRepositoryImpl] ──► [Room DB: work_schedules] ──► [ScheduleScreen (월간 달력)]
   │
   ├─► [DiaDetailRepository]        ──► [Room DB: dia_details]     ──► [TodayWorkScreen (오늘의 근무)]
   │
   └─► [TodayWorkRepository]        ──► [Room DB: attendance_statuses] (오프라인 스냅샷 저장)
```

### 2. 정밀 알람 발동 및 스마트워치 연동 흐름
```text
[TodayWorkRepository] (오늘 다이아 & 출무 데이터 수집)
   │
   ▼
[WorkAlarmRuleEvaluator] (출근, 출무, 운전, 출고준비, 편승, 입환 규칙 판정)
   │
   ▼
[WorkAlarmScheduler] ──(setAlarmClock 정식 시계 알람 등록)──► [안드로이드 OS]
                                                                    │
                                                           (알람 시각 도착!)
                                                                    │
                                                                    ▼
[AlarmLoopService] ◄───────(Broadcast 수신)──────────── [WorkAlarmReceiver]
   │
   ├─► [AlarmPlayerManager] ──► [스마트폰 스피커/진동 모터] (우렁찬 모닝콜 소리)
   │
   └─► [NotificationManager] ──(동적 채널 v4 & WearableExtender)──► [갤럭시 워치] (무한 연속 손목 진동!)
```

---

## 🛠️ 3장: 초보자를 위한 유지보수 가이드 (Maintenance Guide)

나중에 앱을 수정하고 싶을 때 **어느 파일의 어느 줄을 열어야 하는지** 핵심 포인트만 지정해 드립니다.

### 1. 코레일 Xcrew 서버 웹사이트 주소가 변경되었을 때
- **수정할 파일**: [XcrewLoginRepository.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/XcrewLoginRepository.kt) & [XcrewWebViewXhrDataSource.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/XcrewWebViewXhrDataSource.kt)
- **수정 위치**: 파일 최하단의 `XCREW_ORIGIN`, `LOGIN_PAGE_URL`, `LOGIN_POST_URL` 상수 주소를 새로 바뀐 주소로 변경하시면 됩니다.

### 2. 정밀 알람의 기본 '몇 분 전' 시간이나 규칙을 고치고 싶을 때
- **수정할 파일**: [WorkAlarmSettings.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/WorkAlarmSettings.kt) & [WorkAlarmRuleEvaluator.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/alarm/WorkAlarmRuleEvaluator.kt)
- **수정 위치**:
  - `WorkAlarmSettings.kt`에서 `commuteLeadMinutes = 60`, `driveLeadMinutes = 10`, `attendanceLeadMinutes = 5` 기본 분 단위를 원하시는 숫자로 바꾸시면 됩니다.
  - `WorkAlarmRuleEvaluator.kt`에서 알람이 추가되거나 제외될 조건을 수정하실 수 있습니다.

### 3. 앱 패키지명(`applicationId`) 및 서명 키(Keystore) 설정 위치
- **수정할 파일**: [build.gradle.kts](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/build.gradle.kts)
- **수정 위치**:
  - `applicationId = "kr.crewmate.app"` (공식 패키지명 변경 위치)
  - `signingConfigs` 블록 내 서명 키 파일 경로(`crewmate-release-key.jks`) 및 비밀번호 위치

### 4. 가상 날짜 디버그 모드 관리자 비밀번호 변경 위치
- **수정할 파일**: [SettingsScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt)
- **수정 위치**: `[앱 테마 설정]` 카드 5회 연속 탭 시 확인하는 `adminPasswordInput.trim() == "5078"` 비교문에서 비밀번호를 고치시면 됩니다.

### 5. M3 앱 테마 색상, 곡률, 글꼴 크기 변경 위치
- **수정할 파일**: [Color.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Color.kt), [Theme.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Theme.kt), [Type.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Type.kt), [Shape.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Shape.kt)
- **수정 위치**: `Color.kt` 색상 팔레트 헥사 코드, `Type.kt` 서체 크기/굵기, `Shape.kt` 모서리 곡률(`4.dp`, `8.dp`, `12.dp`, `16.dp`, `28.dp`)을 조절하시면 앱 전체 디자인에 한 번에 반영됩니다.

### 6. 상단 CrewMate 로고 모양이나 텍스트를 고치고 싶을 때
- **수정할 파일**: [CrewMateLogo.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/CrewMateLogo.kt)
- **수정 위치**: 로고 벡터 그래픽 디자인 파일입니다.
