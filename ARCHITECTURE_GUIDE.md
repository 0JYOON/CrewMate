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
 ├── telegram/                     [텔레그램 봇 연동] SubwayNowBot 실시간 위치 추적, PIN 인증 및 다이아 관제
 ├── ui/theme/                     [디자인 옷장] M3 색상 팔레트, Typography, Shapes, CrewMate 로고
 └── util/                         [유틸리티] 가상 날짜 디버그 모드 공급자(AppDateProvider)
```

---

### 📋 소스파일 역할 일람표

| 폴더 위치 | 파일명 | 비유로 설명하는 역할 | 비고 / 상태 |
| :--- | :--- | :--- | :--- |
| `root` | [MainActivity.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/MainActivity.kt) | **앱의 메인 대문**: 앱이 시작할 때 가장 먼저 실행되어 테마와 화면 네비게이션을 띄워주는 파일 | **활성 (핵심)** |
| `util/` | [AppDateProvider.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/util/AppDateProvider.kt) | **가상 날짜 공급자**: 시스템 시각 변경 없이 앱 내부 날짜를 임의 변경해 테스트할 수 있게 해주는 싱글톤 | **활성 (신규)** |
| `util/` | [ScreenshotHelper.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/ScreenshotHelper.kt) | **스크린샷 촬영/저장기**: Compose 뷰를 캡처하여 MediaStore(Pictures/CrewMate)에 PNG 저장 | **활성 (신규)** |
| `util/` | [DiaCaptureHelper.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/DiaCaptureHelper.kt) | **전 화면 다이아 캡처 통합기**: 요약/전체 캡처 옵션 다이얼로그, 전용 문서 헤더 및 갤러리 원스톱 저장 헬퍼 | **활성 (신규)** |
| `util/` | [AppUpdateManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/AppUpdateManager.kt) | **공식 GitHub Releases 연동 인앱 업데이트 매니저**: `0JYOON/CrewMate` GitHub Releases API 조회, 버전 비교, 12시간 주기 자동 감지, APK 스트리밍 다운로드 및 FileProvider 원터치 설치 호출기 | **활성 (신규/핵심)** |
| `util/` | [UpdateDialogs.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/UpdateDialogs.kt) | **자체 업데이트 UI 다이얼로그 컴포넌트**: 신규 버전 알림/패치노트 다이얼로그, 실시간 다운로드 프로그레스 바 다이얼로그, 최신 버전 확인 팝업, 설치 권한 안내 모달 | **활성 (신규)** |
| `util/` | [AppInfoDialog.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/AppInfoDialog.kt) | **공용 앱 정보 및 이용 유의사항 모달**: 설정 화면 및 최초 로그인 시 공용으로 표시되는 3대 카드·앰버 경고 다이얼로그 | **활성 (신규)** |
| `login/` | [EmployeeBanGuard.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/login/EmployeeBanGuard.kt) | **사번 해시 기반 비밀 차단 가드**: 평문 사번 없이 `Salted SHA-256` 해시로 최초·일일·온라인 전환 시 접근을 차단하고 일반 오류 문구로 위장하는 보안 모듈 | **활성 (핵심)** |
| `login/` | [DailyBanCheckReceiver.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/login/DailyBanCheckReceiver.kt) | **일일 03:00 백그라운드 차단 검사 리시버**: 매일 새벽 03:00 원격 차단 목록을 확인하고 오프라인 시 온라인 전환 시점 검사로 위임 | **활성 (신규)** |
| `data/remote/xcrew/` | [XcrewNetworkLogStore.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/XcrewNetworkLogStore.kt) | **Xcrew 네트워크 통신 이력 저장소**: 당일 포함 앞뒤 1일 범위의 통신 시각·기능명·성공 여부를 민감정보 없이 로컬 기록/정리 | **활성 (신규)** |
| `widget/` | [MonthlyScheduleWidgetProvider.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleWidgetProvider.kt) | **월별 승무근무표 위젯 프로바이더**: 홈 화면 월별 달력 위젯 생명주기 및 전월/오늘/익월/새로고침 인텐트 수신기 | **활성 (신규)** |
| `widget/` | [MonthlyScheduleWidgetUpdater.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleWidgetUpdater.kt) | **월별 위젯 전용 로컬 DB 업데이터**: 100% 로컬 Room DB(`work_schedules`, `memos`)만 읽어 0.01초 내에 달력 위젯을 갱신 | **활성 (신규)** |
| `widget/` | [MonthlyScheduleRenderer.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleRenderer.kt) | **월별 달력 1080px 2D Canvas 렌더러**: 주간/야간/휴일/비실동 색상 뱃지, 메모 점, 오늘 하이라이트 및 프리텐다드 폰트 지원 달력 비트맵 생성기 | **활성 (신규)** |
| `alarm/` | [MidnightCaptureReceiver.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/alarm/MidnightCaptureReceiver.kt) | **자정 자동 캡처기**: 매일 자정에 당일 다이아를 요약/전체 옵션에 맞춰 가변 높이 비트맵 렌더링해 갤러리에 자동 저장하는 수신기 | **활성 (신규)** |
| `alarm/` | [AutoOfflineManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/alarm/AutoOfflineManager.kt) | **자동 오프라인 스케줄러 및 DB 동기화기**: 1순위 운전 구간(다중 구간 포함) 전환/복귀 시각 계산, 즉시 전환 및 예정 스케줄 요약 기록 | **활성 (핵심)** |
| `alarm/` | [AutoOfflineReceiver.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/alarm/AutoOfflineReceiver.kt) | **자동 오프라인 수신기 및 이력 기록기**: 예약 시각에 오프라인/온라인 전환, 실제 작동 시각(`yyyy-MM-dd HH:mm:ss`) 기록 및 차기 구간 예약 | **활성 (핵심)** |
| `schedule/` | [WorkPatternSettings.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/WorkPatternSettings.kt) | **순환 근무 패턴 저장소**: 기준일자와 사용자 지정 순환 뱃지([주간],[야간],[~],[S]) 패턴 저장 및 달력 매핑 | **활성 (신규)** |
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
| `database/` | [CrewMateDatabase.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/database/CrewMateDatabase.kt) | **앱 내장 오프라인 데이터베이스**: 근무표, 다이아, 출무 스냅샷, 봇 전송 이력을 보관하는 Room DB 마스터 (v8) | **활성 (핵심)** |
| `database/` | [BotCommandHistoryEntity.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/database/BotCommandHistoryEntity.kt) | **봇 전송 이력 장부**: 텔레그램 SubwayNowBot 명령어 전송 내역, 시각, 응답 및 취소 상태 저장 테이블 | **활성 (신규)** |
| `database/` | [BotCommandDao.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/database/BotCommandDao.kt) | **봇 이력 데이터 조작원**: 봇 명령어 삽입, 상태 갱신, 열차별 취소 마킹 및 최근 이력 Flow 제공 | **활성 (신규)** |
| `database/` | [AttendanceStatusEntity.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/database/AttendanceStatusEntity.kt) | **오프라인 출무 스냅샷 장부**: 비행기 모드에서도 출무를 확인할 수 있게 저장하는 DB 테이블 | **활성 (핵심)** |
| `database/` | [DiaCensusEntity.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/database/DiaCensusEntity.kt) | **전수조사 승무원 장부**: 1초 승무원 조회를 위해 한 달 다이아를 미리 수집해둔 DB 테이블 | **활성 (핵심)** |
| `telegram/` | [SubwayNowSettings.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/SubwayNowSettings.kt) | **텔레그램 연동 설정 금고**: 봇 사용 여부, Chat ID, 인증 상태, 전송 시각($T_{send}$), 추적 시작 시각($T_{start}$), 주기 영구 저장 | **활성 (신규)** |
| `telegram/` | [SubwayNowRuleEvaluator.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/SubwayNowRuleEvaluator.kt) | **다이아 4대 필터링 두뇌**: 1순위 업무 추출, Sandwiched 감시 연속업무 제외, K-Prefix 정규화, 선행 감시 출발시각 간주 엔진 | **활성 (신규)** |
| `telegram/` | [SubwayNowApiClient.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/SubwayNowApiClient.kt) | **봇 서버 통신 클라이언트**: OkHttp 기반 PIN 인증, 상태 조회, 열차 추적 등록(`POST /track`), 개별 취소(`POST /stop`) 비동기 호출 | **활성 (신규)** |
| `telegram/` | [SubwayNowManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/SubwayNowManager.kt) | **봇 자동 스케줄러 & 관제사**: 다이아 기반 열차별 전송 시점 AlarmManager 예약, 즉시 전송 및 개별 추적 취소 총괄 | **활성 (신규)** |
| `telegram/` | [SubwayNowReceiver.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/SubwayNowReceiver.kt) | **봇 자동 발송 수신기**: AlarmManager 예약 시점에 백그라운드에서 깨어나 SubwayNowBot으로 명령어를 자동 전송하는 리시버 | **활성 (신규)** |
| `telegram/ui/` | [SubwayNowBotSettingsCard.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/ui/SubwayNowBotSettingsCard.kt) | **텔레그램 설정 & 관제 카드**: Chat ID 입력, 6자리 PIN 상호 인증 다이얼로그, 전송/추적 시각 칩, 최근 전송 이력 및 개별 취소 UI | **활성 (신규)** |
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
| `data/backup/` | [DataBackupManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/data/backup/DataBackupManager.kt) | **사용자 데이터 백업/복원 엔진**: 작성 메모, 연차/휴가 정보, 앱 설정을 JSON으로 암호화/비민감 안전 추출 및 복원 | **활성 (핵심)** |
| `util/` | [DiaCaptureHelper.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/DiaCaptureHelper.kt) | **공통 다이아 캡처 헬퍼 모듈**: 전 화면 다이아 캡처 옵션 팝업, 인쇄 레이아웃 및 무결점 비트맵 캡처 실행기 | **활성 (핵심)** |
| `settings/` | [SettingsScreen.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt) | **일반 설정 화면**: 앱 테마, 주요 항목 사이 감시 숨기기, 가상 날짜 디버그 진입, 데이터 백업/복원, 자동 오프라인 관리, `"설정"` 5회 터치 실험실 활성화 | **활성 (핵심)** |
| `labs/` | `LabFeature.kt`, `LabsManager.kt`, `LabsSettingsScreen.kt`, `LabSafeContainer.kt` | **실험실(CrewMate Labs) 프레임워크**: 실험 기능 중앙 레지스트리, 잠금 해제/토글 저장소, 오류 격리 컨테이너 및 설정 화면 | **활성 (신규)** |
| `labs/routediagram/` | `RailwayMasterModels.kt`, `RailwayMasterRepository.kt`, `RouteDiagramBuilder.kt`, `RouteDiagramView.kt` | **승무 행로도 시각화 뷰 & 마스터 동기화**: GitHub↔Alpha 크로스체크 마스터 병합 및 Compose 행로도 렌더러 | **활성 (실험실 1호)** |
| `labs/paystatement/` | `PayStatementModels.kt`, `PayStatementParser.kt`, `PayStatementViewModel.kt`, `PayStatementScreen.kt` | **개인근무명세서 조회 & 여비/수당 판정**: `searchExtrIndWrkSpec.do` 기반 시간·거리·휴일·연장·야간 표출/합계 및 여비(20일)·수당(17일)·900번대 대기근무 판정 | **활성 (실험실 2호)** |
| `labs/colleaguecompare/` | `ColleagueCompareModels.kt`, `PatternFileParser.kt`, `ColleagueScheduleImportParser.kt`, `ScreenshotScheduleOcr.kt`, `ColleagueCompareStorage.kt`, `ColleagueCompareViewModel.kt`, `ColleagueCompareScreen.kt` | **동료 근무 비교**: 본인·Xcrew 동료·수동 추가(순환 패턴/`.xlsx`/`.csv`/`.txt`/스크린샷 OCR/AI 일괄 붙여넣기) 월간 근무 비교 및 출퇴근 시간 표출 | **활성 (실험실 3호)** |
| `labs/handovercrew/` | `HandoverCrewModels.kt`, `HandoverCrewResolver.kt`, `HandoverCrewRepository.kt`, `HandoverCrewDialog.kt` | **교대승무원 확인**: `DiaDetailCard` 열번 뱃지 옆 `👥` 버튼으로 직전·후속 교대 승무원 및 열차 릴레이 팝업 조회 (실시간 + 오프라인 캐시 폴백) | **활성 (실험실 4호)** |
| `labs/diarating/` | `DiaRatingModels.kt`, `DiaRatingStorage.kt`, `DiaRatingEvaluator.kt`, `DiaRatingDialogs.kt` | **다이아 평가**: 운전시간·총사업시간·취침역·수동 지정 기준에 따른 달력 셀 음영·뱃지 및 다이아 카드 등급 표출 | **활성 (실험실 5호)** |
| `util/` | [ScreenshotHelper.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/ScreenshotHelper.kt) | **오프스크린 스크린샷 렌더러 및 갤러리 저장기**: DecorView 기반 무결점 고화질 비트맵 캡처 및 MediaStore 저장 | **활성 (핵심)** |
| `ui/theme/` | [Color.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Color.kt) | **M3 색상 팔레트**: M3 Tonal Color System 라이트/다크 모드 색상 정의 | **활성 (핵심)** |
| `ui/theme/` | [Type.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Type.kt) | **M3 타이포그래피 스케일**: 숫자, 시각, 열차번호 가독성 최적화 M3 서체 스케일 | **활성 (신규)** |
| `ui/theme/` | [Shape.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Shape.kt) | **M3 곡률 스케일**: 4.dp / 8.dp / 12.dp / 16.dp / 28.dp 정식 M3 곡률 스케일 | **활성 (신규)** |
| `ui/theme/` | [Theme.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Theme.kt) | **M3 앱 테마 마스터**: `CrewMateTheme` 라이트/다크 모드 통합 세팅 | **활성 (핵심)** |
| `ui/theme/` | [CrewMateLogo.kt](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/CrewMateLogo.kt) | **CrewMate 로고 벡터 그래픽**: M3 테마와 조화되는 열차 로고 아이콘 | **활성 (핵심)** |
| `root` | [promo_poster.html](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/promo_poster.html) | **카카오톡 홍보 카드뉴스 웹페이지**: html2canvas 기반 1080x1350 PNG 원터치 다운로드 | **배포용 웹** |
| `root` | [CrewMate_v1.0.1.apk](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/CrewMate_v1.0.1.apk) | **정식 서명 완료 Release APK (v1.0.1)**: `kr.crewmate.app` 패키지명 서명 완료 배포 파일 | **배포 바이너리** |
| `root` | [CHANGELOG.md](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/CHANGELOG.md) | **버전별 한 줄 패치노트**: 버전 업데이트 및 변경 내역 기록 문서 | **관리 문서** |
| `root` | [RELEASE_GUIDE.md](file:///C:/Users/joon1/Documents/CrewMate/RELEASE_GUIDE.md) | **신규 버전 릴리즈 배포 가이드**: `0JYOON/CrewMate` GitHub Releases 등록, 서명 APK 빌드 및 인앱 자동 업데이트 배포 절차서 | **배포 가이드 (신규)** |
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

### 7. 네트워크 HTTPS 보안 도메인 추가 및 WebView 보안/ProGuard 설정 변경 위치
- **수정할 파일**: [network_security_config.xml](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/res/xml/network_security_config.xml), [proguard-rules.pro](file:///C:/Users/joon1/Documents/CrewMate/app/proguard-rules.pro), [KorailWebView.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/login/KorailWebView.kt), [AppNavigation.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt)
- **수정 위치**:
  - 코레일 서버 도메인이 추가/변경될 경우 
es/xml/network_security_config.xml의 <domain-config> 블록에 새 도메인을 등록합니다.
  - WebView 보안 플래그(setWebContentsDebuggingEnabled(BuildConfig.DEBUG), llowFileAccess = false 등)는 KorailWebView.kt와 AppNavigation.kt 하단의 HiddenXcrewWebView에 설정되어 있습니다.

### 8. 구글 플레이 스토어 심사용 테스트 계정(Mock 로그인) 아이디/비밀번호 변경 위치
- **수정할 파일**: [MockReviewDataProvider.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/data/remote/xcrew/MockReviewDataProvider.kt)
- **수정 위치**: 파일 상단의 REVIEW_EMPLOYEE_NO = "000000", REVIEW_PASSWORD = "crewmatereview2026!" 상수를 변경하면 심사용 계정 정보를 즉시 바꿀 수 있습니다.

### 9. 실무 편의 5대 기능(스크린샷, 알람 Bypass, 자동 오프라인 및 QA 기록 패널, 순환 패턴, 레일블루 링크) 설정 위치
- **수정할 파일**: [SettingsScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt), [WorkPatternSettings.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/WorkPatternSettings.kt), [AutoOfflineManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/alarm/AutoOfflineManager.kt), [AutoOfflineReceiver.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/alarm/AutoOfflineReceiver.kt), [DiaDetailCard.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/DiaDetailCard.kt)
- **수정 위치**:
  - `SettingsScreen.kt`에서 '자정 자동 캡처'(`midnight_auto_capture` 및 요약/전체 형태 선택 `midnight_auto_capture_option`), '운전 시간 자동 오프라인'(`auto_offline_mode` 및 하단 QA 시각 기록 관리 패널), '미조회 월 순환 근무 패턴 빌더' UI 및 SharedPreferences 키를 관리합니다.
  - `WorkPatternSettings.kt`에서 기본 패턴 주기(`DEFAULT_CYCLE`)와 기준일자 포맷(`YYYY-MM-DD`) 및 모듈로 매핑 규칙을 조정할 수 있습니다.
  - `AutoOfflineManager.kt`에서 1순위 업무 판정 조건(`isRank1`), 자동 오프라인 전환 리드 타임(1분 전), 복귀 조건('정리', '입고정리'), 그리고 QA 기록용 SharedPreferences 키(`PREF_SCHEDULED_SUMMARY`, `PREF_LAST_OFFLINE_TIME`, `PREF_LAST_ONLINE_TIME`)를 관리합니다.
  - `DiaDetailCard.kt`에서 레일블루 URL 템플릿(`https://rail.blue/railroad/logis/Default.aspx?date={date}&train={train}#!`)을 수정할 수 있습니다.

