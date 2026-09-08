/* AI 브리핑 목업 — M-10. ai_briefings 에 행이 생기면 이 파일을 지운다.

   값은 서버 응답 스키마를 글자 그대로 따른다. 스키마에 없는 필드는 보기 좋더라도
   넣지 않는다 — 전에 종목 상세 목업이 tone · summary · computedAt 을 지어냈다가
   서버에 없는 배지를 화면에 그리고 있었다.

   본문에 무엇을 쓰지 않는가
   AiBriefing 엔티티 주석이 정한 것을 목업도 지킨다 — "수치는 결정론적 코드가
   계산하고 여기에는 서술 문단만 담는다. 종목 추천·미래 예측 문구는 넣지 않는다."
   그래서 여기 본문에도 매수·매도 권유나 목표가가 없다. 목업이 실제보다 화려하면
   붙이는 날 화면이 초라해 보이고, 무엇보다 없는 기능을 약속하게 된다.

   id 를 표로 들지 않고 계산하는 이유
   M-10 은 ?briefing={id} 딥링크로도 열린다. 그때는 목록을 부른 적이 없으므로
   목록이 채워 둔 표에 기대면 새로고침 한 번에 "없는 브리핑" 이 된다. 그래서
   id 안에 대상을 담아 두고 상세에서 되읽는다. */
import { ApiError, ERROR_CODE } from '../errors'
import type { BriefingDetail, BriefingItem } from '../briefings'
import { stockName } from './stockDetail'

const delay = <T,>(value: T, ms = 300) =>
  new Promise<T>((resolve) => setTimeout(() => resolve(value), ms))

/** 목록·상세가 함께 기준하는 영업일. 다른 목업과 같은 날이어야 한다 */
const BASE_DATE = '2026-08-31'

/* ── MARKET ──────────────────────────────────────────────
   서버는 한 날짜에 MARKET 을 한 건만 두려 한다(BriefingService 주석). 여기 셋을
   두는 것은 홈 띠(B-01)가 문장을 돌려 보여주는 화면이라 그 동작을 눈으로 봐야 하기
   때문이다. **실제로는 한 건만 올 가능성이 높다** — 띠는 한 건이어도 그대로 뜬다. */
const MARKET: { id: number; headline: string; body: string }[] = [
  {
    id: 1,
    headline: '반도체 수출 회복이 지수를 끌어올렸습니다',
    body: `이번 주 지수 상승분의 상당 부분이 반도체 업종에서 나왔습니다. 월간 수출 통계에서 메모리 단가가 넉 달 만에 반등한 것이 확인되면서, 그동안 재고 조정 국면으로 읽히던 구간에 대한 해석이 갈리기 시작했습니다.

다만 상승이 업종 전반으로 고르게 퍼지지는 않았습니다. 후공정과 소재 쪽은 같은 기간 지수를 밑돌았고, 대형주 두어 종목이 지수 기여의 대부분을 차지했습니다. 지수만 보고 업종 전체가 돌아섰다고 읽기는 이른 구간입니다.

외국인 순매수가 닷새 이어졌으나 규모는 직전 상승 국면의 절반 수준입니다. 수급이 방향을 바꿨다기보다, 비중을 되돌리는 과정으로 보는 편이 지금 자료와 어긋나지 않습니다.`,
  },
  {
    id: 2,
    headline: '2차전지는 유럽 보조금 축소 소식에 조정받았습니다',
    body: `유럽 일부 국가가 전기차 구매 보조금 축소 일정을 앞당긴다는 보도가 나온 뒤, 2차전지 밸류체인 전반이 함께 밀렸습니다. 셀 업체보다 소재 업체의 낙폭이 컸습니다.

보조금은 최종 수요에 걸리는 변수라 셀·소재·장비에 시차를 두고 전달됩니다. 이번 조정에서 세 구간이 같은 폭으로 움직인 것은 개별 실적 차이보다 업종 전체에 매겨진 할인율이 한꺼번에 조정됐다는 쪽에 가깝습니다.

수주 잔고가 공시된 업체들의 잔고 자체에는 아직 변동이 없습니다. 다음 분기 가이던스가 나오기 전까지는 이번 하락이 실적 변화를 반영한 것인지 확인할 자료가 없습니다.`,
  },
  {
    id: 3,
    headline: '환율 하락으로 수입 비중이 큰 업종에 여유가 생겼습니다',
    body: `원·달러 환율이 2주째 내리며 연중 고점 대비 낙폭을 키웠습니다. 원재료를 달러로 사 오는 업종은 원가 부담이 줄어드는 구간입니다.

다만 같은 환율이 수출 업종에는 반대로 걸립니다. 이번 주 상승 종목과 하락 종목을 업종별로 갈라 보면 수출 비중이 높은 쪽이 대체로 아래에 몰려 있어, 지수가 오른 날에도 종목 사이 온도차가 컸습니다.

환율 변화가 손익에 실리기까지는 통상 한 분기가 걸립니다. 지금 주가에 반영된 것은 실제 원가가 아니라 원가가 내려갈 것이라는 기대입니다.`,
  },
]

