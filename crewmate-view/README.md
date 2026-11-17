# 🚄 CrewMate View (크루메이트 뷰)

**코레일 Xcrew (`https://xcrew.korail.com`) 인페이지 실시간 모바일·PC 가독성 리스킨 뷰어**

---

## 1. 개요 및 설계 철학

- **로그인 화면 유지 + 로그인 후 7대 전체 메뉴 리스킨**:
  - `xcrew.korail.com/loginView.do` 로그인 화면은 원본 그대로 유지합니다.
  - 로그인 후 진입하는 모든 화면에서 좌측 프로필(`소속`, `성명`, `최근 접속 일시`)과 **7대 전체 메뉴(`당월승무근무표`, `승무다이아`, `승무근무표현황`, `개인근무명세서`, `비실동현황`, `개인승무근무표`, `출무현황`)**를 모바일(iPhone Safari 1순위)·안드로이드·PC 고가독성 UI로 재표출합니다.
- **원본 무결성 100% 보장 & 즉시 전환**:
  - 상단 `[🖥️ 원본 화면]` 버튼을 누르면 언제든 0.1초 만에 기존 Xcrew 화면과 교차 비교할 수 있습니다.
  - `CrewMateDomainAdapter`가 코어에 내장되어 있어 향후 `CrewMate`(안드로이드 앱) 및 `CrewMate Alpha`(웹앱)의 고급 도메인 규칙과 UI를 단계적으로 1:1 통합할 수 있도록 설계되어 있습니다.

---

## 2. 빌드 및 단위 테스트

```bash
# 단일 Userscript(.user.js) 및 IIFE 번들(.bundle.js) 빌드
node crewmate-view/build-view.js

# 포매터 및 도메인 어댑터 단위 테스트 실행
node crewmate-view/test-view.js
```

빌드 완료 시 `crewmate-view/dist/` 폴더에 3가지 산출물이 생성됩니다:
- `crewmate-view/dist/crewmate-view-loader.user.js`: **[권장] 기기 배포용 10줄 초경량 로더** (소스 비공개 + 서버 자동 업데이트)
- `crewmate-view/dist/crewmate-view.bundle.js`: **원격 호스팅용 난독화 본체 & 북마클릿 대상** (`Terser` + `javascript-obfuscator`)
- `crewmate-view/dist/crewmate-view.user.js`: **오프라인 단독 실행형 난독화 Userscript**

---

## 3. 환경별 실행 방법

### A. PC 웹브라우저에서 즉시 테스트 (설치 없이 1초 실행)
1. PC 브라우저(Chrome / Edge)에서 `https://xcrew.korail.com`에 로그인합니다.
2. `F12` → `Console` 탭을 엽니다.
3. `crewmate-view/dist/crewmate-view.bundle.js` 파일 내용을 복사해 콘솔에 붙여넣고 **Enter**를 누르면 즉시 **CrewMate View** 화면이 켜집니다.
4. 상단 탭 바를 눌러 7개 메뉴를 페이지 새로고침 없이 즉시 전환해 보실 수 있습니다.

### B. iPhone Safari / PC 자동 실행 (1순위 권장 — Userscripts 확장앱)
1. **iPhone Safari**: 앱스토어에서 무료 오픈소스 앱 **[Userscripts](https://apps.apple.com/app/userscripts/id1463298887)** 설치 → `설정 > 앱 > Safari > 확장 프로그램 > Userscripts` 허용 후 `crewmate-view.user.js` 파일을 등록합니다.
2. **PC Chrome / Edge**: **Tampermonkey** 확장 프로그램 설치 → 새 스크립트 만들기에 `crewmate-view.user.js` 내용을 붙여넣고 저장합니다.
3. 이후 `xcrew.korail.com`에 로그인하기만 하면 **아무 버튼도 누를 필요 없이 100% 자동으로 CrewMate View 화면이 나타납니다.**

### C. 북마클릿 방식 (2순위 병행 — 확장앱 미설치 환경)
`crewmate-view.bundle.js`를 HTTPS 정적 호스팅(예: Cloudflare Pages)에 업로드한 뒤, 아래 1줄 로더를 책갈피(북마크) URL에 등록하여 사용합니다:
```javascript
javascript:(function(){var s=document.createElement('script');s.src='https://crewmate-alpha.pages.dev/crewmate-view.bundle.js?t='+Date.now();document.body.appendChild(s);})();
```
