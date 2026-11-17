/**
 * CrewMate View - Xcrew 현재 페이지 DOM에서 프로필 및 초기 컨텍스트 추출기
 */

import { XCREW_PATHS } from './xcrewApi.js';

export const MENU_DEFINITIONS = [
  {
    id: 'monthly_schedule',
    label: '월별 승무근무표',
    icon: '📅',
    paths: [XCREW_PATHS.PAGE_RLTM_SCHEDULE, XCREW_PATHS.PAGE_IND_SCHEDULE],
  },
  {
    id: 'dia_detail',
    label: '다이아 조회',
    icon: '🚄',
    paths: [XCREW_PATHS.PAGE_DIA_DETAIL],
  },
  {
    id: 'crew_status',
    label: '소속승무원 현황',
    icon: '👥',
    paths: [XCREW_PATHS.PAGE_CREW_STATUS],
  },
  {
    id: 'work_spec',
    label: '개인근무명세서',
    icon: '📑',
    paths: [XCREW_PATHS.PAGE_IND_WORK_SPEC],
  },
  {
    id: 'non_op',
    label: '비실동 현황',
    icon: '🏖️',
    paths: [XCREW_PATHS.PAGE_NON_OP],
  },
  {
    id: 'attendance',
    label: '출무현황',
    icon: '🚆',
    paths: [XCREW_PATHS.PAGE_ATTENDANCE],
  },
];

export function extractUserContextFromDom() {
  let affiliation = '';
  let userName = '';
  let lastLogin = '';
  let crwPlnDvCd = '32';

  // 1. 좌측 사이드바 노란색 프로필 영역 (.sidebar-nav ul.main-menu)
  const sidebarItems = document.querySelectorAll('.sidebar-nav ul.main-menu li');
  sidebarItems.forEach((li) => {
    const txt = (li.innerText || li.textContent || '').replace(/\s+/g, ' ').trim();
    if (txt.indexOf('최근 접속 일시') !== -1) {
      lastLogin = txt.replace('최근 접속 일시', '').trim();
    } else if (!affiliation && !userName) {
      const span = li.querySelector('span');
      if (span && !li.querySelector('a')) {
        const spanTxt = (span.innerText || span.textContent || '').trim();
        const fullTxt = txt.replace(spanTxt, '').trim();
        if (spanTxt && fullTxt) {
          affiliation = fullTxt;
          userName = spanTxt;
        }
      }
    }
  });

  // 2. 폴백: 폼 input[name="empNm"] 또는 텍스트 정규식 매칭
  if (!affiliation || !userName) {
    const bodyText = (document.body ? document.body.innerText : '').replace(/\s+/g, ' ');
    const m = bodyText.match(/([가-힣a-zA-Z0-9]+(?:본부|지사|사업소|센터|역|소|승소|승무원))\s+([가-힣*○O]{2,6})\s+최근\s*접속/);
    if (m) {
      if (!affiliation) affiliation = m[1].trim();
      if (!userName) userName = m[2].trim();
    }
  }

  if (!userName || !affiliation) {
    const inputs = document.querySelectorAll('input[name="empNm"], input.form-control');
    inputs.forEach((inp) => {
      const v = (inp.value || '').trim();
      if (!v) return;
      if (/(?:본부|지사|사업소|센터|역|소|승소)$/.test(v)) {
        if (!affiliation) affiliation = v;
      } else if (/^[가-힣]{2,5}$/.test(v)) {
        if (!userName) userName = v;
      }
    });
  }

  // 3. 인라인 스크립트에서 crwPlnDvCd 추출 (도중출무 가능 직종 판별용)
  const scripts = document.querySelectorAll('script:not([src])');
  scripts.forEach((s) => {
    const code = s.textContent || '';
    const m = code.match(/crwPlnDvCd\s*=\s*['"]([^'"]+)['"]/);
    if (m && m[1]) {
      crwPlnDvCd = m[1].trim();
    }
  });

  // 4. 현재 URL 기준 활성 메뉴 ID 판별
  const pathname = location.pathname || '';
  const matchedMenu = MENU_DEFINITIONS.find((m) =>
    m.paths.some((p) => pathname.indexOf(p) !== -1)
  );
  const initialMenuId = matchedMenu ? matchedMenu.id : 'monthly_schedule';

  return {
    affiliation: affiliation || '소속미상',
    userName: userName || '승무원',
    lastLogin: lastLogin || '',
    crwPlnDvCd,
    initialMenuId,
  };
}