### 10. 전 화면 다이아 스크린샷 오프스크린 렌더러(ScrollView 무제한 높이) 및 공통 캡처 모듈 변경 위치
- **수정할 파일**: [DiaCaptureHelper.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/DiaCaptureHelper.kt), [ScreenshotHelper.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/ScreenshotHelper.kt), [ScheduleScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/ScheduleScreen.kt), [DiaLookupScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/DiaLookupScreen.kt), [TodayWorkScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/TodayWorkScreen.kt), [DiaDetailCard.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/DiaDetailCard.kt)
- **수정 위치**:
  - `DiaCaptureHelper.kt` 상단의 `enum class DiaCaptureOption(title, filePrefix, showAll)`에서 2대 핵심 범위(`타임라인 요약` → `CrewMate_타임라인요약`, `타임라인 전체` → `CrewMate_타임라인전체`)의 제목과 한글 파일명 prefix를 수정할 수 있습니다.
  - `DiaCaptureHelper.kt` 내의 `DiaCaptureLayout` 및 `DiaDetailCard.kt`의 `isCaptureMode = true` 분기에서 캡처본에 표시되는 인쇄용 헤더와 다이아 카드의 배치 및 패딩을 일괄 조정할 수 있습니다.
  - `ScreenshotHelper.kt`의 `captureComposableToBitmap`에서 `ScrollView` 컨테이너 기반 오프스크린 무제한 높이(`MeasureSpec.UNSPECIFIED`) 측정 및 Canvas 배경색을 조정할 수 있습니다.

