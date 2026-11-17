/**
 * CrewMate View - 승무 행로도(Route Diagram) 빌더 및 SVG 렌더러
 * - CrewMate 본앱(RouteDiagramBuilder.kt + diagram.html) 및 CrewMate Alpha 1:1 호환
 */

import { escapeHtml } from '../../core/formatters.js';

const DEFAULT_STATION_ALIASES = {
  '4서울': '서울',
  신인천: '인천',
  불암산: '당고개',
  당고개: '불암산',
  민둥산: '증산',
  증산: '민둥산',
  평택지제: '지제',
  지제: '평택지제',
  총신대입구: '이수',
  이수: '총신대입구',
};

const DEPOT_PARENT_MAP = {
  오송기지: { parent: '오송', position: 'before' },
  구로기지: { parent: '구로', position: 'after' },
  병점기지: { parent: '병점', position: 'after' },
  문산기지: { parent: '문산', position: 'before' },
  창동기지: { parent: '창동', position: 'after' },
  이문기지: { parent: '광운대', position: 'after' },
  수색기지: { parent: '수색', position: 'before' },
  시흥기지: { parent: '오이도', position: 'after' },
  가야기지: { parent: '가야', position: 'after' },
  제천조차장: { parent: '제천', position: 'before' },
  대전조차장: { parent: '대전', position: 'before' },
  동해기지: { parent: '동해', position: 'after' },
  분당기지: { parent: '죽전', position: 'before' },
};

