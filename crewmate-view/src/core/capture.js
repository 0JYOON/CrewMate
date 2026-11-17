/**
 * CrewMate View - 오프라인 순수 Canvas 2D / SVG 이미지 캡쳐 유틸리티
 * 외부 라이브러리(CDN) 없이 다이아 상세, 승무 행로도, 월별 승무근무표를 고해상도 PNG로 저장합니다.
 */

import { pad2, shiftDateYmd, escapeHtml, XcrewRawFormatter, CrewMateDomainAdapter } from './formatters.js';

const FONT_STACK = "'Pretendard Variable', Pretendard, 'Malgun Gothic', 'Apple SD Gothic Neo', sans-serif";

function getPalette(isDark) {
  if (isDark) {
    return {
      bg: '#0f172a',
      card: '#1e293b',
      subtle: '#263244',
      border: '#334155',
      textMain: '#f1f5f9',
      textSub: '#94a3b8',
      primary: '#60a5fa',
      headerBg: '#1e293b',
      dayBg: 'rgba(245, 158, 11, 0.24)',
      dayText: '#fcd34d',
      dayBorder: '#d97706',
      nightBg: 'rgba(99, 102, 241, 0.24)',
      nightText: '#a5b4fc',
      nightBorder: '#6366f1',
      restBg: 'rgba(220, 38, 38, 0.22)',
      restText: '#fca5a5',
      restBorder: 'rgba(248, 113, 113, 0.4)',
      purpleBg: 'rgba(147, 51, 234, 0.25)',
      purpleText: '#d8b4fe',
      purpleBorder: 'rgba(168, 85, 247, 0.45)',
      blueBg: 'rgba(37, 99, 235, 0.22)',
      blueText: '#93c5fd',
      amberBg: 'rgba(217, 119, 6, 0.22)',
      amberText: '#fcd34d',
      slateBg: 'rgba(100, 116, 139, 0.25)',
      slateText: '#cbd5e1',
      sunText: '#f87171',
      satText: '#60a5fa',
    };
  }
  return {
    bg: '#f1f5f9',
    card: '#ffffff',
    subtle: '#f8fafc',
    border: '#cbd5e1',
    textMain: '#0f172a',
    textSub: '#64748b',
    primary: '#2563eb',
    headerBg: '#f8fafc',
    dayBg: '#fef3c7',
    dayText: '#b45309',
    dayBorder: '#f59e0b',
    nightBg: '#e0e7ff',
    nightText: '#3730a3',
    nightBorder: '#818cf8',
    restBg: '#fef2f2',
    restText: '#dc2626',
    restBorder: '#fecaca',
    purpleBg: '#f3e8ff',
    purpleText: '#7e22ce',
    purpleBorder: '#d8b4fe',
    blueBg: '#dbeafe',
    blueText: '#1d4ed8',
    amberBg: '#fef3c7',
    amberText: '#b45309',
    slateBg: '#f1f5f9',
    slateText: '#475569',
    sunText: '#dc2626',
    satText: '#2563eb',
  };
}

function drawRoundedRect(ctx, x, y, w, h, r) {
  const radius = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + w, y, x + w, y + h, radius);
  ctx.arcTo(x + w, y + h, x, y + h, radius);
  ctx.arcTo(x, y + h, x, y, radius);
  ctx.arcTo(x, y, x + w, y, radius);
  ctx.closePath();
}

export function triggerDownloadSync(dataUrl, filename) {
  try {
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = filename;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    setTimeout(() => {
      try {
        document.body.removeChild(a);
      } catch (e) {}
    }, 500);
  } catch (e) {}
}

