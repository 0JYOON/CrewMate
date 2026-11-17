/**
 * CrewMate View 빌드 & 난독화 스크립트
 * - Terser 압축 + javascript-obfuscator 난독화 적용
 * - 산출물:
 *   1) dist/crewmate-view.bundle.js      : 원격 호스팅용 난독화 본체
 *   2) dist/crewmate-view-loader.user.js : 기기 배포용 10줄 초경량 로더 (소스 비공개용)
 *   3) dist/crewmate-view.user.js        : 오프라인 단독 실행형 난독화 Userscript
 * 실행: node crewmate-view/build-view.js
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { minify } = require('terser');
const JavaScriptObfuscator = require('javascript-obfuscator');

const ROOT = __dirname;
const SRC = path.join(ROOT, 'src');
const DIST = path.join(ROOT, 'dist');

const REMOTE_BUNDLE_URL = 'https://crewmate-alpha.pages.dev/crewmate-view.bundle.js';

const MODULE_ORDER = [
  'core/logoData.js',
  'core/xcrewApi.js',
  'core/formatters.js',
  'core/capture.js',
  'core/settingsStore.js',
  'core/domExtractor.js',
  'core/banGuard.js',
  'ui/styles.js',
  'ui/views/routeDiagram.js',
  'ui/views/monthlyScheduleView.js',
  'ui/views/diaDetailView.js',
  'ui/views/crewStatusView.js',
  'ui/views/workSpecView.js',
  'ui/views/nonOpView.js',
  'ui/views/attendanceView.js',
  'ui/views/settingsView.js',
  'ui/shell.js',
];

const OBFUSCATOR_OPTIONS = {
  compact: true,
  controlFlowFlattening: false,
  deadCodeInjection: false,
  debugProtection: false,
  disableConsoleOutput: false,
  identifierNamesGenerator: 'hexadecimal',
  log: false,
  numbersToExpressions: true,
  renameGlobals: false,
  selfDefending: false,
  simplify: true,
  splitStrings: true,
  splitStringsChunkLength: 8,
  stringArray: true,
  stringArrayCallsTransform: true,
  stringArrayEncoding: ['base64'],
  stringArrayIndexShift: true,
  stringArrayRotate: true,
  stringArrayShuffle: true,
  stringArrayWrappersCount: 1,
  stringArrayWrappersChainedCalls: true,
  stringArrayThreshold: 0.8,
  transformObjectKeys: false,
  unicodeEscapeSequence: false,
};

function stripImportsAndExports(code) {
  return code
    .replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
    .replace(/export\s+const\s+/g, 'const ')
    .replace(/export\s+async\s+function\s+/g, 'async function ')
    .replace(/export\s+function\s+/g, 'function ')
    .trim();
}

async function obfuscateJs(source) {
  const terserResult = await minify(source, {
    compress: {
      drop_debugger: true,
      passes: 2,
    },
    mangle: true,
    format: {
      comments: false,
    },
  });
  if (!terserResult || !terserResult.code) {
    throw new Error('[build-view.js] Terser minification failed');
  }
  const obfuscated = JavaScriptObfuscator.obfuscate(terserResult.code, OBFUSCATOR_OPTIONS);
  return obfuscated.getObfuscatedCode();
}

async function buildBundle() {
  if (!fs.existsSync(DIST)) {
    fs.mkdirSync(DIST, { recursive: true });
  }

  const chunks = [];
  for (const rel of MODULE_ORDER) {
    const fullPath = path.join(SRC, rel);
    if (!fs.existsSync(fullPath)) {
      throw new Error('Missing source module: ' + fullPath);
    }
    const raw = fs.readFileSync(fullPath, 'utf8');
    chunks.push(`/* === Module: ${rel} === */\n` + stripImportsAndExports(raw));
  }

  const bootstrap = `
/* === Bootstrap === */
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', mountCrewMateView);
} else {
  mountCrewMateView();
}
`;

  const rawIife = `(function () {\n'use strict';\n\n${chunks.join('\n\n')}\n${bootstrap}\n})();\n`;
  const obfuscatedIife = await obfuscateJs(rawIife);

  const userscriptHeader = `// ==UserScript==
