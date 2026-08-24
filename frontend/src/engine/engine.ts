import type {
  BadgeStatus,
  Block,
  BlockType,
  Dir,
  Pred,
  Report,
  Route,
  Season,
  Stats,
  Stock,
  Tick,
  Track,
  UiState,
  User,
} from './types';

const STOCK_SEED: Omit<Stock, 'prevClose'>[] = [
  { c: '005930', n: '삼성전자', s: '반도체', close: 71800 },
  { c: '000660', n: 'SK하이닉스', s: '반도체', close: 184500 },
  { c: '035420', n: 'NAVER', s: '인터넷', close: 203000 },
  { c: '035720', n: '카카오', s: '인터넷', close: 41250 },
  { c: '207940', n: '삼성바이오로직스', s: '바이오', close: 812000 },
  { c: '068270', n: '셀트리온', s: '바이오', close: 176300 },
  { c: '005380', n: '현대차', s: '자동차', close: 238500 },
  { c: '373220', n: 'LG에너지솔루션', s: '2차전지', close: 402000 },
];

const USER_SEED: Omit<User, 'earned'>[] = [
  { id: 'u1', name: '차트요정', handle: '@chart_fairy', skill: 0.74, fee: 14900, main: '반도체', bio: '수급·차트 기반 2주 스윙. 반도체 섹터 집중.', color: '#A855F7' },
  { id: 'u2', name: '밸류사냥꾼', handle: '@value_hunt', skill: 0.68, fee: 19900, main: '자동차', bio: '재무제표와 밸류에이션만 봅니다. 3개월 이상 장기 관점.', color: '#EC4899' },
  { id: 'u3', name: '바이오박사', handle: '@bio_phd', skill: 0.61, fee: 24900, main: '바이오', bio: '임상 데이터 해석 기반 바이오 전문. 변동성 큽니다.', color: '#FB923C' },
  { id: 'u4', name: '퀀트김씨', handle: '@quant_kim', skill: 0.71, fee: 29900, main: '2차전지', bio: '팩터 모델 시그널 자동 발행. 감정 개입 0%.', color: '#F5F5F6' },
  { id: 'u5', name: '테마추적자', handle: '@theme_rider', skill: 0.52, fee: 9900, main: '인터넷', bio: '단기 테마 매매. 적중률보다 손익비를 봅니다.', color: '#EB8C96' },
  { id: 'u6', name: '존버선생', handle: '@hodl_sir', skill: 0.58, fee: 7900, main: '인터넷', bio: '길게 봅니다. 60영업일 예측 위주.', color: '#A0A8B8' },
  { id: 'me', name: '나 (게스트)', handle: '@me', skill: 0.5, fee: 0, main: '-', bio: '직접 예측을 등록해 기록을 쌓아보세요.', color: '#5b6b85' },
];

const NOTES = [
  '외국인 순매수 5일 연속 + 20일선 정배열 전환. 분할 진입 구간으로 판단.',
  '실적 컨센서스 대비 어닝 서프라이즈 가능성. PER 밴드 하단 구간.',
  '수급 공백 구간. 기관 매도 소진 시점 이후 반등 시나리오.',
  '섹터 전반 모멘텀 둔화. 단기 조정 후 재진입 권고.',
  '경쟁사 가이던스 하향으로 업황 피크아웃 우려. 비중 축소 판단.',
];

const REPORT_TITLES = [
  '2026년 하반기 업황 점검',
  '이번 주 매매 노트',
  '밸류에이션 리레이팅 후보 점검',
  '기술적 지표로 본 시장 국면',
  '실적 시즌 프리뷰: 주목할 지표',
  '수급 데이터로 보는 다음 로테이션',
];

const REPORT_BODIES = [
  '이번 구간은 외국인·기관 수급이 동시에 개선되는 드문 시기입니다. 밸류에이션 부담이 낮은 종목을 중심으로 분할 접근을 권합니다.',
  '실적 시즌을 앞두고 컨센서스 상향 종목과 하향 종목의 괴리가 커지고 있습니다. 이번 주는 가이던스 발표 일정을 먼저 체크하세요.',
  '기술적으로는 단기 과열 신호가 나오고 있어 신규 진입보다는 기존 포지션 점검이 우선입니다. 손절 기준을 재확인하시길 권합니다.',
  '섹터 로테이션이 빨라지는 국면입니다. 한 섹터에 몰리기보다 스토리별로 분산하는 전략이 유효했습니다.',
];

export const HORIZONS: [number, string][] = [
  [5, '5영업일 (약 1주)'],
  [10, '10영업일 (약 2주)'],
  [20, '20영업일 (약 1개월)'],
  [60, '60영업일 (약 3개월)'],
];

export const SEASON_HORIZONS: [number, string][] = [
  [3, '3게임일'],
  [5, '5게임일'],
  [10, '10게임일'],
];

export const SLOT_FREE = 3;
export const SLOT_FEE = 2000;
export const BACKTEST_FEE = 1000;
export const CERT_FEE = 3000;

export interface AuditRow {
  real: string;
  okSelf: boolean;
  okLink: boolean;
  valid: boolean;
  rootOk: boolean;
}

/**
 * 프로토타입의 DCLogic 클래스를 그대로 옮긴 상태 머신.
 * React를 전혀 모르며, 변경 시 listener만 호출한다.
 */
export class Engine {
  private seed = 20260819;
  private listeners = new Set<() => void>();
  /** useSyncExternalStore 스냅샷용 버전 카운터 */
  version = 0;

  STOCKS: Stock[];
  SECTORS: string[];
  USERS: User[];
  me: { tokens: number; subs: Set<string> };

