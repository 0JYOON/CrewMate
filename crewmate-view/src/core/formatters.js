/**
 * CrewMate View - Xcrew 원본 포매터 & CrewMate 도메인 어댑터 (Phase 2)
 */

export function escapeHtml(str) {
  return String(str == null ? '' : str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function pad2(n) {
  return n < 10 ? '0' + n : '' + n;
}

export function getTodayYmd() {
  const d = new Date();
  return '' + d.getFullYear() + pad2(d.getMonth() + 1) + pad2(d.getDate());
}

export function getTodayYm() {
  const d = new Date();
  return '' + d.getFullYear() + pad2(d.getMonth() + 1);
}

export function getMonthStartEndYmd(ymStr) {
  const y = parseInt(ymStr.slice(0, 4), 10);
  const m = parseInt(ymStr.slice(4, 6), 10);
  const lastDay = new Date(y, m, 0).getDate();
  return {
    strtDt: ymStr + '01',
    endDt: ymStr + pad2(lastDay),
  };
}

export function shiftDateYmd(ymd, deltaDays) {
  if (!ymd || ymd.length !== 8) return getTodayYmd();
  const y = parseInt(ymd.slice(0, 4), 10);
  const m = parseInt(ymd.slice(4, 6), 10) - 1;
  const d = parseInt(ymd.slice(6, 8), 10);
  const dt = new Date(y, m, d + deltaDays);
  return '' + dt.getFullYear() + pad2(dt.getMonth() + 1) + pad2(dt.getDate());
}

export function shiftMonthYm(ym, deltaMonths) {
  if (!ym || ym.length !== 6) return getTodayYm();
  const y = parseInt(ym.slice(0, 4), 10);
  const m = parseInt(ym.slice(4, 6), 10) - 1;
  const dt = new Date(y, m + deltaMonths, 1);
  return '' + dt.getFullYear() + pad2(dt.getMonth() + 1);
}

export function buildRailBlueUrl(pjtDt, trnNo) {
  const dateStr = String(pjtDt || getTodayYmd()).replace(/\D/g, '').slice(0, 8);
  const cleanTrn = String(trnNo || '').trim();
  if (!cleanTrn) return '';
  return `https://rail.blue/railroad/logis/Default.aspx?date=${encodeURIComponent(dateStr)}&train=${encodeURIComponent(cleanTrn)}#!`;
}

/**
 * Xcrew 원본 문자열 마스크/시각 변환기
 */
export const XcrewRawFormatter = {
  /** '0953' -> '09:53' */
  formatHHMM(val) {
    const s = String(val == null ? '' : val).trim();
    if (!s) return '';
    if (s.length === 4 && /^\d{4}$/.test(s)) {
      return s.slice(0, 2) + ':' + s.slice(2, 4);
    }
    return s;
  },

  /** 분 단위 또는 HHMM 문자열을 시간:분 포맷으로 변환 */
  toHHMM(val) {
    const s = String(val == null ? '' : val).trim();
    if (!s) return '';
    if (s.indexOf(':') !== -1) return s;
    if (typeof String.prototype.toHHMM === 'function') {
      try {
        return s.toHHMM();
      } catch (e) {}
    }
    const num = parseInt(s, 10);
    if (isNaN(num)) return s;
    const h = Math.floor(num / 60);
    const m = num % 60;
    return pad2(h) + ':' + pad2(m);
  },

  /** 원시 분(또는 HH:MM 문자열)을 정수 분(minutes)으로 파싱 */
  parseMinutes(val) {
    if (val == null || val === '') return 0;
    const formatted = this.toHHMM(val);
    if (formatted && formatted.indexOf(':') !== -1) {
      const parts = formatted.split(':');
      const h = parseInt(parts[0], 10) || 0;
      const m = parseInt(parts[1], 10) || 0;
      return h * 60 + m;
    }
    const n = parseInt(String(val).trim(), 10);
    return isNaN(n) ? 0 : n;
  },

  /** 정수 분 합계를 HH:MM 문자열로 포맷 */
  formatTotalMinutes(totalMins) {
    const mins = Math.max(0, Math.round(Number(totalMins) || 0));
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return pad2(h) + ':' + pad2(m);
  },

  /** '20261116' -> '2026-11-16' */
  formatYMD(val) {
    const s = String(val == null ? '' : val).trim();
    if (s.length === 8 && /^\d{8}$/.test(s)) {
      return s.slice(0, 4) + '-' + s.slice(4, 6) + '-' + s.slice(6, 8);
    }
    return s;
  },

  /** '20261116' -> '2026-11-16 (월)' */
  formatYmdWithDow(val) {
    const s = String(val == null ? '' : val).trim();
    if (s.length === 8 && /^\d{8}$/.test(s)) {
      const y = parseInt(s.slice(0, 4), 10);
      const m = parseInt(s.slice(4, 6), 10) - 1;
      const d = parseInt(s.slice(6, 8), 10);
      const dow = ['일', '월', '화', '수', '목', '금', '토'][new Date(y, m, d).getDay()];
      return s.slice(0, 4) + '-' + s.slice(4, 6) + '-' + s.slice(6, 8) + ' (' + dow + ')';
    }
    return s;
  },

  /** '20261116' -> '16(월)' (상단에 연·월이 표출될 때 일자+요일만 컴팩트 표기) */
  formatDayOnlyWithDow(val) {
    const s = String(val == null ? '' : val).replace(/\D/g, '');
    if (s.length === 8) {
      const y = parseInt(s.slice(0, 4), 10);
      const m = parseInt(s.slice(4, 6), 10) - 1;
      const d = parseInt(s.slice(6, 8), 10);
      const dt = new Date(y, m, d);
      const dowIdx = dt.getDay();
      const dow = ['일', '월', '화', '수', '목', '금', '토'][dowIdx];
      return {
        text: pad2(d) + '(' + dow + ')',
        dayNum: d,
        dowIdx,
      };
    }
    return { text: String(val || ''), dayNum: 0, dowIdx: -1 };
  },

  /** '202611160953' -> '11-16 09:53' */
  formatMMDD_HHMM(val) {
    const s = String(val == null ? '' : val).trim();
    if (!s) return '';
    const sub = s.length >= 12 ? s.substring(4, 12) : s.substring(4);
    if (sub.length === 8 && /^\d{8}$/.test(sub)) {
      return sub.slice(0, 2) + '-' + sub.slice(2, 4) + ' ' + sub.slice(4, 6) + ':' + sub.slice(6, 8);
    }
    return s;
  },

  /** 개인근무명세서 & 개인승무근무표의 다이아번호 원본 포매터 (fnFrmtrDiaNo) */
  renderDiaNoHtml(data, row, mode = 'IND_SCHEDULE') {
    const safeDia = escapeHtml(data == null ? '' : data);
    if (!row) return safeDia;
    const classes = [];
    if (data === 'S') classes.push('cm-dia-red');
    else if (String(row.pdiaDvCd) === '3') classes.push('cm-dia-blue');
    if (row.pjtChgPrsFlg === 'Y') classes.push('cm-dia-underline');
    if (String(row.alcSpecDvCd) === '21') classes.push('cm-dia-bold');

    const isHolidayFlag = mode === 'WORK_SPEC' ? row.offdDdFlg === 'Y' : row.hldyYn === 'Y';
    const prefix =
      String(row.alcSpecDvCd) !== '02' && isHolidayFlag
        ? '<span class="cm-holiday-pill">휴</span>'
        : '';

    return prefix + '<span class="' + classes.join(' ') + '">' + safeDia + '</span>';
  },

  /** 출무현황 출무구분 원본 판정식 (fmtGtwcGubun) */
  resolveAttendanceGubun(code, row, crwPlnDvCd = '32') {
    const c = String(code == null ? '' : code).trim();
    const canMidAttend = crwPlnDvCd === '12' || crwPlnDvCd === '22' || crwPlnDvCd === '32';
    switch (c) {
      case '1':
        return { label: '출무전', badgeClass: 'cm-badge-slate', canDoGtwc: false };
      case '2':
        if (row && String(row.gtwcTpDvCd) === '2' && canMidAttend) {
          return { label: '도중출무', badgeClass: 'cm-badge-action', canDoGtwc: true };
        }
        return { label: '출무임박', badgeClass: 'cm-badge-amber', canDoGtwc: false };
      case '3':
        return { label: '도중출무완료', badgeClass: 'cm-badge-blue', canDoGtwc: false };
      case '4':
        if (row && String(row.gtwcTpDvCd) === '2' && row.flg === 'Y') {
          return { label: '도중출무', badgeClass: 'cm-badge-action', canDoGtwc: true };
        }
        return { label: '미출무', badgeClass: 'cm-badge-red', canDoGtwc: false };
      case '5':
        return { label: '적합성미완료', badgeClass: 'cm-badge-amber', canDoGtwc: false };
      case '6':
        return { label: '적합성완료', badgeClass: 'cm-badge-green', canDoGtwc: false };
      case '7':
        return { label: '지각', badgeClass: 'cm-badge-red', canDoGtwc: false };
      default:
        return { label: '-', badgeClass: 'cm-badge-slate', canDoGtwc: false };
    }
  },
};

/**
 * CrewMate 도메인 규칙 엔진
 */
export const CrewMateDomainAdapter = {
  /**
   * alcSpecDvCd 기반 근무유형 판정식
   * - 02 (S), 01 (X) -> REST (빨간색)
   * - 21 (일반교육, 연차, 병가 등) -> EDU_LEAVE (보라색)
   * - 12 (야간 둘째날) -> NIGHT_DAY2 (네이비색)
   * - 11: 다음날(nextRow)의 alcSpecDvCd === '12'이면 NIGHT_DAY1 (네이비색), 아니면 DAY (노란색)
   */
  determineShiftType(row, nextRow) {
    if (!row) return 'NONE';
    const alc = String(row.alcSpecDvCd || '').trim();
    const pdia = String(row.pdiaNo || '').trim();
    if (!pdia && !alc) return 'NONE';

    if (alc === '02' || alc === '01' || pdia === 'S' || pdia === 'X') {
      return 'REST';
    }
    if (
      alc === '21' ||
      pdia === '휴' ||
      pdia === '대휴' ||
      pdia === '연차' ||
      pdia === '공가' ||
      pdia === '병가' ||
      pdia === '청원' ||
      pdia === '교육' ||
      pdia === '일반교육'
    ) {
      return 'EDU_LEAVE';
    }
    if (alc === '12' || pdia.indexOf('~') === 0) {
      return 'NIGHT_DAY2';
    }
    if (alc === '11') {
      if (nextRow) {
        const nextAlc = String(nextRow.alcSpecDvCd || '').trim();
        const nextPdia = String(nextRow.pdiaNo || '').trim();
        if (nextAlc === '12' || nextPdia.indexOf('~') === 0) {
          return 'NIGHT_DAY1';
        }
        return 'DAY';
      }
      const gwk = String(row.gwkTm || '').replace(/\D/g, '');
      const loiw = String(row.loiwTm || '').replace(/\D/g, '');
      if (gwk.length === 4 && loiw.length === 4 && parseInt(loiw, 10) < parseInt(gwk, 10)) {
        return 'NIGHT_DAY1';
      }
      return 'DAY';
    }

    const gwk = String(row.gwkTm || '').replace(/\D/g, '');
    const loiw = String(row.loiwTm || '').replace(/\D/g, '');
    if (gwk.length === 4 && loiw.length === 4 && parseInt(loiw, 10) < parseInt(gwk, 10)) {
      return 'NIGHT_DAY1';
    }
    return 'DAY';
  },

  /**
   * 휴일근무 여부 판정
   */
  isHolidayWork(row) {
    if (!row) return false;
    const alc = String(row.alcSpecDvCd || '').trim();
    if (alc === '02') return false;
    return row.hldyYn === 'Y' || row.offdDdFlg === 'Y';
  },

  /**
   * 다이아 상세 시정표 구분 항목 3단계 위계
   * - 1순위: 운전, 승무, 입환, 출고준비, 감시
   * - 2순위: 준비, 편승, 입고정리, 비상
   * - 3순위: 나머지
   */
  classifySegmentPriority(pjtHrDvNm) {
    const label = String(pjtHrDvNm || '').trim();
    if (
      label === '운전' ||
      label === '승무' ||
      label === '입환' ||
      label === '출고준비' ||
      label === '감시'
    ) {
      return 1;
    }
    if (
      label === '준비' ||
      label === '편승' ||
      label === '입고정리' ||
      label === '비상'
    ) {
      return 2;
    }
    return 3;
  },

  /**
   * 승무다이아 운용요일 15개 컬럼 명세
   */
  DIA_RUN_DAYS_SPEC: [
    { key: 'diaRunDtDvCd00', title: '전일' },
    { key: 'diaRunDtDvCd01', title: '월' },
    { key: 'diaRunDtDvCd02', title: '화' },
    { key: 'diaRunDtDvCd03', title: '수' },
    { key: 'diaRunDtDvCd04', title: '목' },
    { key: 'diaRunDtDvCd05', title: '금' },
    { key: 'diaRunDtDvCd06', title: '토' },
    { key: 'diaRunDtDvCd07', title: '일' },
    { key: 'diaRunDtDvCd10', title: '평휴' },
    { key: 'diaRunDtDvCd11', title: '휴휴' },
    { key: 'diaRunDtDvCd12', title: '휴평' },
    { key: 'diaRunDtDvCd14', title: '토휴' },
    { key: 'diaRunDtDvCd15', title: '휴토' },
    { key: 'diaRunDtDvCd16', title: '평평' },
    { key: 'diaRunDtDvCd17', title: '평토' },
  ],
};
