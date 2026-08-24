/**
 * 엔진 스모크 테스트 — 프로토타입 시연 시나리오를 그대로 훑는다.
 * 실행: npm run smoke
 */
import assert from 'node:assert/strict';
import { Engine, SLOT_FEE, SLOT_FREE } from './engine.ts';

const checks: string[] = [];
function ok(name: string, fn: () => void) {
  fn();
  checks.push(name);
}

const e = new Engine();

ok('시드: 예측·원장·리포트가 채워진다', () => {
  assert(e.preds.length > 50, `preds=${e.preds.length}`);
  assert(e.chain.length > 50, `chain=${e.chain.length}`);
  assert(e.REPORTS.length > 0);
  assert(e.audit().every((r) => r.valid && r.rootOk), '시드 직후 원장은 무결해야 한다');
});

ok('예측 등록: 커밋 해시 생성 + 앵커 대기', () => {
  const before = e.preds.length;
  const pendingBefore = e.pending.length;
  e.setState({ formCode: '005930', dir: 'UP', formTarget: String(e.ST('005930').close + 5000), formNote: '테스트 근거' });
  e.submitPred();
  assert.equal(e.preds.length, before + 1);
  const p = e.preds[e.preds.length - 1];
  assert.equal(p.status, 'BASE');
  assert(p.commit && p.commit.length === 64, '커밋 해시 64자');
  assert(e.revealOk(p), '리빌 검증 통과');
  assert(e.pending.length > pendingBefore, '앵커 대기열에 올라간다');
});

ok('검증 실패 케이스: 방향과 어긋난 목표가는 거부', () => {
  const before = e.preds.length;
  e.setState({ formCode: '005930', dir: 'UP', formTarget: '1', formNote: '근거' });
  e.submitPred();
  assert.equal(e.preds.length, before, '목표가가 종가보다 낮은 상승 예측은 등록되지 않는다');
});

ok('배치: 기준가 확정 → 만기 검증 → 머클 앵커', () => {
  const p = e.preds.find((x) => x.uid === 'me' && x.status === 'BASE')!;
  const anchorsBefore = e.chain.filter((b) => b.type === 'ANCHOR').length;
  e.runBatch(1);
  assert.equal(p.status, 'OPEN', '다음 영업일 종가로 기준가 확정');
  assert(p.entry !== null);
  assert(e.chain.filter((b) => b.type === 'ANCHOR').length > anchorsBefore, '앵커 블록이 추가된다');
  assert.equal(e.pending.length, 0, '앵커 후 대기열은 비워진다');

  e.runBatch(p.horizon + 1);
  assert(['HIT', 'MISS'].includes(p.status), `만기 판정됨 status=${p.status}`);
  assert.equal(p.reveal, true, 'salt 공개 후 해시 재계산 일치');
});

ok('등록 슬롯: 무료 3건 초과 시 토큰 차감', () => {
  const day = e.DAY;
  const close = e.ST('005930').close;
  for (let i = 0; i < SLOT_FREE; i++) {
    e.setState({ formCode: '005930', dir: 'UP', formTarget: String(close + 1000 + i), formNote: '슬롯' + i });
    e.submitPred();
  }
  assert.equal(e.slotUsed('REAL'), SLOT_FREE);
  const tokens = e.me.tokens;
  e.setState({ formCode: '005930', dir: 'UP', formTarget: String(close + 9000), formNote: '초과분' });
  e.submitPred();
  assert.equal(e.me.tokens, tokens - SLOT_FEE, '초과 등록은 2,000 PRT 차감');
  assert.equal(e.DAY, day, '배치는 진행되지 않았다');
});

ok('구독: 토큰 차감 + 70% 배분 + 원장 기록', () => {
  const u = e.U('u1');
  const tokens = e.me.tokens;
  const blocks = e.chain.length;
  e.askSub('u1');
  e.doSub('u1');
  assert(e.me.subs.has('u1'), '구독 상태');
  assert.equal(e.me.tokens, tokens - u.fee);
  assert.equal(u.earned, Math.round(u.fee * 0.7));
  assert.equal(e.chain.length, blocks + 1);
  assert.equal(e.chain[e.chain.length - 1].type, 'SUBSCRIBE');
});

ok('변조 탐지: 해시 불일치 + 머클루트 불일치를 동시에 잡는다', () => {
  const b = e.chain.find((x) => x.type === 'PREDICT' && x.anchor)!;
  b._orig = JSON.parse(JSON.stringify(b.data));
  b.data.커밋해시 = e.hash('조작된목표가').slice(0, 24) + '…';

  const v = e.audit();
  assert.equal(v[b.i].okSelf, false, '블록 자체 해시 불일치');
  const anchor = e.chain[b.anchor!.idx];
  assert.equal(v[anchor.i].rootOk, false, '머클루트 불일치');
  assert(v.some((r) => !r.valid), '무결성 훼손 감지');

  b.data = b._orig!;
  delete b._orig;
  assert(e.audit().every((r) => r.valid && r.rootOk), '원복 후 무결성 회복');
});