  DAY = 0;
  preds: Pred[] = [];
  chain: Block[] = [];
  pid = 0;
  pending: number[] = [];
  lastBatchNote = '아직 진행된 배치가 없습니다.';
  BASE = new Date('2026-08-19T13:30:00');
  REPORTS: Report[] = [];

  seasonMode: 'practice' | 'ranked' = 'practice';
  SEASON!: Season;
  TICK: Tick[] = [];
  sDay = 0;
  sCash = 0;
  playing = false;
  sClosed: { final: number; profit: number; board: { id: string; real: string; ret: number }[] } | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;

  state: UiState;

  constructor() {
    this.STOCKS = STOCK_SEED.map((s) => ({ ...s, prevClose: s.close }));
    this.SECTORS = [...new Set(this.STOCKS.map((s) => s.s))];
    this.USERS = USER_SEED.map((u) => ({ ...u, earned: 0 }));
    this.me = { tokens: 1000000, subs: new Set() };

    this.state = {
      route: 'feed',
      profileId: null,
      profileTab: 'preds',
      feedTab: 'all',
      rankTab: 'total',
      rankTrack: 'REAL',
      chainMore: 12,
      formCode: this.STOCKS[0].c,
      dir: 'UP',
      formTarget: '',
      formHorizon: 5,
      formConf: 70,
      formNote: '',
      composerOpen: false,
      sComposerOpen: false,
      sCode: '',
      sDir: 'UP',
      sTarget: '',
      sHorizon: 5,
      sNote: '',
      modal: null,
      toasts: [],
      qtyMap: {},
    };

    this.seedData();
    this.seedReports();
    this.seedSeason();
    this.sCash = this.SEASON.cash0;
    const qm: Record<string, number> = {};
    this.TICK.forEach((t) => (qm[t.id] = 10));
    this.state.qtyMap = qm;
  }

  // ── 구독 (React 연결점) ──────────────────────────────────────────
  subscribe = (fn: () => void) => {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  };
  getSnapshot = () => this.version;
  bump() {
    this.version++;
    this.listeners.forEach((fn) => fn());
  }
  setState(patch: Partial<UiState> | ((s: UiState) => Partial<UiState>)) {
    const p = typeof patch === 'function' ? patch(this.state) : patch;
    this.state = { ...this.state, ...p };
    this.bump();
  }
  dispose() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  // ── 유틸 ────────────────────────────────────────────────────────
  rnd() {
    this.seed = (this.seed * 1664525 + 1013904223) >>> 0;
    return this.seed / 4294967296;
  }
  pick<T>(a: T[]): T {
    return a[Math.floor(this.rnd() * a.length)];
  }
  num(n: number) {
    return Math.round(n).toLocaleString('ko-KR');
  }
  pct(n: number) {
    return (n >= 0 ? '+' : '') + n.toFixed(2) + '%';
  }
  ST(c: string) {
    return this.STOCKS.find((s) => s.c === c)!;
  }
  U(id: string) {
    return this.USERS.find((u) => u.id === id)!;
  }

  hash(str: string) {
    let h1 = 0x811c9dc5,
      h2 = 0x01000193,
      h3 = 0xdeadbeef,
      h4 = 0x41c6ce57;
    for (let i = 0; i < str.length; i++) {
      const c = str.charCodeAt(i);
      h1 = Math.imul(h1 ^ c, 16777619) >>> 0;
      h2 = (h2 + Math.imul(c, 2654435761)) >>> 0;
      h3 = Math.imul(h3 ^ (c << i % 16), 2246822519) >>> 0;
      h4 = ((Math.imul(h4, 31) + c) ^ h1) >>> 0;
    }
    return [h1, h2, h3, h4, (h1 ^ h3) >>> 0, (h2 ^ h4) >>> 0, (h1 + h4) >>> 0, (h2 + h3) >>> 0]
      .map((x) => (x >>> 0).toString(16).padStart(8, '0'))
      .join('');
  }

  merkle(hs: string[]) {
    if (!hs.length) return '0'.repeat(64);
    let L = hs.slice();
    while (L.length > 1) {
      const N: string[] = [];
      for (let i = 0; i < L.length; i += 2) N.push(this.hash(L[i] + (L[i + 1] || L[i])));
      L = N;
    }
    return L[0];
  }

  bizDate(n: number) {
    const d = new Date(this.BASE);
    const step = n >= 0 ? 1 : -1;
    let left = Math.abs(n);
    while (left > 0) {
      d.setDate(d.getDate() + step);
      const w = d.getDay();
      if (w !== 0 && w !== 6) left--;
    }
    return d;
  }
  dstr(n: number) {
    const t = this.bizDate(n);
    return (
      t.getFullYear() +
      '.' +
      String(t.getMonth() + 1).padStart(2, '0') +
      '.' +
      String(t.getDate()).padStart(2, '0')
    );
  }

  // ── 원장 ────────────────────────────────────────────────────────
  private body(b: Block) {
    return JSON.stringify({ i: b.i, type: b.type, data: b.data, ts: b.ts, prev: b.prev });
  }

  addBlock(type: BlockType, data: Record<string, string | number>, day?: number) {
    if (day === undefined) day = this.DAY;
    const prev = this.chain.length ? this.chain[this.chain.length - 1].hash : '0'.repeat(64);
    const b: Block = {
      i: this.chain.length,
      type,
      data,
      prev,
      ts:
        this.dstr(day) +
        ' ' +
        (type === 'ANCHOR' ? '13:35' : '09:' + String((this.chain.length * 7) % 60).padStart(2, '0')),
      hash: '',
    };
    b.hash = this.hash(this.body(b));
    this.chain.push(b);
    if (type === 'PREDICT' || type === 'RESULT') {
      b.anchor = null;
      this.pending.push(b.i);
    }
    return b;
  }

