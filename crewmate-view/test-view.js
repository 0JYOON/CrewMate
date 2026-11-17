/**
 * CrewMate View 단위 검증 스크립트
 * 실행: node crewmate-view/test-view.js
 */

const assert = require('assert');
const nodeCrypto = require('crypto');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

function loadModuleExports(relPath) {
  const fullPath = path.join(__dirname, 'src', relPath);
  const exportedNames = [];
  const raw = fs.readFileSync(fullPath, 'utf8')
    .replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
    .replace(/export\s+const\s+([a-zA-Z0-9_]+)/g, (_, name) => {
      exportedNames.push(name);
      return 'const ' + name;
    })
    .replace(/export\s+async\s+function\s+([a-zA-Z0-9_]+)/g, (_, name) => {
      exportedNames.push(name);
      return 'async function ' + name;
    })
    .replace(/export\s+function\s+([a-zA-Z0-9_]+)/g, (_, name) => {
      exportedNames.push(name);
      return 'function ' + name;
    });
  const footer = '\n' + exportedNames.map((n) => `exports.${n} = ${n};`).join('\n');
  const sandbox = {
    exports: {},
    console,
    Date,
    Math,
    Object,
    Array,
    String,
    Number,
    Set,
    Uint8Array,
    TextEncoder,
    JSON,
    RegExp,
    encodeURIComponent,
    isNaN,
    parseInt,
    crypto: globalThis.crypto,
    globalThis,
  };
  vm.runInNewContext(raw + footer, sandbox, { filename: relPath });
  return sandbox.exports;
}

