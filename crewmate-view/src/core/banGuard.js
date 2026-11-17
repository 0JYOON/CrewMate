/**
 * CrewMate View - 비공개 사번 해시 기반 접근 가드 (Android EmployeeBanGuard.kt 1:1 호환)
 *
 * [보안 원칙]
 * - 평문 사번(epno)은 코드·스토리지·로그 어디에도 저장하거나 전송하지 않습니다.
 * - Salted SHA-256 단방향 해시만 계산하여 로컬 및 원격 해시 목록과 대조합니다.
 * - 중복 API 호출 방지를 위해 이미 수신한 epno를 재사용하고, 정상 사용자는 1일 1회만 원격 목록을 확인합니다.
 */

import { XcrewApi } from './xcrewApi.js';
import { getTodayYm } from './formatters.js';

const GUARD_SALT = 'crewmate_salt_2026_xcrew_guard';
const REMOTE_HASH_LIST_URL = 'https://raw.githubusercontent.com/0JYOON/CrewMate/main/config/blocked.json';
const STORAGE_STATE_KEY = 'CREWMATE_VIEW_STATE_FLAGS';

// 로컬 내장 차단 해시 목록 (Salted SHA-256 64자리 소문자 hex)
const LOCAL_BANNED_HASHES = new Set([]);

export async function hashEmployeeNumber(employeeNumber) {
  const normalized = String(employeeNumber == null ? '' : employeeNumber).trim();
  if (!normalized) return '';
  const data = new TextEncoder().encode(GUARD_SALT + ':' + normalized);
  const subtle =
    (typeof crypto !== 'undefined' && crypto.subtle) ||
    (typeof globalThis !== 'undefined' && globalThis.crypto && globalThis.crypto.subtle);
  if (!subtle) return '';
  const hashBuf = await subtle.digest('SHA-256', data);
  const bytes = new Uint8Array(hashBuf);
  let hex = '';
  for (let i = 0; i < bytes.length; i++) {
    const h = bytes[i].toString(16);
    hex += h.length === 1 ? '0' + h : h;
  }
  return hex;
}

export function parseRemoteHashes(jsonText) {
  const result = new Set();
  if (!jsonText || typeof jsonText !== 'string') return result;
  try {
    const obj = JSON.parse(jsonText);
    const arr = obj && Array.isArray(obj.hashes) ? obj.hashes : [];
    for (let i = 0; i < arr.length; i++) {
      const item = String(arr[i] == null ? '' : arr[i]).trim().toLowerCase();
      if (item.length === 64 && /^[0-9a-f]{64}$/.test(item)) {
        result.add(item);
      }
    }
  } catch (e) {}
  return result;
}

function readCachedState() {
  try {
    const raw = localStorage.getItem(STORAGE_STATE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

function writeCachedState(isRestricted) {
  try {
    localStorage.setItem(
      STORAGE_STATE_KEY,
      JSON.stringify({
        r: !!isRestricted,
        d: new Date().toISOString().slice(0, 10),
      })
    );
  } catch (e) {}
}

export function isCachedRestricted() {
  const st = readCachedState();
  return !!(st && st.r === true);
}

function isCheckedTodayAsAllowed() {
  const st = readCachedState();
  const todayStr = new Date().toISOString().slice(0, 10);
  return !!(st && st.r === false && st.d === todayStr);
}

async function fetchRemoteHashesOrNull() {
  try {
    const res = await fetch(REMOTE_HASH_LIST_URL + '?t=' + Date.now(), {
      method: 'GET',
      cache: 'no-store',
    });
    if (res.status === 404) return new Set();
    if (!res.ok) return null;
    const text = await res.text();
    return parseRemoteHashes(text);
  } catch (e) {
    return null;
  }
}

async function resolveCurrentEpno() {
  try {
    const res = await XcrewApi.fetchRltmSchedule(getTodayYm());
    const list = res && Array.isArray(res.data) ? res.data : [];
    for (let i = 0; i < list.length; i++) {
      const epno = list[i] && list[i].epno ? String(list[i].epno).trim() : '';
      if (epno) return epno;
    }
  } catch (e) {}
  return '';
}

/**
 * 현재 로그인한 사용자의 사번을 Salted SHA-256으로 검증합니다.
 * @param {string} [knownEpno] 이미 조회된 사번이 있으면 중복 API 호출을 생략
 * @returns {Promise<boolean>} true이면 사용 제한(차단) 대상
 */
export async function verifyUserAccess(knownEpno) {
  const epno = knownEpno ? String(knownEpno).trim() : await resolveCurrentEpno();
  if (!epno) {
    return isCachedRestricted();
  }

  const hash = await hashEmployeeNumber(epno);
  if (!hash) return false;

  if (LOCAL_BANNED_HASHES.has(hash)) {
    writeCachedState(true);
    return true;
  }

  // 당일 이미 정상 사용자로 검증된 경우 불필요한 원격 fetch 생략
  if (isCheckedTodayAsAllowed()) {
    return false;
  }

  const remoteHashes = await fetchRemoteHashesOrNull();
  if (remoteHashes !== null) {
    const restricted = remoteHashes.has(hash);
    writeCachedState(restricted);
    return restricted;
  }

  return isCachedRestricted();
}