export function openCapturePreviewModal(container, { dataUrl, filename }) {
  const root = container && container.closest ? container.closest('.cm-root') || container : container;
  if (!root) return;
  const existing = root.querySelector('#__cm_capture_modal');
  if (existing) existing.remove();

  const overlay = document.createElement('div');
  overlay.id = '__cm_capture_modal';
  overlay.className = 'cm-modal-overlay';
  overlay.style.zIndex = '2147483647';
  overlay.innerHTML = `
    <div class="cm-modal" style="max-width:460px;">
      <div class="cm-modal-header">
        <span>📷 이미지 캡쳐 완료</span>
        <button type="button" class="cm-icon-btn" id="__cm_cap_close" style="border:none;font-size:16px;">✕</button>
      </div>
      <div class="cm-modal-body" style="gap:10px;">
        <div style="background:var(--bg-subtle);border:1px solid var(--border-color);border-radius:10px;padding:6px;max-height:58vh;overflow:auto;">
          <img src="${dataUrl}" alt="${escapeHtml(filename)}" style="display:block;width:100%;height:auto;border-radius:6px;" />
        </div>
        <div style="font-size:11px;color:var(--text-sub);text-align:center;line-height:1.45;">
          자동 다운로드가 시작되지 않으면 아래 버튼을 누르거나,<br/>모바일에서는 이미지를 길게 눌러 <b>사진 앱에 저장</b>하세요.
        </div>
        <div style="display:flex;gap:8px;">
          <a href="${dataUrl}" download="${escapeHtml(filename)}" class="cm-btn cm-btn-primary" style="flex:1;text-align:center;text-decoration:none;display:inline-flex;align-items:center;justify-content:center;">💾 이미지 다운로드</a>
          <button type="button" class="cm-btn cm-btn-secondary" id="__cm_cap_ok">닫기</button>
        </div>
      </div>
    </div>
  `;

  const closeModal = () => overlay.remove();
  overlay.querySelector('#__cm_cap_close').onclick = closeModal;
  overlay.querySelector('#__cm_cap_ok').onclick = closeModal;
  overlay.onclick = (e) => {
    if (e.target === overlay) closeModal();
  };
  root.appendChild(overlay);
}

function drawCaptureBanner(ctx, width, pad, pal, titleText, subText) {
  ctx.fillStyle = pal.card;
  drawRoundedRect(ctx, pad, pad, width - pad * 2, 54, 10);
  ctx.fill();
  ctx.strokeStyle = pal.border;
  ctx.lineWidth = 1;
  ctx.stroke();

  ctx.fillStyle = pal.textMain;
  ctx.font = `800 15px ${FONT_STACK}`;
  ctx.textBaseline = 'middle';
  ctx.textAlign = 'left';
  ctx.fillText(titleText, pad + 14, pad + 20);

  ctx.fillStyle = pal.textSub;
  ctx.font = `600 11px ${FONT_STACK}`;
  ctx.fillText(subText, pad + 14, pad + 40);

  ctx.fillStyle = pal.primary;
  ctx.font = `800 11px ${FONT_STACK}`;
  ctx.textAlign = 'right';
  ctx.fillText('CrewMate View', width - pad - 14, pad + 28);
  ctx.textAlign = 'left';
}

