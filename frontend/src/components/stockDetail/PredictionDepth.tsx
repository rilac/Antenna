/* 이 종목의 예측 분포 — B-03 예측 탭 왼쪽. 설계 변경 2026-09-10.

   전에는 개별 예측을 목록으로 보여줬다. 이제는 **누가 걸었는지를 보여주지 않고**
   목표가 구간별 인원만 낸다. 개인은 작성자 채널(E-02)에서만 본다.

   주식 호가창을 본떴지만 다른 점이 하나 있다 — 우리에겐 상·하한가가 없다.
   그래서 절대가가 아니라 **전일 종가 대비 %** 로 자른다. 그래야 1,000원 종목과
   100만원 종목이 같은 개수의 구간으로 나뉜다.

   기준선은 전일 종가다. 실전 시세는 그것뿐이라 "현재가" 라고 적지 않는다(§7 legal).

   막대 길이는 최대 구간을 100 으로 본 상대값이다. 전체 합으로 나누면 구간이
   많을수록 모든 막대가 짧아져 분포 모양이 안 보인다. */
import { useBlock } from '../../api/useBlock'
import { getPredictionDistribution } from '../../api/predictions'
import type { DistributionBase, PredictionBucket, PredictionDistribution } from '../../api/predictions'
import ErrorState from '../state/ErrorState'
import { Panel } from './Block'

const won = (n: number) => n.toLocaleString('ko-KR')

/** 2026-09-09 → 2026.09.09. 화면 머리의 "2026.09.09 종가" 와 같은 표기다
    (StockDetail·InfoTab 이 쓰는 것과 같은 변환) */
const dot = (iso: string) => iso.replace(/-/g, '.')

/**
 * 구간 이름. **경계 한쪽만 적는다** — 기준가에서 먼 쪽이다.
 *
 * 양쪽을 다 적으면(`+5 ~ +10%`) 열 줄이 모두 물결표로 뒤덮여, 정작 눈이 좇아야 할
 * 숫자가 묻힌다. 위 구간은 상한, 아래 구간은 하한을 적으면 "여기까지 움직인다고
 * 본 사람들" 로 위아래가 같은 뜻이 되고 기준선을 사이에 두고 대칭이 된다.
 *
 * 아래쪽에 상한을 적으면 기준선 바로 밑 줄이 `0%` 가 되어, 바로 위 기준선과
 * 같은 값을 가리키는 줄이 생긴다.
 */
function bucketLabel(b: PredictionBucket) {
  if (b.fromPct === null) return `${b.toPct}% 미만`
  if (b.toPct === null) return `+${b.fromPct}% 초과`
  const edge = b.fromPct >= 0 ? b.toPct : b.fromPct
  return edge > 0 ? `+${edge}%` : `${edge}%`
}

/** 구간의 실제 가격대. 열린 구간은 한쪽만 적는다 */
function priceLabel(b: PredictionBucket) {
  if (b.fromPrice === null) return `${won(b.toPrice as number)}원 미만`
  if (b.toPrice === null) return `${won(b.fromPrice)}원 이상`
  return `${won(b.fromPrice)} ~ ${won(b.toPrice)}`
}

/* 전일 종가 자리에 굵은 기준선을 그었다가 뺐다(2026-09-10). 한 줄만 두꺼우면
   그 줄이 더 중요한 값처럼 읽힌다. 종가가 어디인지는 등락률 부호가 +5% 에서
   -5% 로 넘어가는 자리와 표 위 말풍선으로 알 수 있다. */

