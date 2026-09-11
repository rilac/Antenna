/* 오늘 판정된 예측 — B-03 머리에 붙는 작은 카드.

   판정 배치 B2 가 13:30 에 그날 만기가 온 예측을 HIT/MISS 로 확정한다. 그 결과를
   종목 머리에서 한 명씩 보여 준다 — "이 종목에 걸었던 사람들이 오늘 어떻게 됐나".
   호가창은 지금 걸려 있는 물량이라 끝난 예측이 들어갈 자리가 없어, 여기가 그 몫이다.

   **적중과 빗나감을 칸으로 나눈다**(2026-09-10). 둘을 한 줄에 섞어 돌리면 지금
   뜬 것이 어느 쪽인지 매번 배지를 읽어야 한다. 칸이 갈리면 자리가 곧 답이라
   카드 안의 배지도 필요 없어져, 한 줄로 줄일 수 있다 — sticky 머리는 자리가 좁다.

   **위아래로 넘긴다.** 카드가 한 장 높이만큼 잘린 창 안에서 세로로 흘러간다.
   점(dot)은 두지 않았다 — 판정이 수십 건이면 점이 수십 개가 되어 카드보다
   길어진다. 대신 칸 이름 옆에 "3/12" 로 몇 번째인지 적는다. 몇 건이 오든 폭이 같다.

   자동으로 넘어가되
   - 손이 올라가거나 초점이 들어오면 그 칸만 멈춘다. 읽는 중에 넘어가면 못 읽는다.
   - 움직임을 줄여 달라는 설정에서는 아예 안 넘긴다(prefers-reduced-motion).

   한쪽이 비면 그 칸은 그리지 않는다. 오늘 적중이 없는 날 "적중 0" 빈 상자를
   띄워 둘 이유가 없다. 둘 다 비면 통째로 사라진다. */
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useBlock } from '../../api/useBlock'
import { getSettledToday } from '../../api/predictions'
import type { SettledPrediction } from '../../api/predictions'
import '../../styles/screens/settled-today.css'

/** 한 장을 보여 주는 시간 */
const EVERY = 4200

/** 카드 한 장의 높이(px). CSS 의 --st-h 와 같은 값이어야 세로 이동이 맞는다 */
const CARD_H = 36

/** 미끄러지는 시간(ms). CSS .st-track 의 transition 과 같아야 한다 */
const SLIDE = 380

const FALLBACK_FACE = '/assets/character/white_ant/antenna-profile.png'

const won = (n: number) => n.toLocaleString('ko-KR')

function Card({ p, hidden }: { p: SettledPrediction; hidden: boolean }) {
  const up = p.direction === 'UP'
  /* 오차는 부호가 뜻을 가진다 — 목표가보다 높게 끝났나 낮게 끝났나.
     0 이면 부호를 붙이지 않는다(+0.0% 는 틀린 말이다). */
  const err = `${p.errorRate > 0 ? '+' : ''}${p.errorRate.toFixed(1)}%`

  return (
    <Link
      className="st-card"
      to={`/predictions/${p.id}`}
      /* 창 밖에 있는 장은 눌리지도, 스크린리더에 읽히지도 않아야 한다 —
         보이지 않는 카드로 탭이 넘어가면 초점이 화면 밖으로 사라진다. */
      aria-hidden={hidden}
      tabIndex={hidden ? -1 : 0}
    >
      <img className="st-face" src={p.author.avatarUrl ?? FALLBACK_FACE} alt="" aria-hidden="true" />
      <b className="st-who">{p.author.nickname}</b>
      {/* 방향은 국내 관례대로 상승이 빨강. 낱말이 있으므로 색만으로 전하지 않는다 */}
      <span className={`st-dir is-${up ? 'up' : 'down'}`}>{up ? '상승' : '하락'}</span>
      <span className="st-target num">{won(p.targetPrice)}원</span>
      {/* 숫자만 두면 무엇의 %인지 알 수 없다 — 목표가 대비 오차다 */}
      <span className="st-err"><i>오차</i><b className="num">{err}</b></span>
    </Link>
  )
}

