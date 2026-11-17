/**
 * CrewMate View - 메인 Shadow DOM 앱 셸(App Shell), 햄버거 드로어, 재접속 및 이용안내 모달 (v0.3.1)
 */

import { escapeHtml } from '../core/formatters.js';
import { CREWMATE_VIEW_LOGO_DATA_URI } from '../core/logoData.js';
import { XcrewApi } from '../core/xcrewApi.js';
import { MENU_DEFINITIONS, extractUserContextFromDom } from '../core/domExtractor.js';
import { isCachedRestricted, verifyUserAccess } from '../core/banGuard.js';
import {
  loadSettings,
  updateSettings,
  onSettingsChange,
  hasSeenInitialNotice,
  markInitialNoticeSeen,
  isEffectiveDarkMode,
  resolveAppearanceCssVars,
} from '../core/settingsStore.js';
import { CREWMATE_VIEW_CSS } from './styles.js';
import { createMonthlyScheduleView } from './views/monthlyScheduleView.js';
import { createDiaDetailView } from './views/diaDetailView.js';
import { createCrewStatusView } from './views/crewStatusView.js';
import { createWorkSpecView } from './views/workSpecView.js';
import { createNonOpView } from './views/nonOpView.js';
import { createAttendanceView } from './views/attendanceView.js';
import { createSettingsView } from './views/settingsView.js';

const ECOSYSTEM_GUIDE_URL = 'https://crewmate-alpha.pages.dev/guide.html';

