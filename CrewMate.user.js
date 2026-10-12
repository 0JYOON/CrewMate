// ==UserScript==
// @name         CrewMate (코레일 승무원 근무관리)
// @namespace    https://xcrew.korail.com/
// @version      1.1.0
// @description  Xcrew 모바일 화면을 최신 CrewMate UI로 전환합니다 (기존 화면 전환 가능)
// @match        https://xcrew.korail.com/*
// @run-at       document-end
// @grant        none
// ==/UserScript==

(function () {
    'use strict';

    if (window.__CREWMATE_INITIALIZED__) return;
    window.__CREWMATE_INITIALIZED__ = true;

    const STORAGE_CREDS_KEY = 'crewmate_saved_credentials_v1';
    const SESSION_SKIP_AUTOLOGIN = 'crewmate_skip_autologin';
    const SESSION_VIEW_MODE = 'crewmate_view_mode';

    const API_SCHEDULE = '/extrCrewMg/searchExtrRltmCrewWrkPstt.do';
    const API_DIA_DETAIL = '/extrCrewMg/searchExtrCrewDia.do';
    const API_CREW_STATUS = '/extrCrewMg/searchExtrCrewWrkPstt.do';
    const PAGE_LOGIN = '/loginView.do';
    const PAGE_LOGIN_POST = '/login.do';
    const PAGE_MAIN_XCREW = '/extrCrewMg/extrItlCrewWrkPstt.do';

    function getLogoSvg(size = 32) {
        return `<svg width="${size}" height="${size}" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg" style="flex-shrink:0;">
            <defs>
                <linearGradient id="cmGrad" x1="0" y1="0" x2="100" y2="100" gradientUnits="userSpaceOnUse">
                    <stop stop-color="#0F2C59"/>
                    <stop offset="1" stop-color="#1E6FB8"/>
                </linearGradient>
            </defs>
            <rect width="100" height="100" rx="26" fill="url(#cmGrad)"/>
            <path d="M18 30 Q50 12 82 30" stroke="#38BDF8" stroke-width="5.5" stroke-linecap="round"/>
            <rect x="22" y="24" width="56" height="47" rx="11" fill="#FFFFFF"/>
            <rect x="28" y="31" width="44" height="17" rx="5" fill="#0F2C59"/>
            <rect x="26" y="52" width="48" height="5" rx="2.5" fill="#2882C8"/>
            <circle cx="33" cy="63" r="4.2" fill="#38BDF8"/>
            <circle cx="67" cy="63" r="4.2" fill="#38BDF8"/>
            <line x1="34" y1="74" x2="22" y2="88" stroke="#93C5FD" stroke-width="5.5" stroke-linecap="round"/>
            <line x1="66" y1="74" x2="78" y2="88" stroke="#93C5FD" stroke-width="5.5" stroke-linecap="round"/>
            <line x1="27" y1="82" x2="73" y2="82" stroke="#FFFFFF" stroke-width="4" stroke-linecap="round" stroke-opacity="0.85"/>
        </svg>`;
    }

    const state = {
        mode: sessionStorage.getItem(SESSION_VIEW_MODE) || 'crewmate',
        route: 'login',
        menuOpen: false,
        showMonthPicker: false,
        login: {
            isLoading: true,
            isAutoLogin: false,
            employeeNumber: '',
            password: '',
            rememberLogin: false,
            errorMessage: null,
        },
        schedule: {
            yearMonth: getCurrentYearMonth(),
            isLoading: false,
            errorMessage: null,
            items: [],
            diaCache: {},
            selectedDate: null,
        },
        todayWork: {
            isLoading: false,
            errorMessage: null,
            result: null,
        },
        lookup: {
            date: formatDateInput(new Date()),
            trainNumber: '',
            isLoading: false,
            errorMessage: null,
            report: null,
            showDeadhead: false,
            diaCache: {},
        }
    };

    function getCurrentYearMonth() {
        const now = new Date();
        return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`;
    }

    function formatDateInput(date) {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
    }

    function getTodayBasicIso() {
        return formatDateInput(new Date()).replace(/-/g, '');
    }

    function basicIsoToDisplay(iso) {
        if (!iso || iso.length !== 8) return iso || '';
        return `${iso.slice(0, 4)}.${iso.slice(4, 6)}.${iso.slice(6, 8)}`;
    }

    function toDisplayTime(raw) {
        if (!raw) return null;
        const clean = String(raw).trim().replace(':', '');
        if (!/^\d{4}$/.test(clean)) return null;
        return `${clean.slice(0, 2)}:${clean.slice(2, 4)}`;
    }

    function parseSavedCredentials() {
        try {
            const raw = localStorage.getItem(STORAGE_CREDS_KEY);
            if (!raw) return null;
            const parsed = JSON.parse(atob(raw));
            if (parsed && parsed.epno && parsed.pwd) return parsed;
        } catch (e) {
            localStorage.removeItem(STORAGE_CREDS_KEY);
        }
        return null;
    }

    function saveCredentials(epno, pwd) {
        localStorage.setItem(STORAGE_CREDS_KEY, btoa(JSON.stringify({ epno, pwd })));
    }

    function clearCredentials() {
        localStorage.removeItem(STORAGE_CREDS_KEY);
    }

    if (!document.querySelector('meta[name="viewport"]')) {
        const meta = document.createElement('meta');
        meta.name = 'viewport';
        meta.content = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';
        document.head.appendChild(meta);
    }

    const styleEl = document.createElement('style');
    styleEl.textContent = `
        body.crewmate-active > *:not(#crewmate-root):not(#crewmate-floating-btn) {
            display: none !important;
        }
        body.crewmate-active {
            margin: 0 !important;
            padding: 0 !important;
            background: #F4F6F9 !important;
            font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Pretendard", "Malgun Gothic", sans-serif !important;
            color: #0F172A !important;
            -webkit-text-size-adjust: 100%;
        }
        #crewmate-root {
            min-height: 100vh;
            background: #F4F6F9;
            display: flex;
            flex-direction: column;
            box-sizing: border-box;
        }
        #crewmate-root * {
            box-sizing: border-box;
        }
        .cm-topbar {
            background: #FFFFFF;
            border-bottom: 1px solid #E2E8F0;
            box-shadow: 0 1px 3px rgba(15,44,89,0.05);
            padding: 8px 12px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: sticky;
            top: 0;
            z-index: 1000;
        }
        .cm-topbar-left {
            display: flex;
            align-items: center;
            gap: 8px;
            position: relative;
        }
        .cm-icon-btn {
            background: #F1F5F9;
            border: none;
            border-radius: 10px;
            width: 38px;
            height: 38px;
            font-size: 20px;
            font-weight: 700;
            color: #0F2C59;
            cursor: pointer;
        }
        .cm-brand {
            font-size: 20px;
            font-weight: 800;
            color: #0F2C59;
            letter-spacing: -0.3px;
        }
        .cm-logout-pill {
            background: #FFFFFF;
            border: 1px solid #CBD5E1;
            border-radius: 999px;
            color: #334155;
            font-size: 13px;
            font-weight: 600;
            padding: 6px 14px;
            cursor: pointer;
        }
        .cm-text-btn {
            background: none;
            border: none;
            color: #1E56A0;
            font-size: 14px;
            font-weight: 700;
            padding: 6px 10px;
            cursor: pointer;
        }
        .cm-dropdown {
            position: absolute;
            top: 46px;
            left: 0;
            background: #FFFFFF;
            border-radius: 14px;
            box-shadow: 0 10px 28px rgba(15,44,89,0.16);
            border: 1px solid #E2E8F0;
            min-width: 220px;
            overflow: hidden;
            z-index: 1200;
        }
        .cm-dropdown-item {
            display: block;
            width: 100%;
            text-align: left;
            padding: 13px 16px;
            background: none;
            border: none;
            font-size: 15px;
            color: #334155;
            font-weight: 500;
            cursor: pointer;
        }
        .cm-dropdown-item.active {
            font-weight: 800;
            background: #F0F7FF;
            color: #0F2C59;
        }
        .cm-dropdown-divider {
            height: 1px;
            background: #E2E8F0;
        }
        .cm-container {
            padding: 14px;
            max-width: 540px;
            width: 100%;
            margin: 0 auto;
            flex: 1;
        }
        .cm-primary-btn {
            width: 100%;
            background: #0F2C59;
            color: #FFFFFF;
            border: none;
            border-radius: 14px;
            padding: 13px 16px;
            font-size: 15px;
            font-weight: 700;
            cursor: pointer;
        }
        .cm-outline-btn {
            width: 100%;
            background: #FFFFFF;
            color: #1E56A0;
            border: 1.5px solid #1E56A0;
            border-radius: 12px;
            padding: 11px 16px;
            font-size: 14px;
            font-weight: 700;
            cursor: pointer;
        }
        .cm-input {
            width: 100%;
            padding: 12px 14px;
            border: 1px solid #CBD5E1;
            border-radius: 12px;
            font-size: 15px;
            background: #FFFFFF;
            color: #0F172A;
        }
        .cm-month-bar {
            background: #FFFFFF;
            border-radius: 14px;
            padding: 6px 8px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 1px 3px rgba(15,44,89,0.05);
            margin-bottom: 10px;
        }
        .cm-cal-weekdays {
            display: grid;
            grid-template-columns: repeat(7, 1fr);
            text-align: center;
            font-weight: 700;
            font-size: 13px;
            padding: 6px 0;
        }
        .cm-cal-grid {
            display: grid;
            grid-template-columns: repeat(7, minmax(0, 1fr));
            gap: 4px;
        }
        .cm-cal-cell {
            background: #EDF1F7;
            border-radius: 10px;
            min-height: 96px;
            padding: 4px;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .cm-cal-cell.has-work {
            background: #FFFFFF;
            border: 1px solid #D8E2EE;
            cursor: pointer;
        }
                .cm-cal-cell.is-today {
            background: #DCE8F8 !important;
            border: 2px solid #0F2C59 !important;
        }
        .cm-cal-cell.is-today .cm-cal-day {
            color: #0F2C59;
            font-weight: 800;
        }
        .cm-cal-holiday {
            font-size: 8.5px;
            font-weight: 700;
            color: #D32F2F;
            text-align: center;
            margin-top: auto;
            padding-top: 2px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .cm-minor-fold summary {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 12px 0;
            cursor: pointer;
            font-size: 13px;
        }
        .cm-minor-fold summary::-webkit-details-marker { display: none; }
        .cm-minor-fold[open] summary { border-bottom: none; padding-bottom: 6px; }
        .cm-minor-fold summary {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 12px 0;
            cursor: pointer;
            font-size: 13px;
        }
        .cm-minor-fold summary::-webkit-details-marker { display: none; }
        .cm-minor-fold[open] summary { border-bottom: none; padding-bottom: 6px; }
        .cm-dia-card {
            background: #FFFFFF;
            border: 1px solid #E2E8F0;
            box-shadow: 0 2px 6px rgba(15,44,89,0.06);
            border-radius: 18px;
            padding: 16px;
            margin-bottom: 14px;
        }
        .cm-table {
            background: #FFFFFF;
            border: 1px solid #D8E2EE;
            border-radius: 12px;
            overflow: hidden;
        }
        .cm-table-head {
            background: #1E56A0;
            color: #FFFFFF;
            font-weight: 700;
            font-size: 13px;
            padding: 9px 12px;
            display: flex;
            justify-content: space-between;
        }
        .cm-table-row {
            border-top: 1px solid #D8E2EE;
            padding: 6px 12px;
        }
        .cm-table-row.highlight {
            background: #F0F7FF;
            border-left: 4px solid #1E6FB8;
            padding: 10px 12px;
        }
        .cm-sheet-backdrop {
            position: fixed;
            inset: 0;
            background: rgba(15, 23, 42, 0.48);
            display: flex;
            align-items: flex-end;
            justify-content: center;
            z-index: 2000;
        }
        .cm-sheet {
            background: #F4F6F9;
            width: 100%;
            max-width: 540px;
            max-height: 84vh;
            border-radius: 22px 22px 0 0;
            padding: 18px 16px 32px;
            overflow-y: auto;
        }
        #crewmate-floating-btn {
            position: fixed;
            right: 16px;
            bottom: 20px;
            z-index: 99999;
            background: #0F2C59;
            color: #FFFFFF;
            border: none;
            border-radius: 999px;
            padding: 12px 18px;
            font-size: 14px;
            font-weight: 700;
            box-shadow: 0 4px 14px rgba(0,0,0,0.3);
            cursor: pointer;
        }
        .cm-lookup-card {
            background: #FFFFFF;
            border: 1px solid #DCE8F8;
            box-shadow: 0 2px 5px rgba(15,44,89,0.05);
            border-radius: 14px;
            padding: 14px 16px;
            margin-bottom: 10px;
        }
    `;
    document.head.appendChild(styleEl);

    const rootEl = document.createElement('div');
    rootEl.id = 'crewmate-root';
    document.body.appendChild(rootEl);

    const floatingBtn = document.createElement('button');
    floatingBtn.id = 'crewmate-floating-btn';
    floatingBtn.textContent = 'CrewMate 열기';
    floatingBtn.onclick = () => {
        state.mode = 'crewmate';
        sessionStorage.setItem(SESSION_VIEW_MODE, 'crewmate');
        render();
    };
    document.body.appendChild(floatingBtn);

    function xhrPost(path, params) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            const body = Object.entries(params)
                .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
                .join('&');
            xhr.open('POST', path, true);
            xhr.withCredentials = true;
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
            xhr.setRequestHeader('Accept', 'application/json, text/javascript, */*; q=0.01');
            xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
            xhr.onreadystatechange = function () {
                if (xhr.readyState === XMLHttpRequest.DONE) {
                    const contentType = xhr.getResponseHeader('Content-Type') || '';
                    const text = xhr.responseText || '';
                    if (xhr.status === 401 || xhr.status === 403 ||
                        (xhr.status === 200 && (contentType.includes('text/html') || text.trim().startsWith('<')))) {
                        reject(new Error('SESSION_EXPIRED'));
                        return;
                    }
                    if (xhr.status !== 200) {
                        reject(new Error(`HTTP_${xhr.status}`));
                        return;
                    }
                    try {
                        resolve(JSON.parse(text));
                    } catch (e) {
                        reject(new Error('INVALID_JSON'));
                    }
                }
            };
            xhr.onerror = () => reject(new Error('NETWORK_ERROR'));
            xhr.send(body);
        });
    }

    async function performLogin(epno, pwd, remember, isAuto) {
        state.login.isLoading = true;
        state.login.isAutoLogin = isAuto;
        state.login.errorMessage = null;
        render();

        try {
            const pageResp = await fetch(PAGE_LOGIN, { credentials: 'include', cache: 'no-store' });
            const html = await pageResp.text();
            const doc = new DOMParser().parseFromString(html, 'text/html');
            const msgInput = doc.querySelector('input#message[name="message"][type="hidden"]') ||
                             document.querySelector('input#message[name="message"][type="hidden"]');
            if (!msgInput || !msgInput.value) throw new Error('LOGIN_PAGE_ERROR');

            const form = new URLSearchParams();
            form.append('message', msgInput.value);
            form.append('epno', epno);
            form.append('pwd', pwd);

            await fetch(PAGE_LOGIN_POST, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: form.toString(),
            });

            const currentYm = getCurrentYearMonth();
            await xhrPost(API_SCHEDULE, { pjtYm: currentYm });

            if (!isAuto) {
                if (remember) saveCredentials(epno, pwd);
                else clearCredentials();
            }
            sessionStorage.removeItem(SESSION_SKIP_AUTOLOGIN);
            state.login.isLoading = false;
            state.route = 'schedule';
            loadScheduleMonth(state.schedule.yearMonth);
        } catch (err) {
            state.login.isLoading = false;
            state.login.isAutoLogin = false;
            state.login.errorMessage = isAuto
                ? '자동 로그인에 실패했습니다. 다시 로그인해 주세요.'
                : '로그인에 실패했습니다. 사번 또는 비밀번호를 확인해 주세요.';
            render();
        }
    }

    function parseScheduleItems(rawList) {
        if (!Array.isArray(rawList)) return [];
        return rawList.map(item => {
            const rawDia = item.pdiaNo ? String(item.pdiaNo).trim() : '';
            const contMatch = rawDia.match(/^~\(?(.+?)\)?$/);
            return {
                workDate: item.pjtDt ? String(item.pjtDt).trim() : null,
                diaNo: contMatch ? contMatch[1].trim() : rawDia,
                rawDiaNo: rawDia,
                trainNo: item.repTrn1No ? String(item.repTrn1No).trim() : null,
                trainNo2: item.repTrn2No ? String(item.repTrn2No).trim() : null,
                startTime: item.gwkTm ? String(item.gwkTm).trim() : null,
                endTime: item.loiwTm ? String(item.loiwTm).trim() : null,
                nonActiveType: item.nonAtulClsfCdNm ? String(item.nonAtulClsfCdNm).trim() : null,
                isContinuation: Boolean(contMatch),
            };
        });
    }

    function isDiaCandidate(dia) {
        return Boolean(dia && dia !== '연차' && dia !== 'S' && /^[A-Za-z0-9]+$/.test(dia));
    }

    function resolveSecondTrainNo(detail, primaryTrainNo) {
        if (!detail) return null;
        const primary = primaryTrainNo ? primaryTrainNo.trim() : '';
        const metaTrain2 = detail.extrCrewMgVO && detail.extrCrewMgVO.repTrn2No
            ? String(detail.extrCrewMgVO.repTrn2No).trim() : '';
        if (metaTrain2 && metaTrain2 !== primary) return metaTrain2;

        const segments = Array.isArray(detail.data) ? detail.data : [];
        const driving = segments
            .filter(s => s.pjtHrDvNm && String(s.pjtHrDvNm).trim() === '운전')
            .map(s => s.trnNo ? String(s.trnNo).trim() : '')
            .filter(Boolean);
        if (driving.length >= 2) {
            if (primary) {
                const idx = driving.indexOf(primary);
                if (idx >= 0 && idx + 1 < driving.length) return driving[idx + 1];
                const diff = driving.find(t => t !== primary);
                if (diff) return diff;
            }
            return driving[1];
        }
        const all = segments.map(s => s.trnNo ? String(s.trnNo).trim() : '').filter(Boolean);
        if (all.length >= 2) {
            if (primary) {
                const idx = all.indexOf(primary);
                if (idx >= 0 && idx + 1 < all.length) return all[idx + 1];
                const diff = all.find(t => t !== primary);
                if (diff) return diff;
            }
            return all[1];
        }
        return null;
    }

    function prevDateIso(iso) {
        const y = Number(iso.slice(0, 4));
        const m = Number(iso.slice(4, 6)) - 1;
        const d = Number(iso.slice(6, 8));
        const dt = new Date(y, m, d - 1);
        return formatDateInput(dt).replace(/-/g, '');
    }

    async function fetchDiaDetailWithFallback(dateIso, diaNo, isContinuation) {
        const primary = await xhrPost(API_DIA_DETAIL, { pdiaNo: diaNo, pjtDt: dateIso });
        if ((!primary.data || primary.data.length === 0) && isContinuation) {
            try {
                const prev = await xhrPost(API_DIA_DETAIL, { pdiaNo: diaNo, pjtDt: prevDateIso(dateIso) });
                if (prev && Array.isArray(prev.data) && prev.data.length > 0) return prev;
            } catch (_) {}
        }
        return primary;
    }

    async function loadScheduleMonth(ym) {
        state.schedule.yearMonth = ym;
        state.schedule.isLoading = true;
        state.schedule.errorMessage = null;
        state.schedule.selectedDate = null;
        render();

        try {
            const json = await xhrPost(API_SCHEDULE, { pjtYm: ym });
            state.schedule.items = parseScheduleItems(json.data);
            state.schedule.isLoading = false;
            render();
            prefetchMonthDiaDetails(ym, state.schedule.items);
        } catch (err) {
            if (err.message === 'SESSION_EXPIRED') {
                handleSessionExpired();
                return;
            }
            state.schedule.isLoading = false;
            state.schedule.errorMessage = '근무표를 불러오지 못했습니다.';
            render();
        }
    }

    async function prefetchMonthDiaDetails(ym, items) {
        const tasks = items.filter(it => it.workDate && it.workDate.startsWith(ym) && isDiaCandidate(it.diaNo));
        let idx = 0;
        async function worker() {
            while (idx < tasks.length) {
                const item = tasks[idx++];
                const key = `${item.workDate}|${item.diaNo}`;
                if (state.schedule.diaCache[key]) continue;
                state.schedule.diaCache[key] = { status: 'loading' };
                try {
                    const detail = await fetchDiaDetailWithFallback(item.workDate, item.diaNo, item.isContinuation);
                    state.schedule.diaCache[key] = { status: 'success', data: detail };
                    render();
                } catch (e) {
                    state.schedule.diaCache[key] = { status: 'error' };
                }
            }
        }
        await Promise.all([worker(), worker(), worker(), worker()]);
    }

    async function loadTodayWork() {
        state.todayWork.isLoading = true;
        state.todayWork.errorMessage = null;
        render();

        try {
            const todayIso = getTodayBasicIso();
            const currentYm = todayIso.slice(0, 6);
            const curResp = await xhrPost(API_SCHEDULE, { pjtYm: currentYm });
            const curItems = parseScheduleItems(curResp.data)
                .filter(it => it.workDate && it.workDate >= todayIso && isDiaCandidate(it.diaNo));

            let selected = curItems.filter(it => it.workDate === todayIso);
            if (selected.length === 0 && curItems.length > 0) {
                const minDate = curItems.map(it => it.workDate).sort()[0];
                selected = curItems.filter(it => it.workDate === minDate);
            }
            if (selected.length === 0) {
                const y = Number(currentYm.slice(0, 4));
                const m = Number(currentYm.slice(4, 6));
                const nextYm = m === 12 ? `${y + 1}01` : `${y}${String(m + 1).padStart(2, '0')}`;
                const nextResp = await xhrPost(API_SCHEDULE, { pjtYm: nextYm });
                const nextItems = parseScheduleItems(nextResp.data)
                    .filter(it => it.workDate && it.workDate > todayIso && isDiaCandidate(it.diaNo));
                if (nextItems.length > 0) {
                    const minDate = nextItems.map(it => it.workDate).sort()[0];
                    selected = nextItems.filter(it => it.workDate === minDate);
                }
            }

            if (selected.length === 0) {
                state.todayWork.result = null;
                state.todayWork.isLoading = false;
                render();
                return;
            }

            const dias = [];
            for (const schedule of selected) {
                const detail = await fetchDiaDetailWithFallback(schedule.workDate, schedule.diaNo, schedule.isContinuation);
                dias.push({ schedule, detail });
            }
            state.todayWork.result = {
                isToday: selected[0].workDate === todayIso,
                workDate: selected[0].workDate,
                dias,
            };
            state.todayWork.isLoading = false;
            render();
        } catch (err) {
            if (err.message === 'SESSION_EXPIRED') {
                handleSessionExpired();
                return;
            }
            state.todayWork.isLoading = false;
            state.todayWork.errorMessage = '근무 정보를 불러오지 못했습니다. 다시 시도해 주세요.';
            render();
        }
    }

    async function searchTrainCrew() {
        const normalizedTrain = state.lookup.trainNumber.replace(/\D/g, '');
        if (!normalizedTrain) {
            state.lookup.errorMessage = '열차번호를 입력해 주세요.';
            state.lookup.report = null;
            render();
            return;
        }
        const pjtDt = state.lookup.date.replace(/-/g, '');
        state.lookup.isLoading = true;
        state.lookup.errorMessage = null;
        state.lookup.report = null;
        state.lookup.showDeadhead = false;
        render();

        try {
            const statusResp = await xhrPost(API_CREW_STATUS, { pjtDt });
            const rows = Array.isArray(statusResp.data) ? statusResp.data : [];
            const assignmentsByDia = {};
            const diaNumbers = [];

            rows.forEach(row => {
                const pdiaNo = row.pdiaNo ? String(row.pdiaNo).trim() : '';
                if (!pdiaNo) return;
                if (!assignmentsByDia[pdiaNo]) {
                    assignmentsByDia[pdiaNo] = [];
                    if (/^[A-Za-z0-9]+$/.test(pdiaNo)) diaNumbers.push(pdiaNo);
                }
                const crewMembers = [row.empNm1, row.empNm2, row.empNm3, row.empNm4]
                    .map(n => n ? String(n).trim() : '')
                    .filter(Boolean);
                assignmentsByDia[pdiaNo].push({ pdiaNo, crewMembers });
            });

            const detailsMap = {};
            let failedCount = 0;
            let idx = 0;

            async function worker() {
                while (idx < diaNumbers.length) {
                    const dia = diaNumbers[idx++];
                    const cacheKey = `${pjtDt}|${dia}`;
                    if (state.lookup.diaCache[cacheKey]) {
                        detailsMap[dia] = state.lookup.diaCache[cacheKey];
                        continue;
                    }
                    try {
                        const detail = await xhrPost(API_DIA_DETAIL, { pdiaNo: dia, pjtDt });
                        state.lookup.diaCache[cacheKey] = detail;
                        detailsMap[dia] = detail;
                    } catch (_) {
                        failedCount++;
                    }
                }
            }
            await Promise.all([worker(), worker(), worker(), worker()]);

            const matched = [];
            diaNumbers.forEach(dia => {
                const detail = detailsMap[dia];
                if (!detail || !Array.isArray(detail.data)) return;
                detail.data.forEach(seg => {
                    const trn = seg.trnNo ? String(seg.trnNo).replace(/\D/g, '') : '';
                    if (trn && trn === normalizedTrain) {
                        const crewList = (assignmentsByDia[dia] || []).flatMap(a => a.crewMembers);
                        const uniqueCrew = [...new Set(crewList)];
                        matched.push({ pdiaNo: dia, segment: seg, crewMembers: uniqueCrew });
                    }
                });
            });

            matched.sort((a, b) => {
                const ka = a.segment.dptTm ? Number(String(a.segment.dptTm).replace(':', '')) || 999999 : 999999;
                const kb = b.segment.dptTm ? Number(String(b.segment.dptTm).replace(':', '')) || 999999 : 999999;
                return ka - kb;
            });

            state.lookup.report = {
                pjtDt,
                normalizedTrainNumber: normalizedTrain,
                results: matched,
                failedDiaCount: failedCount,
            };
            state.lookup.isLoading = false;
            render();
        } catch (err) {
            if (err.message === 'SESSION_EXPIRED') {
                handleSessionExpired();
                return;
            }
            state.lookup.isLoading = false;
            state.lookup.errorMessage = '승무원 정보를 조회하지 못했습니다. 다시 시도해 주세요.';
            render();
        }
    }

    function handleSessionExpired() {
        state.route = 'login';
        state.login.isLoading = false;
        state.login.errorMessage = 'Xcrew 로그인 세션이 만료되었습니다. 다시 로그인해 주세요.';
        render();
    }

            function renderDiaDetailCardHtml(schedule, detail, workDateIso) {
        const dateIso = workDateIso || schedule.workDate || '';
        const segments = (detail && Array.isArray(detail.data)) ? detail.data : [];
        const summaries = (detail && Array.isArray(detail.dtlData)) ? detail.dtlData : [];

        let rowsHtml = '';
        if (segments.length === 0) {
            rowsHtml = `<div style="padding:12px 0;color:#64748B;">상세 구간이 없습니다.</div>`;
        } else {
            let minorGroup = [];
            let groupedHtml = '';
            const flushMinor = () => {
                if (minorGroup.length === 0) return '';
                if (minorGroup.length === 1) {
                    const html = renderMinorRow(minorGroup[0]);
                    minorGroup = [];
                    return html;
                }
                const names = minorGroup.map(s => String(s.pjtHrDvNm || '').trim()).filter(Boolean).join('·');
                const start = toDisplayTime(minorGroup[0].dptTm) || '-';
                const end = toDisplayTime(minorGroup[minorGroup.length - 1].arvTm) || '-';
                const detailsHtml = minorGroup.map(s => renderMinorRow(s)).join('');
                minorGroup = [];
                return `
                    <details class="cm-minor-fold">
                        <summary>
                            <span style="font-weight:600;color:#64748B;">${names}</span>
                            <span style="color:#64748B;">${start} 〰 ... ➔ ${end}</span>
                        </summary>
                        <div style="padding:6px 10px; border:2px solid #F1F5F9; border-radius:8px; margin-bottom:8px;">
                            ${detailsHtml}
                        </div>
                    </details>
                    <div style="height:1px; background:#F1F5F9; width:100%;"></div>
                `;
            };

            const renderMinorRow = (seg) => {
                const cat = seg.pjtHrDvNm ? String(seg.pjtHrDvNm).trim() : '-';
                const trn = seg.trnNo ? String(seg.trnNo).trim() : '';
                const dptTm = toDisplayTime(seg.dptTm) || '-';
                const arvTm = toDisplayTime(seg.arvTm) || '-';
                const timeStr = (dptTm === '-' && arvTm === '-') ? '-' : `${dptTm} → ${arvTm}`;
                return `
                    <div style="display:flex; justify-content:space-between; align-items:center; padding:8px 0;">
                        <span style="font-size:13px; font-weight:500; color:#64748B;">${cat} ${trn}</span>
                        <span style="font-size:13px; font-weight:500; color:#64748B;">${timeStr}</span>
                    </div>
                `;
            };

            const renderMajorRow = (seg) => {
                const cat = seg.pjtHrDvNm ? String(seg.pjtHrDvNm).trim() : '-';
                const trn = seg.trnNo ? String(seg.trnNo).trim() : '';
                const dptStn = seg.dptStnNm ? String(seg.dptStnNm).trim() : '-';
                const arvStn = seg.arvStnNm ? String(seg.arvStnNm).trim() : '-';
                const dptTm = toDisplayTime(seg.dptTm) || '-';
                const arvTm = toDisplayTime(seg.arvTm) || '-';
                const badge = `background:#0F2C59;color:#FFF;padding:4px 8px;border-radius:6px;font-size:12px;font-weight:700;`;
                return `
                    <div style="display:flex; justify-content:space-between; align-items:center; padding:12px 0;">
                        <div style="display:flex; gap:6px;">
                            <span style="${badge}">${cat}</span>
                            ${trn ? `<span style="${badge}">${trn}</span>` : ''}
                        </div>
                        <div style="text-align:right;">
                            <div style="font-size:14px; font-weight:800; color:#0F172A;">${dptStn} → ${arvStn}</div>
                            <div style="font-size:13px; font-weight:600; color:#334155; margin-top:4px; display:flex; gap:16px; justify-content:flex-end;">
                                <span style="width:42px; text-align:center;">${dptTm}</span>
                                <span style="width:42px; text-align:center;">${arvTm}</span>
                            </div>
                        </div>
                    </div>
                    <div style="height:1px; background:#F1F5F9; width:100%;"></div>
                `;
            };

            segments.forEach(seg => {
                const cat = seg.pjtHrDvNm ? String(seg.pjtHrDvNm).trim() : '';
                const isEmphasized = cat === '운전' || cat.includes('출고준비') || cat.includes('편승') || seg.dptStnNm || seg.arvStnNm;
                if (isEmphasized) {
                    groupedHtml += flushMinor();
                    groupedHtml += renderMajorRow(seg);
                } else {
                    minorGroup.push(seg);
                }
            });
            groupedHtml += flushMinor();
            
            // Remove last divider if exists
            if (groupedHtml.endsWith('<div style="height:1px; background:#F1F5F9; width:100%;"></div>\n                ')) {
                groupedHtml = groupedHtml.slice(0, -68);
            }

            rowsHtml = `
                <div style="border:1px solid #E2E8F0; border-radius:12px; padding:6px 14px;">
                    ${groupedHtml}
                </div>
            `;
        }

        let summaryHtml = '';
        if (summaries.length > 0 || segments.length > 0) {
            const totalMin = summaries.find(s => s.totWrkTnum != null)?.totWrkTnum
                ?? summaries.reduce((acc, s) => acc + (Number(s.pjtTnum) || 0), 0);
            const totalDst = summaries.find(s => s.totPjtDst != null)?.totPjtDst
                ?? summaries.reduce((acc, s) => acc + (Number(s.pjtDst) || 0), 0);
            const hh = String(Math.floor(totalMin / 60)).padStart(2, '0');
            const mm = String(totalMin % 60).padStart(2, '0');
            const distText = Number(totalDst).toFixed(1);
            const breakdown = summaries
                .filter(s => s.pjtHrDvNm)
                .map(s => {
                    const m = Number(s.pjtTnum) || 0;
                    return `${String(s.pjtHrDvNm).trim()} ${Math.floor(m / 60)}시간 ${m % 60}분`;
                }).join(' / ');

            summaryHtml = `
                <div style="margin-top:12px;background:#F8FAFC;border-radius:10px;padding:9px 12px;">
                    <div style="font-size:12px;font-weight:600;color:#475569;">총 근무시간 ${hh}시간 ${mm}분 / 총 사업 거리 ${distText}km</div>
                    ${breakdown ? `<div style="margin-top:2px;font-size:11px;color:#94A3B8;line-height:1.4;">${breakdown}</div>` : ''}
                </div>
            `;
        }

        const prefix = schedule.isContinuation ? '~' : '';
        return `
            <div class="cm-dia-card">
                <div style="display:flex;align-items:flex-end;margin-bottom:14px;">
                    <span style="font-size:24px;font-weight:800;color:#0F2C59;">${prefix}${schedule.diaNo || ''}</span>
                    <span style="font-size:15px;font-weight:700;color:#475569;margin-left:5px;margin-bottom:2px;">DIA</span>
                </div>
                ${rowsHtml}
                ${summaryHtml}
            </div>
        `;
    }

    function render() {
        if (state.mode === 'original') {
            document.body.classList.remove('crewmate-active');
            rootEl.style.display = 'none';
            floatingBtn.style.display = 'block';
            return;
        }

        document.body.classList.add('crewmate-active');
        rootEl.style.display = 'flex';
        floatingBtn.style.display = 'none';

        if (state.route === 'login') {
            renderLogin();
            return;
        }

        const topBarHtml = `
            <div class="cm-topbar">
                <div class="cm-topbar-left">
                    <button class="cm-icon-btn" id="cm-menu-btn">☰</button>
                    ${getLogoSvg(30)}
                    <span class="cm-brand">CrewMate</span>
                    ${state.menuOpen ? `
                        <div class="cm-dropdown">
                            <button class="cm-dropdown-item ${state.route === 'schedule' ? 'active' : ''}" data-nav="schedule">월별 승무근무표</button>
                            <button class="cm-dropdown-item ${state.route === 'today-work' ? 'active' : ''}" data-nav="today-work">오늘의 근무 다이아 확인</button>
                            <button class="cm-dropdown-item ${state.route === 'train-crew-lookup' ? 'active' : ''}" data-nav="train-crew-lookup">열차 승무원 조회</button>
                            <div class="cm-dropdown-divider"></div>
                            <button class="cm-dropdown-item" id="cm-switch-original" style="color:#1E6FB8;font-weight:700;">기존 Xcrew 화면 보기</button>
                        </div>
                    ` : ''}
                </div>
                <button class="cm-logout-pill" id="cm-logout-btn">로그아웃</button>
            </div>
        `;

        let contentHtml = '';
        if (state.route === 'schedule') contentHtml = renderScheduleContent();
        else if (state.route === 'today-work') contentHtml = renderTodayWorkContent();
        else if (state.route === 'train-crew-lookup') contentHtml = renderLookupContent();

        rootEl.innerHTML = topBarHtml + `<div class="cm-container">${contentHtml}</div>`;
        bindCommonEvents();
    }

    function renderLogin() {
        const l = state.login;
        if (l.isLoading && l.isAutoLogin) {
            rootEl.innerHTML = `
                <div class="cm-container" style="display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;">
                    ${getLogoSvg(80)}
                    <div style="font-size:32px;font-weight:800;color:#0F2C59;margin-top:14px;">CrewMate</div>
                    <div style="margin-top:18px;color:#64748B;">자동 로그인 중입니다...</div>
                </div>
            `;
            return;
        }
        rootEl.innerHTML = `
            <div class="cm-container" style="display:flex;flex-direction:column;justify-content:center;min-height:92vh;">
                <div style="text-align:center;margin-bottom:26px;display:flex;flex-direction:column;align-items:center;">
                    ${getLogoSvg(78)}
                    <div style="font-size:32px;font-weight:800;color:#0F2C59;margin-top:12px;">CrewMate</div>
                    <div style="margin-top:4px;font-size:14px;color:#64748B;">코레일 승무원 스마트 근무 파트너</div>
                </div>
                <div style="background:#FFFFFF;border-radius:20px;padding:20px;box-shadow:0 4px 16px rgba(15,44,89,0.06);border:1px solid #E2E8F0;">
                    <input class="cm-input" id="cm-epno" type="tel" maxlength="6" placeholder="사번 (6자리)" value="${l.employeeNumber}" ${l.isLoading ? 'disabled' : ''} />
                    <div style="height:12px;"></div>
                    <input class="cm-input" id="cm-pwd" type="password" placeholder="비밀번호" value="${l.password}" ${l.isLoading ? 'disabled' : ''} />
                    <label style="display:flex;align-items:center;gap:8px;margin:14px 0;font-size:14px;color:#334155;cursor:pointer;">
                        <input type="checkbox" id="cm-remember" ${l.rememberLogin ? 'checked' : ''} />
                        로그인 정보 안전하게 저장
                    </label>
                    ${l.errorMessage ? `<div style="color:#D32F2F;font-size:13px;font-weight:600;margin-bottom:10px;">${l.errorMessage}</div>` : ''}
                    <button class="cm-primary-btn" id="cm-login-submit" ${l.isLoading ? 'disabled' : ''}>
                        ${l.isLoading ? '로그인 중...' : '로그인'}
                    </button>
                </div>
                ${l.rememberLogin && !l.isLoading ? `
                    <button class="cm-text-btn" id="cm-delete-creds" style="margin-top:12px;width:100%;color:#64748B;font-size:13px;">저장된 로그인 정보 삭제</button>
                ` : ''}
                <button class="cm-text-btn" id="cm-login-original" style="margin-top:14px;width:100%;color:#64748B;font-size:13px;">기존 Xcrew 웹 화면으로 보기</button>
            </div>
        `;

        const epnoEl = document.getElementById('cm-epno');
        const pwdEl = document.getElementById('cm-pwd');
        const remEl = document.getElementById('cm-remember');
        if (epnoEl) epnoEl.oninput = e => { state.login.employeeNumber = e.target.value.slice(0, 6); };
        if (pwdEl) pwdEl.oninput = e => { state.login.password = e.target.value; };
        if (remEl) remEl.onchange = e => { state.login.rememberLogin = e.target.checked; render(); };

        const submitBtn = document.getElementById('cm-login-submit');
        if (submitBtn) {
            submitBtn.onclick = () => {
                if (!state.login.employeeNumber || !state.login.password) {
                    state.login.errorMessage = '사번과 비밀번호를 입력해 주세요.';
                    render();
                    return;
                }
                performLogin(state.login.employeeNumber, state.login.password, state.login.rememberLogin, false);
            };
        }
        const delBtn = document.getElementById('cm-delete-creds');
        if (delBtn) {
            delBtn.onclick = () => {
                clearCredentials();
                state.login.employeeNumber = '';
                state.login.password = '';
                state.login.rememberLogin = false;
                state.login.errorMessage = '저장된 로그인 정보가 삭제되었습니다.';
                render();
            };
        }
        const origBtn = document.getElementById('cm-login-original');
        if (origBtn) {
            origBtn.onclick = () => {
                state.mode = 'original';
                sessionStorage.setItem(SESSION_VIEW_MODE, 'original');
                render();
            };
        }
    }

        -${mm}-${dd}`;
        const mmdd = `${mm}-${dd}`;
        return specificHolidays[full] || solarHolidays[mmdd] || null;
    }

        function renderScheduleContent() {
        const ym = state.schedule.yearMonth;
        const year = Number(ym.slice(0, 4));
        const month = Number(ym.slice(4, 6));
        const todayIso = getTodayBasicIso();

        const firstDay = new Date(year, month - 1, 1).getDay();
        const daysInMonth = new Date(year, month, 0).getDate();

        const byDate = {};
        state.schedule.items.forEach(item => {
            if (item.workDate && item.workDate.startsWith(ym)) {
                if (!byDate[item.workDate]) byDate[item.workDate] = [];
                byDate[item.workDate].push(item);
            }
        });

        const cells = [];
        for (let i = 0; i < firstDay; i++) {
            cells.push(`<div class="cm-cal-cell" style="background:transparent; border:none;"></div>`);
        }
        for (let d = 1; d <= daysInMonth; d++) {
            const dayStr = String(d).padStart(2, '0');
            const dateIso = `${ym}${dayStr}`;
            const isToday = dateIso === todayIso;
            const daySchedules = byDate[dateIso] || [];
            const hasWork = daySchedules.length > 0;
            const dow = (firstDay + d - 1) % 7;
            const dayColor = isToday ? '#0F2C59' : (dow === 0 ? '#D32F2F' : (dow === 6 ? '#1E6FB8' : '#334155'));

            const isHolidayS = daySchedules.some(s => s.diaNo === 'S' || s.nonActiveType === 'S');
            const hasActualDuty = daySchedules.some(s => s.diaNo && s.diaNo !== 'S' && s.diaNo !== '연차');
            const showRedS = isHolidayS && hasActualDuty;

            const linesHtml = daySchedules.map(sch => {
                const rows = [];
                if (sch.diaNo === '연차') {
                    rows.push(`<div style="font-size:10px;font-weight:800;color:#0D9488;">연차</div>`);
                } else if (sch.diaNo === 'S') {
                    if (!showRedS) rows.push(`<div style="font-size:10px;font-weight:800;color:#0F2C59;">S</div>`);
                } else if (sch.diaNo) {
                    const label = sch.isContinuation ? `~${sch.diaNo}` : sch.diaNo;
                    rows.push(`<div style="font-size:10px;font-weight:800;color:#0F2C59;">${label}</div>`);
                } else if (sch.nonActiveType) {
                    if (!(sch.nonActiveType === 'S' && showRedS)) {
                        rows.push(`<div style="font-size:9.5px;font-weight:600;color:#64748B;">${sch.nonActiveType}</div>`);
                    }
                }

                const st = toDisplayTime(sch.startTime);
                const et = toDisplayTime(sch.endTime);
                if (st) rows.push(`<div style="font-size:9.5px;font-weight:700;color:#1E56A0;">${st}</div>`);
                if (et) rows.push(`<div style="font-size:9.5px;font-weight:700;color:#1E56A0;">${et}</div>`);

                if (sch.trainNo) {
                    rows.push(`<div style="font-size:9px;font-weight:500;color:#64748B;">${sch.trainNo}</div>`);
                }
                const cacheEntry = state.schedule.diaCache[`${dateIso}|${sch.diaNo}`];
                const trn2 = sch.trainNo2 || (cacheEntry && cacheEntry.data ? resolveSecondTrainNo(cacheEntry.data, sch.trainNo) : null);
                if (trn2) {
                    rows.push(`<div style="font-size:9px;font-weight:500;color:#64748B;">${trn2}</div>`);
                }

                return rows.join('');
            }).join('<div style="height:3px;"></div>');

            cells.push(`
                <div class="cm-cal-cell ${hasWork ? 'has-work' : ''} ${isToday ? 'is-today' : ''}" data-date="${hasWork ? dateIso : ''}">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:2px;">
                        <div style="display:flex; align-items:center;">
                            <span style="font-size:11px;font-weight:${isToday ? '800' : '700'};color:${dayColor};">${dayStr}</span>
                            ${showRedS ? `<span style="color:#D32F2F;font-size:10px;font-weight:900;margin-left:2px;">S</span>` : ''}
                        </div>
                        ${isToday ? `<span style="background:#0F2C59;color:#FFF;font-size:7px;font-weight:700;padding:1px 3px;border-radius:4px;">오늘</span>` : ''}
                    </div>
                    <div style="display:flex;flex-direction:column;flex:1;">
                        ${linesHtml}
                    </div>
                </div>
            `);
        }

        let sheetHtml = '';
        if (state.schedule.selectedDate) {
            const selIso = state.schedule.selectedDate;
            const selList = byDate[selIso] || [];
            const cardsHtml = selList.map(sch => {
                if (sch.diaNo === '연차') return `<div style="background:#FFF;border-radius:16px;padding:20px;font-size:20px;font-weight:800;color:#0D9488;">연차</div>`;
                if (isDiaCandidate(sch.diaNo)) {
                    const entry = state.schedule.diaCache[`${selIso}|${sch.diaNo}`];
                    if (entry && entry.status === 'success') {
                        return renderDiaDetailCardHtml(sch, entry.data, selIso);
                    }
                    return `<div style="padding:16px 0;color:#64748B;">다이아 ${sch.diaNo} 상세를 불러오는 중입니다...</div>`;
                }
                return `<div style="background:#FFF;border-radius:16px;padding:16px;font-weight:700;">다이아 ${sch.diaNo || sch.nonActiveType || ''}</div>`;
            }).join('');

            sheetHtml = `
                <div class="cm-sheet-backdrop" id="cm-sheet-backdrop">
                    <div class="cm-sheet" onclick="event.stopPropagation()">
                        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                            <span style="font-size:20px;font-weight:800;color:#0F2C59;">${basicIsoToDisplay(selIso)}</span>
                            <button class="cm-text-btn" id="cm-sheet-close">닫기</button>
                        </div>
                        ${cardsHtml}
                    </div>
                </div>
            `;
        }

        let pickerHtml = '';
        if (state.showMonthPicker) {
            pickerHtml = `
                <div class="cm-sheet-backdrop" id="cm-month-backdrop" style="z-index:3000;">
                    <div class="cm-sheet" style="max-height:none; padding:24px; text-align:center;" onclick="event.stopPropagation()">
                        <div style="font-size:20px; font-weight:800; color:#0F2C59; margin-bottom:20px;">연월 선택</div>
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
                            <button class="cm-text-btn" id="cm-mp-prev" style="font-size:20px;">◀</button>
                            <span id="cm-mp-year" style="font-size:22px; font-weight:800; color:#0F2C59;">${year}년</span>
                            <button class="cm-text-btn" id="cm-mp-next" style="font-size:20px;">▶</button>
                        </div>
                        <div style="display:grid; grid-template-columns:repeat(4, 1fr); gap:8px; margin-bottom:24px;">
                            ${[1,2,3,4,5,6,7,8,9,10,11,12].map(mon => `
                                <button class="cm-mp-month-btn" data-month="${mon}" style="background:${mon===month ? '#0F2C59' : '#F1F5F9'}; color:${mon===month ? '#FFF' : '#334155'}; border:none; border-radius:8px; padding:12px 0; font-size:15px; font-weight:700; cursor:pointer;">
                                    ${mon}월
                                </button>
                            `).join('')}
                        </div>
                        <div style="display:flex; gap:12px;">
                            <button class="cm-outline-btn" id="cm-mp-cancel" style="flex:1;">취소</button>
                            <button class="cm-primary-btn" id="cm-mp-confirm" style="flex:1;">확인</button>
                        </div>
                    </div>
                </div>
            `;
        }

        return `
            <div class="cm-month-bar">
                <button class="cm-text-btn" id="cm-prev-month">〈 이전</button>
                <div id="cm-month-picker-btn" style="cursor:pointer; display:flex; align-items:center; gap:6px; padding:6px 12px; background:#F8FAFC; border-radius:8px;">
                    <span style="font-size:20px;font-weight:800;color:#0F2C59;">${year}년 ${String(month).padStart(2, '0')}월</span>
                    <span style="font-size:12px;color:#0F2C59;">▼</span>
                </div>
                <button class="cm-text-btn" id="cm-next-month">다음 〉</button>
            </div>
            ${state.schedule.isLoading ? `<div style="text-align:center;padding:12px;color:#64748B;">근무표를 불러오는 중입니다...</div>` : ''}
            ${state.schedule.errorMessage ? `<div style="text-align:center;padding:12px;color:#D32F2F;">${state.schedule.errorMessage}</div>` : ''}
            <div class="cm-cal-weekdays">
                <div style="color:#D32F2F;">일</div><div style="color:#475569;">월</div><div style="color:#475569;">화</div><div style="color:#475569;">수</div><div style="color:#475569;">목</div><div style="color:#475569;">금</div><div style="color:#1E6FB8;">토</div>
            </div>
            <div class="cm-cal-grid">${cells.join('')}</div>
            ${sheetHtml}
            ${pickerHtml}
        `;
    }

            function renderTodayWorkContent() {
        const todayDisp = basicIsoToDisplay(getTodayBasicIso());
        const tw = state.todayWork;
        let body = '';
        
        let upcomingBadge = '';
        if (tw.result && !tw.result.isToday) {
            const mmdd = `${tw.result.workDate.slice(4,6)}.${tw.result.workDate.slice(6,8)}`;
            upcomingBadge = `<span style="background:#FEE2E2;color:#C81E1E;padding:4px 10px;border-radius:999px;font-size:13px;font-weight:700;margin-left:8px;">${mmdd} 근무</span>`;
        }

        if (tw.isLoading) {
            body = `<div style="text-align:center;padding:36px 0;color:#64748B;">근무와 다이아 상세를 확인하고 있습니다...</div>`;
        } else if (tw.errorMessage) {
            body = `<div style="color:#D32F2F;margin-bottom:10px;">${tw.errorMessage}</div><button class="cm-primary-btn" id="cm-tw-retry">다시 시도</button>`;
        } else if (!tw.result) {
            body = `<div style="background:#FFF;border-radius:16px;padding:20px;color:#64748B;">예정된 근무가 없습니다.</div>`;
        } else {
            body = tw.result.dias.map(item => renderDiaDetailCardHtml(item.schedule, item.detail, tw.result.workDate)).join('');
        }

        return `
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
                <div style="display:flex;align-items:center;">
                    <span style="font-size:22px;font-weight:800;color:#0F2C59;">오늘의 근무</span>
                    ${upcomingBadge}
                </div>
                <span style="background:#FFFFFF;padding:5px 12px;border-radius:999px;font-size:14px;font-weight:700;color:#334155;box-shadow:0 1px 3px rgba(0,0,0,0.06);">${todayDisp}</span>
            </div>
            ${body}
        `;
    }

    function renderLookupContent() {
        const lk = state.lookup;
        let resultHtml = '';

        if (lk.report) {
            const all = lk.report.results;
            const active = all.filter(r => !(r.segment.pjtHrDvNm && String(r.segment.pjtHrDvNm).includes('편승')));
            const deadhead = all.filter(r => r.segment.pjtHrDvNm && String(r.segment.pjtHrDvNm).includes('편승'));

            const renderCard = r => {
                const seg = r.segment;
                const trn = seg.trnNo ? String(seg.trnNo).trim() : lk.report.normalizedTrainNumber;
                const dpt = seg.dptStnNm ? String(seg.dptStnNm).trim() : '-';
                const arv = seg.arvStnNm ? String(seg.arvStnNm).trim() : '-';
                const crew = r.crewMembers.length > 0 ? r.crewMembers.join(' · ') : '승무원 정보 없음';
                const times = [toDisplayTime(seg.dptTm), toDisplayTime(seg.arvTm)].filter(Boolean).join(' → ');
                return `
                    <div class="cm-lookup-card">
                        <div style="font-size:16px;font-weight:800;color:#0F2C59;">${r.pdiaNo} ${trn} ${dpt} → ${arv} ${crew}</div>
                        ${times ? `<div style="font-size:14px;font-weight:600;color:#64748B;text-align:center;margin-top:5px;">${times}</div>` : ''}
                    </div>
                `;
            };

            resultHtml = `
                <div style="margin-top:18px;">
                    <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px;">
                        <span style="background:#0F2C59;color:#FFF;padding:5px 12px;border-radius:8px;font-size:15px;font-weight:800;">${lk.report.normalizedTrainNumber} 열차</span>
                        <span style="font-size:13px;font-weight:600;color:#64748B;">운행 ${active.length}건</span>
                    </div>
                    ${active.length === 0 && deadhead.length === 0
                        ? `<div style="background:#FFF;border-radius:14px;padding:18px;color:#64748B;">해당 열차의 승무 정보가 없습니다.</div>`
                        : active.map(renderCard).join('')}
                    ${deadhead.length > 0 ? `
                        <div style="margin-top:14px;">
                            <button class="cm-outline-btn" id="cm-toggle-deadhead">
                                ${lk.showDeadhead ? `편승승무원 확인 닫기 (${deadhead.length}건)` : `편승승무원 확인 (${deadhead.length}건)`}
                            </button>
                            ${lk.showDeadhead ? `<div style="margin-top:10px;">${deadhead.map(renderCard).join('')}</div>` : ''}
                        </div>
                    ` : ''}
                </div>
            `;
        }

        return `
            <div style="font-size:22px;font-weight:800;color:#0F2C59;margin-bottom:14px;">열차 승무원 조회</div>
            <div style="background:#FFFFFF;border:1px solid #E2E8F0;border-radius:16px;padding:14px;box-shadow:0 2px 5px rgba(15,44,89,0.05);">
                <div style="display:flex;gap:8px;margin-bottom:12px;">
                    <input class="cm-input" id="cm-lk-date" type="date" value="${lk.date}" style="flex:1;" />
                    <input class="cm-input" id="cm-lk-train" type="text" placeholder="열차번호 (예: 4501)" value="${lk.trainNumber}" style="flex:1;" />
                </div>
                <button class="cm-primary-btn" id="cm-lk-search" ${lk.isLoading ? 'disabled' : ''}>
                    ${lk.isLoading ? '조회 중...' : '승무원 조회'}
                </button>
            </div>
            ${lk.isLoading ? `<div style="text-align:center;padding:20px;color:#64748B;">날짜의 다이아와 상세 정보를 확인하고 있습니다...</div>` : ''}
            ${lk.errorMessage ? `<div style="color:#D32F2F;margin-top:12px;font-weight:600;">${lk.errorMessage}</div>` : ''}
            ${resultHtml}
        `;
    }

    function bindCommonEvents() {
        const menuBtn = document.getElementById('cm-menu-btn');
        if (menuBtn) {
            menuBtn.onclick = e => {
                e.stopPropagation();
                state.menuOpen = !state.menuOpen;
                render();
            };
        }
        document.querySelectorAll('[data-nav]').forEach(btn => {
            btn.onclick = () => {
                const target = btn.getAttribute('data-nav');
                state.menuOpen = false;
                state.route = target;
                if (target === 'schedule' && state.schedule.items.length === 0) {
                    loadScheduleMonth(state.schedule.yearMonth);
                } else if (target === 'today-work') {
                    loadTodayWork();
                } else {
                    render();
                }
            };
        });
        const switchOrig = document.getElementById('cm-switch-original');
        if (switchOrig) {
            switchOrig.onclick = () => {
                state.menuOpen = false;
                state.mode = 'original';
                sessionStorage.setItem(SESSION_VIEW_MODE, 'original');
                if (location.pathname === PAGE_LOGIN) {
                    location.href = PAGE_MAIN_XCREW;
                } else {
                    render();
                }
            };
        }
        const logoutBtn = document.getElementById('cm-logout-btn');
        if (logoutBtn) {
            logoutBtn.onclick = () => {
                sessionStorage.setItem(SESSION_SKIP_AUTOLOGIN, '1');
                const saved = parseSavedCredentials();
                state.login.employeeNumber = saved ? saved.epno : '';
                state.login.password = saved ? saved.pwd : '';
                state.login.rememberLogin = Boolean(saved);
                state.login.isLoading = false;
                state.login.isAutoLogin = false;
                state.login.errorMessage = null;
                state.route = 'login';
                render();
            };
        }

                const mpBtn = document.getElementById('cm-month-picker-btn');
        if (mpBtn) {
            mpBtn.onclick = () => {
                state.showMonthPicker = true;
                render();
            };
        }
        const mpBackdrop = document.getElementById('cm-month-backdrop');
        if (mpBackdrop) {
            let currentY = Number(state.schedule.yearMonth.slice(0, 4));
            let selectedM = Number(state.schedule.yearMonth.slice(4, 6));

            const updateUI = () => {
                document.getElementById('cm-mp-year').textContent = `${currentY}년`;
                document.querySelectorAll('.cm-mp-month-btn').forEach(btn => {
                    if (Number(btn.getAttribute('data-month')) === selectedM) {
                        btn.style.background = '#0F2C59'; btn.style.color = '#FFF';
                    } else {
                        btn.style.background = '#F1F5F9'; btn.style.color = '#334155';
                    }
                });
            };

            document.getElementById('cm-mp-prev').onclick = () => { currentY--; updateUI(); };
            document.getElementById('cm-mp-next').onclick = () => { currentY++; updateUI(); };
            document.querySelectorAll('.cm-mp-month-btn').forEach(btn => {
                btn.onclick = (e) => {
                    selectedM = Number(e.target.getAttribute('data-month'));
                    updateUI();
                };
            });
            document.getElementById('cm-mp-cancel').onclick = () => { state.showMonthPicker = false; render(); };
            document.getElementById('cm-mp-confirm').onclick = () => {
                state.showMonthPicker = false;
                const newYm = `${currentY}${String(selectedM).padStart(2, '0')}`;
                if (newYm !== state.schedule.yearMonth) loadScheduleMonth(newYm);
                else render();
            };
            mpBackdrop.onclick = () => { state.showMonthPicker = false; render(); };
        }
        const prevBtn = document.getElementById('cm-prev-month');
        if (prevBtn) {
            prevBtn.onclick = () => {
                const y = Number(state.schedule.yearMonth.slice(0, 4));
                const m = Number(state.schedule.yearMonth.slice(4, 6));
                const nextYm = m === 1 ? `${y - 1}12` : `${y}${String(m - 1).padStart(2, '0')}`;
                loadScheduleMonth(nextYm);
            };
        }
        const nextBtn = document.getElementById('cm-next-month');
        if (nextBtn) {
            nextBtn.onclick = () => {
                const y = Number(state.schedule.yearMonth.slice(0, 4));
                const m = Number(state.schedule.yearMonth.slice(4, 6));
                const nextYm = m === 12 ? `${y + 1}01` : `${y}${String(m + 1).padStart(2, '0')}`;
                loadScheduleMonth(nextYm);
            };
        }
        document.querySelectorAll('.cm-cal-cell[data-date]').forEach(cell => {
            const dt = cell.getAttribute('data-date');
            if (dt) {
                cell.onclick = async () => {
                    state.schedule.selectedDate = dt;
                    render();
                    const list = state.schedule.items.filter(i => i.workDate === dt && isDiaCandidate(i.diaNo));
                    for (const sch of list) {
                        const key = `${dt}|${sch.diaNo}`;
                        if (!state.schedule.diaCache[key]) {
                            state.schedule.diaCache[key] = { status: 'loading' };
                            try {
                                const detail = await fetchDiaDetailWithFallback(dt, sch.diaNo, sch.isContinuation);
                                state.schedule.diaCache[key] = { status: 'success', data: detail };
                                render();
                            } catch (_) {
                                state.schedule.diaCache[key] = { status: 'error' };
                                render();
                            }
                        }
                    }
                };
            }
        });
        const backdrop = document.getElementById('cm-sheet-backdrop');
        const closeSheet = document.getElementById('cm-sheet-close');
        if (backdrop) backdrop.onclick = () => { state.schedule.selectedDate = null; render(); };
        if (closeSheet) closeSheet.onclick = () => { state.schedule.selectedDate = null; render(); };

        const twRetry = document.getElementById('cm-tw-retry');
        if (twRetry) twRetry.onclick = () => loadTodayWork();

        const lkDate = document.getElementById('cm-lk-date');
        const lkTrain = document.getElementById('cm-lk-train');
        const lkSearch = document.getElementById('cm-lk-search');
        const toggleDeadhead = document.getElementById('cm-toggle-deadhead');
        if (lkDate) lkDate.onchange = e => { state.lookup.date = e.target.value; };
        if (lkTrain) {
            lkTrain.oninput = e => { state.lookup.trainNumber = e.target.value; };
            lkTrain.onkeydown = e => { if (e.key === 'Enter') searchTrainCrew(); };
        }
        if (lkSearch) lkSearch.onclick = () => searchTrainCrew();
        if (toggleDeadhead) {
            toggleDeadhead.onclick = () => {
                state.lookup.showDeadhead = !state.lookup.showDeadhead;
                render();
            };
        }
    }

    async function bootstrap() {
        const saved = parseSavedCredentials();
        if (saved) {
            state.login.employeeNumber = saved.epno;
            state.login.password = saved.pwd;
            state.login.rememberLogin = true;
        }

        if (sessionStorage.getItem(SESSION_SKIP_AUTOLOGIN) === '1') {
            state.login.isLoading = false;
            render();
            return;
        }

        try {
            const currentYm = getCurrentYearMonth();
            const json = await xhrPost(API_SCHEDULE, { pjtYm: currentYm });
            state.login.isLoading = false;
            state.route = 'schedule';
            state.schedule.yearMonth = currentYm;
            state.schedule.items = parseScheduleItems(json.data);
            render();
            prefetchMonthDiaDetails(currentYm, state.schedule.items);
            return;
        } catch (_) {}

        if (saved) {
            await performLogin(saved.epno, saved.pwd, true, true);
        } else {
            state.login.isLoading = false;
            render();
        }
    }

    bootstrap();
})();