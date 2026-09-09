import { keccak256, concat, toUtf8Bytes } from 'ethers';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * 커밋 봉인 크로스 검증 픽스처 생성기 (ANT-PRED-02).
 *
 * ethers(널리 검증된 keccak 구현)가 기준값을 만들고, 다른 구현이 이 값을 재현해야 한다:
 *   - Java     backend/src/test/.../CommitHashesTest         (web3j Hash.sha3 + CommitPayload)
 *   - 브라우저  D-03 ①단계 · C-01 미리보기 (ANT-FE-VERIFY)     — 이 파일을 그대로 가져다 맞추면 된다
 *
 * 같은 구현으로 두 번 계산하면 검증이 아니라서, Java 가 아니라 여기서 먼저 만든다.
 *
 * ── 규격 (plan.md §3 · CommitPayload.java · CommitHashes.java 와 동일해야 한다) ──
 *   noteHash   = keccak256( utf8(note) ‖ utf8(noteSaltHex) )   ← 본문 그대로, 정규화 없음, 구분자 없음
 *   commitStr  = "antenna:commit:v1\n"
 *              + "stockCode=…\ndirection=…\ntargetPrice=…\nhorizon=…\nnoteHash=…"   ← 6줄, 끝 개행 없음
 *   commitHash = keccak256( utf8(commitStr) )
 *   salt 줄은 없다 — noteHash 가 noteSalt 의 난수성을 물려받아 그 역할을 겸한다 (09-09 결정)
 *   targetPrice 는 항상 소수 둘째 자리 ("82000.00")
 *
 * 실행: node scripts/gen-commit-fixture.mjs
 * 산출: test/fixtures/commit-cross-fixture.json
 *       ../backend/src/test/resources/commit/commit-cross-fixture.json (동일 내용 복사)
 */

const HEADER = 'antenna:commit:v1';

const noteHashOf = (note, noteSalt) => keccak256(concat([toUtf8Bytes(note), toUtf8Bytes(noteSalt)]));

function commitStringOf({ stockCode, direction, targetPrice, horizon }, noteHash) {
  return [
    HEADER,
    `stockCode=${stockCode}`,
    `direction=${direction}`,
    `targetPrice=${targetPrice}`, // 이미 "82000.00" 꼴 문자열로 준다 — JS 숫자 포맷에 기대지 않는다
    `horizon=${horizon}`,
    `noteHash=${noteHash}`,
  ].join('\n');
}

// 결정적 noteSalt: 누구나 재생성 가능. 실제 서비스에서는 crypto.getRandomValues 로 만든다.
const saltOf = (label) => keccak256(toUtf8Bytes(`antenna-fixture-note-salt-${label}`)).slice(2);

const CASES = [
  {
    name: '기본 — 한 줄 근거',
    fields: { stockCode: '005930', direction: 'UP', targetPrice: '82000.00', horizon: 30 },
    note: '3분기 실적 컨센서스 상회 전망',
    noteSalt: saltOf('basic'),
  },
  {
    name: '빈 근거 — noteHash 는 keccak(noteSalt) 라 여전히 난수',
    fields: { stockCode: '000660', direction: 'DOWN', targetPrice: '150000.00', horizon: 7 },
    note: '',
    noteSalt: saltOf('empty'),
  },
  {
    name: '줄바꿈 보존 — \\r\\n 과 끝 공백을 정규화하지 않는다',
    fields: { stockCode: '035420', direction: 'UP', targetPrice: '210500.50', horizon: 90 },
    note: '첫 줄\r\n둘째 줄  \n\n셋째 줄 ',
    noteSalt: saltOf('crlf'),
  },
  {
    name: '긴 근거 + 이모지 — UTF-8 다바이트',
    fields: { stockCode: '051910', direction: 'DOWN', targetPrice: '390000.00', horizon: 14 },
    note: '유가 하락 🛢️ 로 마진 압박. '.repeat(40),
    noteSalt: saltOf('long'),
  },
  {
    name: '같은 예측·같은 근거, noteSalt 만 다름 — 해시가 전부 달라야 한다',
    fields: { stockCode: '005930', direction: 'UP', targetPrice: '82000.00', horizon: 30 },
    note: '3분기 실적 컨센서스 상회 전망',
    noteSalt: saltOf('basic-2'),
  },
];

const cases = CASES.map((c) => {
  const noteHash = noteHashOf(c.note, c.noteSalt);
  const commitString = commitStringOf(c.fields, noteHash);
  const commitHash = keccak256(toUtf8Bytes(commitString));
  return { ...c, noteHash, commitString, commitHash };
});

// 자체 무결성: 1번과 5번은 noteSalt 만 다른데 noteHash·commitHash 가 전부 달라야 한다.
if (cases[0].noteHash === cases[4].noteHash || cases[0].commitHash === cases[4].commitHash) {
  throw new Error('self-check 실패: noteSalt 가 다른데 해시가 같다');
}

const fixture = {
  spec: {
    hash: 'keccak256',
    noteHash: 'keccak256(utf8(note) ‖ utf8(noteSaltHex)) — 본문 정규화 없음, 구분자 없음',
    commitString: 'antenna:commit:v1 + 5줄(stockCode·direction·targetPrice·horizon·noteHash), \\n 구분, 끝 개행 없음',
    commitHash: 'keccak256(utf8(commitString))',
    salt: '커밋 salt 줄 없음 — noteHash 가 겸한다. noteSalt 는 클라이언트 32바이트 CSPRNG, 소문자 64 hex, 0x 없음',
    targetPrice: '항상 소수 둘째 자리',
  },
  generator: 'contracts/scripts/gen-commit-fixture.mjs (ethers v6 기준값)',
  cases,
};

const here = path.dirname(fileURLToPath(import.meta.url));
const json = JSON.stringify(fixture, null, 2) + '\n';
const out1 = path.resolve(here, '..', 'test', 'fixtures', 'commit-cross-fixture.json');
const out2 = path.resolve(
  here, '..', '..', 'backend', 'src', 'test', 'resources', 'commit', 'commit-cross-fixture.json',
);
for (const out of [out1, out2]) {
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, json, 'utf8');
  console.log('wrote', path.relative(process.cwd(), out));
}