// @name         CrewMate View
// @namespace    https://github.com/0JYOON/CrewMate
// @version      0.2.0
// @description  xcrew.korail.com 로그인 후 7대 전체 메뉴를 모바일(iOS Safari 1순위)·PC 고가독성 화면으로 재표출합니다.
// @author       studio.yj.lab (0JYOON)
// @match        https://xcrew.korail.com/*
// @exclude      https://xcrew.korail.com/
// @exclude      https://xcrew.korail.com/loginView.do*
// @run-at       document-end
// @grant        none
// ==/UserScript==

`;

  const thinLoaderBody = `(function(){var s=document.createElement('script');s.src='${REMOTE_BUNDLE_URL}?t='+Date.now();document.body.appendChild(s);})();\n`;

  const standaloneUserscript = userscriptHeader + obfuscatedIife + '\n';
  const loaderUserscript = userscriptHeader + thinLoaderBody;

  // V8 문법 무결성 검증
  new vm.Script(obfuscatedIife, { filename: 'crewmate-view.bundle.js' });
  new vm.Script(standaloneUserscript, { filename: 'crewmate-view.user.js' });
  new vm.Script(loaderUserscript, { filename: 'crewmate-view-loader.user.js' });

  const bundleJsPath = path.join(DIST, 'crewmate-view.bundle.js');
  const loaderJsPath = path.join(DIST, 'crewmate-view-loader.user.js');
  const userJsPath = path.join(DIST, 'crewmate-view.user.js');

  fs.writeFileSync(bundleJsPath, obfuscatedIife, 'utf8');
  fs.writeFileSync(loaderJsPath, loaderUserscript, 'utf8');
  fs.writeFileSync(userJsPath, standaloneUserscript, 'utf8');

  console.log('[CrewMate View Build & Obfuscation Success]');
  console.log(' - Thin Loader (배포 권장):', loaderJsPath, '(' + Buffer.byteLength(loaderUserscript, 'utf8') + ' bytes)');
  console.log(' - Obfuscated Bundle (서버 호스팅용):', bundleJsPath, '(' + Buffer.byteLength(obfuscatedIife, 'utf8') + ' bytes)');
  console.log(' - Standalone Userscript (오프라인용):', userJsPath, '(' + Buffer.byteLength(standaloneUserscript, 'utf8') + ' bytes)');

  // CrewMate-Alpha 호스팅 폴더(Cloudflare Pages) 자동 동기화
  const alphaRoot = 'C:\\Users\\joon1\\Documents\\CrewMate-Alpha';
  if (fs.existsSync(alphaRoot)) {
    const guideHtmlPath = path.join(ROOT, 'guide', 'view-guide.html');
    const viewLogoPath = path.join(ROOT, 'assets', 'logo.jpg');
    const crewmateLogoPath = path.join(ROOT, 'assets', 'crewmate-logo.jpg');
    const syncFiles = [
      { src: bundleJsPath, name: 'crewmate-view.bundle.js' },
      { src: loaderJsPath, name: 'crewmate-view-loader.user.js' },
    ];
    if (fs.existsSync(viewLogoPath)) {
      syncFiles.push({ src: viewLogoPath, name: 'view-logo.jpg' });
    }
    if (fs.existsSync(crewmateLogoPath)) {
      syncFiles.push({ src: crewmateLogoPath, name: 'crewmate-logo.jpg' });
    }
    if (fs.existsSync(guideHtmlPath)) {
      syncFiles.push({ src: guideHtmlPath, name: 'view-guide.html' });
      syncFiles.push({ src: guideHtmlPath, name: 'guide.html' });
    }
    const alphaDist = path.join(alphaRoot, 'dist');
    for (const item of syncFiles) {
      fs.copyFileSync(item.src, path.join(alphaRoot, item.name));
      if (fs.existsSync(alphaDist)) {
        fs.copyFileSync(item.src, path.join(alphaDist, item.name));
      }
    }
    console.log(' - Synced to CrewMate-Alpha (root & dist/):', syncFiles.map((f) => f.name).join(', '));
  }
}

buildBundle().catch((err) => {
  console.error(err);
  process.exit(1);
});
