/* 목업 응답. /channels 아래에 실제로 열린 것은 {userId}/reports 하나뿐이라
   나머지 넷을 여기서 대신한다(2026-09-08 확인).

   지우는 절차 — api/insight.ts 와 같다
   1. api/channels.ts 의 MOCK 을 false 로 바꾼다
   2. 이 파일과 channels.ts 의 `if (MOCK)` 네 줄, import 한 줄을 지운다
   화면 코드는 손대지 않는다.

   값은 지어냈지만 형태는 지어내지 않았다 — Subscription · PublisherFee 엔티티의
   컬럼과 Operation.Kind.SUBSCRIBE 를 그대로 따른다.

   일부러 네 사람을 넣었다. 화면에서 서로 다른 카드를 그려야 하는 경우들이다.
     1  미구독   → 구독 CTA · 미판정 예측은 잠금 카드
     2  ACTIVE   → 만료일·자동 갱신 표시 · 예측 전부 열림 · 박제된 옛 가격
     3  PENDING  → "결제 확인 중". ACTIVE 와 같게 그리면 안 되는 상태다
     9  내 채널  → 구독 카드 대신 설정 안내 · 지표 표본 0 */
import { ApiError } from '../errors'
import type {
  Backtest, Channel, ChannelPrediction, ChannelPredictionPage, SubscribeBody,
} from '../channels'

/** 목이라도 네트워크처럼 비동기여야 로딩 상태가 실제로 지나간다 */
const LATENCY_MS = 240

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY_MS))
}

/** 12 ANT. wei 라 18자리다 — 화면이 문자열을 숫자로 바꾸지 않는지 보려고 큰 값을 둔다 */
const FEE = '12000000000000000000'

function base(userId: string, nickname: string): Omit<Channel, 'mySubscription' | 'isMe'> {
  return {
    userId,
    nickname,
    avatarUrl: '/assets/character/white_ant/antena-profile.png',
    bio: '반도체·2차전지 중심으로 실적 시즌마다 방향을 겁니다. 근거는 공시와 실적 발표만 씁니다.',
    interests: ['반도체', '2차전지', '자동차'],
    externalLinks: [
      { label: '블로그', url: 'https://example.com/blog' },
      { label: '유튜브', url: 'https://example.com/yt' },
    ],
    fee: FEE,
    stats: { hitRate: 62.5, doneCount: 48, avgError: 3.1 },
  }
}

const CHANNELS: Record<string, Channel> = {
  '1': {
    ...base('1', '반도체관측소'),
    mySubscription: { status: null, expiresAt: null, autoRenew: false, paidFee: null },
    isMe: false,
  },
  '2': {
    ...base('2', '실적읽는사람'),
    mySubscription: {
      status: 'ACTIVE',
      expiresAt: '2026-10-02T00:00:00Z',
      autoRenew: true,
      // 채널은 지금 12 ANT 지만 나는 10 에 결제했다 — 박제 규칙이 화면에 보이는지 확인용
      paidFee: '10000000000000000000',
    },
    isMe: false,
  },
  '3': {
    ...base('3', '차트말고실적'),
    mySubscription: { status: 'PENDING', expiresAt: null, autoRenew: true, paidFee: FEE },
    isMe: false,
  },
  '9': {
    ...base('9', '안테나'),
    // 지표 표본이 없는 채널. 0% 로 그리지 않는지 확인용이다
    stats: { hitRate: null, doneCount: 0, avgError: null },
    mySubscription: { status: null, expiresAt: null, autoRenew: false, paidFee: null },
    isMe: true,
  },
}

export function channel(userId: string): Promise<Channel> {
  const found = CHANNELS[userId]
  if (!found) {
    return Promise.reject(
      new ApiError({ code: 'USER_NOT_FOUND', message: 'mock', field: 'userId' }, 404),
    )
  }
  return delay(found)
}

/* 예측 목록. 구독자가 아니면 미판정 건이 잠겨 온다 — 종목·방향은 남고 목표가만 빠진다
   (서버 PredictionCardResponse 규칙, Jira S15P21A507-70). */
const JUDGED: ChannelPrediction[] = [
  {
    id: 'p1', stockCode: '005930', stockName: '삼성전자', direction: 'UP',
    status: 'HIT', targetPrice: 78000, errorRate: 1.79, horizon: 10,
    dueDate: '2026-09-04', locked: false,
  },
  {
    id: 'p2', stockCode: '000660', stockName: 'SK하이닉스', direction: 'DOWN',
    status: 'MISS', targetPrice: 214000, errorRate: 6.4, horizon: 20,
    dueDate: '2026-08-28', locked: false,
  },
]

const PENDING: ChannelPrediction[] = [
  {
    id: 'p3', stockCode: '373220', stockName: 'LG에너지솔루션', direction: 'UP',
    status: 'OPEN', targetPrice: 412000, errorRate: null, horizon: 20,
    dueDate: '2026-09-25', locked: false,
  },
  {
    id: 'p4', stockCode: '005380', stockName: '현대차', direction: 'DOWN',
    status: 'BASE', targetPrice: 246000, errorRate: null, horizon: 5,
    dueDate: null, locked: false,
  },
]

/** 잠긴 모양 — 목표가만 빠지고 나머지는 그대로다 */
const lock = (p: ChannelPrediction): ChannelPrediction => ({ ...p, targetPrice: null, locked: true })

export function predictions(
  userId: string,
  cursor?: string | number | null,
): Promise<ChannelPredictionPage> {
  const found = CHANNELS[userId]
  const open = found?.isMe === true || found?.mySubscription.status === 'ACTIVE'
  const items = [...JUDGED, ...(open ? PENDING : PENDING.map(lock))]
  // 목은 한 쪽뿐이다. 커서를 받는 자리만 실제 계약대로 열어 둔다.
  return delay({ items: cursor ? [] : items, nextCursor: null, hasNext: false })
}

export function backtest(userId: string): Promise<Backtest> {
  const found = CHANNELS[userId]
  // 판정 표본이 없는 채널은 결과를 만들지 않는다 — 0% 로 그리면 "손실 없음" 으로 읽힌다
  if (found?.stats.doneCount === 0) {
    return delay({
      months: 3, principal: 10_000_000, returnRate: 0,
      benchmarkRate: null, sampleCount: 0, computedAt: '2026-09-08T00:00:00Z',
    })
  }
  return delay({
    months: 3,
    principal: 10_000_000,
    returnRate: 14.2,
    benchmarkRate: 4.8,
    sampleCount: 48,
    computedAt: '2026-09-08T00:00:00Z',
  })
}

export function subscribe(userId: string, body: SubscribeBody): Promise<{ operationId: string }> {
  if (!body.signature) {
    return Promise.reject(
      new ApiError({ code: 'INVALID_SIGNATURE', message: 'mock', field: 'signature' }, 400),
    )
  }
  // 서버가 op_ + UUID 로 내린다(Operation 엔티티 주석). 형식을 맞춰 둔다.
  return delay({ operationId: `op_${userId}-${Date.now().toString(16)}` })
}
