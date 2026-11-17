/**
 * CrewMate View - [메뉴 5] 비실동 현황 (extrNonAtulPsttList.do)
 */

import { XcrewApi } from '../../core/xcrewApi.js';
import {
  escapeHtml,
  getTodayYm,
  XcrewRawFormatter,
} from '../../core/formatters.js';

export function createNonOpView(appContext) {
  let selectedYear = getTodayYm().slice(0, 4);
  let viewMode = 'SUMMARY'; // 'TABLE' ('표') | 'SUMMARY' ('요약' - 기본값)
  let expandedGroups = {};
  let loading = false;
  let errorMsg = '';
  let rows = [];
  let hasInitialized = false;

  async function loadNonOpByYear(yearStr) {
    selectedYear = String(yearStr || getTodayYm().slice(0, 4)).slice(0, 4);
    loading = true;
    errorMsg = '';
    appContext.requestRender();
    try {
      const res = await XcrewApi.fetchNonOpStatus(selectedYear + '0101', selectedYear + '1231');
      rows = Array.isArray(res && res.data) ? res.data : [];
    } catch (err) {
      errorMsg = err.message || '비실동 현황 조회 중 오류가 발생했습니다.';
    } finally {
      loading = false;
      appContext.requestRender();
    }
  }

  function renderTableView() {
    if (!rows.length) {
      return '<div class="cm-card"><div class="cm-status-box">조회된 비실동 현황 내역이 없습니다.</div></div>';
    }

    let tbody = '';
    rows.forEach((r) => {
      tbody += `
        <tr>
          <td style="font-size:calc(10.5px * var(--cm-font-scale));">${escapeHtml(XcrewRawFormatter.formatYMD(r.nonAtulStDt))}</td>
          <td style="font-size:calc(10.5px * var(--cm-font-scale));">${escapeHtml(XcrewRawFormatter.formatYMD(r.nonAtulClsDt))}</td>
          <td><span class="cm-badge cm-badge-blue">${escapeHtml(r.nonAtulClsfCdNm || '')}</span></td>
          <td style="font-weight:900;">${escapeHtml(r.nonAtulDno != null ? r.nonAtulDno : '')}</td>
          <td style="white-space:normal;word-break:keep-all;text-align:left;padding-left:5px;">${escapeHtml(r.nonAtulRsnCont || '')}</td>
        </tr>
      `;
    });

    return `
      <div class="cm-card">
        <div class="cm-card-header">
          <span>🏖️ 비실동현황</span>
        </div>
        <table class="cm-table-fit">
          <colgroup>
            <col style="width:22%;" />
            <col style="width:22%;" />
            <col style="width:18%;" />
            <col style="width:11%;" />
            <col style="width:27%;" />
          </colgroup>
          <thead>
            <tr>
              <th>시작일자</th>
              <th>종료일자</th>
              <th>비실동명</th>
              <th>일수</th>
              <th>사유</th>
            </tr>
          </thead>
          <tbody>${tbody}</tbody>
        </table>
      </div>
    `;
  }

  function renderSummaryView() {
    if (!rows.length) {
      return '<div class="cm-card"><div class="cm-status-box">조회된 비실동 현황 내역이 없습니다.</div></div>';
    }

    const groupsMap = {};
    const groupOrder = [];
    rows.forEach((r) => {
      const cat = String(r.nonAtulClsfCdNm || '기타').trim() || '기타';
      if (!groupsMap[cat]) {
        groupsMap[cat] = { name: cat, totalDays: 0, items: [] };
        groupOrder.push(cat);
      }
      const d = parseFloat(String(r.nonAtulDno || '0'));
      if (!isNaN(d)) groupsMap[cat].totalDays += d;
      groupsMap[cat].items.push(r);
    });

    let cardsHtml = '';
    groupOrder.forEach((cat) => {
      const g = groupsMap[cat];
      const isOpen = !!expandedGroups[cat];
      const daysStr = (Math.round(g.totalDays * 10) / 10).toString();

      let detailRows = '';
      if (isOpen) {
        let trs = '';
        g.items.forEach((it) => {
          trs += `
            <tr>
              <td style="font-size:calc(10.5px * var(--cm-font-scale));">${escapeHtml(XcrewRawFormatter.formatYMD(it.nonAtulStDt))}</td>
              <td style="font-size:calc(10.5px * var(--cm-font-scale));">${escapeHtml(XcrewRawFormatter.formatYMD(it.nonAtulClsDt))}</td>
              <td style="font-weight:900;">${escapeHtml(it.nonAtulDno != null ? it.nonAtulDno : '')}일</td>
              <td style="white-space:normal;word-break:keep-all;text-align:left;padding-left:6px;">${escapeHtml(it.nonAtulRsnCont || '-')}</td>
            </tr>
          `;
        });
        detailRows = `
          <table class="cm-table-fit">
            <colgroup>
              <col style="width:26%;" />
              <col style="width:26%;" />
              <col style="width:14%;" />
              <col style="width:34%;" />
            </colgroup>
            <thead>
              <tr>
                <th>시작일자</th>
                <th>종료일자</th>
                <th>일수</th>
                <th>사유</th>
              </tr>
            </thead>
            <tbody>${trs}</tbody>
          </table>
        `;
      }

      cardsHtml += `
        <div class="cm-card">
          <div class="cm-card-header" data-action="toggle-group" data-group="${escapeHtml(cat)}" style="cursor:pointer;">
            <div style="display:flex;align-items:center;gap:8px;">
              <span class="cm-badge cm-badge-blue">${escapeHtml(g.name)}</span>
              <span style="font-weight:900;color:var(--primary);">총 ${escapeHtml(daysStr)}일</span>
              <span style="font-size:11px;color:var(--text-muted);">(${g.items.length}건)</span>
            </div>
            <span style="font-size:12px;color:var(--text-sub);font-weight:800;">${isOpen ? '▲ 접기' : '▼ 상세'}</span>
          </div>
          ${detailRows}
        </div>
      `;
    });

    return cardsHtml;
  }

  return {
    onActivate() {
      if (!hasInitialized) {
        hasInitialized = true;
        loadNonOpByYear(selectedYear);
      }
    },

    reload() {
      loadNonOpByYear(selectedYear);
    },

    render() {
      const curY = parseInt(selectedYear, 10);
      let yearOpts = '';
      for (let y = 2000; y <= 2040; y++) {
        yearOpts += `<option value="${y}" ${y === curY ? 'selected' : ''}>${y}년</option>`;
      }

      return `
        <div class="cm-control-card">
          <div class="cm-control-row" style="justify-content:space-between;">
            <div style="display:flex;align-items:center;gap:4px;">
              <select id="__cm_nop_year" class="cm-select">${yearOpts}</select>
              <button type="button" class="cm-btn cm-btn-primary" data-action="search">조회</button>
            </div>
            <div style="display:flex;align-items:center;gap:4px;">
              <button type="button" class="cm-pill-btn ${viewMode === 'TABLE' ? 'active' : ''}" data-action="mode-table">표</button>
              <button type="button" class="cm-pill-btn ${viewMode === 'SUMMARY' ? 'active' : ''}" data-action="mode-summary">요약</button>
            </div>
          </div>
        </div>
        ${
          loading
            ? '<div class="cm-card"><div class="cm-status-box">⏳ 비실동 현황을 불러오는 중...</div></div>'
            : errorMsg
            ? `<div class="cm-error-box">${escapeHtml(errorMsg)}</div>`
            : viewMode === 'TABLE'
            ? renderTableView()
            : renderSummaryView()
        }
      `;
    },

    bindEvents(container) {
      const yearSel = container.querySelector('#__cm_nop_year');
      if (yearSel) {
        yearSel.onchange = () => {
          loadNonOpByYear(yearSel.value);
        };
      }

      container.querySelectorAll('[data-action]').forEach((el) => {
        el.onclick = () => {
          const act = el.getAttribute('data-action');
          if (act === 'search') {
            const y = yearSel ? yearSel.value : selectedYear;
            loadNonOpByYear(y);
          } else if (act === 'mode-table') {
            viewMode = 'TABLE';
            appContext.requestRender();
          } else if (act === 'mode-summary') {
            viewMode = 'SUMMARY';
            appContext.requestRender();
          } else if (act === 'toggle-group') {
            const grp = el.getAttribute('data-group');
            if (grp) {
              expandedGroups[grp] = !expandedGroups[grp];
              appContext.requestRender();
            }
          }
        };
      });
    },
  };
}