  anchorBatch(day: number) {
    if (!this.pending.length) return null;
    const members = this.pending.slice();
    this.pending = [];
    const root = this.merkle(members.map((i) => this.chain[i].hash));
    const tx = '0x' + this.hash(root + day).slice(0, 40);
    const a = this.addBlock(
      'ANCHOR',
      { 머클루트: root, 포함커밋: members.length + '건', 트랜잭션: tx, 체인: 'PredictChain Ledger' },
      day,
    );
    a._members = members;
    members.forEach((i) => {
      this.chain[i].anchor = { idx: a.i, tx };
    });
    return a;
  }

  audit(): AuditRow[] {
    let prevReal = '0'.repeat(64);
    let broken = false;
    const v: AuditRow[] = this.chain.map((b) => {
      const real = this.hash(this.body(b));
      const okSelf = real === b.hash;
      const okLink = b.prev === prevReal;
      const valid = okSelf && okLink && !broken;
      if (!valid) broken = true;
      prevReal = real;
      return { real, okSelf, okLink, valid, rootOk: true };
    });
    this.chain.forEach((b) => {
      if (b.type !== 'ANCHOR') return;
      const now = this.merkle(b._members!.map((i) => v[i].real));
      v[b.i].rootOk = now === b.data.머클루트;
      if (!v[b.i].rootOk) {
        v[b.i].valid = false;
        b._members!.forEach((i) => {
          if (!v[i].okSelf) v[i].rootOk = false;
        });
      }
    });
    return v;
  }

  // ── 커밋-리빌 ───────────────────────────────────────────────────
  private payload(p: Pred) {
    return [p.code, p.dir, p.target, p.horizon].join('|');
  }
  private makeSalt() {
    return this.hash('salt' + this.rnd()).slice(0, 16);
  }
  commitPred(p: Pred, day: number) {
    p.salt = this.makeSalt();
    p.commit = this.hash(this.payload(p) + p.salt);
    const d: Record<string, string | number> = {
      예측ID: p.id,
      예측자: this.U(p.uid).name,
      커밋해시: p.commit.slice(0, 24) + '…',
    };
    if (this.isReplay(p)) d.트랙 = '리플레이 Day ' + (p.day + 1);
    p.block = this.addBlock('PREDICT', d, day).i;
  }
  revealOk(p: Pred) {
    return this.hash(this.payload(p) + p.salt) === p.commit;
  }

  // ── 시드 ────────────────────────────────────────────────────────
  private seedData() {
    this.addBlock('GENESIS', { note: 'PredictChain 커밋 원장 제네시스' }, -200);
    const rows: Omit<Pred, 'id' | 'status' | 'entry' | 'actual' | 'baseDay' | 'settle'>[] = [];
    this.USERS.filter((u) => u.id !== 'me').forEach((u) => {
      const cnt = 14 + Math.floor(this.rnd() * 16);
      for (let i = 0; i < cnt; i++) {
        const st = this.rnd() < 0.45 ? this.STOCKS.find((s) => s.s === u.main)! : this.pick(this.STOCKS);
        const dir: Dir = this.rnd() < 0.62 ? 'UP' : 'DOWN';
        const ref = Math.round(st.close * (0.86 + this.rnd() * 0.28));
        const horizon = this.pick([5, 5, 10, 10, 20, 20, 60]);
        const day = -(6 + Math.floor(this.rnd() * 170));
        rows.push({
          uid: u.id,
          code: st.c,
          dir,
          refClose: ref,
          target: Math.round(ref * (1 + (dir === 'UP' ? 1 : -1) * (0.03 + this.rnd() * 0.12))),
          horizon,
          conf: 55 + Math.floor(this.rnd() * 45),
          day,
          note: this.pick(NOTES),
        });
      }
    });
    [0, 0, 0].forEach(() => {
      const u = this.pick(this.USERS.filter((x) => x.id !== 'me'));
      const st = this.pick(this.STOCKS);
      const dir: Dir = this.rnd() < 0.6 ? 'UP' : 'DOWN';
      rows.push({
        uid: u.id,
        code: st.c,
        dir,
        refClose: st.close,
        target: Math.round(st.close * (1 + (dir === 'UP' ? 1 : -1) * (0.04 + this.rnd() * 0.08))),
        horizon: this.pick([5, 10, 20]),
        conf: 60 + Math.floor(this.rnd() * 35),
        day: 0,
        note: this.pick(NOTES),
      });
    });
    rows
      .sort((a, b) => a.day - b.day)
      .forEach((r) => {
        const p: Pred = { id: ++this.pid, status: 'BASE', entry: null, actual: null, settle: null, baseDay: r.day + 1, ...r };
        this.preds.push(p);
        this.commitPred(p, p.day);
        if (p.baseDay <= 0) {
          p.entry = Math.round(p.refClose * (1 + (this.rnd() - 0.5) * 0.03));
          p.settle = p.baseDay + p.horizon;
          p.status = 'OPEN';
          if (p.settle <= 0) this.settleSeed(p);
        }
        if (p.day % 9 === 0) this.anchorBatch(p.day);
      });
    this.anchorBatch(-1);
  }