/** 한 칸. 적중과 빗나감이 각자 제 차례와 타이머를 가진다 */
function Lane({ tone, label, items }: {
  tone: 'hit' | 'miss'
  label: string
  items: SettledPrediction[]
}) {
  const n = items.length
  /* 0 ~ n. **n 은 맨 앞 카드의 복제본**이다 — 아래 track 참고 */
  const [at, setAt] = useState(0)
  const [held, setHeld] = useState(false)
  /** 복제본에서 진짜 첫 장으로 돌아갈 때만 참. 그 한 번은 애니메이션 없이 건너뛴다 */
  const [snap, setSnap] = useState(false)

  /* 목록이 짧아지면(종목을 옮겼다) 차례를 되돌린다. 안 되돌리면 두 건짜리
     목록에서 네 번째를 가리켜 빈 자리가 보인다. */
  const [seen, setSeen] = useState(n)
  if (seen !== n) { setSeen(n); setAt(0); setSnap(false) }

  /* setInterval 이 아니라 **at 이 바뀔 때마다 새로 거는 setTimeout** 이다.
     interval 로 두면 주기가 카드와 무관하게 흘러서, 한 장이 뜨자마자 남은 주기가
     끝나며 곧바로 다음 장으로 넘어가는 일이 생긴다. */
  useEffect(() => {
    if (n < 2 || held || at >= n) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const t = window.setTimeout(() => setAt((i) => i + 1), EVERY)
    return () => window.clearTimeout(t)
  }, [n, held, at])

  /* 복제본까지 올라갔으면, 미끄러짐이 끝난 뒤 진짜 첫 장으로 소리 없이 옮긴다.
     이 장치가 없으면 마지막에서 처음으로 갈 때 track 이 통째로 **아래로** 내려와
     혼자만 방향이 반대가 된다. 복제본을 한 장 더 두면 늘 위로만 흐른다. */
  useEffect(() => {
    if (at !== n || n < 2) return
    const t = window.setTimeout(() => { setSnap(true); setAt(0) }, SLIDE)
    return () => window.clearTimeout(t)
  }, [at, n])

  /* 되돌린 다음 프레임에 애니메이션을 되살린다. 같은 프레임에 풀면 그 되돌림까지
     미끄러짐으로 그려져 결국 아래로 내려가는 것이 보인다. */
  useEffect(() => {
    if (!snap) return
    const r = requestAnimationFrame(() => setSnap(false))
    return () => cancelAnimationFrame(r)
  }, [snap])

  if (n === 0) return null
  const at2 = Math.min(at, n)

  return (
    <div
      className="st-lane"
      onMouseEnter={() => setHeld(true)}
      onMouseLeave={() => setHeld(false)}
      onFocusCapture={() => setHeld(true)}
      onBlurCapture={() => setHeld(false)}
    >
      <p className={`st-tag is-${tone}`}>{label}</p>

      {/* 한 장 높이만큼만 보이는 창. 안쪽 줄이 **늘 위로만** 밀려 올라간다.
          맨 끝에 첫 장을 한 번 더 붙여 두고, 거기까지 올라가면 소리 없이
          진짜 첫 장으로 돌아온다(위 effect). */}
      <div className="st-stage" aria-live="polite">
        <div
          className={`st-track${snap ? ' is-snap' : ''}`}
          style={{ transform: `translateY(-${at2 * CARD_H}px)` }}
        >
          {items.map((p, i) => (
            <Card key={p.id} p={p} hidden={i !== at2} />
          ))}
          {n > 1 && <Card key="loop" p={items[0]} hidden={at2 !== n} />}
        </div>
      </div>
    </div>
  )
}

export default function SettledToday({ code }: { code: string }) {
  const q = useBlock(() => getSettledToday(code), [code])
  const items = q.data?.items ?? []

  const hits = items.filter((p) => p.status === 'HIT')
  const misses = items.filter((p) => p.status === 'MISS')

  // 불러오는 중·실패·빈 목록에는 자리를 만들지 않는다. 머리가 들썩이지 않게.
  if (q.loading || q.error || items.length === 0) return null

  /* 바깥 제목은 두지 않는다(2026-09-10). 이름표가 이미 적중·빗나감을 말하고,
     머리에 줄을 하나 더 얹으면 sticky 가 그만큼 본문을 가린다. */
  return (
    <div className="st">
      <Lane tone="hit" label="적중" items={hits} />
      <Lane tone="miss" label="빗나감" items={misses} />
    </div>
  )
}