### 11. 데이터 백업/복원(JSON) 및 오프라인 모드 복귀 지연/Skip 설정 위치
- **수정할 파일**: [DataBackupManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/data/backup/DataBackupManager.kt), [AutoOfflineManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/alarm/AutoOfflineManager.kt), [AutoOfflineReceiver.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/alarm/AutoOfflineReceiver.kt), [SettingsScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt), [TodayWorkScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/TodayWorkScreen.kt)
- **수정 위치**:
  - `DataBackupManager.kt`에서 백업 파일 제외 대상 키(`EXCLUDED_PREF_KEYS`, 비밀번호, 키스토어, 세션 토큰 등) 및 JSON 직렬화 구조를 관리합니다.
  - `AutoOfflineManager.kt`에서 정리/입고정리 후 온라인 복귀 기본 지연 시간(`PREF_RETURN_DELAY_MINUTES`, 기본 10분) 및 `skipOnlineReturn` / `resetReturnSkipped` 로직을 관리합니다.
  - `SettingsScreen.kt`에서 데이터 백업(내보내기)/복원(가져오기) UI 카드와 복귀 지연 칩(`0분`, `5분`, `10분`, `15분`, `20분`) 및 복귀 Skip 버튼을 제어합니다.
  - `TodayWorkScreen.kt` 상단바에서 오프라인 모드 진입 시 표시되는 원터치 `[복귀 Skip]` 버튼 상태를 제어합니다.


