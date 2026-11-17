/**
 * CrewMate View - [메뉴 6] 출무현황 (extrTrnQryItmtGtwc.do)
 * - 하루 1~4건 표출 특성에 맞춰 모바일 카드 뷰로 전면 개편
 */

import { XcrewApi } from '../../core/xcrewApi.js';
import {
  escapeHtml,
  getTodayYmd,
  XcrewRawFormatter,
} from '../../core/formatters.js';

export function createAttendanceView(appContext) {
  let pjtDt = getTodayYmd();
  let loading = false;
  let errorMsg = '';
  let rows = [];
  let hasInitialized = false;

  async function loadAttendance(targetYmd) {
    pjtDt = targetYmd;
    const empNm = (appContext.userContext.userName || '').trim();

    loading = true;
    errorMsg = '';
    appContext.requestRender();
    try {
      const res = await XcrewApi.fetchAttendanceStatus(pjtDt, empNm);
      rows = Array.isArray(res && res.data) ? res.data : [];
    } catch (err) {
      errorMsg = err.message || '출무현황 조회 중 오류가 발생했습니다.';
    } finally {
      loading = false;
      appContext.requestRender();
    }
  }

  async function handleMidAttendance(row) {
    if (!row) return;
    const payload = {
      crwGtwcDt: row.gtwcEpctDt || '',
      crwGtwcTm: row.gtwcEpctTm || '',
      gtwcPlcBlgCd: row.gtwcEpctBlgCd || '',
    };
    if (!confirm('열차 [' + (row.trnNo || '-') + '] 도중출무 처리를 진행하시겠습니까?')) {
      return;
    }
    try {
      await XcrewApi.registerMidAttendance(payload);
      alert('출무가 완료되었습니다.');
      await loadAttendance(pjtDt);
    } catch (err) {
      alert('도중출무 처리 중 오류: ' + (err.message || err));
    }
  }

  function renderCards() {
    if (!rows.length) {
      return `
        <div class="cm-card">
          <div class="cm-card-header"><span>🚆 출무현황 조회 (0건)</span></div>
          <div class="cm-status-box">조회된 출무현황 데이터가 없습니다.</div>
        </div>
      `;
    }

    const crwPlnDvCd = appContext.userContext.crwPlnDvCd || '32';
    let cardsHtml = '';

    rows.forEach((r, idx) => {
      const gubun = XcrewRawFormatter.resolveAttendanceGubun(r.gtwcGubunCd, r, crwPlnDvCd);
      const epctStr = XcrewRawFormatter.formatMMDD_HHMM(r.gtwcEpctDtm) || '-';
      const tnsmStr = XcrewRawFormatter.formatMMDD_HHMM(r.tnsmDttm) || '-';
      const dlay = Number(r.dlayTm) || 0;

      const gubunHtml = gubun.canDoGtwc
        ? `<button type="button" class="cm-badge cm-badge-action" data-action="do-gtwc" data-idx="${idx}" style="padding:4px 10px;">⚡ 도중출무</button>`
        : `<span class="cm-badge ${gubun.badgeClass}">${escapeHtml(gubun.label)}</span>`;

      cardsHtml += `
        <div style="border:1px solid var(--border-color);background:var(--bg-subtle);border-radius:10px;padding:10px 12px;display:flex;flex-direction:column;gap:8px;">
          <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;border-bottom:1px solid var(--border-color);padding-bottom:7px;">
            <div style="display:flex;align-items:center;gap:8px;">
              <span style="font-size:calc(14.5px * var(--cm-font-scale));font-weight:900;color:var(--primary);">열차 ${escapeHtml(r.trnNo || '-')}</span>
              <span class="cm-badge cm-badge-slate">${escapeHtml(r.trnSttNm || '-')}</span>
              ${
                dlay > 0
                  ? `<span class="cm-badge cm-badge-red">지연 ${escapeHtml(r.dlayTm)}분</span>`
                  : `<span style="font-size:calc(11px * var(--cm-font-scale));color:var(--text-muted);font-weight:700;">지연 ${escapeHtml(r.dlayTm != null ? r.dlayTm : '0')}</span>`
              }
            </div>
            <div>${gubunHtml}</div>
          </div>
          <div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px;font-size:calc(11.5px * var(--cm-font-scale));">
            <div style="background:var(--bg-card);border:1px solid var(--border-color);border-radius:7px;padding:6px 8px;display:flex;justify-content:space-between;align-items:center;">
              <span style="color:var(--text-sub);font-weight:700;">예정일시</span>
              <span style="font-weight:900;color:var(--text-main);">${escapeHtml(epctStr)}</span>
            </div>
            <div style="background:var(--bg-card);border:1px solid var(--border-color);border-radius:7px;padding:6px 8px;display:flex;justify-content:space-between;align-items:center;">
              <span style="color:var(--text-sub);font-weight:700;">출무일시</span>
              <span style="font-weight:900;color:var(--primary);">${escapeHtml(tnsmStr)}</span>
            </div>
            <div style="background:var(--bg-card);border:1px solid var(--border-color);border-radius:7px;padding:6px 8px;display:flex;justify-content:space-between;align-items:center;">
              <span style="color:var(--text-sub);font-weight:700;">출무소속</span>
              <span style="font-weight:800;">${escapeHtml(r.gtwcEpctBlgNm || '-')}</span>
            </div>
            <div style="background:var(--bg-card);border:1px solid var(--border-color);border-radius:7px;padding:6px 8px;display:flex;justify-content:space-between;align-items:center;">
              <span style="color:var(--text-sub);font-weight:700;">동력차 / 현차수</span>
              <span style="font-weight:800;">${escapeHtml(r.lcm || '-')} / ${escapeHtml(r.trnCno != null ? r.trnCno : '-')}</span>
            </div>
          </div>
        </div>
      `;
    });

    return `
      <div class="cm-card">
        <div class="cm-card-header">
          <span>🚆 출무현황 조회 (${rows.length}건)</span>
        </div>
        <div style="padding:10px;display:flex;flex-direction:column;gap:8px;">
          ${cardsHtml}
        </div>
      </div>
    `;
  }

  return {
    onActivate() {
      if (!hasInitialized) {
        hasInitialized = true;
        loadAttendance(pjtDt);
      }
    },

    reload() {
      loadAttendance(pjtDt);
    },

    render() {
      const dateInputVal = XcrewRawFormatter.formatYMD(pjtDt);
      return `
        <div class="cm-control-card">
          <div class="cm-control-row">
            <input type="date" id="__cm_att_date" class="cm-input" style="flex:1;min-width:0;text-align:center;" value="${escapeHtml(dateInputVal)}" />
            <button type="button" class="cm-btn" data-action="today">오늘</button>
            <button type="button" class="cm-btn cm-btn-primary" data-action="search">조회</button>
          </div>
        </div>
        ${
          loading
            ? '<div class="cm-card"><div class="cm-status-box">⏳ 출무현황을 불러오는 중...</div></div>'
            : errorMsg
            ? `<div class="cm-error-box">${escapeHtml(errorMsg)}</div>`
            : renderCards()
        }
      `;
    },

    bindEvents(container) {
      const dateInput = container.querySelector('#__cm_att_date');
      if (dateInput) {
        dateInput.onchange = () => {
          const v = (dateInput.value || '').replace(/-/g, '').trim();
          if (v.length === 8) loadAttendance(v);
        };
      }

      container.querySelectorAll('[data-action]').forEach((el) => {
        el.onclick = () => {
          const act = el.getAttribute('data-action');
          if (act === 'today') loadAttendance(getTodayYmd());
          else if (act === 'search') loadAttendance(pjtDt);
          else if (act === 'do-gtwc') {
            const idx = parseInt(el.getAttribute('data-idx'), 10);
            if (!isNaN(idx) && rows[idx]) {
              handleMidAttendance(rows[idx]);
            }
          }
        };
      });
    },
  };
}