const RAILWAY_LINES = [
  { id: 'line1_gyeongin', stations: ['연천', '전곡', '청산', '소요산', '동두천', '보산', '동두천중앙', '지행', '덕정', '덕계', '양주', '녹양', '가능', '의정부', '회룡', '망월사', '도봉산', '도봉', '방학', '창동', '녹천', '월계', '광운대', '석계', '신이문', '외대앞', '회기', '청량리', '제기동', '신설동', '동묘앞', '동대문', '종로5가', '종로3가', '종각', '시청', '서울', '남영', '용산', '노량진', '대방', '신길', '영등포', '신도림', '구로', '구일', '개봉', '오류동', '온수', '역곡', '소사', '부천', '중동', '송내', '부개', '부평', '백운', '동암', '간석', '주안', '도화', '제물포', '도원', '동인천', '인천'] },
  { id: 'line1_gyeongbu_south', stations: ['광운대', '청량리', '서울', '용산', '노량진', '영등포', '신도림', '구로', '가산디지털단지', '독산', '금천구청', '석수', '관악', '안양', '명학', '금정', '군포', '당정', '의왕', '오봉', '성균관대', '화서', '수원', '세류', '병점', '세마', '오산대', '오산', '진위', '송탄', '서정리', '평택지제', '지제', '평택', '성환', '직산', '두정', '천안', '봉명', '쌍용', '아산', '탕정', '배방', '온양온천', '신창'] },
  { id: 'line4_ansan', stations: ['진접', '오남', '별내별가람', '불암산', '당고개', '상계', '노원', '창동', '쌍문', '수유', '미아', '미아사거리', '길음', '성신여대입구', '한성대입구', '혜화', '동대문', '동대문역사문화공원', '충무로', '명동', '회현', '서울', '숙대입구', '삼각지', '신용산', '이촌', '동작', '총신대입구', '이수', '사당', '남태령', '선바위', '경마공원', '대공원', '과천', '정부과천청사', '인덕원', '평촌', '범계', '금정', '산본', '수리산', '대야미', '반월', '상록수', '한대앞', '중앙', '고잔', '초지', '안산', '신길온천', '정왕', '오이도'] },
  { id: 'chungbuk_taebaek', stations: ['오송기지', '오송', '청주', '오근장', '청주공항', '내수', '증평', '도안', '보천', '음성', '소이', '주덕', '달천', '충주', '목행', '동량', '삼탄', '공전', '봉양', '제천', '제천조차장', '입석리', '쌍용', '연당', '청령포', '영월', '탄부', '연하', '석항', '예미', '조동', '자미원', '민둥산', '증산', '사북', '고한', '추전', '태백', '문곡', '백산', '동백산', '도계', '고사리', '하고사리', '신기', '상정', '미로', '도경리', '동해', '동해기지', '묵호', '망상', '옥계', '정동진', '안인', '강릉'] },
  { id: 'gyeongui_jungang', stations: ['임진강', '운천', '문산', '파주', '월롱', '금촌', '금릉', '운정', '야당', '탄현', '일산', '풍산', '백마', '곡산', '대곡', '능곡', '행신', '강매', '한국항공대', '화전', '수색', '디지털미디어시티', '가좌', '신촌', '서울', '홍대입구', '서강대', '공덕', '효창공원앞', '용산', '이촌', '서빙고', '한남', '옥수', '응봉', '왕십리', '청량리', '회기', '중랑', '상봉', '망우', '양원', '구리', '도농', '양정', '덕소', '도심', '팔당', '운길산', '양수', '신원', '아신', '오빈', '양평', '원덕', '용문', '지평'] },
  { id: 'suin_bundang', stations: ['청량리', '왕십리', '서울숲', '압구정로데오', '강남구청', '선정릉', '선릉', '한티', '도곡', '구룡', '개포동', '대모산입구', '수서', '복정', '가천대', '태평', '모란', '야탑', '이매', '서현', '수내', '정자', '미금', '오리', '죽전', '보정', '구성', '신갈', '기흥', '상갈', '청명', '영통', '망포', '매탄권선', '수원시청', '매교', '수원', '고색', '오목천', '어천', '야목', '사리', '한대앞', '중앙', '고잔', '초지', '안산', '신길온천', '정왕', '오이도', '달월', '월곶', '소래포구', '인천논현', '호구포', '남동인더스파크', '원인재', '연수', '송도', '인하대', '숭의', '신포', '인천'] },
  { id: 'gyeongchun', stations: ['광운대', '청량리', '회기', '중랑', '상봉', '망우', '신내', '갈매', '별내', '퇴계원', '사릉', '금곡', '평내호평', '천마산', '마석', '대성리', '청평', '상천', '가평', '굴봉산', '백양리', '강촌', '김유정', '남춘천', '춘천'] },
  { id: 'line3_ilsan', stations: ['대화', '주엽', '정발산', '마두', '백석', '대곡', '화정', '원당', '원흥', '삼송', '지축', '구파발', '연신내', '불광', '녹번', '홍제', '무악재', '독립문', '경복궁', '안국', '종로3가', '을지로3가', '충무로', '동대입구', '약수', '금호', '옥수', '압구정', '신사', '잠원', '고속터미널', '교대', '남부터미널', '양재', '매봉', '도곡', '대치', '학여울', '대청', '일원', '수서', '가락시장', '경찰병원', '오금'] },
  { id: 'seohae', stations: ['일산', '풍산', '백마', '곡산', '대곡', '능곡', '김포공항', '원종', '부천종합운동장', '소사', '소새울', '시흥대야', '신천', '신현', '시흥시청', '시흥능곡', '달미', '선부', '초지', '원시'] },
  { id: 'gyeongbu_main', stations: ['행신', '서울', '영등포', '용산', '광명', '수원', '천안아산', '천안', '오송', '조치원', '신탄진', '대전', '대전조차장', '옥천', '영동', '김천구미', '김천', '구미', '왜관', '신동', '서대구', '대구', '동대구', '경산', '청도', '밀양', '삼랑진', '물금', '양산화물', '구포', '부산진', '신경주', '경주', '울산', '부산'] },
  { id: 'honam_jeolla', stations: ['행신', '서울', '용산', '광명', '천안아산', '오송', '공주', '서대전', '계룡', '논산', '강경', '익산', '김제', '정읍', '장성', '광주송정', '나주', '다시', '함평', '무안', '일로', '임성리', '목포', '삼례', '전주', '임실', '오수', '남원', '곡성', '구례구', '순천', '여천', '흥국사', '적량', '여수EXPO'] },
  { id: 'gangneung_jungang_ktx', stations: ['서울', '청량리', '상봉', '양평', '서원주', '만종', '횡성', '둔내', '평창', '진부', '강릉', '정동진', '묵호', '동해', '원주', '봉양', '도담', '제천', '제천조차장', '입석리', '쌍용', '단양', '풍기', '영주', '안동'] },
];

const MASTER_CACHE_KEY = 'CREWMATE_VIEW_RAILWAY_MASTER_V1';
const REMOTE_MASTER_URL = 'https://crewmate-alpha.pages.dev/railwayMasterData.json';
let masterSyncStarted = false;

function applyMasterData(data) {
  if (!data || typeof data !== 'object') return;
  if (data.aliases && typeof data.aliases === 'object') {
    Object.assign(DEFAULT_STATION_ALIASES, data.aliases);
  }
  if (data.depots && typeof data.depots === 'object') {
    Object.assign(DEPOT_PARENT_MAP, data.depots);
  }
  if (Array.isArray(data.lines) && data.lines.length > 0) {
    RAILWAY_LINES.length = 0;
    data.lines.forEach((ln) => RAILWAY_LINES.push(ln));
  }
}

