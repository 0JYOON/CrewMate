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