  private seedReports() {
    let rid = 0;
    this.USERS.filter((u) => u.id !== 'me').forEach((u) => {
      const cnt = 2 + Math.floor(this.rnd() * 2);
      for (let i = 0; i < cnt; i++) {
        this.REPORTS.push({
          id: ++rid,
          uid: u.id,
          title: this.pick(REPORT_TITLES),
          day: -(1 + Math.floor(this.rnd() * 40)),
          body: this.pick(REPORT_BODIES),
        });
      }
    });
  }

  reportsFor(uid: string) {
    return this.REPORTS.filter((r) => r.uid === uid).sort((a, b) => b.day - a.day);
  }

  private settleSeed(p: Pred) {
    const u = this.U(p.uid);
    const hit = this.rnd() < u.skill;
    const sign = (p.dir === 'UP') === hit ? 1 : -1;
    this.finish(p, Math.round(p.entry! * (1 + sign * (0.005 + this.rnd() * 0.14))), p.settle!);
  }

  finish(p: Pred, actual: number, day: number) {
    p.actual = actual;
    p.status = actual !== p.entry && actual > p.entry! === (p.dir === 'UP') ? 'HIT' : 'MISS';
    p.err = (Math.abs(actual - p.target) / p.target) * 100;
    p.reveal = this.revealOk(p);
    p.block2 = this.addBlock(
      'RESULT',
      {
        예측ID: p.id,
        판정: p.status === 'HIT' ? '적중' : '실패',
        만기종가: actual,
        목표가오차: p.err.toFixed(2) + '%',
        공개salt: p.salt!,
        리빌검증: p.reveal ? '통과' : '실패',
      },
      day,
    ).i;
  }

  // ── 통계 ────────────────────────────────────────────────────────
  stats(uid: string, filter?: string | null, track?: Track): Stats {
    let ps = this.preds.filter((p) => p.uid === uid && (p.track || 'REAL') === (track || 'REAL'));
    if (filter === 'short') ps = ps.filter((p) => p.horizon <= 10);
    else if (filter === 'long') ps = ps.filter((p) => p.horizon >= 20);
    else if (filter && this.SECTORS.includes(filter)) ps = ps.filter((p) => this.sectorOf(p) === filter);
    const done = ps.filter((p) => p.status === 'HIT' || p.status === 'MISS');
    const hit = done.filter((p) => p.status === 'HIT').length;
    const rate = done.length ? (hit / done.length) * 100 : 0;
    const err = done.length ? done.reduce((a, p) => a + p.err!, 0) / done.length : 0;
    const raw = rate * 0.7 + Math.max(0, 100 - err * 3) * 0.3;
    const score = done.length ? raw * (0.55 + (0.45 * done.length) / (done.length + 12)) : 0;
    return { total: ps.length, done: done.length, open: ps.length - done.length, hit, rate, err, score };
  }

  sectorStats(uid: string, track?: Track) {
    return this.SECTORS.map((s) => ({ s, ...this.stats(uid, s, track) })).filter((x) => x.done > 0);
  }

  // ── 리플레이 시즌 ───────────────────────────────────────────────
  /** 20일 블록 부트스트랩 — 통계는 보존하고 역사적 시점만 제거 */
  bootstrap(series: number[], blockLen: number) {
    const rets: number[] = [];
    for (let i = 1; i < series.length; i++) rets.push(series[i] / series[i - 1]);
    const blocks: number[][] = [];
    for (let i = 0; i < rets.length; i += blockLen) blocks.push(rets.slice(i, i + blockLen));
    for (let i = blocks.length - 1; i > 0; i--) {
      const j = Math.floor(this.rnd() * (i + 1));
      [blocks[i], blocks[j]] = [blocks[j], blocks[i]];
    }
    const out = [series[0]];
    blocks.forEach((b) => b.forEach((r) => out.push(Math.max(100, Math.round(out[out.length - 1] * r)))));
    return out.slice(0, series.length);
  }

  seedSeason(mode?: 'practice' | 'ranked') {
    this.seasonMode = mode || this.seasonMode || 'practice';
    const ranked = this.seasonMode === 'ranked';
    this.SEASON = ranked
      ? {
          name: '랭킹 시즌 · 블라인드',
          len: 120,
          cash0: 10000000,
          fee: 1000,
          mode: 'ranked',
          desc: '실제 일별 수익률을 20일 블록으로 잘라 무작위 재배열한 합성 경로입니다. 변동성·모멘텀 등 통계적 성질은 보존되지만 역사적 시점이 사라져 검색으로 정답을 알 수 없습니다.',
        }
      : {
          name: '연습 시즌 · 폭락과 회복',
          len: 120,
          cash0: 10000000,
          fee: 0,
          mode: 'practice',
          desc: '실제 과거 일봉 구간을 그대로 압축 재생합니다. 종목명이 공개되고 보상이 없는 학습용 시즌입니다.',
        };
    this.TICK = [];
    this.sDay = 0;
    this.playing = false;
    this.sClosed = null;
    this.sCash = this.SEASON.cash0;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    const src: [string, number][] = [
      ['반도체', 1.2],
      ['반도체', 1.35],
      ['인터넷', 1.1],
      ['인터넷', 1.45],
      ['바이오', 1.6],
      ['바이오', 1.3],
      ['자동차', 0.85],
      ['2차전지', 1.55],
    ];
    src.forEach((sp, i) => {
      const beta = sp[1];
      let series: number[] = [];
      let v = 10000 + Math.floor(this.rnd() * 40000);
      for (let d = 0; d < this.SEASON.len; d++) {
        let drift = 0.0006;
        if (d >= 28 && d < 45) drift = -0.028;
        else if (d >= 45 && d < 95) drift = 0.011;
        v = Math.max(500, v * (1 + drift * beta + (this.rnd() - 0.5) * 0.03 * beta));
        series.push(Math.round(v));
      }
      if (ranked) series = this.bootstrap(series, 20);
      const real = this.STOCKS[i].n;
      this.TICK.push({
        id: ranked ? String.fromCharCode(65 + i) + '사' : real,
        sector: sp[0],
        beta,
        series,
        real,
        qty: 0,
        avg: 0,
        blind: ranked,
      });
    });
  }