export function mountCrewMateView() {
  if (location.hostname !== 'xcrew.korail.com') {
    return;
  }

  // 로그인 화면(/loginView.do)은 원본 그대로 유지
  const pathname = location.pathname || '';
  if (pathname.indexOf('loginView.do') !== -1 || pathname === '/' || document.querySelector('input[name="pwd"]')) {
    return;
  }

  const cachedRestricted = isCachedRestricted();

  const existingHost = document.getElementById('__cm_view_host');
  if (existingHost && existingHost.__cmToggleShow) {
    if (!cachedRestricted) {
      existingHost.__cmToggleShow(true);
    }
    return;
  }
  if (existingHost) {
    existingHost.remove();
  }

  const userContext = extractUserContextFromDom();
  let activeMenuId = userContext.initialMenuId || 'monthly_schedule';
  let isOverlayVisible = true;
  let isDrawerOpen = false;
  let isNoticeModalOpen = !hasSeenInitialNotice();
  let toastMessage = '';
  let toastTimer = null;

  let viewportMeta = document.querySelector('meta[name="viewport"]');
  const originalViewportContent = viewportMeta ? viewportMeta.getAttribute('content') : null;
  const originalBodyOverflow = document.body ? document.body.style.overflow : '';

  function applyMobileViewport(enable) {
    if (!document.head) return;
    if (enable) {
      if (!viewportMeta) {
        viewportMeta = document.createElement('meta');
        viewportMeta.name = 'viewport';
        document.head.appendChild(viewportMeta);
      }
      viewportMeta.setAttribute(
        'content',
        'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover'
      );
      if (document.body) document.body.style.overflow = 'hidden';
    } else {
      if (viewportMeta && originalViewportContent !== null) {
        viewportMeta.setAttribute('content', originalViewportContent);
      }
      if (document.body) document.body.style.overflow = originalBodyOverflow;
    }
  }

  const host = document.createElement('div');
  host.id = '__cm_view_host';
  document.body.appendChild(host);

  const shadow = host.attachShadow({ mode: 'open' });

  function showToast(msg) {
    toastMessage = msg;
    if (toastTimer) clearTimeout(toastTimer);
    renderShell();
    toastTimer = setTimeout(() => {
      toastMessage = '';
      renderShell();
    }, 2400);
  }

  let banVerifyTriggered = false;
  function runBanVerifyOnce(knownEpno) {
    if (banVerifyTriggered && !knownEpno) return;
    banVerifyTriggered = true;
    verifyUserAccess(knownEpno)
      .then((restricted) => {
        if (restricted) {
          teardownView();
        } else if (cachedRestricted) {
          applyMobileViewport(true);
          views[activeMenuId].onActivate();
          renderShell();
        }
      })
      .catch(() => {});
  }

  const appContext = {
    userContext,
    showToast,
    requestRender() {
      renderShell();
    },
    openNoticeModal() {
      isNoticeModalOpen = true;
      renderShell();
    },
    onEpnoResolved(epno) {
      if (epno && !banVerifyTriggered) {
        runBanVerifyOnce(epno);
      }
    },
    navigateToMenu(menuId, params) {
      if (!views[menuId]) return;
      activeMenuId = menuId;
      isDrawerOpen = false;
      views[menuId].onActivate(params);
      renderShell();
    },
  };

  const views = {
    monthly_schedule: createMonthlyScheduleView(appContext),
    dia_detail: createDiaDetailView(appContext),
    crew_status: createCrewStatusView(appContext),
    work_spec: createWorkSpecView(appContext),
    non_op: createNonOpView(appContext),
    attendance: createAttendanceView(appContext),
    settings: createSettingsView(appContext),
  };

  onSettingsChange(() => {
    renderShell();
  });

  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    try {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (loadSettings().themeMode === 'SYSTEM') {
          renderShell();
        }
      });
    } catch (e) {}
  }

  async function handleReloginClick() {
    showToast('🔄 Xcrew 세션 연결 확인 중...');
    try {
      await XcrewApi.fetchNotiCount();
      const activeView = views[activeMenuId];
      if (activeView && typeof activeView.reload === 'function') {
        activeView.reload();
      }
      showToast('✅ 세션이 정상 유지되어 화면을 새로고침했습니다.');
    } catch (err) {
      location.href = 'https://xcrew.korail.com/loginView.do';
    }
  }

  function setOverlayVisible(visible) {
    isOverlayVisible = visible;
    isDrawerOpen = false;
    applyMobileViewport(visible);
    renderShell();
  }

  host.__cmToggleShow = setOverlayVisible;

  function resolveCurrentMenuLabel() {
    if (activeMenuId === 'settings') return '설정';
    const m = MENU_DEFINITIONS.find((it) => it.id === activeMenuId);
    return m ? m.label : 'CrewMate View';
  }

  function renderDrawer(settings) {
    if (!isDrawerOpen) return '';
    let menuButtons = '';
    MENU_DEFINITIONS.forEach((m) => {
      const isAct = m.id === activeMenuId;
      menuButtons += `
        <button type="button" class="cm-drawer-item ${isAct ? 'active' : ''}" data-drawer-menu="${m.id}">
          <span>${m.icon}</span>
          <span>${escapeHtml(m.label)}</span>
        </button>
      `;
    });

    const themeLabel =
      settings.themeMode === 'DARK'
        ? '🌙 다크 모드'
        : settings.themeMode === 'LIGHT'
        ? '☀️ 라이트 모드'
        : '🌓 시스템 테마';

    return `
      <div class="cm-drawer-backdrop" id="__cm_drawer_bg">
        <aside class="cm-drawer" onclick="event.stopPropagation();">
          <div class="cm-drawer-header">
            <div class="cm-drawer-brand">
              <div class="cm-drawer-brand-left">
                <img class="cm-logo-img" src="${CREWMATE_VIEW_LOGO_DATA_URI}" alt="CrewMate View" />
                <span>CrewMate View</span>
              </div>
              <button type="button" class="cm-drawer-close" id="__cm_drawer_close">✕</button>
            </div>
            <div class="cm-drawer-profile">
              <div style="font-weight:900;">
                <span style="color:#facc15;">${escapeHtml(userContext.affiliation)}</span>
                <span> ${escapeHtml(userContext.userName)}</span>
              </div>
              ${
                userContext.lastLogin
                  ? `<div style="font-size:10.5px;color:#94a3b8;margin-top:2px;">최근 접속 ${escapeHtml(userContext.lastLogin)}</div>`
                  : ''
              }
            </div>
          </div>
          <div class="cm-drawer-list">
            ${menuButtons}
          </div>
          <div class="cm-drawer-footer">
            <div style="display:flex;gap:6px;">
              <button type="button" class="cm-btn" id="__cm_drawer_theme" style="flex:1;height:36px;">
                ${themeLabel}
              </button>
              <button type="button" class="cm-btn ${activeMenuId === 'settings' ? 'cm-btn-primary' : ''}" data-drawer-menu="settings" style="flex:1;height:36px;">
                ⚙️ 설정
              </button>
            </div>
            <button type="button" class="cm-drawer-raw-btn" id="__cm_drawer_raw">
              🖥️ 원본 화면 전환
            </button>
          </div>
        </aside>
      </div>
    `;
  }

  function renderNoticeModal(isDark) {
    if (!isNoticeModalOpen) return '';
    const warnBg = isDark ? '#2c2214' : '#fffbeb';
    const warnBorder = isDark ? '#5a4016' : '#fde68a';
    const warnTitle = isDark ? '#fde68a' : '#92400e';
    const warnText = isDark ? '#f6e5b8' : '#78350f';

    const secBg = isDark ? '#2a161e' : '#fff1f2';
    const secBorder = isDark ? '#6b2137' : '#fecdd3';
    const secTitle = isDark ? '#fda4af' : '#9f1239';
    const secText = isDark ? '#fecdd3' : '#881337';

    return `
      <div class="cm-modal-backdrop" id="__cm_notice_bg">
        <div class="cm-modal" onclick="event.stopPropagation();">
          <div class="cm-modal-header">
            <span>ℹ️ 이용 안내 및 유의사항</span>
          </div>
          <div class="cm-modal-body">
            <div style="background:var(--bg-subtle);border:1px solid var(--border-color);border-radius:12px;padding:10px 12px;">
              <div style="font-weight:900;margin-bottom:4px;color:var(--text-main);">📖 비공식 실시간 뷰어 안내</div>
              <div style="color:var(--text-sub);font-size:calc(12px * var(--cm-font-scale));">
                이 도구는 제작자 개인의 업무 편의를 목적으로 개발된 비공식 인페이지 뷰어입니다. Xcrew 로그인 세션 내에서만 동작하며, 기능상 다소 오류가 있거나 예고 없이 서비스가 중단될 수 있으니 너른 양해 부탁드립니다.
              </div>
            </div>

            <div style="background:${warnBg};border:1px solid ${warnBorder};border-radius:12px;padding:10px 12px;">
              <div style="font-weight:900;margin-bottom:4px;color:${warnTitle};">⚠️ [필독] 데이터 참고 및 면책 공지</div>
              <div style="color:${warnText};font-size:calc(12px * var(--cm-font-scale));">
                본 화면에서 제공하는 모든 조회 결과 및 데이터는 참고용일 뿐입니다. 화면 표출 오류로 인해 발생한 근태 문제나 불이익 등에 대해서도 제작자는 책임지지 않습니다.<br /><br />
                <b>정확한 근무 내역은 반드시 공식 시스템(원본 화면)을 통해 교차 확인하시기 바랍니다.</b>
              </div>
            </div>

            <div style="background:${secBg};border:1px solid ${secBorder};border-radius:12px;padding:10px 12px;">
              <div style="font-weight:900;margin-bottom:4px;color:${secTitle};">🔒 [필독] 개인정보 보안 한계 및 면책 고지</div>
              <div style="color:${secText};font-size:calc(11.5px * var(--cm-font-scale));">
                본 생태계(CrewMate 앱 · Alpha 웹 · View)는 보안 전문 기업이 아닌, 개발·보안 전문 지식이 없는 현직 승무원 개인이 AI 보조 도구(바이브 코딩)를 활용해 제작한 비공식 프로젝트입니다.<br /><br />
                최근 빈번한 사이버 침해 사고나 브라우저·확장앱·외부 인프라의 취약점 등 비전문가 개인이 통제·대응하기 어려운 보안적 한계가 존재할 수 있으므로, 사용자는 이를 인지하고 본인의 자율적 판단과 책임하에 이용해야 하며 예기치 못한 정보 유출·오류 등으로 인한 직·간접적 손해에 대해 제작자는 법적·실무적 책임을 지지 않습니다.
              </div>
            </div>

            <a href="${ECOSYSTEM_GUIDE_URL}" target="_blank" rel="noopener noreferrer" style="display:flex;align-items:center;justify-content:space-between;text-decoration:none;background:var(--primary-bg);border:1px solid var(--primary-border);color:var(--primary);border-radius:12px;padding:10px 12px;font-weight:900;font-size:calc(12px * var(--cm-font-scale));">
              <span>📘 CrewMate 생태계 종합 안내서 (앱·웹·뷰 설치 및 사용법)</span>
              <span>↗</span>
            </a>

            <div style="font-size:calc(11.5px * var(--cm-font-scale));color:var(--text-muted);padding-top:2px;font-weight:700;">
              제작/관리: <b>안산승무사업소 윤영준</b>
            </div>
          </div>
          <div class="cm-modal-footer">
            <button type="button" class="cm-btn cm-btn-primary" id="__cm_notice_confirm" style="width:100%;height:38px;">
              확인했습니다
            </button>
          </div>
        </div>
      </div>
    `;
  }

  function renderShell() {
    const settings = loadSettings();
    const isDark = isEffectiveDarkMode(settings);
    const cssVars = resolveAppearanceCssVars(settings);

    if (!isOverlayVisible) {
      shadow.innerHTML = `
        <style>${CREWMATE_VIEW_CSS}</style>
        <button type="button" class="cm-floating-restore" id="__cm_restore_btn">
          <img class="cm-logo-img" style="width:20px;height:20px;border-radius:5px;" src="${CREWMATE_VIEW_LOGO_DATA_URI}" alt="CrewMate View" />
          <span>CrewMate View 열기</span>
        </button>
      `;
      const restoreBtn = shadow.getElementById('__cm_restore_btn');
      if (restoreBtn) {
        restoreBtn.onclick = () => setOverlayVisible(true);
      }
      return;
    }

    const activeView = views[activeMenuId] || views.monthly_schedule;
    const menuTitle = resolveCurrentMenuLabel();

    shadow.innerHTML = `
      <style>${CREWMATE_VIEW_CSS}</style>
      <div class="cm-root ${isDark ? 'cm-dark' : ''}" style="${cssVars}">
        <header class="cm-header">
          <div class="cm-header-left">
            <button type="button" class="cm-hamburger-btn" id="__cm_menu_btn" title="메뉴 열기">☰</button>
            <img class="cm-logo-img" src="${CREWMATE_VIEW_LOGO_DATA_URI}" alt="CrewMate View" />
            <div class="cm-header-title-wrap">
              <div class="cm-header-menu-title">${escapeHtml(menuTitle)}</div>
            </div>
          </div>
          <button type="button" class="cm-relogin-btn" id="__cm_relogin_btn" title="Xcrew 세션 갱신 또는 재로그인">
            🔄 재접속
          </button>
        </header>
        <main class="cm-main" id="__cm_main_area">
          <div class="cm-container" id="__cm_view_container">
            ${activeView.render()}
          </div>
        </main>
        ${renderDrawer(settings)}
        ${renderNoticeModal(isDark)}
        ${toastMessage ? `<div class="cm-toast">${escapeHtml(toastMessage)}</div>` : ''}
      </div>
    `;

    const menuBtn = shadow.getElementById('__cm_menu_btn');
    if (menuBtn) {
      menuBtn.onclick = () => {
        isDrawerOpen = true;
        renderShell();
      };
    }

    const reloginBtn = shadow.getElementById('__cm_relogin_btn');
    if (reloginBtn) {
      reloginBtn.onclick = handleReloginClick;
    }

    const drawerBg = shadow.getElementById('__cm_drawer_bg');
    if (drawerBg) {
      drawerBg.onclick = () => {
        isDrawerOpen = false;
        renderShell();
      };
    }

    const drawerClose = shadow.getElementById('__cm_drawer_close');
    if (drawerClose) {
      drawerClose.onclick = () => {
        isDrawerOpen = false;
        renderShell();
      };
    }

    const drawerTheme = shadow.getElementById('__cm_drawer_theme');
    if (drawerTheme) {
      drawerTheme.onclick = () => {
        const order = ['SYSTEM', 'LIGHT', 'DARK'];
        const nextIdx = (order.indexOf(settings.themeMode) + 1) % order.length;
        updateSettings({ themeMode: order[nextIdx] });
      };
    }

    const drawerRaw = shadow.getElementById('__cm_drawer_raw');
    if (drawerRaw) {
      drawerRaw.onclick = () => setOverlayVisible(false);
    }

    shadow.querySelectorAll('[data-drawer-menu]').forEach((btn) => {
      btn.onclick = () => {
        const targetId = btn.getAttribute('data-drawer-menu');
        if (targetId) {
          appContext.navigateToMenu(targetId);
        }
      };
    });

    const noticeConfirm = shadow.getElementById('__cm_notice_confirm');
    if (noticeConfirm) {
      noticeConfirm.onclick = () => {
        markInitialNoticeSeen();
        isNoticeModalOpen = false;
        renderShell();
      };
    }

    const viewContainer = shadow.getElementById('__cm_view_container');
    if (viewContainer && typeof activeView.bindEvents === 'function') {
      activeView.bindEvents(viewContainer);
    }
  }

  function teardownView() {
    applyMobileViewport(false);
    if (host && host.parentNode) {
      host.parentNode.removeChild(host);
    }
  }

  if (!cachedRestricted) {
    applyMobileViewport(true);
    views[activeMenuId].onActivate();
    renderShell();
  }

  if (cachedRestricted || activeMenuId !== 'monthly_schedule') {
    runBanVerifyOnce();
  }
}