function ensureRemoteMasterSynced() {
  if (masterSyncStarted) return;
  masterSyncStarted = true;
  try {
    const cached = localStorage.getItem(MASTER_CACHE_KEY);
    if (cached) applyMasterData(JSON.parse(cached));
  } catch (e) {}
  if (typeof fetch !== 'function') return;
  fetch(REMOTE_MASTER_URL + '?t=' + Date.now(), { cache: 'no-store' })
    .then((r) => (r && r.ok ? r.json() : null))
    .then((data) => {
      if (!data) return;
      applyMasterData(data);
      try {
        localStorage.setItem(MASTER_CACHE_KEY, JSON.stringify(data));
      } catch (e) {}
    })
    .catch(() => {});
}

function normalizeStn(name) {
  if (!name) return '';
  let c = String(name).trim().replace(/\s*\(.*?\)\s*/g, '');
  if (c.length > 2 && c.endsWith('역')) c = c.slice(0, -1);
  else if (c === '서울역') c = '서울';
  return DEFAULT_STATION_ALIASES[c] || c;
}

function sortStationsByLineMaster(rawStations) {
  const unique = [];
  rawStations.forEach((s) => {
    const t = String(s || '').trim();
    if (t && unique.indexOf(t) === -1) unique.push(t);
  });
  if (unique.length <= 1) return unique;

  let bestLine = null;
  let maxScore = 0;
  RAILWAY_LINES.forEach((line) => {
    const set = new Set(line.stations);
    let score = 0;
    unique.forEach((s) => {
      const n = normalizeStn(s);
      if (set.has(n) || set.has(s)) score++;
      else if (DEPOT_PARENT_MAP[n] && set.has(DEPOT_PARENT_MAP[n].parent)) score++;
    });
    if (score > maxScore) {
      maxScore = score;
      bestLine = line;
    }
  });

  if (!bestLine || maxScore < 2) return unique;

  const lineList = bestLine.stations;
  function getPos(stn) {
    const n = normalizeStn(stn);
    let idx = lineList.indexOf(stn);
    if (idx !== -1) return idx;
    idx = lineList.indexOf(n);
    if (idx !== -1) return idx;
    const depot = DEPOT_PARENT_MAP[n] || DEPOT_PARENT_MAP[stn];
    if (depot) {
      const pIdx = lineList.indexOf(depot.parent);
      if (pIdx !== -1) return depot.position === 'before' ? pIdx - 0.3 : pIdx + 0.3;
    }
    return 9999;
  }

  return unique.slice().sort((a, b) => getPos(a) - getPos(b));
}

function formatMinsDwell(mins) {
  const m = Number(mins) || 0;
  if (m < 60) return String(m);
  const h = Math.floor(m / 60);
  const r = m % 60;
  return (h < 10 ? '0' + h : '' + h) + ':' + (r < 10 ? '0' + r : '' + r);
}

function formatTimeDisp(raw) {
  const digits = String(raw || '').replace(/\D/g, '');
  if (digits.length >= 4) return digits.slice(0, 2) + ':' + digits.slice(2, 4);
  return String(raw || '').trim();
}

function formatDwellBadgeLabel(segs) {
  if (!segs || !segs.length) return '';
  const parts = [];
  segs.forEach((s) => {
    const name = String(s.pjtHrDvNm || '').trim();
    const mins = Number(s.pjtTnum) || 0;
    if (mins <= 0 && !name) return;
    let tag = '업';
    if (name.indexOf('출고') !== -1) tag = '출고';
    else if (name.indexOf('정리') !== -1 || name.indexOf('입고') !== -1) tag = '정';
    else if (name.indexOf('준비') !== -1) tag = '준';
    else if (name.indexOf('입환') !== -1) tag = '입환';
    else if (name.indexOf('감시') !== -1) tag = '감';
    else if (name.indexOf('휴게') !== -1 || name.indexOf('식사') !== -1) tag = '휴';
    else if (name.indexOf('승계') !== -1 || name.indexOf('대기') !== -1) tag = '승';
    else if (name.indexOf('편승') !== -1) tag = '편)';
    else if (name) tag = name.slice(0, 1);
    parts.push(tag + formatMinsDwell(mins));
  });
  return parts.length ? '(' + parts.join(' ') + ')' : '';
}

