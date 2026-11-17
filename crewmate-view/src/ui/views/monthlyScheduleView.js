/**
 * CrewMate View - [메뉴 1] 월별 승무근무표 (당월승무근무표 + 개인승무근무표 통합)
 */

import { XcrewApi } from '../../core/xcrewApi.js';
import {
  escapeHtml,
  pad2,
  getTodayYmd,
  getTodayYm,
  shiftDateYmd,
  XcrewRawFormatter,
  CrewMateDomainAdapter,
} from '../../core/formatters.js';
import { loadSettings, isEffectiveDarkMode } from '../../core/settingsStore.js';
import { captureMonthlyScheduleToPng, openCapturePreviewModal } from '../../core/capture.js';

export function createMonthlyScheduleView(appContext) {
  let currentYm = getTodayYm();
  let viewMode = 'CALENDAR'; // 'CALENDAR' | 'LIST'
  let loading = false;
  let errorMsg = '';
  let items = [];
  let hasLoadedInitial = false;

  async function loadMonth(ym) {
    currentYm = ym;
    loading = true;
    errorMsg = '';
    appContext.requestRender();
    try {
      const empNm = (appContext.userContext.userName || '').trim();
      const [indRes, rltmRes] = await Promise.all([
        XcrewApi.fetchIndSchedule(ym + '01', empNm).catch(() => null),
        XcrewApi.fetchRltmSchedule(ym).catch(() => null),
      ]);

      const indList = indRes && Array.isArray(indRes.data) ? indRes.data : [];
      const rltmList = rltmRes && Array.isArray(rltmRes.data) ? rltmRes.data : [];

      if (typeof appContext.onEpnoResolved === 'function') {
        const foundEpno =
          rltmList.find((it) => it && it.epno) || indList.find((it) => it && it.epno);
        if (foundEpno && foundEpno.epno) {
          appContext.onEpnoResolved(String(foundEpno.epno).trim());
        }
      }

      const mergedByDate = {};
      rltmList.forEach((r) => {
        const dt = String(r && r.pjtDt ? r.pjtDt : '').trim();
        if (!dt) return;
        mergedByDate[dt] = Object.assign({}, r);
      });
      indList.forEach((r) => {
        const dt = String(r && r.pjtDt ? r.pjtDt : '').trim();
        if (!dt) return;
        mergedByDate[dt] = Object.assign({}, mergedByDate[dt] || {}, r);
      });

      items = Object.keys(mergedByDate)
        .sort()
        .map((dt) => mergedByDate[dt]);
    } catch (err) {
      errorMsg = err.message || '월별 승무근무표 조회 중 오류가 발생했습니다.';
    } finally {
      loading = false;
      appContext.requestRender();
    }
  }

  function extractCleanDiaNo(rawDia) {
    const s = String(rawDia || '').trim();
    const m = s.match(/^~\(([^)]+)\)$/);
    if (m) return m[1].trim();
    if (s.indexOf('~') === 0) return s.slice(1).trim();
    return s;
  }

  function isOperationalDia(rawDia) {
    const clean = extractCleanDiaNo(rawDia);
    if (
      !clean ||
      clean === 'S' ||
      clean === 'X' ||
      clean === '휴' ||
      clean === '대휴' ||
      clean === '연차' ||
      clean === '공가' ||
      clean === '병가'
    ) {
      return false;
    }
    return /^[A-Za-z0-9]+$/.test(clean);
  }

  function buildDiaBadgeHtml(ev, nextEv) {
    if (!ev || !ev.pdiaNo) return '';
    const pdiaNo = String(ev.pdiaNo).trim();
    if (!pdiaNo) return '';

    const shiftType = CrewMateDomainAdapter.determineShiftType(ev, nextEv);
    let badgeClass = 'day';
    if (shiftType === 'REST') badgeClass = 'rest';
    else if (shiftType === 'EDU_LEAVE') badgeClass = 'purple';
    else if (shiftType === 'NIGHT_DAY1' || shiftType === 'NIGHT_DAY2') badgeClass = 'night';

    const isHol = CrewMateDomainAdapter.isHolidayWork(ev);
    const decoCls = ev.pjtChgPrsFlg === 'Y' ? 'cm-dia-underline' : '';

    return `
      <div class="cm-dia-pill-wrap">
        ${isHol ? '<span class="cm-holiday-pill">휴</span>' : ''}
        <span class="cm-dia-badge ${badgeClass} ${decoCls}">${escapeHtml(pdiaNo)}</span>
      </div>
    `;
  }

  function renderCalendarGrid() {
    const year = parseInt(currentYm.slice(0, 4), 10);
    const month = parseInt(currentYm.slice(4, 6), 10);
    const firstDow = new Date(year, month - 1, 1).getDay();
    const lastDate = new Date(year, month, 0).getDate();
    const todayYmd = getTodayYmd();

    const byDate = {};
    items.forEach((it) => {
      if (it && it.pjtDt) {
        byDate[String(it.pjtDt).trim()] = it;
      }
    });

    const dows = ['일', '월', '화', '수', '목', '금', '토'];
    let html = '<div class="cm-cal-grid">';
    dows.forEach((d, idx) => {
      const cls = idx === 0 ? 'sun' : idx === 6 ? 'sat' : '';
      html += `<div class="cm-cal-dow ${cls}">${d}</div>`;
    });

    for (let i = 0; i < firstDow; i++) {
      html += '<div class="cm-cal-cell empty"></div>';
    }

    for (let day = 1; day <= lastDate; day++) {
      const ymd = currentYm + pad2(day);
      const nextYmd = shiftDateYmd(ymd, 1);
      const dow = (firstDow + day - 1) % 7;
      const dayCls = dow === 0 ? 'sun' : dow === 6 ? 'sat' : '';
      const isToday = ymd === todayYmd;
      const ev = byDate[ymd];
      const nextEv = byDate[nextYmd];

      const pdiaNo = ev && ev.pdiaNo != null ? String(ev.pdiaNo).trim() : '';
      const isNight2 = ev && String(ev.alcSpecDvCd) === '12';
      const gwk = ev && !isNight2 ? XcrewRawFormatter.formatHHMM(ev.gwkTm) : '';
      const loiw = ev && !isNight2 ? XcrewRawFormatter.formatHHMM(ev.loiwTm) : '';
      const trn1 = ev && !isNight2 ? String(ev.repTrn1No || '').trim() : '';
      const trn2 = ev && !isNight2 ? String(ev.repTrn2No || '').trim() : '';

      const canOpenDia = isOperationalDia(pdiaNo);
      const dataAttrs = canOpenDia
        ? `data-action="open-dia" data-ymd="${ymd}" data-dia="${escapeHtml(extractCleanDiaNo(pdiaNo))}"`
        : '';

      html += `
        <div class="cm-cal-cell ${isToday ? 'today' : ''}" ${dataAttrs}>
          <div class="cm-cal-day-row">
            <span class="cm-cal-day-num ${dayCls}">${day}</span>
          </div>
          ${pdiaNo ? buildDiaBadgeHtml(ev, nextEv) : ''}
          ${gwk ? `<div class="cm-cal-time-line"><span class="cm-bulet-orange">출</span><span>${escapeHtml(gwk)}</span></div>` : ''}
          ${loiw ? `<div class="cm-cal-time-line"><span class="cm-bulet-green">퇴</span><span>${escapeHtml(loiw)}</span></div>` : ''}
          ${trn1 ? `<div class="cm-cal-trn-line">${escapeHtml(trn1)}</div>` : ''}
          ${trn2 ? `<div class="cm-cal-trn-line" style="color:var(--text-sub);">${escapeHtml(trn2)}</div>` : ''}
        </div>
      `;
    }

    const totalCells = firstDow + lastDate;
    const trailing = (7 - (totalCells % 7)) % 7;
    for (let i = 0; i < trailing; i++) {
      html += '<div class="cm-cal-cell empty"></div>';
    }

    html += '</div>';
    return html;
  }

  function renderListView() {
    if (!items.length) {
      return '<div class="cm-card"><div class="cm-status-box">조회된 월별 승무근무표 데이터가 없습니다.</div></div>';
    }

    const byDate = {};
    items.forEach((it) => {
      if (it && it.pjtDt) byDate[String(it.pjtDt).trim()] = it;
    });

    const todayYmd = getTodayYmd();
    let rowsHtml = '';
    items.forEach((ev) => {
      const ymd = String(ev.pjtDt || '').trim();
      const nextEv = byDate[shiftDateYmd(ymd, 1)];
      const dayInfo = XcrewRawFormatter.formatDayOnlyWithDow(ymd);
      const dayColor =
        dayInfo.dowIdx === 0 ? 'color:#dc2626;' : dayInfo.dowIdx === 6 ? 'color:#2563eb;' : '';

      const pdiaNo = String(ev.pdiaNo || '').trim();
      const isNight2 = String(ev.alcSpecDvCd) === '12';
      const trn1 = isNight2 ? '' : String(ev.repTrn1No || '').trim();
      const trn2 = isNight2 ? '' : String(ev.repTrn2No || '').trim();
      const trnCombined = trn1 && trn2 ? `${trn1} · ${trn2}` : trn1 || trn2 || '';

      const gwk = isNight2 ? '' : XcrewRawFormatter.formatHHMM(ev.gwkTm);
      const loiw = isNight2 ? '' : XcrewRawFormatter.formatHHMM(ev.loiwTm);
      const isToday = ymd === todayYmd;
      const canOpenDia = isOperationalDia(pdiaNo);

      rowsHtml += `
        <tr class="${isToday ? 'cm-row-highlight' : ''}">
          <td style="font-weight:800;${dayColor}">${escapeHtml(dayInfo.text)}</td>
          <td ${
            canOpenDia
              ? `data-action="open-dia" data-ymd="${ymd}" data-dia="${escapeHtml(extractCleanDiaNo(pdiaNo))}" style="cursor:pointer;"`
              : ''
          }>
            ${pdiaNo ? buildDiaBadgeHtml(ev, nextEv) : '-'}
          </td>
          <td style="font-size:calc(10px * var(--cm-font-scale));font-weight:700;color:var(--text-sub);">${escapeHtml(trnCombined)}</td>
          <td style="font-weight:800;">${escapeHtml(gwk)}</td>
          <td style="font-weight:800;">${escapeHtml(loiw)}</td>
        </tr>
      `;
    });

    return `
      <div class="cm-card">
        <table class="cm-table-fit">
          <colgroup>
            <col style="width:16%;" />
            <col style="width:24%;" />
            <col style="width:28%;" />
            <col style="width:16%;" />
            <col style="width:16%;" />
          </colgroup>
          <thead>
            <tr>
              <th>일자</th>
              <th>다이아번호</th>
              <th>대표열번</th>
              <th>출근</th>
              <th>퇴근</th>
            </tr>
          </thead>
          <tbody>${rowsHtml}</tbody>
        </table>
      </div>
    `;
  }

  return {
    onActivate() {
      if (!hasLoadedInitial) {
        hasLoadedInitial = true;
        loadMonth(currentYm);
      }
    },

    reload() {
      loadMonth(currentYm);
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
              <select id="__cm_ms_year" class="cm-select">${yearOpts}</select>
              <select id="__cm_ms_month" class="cm-select">${monthOpts}</select>
            </div>
            <div style="display:flex;align-items:center;gap:4px;flex-shrink:0;">
              <button type="button" class="cm-pill-btn" data-action="today">오늘</button>
              <button type="button" class="cm-pill-btn ${viewMode === 'CALENDAR' ? 'active' : ''}" data-action="mode-cal">달력</button>
              <button type="button" class="cm-pill-btn ${viewMode === 'LIST' ? 'active' : ''}" data-action="mode-list">목록</button>
              <button type="button" class="cm-capture-btn" data-action="capture-schedule" title="승무근무표 이미지 저장">📷</button>
            </div>
          </div>
        </div>
        ${
          loading
            ? '<div class="cm-card"><div class="cm-status-box">⏳ 월별 승무근무표를 불러오는 중...</div></div>'
            : errorMsg
            ? `<div class="cm-error-box">${escapeHtml(errorMsg)}</div>`
            : viewMode === 'CALENDAR'
            ? renderCalendarGrid()
            : renderListView()
        }
      `;
    },

    bindEvents(container) {
      const yearSel = container.querySelector('#__cm_ms_year');
      const monthSel = container.querySelector('#__cm_ms_month');

      function onYmChange() {
        const y = yearSel ? yearSel.value : currentYm.slice(0, 4);
        const m = monthSel ? monthSel.value : currentYm.slice(4, 6);
        loadMonth(y + m);
      }

      if (yearSel) yearSel.onchange = onYmChange;
      if (monthSel) monthSel.onchange = onYmChange;

      container.querySelectorAll('[data-action]').forEach((el) => {
        el.onclick = (e) => {
          e.stopPropagation();
          const act = el.getAttribute('data-action');
          if (act === 'today') {
            loadMonth(getTodayYm());
          } else if (act === 'mode-cal') {
            viewMode = 'CALENDAR';
            appContext.requestRender();
          } else if (act === 'mode-list') {
            viewMode = 'LIST';
            appContext.requestRender();
          } else if (act === 'capture-schedule') {
            if (!items.length) return;
            try {
              const isDark = isEffectiveDarkMode(loadSettings());
              const res = captureMonthlyScheduleToPng({ ym: currentYm, viewMode, items, isDark });
              openCapturePreviewModal(container, res);
            } catch (err) {
              appContext.showToast('⚠️ 이미지 저장 실패: ' + (err && err.message ? err.message : '오류'));
            }
          } else if (act === 'open-dia') {
            const ymd = el.getAttribute('data-ymd');
            const dia = el.getAttribute('data-dia');
            if (ymd && dia) {
              appContext.navigateToMenu('dia_detail', { pjtDt: ymd, pdiaNo: dia });
            }
          }
        };
      });
    },
  };
}