### 12. 정밀 알람 3대 필수 권한(알림/정밀알람/배터리 최적화) 직통 연결 및 시스템 사전 준비 다이얼로그 위치
- **수정할 파일**: [WorkAlarmSettingsScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/alarm/WorkAlarmSettingsScreen.kt), [SettingsScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt)
- **수정 위치**:
  - WorkAlarmSettingsScreen.kt 상단의 checkAlarmPrerequisites 함수에서 NotificationManagerCompat, POST_NOTIFICATIONS, canScheduleExactAlarms, isIgnoringBatteryOptimizations 3대 권한 검사 조건을 관리합니다.
  - notificationPermissionLauncher 및 handleNotificationPermissionClick에서 Android 13+ 1-탭 팝업(RequestPermission()) 및 Android 8.0+ 직통 알림 설정 인텐트(Settings.ACTION_APP_NOTIFICATION_SETTINGS)를 제어합니다.
  - showSafetyDialog에서 🔔 알림 권한 허용 (직통 연결), ⏰ 정확한 알람 권한 설정 (직통 연결), 🔋 배터리 최적화 제외 설정 (직통 연결)의 다이얼로그 UI 및 색상을 관리합니다.

### 13. SubwayNowBot 텔레그램 실시간 열차 추적 연동 및 다이아 관제 엔진 설정 위치
- **수정할 파일**: [SubwayNowSettings.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/SubwayNowSettings.kt), [SubwayNowRuleEvaluator.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/SubwayNowRuleEvaluator.kt), [SubwayNowApiClient.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/SubwayNowApiClient.kt), [SubwayNowManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/SubwayNowManager.kt), [SubwayNowBotSettingsCard.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/telegram/ui/SubwayNowBotSettingsCard.kt), [TodayWorkScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/TodayWorkScreen.kt)
- **수정 위치**:
  - `SubwayNowSettings.kt`에서 봇 서버 기본 URL(`DEFAULT_SERVER_URL = "http://129.225.156.25:8088"`), 기본 예약 전송 여유 시간(30분), 기본 추적 시작 여유 시간(15분), 기본 알림 주기(3개 역) 및 SharedPreferences 키를 관리합니다.
  - `SubwayNowRuleEvaluator.kt`에서 4대 다이아 필터링 규칙(1순위 업무 판정 `isRank1`, 연속 감시 제외 `isSandwiched`, `K` prefix 정규화 `normalizeTrainNo`, 선행 감시 출발시각 상속)을 관리합니다.
  - `SubwayNowApiClient.kt`에서 SubwayNowBot REST API 엔드포인트(`/api/v1/ping`, `/api/v1/auth/request`, `/api/v1/auth/status`, `/api/v1/track`, `/api/v1/stop`) 호출 규격과 타임아웃을 관리합니다.
  - `SubwayNowManager.kt`에서 열차별 AlarmManager 발송 예약(`syncTodayDiaSchedule`), 즉시 발송(`dispatchTarget`), 개별 취소(`cancelTarget`), Room DB(`bot_command_histories`) 기록을 총괄합니다.
  - `SubwayNowBotSettingsCard.kt`에서 설정 화면의 Chat ID 입력, 6자리 PIN 발급 및 3초 주기 자동 검증 다이얼로그, 시각/주기 FilterChip, 최근 전송 이력 다이얼로그를 제어합니다.
  - `TodayWorkScreen.kt`에서 당일 근무표 로드 시 다이아 자동 동기화 및 메인 화면 상단 `[🤖 텔레그램 열차 추적]` 연동 뱃지 및 원터치 `[🚀 즉시 전송]` / `[🛑 추적 취소]` 제어 다이얼로그를 제공합니다.

