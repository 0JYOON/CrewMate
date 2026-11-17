/**
 * CrewMate View - [메뉴 3] 소속승무원 현황 (extrCrewWrkPsttList.do)
 */

import { XcrewApi } from '../../core/xcrewApi.js';
import {
  escapeHtml,
  getTodayYmd,
  XcrewRawFormatter,
} from '../../core/formatters.js';

export function createCrewStatusView(appContext) {
  let pjtDt = getTodayYmd();
  let filterKeyword = '';
  let isSearchModalOpen = false;
  let tempSearchInput = '';
  let loading = false;
  let errorMsg = '';
  let rows = [];
  let hasInitialized = false;

  async function loadCrewStatus(targetYmd) {
    pjtDt = targetYmd;
    loading = true;
    errorMsg = '';
    appContext.requestRender();
    try {
      const res = await XcrewApi.fetchCrewWorkStatus(pjtDt);
      rows = Array.isArray(res && res.data) ? res.data : [];
    } catch (err) {
      errorMsg = err.message || '소속승무원 현황 조회 중 오류가 발생했습니다.';
    } finally {
      loading = false;
      appContext.requestRender();
    }
  }

  function formatTrainCol(row) {
    const t1 = String(row.repTrn1No || '').trim();
    const t2 = String(row.repTrn2No || '').trim();
    return t1 + (t2 ? ', ' + t2 : '');
  }

  function formatCrewCol(row) {
    const n1 = String(row.empNm1 || '').trim();
    const n2 = String(row.empNm2 || '').trim();
    const n3 = String(row.empNm3 || '').trim();
    const n4 = String(row.empNm4 || '').trim();
    return n1 + (n2 ? ', ' + n2 : '') + (n3 ? ', ' + n3 : '') + (n4 ? ', ' + n4 : '');
  }

  function renderTable() {
    const kw = filterKeyword.trim().toLowerCase();
    const myName = (appContext.userContext.userName || '').trim();

    const filtered = kw
      ? rows.filter((r) => {
          const dia = String(r.pdiaNo || '').toLowerCase();
          const trn = formatTrainCol(r).toLowerCase();
          const crew = formatCrewCol(r).toLowerCase();
          return dia.indexOf(kw) !== -1 || trn.indexOf(kw) !== -1 || crew.indexOf(kw) !== -1;
        })
      : rows;

    if (!filtered.length) {
      return `<div class="cm-card"><div class="cm-status-box">${
        rows.length ? '검색 조건과 일치하는 행이 없습니다.' : '조회된 소속승무원 현황 데이터가 없습니다.'
      }</div></div>`;
    }

    let tbody = '';
    filtered.forEach((r) => {
      const dia = String(r.pdiaNo || '').trim();
      const trn = formatTrainCol(r);
      const gwk = XcrewRawFormatter.formatHHMM(r.gwkTm);
      const loiw = XcrewRawFormatter.formatHHMM(r.loiwTm);
      const crew = formatCrewCol(r);
      const isMine = myName && crew.indexOf(myName) !== -1;
      const canOpenDia = dia && /^[A-Za-z0-9]+$/.test(dia) && dia !== 'S' && dia !== 'X';

      tbody += `
        <tr class="${isMine ? 'cm-row-highlight' : ''}">
          <td ${
            canOpenDia
              ? `data-action="open-dia" data-dia="${escapeHtml(dia)}" style="cursor:pointer;color:var(--primary);font-weight:800;"`
              : 'style="font-weight:700;"'
          }>
            ${escapeHtml(dia)}
          </td>
          <td style="font-weight:500;color:var(--text-sub);font-size:calc(10.5px * var(--cm-font-scale));">${escapeHtml(trn)}</td>
          <td style="font-weight:900;color:var(--text-main);">${escapeHtml(gwk)}</td>
          <td style="font-weight:900;color:var(--text-main);">${escapeHtml(loiw)}</td>
          <td style="white-space:normal;word-break:keep-all;font-weight:900;color:${
            isMine ? 'var(--primary)' : 'var(--text-main)'
          };">${escapeHtml(crew)}</td>
        </tr>
      `;
    });

    return `
      <div class="cm-card">
        <div class="cm-card-header">
          <span>👥 소속 승무근무표현황</span>
          ${
            filterKeyword
              ? `<button type="button" class="cm-btn" data-action="clear-filter" style="height:22px;padding:0 6px;font-size:10.5px;">필터 해제: ${escapeHtml(filterKeyword)} ✕</button>`
              : ''
          }
        </div>
        <table class="cm-table-fit">
          <colgroup>
            <col style="width:17%;" />
            <col style="width:27%;" />
            <col style="width:15%;" />
            <col style="width:15%;" />
            <col style="width:26%;" />
          </colgroup>
          <thead>
            <tr>
              <th>다이아</th>
              <th>열차번호</th>
              <th>출근</th>
              <th>퇴근</th>
              <th>승무원</th>
            </tr>
          </thead>
          <tbody>${tbody}</tbody>
        </table>
      </div>
    `;
  }

  function renderSearchModal() {
    if (!isSearchModalOpen) return '';
    return `
      <div class="cm-modal-backdrop" id="__cm_cs_modal_bg">
        <div class="cm-modal" onclick="event.stopPropagation();">
          <div class="cm-modal-header">
            <span>🔍 결과 내 검색</span>
            <button type="button" class="cm-btn" data-action="close-modal" style="height:26px;padding:0 8px;">✕</button>
          </div>
          <div class="cm-modal-body">
            <div style="font-size:12px;color:var(--text-sub);font-weight:700;">다이아 번호, 열차번호 또는 승무원 이름을 입력하세요.</div>
            <input type="text" id="__cm_cs_modal_input" class="cm-input" style="width:100%;height:38px;" placeholder="예: 16035, K4501, 홍길동" value="${escapeHtml(tempSearchInput)}" />
            <div style="display:flex;gap:6px;">
              <button type="button" class="cm-btn" data-action="clear-filter" style="flex:1;">전체 보기(초기화)</button>
            </div>
          </div>
          <div class="cm-modal-footer">
            <button type="button" class="cm-btn" data-action="close-modal">취소</button>
            <button type="button" class="cm-btn cm-btn-primary" data-action="apply-search">검색 적용</button>
          </div>
        </div>
      </div>
    `;
  }

  return {
    onActivate(params) {
      if (params && params.pjtDt) {
        hasInitialized = true;
        loadCrewStatus(params.pjtDt);
        return;
      }
      if (!hasInitialized) {
        hasInitialized = true;
        loadCrewStatus(pjtDt);
      }
    },

    reload() {
      loadCrewStatus(pjtDt);
    },

    render() {
      const dateInputVal = XcrewRawFormatter.formatYMD(pjtDt);
      return `
        <div class="cm-control-card">
          <div class="cm-control-row">
            <input type="date" id="__cm_cs_date" class="cm-input" style="flex:1;min-width:0;text-align:center;" value="${escapeHtml(dateInputVal)}" />
            <button type="button" class="cm-btn" data-action="today">오늘</button>
            <button type="button" class="cm-btn cm-btn-primary" data-action="search">조회</button>
            <button type="button" class="cm-btn ${filterKeyword ? 'cm-btn-primary' : ''}" data-action="open-search-modal">검색</button>
          </div>
        </div>
        ${
          loading
            ? '<div class="cm-card"><div class="cm-status-box">⏳ 소속승무원 현황을 불러오는 중...</div></div>'
            : errorMsg
            ? `<div class="cm-error-box">${escapeHtml(errorMsg)}</div>`
            : renderTable()
        }
        ${renderSearchModal()}
      `;
    },

    bindEvents(container) {
      const dateInput = container.querySelector('#__cm_cs_date');
      if (dateInput) {
        dateInput.onchange = () => {
          const v = (dateInput.value || '').replace(/-/g, '').trim();
          if (v.length === 8) loadCrewStatus(v);
        };
      }

      const modalInput = container.querySelector('#__cm_cs_modal_input');
      if (modalInput) {
        modalInput.oninput = () => {
          tempSearchInput = modalInput.value;
        };
        modalInput.onkeydown = (e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            filterKeyword = tempSearchInput.trim();
            isSearchModalOpen = false;
            appContext.requestRender();
          }
        };
        setTimeout(() => {
          try {
            modalInput.focus();
          } catch (e) {}
        }, 20);
      }

      const modalBg = container.querySelector('#__cm_cs_modal_bg');
      if (modalBg) {
        modalBg.onclick = () => {
          isSearchModalOpen = false;
          appContext.requestRender();
        };
      }

      container.querySelectorAll('[data-action]').forEach((el) => {
        el.onclick = (e) => {
          e.stopPropagation();
          const act = el.getAttribute('data-action');
          if (act === 'today') loadCrewStatus(getTodayYmd());
          else if (act === 'search') loadCrewStatus(pjtDt);
          else if (act === 'open-search-modal') {
            tempSearchInput = filterKeyword;
            isSearchModalOpen = true;
            appContext.requestRender();
          } else if (act === 'close-modal') {
            isSearchModalOpen = false;
            appContext.requestRender();
          } else if (act === 'fill-my-name') {
            tempSearchInput = appContext.userContext.userName || '';
            filterKeyword = tempSearchInput;
            isSearchModalOpen = false;
            appContext.requestRender();
          } else if (act === 'clear-filter') {
            filterKeyword = '';
            tempSearchInput = '';
            isSearchModalOpen = false;
            appContext.requestRender();
          } else if (act === 'apply-search') {
            filterKeyword = tempSearchInput.trim();
            isSearchModalOpen = false;
            appContext.requestRender();
          } else if (act === 'open-dia') {
            const dia = el.getAttribute('data-dia');
            if (dia) {
              appContext.navigateToMenu('dia_detail', { pjtDt, pdiaNo: dia });
            }
          }
        };
      });
    },
  };
}