ok('분석 도구: 백테스트 · 증명서 발급', () => {
  const tokens = e.me.tokens;
  e.backtest('u1');
  assert.equal(e.me.tokens, tokens - 1000);
  assert.equal(e.state.modal?.kind, 'info');
  e.closeModal();

  const t2 = e.me.tokens;
  e.certificate('u1');
  assert.equal(e.me.tokens, t2 - 3000);
  assert.equal(e.chain[e.chain.length - 1].type, 'CERT');
  e.closeModal();
});

ok('랭킹: 신뢰도 순 정렬 + 트랙 분리 집계', () => {
  const real = e.USERS.map((u) => ({ u, ...e.stats(u.id, null, 'REAL') })).filter((r) => r.done > 0);
  assert(real.length > 0);
  const sorted = [...real].sort((a, b) => b.score - a.score);
  assert(sorted[0].score >= sorted[sorted.length - 1].score);
  assert.equal(e.stats('u1', null, 'REPLAY').done, 0, '리플레이 실적은 실전과 별도로 0건');
});

ok('리플레이: 예측 등록 → 게임일 진행 → 자동 검증', () => {
  const code = e.TICK[0].id;
  const px = e.sPrice(e.TICK[0]);
  e.setState({ sCode: code, sDir: 'UP', sTarget: String(px + 1000), sHorizon: 3, sNote: '리플레이 근거' });
  e.submitPredReplay();
  const p = e.preds[e.preds.length - 1];
  assert.equal(p.track, 'REPLAY');
  assert.equal(p.status, 'BASE');

  e.seasonStep();
  assert.equal(p.status, 'OPEN', '다음 게임일 종가로 기준가 확정');
  for (let i = 0; i < 4; i++) e.seasonStep();
  assert(['HIT', 'MISS'].includes(p.status), `만기 판정됨 status=${p.status}`);
  assert.equal(e.stats('me', null, 'REPLAY').done, 1, '리플레이 트랙에만 집계된다');
});

ok('실전 배치는 리플레이 예측을 건드리지 않는다', () => {
  const code = e.TICK[1].id;
  const px = e.sPrice(e.TICK[1]);
  e.setState({ sCode: code, sDir: 'UP', sTarget: String(px + 500), sHorizon: 3, sNote: '혼선 방지' });
  e.submitPredReplay();
  const p = e.preds[e.preds.length - 1];
  e.runBatch(3); // 프로토타입에서는 여기서 ST('삼성전자') → undefined 로 터졌다
  assert.equal(p.status, 'BASE', '리플레이 예측은 실전 시계에 반응하지 않는다');
});

ok('모의 체결: 매수·매도 + 예수금 정산', () => {
  const t = e.TICK[0];
  const px = e.sPrice(t);
  const cash = e.sCash;
  e.setState({ qtyMap: { ...e.state.qtyMap, [t.id]: 10 } });
  e.sTrade(t.id, 1);
  assert.equal(t.qty, 10);
  assert.equal(e.sCash, cash - px * 10);
  assert.equal(t.avg, px);
  e.sTrade(t.id, -1);
  assert.equal(t.qty, 0);
  assert.equal(e.sCash, cash);
});

ok('시즌 전환: 랭킹 시즌은 참가비 차감 + 종목 블라인드', () => {
  const tokens = e.me.tokens;
  e.switchSeason('ranked');
  assert.equal(e.me.tokens, tokens - 1000);
  assert.equal(e.seasonMode, 'ranked');
  assert(e.TICK.every((t) => t.blind && /^[A-H]사$/.test(t.id)), '종목명이 블라인드 처리된다');
  assert.equal(e.preds.filter((p) => (p.track ?? 'REAL') === 'REPLAY').length, 0, '리플레이 기록 초기화');
  assert.equal(e.chain[e.chain.length - 1].type, 'SEASON_ENTRY');
});

ok('블록 부트스트랩: 길이 유지, 경로는 달라진다', () => {
  const src = Array.from({ length: 120 }, (_, i) => 10000 + i * 37);
  const out = e.bootstrap(src, 20);
  assert.equal(out.length, src.length);
  assert.equal(out[0], src[0]);
  assert(out.some((v, i) => v !== src[i]), '재배열로 경로가 바뀐다');
});

ok('시즌 종료: 자동 청산 + 정답 공개', () => {
  e.setState({ qtyMap: { ...e.state.qtyMap, [e.TICK[0].id]: 5 } });
  e.sTrade(e.TICK[0].id, 1);
  assert.equal(e.TICK[0].qty, 5);
  e.endSeason();
  assert(e.sClosed, '종료 상태 기록');
  assert.equal(e.TICK[0].qty, 0, '포지션 자동 청산');
  assert.equal(e.sClosed!.board.length, e.TICK.length);
  assert(e.sClosed!.board.every((x) => x.real), '블라인드 종목의 실제 이름 공개');
});

ok('최종 원장 무결성', () => {
  const v = e.audit();
  assert(v.every((r) => r.valid && r.rootOk), '전 구간 검증 통과');
});

e.dispose();
console.log(checks.map((c) => '  ✔ ' + c).join('\n'));
console.log(`\n${checks.length} checks passed.`);
