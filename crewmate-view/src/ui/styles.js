/**
 * CrewMate View - Shadow DOM 전용 고밀도 반응형 스타일시트 (Phase 2)
 */

export const CREWMATE_VIEW_CSS = `
:host {
  all: initial;
  display: block;
  color: #0f172a;
  -webkit-text-size-adjust: 100%;
  -webkit-tap-highlight-color: transparent;
}

*, *::before, *::after {
  box-sizing: border-box;
}

.cm-root {
  --bg-app: #f1f5f9;
  --bg-card: #ffffff;
  --bg-subtle: #f8fafc;
  --bg-header: #0f172a;
  --text-main: #0f172a;
  --text-sub: #475569;
  --text-muted: #64748b;
  --border-color: #e2e8f0;
  --border-strong: #cbd5e1;
  --primary: #1d4ed8;
  --primary-bg: #eff6ff;
  --primary-border: #bfdbfe;
  --grid-line: #cbd5e1;
  --cm-cell-py: 4px;
  --cm-font-scale: 1;

  position: fixed;
  inset: 0;
  z-index: 2147483640;
  background: var(--bg-app);
  color: var(--text-main);
  font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Malgun Gothic", "Segoe UI", sans-serif;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100dvh;
  overflow: hidden;
}

.cm-root.cm-dark {
  --bg-app: #020617;
  --bg-card: #0f172a;
  --bg-subtle: #1e293b;
  --bg-header: #090d16;
  --text-main: #f8fafc;
  --text-sub: #cbd5e1;
  --text-muted: #94a3b8;
  --border-color: #1e293b;
  --border-strong: #334155;
  --primary: #3b82f6;
  --primary-bg: rgba(59, 130, 246, 0.15);
  --primary-border: rgba(59, 130, 246, 0.35);
  --grid-line: #334155;
}

/* 최상단 헤더 */
.cm-header {
  background: var(--bg-header);
  color: #ffffff;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  flex-shrink: 0;
}

.cm-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.cm-hamburger-btn {
  border: 1px solid rgba(255,255,255,0.18);
  background: rgba(255,255,255,0.08);
  color: #ffffff;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  font-size: 17px;
  font-weight: 900;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cm-logo-img {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  object-fit: cover;
  display: block;
  border: 1px solid rgba(255, 255, 255, 0.18);
  flex-shrink: 0;
}

.cm-header-title-wrap {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.cm-header-menu-title {
  font-size: calc(14px * var(--cm-font-scale));
  font-weight: 900;
  color: #ffffff;
  letter-spacing: -0.3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cm-header-sub {
  font-size: calc(10.5px * var(--cm-font-scale));
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cm-header-affil {
  color: #facc15;
  font-weight: 800;
}

.cm-relogin-btn {
  border: 1px solid rgba(59, 130, 246, 0.5);
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #ffffff;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: calc(11.5px * var(--cm-font-scale));
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.cm-relogin-btn:active {
  transform: scale(0.97);
}

/* 좌측 슬라이드 햄버거 드로어 */
.cm-drawer-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2147483644;
  background: rgba(2, 6, 23, 0.6);
  display: flex;
}

.cm-drawer {
  width: 270px;
  max-width: 82vw;
  height: 100%;
  background: var(--bg-card);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  box-shadow: 12px 0 30px rgba(0, 0, 0, 0.35);
}

.cm-drawer-header {
  background: var(--bg-header);
  color: #ffffff;
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cm-drawer-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cm-drawer-brand-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 900;
}

.cm-drawer-close {
  border: none;
  background: rgba(255,255,255,0.12);
  color: #ffffff;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}

.cm-drawer-profile {
  background: rgba(255,255,255,0.07);
  border-radius: 9px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.4;
}

.cm-drawer-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.cm-drawer-item {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--text-main);
  border-radius: 10px;
  padding: 10px 12px;
  font-size: calc(13.5px * var(--cm-font-scale));
  font-weight: 800;
  text-align: left;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: inherit;
}

.cm-drawer-item.active {
  background: var(--primary-bg);
  color: var(--primary);
  border: 1px solid var(--primary-border);
}

.cm-drawer-footer {
  padding: 10px 8px calc(12px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cm-drawer-raw-btn {
  width: 100%;
  border: 1px solid #d97706;
  background: #f59e0b;
  color: #0f172a;
  border-radius: 10px;
  padding: 9px 12px;
  font-size: calc(12.5px * var(--cm-font-scale));
  font-weight: 900;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-family: inherit;
}

/* 메인 스크롤 컨텐츠 영역 */
.cm-main {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  padding: 8px 8px calc(24px + env(safe-area-inset-bottom, 0px)) 8px;
}

.cm-container {
  max-width: 960px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 1줄 고정 컨트롤 바 (좌우 스크롤 없음) */
.cm-control-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 11px;
  padding: 6px 8px;
}

.cm-control-row {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
  width: 100%;
  min-width: 0;
}

.cm-btn {
  border: 1px solid var(--border-strong);
  background: var(--bg-subtle);
  color: var(--text-main);
  border-radius: 8px;
  padding: 5px 8px;
  font-size: calc(11.5px * var(--cm-font-scale));
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  font-family: inherit;
}

.cm-btn-primary {
  background: var(--primary);
  border-color: var(--primary);
  color: #ffffff;
}

.cm-select, .cm-input {
  border: 1px solid var(--border-strong);
  background: var(--bg-subtle);
  color: var(--text-main);
  border-radius: 8px;
  padding: 3px 6px;
  font-size: 16px;
  font-weight: 800;
  min-width: 0;
  height: 30px;
  font-family: inherit;
}

/* 뱃지 토글 그룹 ('오늘', '달력', '목록', '표', '요약') */
.cm-pill-btn {
  border: 1px solid var(--border-strong);
  background: var(--bg-subtle);
  color: var(--text-sub);
  border-radius: 7px;
  padding: 4px 8px;
  font-size: calc(11px * var(--cm-font-scale));
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: inherit;
}

.cm-pill-btn.active {
  background: var(--primary);
  border-color: var(--primary);
  color: #ffffff;
}

/* 카드 및 테이블 */
.cm-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 11px;
  overflow: hidden;
}

.cm-card-header {
  padding: 7px 10px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-subtle);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: calc(12px * var(--cm-font-scale));
  font-weight: 800;
}

/* 좌우 스크롤 없는 기기 폭 맞춤 테이블 */
.cm-table-fit {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: calc(11.5px * var(--cm-font-scale));
  line-height: 1.25;
}

.cm-table-fit th {
  background: var(--bg-subtle);
  color: var(--text-sub);
  font-weight: 800;
  font-size: calc(10.5px * var(--cm-font-scale));
  padding: var(--cm-cell-py) 3px;
  border-bottom: 1px solid var(--border-strong);
  border-right: 1px solid var(--border-color);
  white-space: nowrap;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cm-table-fit td {
  padding: var(--cm-cell-py) 3px;
  border-bottom: 1px solid var(--border-color);
  border-right: 1px solid var(--border-color);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-main);
  font-weight: 600;
}

.cm-table-fit tr:last-child td { border-bottom: none; }
.cm-table-fit th:last-child, .cm-table-fit td:last-child { border-right: none; }
.cm-table-fit tr.cm-row-highlight td { background: var(--primary-bg); }

/* 개인근무명세서 전용 상하+좌우 고정 스크롤 테이블 */
.cm-spec-scroll {
  width: 100%;
  max-height: calc(100dvh - 150px);
  overflow: auto;
  -webkit-overflow-scrolling: touch;
  position: relative;
}

.cm-table-spec {
  width: max-content;
  min-width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: calc(11.5px * var(--cm-font-scale));
}

.cm-table-spec th, .cm-table-spec td {
  padding: var(--cm-cell-py) 6px;
  border-bottom: 1px solid var(--border-color);
  border-right: 1px solid var(--border-color);
  text-align: center;
  white-space: nowrap;
}

.cm-table-spec thead th {
  position: sticky;
  top: 0;
  z-index: 4;
  background: var(--bg-subtle);
  color: var(--text-sub);
  font-weight: 800;
  font-size: calc(10.5px * var(--cm-font-scale));
  border-bottom: 1px solid var(--border-strong);
}

.cm-table-spec .cm-sticky-col-1 {
  position: sticky;
  left: 0;
  width: 54px;
  min-width: 54px;
  max-width: 54px;
  z-index: 2;
  background: var(--bg-card);
}

.cm-table-spec .cm-sticky-col-2 {
  position: sticky;
  left: 54px;
  width: 72px;
  min-width: 72px;
  max-width: 72px;
  z-index: 2;
  background: var(--bg-card);
  border-right: 2px solid var(--border-strong);
}

.cm-table-spec thead th.cm-sticky-col-1,
.cm-table-spec thead th.cm-sticky-col-2 {
  z-index: 6;
  background: var(--bg-subtle);
}

.cm-table-spec tfoot td {
  position: sticky;
  bottom: 0;
  z-index: 3;
  background: var(--bg-subtle);
  border-top: 2px solid var(--border-strong);
  font-weight: 900;
  color: var(--primary);
}

.cm-table-spec tfoot td.cm-sticky-col-1,
.cm-table-spec tfoot td.cm-sticky-col-2 {
  z-index: 5;
  background: var(--bg-subtle);
}

/* 주간(노랑/주황) · 야간(네이비/검정) · 휴무(빨강) 다이아 뱃지 */
.cm-dia-pill-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  max-width: 100%;
}

.cm-holiday-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #dc2626;
  color: #ffffff;
  font-size: calc(9.5px * var(--cm-font-scale));
  font-weight: 900;
  padding: 1px 3px;
  border-radius: 4px;
  line-height: 1.05;
  flex-shrink: 0;
}

.cm-dia-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: calc(10.5px * var(--cm-font-scale));
  font-weight: 900;
  padding: 2px 4px 1px 4px;
  border-radius: 5px;
  line-height: 1.1;
  letter-spacing: -0.4px;
  white-space: nowrap;
  overflow: hidden;
  cursor: pointer;
}

/* 주간(일근): 노란색/주황색 계열 */
.cm-dia-badge.day {
  background: #fef3c7;
  color: #b45309;
  border: 1px solid #f59e0b;
}
.cm-root.cm-dark .cm-dia-badge.day {
  background: rgba(245, 158, 11, 0.24);
  color: #fcd34d;
  border-color: #d97706;
}

/* 야간(박 1·2일차): 주간(앰버)과 조화로운 쿨 인디고/남색 파스텔 계열 */
.cm-dia-badge.night {
  background: #e0e7ff;
  color: #3730a3;
  border: 1px solid #818cf8;
}
.cm-root.cm-dark .cm-dia-badge.night {
  background: rgba(99, 102, 241, 0.24);
  color: #a5b4fc;
  border-color: #6366f1;
}

.cm-capture-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 8px;
  border-radius: 7px;
  border: 1px solid var(--border-strong);
  background: var(--bg-card);
  color: var(--text-main);
  font-size: calc(13px * var(--cm-font-scale));
  line-height: 1.1;
  cursor: pointer;
}
.cm-capture-btn:hover {
  border-color: var(--primary);
  background: var(--bg-subtle);
}

/* 휴무(S, X): 빨간색 계열 */
.cm-dia-badge.rest {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
  cursor: default;
}
.cm-root.cm-dark .cm-dia-badge.rest {
  background: rgba(220, 38, 38, 0.22);
  color: #fca5a5;
  border-color: rgba(248, 113, 113, 0.35);
}

/* 일반교육·연차·병가(21): 보라색 계열 */
.cm-dia-badge.purple {
  background: #f3e8ff;
  color: #7e22ce;
  border: 1px solid #d8b4fe;
  cursor: default;
}
.cm-root.cm-dark .cm-dia-badge.purple {
  background: rgba(147, 51, 234, 0.25);
  color: #d8b4fe;
  border-color: rgba(168, 85, 247, 0.45);
}

/* 월간 캘린더 1px 격자 그리드 */
.cm-cal-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 1px;
  background: var(--grid-line);
  border: 1px solid var(--grid-line);
  border-radius: 10px;
  overflow: hidden;
}

.cm-cal-dow {
  background: var(--bg-subtle);
  text-align: center;
  font-size: calc(10.5px * var(--cm-font-scale));
  font-weight: 800;
  padding: 4px 2px;
  color: var(--text-sub);
}
.cm-cal-dow.sun { color: #dc2626; }
.cm-cal-dow.sat { color: #2563eb; }

.cm-cal-cell {
  background: var(--bg-card);
  min-height: 76px;
  padding: 3px 2px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
  min-width: 0;
}
.cm-cal-cell.empty {
  background: var(--bg-subtle);
  cursor: default;
}
.cm-cal-cell.today {
  box-shadow: inset 0 0 0 2px #2563eb;
  background: var(--primary-bg);
}

.cm-cal-day-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: calc(10.5px * var(--cm-font-scale));
  font-weight: 800;
  padding: 0 1px;
}
.cm-cal-day-num.sun { color: #dc2626; }
.cm-cal-day-num.sat { color: #2563eb; }

.cm-cal-trn-line {
  font-size: calc(9.5px * var(--cm-font-scale));
  font-weight: 800;
  line-height: 1.15;
  color: var(--text-main);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: -0.3px;
}

.cm-cal-time-line {
  font-size: calc(9.5px * var(--cm-font-scale));
  font-weight: 700;
  line-height: 1.15;
  white-space: nowrap;
  letter-spacing: -0.4px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.cm-bulet-orange {
  background: #f59e0b;
  color: #ffffff;
  font-size: 8.5px;
  font-weight: 900;
  border-radius: 3px;
  padding: 0 2px;
  line-height: 1.2;
}

.cm-bulet-green {
  background: #16a34a;
  color: #ffffff;
  font-size: 8.5px;
  font-weight: 900;
  border-radius: 3px;
  padding: 0 2px;
  line-height: 1.2;
}

/* Xcrew 원본 다이아 텍스트 스타일 */
.cm-dia-red { color: #dc2626 !important; }
.cm-root.cm-dark .cm-dia-red { color: #f87171 !important; }
.cm-dia-blue { color: #2563eb !important; }
.cm-root.cm-dark .cm-dia-blue { color: #60a5fa !important; }
.cm-dia-underline { text-decoration: underline; text-underline-offset: 2px; }
.cm-dia-bold { font-weight: 900 !important; }

/* 상태 배지 */
.cm-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 5px;
  border-radius: 5px;
  font-size: calc(10.5px * var(--cm-font-scale));
  font-weight: 800;
  white-space: nowrap;
}
.cm-badge-slate { background: #e2e8f0; color: #334155; }
.cm-root.cm-dark .cm-badge-slate { background: #334155; color: #e2e8f0; }
.cm-badge-blue { background: #dbeafe; color: #1d4ed8; }
.cm-root.cm-dark .cm-badge-blue { background: rgba(37,99,235,0.28); color: #93c5fd; }
.cm-badge-green { background: #dcfce7; color: #15803d; }
.cm-root.cm-dark .cm-badge-green { background: rgba(22,163,74,0.28); color: #86efac; }
.cm-badge-amber { background: #fef3c7; color: #b45309; }
.cm-root.cm-dark .cm-badge-amber { background: rgba(217,119,6,0.28); color: #fcd34d; }
.cm-badge-red { background: #fee2e2; color: #b91c1c; }
.cm-root.cm-dark .cm-badge-red { background: rgba(220,38,38,0.28); color: #fca5a5; }
.cm-badge-action {
  background: #2563eb;
  color: #ffffff;
  cursor: pointer;
  border: none;
  box-shadow: 0 1px 3px rgba(37,99,235,0.3);
}

/* 다이아 부분합 반응형 그리드 (좌우 스크롤 없음) */
.cm-subtotal-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  padding: 8px;
}
.cm-subtotal-item {
  border: 1px solid var(--border-color);
  background: var(--bg-subtle);
  border-radius: 7px;
  padding: 5px 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: calc(11px * var(--cm-font-scale));
}
.cm-subtotal-item.highlight {
  background: var(--primary-bg);
  border-color: var(--primary-border);
}

/* 운용요일 15개 그리드 */
.cm-rundays-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 4px;
  padding: 8px;
}
@media (min-width: 640px) {
  .cm-rundays-grid {
    grid-template-columns: repeat(15, minmax(0, 1fr));
  }
}
.cm-runday-item {
  border: 1px solid var(--border-color);
  background: var(--bg-subtle);
  border-radius: 6px;
  padding: 4px 2px;
  text-align: center;
  font-size: calc(10.5px * var(--cm-font-scale));
  color: var(--text-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.cm-runday-item.checked {
  background: var(--primary-bg);
  border-color: var(--primary);
  color: var(--primary);
  font-weight: 900;
}

/* 모달 (이용안내 / 검색 팝업) */
.cm-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2147483646;
  background: rgba(2, 6, 23, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.cm-modal {
  width: 100%;
  max-width: 420px;
  max-height: 86dvh;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 20px 45px rgba(0, 0, 0, 0.45);
}

.cm-modal-header {
  padding: 13px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-subtle);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: calc(15px * var(--cm-font-scale));
  font-weight: 900;
  color: var(--primary);
}

.cm-modal-body {
  padding: 14px 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: calc(12.5px * var(--cm-font-scale));
  line-height: 1.5;
}

.cm-modal-footer {
  padding: 10px 16px;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 로딩 / 빈 상태 / 토스트 */
.cm-status-box {
  padding: 26px 14px;
  text-align: center;
  color: var(--text-muted);
  font-size: calc(12.5px * var(--cm-font-scale));
  font-weight: 700;
}

.cm-error-box {
  padding: 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  border-radius: 10px;
  font-size: calc(12px * var(--cm-font-scale));
  font-weight: 700;
  text-align: center;
}

.cm-toast {
  position: fixed;
  left: 50%;
  bottom: calc(24px + env(safe-area-inset-bottom, 0px));
  transform: translateX(-50%);
  z-index: 2147483647;
  background: rgba(15, 23, 42, 0.95);
  color: #ffffff;
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 999px;
  padding: 9px 16px;
  font-size: 12.5px;
  font-weight: 800;
  box-shadow: 0 10px 25px rgba(0,0,0,0.35);
  white-space: nowrap;
}

/* 원본 화면 보기 중일 때 우측 하단 복귀 플로팅 버튼 */
.cm-floating-restore {
  position: fixed;
  right: 16px;
  bottom: calc(20px + env(safe-area-inset-bottom, 0px));
  z-index: 2147483647;
  background: linear-gradient(135deg, #1d4ed8, #1e3a8a);
  color: #ffffff;
  border: 2px solid #93c5fd;
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 900;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.45);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
}
`;