export function buildRouteDiagramModel(rawSegments) {
  ensureRemoteMasterSynced();
  const segs = Array.isArray(rawSegments) ? rawSegments : [];
  const rawStns = [];
  segs.forEach((s) => {
    const d = String(s.dptStnNm || '').trim();
    const a = String(s.arvStnNm || '').trim();
    if (d) rawStns.push(d);
    if (a) rawStns.push(a);
  });
  const stations = sortStationsByLineMaster(rawStns);

  const steps = [];
  let dwellGroup = [];
  let dwellGroupIsBlueCore = null;
  let lastStation = '';
  let stepIdx = 0;

  function flushDwell(stationName, arvTime, dptTime) {
    if (!dwellGroup.length) return;
    const label = formatDwellBadgeLabel(dwellGroup);
    const totalMins = dwellGroup.reduce((acc, it) => acc + (Number(it.pjtTnum) || 0), 0);
    const isBlueCore = dwellGroupIsBlueCore === true;
    const hasWatch = dwellGroup.some((it) => String(it.pjtHrDvNm || '').indexOf('감시') !== -1);
    const hasPrep = dwellGroup.some((it) => String(it.pjtHrDvNm || '').indexOf('출고') !== -1);
    const hasShunt = dwellGroup.some((it) => String(it.pjtHrDvNm || '').indexOf('입환') !== -1);
    const isWatchOrPrepNode = hasWatch || hasPrep || hasShunt;

    const firstWithDpt = dwellGroup.find((it) => String(it.dptTm || '').trim());
    const lastWithArv = dwellGroup.slice().reverse().find((it) => String(it.arvTm || '').trim());
    const actualStartRaw = (firstWithDpt && firstWithDpt.dptTm) || arvTime || '';
    const actualEndRaw = (lastWithArv && lastWithArv.arvTm) || dptTime || actualStartRaw;

    if (label || totalMins > 0) {
      steps.push({
        id: 'step-dwell-' + stepIdx++,
        type: 'DWELL',
        station: stationName || lastStation,
        label,
        durationMinutes: totalMins,
        isBlueCore,
        arvTm: actualEndRaw,
        dptTm: actualStartRaw,
        isWatchOrPrepNode,
        displayStartTime: isWatchOrPrepNode ? formatTimeDisp(actualStartRaw) : '',
        displayEndTime: isWatchOrPrepNode ? formatTimeDisp(actualEndRaw) : '',
      });
    }
    dwellGroup = [];
    dwellGroupIsBlueCore = null;
  }

  const firstStnSeg = segs.find((s) => String(s.dptStnNm || '').trim());
  const startStation = (firstStnSeg && String(firstStnSeg.dptStnNm).trim()) || stations[0] || '';
  const firstTmSeg = segs.find((s) => String(s.dptTm || '').trim());
  const initialStartTime = (firstTmSeg && String(firstTmSeg.dptTm).trim()) || '';

  steps.push({
    id: 'step-start-' + stepIdx++,
    type: 'START',
    station: startStation,
    dptTm: initialStartTime,
  });
  lastStation = startStation;

  segs.forEach((seg) => {
    const cat = String(seg.pjtHrDvNm || '').trim();
    const trn = String(seg.trnNo || '').trim();
    const dptStn = String(seg.dptStnNm || '').trim();
    const arvStn = String(seg.arvStnNm || '').trim();
    const isWatch = cat.indexOf('감시') !== -1;
    const isShunt = cat.indexOf('입환') !== -1;
    const isDeadhead = cat.indexOf('편승') !== -1;
    const isMove =
      dptStn &&
      arvStn &&
      dptStn !== arvStn &&
      (trn || cat.indexOf('본무') !== -1 || cat.indexOf('운전') !== -1 || cat.indexOf('승무') !== -1 || isWatch || isShunt || isDeadhead);

    if (isMove) {
      flushDwell(dptStn, undefined, seg.dptTm);
      let cleanTrn = trn;
      if (cleanTrn === '도') cleanTrn = '도보';
      else if (!cleanTrn) cleanTrn = isWatch || isShunt || isDeadhead ? '' : cat;

      steps.push({
        id: 'step-run-' + stepIdx++,
        type: 'RUN',
        trnNo: cleanTrn,
        fromStation: dptStn,
        toStation: arvStn,
        dptTm: seg.dptTm || '',
        arvTm: seg.arvTm || '',
        durationMinutes: Number(seg.pjtTnum) || 0,
        isDeadhead,
      });
      lastStation = arvStn;
    } else {
      const isBlueCore = isWatch || isShunt || cat.indexOf('출고') !== -1;
      const segStn = dptStn || arvStn || lastStation;
      if (dwellGroupIsBlueCore !== null && (dwellGroupIsBlueCore !== isBlueCore || isBlueCore)) {
        flushDwell(segStn);
      }
      dwellGroupIsBlueCore = isBlueCore;
      dwellGroup.push(seg);
      if (segStn) lastStation = segStn;
    }
  });

  flushDwell(lastStation);

  const lastArvSeg = segs.slice().reverse().find((s) => String(s.arvTm || '').trim());
  const lastEndTime = (lastArvSeg && String(lastArvSeg.arvTm).trim()) || '';
  steps.push({
    id: 'step-end-' + stepIdx++,
    type: 'END',
    station: lastStation || startStation,
    arvTm: lastEndTime,
  });

  // 인접 시각 중복 제거 (v1.0.64 규칙)
  const fmtStart = formatTimeDisp(initialStartTime);
  const fmtEnd = formatTimeDisp(lastEndTime);
  for (let i = 0; i < steps.length; i++) {
    const st = steps[i];
    if (st.type === 'DWELL' && st.isWatchOrPrepNode) {
      const prevRun = steps.slice(0, i).reverse().find((x) => x.type === 'RUN');
      const nextRun = steps.slice(i + 1).find((x) => x.type === 'RUN');
      const prevStep = steps[i - 1];
      const nextStep = steps[i + 1];

      const prevTimes = new Set();
      if (prevRun && prevRun.arvTm) prevTimes.add(formatTimeDisp(prevRun.arvTm));
      if (prevStep && prevStep.type === 'START') prevTimes.add(fmtStart);
      if (prevStep && prevStep.type === 'DWELL' && prevStep.displayEndTime) prevTimes.add(prevStep.displayEndTime);

      const nextTimes = new Set();
      if (nextRun && nextRun.dptTm) nextTimes.add(formatTimeDisp(nextRun.dptTm));
      if (nextStep && nextStep.type === 'END') nextTimes.add(fmtEnd);
      if (nextStep && nextStep.type === 'DWELL' && nextStep.displayStartTime) nextTimes.add(nextStep.displayStartTime);

      if (prevTimes.has(st.displayStartTime)) st.displayStartTime = '';
      if (nextTimes.has(st.displayEndTime) || st.displayEndTime === st.displayStartTime) st.displayEndTime = '';
    }
  }

  return {
    startTime: initialStartTime,
    endTime: lastEndTime,
    stations,
    steps,
  };
}

