/**
 * CrewMate View - [메뉴 4] 개인근무명세서 (extrIndWrkSpecList.do)
 */

import { XcrewApi } from '../../core/xcrewApi.js';
import {
  escapeHtml,
  pad2,
  getTodayYm,
  XcrewRawFormatter,
} from '../../core/formatters.js';

const SPEC_SUB_COLUMNS = [
  { key: 'hldyTnum', label: '휴일' },
  { key: 'extnTnum', label: '연장' },
  { key: 'nihtTnum', label: '야간' },
  { key: 'pjtTnum200', label: '운전' },
  { key: 'pjtTnum300', label: '편승' },
  { key: 'pjtTnum100', label: '준비' },
  { key: 'pjtTnum800', label: '정리' },
  { key: 'pjtTnum500', label: '감시' },
  { key: 'pjtTnum900', label: '비상' },
  { key: 'pjtTnumA00', label: '지연' },
  { key: 'pjtTnumB00', label: '승계' },
  { key: 'pjtTnum700', label: '휴계' },
  { key: 'pjtTnumS00', label: '숙박' },
  { key: 'pjtTnumC00', label: '단절' },
];

export function createWorkSpecView(appContext) {
  let currentYm = getTodayYm();
  let loading = false;
  let errorMsg = '';
  let rows = [];
  let hasInitialized = false;

  async function loadWorkSpec(ym) {
    currentYm = ym;
    loading = true;
    errorMsg = '';
    appContext.requestRender();
    try {
      const res = await XcrewApi.fetchIndWorkSpec(ym + '01');
      rows = Array.isArray(res && res.data) ? res.data : [];
    } catch (err) {
      errorMsg = err.message || '개인근무명세서 조회 중 오류가 발생했습니다.';
    } finally {
      loading = false;
      appContext.requestRender();
    }
  }

  function isNonZeroTime(formattedStr) {
    if (!formattedStr) return false;
    const cleaned = formattedStr.replace(/[0:\-\s]/g, '');
    return cleaned.length > 0;
  }

  function render18ColTable() {
    if (!rows.length) {
      return '<div class="cm-card"><div class="cm-status-box">조회된 개인근무명세서 데이터가 없습니다.</div></div>';
    }

    let thSub = '';
    SPEC_SUB_COLUMNS.forEach((c) => {
      thSub += `<th>${escapeHtml(c.label)}</th>`;
    });

    let totalTotMins = 0;
    let totalDistRaw = 0;
    const subTotalMinsByKey = {};
    SPEC_SUB_COLUMNS.forEach((c) => {
      subTotalMinsByKey[c.key] = 0;
    });

    let tbody = '';
    rows.forEach((r) => {
      const dayInfo = XcrewRawFormatter.formatDayOnlyWithDow(r.pjtDt);
      const dayColor =
        dayInfo.dowIdx === 0 ? 'color:#dc2626;' : dayInfo.dowIdx === 6 ? 'color:#2563eb;' : '';
      const diaHtml = XcrewRawFormatter.renderDiaNoHtml(r.pdiaNo, r, 'WORK_SPEC');
      const totTm = XcrewRawFormatter.toHHMM(r.totPjtTnum);
      const totDst = r.totPjtDst != null ? r.totPjtDst : '';

      totalTotMins += XcrewRawFormatter.parseMinutes(r.totPjtTnum);
      const dstNum = parseFloat(String(r.totPjtDst || '0'));
      if (!isNaN(dstNum)) totalDistRaw += dstNum;

      let subTds = '';
      SPEC_SUB_COLUMNS.forEach((c) => {
        const rawVal = r[c.key];
        const v = XcrewRawFormatter.toHHMM(rawVal);
        subTotalMinsByKey[c.key] += XcrewRawFormatter.parseMinutes(rawVal);
        const active = isNonZeroTime(v);
        subTds += `<td style="${
          active ? 'font-weight:800;color:var(--primary);' : 'color:var(--text-muted);'
        }">${escapeHtml(v)}</td>`;
      });

      tbody += `
        <tr>
          <td class="cm-sticky-col-1" style="font-weight:800;${dayColor}">${escapeHtml(dayInfo.text)}</td>
          <td class="cm-sticky-col-2" style="font-weight:800;">${diaHtml}</td>
          <td style="font-weight:800;">${escapeHtml(totTm)}</td>
          <td>${escapeHtml(totDst)}</td>
          ${subTds}
        </tr>
      `;
    });

    const totalTotStr = XcrewRawFormatter.formatTotalMinutes(totalTotMins);
    const totalDstStr = (Math.round(totalDistRaw * 10) / 10).toFixed(1).replace(/\.0$/, '');
    let tfootSub = '';
    SPEC_SUB_COLUMNS.forEach((c) => {
      const mins = subTotalMinsByKey[c.key];
      const str = mins > 0 ? XcrewRawFormatter.formatTotalMinutes(mins) : '00:00';
      tfootSub += `<td style="${mins > 0 ? 'font-weight:900;color:var(--primary);' : 'color:var(--text-muted);font-weight:600;'}">${escapeHtml(str)}</td>`;
    });

    return `
      <div class="cm-card">
        <div class="cm-spec-scroll">
          <table class="cm-table-spec">
            <thead>
              <tr>
                <th class="cm-sticky-col-1">일자</th>
                <th class="cm-sticky-col-2">다이아번호</th>
                <th>시간</th>
                <th>거리</th>
                ${thSub}
              </tr>
            </thead>
            <tbody>${tbody}</tbody>
            <tfoot>
              <tr>
                <td class="cm-sticky-col-1">합계</td>
                <td class="cm-sticky-col-2">${rows.length}건</td>
                <td>${escapeHtml(totalTotStr)}</td>
                <td>${escapeHtml(totalDstStr)}</td>
                ${tfootSub}
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    `;
  }

  return {
    onActivate() {
      if (!hasInitialized) {
        hasInitialized = true;
        loadWorkSpec(currentYm);
      }
    },

    reload() {
      loadWorkSpec(currentYm);
    },

    render() {
      const curYear = parseInt(currentYm.slice(0, 4), 10);
      const curMonth = parseInt(currentYm.slice(4, 6), 10);
      let yearOpts = '';
      for (let y = 2000; y <= 2040; y++) {
        yearOpts += `<option value="${y}" ${y === curYear ? 'selected' : ''}>${y}년</option>`;
      }
      let monthOpts = '';
      for (let m = 1; m <= 12; m++) {
        monthOpts += `<option value="${pad2(m)}" ${m === curMonth ? 'selected' : ''}>${m}월</option>`;
      }

      return `
        <div class="cm-control-card">
          <div class="cm-control-row" style="justify-content:space-between;">
            <div style="display:flex;align-items:center;gap:4px;min-width:0;">
              <select id="__cm_ws_year" class="cm-select">${yearOpts}</select>
              <select id="__cm_ws_month" class="cm-select">${monthOpts}</select>
            </div>
            <div style="display:flex;align-items:center;gap:4px;flex-shrink:0;">
              <button type="button" class="cm-btn" data-action="today">오늘</button>
              <button type="button" class="cm-btn cm-btn-primary" data-action="search">조회</button>
            </div>
          </div>
        </div>
        ${
          loading
            ? '<div class="cm-card"><div class="cm-status-box">⏳ 개인근무명세서를 불러오는 중...</div></div>'
            : errorMsg
            ? `<div class="cm-error-box">${escapeHtml(errorMsg)}</div>`
            : render18ColTable()
        }
      `;
    },

    bindEvents(container) {
      const yearSel = container.querySelector('#__cm_ws_year');
      const monthSel = container.querySelector('#__cm_ws_month');

      function readSelectedYm() {
        const y = yearSel ? yearSel.value : currentYm.slice(0, 4);
        const m = monthSel ? monthSel.value : currentYm.slice(4, 6);
        return y + m;
      }

      if (yearSel) {
        yearSel.onchange = () => loadWorkSpec(readSelectedYm());
      }
      if (monthSel) {
        monthSel.onchange = () => loadWorkSpec(readSelectedYm());
      }

      container.querySelectorAll('[data-action]').forEach((el) => {
        el.onclick = () => {
          const act = el.getAttribute('data-action');
          if (act === 'today') loadWorkSpec(getTodayYm());
          else if (act === 'search') loadWorkSpec(readSelectedYm());
        };
      });
    },
  };
}