async function runTests() {
  const fmt = loadModuleExports('core/formatters.js');

  // 1. XcrewRawFormatter 검증
  assert.strictEqual(fmt.XcrewRawFormatter.formatHHMM('0953'), '09:53');
  assert.strictEqual(fmt.XcrewRawFormatter.formatHHMM('1851'), '18:51');
  assert.strictEqual(fmt.XcrewRawFormatter.formatHHMM(''), '');

  assert.strictEqual(fmt.XcrewRawFormatter.formatYMD('20261116'), '2026-11-16');
  assert.strictEqual(fmt.XcrewRawFormatter.formatMMDD_HHMM('202611160953'), '11-16 09:53');

  // 2. fnFrmtrDiaNo (renderDiaNoHtml) 검증
  const diaS = fmt.XcrewRawFormatter.renderDiaNoHtml('S', { pdiaDvCd: '1', alcSpecDvCd: '02' }, 'IND_SCHEDULE');
  assert(diaS.includes('cm-dia-red'), 'S 다이아는 빨간색 클래스가 적용되어야 함');

  const diaHoliday = fmt.XcrewRawFormatter.renderDiaNoHtml('16035', { alcSpecDvCd: '11', hldyYn: 'Y', pjtChgPrsFlg: 'Y' }, 'IND_SCHEDULE');
  assert(diaHoliday.includes('>휴<') || diaHoliday.includes('휴 '), '휴일 근무 시 휴 접두어가 붙어야 함');
  assert(diaHoliday.includes('cm-dia-underline'), '사업변경 시 밑줄 클래스가 적용되어야 함');

  const diaSpecHoliday = fmt.XcrewRawFormatter.renderDiaNoHtml('16315', { alcSpecDvCd: '21', offdDdFlg: 'Y', pdiaDvCd: '3' }, 'WORK_SPEC');
  assert(diaSpecHoliday.includes('>휴<') || diaSpecHoliday.includes('휴 '), '명세서 휴일 근무 시 휴 접두어가 붙어야 함');
  assert(diaSpecHoliday.includes('cm-dia-blue'), 'pdiaDvCd === 3 파란색 클래스 적용');
  assert(diaSpecHoliday.includes('cm-dia-bold'), 'alcSpecDvCd === 21 굵게 클래스 적용');

  // 3. fmtGtwcGubun (resolveAttendanceGubun) 검증
  assert.strictEqual(fmt.XcrewRawFormatter.resolveAttendanceGubun('1', {}).label, '출무전');
  assert.strictEqual(fmt.XcrewRawFormatter.resolveAttendanceGubun('2', { gtwcTpDvCd: '2' }, '32').canDoGtwc, true);
  assert.strictEqual(fmt.XcrewRawFormatter.resolveAttendanceGubun('2', { gtwcTpDvCd: '1' }, '32').label, '출무임박');
  assert.strictEqual(fmt.XcrewRawFormatter.resolveAttendanceGubun('3', {}).label, '도중출무완료');
  assert.strictEqual(fmt.XcrewRawFormatter.resolveAttendanceGubun('4', { gtwcTpDvCd: '2', flg: 'Y' }, '32').canDoGtwc, true);
  assert.strictEqual(fmt.XcrewRawFormatter.resolveAttendanceGubun('4', { gtwcTpDvCd: '1', flg: 'N' }, '32').label, '미출무');
  assert.strictEqual(fmt.XcrewRawFormatter.resolveAttendanceGubun('5', {}).label, '적합성미완료');
  assert.strictEqual(fmt.XcrewRawFormatter.resolveAttendanceGubun('6', {}).label, '적합성완료');
  assert.strictEqual(fmt.XcrewRawFormatter.resolveAttendanceGubun('7', {}).label, '지각');

  // 4. CrewMateDomainAdapter 검증 (alcSpecDvCd 5대 규칙 + rail.blue K 접두어 유지)
  assert.strictEqual(fmt.buildRailBlueUrl('20261117', 'K4501'), 'https://rail.blue/railroad/logis/Default.aspx?date=20261117&train=K4501#!');
  assert.strictEqual(fmt.CrewMateDomainAdapter.determineShiftType({ pdiaNo: 'S', alcSpecDvCd: '02' }), 'REST');
  assert.strictEqual(fmt.CrewMateDomainAdapter.determineShiftType({ pdiaNo: 'X', alcSpecDvCd: '01' }), 'REST');
  assert.strictEqual(fmt.CrewMateDomainAdapter.determineShiftType({ pdiaNo: '연차', alcSpecDvCd: '21' }), 'EDU_LEAVE');
  assert.strictEqual(
    fmt.CrewMateDomainAdapter.determineShiftType({ pdiaNo: '16315', alcSpecDvCd: '11' }, { pdiaNo: '~(16315)', alcSpecDvCd: '12' }),
    'NIGHT_DAY1'
  );
  assert.strictEqual(
    fmt.CrewMateDomainAdapter.determineShiftType({ pdiaNo: '16035', alcSpecDvCd: '11' }, { pdiaNo: '16204', alcSpecDvCd: '11' }),
    'DAY'
  );
  assert.strictEqual(fmt.CrewMateDomainAdapter.determineShiftType({ pdiaNo: '~(16315)', alcSpecDvCd: '12' }), 'NIGHT_DAY2');

  assert.strictEqual(fmt.CrewMateDomainAdapter.classifySegmentPriority('운전'), 1);
  assert.strictEqual(fmt.CrewMateDomainAdapter.classifySegmentPriority('출고준비'), 1);
  assert.strictEqual(fmt.CrewMateDomainAdapter.classifySegmentPriority('입환'), 1);
  assert.strictEqual(fmt.CrewMateDomainAdapter.classifySegmentPriority('감시'), 1);
  assert.strictEqual(fmt.CrewMateDomainAdapter.classifySegmentPriority('준비'), 2);
  assert.strictEqual(fmt.CrewMateDomainAdapter.classifySegmentPriority('편승'), 2);
  assert.strictEqual(fmt.CrewMateDomainAdapter.classifySegmentPriority('휴게'), 3);
  assert.strictEqual(fmt.CrewMateDomainAdapter.DIA_RUN_DAYS_SPEC.length, 15);

  // 5. banGuard Salted SHA-256 (Android EmployeeBanGuard.kt 1:1 교차 검증)
  const banGuard = loadModuleExports('core/banGuard.js');
  const expectedHash = nodeCrypto
    .createHash('sha256')
    .update('crewmate_salt_2026_xcrew_guard:123456', 'utf8')
    .digest('hex');
  const actualHash = await banGuard.hashEmployeeNumber(' 123456 ');
  assert.strictEqual(actualHash, expectedHash, 'Android EmployeeBanGuard와 SHA-256 해시가 100% 일치해야 함');

  const parsed = banGuard.parseRemoteHashes(JSON.stringify({ hashes: [expectedHash.toUpperCase(), 'invalid', 'abc'] }));
  assert.strictEqual(parsed.size, 1);
  assert.strictEqual(parsed.has(expectedHash), true);

  // 6. 종합 안내서(view-guide.html) 및 shell.js 보안·면책 고지 검증
  const guideHtml = fs.readFileSync(path.join(__dirname, 'guide', 'view-guide.html'), 'utf8');
  assert(guideHtml.includes('id="eco-panel-app"'), '앱 안내 패널 포함 필수');
  assert(guideHtml.includes('id="eco-panel-web"'), '웹 안내 패널 포함 필수');
  assert(guideHtml.includes('id="eco-panel-view"'), '뷰 안내 패널 포함 필수');
  assert(guideHtml.includes('id="security-disclaimer"'), '보안 및 면책 고지 섹션 포함 필수');

  const shellJs = fs.readFileSync(path.join(__dirname, 'src', 'ui', 'shell.js'), 'utf8');
  assert(shellJs.includes('__cm_notice_confirm'), 'View 최초 이용안내 모달 확인 버튼 포함 필수');
  assert(shellJs.includes('개인정보 보안 한계 및 면책 고지'), 'View 보안 면책 고지 모달 포함 필수');

  console.log('✅ All CrewMate View unit tests (including banGuard SHA-256 parity) passed!');
}

runTests().catch((err) => {
  console.error(err);
  process.exit(1);
});