export function captureDiaTableToPng({ pjtDt, pdiaNo, rows, isDark }) {
  const pal = getPalette(isDark);
  const scale = 2;
  const width = 560;
  const pad = 12;
  const bannerH = 54;
  const gap = 10;
  const headerH = 30;
  const rowH = 30;
  const tableW = width - pad * 2;
  const totalH = pad + bannerH + gap + headerH + rows.length * rowH + pad;

  const canvas = document.createElement('canvas');
  canvas.width = width * scale;
  canvas.height = totalH * scale;
  const ctx = canvas.getContext('2d');
  ctx.scale(scale, scale);

  ctx.fillStyle = pal.bg;
  ctx.fillRect(0, 0, width, totalH);

  const dateText = XcrewRawFormatter.formatYMD(pjtDt);
  drawCaptureBanner(
    ctx,
    width,
    pad,
    pal,
    `🚄 ${dateText} · 다이아 ${pdiaNo}`,
    `사업다이아 상세 (${rows.length}건)`
  );

  const tableTop = pad + bannerH + gap;
  const tableH = headerH + rows.length * rowH;

  ctx.fillStyle = pal.card;
  drawRoundedRect(ctx, pad, tableTop, tableW, tableH, 10);
  ctx.fill();
  ctx.strokeStyle = pal.border;
  ctx.lineWidth = 1;
  ctx.stroke();

  const colRatios = [0.17, 0.17, 0.18, 0.15, 0.18, 0.15];
  const colTitles = ['구분', '열차번호', '출발역', '출발시각', '도착역', '도착시각'];
  const colXs = [pad];
  for (let i = 0; i < colRatios.length; i++) {
    colXs.push(colXs[i] + tableW * colRatios[i]);
  }

  ctx.fillStyle = pal.subtle;
  drawRoundedRect(ctx, pad + 1, tableTop + 1, tableW - 2, headerH - 1, 9);
  ctx.fill();

  ctx.strokeStyle = pal.border;
  ctx.beginPath();
  ctx.moveTo(pad, tableTop + headerH);
  ctx.lineTo(pad + tableW, tableTop + headerH);
  ctx.stroke();

  ctx.fillStyle = pal.textSub;
  ctx.font = `800 11px ${FONT_STACK}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  for (let i = 0; i < colTitles.length; i++) {
    const cx = (colXs[i] + colXs[i + 1]) / 2;
    ctx.fillText(colTitles[i], cx, tableTop + headerH / 2);
  }

  rows.forEach((r, idx) => {
    const y = tableTop + headerH + idx * rowH;
    const cy = y + rowH / 2;
    if (idx > 0) {
      ctx.strokeStyle = pal.border;
      ctx.lineWidth = 0.6;
      ctx.beginPath();
      ctx.moveTo(pad, y);
      ctx.lineTo(pad + tableW, y);
      ctx.stroke();
    }

    const rank = CrewMateDomainAdapter.classifySegmentPriority(r.pjtHrDvNm);
    const dvText = String(r.pjtHrDvNm || '').trim();
    const badgeBg = rank === 1 ? pal.blueBg : rank === 2 ? pal.amberBg : pal.slateBg;
    const badgeTx = rank === 1 ? pal.blueText : rank === 2 ? pal.amberText : pal.slateText;

    ctx.font = `800 10.5px ${FONT_STACK}`;
    const bw = Math.min(colXs[1] - colXs[0] - 8, Math.max(36, ctx.measureText(dvText).width + 12));
    const bx = (colXs[0] + colXs[1]) / 2 - bw / 2;
    ctx.fillStyle = badgeBg;
    drawRoundedRect(ctx, bx, cy - 10, bw, 20, 5);
    ctx.fill();
    ctx.fillStyle = badgeTx;
    ctx.fillText(dvText, (colXs[0] + colXs[1]) / 2, cy);

    const trn = String(r.trnNo || '').trim();
    ctx.fillStyle = pal.primary;
    ctx.font = `900 11.5px ${FONT_STACK}`;
    ctx.fillText(trn, (colXs[1] + colXs[2]) / 2, cy);

    ctx.fillStyle = pal.textMain;
    ctx.font = `700 11.5px ${FONT_STACK}`;
    ctx.fillText(String(r.dptStnNm || ''), (colXs[2] + colXs[3]) / 2, cy);

    ctx.font = `800 11.5px ${FONT_STACK}`;
    ctx.fillText(XcrewRawFormatter.formatHHMM(r.dptTm), (colXs[3] + colXs[4]) / 2, cy);

    ctx.font = `700 11.5px ${FONT_STACK}`;
    ctx.fillText(String(r.arvStnNm || ''), (colXs[4] + colXs[5]) / 2, cy);

    ctx.font = `800 11.5px ${FONT_STACK}`;
    ctx.fillText(XcrewRawFormatter.formatHHMM(r.arvTm), (colXs[5] + colXs[6]) / 2, cy);
  });

  const dataUrl = canvas.toDataURL('image/png');
  const filename = `CrewMate_다이아_${pjtDt}_${pdiaNo}.png`;
  triggerDownloadSync(dataUrl, filename);
  return { dataUrl, filename };
}

export async function captureDiaDiagramToPng({ pjtDt, pdiaNo, svgElement, isDark }) {
  if (!svgElement) throw new Error('SVG 요소를 찾을 수 없습니다.');
  const pal = getPalette(isDark);
  const scale = 2;
  const pad = 12;
  const bannerH = 54;
  const gap = 10;

  const clone = svgElement.cloneNode(true);
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  const vb = svgElement.viewBox && svgElement.viewBox.baseVal;
  const rect = svgElement.getBoundingClientRect();
  const svgW = vb && vb.width > 0 ? vb.width : Math.max(360, Math.round(rect.width) || 400);
  const svgH = vb && vb.height > 0 ? vb.height : Math.max(240, Math.round(rect.height) || 320);
  clone.setAttribute('width', String(svgW));
  clone.setAttribute('height', String(svgH));

  const xml = new XMLSerializer().serializeToString(clone);
  const svgDataUrl = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(xml);

  const img = await new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('SVG 이미지 변환 실패'));
    image.src = svgDataUrl;
  });

  const width = Math.max(460, svgW + pad * 2 + 16);
  const totalH = pad + bannerH + gap + svgH + 16 + pad;
  const canvas = document.createElement('canvas');
  canvas.width = width * scale;
  canvas.height = totalH * scale;
  const ctx = canvas.getContext('2d');
  ctx.scale(scale, scale);

  ctx.fillStyle = pal.bg;
  ctx.fillRect(0, 0, width, totalH);

  const dateText = XcrewRawFormatter.formatYMD(pjtDt);
  drawCaptureBanner(
    ctx,
    width,
    pad,
    pal,
    `🗺️ ${dateText} · 다이아 ${pdiaNo}`,
    '승무 행로도 (참고용)'
  );

  const cardTop = pad + bannerH + gap;
  const cardW = width - pad * 2;
  const cardH = svgH + 16;
  ctx.fillStyle = pal.card;
  drawRoundedRect(ctx, pad, cardTop, cardW, cardH, 10);
  ctx.fill();
  ctx.strokeStyle = pal.border;
  ctx.lineWidth = 1;
  ctx.stroke();

  const drawX = pad + (cardW - svgW) / 2;
  ctx.drawImage(img, drawX, cardTop + 8, svgW, svgH);

  const dataUrl = canvas.toDataURL('image/png');
  const filename = `CrewMate_행로도_${pjtDt}_${pdiaNo}.png`;
  triggerDownloadSync(dataUrl, filename);
  return { dataUrl, filename };
}

function getBadgeColors(shiftType, pal) {
  if (shiftType === 'REST') return { bg: pal.restBg, text: pal.restText, border: pal.restBorder };
  if (shiftType === 'EDU_LEAVE') return { bg: pal.purpleBg, text: pal.purpleText, border: pal.purpleBorder };
  if (shiftType === 'NIGHT_DAY1' || shiftType === 'NIGHT_DAY2') {
    return { bg: pal.nightBg, text: pal.nightText, border: pal.nightBorder };
  }
  return { bg: pal.dayBg, text: pal.dayText, border: pal.dayBorder };
}

export function captureMonthlyScheduleToPng({ ym, viewMode, items, isDark }) {
  const pal = getPalette(isDark);
  const year = parseInt(ym.slice(0, 4), 10);
  const month = parseInt(ym.slice(4, 6), 10);
  const scale = 2;
  const pad = 12;
  const bannerH = 54;
  const gap = 10;

  const byDate = {};
  (items || []).forEach((it) => {
    if (it && it.pjtDt) byDate[String(it.pjtDt).trim()] = it;
  });

  if (viewMode === 'LIST') {
    const width = 560;
    const headerH = 30;
    const rowH = 30;
    const list = items || [];
    const totalH = pad + bannerH + gap + headerH + Math.max(1, list.length) * rowH + pad;

    const canvas = document.createElement('canvas');
    canvas.width = width * scale;
    canvas.height = totalH * scale;
    const ctx = canvas.getContext('2d');
    ctx.scale(scale, scale);

    ctx.fillStyle = pal.bg;
    ctx.fillRect(0, 0, width, totalH);
    drawCaptureBanner(ctx, width, pad, pal, `📅 ${year}년 ${month}월 승무근무표`, '목록 보기');

    const tableTop = pad + bannerH + gap;
    const tableW = width - pad * 2;
    const tableH = headerH + Math.max(1, list.length) * rowH;

    ctx.fillStyle = pal.card;
    drawRoundedRect(ctx, pad, tableTop, tableW, tableH, 10);
    ctx.fill();
    ctx.strokeStyle = pal.border;
    ctx.lineWidth = 1;
    ctx.stroke();

    const colRatios = [0.16, 0.24, 0.28, 0.16, 0.16];
    const colTitles = ['일자', '다이아번호', '대표열번', '출근', '퇴근'];
    const colXs = [pad];
    for (let i = 0; i < colRatios.length; i++) {
      colXs.push(colXs[i] + tableW * colRatios[i]);
    }

    ctx.fillStyle = pal.subtle;
    drawRoundedRect(ctx, pad + 1, tableTop + 1, tableW - 2, headerH - 1, 9);
    ctx.fill();
    ctx.strokeStyle = pal.border;
    ctx.beginPath();
    ctx.moveTo(pad, tableTop + headerH);
    ctx.lineTo(pad + tableW, tableTop + headerH);
    ctx.stroke();

    ctx.fillStyle = pal.textSub;
    ctx.font = `800 11px ${FONT_STACK}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    colTitles.forEach((t, i) => ctx.fillText(t, (colXs[i] + colXs[i + 1]) / 2, tableTop + headerH / 2));

    list.forEach((ev, idx) => {
      const y = tableTop + headerH + idx * rowH;
      const cy = y + rowH / 2;
      if (idx > 0) {
        ctx.strokeStyle = pal.border;
        ctx.lineWidth = 0.6;
        ctx.beginPath();
        ctx.moveTo(pad, y);
        ctx.lineTo(pad + tableW, y);
        ctx.stroke();
      }

      const ymd = String(ev.pjtDt || '').trim();
      const nextEv = byDate[shiftDateYmd(ymd, 1)];
      const dayInfo = XcrewRawFormatter.formatDayOnlyWithDow(ymd);

      ctx.fillStyle = dayInfo.dowIdx === 0 ? pal.sunText : dayInfo.dowIdx === 6 ? pal.satText : pal.textMain;
      ctx.font = `800 11px ${FONT_STACK}`;
      ctx.fillText(dayInfo.text, (colXs[0] + colXs[1]) / 2, cy);

      const pdiaNo = String(ev.pdiaNo || '').trim();
      if (pdiaNo) {
        const shiftType = CrewMateDomainAdapter.determineShiftType(ev, nextEv);
        const bc = getBadgeColors(shiftType, pal);
        const isHol = CrewMateDomainAdapter.isHolidayWork(ev);
        ctx.font = `800 10.5px ${FONT_STACK}`;
        const bw = Math.min(colXs[2] - colXs[1] - 20, Math.max(36, ctx.measureText(pdiaNo).width + 14));
        const totalBadgeW = isHol ? bw + 18 : bw;
        let bx = (colXs[1] + colXs[2]) / 2 - totalBadgeW / 2;
        if (isHol) {
          ctx.fillStyle = '#dc2626';
          drawRoundedRect(ctx, bx, cy - 8, 15, 16, 4);
          ctx.fill();
          ctx.fillStyle = '#ffffff';
          ctx.font = `900 9px ${FONT_STACK}`;
          ctx.fillText('휴', bx + 7.5, cy);
          bx += 18;
        }
        ctx.fillStyle = bc.bg;
        drawRoundedRect(ctx, bx, cy - 9.5, bw, 19, 5);
        ctx.fill();
        ctx.strokeStyle = bc.border;
        ctx.lineWidth = 1;
        ctx.stroke();
        ctx.fillStyle = bc.text;
        ctx.font = `800 10.5px ${FONT_STACK}`;
        ctx.fillText(pdiaNo, bx + bw / 2, cy);
      }

      const isNight2 = String(ev.alcSpecDvCd) === '12';
      const trn1 = isNight2 ? '' : String(ev.repTrn1No || '').trim();
      const trn2 = isNight2 ? '' : String(ev.repTrn2No || '').trim();
      const trnCombined = trn1 && trn2 ? `${trn1} · ${trn2}` : trn1 || trn2 || '';
      ctx.fillStyle = pal.textSub;
      ctx.font = `700 10px ${FONT_STACK}`;
      ctx.fillText(trnCombined, (colXs[2] + colXs[3]) / 2, cy);

      const gwk = isNight2 ? '' : XcrewRawFormatter.formatHHMM(ev.gwkTm);
      const loiw = isNight2 ? '' : XcrewRawFormatter.formatHHMM(ev.loiwTm);
      ctx.fillStyle = pal.textMain;
      ctx.font = `800 11px ${FONT_STACK}`;
      ctx.fillText(gwk, (colXs[3] + colXs[4]) / 2, cy);
      ctx.fillText(loiw, (colXs[4] + colXs[5]) / 2, cy);
    });

    const dataUrl = canvas.toDataURL('image/png');
    const filename = `CrewMate_근무표_${ym}_목록.png`;
    triggerDownloadSync(dataUrl, filename);
    return { dataUrl, filename };
  }

  // CALENDAR
  const firstDow = new Date(year, month - 1, 1).getDay();
  const lastDate = new Date(year, month, 0).getDate();
  const totalCells = firstDow + lastDate;
  const rowsCount = Math.ceil(totalCells / 7);

  const width = 660;
  const gridW = width - pad * 2;
  const cellW = gridW / 7;
  const dowH = 26;
  const cellH = 88;
  const totalH = pad + bannerH + gap + dowH + rowsCount * cellH + pad;

  const canvas = document.createElement('canvas');
  canvas.width = width * scale;
  canvas.height = totalH * scale;
  const ctx = canvas.getContext('2d');
  ctx.scale(scale, scale);

  ctx.fillStyle = pal.bg;
  ctx.fillRect(0, 0, width, totalH);
  drawCaptureBanner(ctx, width, pad, pal, `📅 ${year}년 ${month}월 승무근무표`, '달력 보기');

  const gridTop = pad + bannerH + gap;
  const gridH = dowH + rowsCount * cellH;
  ctx.fillStyle = pal.card;
  drawRoundedRect(ctx, pad, gridTop, gridW, gridH, 10);
  ctx.fill();
  ctx.strokeStyle = pal.border;
  ctx.lineWidth = 1;
  ctx.stroke();

  const dows = ['일', '월', '화', '수', '목', '금', '토'];
  ctx.fillStyle = pal.subtle;
  drawRoundedRect(ctx, pad + 1, gridTop + 1, gridW - 2, dowH - 1, 9);
  ctx.fill();

  ctx.font = `800 11px ${FONT_STACK}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  dows.forEach((d, idx) => {
    ctx.fillStyle = idx === 0 ? pal.sunText : idx === 6 ? pal.satText : pal.textSub;
    ctx.fillText(d, pad + idx * cellW + cellW / 2, gridTop + dowH / 2);
  });

  ctx.strokeStyle = pal.border;
  ctx.lineWidth = 0.7;
  for (let c = 1; c < 7; c++) {
    ctx.beginPath();
    ctx.moveTo(pad + c * cellW, gridTop);
    ctx.lineTo(pad + c * cellW, gridTop + gridH);
    ctx.stroke();
  }
  for (let r = 0; r <= rowsCount; r++) {
    const y = gridTop + dowH + r * cellH;
    ctx.beginPath();
    ctx.moveTo(pad, y);
    ctx.lineTo(pad + gridW, y);
    ctx.stroke();
  }

  for (let day = 1; day <= lastDate; day++) {
    const cellIdx = firstDow + day - 1;
    const r = Math.floor(cellIdx / 7);
    const c = cellIdx % 7;
    const x = pad + c * cellW;
    const y = gridTop + dowH + r * cellH;

    ctx.textAlign = 'left';
    ctx.fillStyle = c === 0 ? pal.sunText : c === 6 ? pal.satText : pal.textMain;
    ctx.font = `800 11px ${FONT_STACK}`;
    ctx.fillText(String(day), x + 6, y + 12);

    const ymd = ym + pad2(day);
    const nextEv = byDate[shiftDateYmd(ymd, 1)];
    const ev = byDate[ymd];
    if (!ev) continue;

    const pdiaNo = ev.pdiaNo != null ? String(ev.pdiaNo).trim() : '';
    if (pdiaNo) {
      const shiftType = CrewMateDomainAdapter.determineShiftType(ev, nextEv);
      const bc = getBadgeColors(shiftType, pal);
      const isHol = CrewMateDomainAdapter.isHolidayWork(ev);
      let bx = x + 5;
      const by = y + 22;
      if (isHol) {
        ctx.fillStyle = '#dc2626';
        drawRoundedRect(ctx, bx, by, 14, 15, 3);
        ctx.fill();
        ctx.fillStyle = '#ffffff';
        ctx.font = `900 8.5px ${FONT_STACK}`;
        ctx.textAlign = 'center';
        ctx.fillText('휴', bx + 7, by + 7.5);
        bx += 16;
      }
      ctx.font = `800 10px ${FONT_STACK}`;
      const bw = Math.min(cellW - (bx - x) - 5, Math.max(26, ctx.measureText(pdiaNo).width + 10));
      ctx.fillStyle = bc.bg;
      drawRoundedRect(ctx, bx, by, bw, 16, 4);
      ctx.fill();
      ctx.strokeStyle = bc.border;
      ctx.lineWidth = 0.8;
      ctx.stroke();
      ctx.fillStyle = bc.text;
      ctx.textAlign = 'center';
      ctx.fillText(pdiaNo, bx + bw / 2, by + 8);
    }

    const isNight2 = String(ev.alcSpecDvCd) === '12';
    const gwk = !isNight2 ? XcrewRawFormatter.formatHHMM(ev.gwkTm) : '';
    const loiw = !isNight2 ? XcrewRawFormatter.formatHHMM(ev.loiwTm) : '';
    const trn1 = !isNight2 ? String(ev.repTrn1No || '').trim() : '';
    const trn2 = !isNight2 ? String(ev.repTrn2No || '').trim() : '';

    ctx.textAlign = 'left';
    let ty = y + 46;
    if (gwk) {
      ctx.fillStyle = '#ea580c';
      ctx.font = `800 9px ${FONT_STACK}`;
      ctx.fillText('출', x + 6, ty);
      ctx.fillStyle = pal.textMain;
      ctx.font = `700 9.5px ${FONT_STACK}`;
      ctx.fillText(gwk, x + 19, ty);
      ty += 11;
    }
    if (loiw) {
      ctx.fillStyle = '#16a34a';
      ctx.font = `800 9px ${FONT_STACK}`;
      ctx.fillText('퇴', x + 6, ty);
      ctx.fillStyle = pal.textMain;
      ctx.font = `700 9.5px ${FONT_STACK}`;
      ctx.fillText(loiw, x + 19, ty);
      ty += 11;
    }
    if (trn1) {
      ctx.fillStyle = pal.primary;
      ctx.font = `800 9px ${FONT_STACK}`;
      ctx.fillText(trn1, x + 6, ty);
      ty += 10;
    }
    if (trn2) {
      ctx.fillStyle = pal.textSub;
      ctx.font = `700 9px ${FONT_STACK}`;
      ctx.fillText(trn2, x + 6, ty);
    }
  }

  const dataUrl = canvas.toDataURL('image/png');
  const filename = `CrewMate_근무표_${ym}_달력.png`;
  triggerDownloadSync(dataUrl, filename);
  return { dataUrl, filename };
}