/* ── STOCK ───────────────────────────────────────────────
   종목 브리핑은 코드에서 만들어 낸다. 표로 들면 딥링크가 깨지기 때문이다(머리말).
   id = 100000 + 코드숫자 * 10 + 순번 이고, 상세에서 그대로 되읽는다. */
const STOCK_PER_CODE = 2
const STOCK_ID_BASE = 100000

const stockId = (code: string, n: number) => STOCK_ID_BASE + Number(code) * 10 + n

function decodeStockId(id: number): { code: string; n: number } | null {
  if (id < STOCK_ID_BASE) return null
  const rest = id - STOCK_ID_BASE
  const n = rest % 10
  if (n >= STOCK_PER_CODE) return null
  return { code: String((rest - n) / 10).padStart(6, '0'), n }
}

function stockBriefing(code: string, n: number): { headline: string; body: string } {
  const name = stockName(code) ?? code
  if (n === 0) {
    return {
      headline: `${name}, 최근 5거래일 종가 흐름과 업종 대비 위치`,
      body: `${name}의 최근 5거래일 종가는 같은 기간 소속 업종 지수와 대체로 같은 방향으로 움직였습니다. 개별 재료보다 업황 요인이 우세했던 구간으로 읽힙니다.

거래대금은 직전 20일 평균을 밑돌았습니다. 방향이 바뀌는 구간에서는 대개 거래대금이 먼저 늘어나는데, 이번에는 그 신호가 함께 나타나지 않았습니다.

공시된 자료 중 이번 구간의 움직임을 설명할 만한 개별 사건은 확인되지 않았습니다. 이 브리핑은 지난 자료를 정리한 것이며 앞으로의 방향을 말하지 않습니다.`,
    }
  }
  return {
    headline: `${name} 수급과 밸류에이션 점검`,
    body: `기관과 외국인의 최근 매매 방향이 엇갈렸습니다. 한쪽의 순매수가 다른 쪽의 순매도와 규모가 비슷해 지분 구조에는 큰 변화가 없었습니다.

밸류에이션 지표는 이익 추정치를 전제로 계산됩니다. 추정치가 내려가면 주가가 그대로여도 배수는 올라갑니다. 지금 배수만으로 비싸다·싸다를 가르기 어려운 이유입니다.

동일 업종 비교군과의 배수 차이는 지난 분기와 비슷한 수준을 유지하고 있습니다. 상대적인 위치가 바뀌지는 않았다는 뜻입니다.`,
  }
}

/* ── 응답 ────────────────────────────────────────────────── */

export function briefings(query: { scope?: string; stockCode?: string; date?: string }) {
  const date = query.date ?? BASE_DATE
  /* 서버는 date 를 안 주면 최신 하루치를 준다. 목업에는 BASE_DATE 하루뿐이라
     다른 날짜를 물으면 빈 목록이다 — 빈 상태 경로를 눈으로 볼 수 있다. */
  if (date !== BASE_DATE) return delay<{ items: BriefingItem[] }>({ items: [] })

  const items: BriefingItem[] = []
  if (query.scope !== 'STOCK') {
    items.push(...MARKET.map((m) => ({
      id: m.id, scope: 'MARKET' as const, stockCode: null, headline: m.headline, targetDate: date,
    })))
  }
  if (query.scope !== 'MARKET' && query.stockCode) {
    const code = query.stockCode
    for (let n = 0; n < STOCK_PER_CODE; n++) {
      items.push({
        id: stockId(code, n), scope: 'STOCK', stockCode: code,
        headline: stockBriefing(code, n).headline, targetDate: date,
      })
    }
  }
  return delay({ items })
}

export function briefing(id: number): Promise<BriefingDetail> {
  const market = MARKET.find((m) => m.id === id)
  if (market) {
    return delay({ id, headline: market.headline, body: market.body, targetDate: BASE_DATE })
  }
  const at = decodeStockId(id)
  if (at) {
    const b = stockBriefing(at.code, at.n)
    return delay({ id, headline: b.headline, body: b.body, targetDate: BASE_DATE })
  }
  /* 서버와 같은 404 를 만든다. 딥링크가 오래돼 사라진 브리핑을 가리키는 경우다 */
  return Promise.reject(
    new ApiError({ code: ERROR_CODE.BRIEFING_NOT_FOUND, message: '브리핑을 찾을 수 없습니다.', field: 'id' }, 404),
  )
}
