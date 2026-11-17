/**
 * CrewMate View - 사용자 환경설정 저장소 및 웹폰트/테마 매니저
 */

export const CREWMATE_VIEW_VERSION = 'v0.3.1';

const SETTINGS_STORAGE_KEY = 'CREWMATE_VIEW_SETTINGS_V2';
const NOTICE_SEEN_KEY = 'CREWMATE_VIEW_SEEN_NOTICE_V1';

const DEFAULT_SETTINGS = {
  rowPadding: 'COMPACT', // 'COMPACT' (작게-기본) | 'NORMAL' (보통) | 'RELAXED' (크게)
  fontScale: 'NORMAL',   // 'SMALL' (작게) | 'NORMAL' (보통-기본) | 'LARGE' (크게)
  themeMode: 'SYSTEM',   // 'SYSTEM' | 'LIGHT' | 'DARK'
  labRouteDiagram: false,
};

let currentSettings = null;
const listeners = [];

export function loadSettings() {
  if (currentSettings) return currentSettings;
  try {
    const raw = localStorage.getItem(SETTINGS_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    currentSettings = Object.assign({}, DEFAULT_SETTINGS, parsed);
  } catch (e) {
    currentSettings = Object.assign({}, DEFAULT_SETTINGS);
  }
  return currentSettings;
}

export function updateSettings(patch) {
  const prev = loadSettings();
  currentSettings = Object.assign({}, prev, patch);
  try {
    localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(currentSettings));
  } catch (e) {}
  listeners.forEach((fn) => {
    try {
      fn(currentSettings);
    } catch (e) {}
  });
  return currentSettings;
}

export function onSettingsChange(fn) {
  listeners.push(fn);
}

export function hasSeenInitialNotice() {
  try {
    return localStorage.getItem(NOTICE_SEEN_KEY) === '1';
  } catch (e) {
    return false;
  }
}

export function markInitialNoticeSeen() {
  try {
    localStorage.setItem(NOTICE_SEEN_KEY, '1');
  } catch (e) {}
}

export function isEffectiveDarkMode(settings) {
  const s = settings || loadSettings();
  if (s.themeMode === 'DARK') return true;
  if (s.themeMode === 'LIGHT') return false;
  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
  return false;
}

export function resolveAppearanceCssVars(settings) {
  const s = settings || loadSettings();
  const pyMap = {
    COMPACT: '4px',
    NORMAL: '7px',
    RELAXED: '10px',
  };
  const scaleMap = {
    SMALL: '0.92',
    NORMAL: '1',
    LARGE: '1.09',
  };
  const py = pyMap[s.rowPadding] || '4px';
  const scale = scaleMap[s.fontScale] || '1';

  return `--cm-cell-py:${py};--cm-font-scale:${scale};`;
}