function Rows({ d }: { d: PredictionDistribution }) {
  /* 비싼 쪽이 위로 오게 뒤집는다 — 호가창과 같은 방향이다 */
  const rows = [...d.buckets].reverse()
  const peak = Math.max(1, ...rows.map((b) => b.count))

  return (
    <div className="pd-book">
      {/* 기준가 안내를 표 아래에서 제목 바로 밑으로 올렸다(2026-09-10). 0% 가
          무엇인지 모르고 표를 읽으면 등락률이 무엇 대비인지 알 수 없어, 표보다
          먼저 와야 하는 문장이다. 개미가 말해 주는 모양으로 둔다. */}
      <div className="pd-say">
        <img className="pd-face" src="/assets/character/black_ant/antenna-profile.png"
             alt="" aria-hidden="true" />
        <p className="pd-bubble">
          등락률은 전일 종가 <b className="num">{won(d.basePrice)}원</b>({dot(d.asOf)}) 기준입니다.
          개별 예측은 작성자 채널에서 볼 수 있습니다.
        </p>
      </div>

      <p className="pd-total num">
        판정 대기 <b>{won(d.total)}</b>건
      </p>

      {/* 가운데 등락률을 축으로 상승은 왼쪽, 하락은 오른쪽으로 뻗는다.
          한 줄은 상승이거나 하락이라 반대쪽 칸은 늘 비고, 그 덕에 위아래로
          두 덩어리가 갈려 "어느 쪽이 몰렸나" 가 한눈에 들어온다. */}
      <table className="pd-table">
        <caption className="hm-sr">
          목표가 구간별 예측 수. 전일 종가 {won(d.basePrice)}원({dot(d.asOf)}) 기준.
          상승 예측은 왼쪽, 하락 예측은 오른쪽 막대입니다.
        </caption>
        <colgroup>
          <col className="pd-c-price" />
          <col className="pd-c-side" />
          <col className="pd-c-pct" />
          <col className="pd-c-side" />
        </colgroup>
        <thead>
          <tr>
            <th scope="col" className="pd-h-price">목표가</th>
            <th scope="col" className="pd-h-up">상승</th>
            <th scope="col" className="pd-h-pct">등락률</th>
            <th scope="col" className="pd-h-down">하락</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((b) => {
            const up = b.fromPct === null ? false : b.fromPct >= 0
            const width = `${(b.count / peak) * 100}%`
            /* 막대는 장식이라 읽어 주지 않는다 — 숫자를 늘 함께 적는다. 건수가 0 이면
               막대를 그리지 않는다: 폭 0 짜리 조각이 남으면 한 점으로 보여 "한 명 있음"
               처럼 읽힌다.

               둘을 **셀 안쪽 상자**에 넣는 이유 — td 에 직접 display:flex 를 주면 그 칸이
               표 레이아웃에서 빠져, 아래 테두리가 행 높이가 아니라 flex 상자 높이에
               걸린다(옆 칸과 줄이 어긋난다). */
            const bar = (
              <div className="pd-side-in">
                {b.count > 0 && (
                  <span className={`pd-bar is-${up ? 'up' : 'down'}`} style={{ width }} aria-hidden="true" />
                )}
                <b className="num">{b.count > 0 ? won(b.count) : '·'}</b>
              </div>
            )
            return (
              <tr
                key={`${b.fromPct ?? 'min'}:${b.toPct ?? 'max'}`}
                className={`pd-row${b.count === 0 ? ' is-empty' : ''}`}
              >
                <td className="pd-price num">{priceLabel(b)}</td>
                <td className="pd-side is-up">{up && bar}</td>
                <th scope="row" className={`pd-pct num is-${up ? 'up' : 'down'}`}>
                  {bucketLabel(b)}
                </th>
                <td className="pd-side is-down">{!up && bar}</td>
              </tr>
            )
          })}
        </tbody>
      </table>

    </div>
  )
}

type Props = {
  code: string
  /**
   * 화면 머리의 전일 종가. **목업이 기준가를 지어내지 않게** 넘긴다 — 지어내면
   * 같은 화면에 269,500원과 205,000원이 나란히 뜬다. 서버가 붙으면 응답값을 쓴다.
   */
  base: DistributionBase
  span?: 4 | 5 | 6 | 7 | 8 | 12
}

export default function PredictionDepth({ code, base, span = 6 }: Props) {
  /* 요약은 뒤늦게 온다(처음엔 prevClose 가 null). deps 에 넣어 값이 들어오면
     기준가를 그것으로 다시 잡는다 — 안 넣으면 첫 렌더의 난수가 그대로 남는다. */
  const q = useBlock(
    () => getPredictionDistribution(code, base),
    [code, base.basePrice, base.asOf],
  )

  return (
    <Panel title="이 종목의 예측 분포" span={span}>
      {q.error && <ErrorState error={q.error} onRetry={q.retry} inline />}
      {!q.error && q.loading && <p className="pf-none">불러오는 중입니다…</p>}
      {/* 아직 아무도 안 걸었으면 빈 호가창을 그리지 않는다 — 0 만 스무 줄 나온다 */}
      {!q.error && !q.loading && q.data && (
        q.data.total === 0
          ? <p className="pf-none">아직 이 종목에 걸린 예측이 없습니다. 첫 예측을 등록해 보세요.</p>
          : <Rows d={q.data} />
      )}
    </Panel>
  )
}
