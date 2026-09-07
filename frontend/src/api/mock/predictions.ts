/* C-01 예측 등록 목업. 백엔드에 predictions 표와 엔드포인트가 붙으면 지운다.

   응답 분기를 실제로 재현한다. 화면이 네 갈래를 모두 그리는지 확인하려는 것이다
   (§4 C-01): 201 슬롯 내 · 202 슬롯 초과(소각) → M-02 · 401 서명 불일치 · 409 잔액 부족.

   슬롯은 모듈 변수로 들고 있어 한 세션 안에서 실제로 줄어든다. 새로고침하면
   초기값으로 돌아간다 — 진짜 저장은 백엔드가 할 일이다. */
import { phaseOf } from '../predictions'
import type {
  CreateResult, Horizon, MyPrediction, MyPredictionList, PredictionDraft,
  PredictionStatus, SlotStatus, StockPrediction, StockPredictionList,
} from '../predictions'

const delay = <T,>(value: T, ms: number) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(value), ms))

const WEEKLY_LIMIT = 3
let used = 1

/** 다음 주 월요일 09:00 에 다시 찬다 */
function nextReset() {
  const d = new Date()
  d.setDate(d.getDate() + ((8 - d.getDay()) % 7 || 7))
  d.setHours(9, 0, 0, 0)
  return d.toISOString()
}

export function slots(): Promise<SlotStatus> {
  return delay({
    weeklyLimit: WEEKLY_LIMIT,
    used,
    remaining: Math.max(0, WEEKLY_LIMIT - used),
    resetsAt: nextReset(),
  }, 240)
}

/* 잔액 부족을 한 번은 보여 주기 위한 장치. 목표가 끝자리가 9 면 409 를 낸다 —
   화면이 409 를 어떻게 그리는지 매번 확인할 수 있어야 해서 남겨 둔다. */
const wouldFailBalance = (draft: PredictionDraft) =>
  Math.round(draft.targetPrice) % 10 === 9

export function create(draft: PredictionDraft): Promise<CreateResult> {
  if (wouldFailBalance(draft)) {
    return Promise.reject(Object.assign(new Error('INSUFFICIENT_BALANCE'), {
      status: 409, code: 'INSUFFICIENT_BALANCE',
      message: '소각에 필요한 토큰이 부족합니다.',
    }))
  }

  const overSlot = used >= WEEKLY_LIMIT
  used += 1

  /* 슬롯을 넘기면 소각 거래라 온체인 확정을 기다린다 — 202 + operationId.
     여기서 predictionId 를 같이 주면 화면이 M-02 를 건너뛰게 되므로 주지 않는다. */
  if (overSlot) {
    return delay<CreateResult>({
      kind: 'queued',
      data: { operationId: `op_${Date.now().toString(36)}` },
    }, 900)
  }

  /* commitHash 는 서버가 salt 를 섞어 만든다. 목업도 클라이언트가 계산할 수 없는
     값이라는 걸 드러내려고 draft 와 무관한 난수로 만든다. */
  const hash = `0x${Array.from({ length: 64 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join('')}`

  return delay<CreateResult>({
    kind: 'created',
    data: { predictionId: `pr_${Date.now().toString(36)}`, status: 'BASE', commitHash: hash },
  }, 900)
}

/* ── 이 종목에 걸린 남의 예측 ─────────────────────────────
   §5 게이팅을 실제로 재현한다. 판정 완료는 전부 공개하고, 미판정은 잠가서
   direction·targetPrice 를 비운 채 내린다. 화면이 잠금 카드와 구독 CTA 를
   제대로 그리는지 눈으로 확인하려는 것이다.

   구독 중인 채널이 하나 있다고 가정해 잠기지 않은 미판정도 한 건 둔다 —
   "잠긴 것" 과 "구독해서 보이는 것" 이 같은 목록에 섞이는 모습을 봐야 한다. */
const AUTHORS = [
  { userId: 'u1', nickname: '데이터로보는사람', accuracy: 71.4, subscribed: true },
  { userId: 'u2', nickname: '반도체존버', accuracy: 58.2, subscribed: false },
  { userId: 'u3', nickname: '레드와이어사지마라했다', accuracy: 64.9, subscribed: false },
  { userId: 'u4', nickname: '분기실적만본다', accuracy: null, subscribed: false },
  { userId: 'u5', nickname: '차트는거들뿐', accuracy: 49.1, subscribed: false },
  { userId: 'u6', nickname: '외국인수급추적', accuracy: 77.0, subscribed: false },
  { userId: 'u7', nickname: '적립식개미', accuracy: 52.6, subscribed: false },
  { userId: 'u8', nickname: 'HBM만판다', accuracy: 68.3, subscribed: false },
  { userId: 'u9', nickname: '메모리사이클', accuracy: 61.5, subscribed: false },
  { userId: 'u10', nickname: '공시읽는남자', accuracy: 55.8, subscribed: false },
]

