/* 목업 응답. /channels 아래에 실제로 열린 것은 {userId}/reports 하나뿐이라
   나머지 넷을 여기서 대신한다(2026-09-08 확인).

   지우는 절차 — api/insight.ts 와 같다
   1. api/channels.ts 의 MOCK 을 false 로 바꾼다
   2. 이 파일과 channels.ts 의 `if (MOCK)` 네 줄, import 한 줄을 지운다
   화면 코드는 손대지 않는다.

   값은 지어냈지만 형태는 지어내지 않았다 — Subscription · PublisherFee 엔티티의
   컬럼과 Operation.Kind.SUBSCRIBE 를 그대로 따른다.

   일부러 세 사람을 넣었다. 화면에서 서로 다른 카드를 그려야 하는 경우들이다.
     1  미구독   → 구독 CTA · 미판정 예측은 잠금 카드
     2  ACTIVE   → 만료일·자동 갱신 표시 · 예측 전부 열림 · 박제된 옛 가격
     3  PENDING  → "결제 확인 중". ACTIVE 와 같게 그리면 안 되는 상태다

   내 채널은 이 표에 없다 — 아래 mine() 이 실제 내 id 로 만든다. */
import { api } from '../client'
import { ApiError } from '../errors'
import type { MeResponse } from '../../auth/session'
import type {
  Backtest, Channel, ChannelPrediction, ChannelPredictionPage, SubscribeBody,
} from '../channels'

/** 목이라도 네트워크처럼 비동기여야 로딩 상태가 실제로 지나간다 */
const LATENCY_MS = 240

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY_MS))
}

/** 12,000 ANT. 정수 ANT 문자열이다(decimals 0) — 천 단위 구분이 화면에 실제로 보이도록 네 자리 이상으로 둔다 */
const FEE = '12000'

function base(userId: string, nickname: string): Omit<Channel, 'mySubscription' | 'isMe'> {
  return {
    userId,
    nickname,
    avatarUrl: '/assets/character/white_ant/antenna-profile.png',
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
      // 채널은 지금 12,000 ANT 지만 나는 10,000 에 결제했다 — 박제 규칙이 화면에 보이는지 확인용
      paidFee: '10000',
    },
    isMe: false,
  },
  '3': {
    ...base('3', '차트말고실적'),
    mySubscription: { status: 'PENDING', expiresAt: null, autoRenew: true, paidFee: FEE },
    isMe: false,
  },
}

/* ── 내 채널 ──────────────────────────────────────────────
   채널은 따로 만드는 것이 아니다. ERD 에 channels 테이블이 없고 publisher_fees 의
   publisher_id 가 users.id 를 직접 가리킨다 — **가입한 사람은 이미 채널이다.**
   그래서 서버가 열리면 GET /channels/{내 id} 는 당연히 내 채널을 준다.

   목업은 그렇지 않았다. 고정 id 세 개만 알아서 내 실제 id 가 1~3 중 하나면
   **남의 채널이 열렸고**(마이페이지의 "내 채널 보기"가 그랬다) 그 밖이면 404 였다.
   실제 id 를 물어서 그게 나면 내 채널을 만들어 준다.

   /users/me 는 실제로 열려 있는 API 다. 목업이 지어내는 값이 아니다. */
let mePromise: Promise<MeResponse> | null = null

function fetchMe(): Promise<MeResponse> {
  /* 실패를 캐시하면 로그인 뒤에도 계속 실패한다 — 거절된 약속은 지우고 다시 묻는다 */
  mePromise ??= api.get<MeResponse>('/users/me').catch((e: unknown) => {
    mePromise = null
    throw e
  })
  return mePromise
}

async function mine(userId: string): Promise<Channel | null> {
  // 비로그인이면 물을 것이 없다. 그 경우는 그냥 "내가 아니다"로 둔다
  const me = await fetchMe().catch(() => null)
  if (!me || String(me.id) !== userId) return null

  return {
    ...base(userId, me.nickname ?? '나'),
    bio: me.introduce,
    /* 구독료를 한 번도 정하지 않은 상태다. **0 으로 두지 않는다** — 무료 채널로
       읽힌다. E-04 내 채널 구독료(S15P21A507-186)도 같은 판단으로 미설정을
       "아직 정하지 않았습니다" 로 그린다. 행이 없는 것이 곧 미설정이다. */
    fee: null,
    /* /users/me 가 주지 않는 것은 비워 둔다. 지어내면 내 채널만 남과 다른 값을
       보여주게 되고, 서버가 열렸을 때 무엇이 바뀐 건지 알 수 없다 */
    interests: [],
    externalLinks: [],
    mySubscription: { status: null, expiresAt: null, autoRenew: false, paidFee: null },
    isMe: true,
  }
}

/** 목업 표보다 **내가 먼저다** — 내 id 가 표의 자리와 겹쳐도 남의 채널이 나오지 않는다 */
async function lookup(userId: string): Promise<Channel | null> {
  return (await mine(userId)) ?? CHANNELS[userId] ?? null
}

export async function channel(userId: string): Promise<Channel> {
  const found = await lookup(userId)
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

export async function predictions(
  userId: string,
  cursor?: string | number | null,
): Promise<ChannelPredictionPage> {
  const found = await lookup(userId)
  const open = found?.isMe === true || found?.mySubscription.status === 'ACTIVE'
  const items = [...JUDGED, ...(open ? PENDING : PENDING.map(lock))]
  // 목은 한 쪽뿐이다. 커서를 받는 자리만 실제 계약대로 열어 둔다.
  return delay({ items: cursor ? [] : items, nextCursor: null, hasNext: false })
}

export async function backtest(userId: string): Promise<Backtest> {
  const found = await lookup(userId)
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
