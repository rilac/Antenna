/* 예측 목업. **서버에 아직 API 가 없는 것만 남았다.**

   등록(POST /predictions)과 슬롯(GET /predictions/slots)은 ANT-PRED-01 이 들어와
   실제 호출로 넘어갔고, 그 목업은 지웠다. 남은 둘은 명세에 엔드포인트가 없다:

     GET /predictions/{id}                         예측 상세 (C-03)
     GET /stocks/{code}/predictions?phase=&cursor=  종목별 예측 목록

   둘 다 §5 게이팅을 재현한다 — 판정 완료는 전부 공개하고, 미판정은 근거만 잠근다.
   화면이 잠금 카드와 구독 CTA 를 제대로 그리는지 눈으로 확인하려는 것이다. */
import { phaseOf } from '../predictions'
import type {
  Direction, Horizon, PredictionDetail,
  PredictionStatus, StockPrediction, StockPredictionList,
} from '../predictions'

const delay = <T,>(value: T, ms: number) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(value), ms))


/* ── 이 종목에 걸린 남의 예측 ─────────────────────────────
   §5 게이팅을 실제로 재현한다. 판정 완료는 전부 공개하고, 미판정은 잠가서
   direction·targetPrice 를 비운 채 내린다. 화면이 잠금 카드와 구독 CTA 를
   제대로 그리는지 눈으로 확인하려는 것이다.

   구독 중인 채널이 하나 있다고 가정해 잠기지 않은 미판정도 한 건 둔다 —
   "잠긴 것" 과 "구독해서 보이는 것" 이 같은 목록에 섞이는 모습을 봐야 한다. */
/* 채널 목업(mock/channels.ts)이 이 표를 읽는다. 표를 두 곳에 두면 같은 사람이
   목록에서는 "반도체존버", 채널 화면에서는 다른 이름으로 뜬다 — 실제로 그랬다. */
/* userId 는 **숫자 문자열**이다. 서버 userId 가 long 이라 'u2' 같은 값을 보내면
   실제 API 가 400 INVALID_REQUEST 를 낸다 — 채널 화면이 리포트 목록만 실제로
   부르는데 거기서 그렇게 터졌다.

   **작성자 이름을 눌러 채널로 가면 404 다.** 채널 목업(mock/channels.ts)이
   네 명('1'·'2'·'3'·'9')만 알기 때문인데, 그 파일은 채널 화면(E-02)의 것이라
   여기서 고치지 않는다. 그쪽에 알린다. */
export const AUTHORS = [
  { userId: '101', nickname: '데이터로보는사람', accuracy: 71.4, subscribed: true },
  { userId: '102', nickname: '반도체존버', accuracy: 58.2, subscribed: false },
  { userId: '103', nickname: '레드와이어사지마라했다', accuracy: 64.9, subscribed: false },
  { userId: '104', nickname: '분기실적만본다', accuracy: null, subscribed: false },
  { userId: '105', nickname: '차트는거들뿐', accuracy: 49.1, subscribed: false },
  { userId: '106', nickname: '외국인수급추적', accuracy: 77.0, subscribed: false },
  { userId: '107', nickname: '적립식개미', accuracy: 52.6, subscribed: false },
  { userId: '108', nickname: 'HBM만판다', accuracy: 68.3, subscribed: false },
  { userId: '109', nickname: '메모리사이클', accuracy: 61.5, subscribed: false },
  { userId: '110', nickname: '공시읽는남자', accuracy: 55.8, subscribed: false },
]