function estimateWidth(txt, pxSize) {
  let w = 0;
  const s = String(txt || '');
  for (let i = 0; i < s.length; i++) {
    const ch = s.charCodeAt(i);
    w += ch >= 0x3131 && ch <= 0xd79d ? pxSize * 0.95 : pxSize * 0.6;
  }
  return Math.max(16, w + 4);
}

export function renderRouteDiagramSvgHtml(rawSegments, isDarkMode) {
  const model = buildRouteDiagramModel(rawSegments);
  const stations = model.stations || [];
  const steps = model.steps || [];
  if (!stations.length || !steps.length) {
    return '<div class="cm-status-box">행로도를 생성할 구간 데이터가 없습니다.</div>';
  }

  const gridLineCol = isDarkMode ? '#334155' : '#E2E8F0';
  const textPrimary = isDarkMode ? '#F8FAFC' : '#0F172A';
  const textSecondary = isDarkMode ? '#94A3B8' : '#64748B';
  const runBlue = isDarkMode ? '#60A5FA' : '#1D4ED8';
  const deadheadPurple = isDarkMode ? '#A78BFA' : '#7C3AED';
  const startGreen = isDarkMode ? '#4ADE80' : '#15803D';
  const endRed = isDarkMode ? '#F87171' : '#DC2626';
  const pillActiveBg = isDarkMode ? '#2563EB' : '#1E3A8A';

  const colCount = Math.max(stations.length, 2);
  const padX = 46;
  const colWidth = colCount <= 4 ? 102 : 90;
  const badgeWidth = Math.min(78, colWidth - 12);
  const width = padX * 2 + (colCount - 1) * colWidth;

  const getX = (stn) => {
    const idx = stations.indexOf(String(stn || '').trim());
    return idx === -1 ? padX : padX + idx * colWidth;
  };

  const stationHeaderY = 20;
  const timelineStartY = 52;
  let curY = timelineStartY;
  const stepYMap = [];

  for (let i = 0; i < steps.length; i++) {
    const step = steps[i];
    const prevStep = steps[i - 1];
    const nextStep = steps[i + 1];
    if (step.type === 'START') {
      stepYMap.push({ startY: curY, endY: curY, topBuf: 0, botBuf: 0 });
      if (nextStep && nextStep.type === 'RUN') curY += 28;
    } else if (step.type === 'DWELL') {
      const topBuf = prevStep && prevStep.type === 'RUN' ? 26 : 8;
      const botBuf = nextStep && nextStep.type === 'RUN' ? 24 : 8;
      const h = topBuf + 42 + botBuf;
      stepYMap.push({ startY: curY, endY: curY + h, topBuf, botBuf });
      curY += h;
    } else if (step.type === 'RUN') {
      stepYMap.push({ startY: curY, endY: curY, topBuf: 0, botBuf: 0 });
      if (!nextStep || nextStep.type === 'RUN' || nextStep.type === 'END') curY += 56;
    } else if (step.type === 'END') {
      stepYMap.push({ startY: curY, endY: curY, topBuf: 0, botBuf: 0 });
      curY += 20;
    }
  }
  const totalHeight = curY + 8;

  const startStep = steps.find((s) => s.type === 'START');
  const startStn = startStep ? String(startStep.station || '').trim() : '';

  const parts = [];
  parts.push(`<svg viewBox="0 0 ${width} ${totalHeight}" style="width:100%;height:auto;display:block;user-select:none;">`);

  // 1. 배경 1dp 수직 실선 & 상단 역명 헤더
  stations.forEach((stn) => {
    const x = getX(stn);
    const isBase = stn === startStn;
    parts.push(`<line x1="${x}" y1="${stationHeaderY + 12}" x2="${x}" y2="${totalHeight - 10}" stroke="${gridLineCol}" stroke-width="1" />`);
    if (isBase) {
      parts.push(`<rect x="${x - badgeWidth / 2}" y="${stationHeaderY - 12}" width="${badgeWidth}" height="24" rx="12" fill="${pillActiveBg}" />`);
    }
    parts.push(`<text x="${x}" y="${stationHeaderY + 4}" text-anchor="middle" fill="${isBase ? '#FFFFFF' : textPrimary}" font-size="11.5" font-weight="${isBase ? '800' : '700'}">${escapeHtml(stn)}</text>`);
  });

  // 2D 충돌 회피
  const occupiedRects = [];
  function makeRect(x, baselineY, anchor, text, pxSize) {
    const w = estimateWidth(text, pxSize);
    let x1 = x;
    if (anchor === 'middle') x1 = x - w / 2;
    else if (anchor === 'end') x1 = x - w;
    return { x1, y1: baselineY - pxSize, x2: x1 + w, y2: baselineY + 4 };
  }
  function intersectsAny(r) {
    return occupiedRects.some((o) => !(r.x2 + 3 <= o.x1 || r.x1 >= o.x2 + 3 || r.y2 + 2 <= o.y1 || r.y1 >= o.y2 + 2));
  }
  function reserveRect(x, baselineY, anchor, text, pxSize) {
    occupiedRects.push(makeRect(x, baselineY, anchor, text, pxSize));
  }
  function placeAvoiding(x, baselineY, anchor, text, pxSize, minY, maxY) {
    const offsets = [0, 14, -14, 24, -24, 34, -34];
    for (let i = 0; i < offsets.length; i++) {
      const candY = baselineY + offsets[i];
      if (minY !== null && candY - pxSize < minY) continue;
      if (maxY !== null && candY + 4 > maxY) continue;
      const r = makeRect(x, candY, anchor, text, pxSize);
      if (!intersectsAny(r)) {
        occupiedRects.push(r);
        return candY;
      }
    }
    occupiedRects.push(makeRect(x, baselineY, anchor, text, pxSize));
    return baselineY;
  }

  steps.forEach((step, idx) => {
    const yInfo = stepYMap[idx];
    if (step.type === 'START') {
      const x = getX(step.station);
      const y = yInfo.startY;
      const isLeftmost = stations.indexOf(step.station) === 0;
      const tStr = formatTimeDisp(model.startTime || step.dptTm);
      reserveRect(isLeftmost ? x + 8 : x - 8, isLeftmost ? y - 6 : y + 4, isLeftmost ? 'start' : 'end', '출근 ' + tStr, 12);
    } else if (step.type === 'RUN') {
      const x1 = getX(step.fromStation);
      const x2 = getX(step.toStation);
      const y = yInfo.startY;
      const isL2R = x2 > x1;
      const midX = (x1 + x2) / 2;
      reserveRect(midX, y - 10, 'middle', String(step.trnNo || '0000') + ' ▶', 12);
      const dptStr = formatTimeDisp(step.dptTm);
      const arvStr = formatTimeDisp(step.arvTm);
      if (dptStr) reserveRect(isL2R ? x1 + 6 : x1 - 6, y + 18, isL2R ? 'start' : 'end', dptStr, 12);
      if (arvStr) reserveRect(isL2R ? x2 - 6 : x2 + 6, y + 18, isL2R ? 'end' : 'start', arvStr, 12);
    } else if (step.type === 'END') {
      const x = getX(step.station);
      const y = yInfo.startY;
      const isRightmost = stations.indexOf(step.station) === stations.length - 1;
      const tStr = formatTimeDisp(model.endTime || step.arvTm);
      reserveRect(isRightmost ? x - 8 : x + 8, y + 4, isRightmost ? 'end' : 'start', '퇴근 ' + tStr, 12);
    }
  });

  steps.forEach((step, idx) => {
    const yInfo = stepYMap[idx];
    const nextStep = steps[idx + 1];
    const nextYInfo = stepYMap[idx + 1];

    if (step.type === 'START') {
      const x = getX(step.station);
      const y = yInfo.startY;
      const isLeftmost = stations.indexOf(step.station) === 0;
      const tStr = formatTimeDisp(model.startTime || step.dptTm);
      if (nextStep && nextStep.type === 'RUN' && nextYInfo && nextYInfo.startY > y) {
        parts.push(`<line x1="${x}" y1="${y}" x2="${x}" y2="${nextYInfo.startY}" stroke="${textSecondary}" stroke-width="1.6" stroke-dasharray="3 3" />`);
      }
      parts.push(`<circle cx="${x}" cy="${y}" r="4.5" fill="${startGreen}" />`);
      parts.push(`<text x="${isLeftmost ? x + 8 : x - 8}" y="${isLeftmost ? y - 6 : y + 4}" text-anchor="${isLeftmost ? 'start' : 'end'}" fill="${startGreen}" font-size="12" font-weight="800">${escapeHtml('출근 ' + tStr)}</text>`);
    } else if (step.type === 'RUN') {
      const x1 = getX(step.fromStation);
      const x2 = getX(step.toStation);
      const y = yInfo.startY;
      const isL2R = x2 > x1;
      const midX = (x1 + x2) / 2;

      if (nextStep && (nextStep.type === 'RUN' || nextStep.type === 'END') && nextYInfo && nextYInfo.startY > y) {
        parts.push(`<line x1="${x2}" y1="${y}" x2="${x2}" y2="${nextYInfo.startY}" stroke="${textSecondary}" stroke-width="1.6" stroke-dasharray="3 3" />`);
      }

      const strokeCol = step.isDeadhead ? deadheadPurple : runBlue;
      const strokeW = step.isDeadhead ? 2.2 : 2.6;
      const dashAttr = step.isDeadhead ? 'stroke-dasharray="5 4"' : '';

      parts.push(`<line x1="${x1}" y1="${y}" x2="${x2}" y2="${y}" stroke="${strokeCol}" stroke-width="${strokeW}" ${dashAttr} stroke-linecap="round" />`);
      parts.push(`<circle cx="${x1}" cy="${y}" r="3.5" fill="${strokeCol}" />`);
      parts.push(`<circle cx="${x2}" cy="${y}" r="3.5" fill="${strokeCol}" />`);

      const prefix = step.isDeadhead ? '편) ' : '';
      const rawTrn = String(step.trnNo || '').trim();
      const label = isL2R ? prefix + rawTrn + ' ▶' : '◀ ' + prefix + rawTrn;
      parts.push(`<text x="${midX}" y="${y - 10}" text-anchor="middle" fill="${strokeCol}" font-size="12" font-weight="800">${escapeHtml(label.trim())}</text>`);

      const dptStr = formatTimeDisp(step.dptTm);
      const arvStr = formatTimeDisp(step.arvTm);
      if (dptStr) {
        parts.push(`<text x="${isL2R ? x1 + 6 : x1 - 6}" y="${y + 18}" text-anchor="${isL2R ? 'start' : 'end'}" fill="${runBlue}" font-size="12" font-weight="800">${escapeHtml(dptStr)}</text>`);
      }
      if (arvStr) {
        parts.push(`<text x="${isL2R ? x2 - 6 : x2 + 6}" y="${y + 18}" text-anchor="${isL2R ? 'end' : 'start'}" fill="${textPrimary}" font-size="12" font-weight="800">${escapeHtml(arvStr)}</text>`);
      }
    } else if (step.type === 'DWELL') {
      const x = getX(step.station);
      const y1 = yInfo.startY;
      const y2 = yInfo.endY;
      const stnIdx = stations.indexOf(step.station);
      const isLeftmost = stnIdx === 0;
      const isRightmost = stnIdx === stations.length - 1;

      const prevRun = steps.slice(0, idx).reverse().find((s) => s.type === 'RUN');
      const nextRun = steps.slice(idx + 1).find((s) => s.type === 'RUN');
      let hasLeft = false;
      let hasRight = false;
      if (prevRun && prevRun.toStation === step.station) {
        const pX = getX(prevRun.fromStation);
        if (pX < x) hasLeft = true;
        else if (pX > x) hasRight = true;
      }
      if (nextRun && nextRun.fromStation === step.station) {
        const nX = getX(nextRun.toStation);
        if (nX < x) hasLeft = true;
        else if (nX > x) hasRight = true;
      }
      let labelOnRight = true;
      if (isLeftmost) labelOnRight = true;
      else if (isRightmost) labelOnRight = false;
      else if (hasLeft && !hasRight) labelOnRight = true;
      else if (!hasLeft && hasRight) labelOnRight = false;
      else if (hasLeft && hasRight) labelOnRight = !(prevRun && getX(prevRun.fromStation) > x);

      const isBlueCore = Boolean(step.isBlueCore);
      parts.push(`<line x1="${x}" y1="${y1}" x2="${x}" y2="${y2}" stroke="${isBlueCore ? runBlue : textSecondary}" stroke-width="1.6" ${isBlueCore ? '' : 'stroke-dasharray="3 3"'} />`);

      if (step.isWatchOrPrepNode) {
        parts.push(`<circle cx="${x}" cy="${y1}" r="3.5" fill="${runBlue}" />`);
        parts.push(`<circle cx="${x}" cy="${y2}" r="3.5" fill="${runBlue}" />`);
      }

      const safeTop = y1 + yInfo.topBuf;
      const safeBot = y2 - yInfo.botBuf;
      const midSafeY = (safeTop + safeBot) / 2;
      const labelPx = 12;
      const lblAnchor = labelOnRight ? 'start' : 'end';
      const lblX = labelOnRight ? x + 8 : x - 8;
      if (step.label) {
        const placedY = placeAvoiding(lblX, midSafeY + 3.5, lblAnchor, step.label, labelPx, safeTop, safeBot + 8);
        parts.push(`<text x="${lblX}" y="${placedY}" text-anchor="${lblAnchor}" fill="${isBlueCore ? runBlue : textSecondary}" font-size="${labelPx}" font-weight="${isBlueCore ? '800' : '700'}">${escapeHtml(step.label)}</text>`);
      }

      const tmAnchor = labelOnRight ? 'end' : 'start';
      const tmX = labelOnRight ? x - 8 : x + 8;
      if (step.displayStartTime) {
        const placedSY = placeAvoiding(tmX, y1 + 4, tmAnchor, step.displayStartTime, 12, y1 - 8, y2 + 8);
        parts.push(`<text x="${tmX}" y="${placedSY}" text-anchor="${tmAnchor}" fill="${textPrimary}" font-size="12" font-weight="800">${escapeHtml(step.displayStartTime)}</text>`);
      }
      if (step.displayEndTime) {
        const placedEY = placeAvoiding(tmX, y2 + 4, tmAnchor, step.displayEndTime, 12, y1 - 8, y2 + 8);
        parts.push(`<text x="${tmX}" y="${placedEY}" text-anchor="${tmAnchor}" fill="${textPrimary}" font-size="12" font-weight="800">${escapeHtml(step.displayEndTime)}</text>`);
      }
    } else if (step.type === 'END') {
      const x = getX(step.station);
      const y = yInfo.startY;
      const isRightmost = stations.indexOf(step.station) === stations.length - 1;
      const tStr = formatTimeDisp(model.endTime || step.arvTm);
      parts.push(`<circle cx="${x}" cy="${y}" r="4.5" fill="${endRed}" />`);
      parts.push(`<text x="${isRightmost ? x - 8 : x + 8}" y="${y + 4}" text-anchor="${isRightmost ? 'end' : 'start'}" fill="${endRed}" font-size="12" font-weight="800">${escapeHtml('퇴근 ' + tStr)}</text>`);
    }
  });

  parts.push('</svg>');
  return parts.join('');
}
