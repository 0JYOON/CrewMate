/**
 * CrewMate View - [메뉴 2] 다이아 조회 (extrCrewDiaList.do)
 */

import { XcrewApi } from '../../core/xcrewApi.js';
import {
  escapeHtml,
  getTodayYmd,
  buildRailBlueUrl,
  XcrewRawFormatter,
  CrewMateDomainAdapter,
} from '../../core/formatters.js';
import { loadSettings, isEffectiveDarkMode } from '../../core/settingsStore.js';
import { captureDiaTableToPng, captureDiaDiagramToPng, openCapturePreviewModal } from '../../core/capture.js';
import { renderRouteDiagramSvgHtml } from './routeDiagram.js';

export function createDiaDetailView(appContext) {
  let pjtDt = getTodayYmd();
  let pdiaNo = '';
  let contentMode = 'TABLE'; // 'TABLE' (시정표) | 'DIAGRAM' (승무 행로도 - 실험실 On 시)
  let loading = false;
  let errorMsg = '';
  let result = null;
  let hasInitialized = false;

  async function autoFillAndSearch(targetDate) {
    pjtDt = targetDate;
    loading = true;
    errorMsg = '';
    appContext.requestRender();
    try {
      const rslt2 = await XcrewApi.fetchAutoPdiaNo(pjtDt);
      if (rslt2 && Array.isArray(rslt2.pdiaNo) && rslt2.pdiaNo.length > 0 && rslt2.pdiaNo[0].pdiaNo) {
        pdiaNo = String(rslt2.pdiaNo[0].pdiaNo).trim();
      }
      if (pdiaNo && pdiaNo !== 'S' && pdiaNo !== 'X' && pdiaNo !== '연차') {
        result = await XcrewApi.fetchDiaDetail(pjtDt, pdiaNo);
      } else {
        result = null;
      }
    } catch (err) {
      errorMsg = err.message || '승무다이아 조회 중 오류가 발생했습니다.';
    } finally {
      loading = false;
      appContext.requestRender();
    }
  }

  async function executeSearch() {
    if (!pjtDt || pjtDt.length !== 8) {
      errorMsg = '조회할 사업일자를 선택하세요.';
      appContext.requestRender();
      return;
    }
    if (!pdiaNo.trim()) {
      errorMsg = '조회할 승무 다이아번호를 입력하세요.';
      appContext.requestRender();
      return;
    }
    loading = true;
    errorMsg = '';
    appContext.requestRender();
    try {
      result = await XcrewApi.fetchDiaDetail(pjtDt, pdiaNo.trim());
    } catch (err) {
      errorMsg = err.message || '승무다이아 상세 조회 중 오류가 발생했습니다.';
    } finally {
      loading = false;
      appContext.requestRender();
    }
  }

  function renderDetailSection() {
    if (!result) return '';
    const rows = Array.isArray(result.data) ? result.data : [];
    if (!rows.length) {
      return '<div class="cm-card"><div class="cm-status-box">조회된 사업다이아 상세 내역이 없습니다.</div></div>';
    }

    const settings = loadSettings();
    if (settings.labRouteDiagram && contentMode === 'DIAGRAM') {
      const isDark = isEffectiveDarkMode(settings);
      return `
        <div class="cm-card">
          <div class="cm-card-header">
            <span>🗺️ 승무 행로도 <span style="font-size:calc(10.5px * var(--cm-font-scale));color:var(--text-sub);font-weight:700;">(참고용)</span></span>
            <button type="button" class="cm-capture-btn" data-action="capture-diagram" title="승무 행로도 이미지 저장">📷</button>
          </div>
          <div style="padding:8px 4px;">
            ${renderRouteDiagramSvgHtml(rows, isDark)}
          </div>
        </div>
      `;
    }

    let trs = '';
    rows.forEach((r) => {
      const rank = CrewMateDomainAdapter.classifySegmentPriority(r.pjtHrDvNm);
      const badgeCls =
        rank === 1 ? 'cm-badge-blue' : rank === 2 ? 'cm-badge-amber' : 'cm-badge-slate';
      const trn = String(r.trnNo || '').trim();
      const canRailBlue = trn && /\d/.test(trn) && trn !== '도';
      const railUrl = canRailBlue ? buildRailBlueUrl(pjtDt, trn) : '';

      const trnCellHtml = railUrl
        ? `<a href="${escapeHtml(railUrl)}" target="_blank" rel="noopener" style="color:var(--primary);font-weight:900;text-decoration:underline;text-underline-offset:2px;">${escapeHtml(trn)}</a>`
        : `<span style="font-weight:800;">${escapeHtml(trn)}</span>`;

      trs += `
        <tr class="${rank === 1 ? 'cm-row-highlight' : ''}">
          <td><span class="cm-badge ${badgeCls}">${escapeHtml(r.pjtHrDvNm || '')}</span></td>
          <td>${trnCellHtml}</td>
          <td style="font-weight:700;">${escapeHtml(r.dptStnNm || '')}</td>
          <td style="font-weight:800;">${escapeHtml(XcrewRawFormatter.formatHHMM(r.dptTm))}</td>
          <td style="font-weight:700;">${escapeHtml(r.arvStnNm || '')}</td>
          <td style="font-weight:800;">${escapeHtml(XcrewRawFormatter.formatHHMM(r.arvTm))}</td>
        </tr>
      `;
    });

    return `
      <div class="cm-card">
        <div class="cm-card-header">
          <span>🚄 사업다이아 상세</span>
          <button type="button" class="cm-capture-btn" data-action="capture-table" title="사업다이아 상세 이미지 저장">📷</button>
        </div>
        <table class="cm-table-fit">
          <colgroup>
            <col style="width:17%;" />
            <col style="width:17%;" />
            <col style="width:17%;" />
            <col style="width:16%;" />
            <col style="width:17%;" />
            <col style="width:16%;" />
          </colgroup>
          <thead>
            <tr>
              <th>구분</th>
              <th>열차번호</th>
              <th>출발역</th>
              <th>출발시각</th>
              <th>도착역</th>
              <th>도착시각</th>
            </tr>
          </thead>
          <tbody>${trs}</tbody>
        </table>
      </div>
    `;
  }

  function renderSubtotalsSection() {
    if (!result) return '';
    const dtlData = Array.isArray(result.dtlData) ? result.dtlData : [];
    if (!dtlData.length) {
      return `
        <div class="cm-card">
          <div class="cm-card-header">📊 사업다이아 상세 부분합</div>
          <div class="cm-subtotal-grid">
            <div class="cm-subtotal-item"><span>총사업시간</span><b>-</b></div>
            <div class="cm-subtotal-item"><span>총사업거리</span><b>-</b></div>
          </div>
        </div>
      `;
    }

    let itemsHtml = '';
    dtlData.forEach((obj) => {
      itemsHtml += `
        <div class="cm-subtotal-item">
          <span style="color:var(--text-sub);font-weight:700;">${escapeHtml(obj.pjtHrDvNm || '-')}</span>
          <span style="font-weight:900;color:var(--text-main);">${escapeHtml(XcrewRawFormatter.toHHMM(obj.pjtTnum))}</span>
        </div>
      `;
    });

    const totWrk = XcrewRawFormatter.toHHMM(dtlData[0].totWrkTnum);
    const totDst = dtlData[0].totPjtDst != null ? dtlData[0].totPjtDst : '-';
    itemsHtml += `
      <div class="cm-subtotal-item highlight">
        <span style="color:var(--primary);font-weight:800;">총사업시간</span>
        <span style="font-weight:900;color:var(--primary);">${escapeHtml(totWrk)}</span>
      </div>
      <div class="cm-subtotal-item highlight">
        <span style="color:var(--primary);font-weight:800;">총사업거리</span>
        <span style="font-weight:900;color:var(--primary);">${escapeHtml(totDst)}</span>
      </div>
    `;

    return `
      <div class="cm-card">
        <div class="cm-card-header">
          <span>📊 사업다이아 상세 부분합</span>
        </div>
        <div class="cm-subtotal-grid">${itemsHtml}</div>
      </div>
    `;
  }

  function renderRunDaysSection() {
    if (!result) return '';
    const efc = result.efcTrm;
    const efcStr =
      efc && efc.pdiaEfcStDt && efc.pdiaEfcClsDt
        ? XcrewRawFormatter.formatYMD(efc.pdiaEfcStDt) + ' ~ ' + XcrewRawFormatter.formatYMD(efc.pdiaEfcClsDt)
        : '-';

    const runRow = Array.isArray(result.runDtDv) && result.runDtDv.length > 0 ? result.runDtDv[0] : {};
    let runDaysHtml = '<div class="cm-rundays-grid">';
    CrewMateDomainAdapter.DIA_RUN_DAYS_SPEC.forEach((col) => {
      const checked = Number(runRow[col.key]) === 1;
      runDaysHtml += `
        <div class="cm-runday-item ${checked ? 'checked' : ''}">
          <span>${escapeHtml(col.title)}</span>
          <span>${checked ? '☑' : '☐'}</span>
        </div>
      `;
    });
    runDaysHtml += '</div>';

    return `
      <div class="cm-card">
        <div class="cm-card-header" style="flex-direction:column;align-items:flex-start;gap:2px;">
          <span>📅 사업다이아 운용요일</span>
          <span style="font-size:calc(11px * var(--cm-font-scale));color:var(--text-sub);font-weight:700;">시행기간 : ${escapeHtml(efcStr)}</span>
        </div>
        ${runDaysHtml}
      </div>
    `;
  }

  function renderLabBottomSwitch() {
    const settings = loadSettings();
    if (!settings.labRouteDiagram || !result) return '';
    return `
      <div class="cm-control-card" style="display:flex;justify-content:center;">
        <div style="display:flex;gap:6px;width:100%;">
          <button type="button" class="cm-pill-btn ${contentMode === 'TABLE' ? 'active' : ''}" data-action="mode-table" style="flex:1;">📋 표로 보기</button>
          <button type="button" class="cm-pill-btn ${contentMode === 'DIAGRAM' ? 'active' : ''}" data-action="mode-diagram" style="flex:1;">🗺️ 승무 행로도 보기 (참고용)</button>
        </div>
      </div>
    `;
  }

  return {
    onActivate(params) {
      if (params && params.pjtDt && params.pdiaNo) {
        pjtDt = params.pjtDt;
        pdiaNo = params.pdiaNo;
        hasInitialized = true;
        executeSearch();
        return;
      }
      if (!hasInitialized) {
        hasInitialized = true;
        autoFillAndSearch(pjtDt);
      }
    },

    reload() {
      if (pdiaNo.trim()) executeSearch();
      else autoFillAndSearch(pjtDt);
    },

    render() {
      const dateInputVal = XcrewRawFormatter.formatYMD(pjtDt);
      return `
        <div class="cm-control-card">
          <div class="cm-control-row">
            <input type="date" id="__cm_dia_date" class="cm-input" style="flex:1.15;min-width:0;text-align:center;" value="${escapeHtml(dateInputVal)}" />
            <input type="text" id="__cm_dia_no" class="cm-input" style="flex:0.85;min-width:0;text-align:center;" placeholder="다이아번호" value="${escapeHtml(pdiaNo)}" />
            <button type="button" class="cm-btn cm-btn-primary" data-action="search">조회</button>
          </div>
        </div>
        ${
          loading
            ? '<div class="cm-card"><div class="cm-status-box">⏳ 승무다이아 정보를 조회하는 중...</div></div>'
            : errorMsg
            ? `<div class="cm-error-box">${escapeHtml(errorMsg)}</div>`
            : !result
            ? '<div class="cm-card"><div class="cm-status-box">사업일자와 다이아번호를 입력한 후 [조회]를 눌러주세요.</div></div>'
            : `
              ${renderDetailSection()}
              ${renderSubtotalsSection()}
              ${renderRunDaysSection()}
              ${renderLabBottomSwitch()}
            `
        }
      `;
    },

    bindEvents(container) {
      const dateInput = container.querySelector('#__cm_dia_date');
      const diaInput = container.querySelector('#__cm_dia_no');

      if (dateInput) {
        dateInput.onchange = () => {
          const v = (dateInput.value || '').replace(/-/g, '').trim();
          if (v.length === 8) {
            autoFillAndSearch(v);
          }
        };
      }

      if (diaInput) {
        diaInput.oninput = () => {
          pdiaNo = diaInput.value;
        };
        diaInput.onkeydown = (e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            executeSearch();
          }
        };
      }

      container.querySelectorAll('[data-action]').forEach((el) => {
        el.onclick = () => {
          const act = el.getAttribute('data-action');
          if (act === 'search') executeSearch();
          else if (act === 'mode-table') {
            contentMode = 'TABLE';
            appContext.requestRender();
          } else if (act === 'mode-diagram') {
            contentMode = 'DIAGRAM';
            appContext.requestRender();
          } else if (act === 'capture-table') {
            const rows = result && Array.isArray(result.data) ? result.data : [];
            if (!rows.length) return;
            try {
              const isDark = isEffectiveDarkMode(loadSettings());
              const res = captureDiaTableToPng({ pjtDt, pdiaNo: pdiaNo.trim(), rows, isDark });
              openCapturePreviewModal(container, res);
            } catch (err) {
              appContext.showToast('⚠️ 이미지 저장 실패: ' + (err && err.message ? err.message : '오류'));
            }
          } else if (act === 'capture-diagram') {
            const svgEl = container.querySelector('svg');
            if (!svgEl) return;
            const isDark = isEffectiveDarkMode(loadSettings());
            captureDiaDiagramToPng({ pjtDt, pdiaNo: pdiaNo.trim(), svgElement: svgEl, isDark })
              .then((res) => openCapturePreviewModal(container, res))
              .catch((err) => appContext.showToast('⚠️ 이미지 저장 실패: ' + (err && err.message ? err.message : '오류')));
          }
        };
      });
    },
  };
}
