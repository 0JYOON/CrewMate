# 🤖 AGENTS.md - CrewMate AI 에이전트 작업 필수 수칙

이 문서는 안드로이드 스튜디오 및 개발 환경에서 **CrewMate(크루메이트)** 프로젝트의 코드 수정, 버그 해결, 기능 보완 작업을 수행하는 **모든 AI 에이전트가 반드시 준수해야 하는 필수 작업 수칙 지침서**입니다.

---

## 🚨 [작업 완료 후 필수 수행 3대 수칙]

AI 에이전트는 코드 수정 작업을 마치고 단위 테스트 및 빌드 검증(`gradlew.bat test` 및 `gradlew.bat assembleDebug`)이 성공한 직후, **사용자에게 최종 답변/보고를 하기 전에 반드시 아래 3개 문서 기록 작업을 완수**해야 합니다.

### 수칙 1. `CHANGELOG.md` 최상단 패치노트 기록
- 작업이 완료되면 [CHANGELOG.md](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/CHANGELOG.md) 최상단에 오늘 날짜, 변경 버전/패치명, 그리고 수정 내역을 최신순으로 추가 기록하십시오.
- **기록 필수 항목**:
  - 오늘 날짜 (`YYYY-MM-DD`) 및 버전 명 (`[v1.x.x]`)
  - 무엇을 왜 고쳤는지 핵심 개요
  - 변경되거나 신설된 소스 파일 목록 및 핵심 코드 위치

### 수칙 2. `DEVELOPMENT_HISTORY.md` 개발 연대기 최신화
- 새로운 기능 추가, 핵심 버그 수정, 아키텍처 변경, 빌드/배포 설정 변경이 있었다면 [DEVELOPMENT_HISTORY.md](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/DEVELOPMENT_HISTORY.md)의 최하단에 새로운 STEP 항목(예: STEP 27, STEP 28...)을 이어서 상세히 기록하십시오.
- **주의**: 기존 작성된 STEP 1 ~ STEP 26 기록은 절대로 삭제하거나 축약하지 말고 그대로 보존해야 합니다.

### 수칙 3. `ARCHITECTURE_GUIDE.md` 앱 구조 설명서 최신화
- 새로 생성되거나 삭제/역할 변경된 소스 파일이 있다면 [ARCHITECTURE_GUIDE.md](file:///C:/Users/joon1/Documents/ChatGPT/CrewMate/ARCHITECTURE_GUIDE.md)의 **'1장: 폴더 및 소스파일 지도'** 표에 반영하십시오.
- 새로운 유지보수 포인트(설정 위치, API 주소, 디자인 토큰 등)가 생겼다면 **'3장: 초보자를 위한 유지보수 가이드'**에 파일명과 위치를 지정하여 추가하십시오.

---

## 🛡️ [코드 수정 시 절대 안전 원칙]

1. **로그인/Xcrew 보안 규칙 엄수**:
   - 사번, 비밀번호, 쿠키, JSESSIONID, 인증 토큰 등 민감 정보는 절대로 디버그 로그(`Log.d/Log.i/Log.e`)에 출력하거나 외부로 전송하지 마십시오.
   - `SecureCredentialStore.kt` 키스토어 AES-256 암호화 저장 구조를 100% 유지하십시오.
2. **정보 밀도(Information Density) 보존**:
   - 철도 승무원 실무 앱 특성상 한눈에 들어오는 다이아 타임라인, 근무표, 승무원 조회 카드의 컴팩트한 여백과 정보 밀도를 유지하십시오.
3. **빌드 검증 필수 실행**:
   - 작업 완료 후 항상 `./gradlew.bat test` 및 `./gradlew.bat assembleDebug`를 실제로 실행하여 0 오류(Zero Error) 상태임을 확인한 뒤 최종 보고하십시오.
4. **코어 모듈 공유 + 플랫폼별 맞춤형 UI 디자인 원칙 (Web vs Android Native)**:
   - 파서·계산·릴레이 매칭·행로도 데이터 모델 등 **코어 비즈니스 로직은 앱(`CrewMate`)과 웹(`CrewMate-Alpha`)이 1:1 동일한 결과**를 보장해야 합니다.
   - 단, **화면 UI/UX는 웹(데스크톱·모바일 브라우저)과 앱(Android Compose 세로 화면·터치 환경) 각각의 특성에 맞게 별도로 최적화**하십시오. 웹 레이아웃을 앱에 그대로 이식하여 가로 잘림·버튼 겹침·여백 붕괴가 발생하지 않도록 주의하십시오.
5. **임의 안내 팁(Tip) 삽입 및 스크롤 영역 침범 금지**:
   - 사용자가 요청하지 않은 안내 팁/설명 문구를 화면 하단 등에 임의로 추가하거나, 그로 인해 표/리스트의 상하 스크롤 영역이 제한되게 하지 마십시오.
6. **실험실 기능의 보수적 기본값 원칙**:
   - 실험실 기능이 켜져 있더라도 기존 핵심 화면의 기본 뷰(예: 다이아 상세의 `'타임라인'` 기본 표출)를 침범하거나, 화면 진입 시 자동 온라인 새로고침을 강제하지 마십시오(`수동` 새로고침 기본값 유지).
7. **배포 산출물 난독화·무결성 유지**:
   - 웹 배포 시 `node build.js`(`terser` + `javascript-obfuscator`)를 거친 `dist/` 산출물을 유지하고, Android Release 배포 시 R8 난독화(`isMinifyEnabled = true`) 및 `SecurityGuard.kt` 서명 무결성 검증을 유지하십시오.
8. **`CrewMate View` 개발 시 자동 빌드·난독화 및 `CrewMate-Alpha` 호스팅 동기화 필수**:
   - `crewmate-view/` 내 소스를 수정할 때마다 작업 완료 전 반드시 `node crewmate-view/build-view.js` 및 `node crewmate-view/test-view.js`를 실행하십시오.
   - `build-view.js`는 `Terser` + `javascript-obfuscator` 난독화 본체(`crewmate-view.bundle.js`), 10줄 로더(`crewmate-view-loader.user.js`), 생태계 종합 안내서(`guide.html`, `view-guide.html`)를 생성함과 동시에 `C:\Users\joon1\Documents\CrewMate-Alpha` 루트 및 `dist/` 폴더에 자동 동기화합니다.
   - `CrewMate View`의 사번 차단 가드(`banGuard.js`)는 Android `EmployeeBanGuard.kt`와 동일한 `Salted SHA-256` 및 원격 `config/blocked.json` 체계를 항상 유지해야 합니다.