const HORIZONS: Horizon[] = [7, 14, 30, 90]
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
    d.setUTCDate(d.getUTCDate() + HORIZONS[i % 4])

    return {
      id: `${code}-pr${i + 1}`,
      author: { userId: a.userId, nickname: a.nickname },
      accuracy: a.accuracy,
      status,
      horizon: HORIZONS[i % 4],
      createdAt: `2026-08-${String(10 + i).padStart(2, '0')}T09:30:00+09:00`,
      dueDate: d.toISOString().slice(0, 10),
      locked,
      direction,
      /* 잠겨도 목표가는 온다(2026-09-08 결정). 잠기는 것은 근거뿐이다 */
      targetPrice: target,
      /* 등록 직후(BASE)는 기준가가 아직 없다. 0 으로 채우지 않는다 */
      basePrice: status === 'BASE' ? null : base,
      closePrice: close,
      errorRate: close === null ? null : Math.round(((close - target) / target) * 1000) / 10,
      /* 잠금은 근거에만 걸린다. 구독 CTA 를 그리려면 채널을 알아야 한다 */
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

/* C-03 상세 목업이 없는 id 로 들어왔을 때 만들어 낼 씨앗.

   **C-02 의 MyPrediction 을 빌려 쓰지 않는다.** 그 타입은 이제 실제 서버
   DTO(MyPredictionItemResponse)를 따라가므로, 목업이 거기 매달리면 서버가
   필드를 바꿀 때 상세 목업까지 함께 깨진다. 필요한 만큼만 따로 적는다.

   상태 분포를 골고루 둔다. 등록 직후(BASE)는 dday·errorRate 가 둘 다 비어 있고,
   판정 대기(OPEN)는 dday 만, 판정 완료(HIT/MISS)는 errorRate 만 있다. */
type DetailSeed = {
  id: string
  stockCode: string
  stockName: string | null
  direction: Direction
  targetPrice: number
  horizon: Horizon
  status: PredictionStatus
  dday: number | null
  errorRate: number | null
  settleDate: string
}

const SEEDS: DetailSeed[] = [
  { id: 'p1', stockCode: '005930', stockName: '삼성전자', direction: 'UP', targetPrice: 78000, horizon: 30, status: 'BASE', dday: null, errorRate: null, settleDate: '2026-09-28' },
  { id: 'p2', stockCode: '000660', stockName: 'SK하이닉스', direction: 'UP', targetPrice: 215000, horizon: 14, status: 'OPEN', dday: 7, errorRate: null, settleDate: '2026-09-14' },
  { id: 'p3', stockCode: '035420', stockName: 'NAVER', direction: 'DOWN', targetPrice: 165000, horizon: 7, status: 'OPEN', dday: 2, errorRate: null, settleDate: '2026-09-07' },
  { id: 'p4', stockCode: '373220', stockName: 'LG에너지솔루션', direction: 'UP', targetPrice: 380000, horizon: 90, status: 'OPEN', dday: 58, errorRate: null, settleDate: '2026-11-23' },
  { id: 'p5', stockCode: '005380', stockName: '현대차', direction: 'UP', targetPrice: 260000, horizon: 30, status: 'HIT', dday: null, errorRate: 1.2, settleDate: '2026-08-24' },
  { id: 'p6', stockCode: '068270', stockName: '셀트리온', direction: 'DOWN', targetPrice: 180000, horizon: 14, status: 'HIT', dday: null, errorRate: -2.4, settleDate: '2026-08-17' },
  { id: 'p7', stockCode: '051910', stockName: 'LG화학', direction: 'UP', targetPrice: 420000, horizon: 30, status: 'MISS', dday: null, errorRate: -11.6, settleDate: '2026-08-10' },
  { id: 'p8', stockCode: '105560', stockName: 'KB금융', direction: 'DOWN', targetPrice: 80000, horizon: 7, status: 'MISS', dday: null, errorRate: 9.3, settleDate: '2026-08-03' },
  { id: 'p9', stockCode: '000270', stockName: '기아', direction: 'UP', targetPrice: 108000, horizon: 14, status: 'HIT', dday: null, errorRate: 0.4, settleDate: '2026-07-27' },
  { id: 'p10', stockCode: '006400', stockName: '삼성SDI', direction: 'DOWN', targetPrice: 300000, horizon: 30, status: 'MISS', dday: null, errorRate: 6.8, settleDate: '2026-07-20' },
  { id: 'p11', stockCode: '247540', stockName: '에코프로비엠', direction: 'DOWN', targetPrice: 150000, horizon: 90, status: 'HIT', dday: null, errorRate: -1.9, settleDate: '2026-07-13' },
  { id: 'p12', stockCode: '055550', stockName: '신한지주', direction: 'UP', targetPrice: 52000, horizon: 7, status: 'HIT', dday: null, errorRate: 2.1, settleDate: '2026-07-06' },
  { id: 'p13', stockCode: '207940', stockName: '삼성바이오로직스', direction: 'UP', targetPrice: 1020000, horizon: 30, status: 'MISS', dday: null, errorRate: -8.2, settleDate: '2026-06-29' },
  /* 이름이 아직 안 오는 갈래 — 화면이 종목코드로 대체하는지 본다 */
  { id: 'p14', stockCode: '005490', stockName: null, direction: 'DOWN', targetPrice: 390000, horizon: 14, status: 'HIT', dday: null, errorRate: -0.7, settleDate: '2026-06-22' },
]

/* ── 예측 상세 (C-03) ─────────────────────────────────────
   잠금 두 갈래를 실제로 재현한다. 화면이 §5 를 지키는지 눈으로 확인하려는 것이다.

     내 예측(p1~)        전부 열려 있다. 근거 본문까지 보인다
     남의 예측(o-open)   미판정이어도 **내용은 열려 있다** — 근거 본문만 잠긴다
     남의 예측(o-hit)    판정 완료 → 전체 공개. 역시 **근거 본문은 잠긴다**
                         (만기 리빌 후에도 구독자 전용이다)

   2026-09-08 결정 — 예측가를 고를 근거가 되어야 하므로 방향·목표가까지 공개한다.
   구독으로 사는 것은 판단의 이유(근거 본문) 하나뿐이다.

   어느 경우에도 commitHash · 서명 주소 · 앵커는 내린다 — 잠금 대상이 아니다. */
const ANCHOR_CONFIRMED = {
  id: 412, businessDate: '2026-08-25', merkleRoot: '0x9f2c1a77b4e0d3568a1e4c9b70d2f83ac6154e9b28d7f0a3c5b6e1928d4f70ab',
  commitCount: 138, status: 'CONFIRMED' as const,
  txHash: '0x3b8e1d05f7a2c9461b0d8e5372af49c1806e2d5b7f30a94c1e6b285d7013fa9c',
  blockNumber: 7418552, confirmedAt: '2026-08-26T04:12:31Z',
  contractAddress: '0x5F0a4c31B7e29D6c8134aE59027bD1cF3806e4A2', chainId: 11155111,
}
const ANCHOR_PENDING = {
  ...ANCHOR_CONFIRMED, id: 419, businessDate: '2026-09-04',
  status: 'PENDING' as const, txHash: null, blockNumber: null, confirmedAt: null,
}

const EVIDENCE = [
  { id: 59301, body: '반도체 전방 수요 지표가 2개 분기 연속 개선됐습니다. 가동률이 함께 오르면 고정비 부담이 줄어 영업이익률에 먼저 나타납니다.' },
  { id: 59302, body: '공급이 제한된 품목의 비중이 높아, 원가가 오를 때 판가로 옮길 여지가 경쟁사 대비 큽니다.' },
]

const DETAILS: Record<string, PredictionDetail> = {
  /* 내 예측 · 판정 대기 — 진행률이 그려지는 경로 */
  p2: {
    id: 'p2', stockCode: '000660', stockName: 'SK하이닉스',
    author: { userId: 'me', nickname: '레드와이어사지마라했다' },
    status: 'OPEN', horizon: 14, createdAt: '2026-08-28T09:31:00+09:00',
    settleDate: '2026-09-14', dday: 7,
    locked: false, direction: 'UP', targetPrice: 215000,
    basePrice: 188700, settlePrice: null, errorRate: null,
    lastClose: { close: 198500, asOf: '2026-08-31' },
    noteLocked: false,
    note: '메모리 사이클이 바닥을 지났다고 본다. HBM 물량이 확정돼 있어 가동률이 먼저 오르고, 그 다음 분기에 판가가 따라올 것으로 판단했다.\n\n다만 환율이 1,300원 아래로 내려가면 원화 환산 매출이 눌려 목표가 도달이 늦어질 수 있다.',
    evidencePoints: EVIDENCE,
    commitHash: '0x7d41e9a3c2b58f0716d4a9c3e58b0271f4a6d9c3b5e70128a4f6c9b3d5e70142',
    signerAddress: '0x8A31f4C2b90E5d7163aC48b920D5f0e7B4c92D1a',
    anchor: ANCHOR_PENDING, channelId: null,
  },

  /* 내 예측 · 적중 — 결과와 오차가 다 있는 경로 */
  p5: {
    id: 'p5', stockCode: '005380', stockName: '현대차',
    author: { userId: 'me', nickname: '레드와이어사지마라했다' },
    status: 'HIT', horizon: 30, createdAt: '2026-07-24T10:02:00+09:00',
    settleDate: '2026-08-24', dday: null,
    locked: false, direction: 'UP', targetPrice: 260000,
    basePrice: 238500, settlePrice: 263100, errorRate: 1.2,
    lastClose: { close: 263100, asOf: '2026-08-24' },
    noteLocked: false,
    note: '판매 대수보다 믹스 개선이 실적을 끌어올린다고 봤다. 고수익 차종 비중이 올라가는 흐름이 두 분기 이어졌다.',
    evidencePoints: [EVIDENCE[1]],
    commitHash: '0x2c58f0716d4a9c3e58b0271f4a6d9c3b5e70128a4f6c9b3d5e701427d41e9a3',
    signerAddress: '0x8A31f4C2b90E5d7163aC48b920D5f0e7B4c92D1a',
    anchor: ANCHOR_CONFIRMED, channelId: null,
  },

  /* 남의 예측 · 미판정 + 비구독 → 전체 잠금.
     그래도 작성자·기간·커밋·앵커는 보인다(§5 "존재 자체는 공개") */
  'o-open': {
    id: 'o-open', stockCode: '005930', stockName: '삼성전자',
    author: { userId: '102', nickname: '반도체존버' },
    status: 'OPEN', horizon: 30, createdAt: '2026-08-20T11:40:00+09:00',
    settleDate: '2026-09-18', dday: 11,
    /* 남의 미판정 예측이지만 **내용은 열려 있다**(2026-09-08 결정).
       잠기는 것은 근거 본문뿐이라 noteLocked 만 참이다. */
    locked: false, direction: 'UP', targetPrice: 82000,
    basePrice: 78600, settlePrice: null, errorRate: null,
    lastClose: { asOf: '2026-09-07', close: 80100 },
    noteLocked: true, note: null, evidencePoints: [],
    commitHash: '0x58b0271f4a6d9c3b5e70128a4f6c9b3d5e701427d41e9a3c2c58f0716d4a9c3e',
    signerAddress: '0x4Bc7e19aD05f2C863b0e4719aD05f2C861e0B37d',
    anchor: ANCHOR_CONFIRMED, channelId: 'u2',
  },

  /* 남의 예측 · 판정 완료 → 내용은 전체 공개. 근거 본문만 여전히 잠긴다 */
  'o-hit': {
    id: 'o-hit', stockCode: '035420', stockName: 'NAVER',
    author: { userId: '101', nickname: '데이터로보는사람' },
    status: 'HIT', horizon: 14, createdAt: '2026-07-30T09:12:00+09:00',
    settleDate: '2026-08-13', dday: null,
    locked: false, direction: 'DOWN', targetPrice: 165000,
    basePrice: 178200, settlePrice: 163900, errorRate: -0.7,
    lastClose: { close: 163900, asOf: '2026-08-13' },
    /* 만기가 지났어도 근거 본문은 구독자 전용이다 — payload 에 noteHash 만 들어간다 */
    noteLocked: true, note: null,
    evidencePoints: EVIDENCE,
    commitHash: '0x9c3b5e70128a4f6c9b3d5e701427d41e9a3c2c58f0716d4a9c3e58b0271f4a6d',
    signerAddress: '0x91Ee0b7C42a58d0361fB9e47205cD8a3F0b6142e',
    anchor: ANCHOR_CONFIRMED, channelId: 'u1',
  },
}

const notFoundPrediction = (id: string) =>
  Promise.reject(Object.assign(new Error('PREDICTION_NOT_FOUND'), {
    status: 404, code: 'PREDICTION_NOT_FOUND',
    message: `예측 ${id} 을(를) 찾을 수 없습니다`,
  }))

/** 만기까지 남은 일수. 목록 응답에 dday 가 없어 만기일에서 센다 */
const ddayTo = (dueDate: string) =>
  Math.max(0, Math.round((Date.parse(dueDate) - Date.parse('2026-09-08')) / 86400000))

export function predictionDetail(id: string): Promise<PredictionDetail> {
  const hit = DETAILS[id]
  if (hit) return delay(hit, 300)

  /* 내 예측 목록의 다른 id 로 들어오면 그 줄을 바탕으로 만들어 준다 —
     목록에서 아무 줄이나 눌러도 상세가 뜨게 해서 흐름을 확인할 수 있다.

     **서버 id(숫자)도 받는다.** 내 예측 목록은 이미 실제 API 라 거기서 누른
     id 는 long 이고, 목업 픽스처에는 없다. 404 로 막으면 실제 목록에서 상세로
     넘어가는 길이 끊기고, 화면이 실제로 읽는 앵커 상태(GET .../proof)도 볼 수
     없다. GET /predictions/{id} 가 붙으면 이 함수 전체가 사라진다. */
  /* 종목 상세의 예측 목록에서 들어온 건({코드}-pr{n}). 그 목록과 같은 생성기를
     써야 작성자·방향·목표가가 목록과 어긋나지 않는다 — 전에는 여기서 막혀
     목록의 어느 줄을 눌러도 PREDICTION_NOT_FOUND 였다. */
  const fromStock = /^([0-9]{6})-pr[0-9]+$/.exec(id)
  if (fromStock) {
    const found = buildRows(fromStock[1]).find((r) => r.id === id)
    if (!found) return notFoundPrediction(id)
    const settled = phaseOf(found.status) === 'JUDGED'
    const basePrice = found.basePrice
    return delay<PredictionDetail>({
      /* 목록 응답에는 종목명이 없다 — 목록이 이미 종목 화면 안에 있어서다.
         코드는 id 에서 되읽고, 이름은 상세 헤더가 따로 채운다. */
      id: found.id, stockCode: fromStock[1], stockName: null,
      author: found.author, status: found.status, horizon: found.horizon,
      createdAt: found.createdAt, settleDate: found.dueDate,
      dday: settled ? null : ddayTo(found.dueDate),
      /* 예측 내용은 잠기지 않는다. 잠기는 것은 근거 본문뿐이고, 그건 구독 여부를
         따른다 — 목록에서 잠겨 보이던 사람이 상세에서 열리면 안 된다. */
      locked: false,
      direction: found.direction, targetPrice: found.targetPrice, basePrice,
      settlePrice: settled ? found.closePrice : null,
      errorRate: found.errorRate,
      lastClose: basePrice === null || found.targetPrice === null ? null
        : { close: Math.round((basePrice + found.targetPrice) / 2), asOf: '2026-09-07' },
      noteLocked: found.locked,
      note: found.locked ? null
        : '공시와 실적 발표만 근거로 씁니다. 이번 건은 분기 가이던스 상향을 보고 걸었습니다.',
      evidencePoints: found.locked ? [] : [EVIDENCE[0]],
      commitHash: '0x' + [...found.id].map((c) => c.charCodeAt(0).toString(16)).join('').padEnd(64, 'b41e').slice(0, 64),
      signerAddress: '0x4Bc7e19aD05f2C863b0e4719aD05f2C861e0B37d',
      anchor: found.status === 'BASE' ? null : ANCHOR_CONFIRMED,
      channelId: found.author.userId,
    }, 300)
  }

  const row = SEEDS.find((r) => r.id === id)
    ?? (/^\d+$/.test(id) ? { ...SEEDS[1], id } : undefined)
  if (!row) return notFoundPrediction(id)

  const judged = phaseOf(row.status) === 'JUDGED'
  const base = row.status === 'BASE' ? null : Math.round(row.targetPrice * 0.92)
  return delay<PredictionDetail>({
    id: row.id, stockCode: row.stockCode, stockName: row.stockName,
    author: { userId: 'me', nickname: '레드와이어사지마라했다' },
    status: row.status, horizon: row.horizon, createdAt: '2026-08-10T09:30:00+09:00',
    settleDate: row.settleDate, dday: row.dday,
    locked: false, direction: row.direction, targetPrice: row.targetPrice,
    basePrice: base,
    settlePrice: judged && base !== null
      ? Math.round(row.targetPrice * (1 + (row.errorRate ?? 0) / 100))
      : null,
    errorRate: row.errorRate,
    lastClose: base === null ? null : { close: Math.round((base + row.targetPrice) / 2), asOf: '2026-08-31' },
    noteLocked: false,
    note: '등록할 때 남긴 판단입니다. 목록에서 들어온 건이라 목업이 본문을 만들어 넣었습니다.',
    evidencePoints: [EVIDENCE[0]],
    /* id 로 16진수를 만든다. id 를 그대로 넣으면 'p' 가 섞여 해시가 아니게 된다 */
    commitHash: '0x' + [...row.id].map((c) => c.charCodeAt(0).toString(16)).join('').padEnd(64, 'a3f7').slice(0, 64),
    signerAddress: '0x8A31f4C2b90E5d7163aC48b920D5f0e7B4c92D1a',
    anchor: row.status === 'BASE' ? null : ANCHOR_CONFIRMED,
    channelId: null,
  }, 300)
}
