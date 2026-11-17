/**
 * CrewMate View - 코레일 Xcrew (xcrew.korail.com) 동일 출처(Same-Origin) API 클라이언트
 *
 * [보안 및 무결성 원칙]
 * - 사번(epno), 비밀번호(pwd), 쿠키(JSESSIONID)를 절대 외부로 수집하거나 전송하지 않습니다.
 * - 현재 브라우저에 로그인된 Xcrew 세션 안에서만 동일 출처(Same-Origin) XMLHttpRequest를 수행합니다.
 */

export const XCREW_PATHS = {
  // 페이지 경로 (GET)
  PAGE_RLTM_SCHEDULE: '/extrCrewMg/extrRltmCrewWrkPstt.do',
  PAGE_DIA_DETAIL: '/extrCrewMg/extrCrewDiaList.do',
  PAGE_CREW_STATUS: '/extrCrewMg/extrCrewWrkPsttList.do',
  PAGE_IND_WORK_SPEC: '/extrCrewMg/extrIndWrkSpecList.do',
  PAGE_NON_OP: '/extrCrewMg/extrNonAtulPsttList.do',
  PAGE_IND_SCHEDULE: '/extrCrewMg/extrIndCrewWrkList.do',
  PAGE_ATTENDANCE: '/extrCrewMg/extrTrnQryItmtGtwc.do',

  // 데이터 조회/처리 엔드포인트 (POST JSON)
  API_RLTM_SCHEDULE: '/extrCrewMg/searchExtrRltmCrewWrkPstt.do',
  API_SEARCH_PDIA_NO: '/extrCrewMg/searchPdiaNo.do',
  API_DIA_DETAIL: '/extrCrewMg/searchExtrCrewDia.do',
  API_CREW_STATUS: '/extrCrewMg/searchExtrCrewWrkPstt.do',
  API_IND_WORK_SPEC: '/extrCrewMg/searchExtrIndWrkSpec.do',
  API_NON_OP: '/extrCrewMg/searchExtrNonAtulPstt.do',
  API_IND_SCHEDULE: '/extrCrewMg/searchExtrIndCrewWrk.do',
  API_ATTENDANCE: '/extrCrewMg/searchExtrTrnQryItmtGtwc.do',
  API_REG_GTWC: '/extrCrewMg/regExtrGtwc.do',
  API_NOTI_COUNT: '/cmmn/notiCount.do',
};

/**
 * Xcrew 표준 fnCmmnPostAjax / fnCmmnPostObjectAjax와 100% 동일한 헤더·인코딩으로 POST 요청 수행
 */
export function postXcrewJson(path, params = {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', path, true);
    xhr.withCredentials = true;
    xhr.timeout = 15000;
    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
    xhr.setRequestHeader('Accept', 'application/json, text/javascript, */*; q=0.01');
    xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');

    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4) return;
      const raw = (xhr.responseText || '').replace(/^\uFEFF/, '').trim();
      if (xhr.status === 200 && raw && (raw.charAt(0) === '{' || raw.charAt(0) === '[')) {
        try {
          resolve(JSON.parse(raw));
        } catch (err) {
          reject(new Error('응답 JSON 파싱에 실패했습니다.'));
        }
      } else if (raw.indexOf('loginView.do') !== -1 || xhr.status === 401 || xhr.status === 403) {
        const expErr = new Error('Xcrew 로그인 세션이 만료되었습니다. 다시 로그인해 주세요.');
        expErr.isSessionExpired = true;
        reject(expErr);
      } else {
        reject(new Error('Xcrew 서버 통신 오류 (HTTP ' + xhr.status + ')'));
      }
    };

    xhr.onerror = function () {
      reject(new Error('네트워크 연결 오류가 발생했습니다.'));
    };

    xhr.ontimeout = function () {
      reject(new Error('Xcrew 서버 응답 시간이 초과되었습니다 (15초).'));
    };

    const body = Object.keys(params)
      .filter((k) => params[k] !== undefined && params[k] !== null)
      .map((k) => encodeURIComponent(k) + '=' + encodeURIComponent(String(params[k])))
      .join('&');

    xhr.send(body);
  });
}

export const XcrewApi = {
  /** 1. 당월승무근무표 조회 (pjtYm: 'yyyyMM') */
  fetchRltmSchedule(pjtYm) {
    return postXcrewJson(XCREW_PATHS.API_RLTM_SCHEDULE, { pjtYm });
  },

  /** 2-A. 해당 일자 본인 다이아번호 자동 조회 (pjtDt: 'yyyyMMdd') */
  fetchAutoPdiaNo(pjtDt) {
    return postXcrewJson(XCREW_PATHS.API_SEARCH_PDIA_NO, { pjtDt, pdiaNo: '' });
  },

  /** 2-B. 승무다이아 상세 조회 (pjtDt: 'yyyyMMdd', pdiaNo: '16035') */
  fetchDiaDetail(pjtDt, pdiaNo) {
    return postXcrewJson(XCREW_PATHS.API_DIA_DETAIL, { pjtDt, pdiaNo });
  },

  /** 3. 승무근무표현황 조회 (pjtDt: 'yyyyMMdd') */
  fetchCrewWorkStatus(pjtDt) {
    return postXcrewJson(XCREW_PATHS.API_CREW_STATUS, { pjtDt });
  },

  /** 4. 개인근무명세서 조회 (pjtDt: 'yyyyMMdd') */
  fetchIndWorkSpec(pjtDt) {
    return postXcrewJson(XCREW_PATHS.API_IND_WORK_SPEC, { pjtDt });
  },

  /** 5. 비실동현황 조회 (strtDt: 'yyyyMMdd', endDt: 'yyyyMMdd') */
  fetchNonOpStatus(strtDt, endDt) {
    return postXcrewJson(XCREW_PATHS.API_NON_OP, { strtDt, endDt });
  },

  /** 6. 개인승무근무표 조회 (pjtDt: 'yyyyMMdd', empNm: '홍길동') */
  fetchIndSchedule(pjtDt, empNm) {
    return postXcrewJson(XCREW_PATHS.API_IND_SCHEDULE, { pjtDt, empNm });
  },

  /** 7-A. 출무현황 조회 (pjtDt: 'yyyyMMdd', empNm: '홍길동') */
  fetchAttendanceStatus(pjtDt, empNm) {
    return postXcrewJson(XCREW_PATHS.API_ATTENDANCE, { pjtDt, empNm });
  },

  /** 7-B. 도중출무 등록 (crwGtwcDt, crwGtwcTm, gtwcPlcBlgCd) */
  registerMidAttendance(payload) {
    return postXcrewJson(XCREW_PATHS.API_REG_GTWC, payload);
  },

  /** 알림 건수 조회 */
  fetchNotiCount() {
    return postXcrewJson(XCREW_PATHS.API_NOTI_COUNT, {});
  },
};