### 14. 공식 GitHub Releases 연동 인앱 업데이트(Self-Update) 및 12시간 주기 자동 감지 알림 설정 위치
- **수정할 파일**: [AppUpdateManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/AppUpdateManager.kt), [UpdateDialogs.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/UpdateDialogs.kt), [SettingsScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt), [AppNavigation.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/navigation/AppNavigation.kt), [AndroidManifest.xml](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/AndroidManifest.xml), [file_paths.xml](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/res/xml/file_paths.xml), [build.gradle.kts](file:///C:/Users/joon1/Documents/CrewMate/app/build.gradle.kts), [AppUpdateManagerTest.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/test/java/com/example/crewmate/util/AppUpdateManagerTest.kt)
- **수정 위치**:
  - `AppUpdateManager.kt`의 `GITHUB_OWNER`(`"0JYOON"`), `GITHUB_REPO`(`"CrewMate"`), `GITHUB_RELEASES_API_URL`에서 배포 저장소 주소를 관리하며, `checkForUpdate()`에서 태그·릴리즈 노트·APK 에셋을 파싱하고 시맨틱 버전 비교(`compareVersions`)를 수행합니다.
  - `AppUpdateManager.kt`의 `downloadApk()`에서 OkHttp 기반으로 캐시 디렉토리(`cacheDir/updates/`)에 스트리밍 다운로드 및 0~100% 진행률 콜백을 전달하고, `installApk()`에서 `FileProvider`를 통해 안드로이드 시스템 패키지 인스톨러(`Intent.ACTION_VIEW`)를 호출합니다.
  - `AppUpdateManager.kt`의 `shouldCheckAutoUpdate()` 및 `isDismissedToday()`에서 12시간 주기 Throttling 및 '오늘 하루 보지 않기' SharedPreferences(`crewmate_update_prefs`) 영구 기록을 관리합니다.
  - `UpdateDialogs.kt`에서 `UpdateNoticeDialog`(새 버전 안내 및 패치노트), `UpdateProgressDialog`(다운로드 진행률 바), `LatestVersionDialog`(최신 버전 확인), `InstallPermissionDialog`(Android 8.0+ 설치 권한 안내)의 레이아웃과 버튼 동작을 관리합니다.
  - `SettingsScreen.kt`에서 "버전 정보 및 업데이트" 카드의 현재 버전 뱃지(`v${BuildConfig.VERSION_NAME}`) 및 수동 `[업데이트 확인]` 버튼 액션을 관리합니다.
  - `AppNavigation.kt`의 `LaunchedEffect(isOfflineMode)`에서 앱 구동 시 백그라운드 자동 업데이트 체크를 실행하고 새 버전 감지 시 모달 팝업을 연동합니다.
  - `AndroidManifest.xml`과 `file_paths.xml`에서 `androidx.core.content.FileProvider` 공유 경로를 관리하며, 업데이트는 브라우저 다운로드(`AppUpdateManager.openInBrowser`) 방식으로 동작합니다.
  - `build.gradle.kts`에서 앱 런타임 기준점인 `versionCode = 37`, `versionName = "1.0.37"`을 관리합니다.

### 15. 월별 승무근무표 위젯, 프리텐다드 폰트, 캘린더 다일 일정 및 기초자료 비실시간 안내 설정 위치
- **수정할 파일**: [MonthlyScheduleWidgetProvider.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleWidgetProvider.kt), [MonthlyScheduleWidgetUpdater.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleWidgetUpdater.kt), [MonthlyScheduleRenderer.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleRenderer.kt), [DiaCaptureRenderer.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/DiaCaptureRenderer.kt), [DeviceCalendarReader.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/DeviceCalendarReader.kt), [HolidayWorkManager.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/HolidayWorkManager.kt), [Type.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Type.kt), [Theme.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Theme.kt), [AppInfoDialog.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/AppInfoDialog.kt), [SettingsScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/settings/SettingsScreen.kt), [DiaLookupScreen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/DiaLookupScreen.kt), [TrainCrewLookupV2Screen.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/schedule/TrainCrewLookupV2Screen.kt)
- **수정 위치**:
  - `DiaCaptureRenderer.kt`의 `reserveTopEndSpaceDp`와 `widget_today_dia.xml`의 `widget_refresh_button`(32dp) 크기 조정을 통해 위젯 날짜 알약과 새로고침 버튼 간의 간격을 제어합니다.
  - `MonthlyScheduleWidgetUpdater.kt` 및 `MonthlyScheduleRenderer.kt`에서 월별 근무표 달력 위젯의 월 이동(`crewmate_widget_prefs`), 색상 뱃지, 메모 점 및 1080px 렌더링을 관리합니다.
  - `Type.kt`의 `PretendardFontFamily`, `Theme.kt`의 `AppFontOption`(`SYSTEM`/`PRETENDARD`) 및 `getCanvasTypeface()`, `SettingsScreen.kt`의 글꼴 선택 칩(`app_font`)을 통해 앱 UI·캡처·위젯 서체를 일괄 제어합니다.
  - `DeviceCalendarReader.kt`의 `expandDeviceEventDates()`에서 `END` 및 `ALL_DAY` 기반 다일 일정 전개 로직을 관리합니다.
  - `AppInfoDialog.kt` 및 `AppNavigation.kt`의 `has_seen_initial_app_info` 플래그를 통해 최초 로그인 시 유의사항 1회 자동 팝업 동작을 제어합니다.
  - `DiaLookupScreen.kt`, `TrainCrewLookupV2Screen.kt`, `TodayWorkScreen.kt` 하단의 오프라인·기초자료 비실시간 안내 문구를 관리합니다.

### 16. SUIT·Spoqa 폰트 추가, 월별 위젯 개편 및 사번 해시 차단 관리 위치
- **수정할 파일**: [Type.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Type.kt), [Theme.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/ui/theme/Theme.kt), [MonthlyScheduleRenderer.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/widget/MonthlyScheduleRenderer.kt), [widget_monthly_schedule.xml](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/res/layout/widget_monthly_schedule.xml), [UpdateDialogs.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/util/UpdateDialogs.kt), [EmployeeBanGuard.kt](file:///C:/Users/joon1/Documents/CrewMate/app/src/main/java/com/example/crewmate/login/EmployeeBanGuard.kt)
- **수정 위치**:
  - `Type.kt` 및 `Theme.kt`에서 `AppFontOption`(`SYSTEM`, `PRETENDARD`, `SUIT`, `SPOQA`)과 `getCanvasTypeface()`를 관리합니다.
  - `EmployeeBanGuard.kt`의 `REMOTE_HASH_LIST_URL`(`config/blocked.json`) 및 `LOCAL_BANNED_HASHES`에 `hashEmployeeNumber(사번)`으로 생성한 64자리 SHA-256 해시를 등록하여 특정 사번을 비공개 차단합니다.