const HORIZONS: Horizon[] = [5, 10, 20, 60]
const STATUSES: PredictionStatus[] = ['HIT', 'BASE', 'MISS', 'OPEN', 'HIT', 'OPEN', 'MISS', 'BASE', 'HIT', 'OPEN']

/** 종목코드를 씨앗으로 쓰는 결정적 난수. 새로고침해도 같은 목록이 나온다 */
function seeded(seed: string) {
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) { h ^= seed.charCodeAt(i); h = Math.imul(h, 16777619) }
  return () => { h ^= h << 13; h ^= h >>> 17; h ^= h << 5; return ((h >>> 0) % 100000) / 100000 }
}

function buildRows(code: string): StockPrediction[] {
  const rnd = seeded(`${code}p`)
  /* 기준 종가가 있어야 목표가가 그럴듯하다. 목록 목업과 같은 값을 쓴다 */
  const base = 100000 + Math.floor(rnd() * 200000)

  return AUTHORS.map((a, i) => {
    const status = STATUSES[i]
    const judged = status === 'HIT' || status === 'MISS'
    /* 판정 완료는 전체 공개, 미판정은 구독자만 — 구독 안 한 채널이면 잠근다 */
    const locked = !judged && !a.subscribed
    const direction = rnd() > 0.35 ? 'UP' as const : 'DOWN' as const
    const target = Math.round(base * (direction === 'UP' ? 1.04 + rnd() * 0.12 : 0.84 + rnd() * 0.12) / 100) * 100
    const close = judged ? Math.round(base * (0.9 + rnd() * 0.2) / 100) * 100 : null

    const d = new Date('2026-08-31T00:00:00Z')
    d.setUTCDate(d.getUTCDate() + HORIZONS[i % 4] * 1.4)

    return {
      id: `${code}-pr${i + 1}`,
      author: { userId: a.userId, nickname: a.nickname },
      accuracy: a.accuracy,
      status,
      horizon: HORIZONS[i % 4],
      createdAt: `2026-08-${String(10 + i).padStart(2, '0')}T09:30:00+09:00`,
      dueDate: d.toISOString().slice(0, 10),
      locked,
      /* 방향은 잠겨도 내린다. 목표가만 뺀다(서버 PredictionCardResponse 와 같은 규칙) */
      direction,
      targetPrice: locked ? null : target,
      /* 등록 직후(BASE)는 기준가가 아직 없다. 0 으로 채우지 않는다 */
      basePrice: status === 'BASE' ? null : base,
      closePrice: close,
      errorRate: close === null || locked ? null : Math.round(((close - target) / target) * 1000) / 10,
      channelId: locked ? a.userId : null,
    }
  })
}

type Query = Record<string, string | number | boolean | undefined>

/* 서버가 할 일을 그대로 흉내낸다 — phase 로 먼저 거르고, 그다음 커서로 자른다.
   순서가 뒤바뀌면(자른 뒤에 거르면) 페이지마다 줄 수가 들쭉날쭉해진다.

   건수와 적중률은 거르기 전 전체에서 센다. 탭 옆 숫자는 지금 보고 있는
   페이지가 아니라 목록 전체를 가리켜야 하기 때문이다. */
export function stockPredictions(code: string, query: Query): Promise<StockPredictionList> {
  const all = buildRows(code)
  const pending = all.filter((r) => phaseOf(r.status) === 'PENDING')
  const judged = all.filter((r) => phaseOf(r.status) === 'JUDGED')
  const hit = judged.filter((r) => r.status === 'HIT').length

  const rows = query.phase === 'JUDGED' ? judged : pending
  const size = Number(query.size ?? 8)
  const cursor = query.cursor as string | undefined

  const start = cursor ? rows.findIndex((r) => r.id === cursor) + 1 : 0
  const page = rows.slice(start, start + size)
  const last = page[page.length - 1]
  const hasNext = last ? rows.indexOf(last) < rows.length - 1 : false

  return delay({
    items: page,
    nextCursor: hasNext && last ? last.id : null,
    hasNext,
    pendingCount: pending.length,
    judgedCount: judged.length,
    hitRate: judged.length ? Math.round((hit / judged.length) * 100) : null,
  }, 340)
}

/* ── 내 예측 (C-02) ───────────────────────────────────────
   명세의 아홉 필드에 stockName 을 더해 만든다(팀에서 추가하기로 정한 필드).
   서버에 아직 없으므로 화면은 비었을 때 종목코드로 대체하게 되어 있다 —
   그 갈래도 확인하려고 한 줄만 이름을 비워 둔다.

   상태 분포를 골고루 둔다. 등록 직후(BASE)는 dday·errorRate 가 둘 다 비어 있고,
   판정 대기(OPEN)는 dday 만, 판정 완료(HIT/MISS)는 errorRate 만 있다. */