  switchSeason(mode: 'practice' | 'ranked') {
    if (mode === this.seasonMode) return;
    if (mode === 'ranked') {
      const fee = 1000;
      if (this.me.tokens < fee)
        return this.toast('랭킹 시즌 참가비 ' + this.num(fee) + ' PRT가 부족합니다.', 'warn');
      this.me.tokens -= fee;
      this.addBlock('SEASON_ENTRY', { 시즌: '랭킹 시즌', 참가비: fee, 참가자: '나 (게스트)' });
    }
    this.preds = this.preds.filter((p) => (p.track || 'REAL') !== 'REPLAY');
    this.seedSeason(mode);
    const qm: Record<string, number> = {};
    this.TICK.forEach((t) => (qm[t.id] = 10));
    this.setState({ qtyMap: qm, sCode: this.TICK[0].id, sTarget: '', sNote: '' });
    this.toast(this.SEASON.name + ' 시작 · 리플레이 예측 기록이 초기화되었습니다.');
  }

  TK(id: string) {
    return this.TICK.find((t) => t.id === id) || this.TICK[0];
  }
  isReplay(p: Pred) {
    return (p.track || 'REAL') === 'REPLAY';
  }
  nameOf(p: Pred) {
    return this.isReplay(p) ? p.code : this.ST(p.code).n;
  }
  sectorOf(p: Pred) {
    return this.isReplay(p) ? this.TK(p.code).sector : this.ST(p.code).s;
  }
  dayLabelOf(p: Pred, d: number) {
    return this.isReplay(p) ? 'Day ' + (d + 1) : this.dstr(d);
  }

  // ── 등록 슬롯 ───────────────────────────────────────────────────
  slotUsed(track: Track) {
    const now = track === 'REPLAY' ? this.sDay : this.DAY;
    return this.preds.filter((p) => p.uid === 'me' && (p.track || 'REAL') === track && p.day === now).length;
  }
  private slotCheck(track: Track) {
    const used = this.slotUsed(track);
    if (used < SLOT_FREE) return true;
    if (this.me.tokens < SLOT_FEE) {
      this.toast(
        '무료 등록 슬롯 ' + SLOT_FREE + '건을 모두 사용했습니다. 추가 등록에는 ' + this.num(SLOT_FEE) + ' PRT가 필요합니다.',
        'warn',
      );
      return false;
    }
    this.me.tokens -= SLOT_FEE;
    this.addBlock('SLOT_FEE', { 계정: '나 (게스트)', 사유: '일일 무료 슬롯 초과', 차감: SLOT_FEE });
    this.toast('무료 슬롯 초과 · 추가 등록 비용 ' + this.num(SLOT_FEE) + ' PRT 차감');
    return true;
  }

  // ── 분석 도구 ───────────────────────────────────────────────────
  backtest(uid: string) {
    const ps = this.preds.filter(
      (p) => p.uid === uid && !this.isReplay(p) && (p.status === 'HIT' || p.status === 'MISS'),
    );
    if (!ps.length) return this.toast('검증 완료된 예측이 없어 백테스트할 수 없습니다.', 'warn');
    if (this.me.tokens < BACKTEST_FEE) return this.toast('포인트가 부족합니다.', 'warn');
    this.me.tokens -= BACKTEST_FEE;
    let cum = 1,
      wins = 0,
      best = -999,
      worst = 999;
    ps.forEach((p) => {
      const r = ((p.actual! - p.entry!) / p.entry!) * (p.dir === 'UP' ? 1 : -1) * 100;
      if (r > 0) wins++;
      if (r > best) best = r;
      if (r < worst) worst = r;
      cum *= 1 + (r / 100) * 0.2;
    });
    const u = this.U(uid);
    const ret = (cum - 1) * 100;
    this.addBlock('BACKTEST', {
      대상: u.name,
      표본: ps.length + '건',
      누적수익률: ret.toFixed(2) + '%',
      차감: BACKTEST_FEE,
    });
    this.setState({
      modal: {
        kind: 'info',
        title: u.name + ' 팔로우 백테스트',
        desc: '등록일 종가 매수 · 만기일 종가 청산, 건당 자산 20% 배분 가정',
        lines: [
          ['표본 (검증 완료)', ps.length + '건'],
          ['수익 발생', wins + '건'],
          ['누적 수익률', this.pct(ret)],
          ['최고 / 최저 1건', this.pct(best) + ' / ' + this.pct(worst)],
          ['차감 포인트', this.num(BACKTEST_FEE) + ' PRT'],
        ],
        note: '과거 기록에 대한 계산 결과이며 미래 성과를 보장하지 않습니다. 투자 판단과 그 결과는 이용자 본인의 책임입니다.',
      },
    });
  }

