/**
 * CrewMate View - [메뉴 7] 설정 (환경설정, 실험실, 이용안내, 버전 표기)
 */

import { escapeHtml } from '../../core/formatters.js';
import {
  CREWMATE_VIEW_VERSION,
  loadSettings,
  updateSettings,
} from '../../core/settingsStore.js';

export function createSettingsView(appContext) {
  function renderOptionButtons(groupKey, currentVal, options) {
    return options
      .map((opt) => {
        const isAct = currentVal === opt.value;
        return `<button type="button" class="cm-pill-btn ${isAct ? 'active' : ''}" data-setting-key="${groupKey}" data-setting-val="${escapeHtml(opt.value)}" style="flex:1;">${escapeHtml(opt.label)}</button>`;
      })
      .join('');
  }

  return {
    onActivate() {},
    reload() {
      appContext.requestRender();
    },

    render() {
      const s = loadSettings();

      return `
        <div class="cm-card">
          <div class="cm-card-header"><span>🎨 화면 및 가독성 설정</span></div>
          <div style="padding:12px;display:flex;flex-direction:column;gap:14px;">
            <div>
              <div style="font-size:calc(12px * var(--cm-font-scale));font-weight:800;margin-bottom:6px;color:var(--text-main);">테마 모드</div>
              <div style="display:flex;gap:6px;">
                ${renderOptionButtons('themeMode', s.themeMode, [
                  { value: 'SYSTEM', label: '시스템 설정' },
                  { value: 'LIGHT', label: '☀️ 라이트' },
                  { value: 'DARK', label: '🌙 다크' },
                ])}
              </div>
            </div>

            <div>
              <div style="font-size:calc(12px * var(--cm-font-scale));font-weight:800;margin-bottom:6px;color:var(--text-main);">표 상하 여백 (전체 메뉴 공통)</div>
              <div style="display:flex;gap:6px;">
                ${renderOptionButtons('rowPadding', s.rowPadding, [
                  { value: 'COMPACT', label: '작게 (기본)' },
                  { value: 'NORMAL', label: '보통' },
                  { value: 'RELAXED', label: '크게' },
                ])}
              </div>
            </div>

            <div>
              <div style="font-size:calc(12px * var(--cm-font-scale));font-weight:800;margin-bottom:6px;color:var(--text-main);">글자 크기</div>
              <div style="display:flex;gap:6px;">
                ${renderOptionButtons('fontScale', s.fontScale, [
                  { value: 'SMALL', label: '작게' },
                  { value: 'NORMAL', label: '보통 (기본)' },
                  { value: 'LARGE', label: '크게' },
                ])}
              </div>
            </div>
          </div>
        </div>

        <div class="cm-card">
          <div class="cm-card-header"><span>🧪 실험실 (Labs)</span></div>
          <div style="padding:12px;display:flex;align-items:center;justify-content:space-between;gap:10px;">
            <div style="min-width:0;">
              <div style="font-size:calc(12.5px * var(--cm-font-scale));font-weight:900;color:var(--text-main);">🗺️ 승무 행로도 시각화 뷰</div>
              <div style="font-size:calc(11px * var(--cm-font-scale));color:var(--text-sub);margin-top:2px;">활성화 시 다이아 조회 화면 최하단에 [시정표 ↔ 행로도] 전환 버튼을 표시합니다.</div>
            </div>
            <button type="button" class="cm-pill-btn ${s.labRouteDiagram ? 'active' : ''}" data-action="toggle-route-diagram" style="min-width:64px;">
              ${s.labRouteDiagram ? 'ON' : 'OFF'}
            </button>
          </div>
        </div>

        <div class="cm-card">
          <div class="cm-card-header"><span>ℹ️ 안내 및 정보</span></div>
          <div style="padding:12px;display:flex;flex-direction:column;gap:8px;">
            <button type="button" class="cm-btn" data-action="open-notice" style="width:100%;height:38px;justify-content:center;font-size:calc(12.5px * var(--cm-font-scale));">
              📖 이용 안내 및 면책 공지 확인
            </button>
          </div>
        </div>

        <div style="text-align:center;padding:8px 0 4px;font-size:11px;color:var(--text-muted);font-weight:700;">
          CrewMate View ${escapeHtml(CREWMATE_VIEW_VERSION)}
        </div>
      `;
    },

    bindEvents(container) {
      container.querySelectorAll('[data-setting-key]').forEach((btn) => {
        btn.onclick = () => {
          const k = btn.getAttribute('data-setting-key');
          const v = btn.getAttribute('data-setting-val');
          if (k && v) {
            updateSettings({ [k]: v });
          }
        };
      });

      const labBtn = container.querySelector('[data-action="toggle-route-diagram"]');
      if (labBtn) {
        labBtn.onclick = () => {
          const cur = loadSettings().labRouteDiagram;
          updateSettings({ labRouteDiagram: !cur });
        };
      }

      const noticeBtn = container.querySelector('[data-action="open-notice"]');
      if (noticeBtn) {
        noticeBtn.onclick = () => {
          if (typeof appContext.openNoticeModal === 'function') {
            appContext.openNoticeModal();
          }
        };
      }
    },
  };
}