const MY_ROWS: MyPrediction[] = [
  { id: 'p1', stockCode: '005930', stockName: '삼성전자', direction: 'UP', targetPrice: 78000, horizon: 20, status: 'BASE', dday: null, errorRate: null, settleDate: '2026-09-28' },
  { id: 'p2', stockCode: '000660', stockName: 'SK하이닉스', direction: 'UP', targetPrice: 215000, horizon: 10, status: 'OPEN', dday: 7, errorRate: null, settleDate: '2026-09-14' },
  { id: 'p3', stockCode: '035420', stockName: 'NAVER', direction: 'DOWN', targetPrice: 165000, horizon: 5, status: 'OPEN', dday: 2, errorRate: null, settleDate: '2026-09-07' },
  { id: 'p4', stockCode: '373220', stockName: 'LG에너지솔루션', direction: 'UP', targetPrice: 380000, horizon: 60, status: 'OPEN', dday: 58, errorRate: null, settleDate: '2026-11-23' },
  { id: 'p5', stockCode: '005380', stockName: '현대차', direction: 'UP', targetPrice: 260000, horizon: 20, status: 'HIT', dday: null, errorRate: 1.2, settleDate: '2026-08-24' },
  { id: 'p6', stockCode: '068270', stockName: '셀트리온', direction: 'DOWN', targetPrice: 180000, horizon: 10, status: 'HIT', dday: null, errorRate: -2.4, settleDate: '2026-08-17' },
  { id: 'p7', stockCode: '051910', stockName: 'LG화학', direction: 'UP', targetPrice: 420000, horizon: 20, status: 'MISS', dday: null, errorRate: -11.6, settleDate: '2026-08-10' },
  { id: 'p8', stockCode: '105560', stockName: 'KB금융', direction: 'DOWN', targetPrice: 80000, horizon: 5, status: 'MISS', dday: null, errorRate: 9.3, settleDate: '2026-08-03' },
  { id: 'p9', stockCode: '000270', stockName: '기아', direction: 'UP', targetPrice: 108000, horizon: 10, status: 'HIT', dday: null, errorRate: 0.4, settleDate: '2026-07-27' },
  { id: 'p10', stockCode: '006400', stockName: '삼성SDI', direction: 'DOWN', targetPrice: 300000, horizon: 20, status: 'MISS', dday: null, errorRate: 6.8, settleDate: '2026-07-20' },
  { id: 'p11', stockCode: '247540', stockName: '에코프로비엠', direction: 'DOWN', targetPrice: 150000, horizon: 60, status: 'HIT', dday: null, errorRate: -1.9, settleDate: '2026-07-13' },
  { id: 'p12', stockCode: '055550', stockName: '신한지주', direction: 'UP', targetPrice: 52000, horizon: 5, status: 'HIT', dday: null, errorRate: 2.1, settleDate: '2026-07-06' },
  { id: 'p13', stockCode: '207940', stockName: '삼성바이오로직스', direction: 'UP', targetPrice: 1020000, horizon: 20, status: 'MISS', dday: null, errorRate: -8.2, settleDate: '2026-06-29' },
  /* 이름이 아직 안 오는 갈래 — 화면이 종목코드로 대체하는지 본다 */
  { id: 'p14', stockCode: '005490', stockName: null, direction: 'DOWN', targetPrice: 390000, horizon: 10, status: 'HIT', dday: null, errorRate: -0.7, settleDate: '2026-06-22' },
]

/* 서버가 할 일을 그대로 흉내낸다 — status 로 먼저 거르고 그다음 커서로 자른다.
   집계는 거르기 전 전체에서 센다. 필터를 바꿔도 탭 옆 숫자가 흔들리면 안 된다. */
export function myPredictions(query: Query): Promise<MyPredictionList> {
  const pending = MY_ROWS.filter((r) => phaseOf(r.status) === 'PENDING')
  const judged = MY_ROWS.filter((r) => phaseOf(r.status) === 'JUDGED')
  const hit = judged.filter((r) => r.status === 'HIT').length

  const status = query.status as string | undefined
  const rows = status === 'PENDING' ? pending : status === 'JUDGED' ? judged : MY_ROWS

  const size = Number(query.size ?? 12)
  const cursor = query.cursor as string | undefined
  const start = cursor ? rows.findIndex((r) => r.id === cursor) + 1 : 0
  const page = rows.slice(start, start + size)
  const last = page[page.length - 1]
  const hasNext = last ? rows.indexOf(last) < rows.length - 1 : false

  return delay({
    items: page,
    nextCursor: hasNext && last ? last.id : null,
    hasNext,
    total: MY_ROWS.length,
    pendingCount: pending.length,
    judgedCount: judged.length,
    hitRate: judged.length ? Math.round((hit / judged.length) * 100) : null,
  }, 320)
}