  certificate(uid: string) {
    if (this.me.tokens < CERT_FEE) return this.toast('포인트가 부족합니다.', 'warn');
    const u = this.U(uid);
    const s = this.stats(uid, null, 'REAL');
    if (!s.done) return this.toast('검증 완료된 예측이 없어 증명서를 발급할 수 없습니다.', 'warn');
    this.me.tokens -= CERT_FEE;
    const txs = [
      ...new Set(
        this.preds
          .filter((p) => p.uid === uid)
          .map((p) => this.chain[p.block!])
          .filter((b) => b && b.anchor)
          .map((b) => b.anchor!.tx),
      ),
    ];
    const certId = this.hash('cert|' + uid + '|' + s.done + '|' + this.DAY).slice(0, 32);
    this.addBlock('CERT', {
      대상: u.name,
      검증완료: s.done + '건',
      적중률: s.rate.toFixed(1) + '%',
      증명서ID: certId.slice(0, 16) + '…',
    });
    this.setState({
      modal: {
        kind: 'info',
        title: u.name + ' 검증 증명서 발급',
        desc: '온체인 앵커된 예측 기록만으로 구성된 실적 증명서입니다.',
        lines: [
          ['검증 완료', s.done + '건'],
          ['적중률', s.rate.toFixed(1) + '%'],
          ['목표가 평균오차', s.err.toFixed(2) + '%'],
          ['앵커 트랜잭션', txs.length + '건'],
          ['증명서 ID', certId.slice(0, 16) + '…'],
          ['차감 포인트', this.num(CERT_FEE) + ' PRT'],
        ],
        note: '발급 내역도 커밋 원장에 기록됩니다. 외부 공유용 링크로 사용할 수 있습니다.',
      },
    });
  }

  // ── 리플레이 시계 ───────────────────────────────────────────────
  sPrice(t: Tick) {
    return t.series[Math.min(this.sDay, this.SEASON.len - 1)];
  }
  sValue() {
    return this.TICK.reduce((a, t) => a + t.qty * this.sPrice(t), 0);
  }

  seasonStep() {
    if (this.sDay >= this.SEASON.len - 1) {
      this.endSeason();
      return;
    }
    this.sDay++;
    this.replayBatch();
    this.bump();
  }

  private replayBatch() {
    let fixed = 0,
      hit = 0,
      miss = 0;
    this.preds
      .filter((p) => this.isReplay(p) && p.status === 'BASE' && p.baseDay <= this.sDay)
      .forEach((p) => {
        p.entry = this.sPrice(this.TK(p.code));
        p.settle = p.baseDay + p.horizon;
        p.status = 'OPEN';
        fixed++;
      });
    this.preds
      .filter((p) => this.isReplay(p) && p.status === 'OPEN' && p.settle! <= this.sDay)
      .forEach((p) => {
        this.finish(p, this.sPrice(this.TK(p.code)), this.DAY);
        if (p.status === 'HIT') hit++;
        else miss++;
      });
    if (fixed || hit || miss) {
      this.anchorBatch(this.DAY);
      this.lastBatchNote =
        '리플레이 Day ' + (this.sDay + 1) + ' 배치 · 기준가 확정 ' + fixed + '건 · 검증 ' + (hit + miss) + '건 (적중 ' + hit + ')';
      if (hit + miss) this.toast(this.lastBatchNote);
    }
  }

  togglePlay() {
    this.playing = !this.playing;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.playing) this.timer = setInterval(() => this.seasonStep(), 700);
    this.bump();
  }

  endSeason() {
    this.playing = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    const liq = this.sValue();
    this.TICK.forEach((t) => {
      t.qty = 0;
      t.avg = 0;
    });
    this.sCash += liq;
    const profit = this.sCash - this.SEASON.cash0;
    const reward = this.SEASON.mode === 'ranked' && profit > 0 ? Math.round(profit * 0.0005) : 0;
    this.me.tokens += reward;
    if (reward) this.addBlock('SEASON_REWARD', { 시즌: this.SEASON.name, 시즌손익: profit, 지급상금: reward });
    this.sClosed = {
      final: this.sCash,
      profit,
      board: this.TICK.map((t) => ({
        id: t.id,
        real: t.real,
        ret: ((t.series[this.SEASON.len - 1] - t.series[0]) / t.series[0]) * 100,
      })).sort((a, b) => b.ret - a.ret),
    };
    this.addBlock('SEASON', { 시즌: this.SEASON.name, 최종자산: this.sCash, 손익: profit });
    this.toast(
      '시즌 종료 · 포지션 자동 청산 · 시즌 손익 ' +
        (profit >= 0 ? '+' : '') +
        this.num(profit) +
        ' PRT' +
        (this.SEASON.mode === 'ranked'
          ? reward
            ? ' · 상금 ' + this.num(reward) + ' PRT 지급'
            : ' · 상금 없음'
          : ' · 연습 시즌이라 보상은 없습니다.'),
    );
  }

  resetSeason() {
    this.preds = this.preds.filter((p) => (p.track || 'REAL') !== 'REPLAY');
    this.sDay = 0;
    this.sCash = this.SEASON.cash0;
    this.sClosed = null;
    this.playing = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.TICK.forEach((t) => {
      t.qty = 0;
      t.avg = 0;
    });
    this.bump();
  }

  sTrade(id: string, sign: 1 | -1) {
    const t = this.TICK.find((x) => x.id === id)!;
    const qty = +(this.state.qtyMap[id] || 0);
    if (this.sClosed) return this.toast('시즌이 종료되었습니다. 재시작 후 거래하세요.', 'warn');
    if (!qty || qty < 1) return this.toast('수량을 확인하세요.', 'warn');
    const px = this.sPrice(t);
    if (sign > 0) {
      const cost = px * qty;
      if (cost > this.sCash) return this.toast('시즌 예수금이 부족합니다.', 'warn');
      this.sCash -= cost;
      t.avg = (t.avg * t.qty + cost) / (t.qty + qty);
      t.qty += qty;
    } else {
      if (t.qty < qty) return this.toast('보유 수량이 부족합니다.', 'warn');
      this.sCash += px * qty;
      t.qty -= qty;
    }
    this.toast(t.id + ' ' + qty + '주 ' + (sign > 0 ? '매수' : '매도') + ' · 당일 종가 ' + this.num(px) + ' 체결');
  }

  // ── 실전 배치 ───────────────────────────────────────────────────
  private batch() {
    this.DAY++;
    this.STOCKS.forEach((s) => {
      s.prevClose = s.close;
      s.close = Math.max(100, Math.round(s.close * (1 + (this.rnd() - 0.485) * 0.035)));
    });
    let fixed = 0,
      hit = 0,
      miss = 0;
    this.preds
      .filter((p) => p.status === 'BASE' && p.baseDay <= this.DAY && !this.isReplay(p))
      .forEach((p) => {
        p.entry = this.ST(p.code).close;
        p.settle = p.baseDay + p.horizon;
        p.status = 'OPEN';
        fixed++;
      });
    this.preds
      .filter((p) => p.status === 'OPEN' && p.settle! <= this.DAY && !this.isReplay(p))
      .forEach((p) => {
        this.finish(p, this.ST(p.code).close, this.DAY);
        if (p.status === 'HIT') hit++;
        else miss++;
      });
    const a = this.anchorBatch(this.DAY);
    return { fixed, hit, miss, anchor: a };
  }

  runBatch(n: number) {
    let f = 0,
      h = 0,
      m = 0,
      anc = 0;
    for (let i = 0; i < n; i++) {
      const r = this.batch();
      f += r.fixed;
      h += r.hit;
      m += r.miss;
      if (r.anchor) anc++;
    }
    this.lastBatchNote =
      this.dstr(this.DAY) + ' 배치 · 기준가 확정 ' + f + '건 · 검증 ' + (h + m) + '건 (적중 ' + h + ') · 앵커 ' + anc + '건';
    this.toast(this.lastBatchNote);
  }

  // ── 토스트 / 모달 / 구독 ────────────────────────────────────────
  private toastSeq = 0;
  toast(msg: string, kind?: 'warn') {
    const id = ++this.toastSeq;
    this.setState((s) => ({
      toasts: [...s.toasts, { id, msg, color: kind === 'warn' ? 'var(--status-warning)' : 'var(--accent)' }],
    }));
    setTimeout(() => this.setState((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })), 4600);
  }

  askSub(uid: string) {
    this.setState({ modal: { kind: 'sub', uid } });
  }
  closeModal() {
    this.setState({ modal: null });
  }
  doSub(uid: string) {
    const u = this.U(uid);
    if (this.me.subs.has(uid)) return this.closeModal();
    if (this.me.tokens < u.fee) {
      this.toast('포인트가 부족합니다.', 'warn');
      return this.closeModal();
    }
    this.me.tokens -= u.fee;
    this.me.subs.add(uid);
    u.earned += Math.round(u.fee * 0.7);
    this.addBlock('SUBSCRIBE', {
      구독자: '나 (게스트)',
      예측자: u.name,
      구독료: u.fee,
      예측자배분: Math.round(u.fee * 0.7),
    });
    this.closeModal();
    this.toast(u.name + ' 구독 완료 · 분석 근거와 리포트가 공개됩니다.');
  }
  unsub(uid: string) {
    this.me.subs.delete(uid);
    this.toast(this.U(uid).name + ' 구독을 해지했습니다.');
  }

  // ── 예측 등록 ───────────────────────────────────────────────────
  submitPred() {
    const S = this.state;
    const st = this.ST(S.formCode);
    const target = +S.formTarget;
    const note = (S.formNote || '').trim();
    if (!target) return this.toast('목표가를 입력하세요.', 'warn');
    if (S.dir === 'UP' && target <= st.close) return this.toast('상승 예측의 목표가는 직전 종가보다 높아야 합니다.', 'warn');
    if (S.dir === 'DOWN' && target >= st.close) return this.toast('하락 예측의 목표가는 직전 종가보다 낮아야 합니다.', 'warn');
    if (!note) return this.toast('분석 근거를 입력하세요.', 'warn');
    if (!this.slotCheck('REAL')) return;
    const p: Pred = {
      id: ++this.pid,
      uid: 'me',
      track: 'REAL',
      code: S.formCode,
      dir: S.dir,
      refClose: st.close,
      target,
      horizon: +S.formHorizon,
      conf: +S.formConf,
      day: this.DAY,
      baseDay: this.DAY + 1,
      entry: null,
      settle: null,
      note,
      status: 'BASE',
      actual: null,
    };
    this.preds.push(p);
    this.commitPred(p, this.DAY);
    this.toast('예측 #' + p.id + ' 커밋 완료 · 앵커 대기 · 기준가는 ' + this.dstr(p.baseDay) + ' 종가로 확정됩니다.');
    this.setState({ formTarget: '', formNote: '', composerOpen: false });
  }

  submitPredReplay() {
    const S = this.state;
    const t = this.TK(S.sCode || this.TICK[0].id);
    const target = +S.sTarget;
    const px = this.sPrice(t);
    const h = +S.sHorizon;
    const note = (S.sNote || '').trim();
    if (this.sClosed) return this.toast('시즌이 종료되었습니다. 재시작 후 등록하세요.', 'warn');
    if (!target) return this.toast('목표가를 입력하세요.', 'warn');
    if (S.sDir === 'UP' && target <= px) return this.toast('상승 예측의 목표가는 당일 종가보다 높아야 합니다.', 'warn');
    if (S.sDir === 'DOWN' && target >= px) return this.toast('하락 예측의 목표가는 당일 종가보다 낮아야 합니다.', 'warn');
    if (!note) return this.toast('분석 근거를 입력하세요.', 'warn');
    if (this.sDay + 1 + h > this.SEASON.len - 1) return this.toast('남은 시즌 기간보다 예측 기간이 깁니다.', 'warn');
    if (!this.slotCheck('REPLAY')) return;
    const p: Pred = {
      id: ++this.pid,
      uid: 'me',
      track: 'REPLAY',
      code: t.id,
      dir: S.sDir,
      refClose: px,
      target,
      horizon: h,
      conf: 70,
      day: this.sDay,
      baseDay: this.sDay + 1,
      entry: null,
      settle: null,
      note,
      status: 'BASE',
      actual: null,
    };
    this.preds.push(p);
    this.commitPred(p, this.DAY);
    this.toast('리플레이 예측 #' + p.id + ' 커밋 · 기준가는 Day ' + (p.baseDay + 1) + ' 종가로 확정됩니다.');
    this.setState({ sTarget: '', sNote: '', sComposerOpen: false });
  }

  // ── 표시용 ──────────────────────────────────────────────────────
  statusInfo(p: Pred): { status: BadgeStatus; text: string } {
    if (p.status === 'BASE') return { status: 'accent', text: '기준가 확정 대기' };
    if (p.status === 'OPEN')
      return { status: 'warning', text: '검증 대기 · D-' + (p.settle! - (this.isReplay(p) ? this.sDay : this.DAY)) };
    if (p.status === 'HIT') return { status: 'success', text: '✔ 적중' };
    return { status: 'error', text: '✘ 실패' };
  }

  dirInfo(dir: Dir) {
    return dir === 'UP'
      ? { label: '▲ 상승', color: 'var(--status-error)', bg: 'var(--status-error-bg)' }
      : { label: '▼ 하락', color: 'var(--pink)', bg: 'var(--pink-soft)' };
  }

  go(route: Route, profileId?: string) {
    this.setState({ route, ...(profileId ? { profileId, profileTab: 'preds' as const } : {}) });
  }
}

export interface PredVM {
  id: number;
  uName: string;
  uHandle: string;
  uColor: string;
  uInitial: string;
  dayLabel: string;
  badgeStatus: BadgeStatus;
  badgeText: string;
  isReplay: boolean;
  trackLabel: string;
  trackColor: string;
  stockName: string;
  stockCode: string;
  sectorTag: string;
  dirLabel: string;
  dirColor: string;
  dirBg: string;
  confPct: number;
  horizonLabel: string;
  refCloseFmt: string;
  targetFmt: string;
  gapFmt: string;
  entryFmt: string;
  lastLabel: string;
  lastValueFmt: string;
  noteLocked: boolean;
  noteText: string;
  subLabel: string;
  onSubscribe: () => void;
  onOpenProfile: () => void;
  commitShort: string;
  anchorText: string;
  revealText: string;
}

/** 프로토타입의 buildPredVM 이식 — 예측 카드가 필요로 하는 표시값 일체 */
export function buildPredVM(e: Engine, p: Pred): PredVM {
  const u = e.U(p.uid);
  const si = e.statusInfo(p);
  const di = e.dirInfo(p.dir);
  const rep = e.isReplay(p);
  const gap = ((p.target - p.refClose) / p.refClose) * 100;
  const blk = e.chain[p.block!];
  const subscribed = e.me.subs.has(p.uid) || p.uid === 'me';
  const done = p.status === 'HIT' || p.status === 'MISS';
  return {
    id: p.id,
    uName: u.name,
    uHandle: u.handle,
    uColor: u.color,
    uInitial: u.name[0],
    dayLabel: e.dayLabelOf(p, p.day),
    badgeStatus: si.status,
    badgeText: si.text,
    isReplay: rep,
    trackLabel: rep ? '리플레이' : '실전',
    trackColor: rep ? 'var(--pink)' : 'var(--accent)',
    stockName: e.nameOf(p),
    stockCode: rep ? e.SEASON.name : p.code,
    sectorTag: e.sectorOf(p),
    dirLabel: di.label,
    dirColor: di.color,
    dirBg: di.bg,
    confPct: p.conf,
    horizonLabel: p.horizon + (rep ? '게임일' : '영업일'),
    refCloseFmt: e.num(p.refClose),
    targetFmt: e.num(p.target),
    gapFmt: e.pct(gap),
    entryFmt: p.entry ? e.num(p.entry) : '확정 대기',
    lastLabel: done ? '만기종가 / 오차' : '만기일',
    lastValueFmt: done
      ? e.num(p.actual!) + ' / ' + p.err!.toFixed(1) + '%'
      : p.status === 'OPEN'
        ? e.dayLabelOf(p, p.settle!)
        : '확정 후 산정',
    noteLocked: !subscribed,
    noteText: p.note,
    subLabel: '🔒 구독하고 근거 보기 · 월 ' + e.num(u.fee) + ' PRT',
    onSubscribe: () => e.askSub(u.id),
    onOpenProfile: () => e.go('profile', u.id),
    commitShort: p.commit!.slice(0, 26),
    anchorText: blk.anchor ? '⛓ 앵커 완료' : '⏳ 앵커 대기',
    revealText: p.reveal !== undefined ? ' · 🔓 리빌 ' + (p.reveal ? '통과' : '실패') : '',
  };
}
