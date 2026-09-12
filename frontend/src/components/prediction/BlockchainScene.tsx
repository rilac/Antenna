/* 예측 봉인 장면 — C-01 등록 진행 모달 안에서 도는 애니메이션
   설계서 docs/화면설계서.md §4 C-01

   무엇을 그리는가
   왼쪽에 예측 데이터가 사는 데이터센터가, 오른쪽에 블록을 올려놓는 받침대가 있다.
   개미들이 저마다 예측 블록을 안고 입구에서 나와 받침대까지 걸어가, 하나씩
   내려놓고 빈손으로 걸어 나간다. 받침대 위의 사슬이 그만큼 자란다.
   그중 하나가 내 예측이다.

   **사슬이 자라는 것은 지어낸 진행이 아니다.** 앵커 배치는 여러 사람의 예측을
   한 번에 묶는다 — 다른 개미들이 나르는 블록은 같이 묶이는 남의 예측이고,
   내 예측 하나만 따로 가는 것이 아니라는 사실을 그림이 그대로 말한다.

   **언제 무엇이 일어나는가.** 모달의 단계 목록과 짝을 맞춘다.
     지갑 서명(signing)      화면에는 동료들만 — 내 개미는 아직 건물 안이다
     커밋 해시 생성(committing) 내 개미가 나와 받침대까지 걸어간다
     등록됨(settled)          블록을 내려놓는다. 여기서 이야기가 끝난다

   걷기 시작은 커밋 해시 생성으로 넘어갈 때지만, **블록을 내려놓는 것은 서버가
   받아 줬다고 말한 뒤다.** 둘은 보통 1초도 차이 나지 않아 걷는 동안 답이 온다 —
   혹시 늦으면 받침대 앞에서 그 한 박자만 기다린다. 맡기지도 않은 블록이 먼저
   놓이면 아직 일어나지 않은 일을 그린 것이 된다.

   앵커 확정(CONFIRMED)은 배치가 정해진 시각에 돌아 몇 분 뒤에 온다. 그때까지
   이야기를 붙들어 두지 않는다 — 슬롯이 자리 잡는 것이 등록의 끝이고, 확정은
   그 슬롯이 환해지는 것으로 뒤늦게 따라온다.

   **받침대는 놓기 스프라이트에서 그대로 떼어낸 그림이다.** 따로 그린 목적지를
   쓰면 스프라이트 안의 받침대와 둘이 되거나, 덮어서 가리면 블록 놓는 장면이
   같이 가려진다. 같은 그림이라 개미가 다가서면 한 치도 어긋나지 않는다.

   **일이 끝난 개미는 화면에 남지 않는다.** 내 개미도 블록을 놓고 나면 동료들과
   똑같이 무대 밖으로 걸어 나간다 — 놓은 자리 옆에 세워 두면 길 한가운데
   멀쩡한 개미가 하나 서 있게 되고, 그게 무슨 뜻인지 아무도 모른다.
   **마무리.** 블록을 놓으면 받침대가 완성 슬롯으로 바뀌어 무대 가운데로 옮겨
   가고, 개미는 놓았던 그 자리에서 환호한다. 슬롯이 비켜 주기 때문에 개미가
   어디로 물러날 필요가 없다 — 둘이 동시에 자기 자리를 갖는다.

   **모달이 열리는 순간이 첫 개미가 문에서 나오는 순간이다.** 빈 받침대에서
   시작해 개미가 하나씩 나온다 — 열자마자 개미들이 길 한복판에 흩어져 있으면
   무엇이 시작이고 무엇이 끝인지 읽히지 않는다. */
import { useEffect, useReducer, useRef, useState } from 'react'
import type { ProofAnchorStatus } from '../../api/proof'
import type { CommitPhase } from './CommitProgressModal'
import {
  ALL, BASELINE, CANVAS, CHAIN, CHEER, CUBE, DOOR, FX, PAD, PLACE, SCENE, SLOT,
  WALK_CARRY, WALK_EMPTY, WATCH,
} from './animationFrames'
import '../../styles/screens/blockchain-scene.css'

/* 장면이 지나는 자리. 이름은 사용자가 보는 사건에 맞춘다 */
type Stage =
  | 'carry'      // 블록을 안고 받침대까지 걸어간다
  | 'place'      // 블록을 받침대에 내려놓는 중
  | 'sealed'     // 봉인됨 — 앵커 대기
  | 'confirmed'  // 블록체인 기록 확정
  | 'failed'     // 앵커 실패

/* 프레임 간격(ms). 걸음은 또박또박, 내려놓기는 한 박자 느리게 */
const MS = { walk: 130, place: 170, cheer: 150 }

/* 건물이 무대에서 차지하는 자리(%). CSS 의 .bs-building 과 맞춰 둔다 */
const BUILDING = { left: -3, width: 40 }

/* 개미는 건물 입구에서 나온다. 입구가 그림 어디쯤인지는 자르는 스크립트가
   재서 DOOR 로 알려준다 — 눈대중으로 정하면 건물 그림을 바꿀 때마다
   개미가 벽에서 튀어나온다. */
const X = {
  door: BUILDING.left + BUILDING.width * DOOR,
  /** 블록을 내려놓는 자리. 여기 섰을 때 스프라이트 속 받침대가 무대의 받침대와
   *  정확히 포개진다 — 같은 그림에서 떼어낸 것이라 한 치도 어긋나지 않는다 */
  stop: 64,
  /** 무대 오른쪽 밖. 빈손이 된 개미는 여기까지 걸어 나간다 */
  out: 118,
}
/** 완성 슬롯이 옮겨 가 자리 잡는 곳. 무대 한가운데 */
const X_MID = 46
/* 걸음 속도. 거리에서 시간을 계산한다 — 구간마다 시간을 따로 적으면 같은
   걷기인데도 어떤 구간에서 갑자기 빨라져 미끄러지듯 보인다. */
const SPEED = 18                                  // 무대 폭 대비 %/초
const ms = (from: number, to: number) => Math.round((Math.abs(to - from) / SPEED) * 1000)

/** 사슬에 보여 줄 최대 칸수. 두 칸이면 "엮인다" 는 뜻은 이미 다 전해지고,
 *  그 뒤로는 받침대 위의 내 블록이 화면의 주인공으로 남는다 —
 *  사슬이 계속 길어지면 정작 봐야 할 내 블록에서 눈이 떠난다. */
const MAX_LINKS = Math.min(2, CHAIN.length)

/** 마지막 프레임에서 한 박자 머문다. 놓자마자 걸어 나가면 놓는 장면이
 *  스쳐 지나가 "놓았다" 가 안 읽힌다. */
const PLACE_HOLD = 320
const PLACE_MS = PLACE.length * MS.place
const T = {
  in: ms(X.door, X.stop),      // 블록을 안고 들어오는 구간
  place: PLACE_MS + PLACE_HOLD,  // 멈춰서 내려놓고 한 박자 머무는 구간
  out: ms(X.stop, X.out),      // 빈손으로 나가는 구간
  rest: 1800,                  // 다음 개미가 나오기까지의 여유. 이 값이 개미 사이 간격을 만든다
}
const CYCLE = T.in + T.place + T.out + T.rest

/* 동료 개미들. 같은 길을 도는 동료이고, 저마다 블록을 하나씩 두고 나간다.
   발이 같이 구르면 복제처럼 보이므로 프레임을 어긋내 준다. */
const FOLLOWERS = [{ shift: 2 }, { shift: 4 }]
/** 한 명씩 간격을 두고 나오도록. 나 자신도 한 자리 차지하고 센다 */
const HEADWAY = CYCLE / (FOLLOWERS.length + 1)

const REDUCED = () =>
  typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

/** 동료가 한 바퀴 안에서 지금 무엇을 하고 있는가.
 *  자리(left)는 CSS 가 부드럽게 옮기고 여기서는 어떤 그림을 쓸지만 고른다 —
 *  자리까지 이 시계로 옮기면 다리는 구르는데 몸이 계단처럼 튄다. */
function follower(t: number) {
  if (t < T.in) return { src: WALK_CARRY, i: Math.floor(t / MS.walk) }
  if (t < T.in + T.place) {
    return { src: PLACE, i: Math.min(Math.floor((t - T.in) / MS.place), PLACE.length - 1) }
  }
  return { src: WALK_EMPTY, i: Math.floor((t - T.in - T.place) / MS.walk) }
}

/** 동료가 지나는 길. 걸어 들어와 — 멈춰서 놓고 — 걸어 나가고 — 밖에서 쉰다.
 *  네 구간의 길이가 모두 위 상수에서 나오므로 속도를 바꿔도 그림과 어긋나지
 *  않는다. 자리 이동은 CSS 에 맡겨야 60fps 로 부드럽다 — 그래서 keyframes 를
 *  여기서 만들어 넣는다. CSS 파일에 적어 두면 속도를 바꿀 때마다 개미가
 *  미끄러지거나 순간이동한다. */
const MARCH = (() => {
  const p = (t: number) => ((t / CYCLE) * 100).toFixed(3)
  const stops = [
    ['0', X.door],
    [p(T.in), X.stop],
    [p(T.in + T.place), X.stop],
    [p(T.in + T.place + T.out), X.out],
    ['100', X.out],
  ] as const
  return '@keyframes bs-march{'
    + stops.map(([at, left]) => at + '%{left:' + left + '%}').join('')
    + '}'
})()

type Props = {
  phase: CommitPhase
  /** 서버가 말한 앵커 상태. null 이면 아직 물어보지 못했다는 뜻이다 */
  anchor: ProofAnchorStatus | null
  /** 봉인이 화면에 다 그려졌을 때. 여기서 이야기가 끝나므로 모달이 비켜설 수 있다 */
  onSealed?: () => void
}

export default function BlockchainScene({ phase, anchor, onSealed }: Props) {
  const [stage, setStage] = useState<Stage>('carry')
  const [x, setX] = useState(X.door)
  const [moveMs, setMoveMs] = useState(0)
  const [hidden, setHidden] = useState(true)   // 건물 안에 있다 · 무대 밖으로 나갔다
  /** 지금 어떤 걸음인가. 블록을 놓고 나면 빈손이 되므로 그림이 바뀐다 */
  const [gait, setGait] = useState<'carry' | 'empty' | 'stand'>('carry')
  const [spark, setSpark] = useState<string | null>(null)
  const [ring, setRing] = useState(0)           // 블록이 얹힌 순간의 파문
  const [links, setLinks] = useState(0)         // 받침대 위 사슬 길이. 빈 받침대에서 시작한다
  const [moved, setMoved] = useState(false)     // 완성 슬롯이 가운데로 옮겨 갔는가
  const timers = useRef<number[]>([])

  const settled = phase === 'settled'
  const settledRef = useRef(settled)
  settledRef.current = settled
  /** 커밋 해시 생성으로 넘어갔는가. 내 개미가 건물에서 나오는 신호다 */
  const startedRef = useRef(false)
  startedRef.current = phase !== 'signing'
  /* 나가는 걸음이 끝났을 때 화면을 치울지 판단하려면 그 시점의 상태가 필요하다 —
     그 사이에 확정이 와서 개미가 되돌아오는 중이면 치우면 안 된다. */
  const stageRef = useRef(stage)
  stageRef.current = stage

  useEffect(() => () => { timers.current.forEach(clearTimeout) }, [])

  /* 그리기 전에 전부 받아 둔다. 첫 재생에서 프레임이 비면 애니메이션이 끊겨 보인다 */
  useEffect(() => { ALL.forEach((s) => { const im = new Image(); im.src = s }) }, [])

  /* 장면 전체가 같은 시계를 본다. 프레임 번호를 세어 두는 대신 흐른 시간에서
     그때그때 계산한다 — 세어 두면 화면이 잠깐 멈췄다 돌아올 때 동료들이
     저마다 다른 박자로 어긋난다. */
  const [, tick] = useReducer((n: number) => n + 1, 0)
  const startRef = useRef(performance.now())
  useEffect(() => {
    if (REDUCED()) return
    const t = window.setInterval(tick, 50)
    return () => window.clearInterval(t)
  }, [])
  const now = performance.now() - startRef.current
  const walkFrame = Math.floor(now / MS.walk)
  const cheerFrame = Math.floor(now / MS.cheer) % CHEER.length

  /** 동료 i 가 자기 한 바퀴에서 어디쯤인지(ms). 아직 안 나왔으면 null.
   *  CSS 애니메이션과 같은 주기·같은 시작점을 쓴다 — 그래야 그림이 바뀌는
   *  순간과 받침대 앞에 서는 순간이 맞는다.
   *  시작을 앞당기지 않고 뒤로 미룬다(양수 지연). 예전에는 음수 지연이라
   *  모달이 뜨는 순간 개미들이 이미 길 한복판에 흩어져 있었다. */
  const at = (i: number) => {
    const t = now - i * HEADWAY
    return t < 0 ? null : t % CYCLE
  }

  /* 받침대가 완성 슬롯으로 바뀌는 시점은 등록이 끝난 때다. 앵커 확정은
     그 슬롯이 환해지는 것으로 뒤늦게 따라온다(CSS data-stage). */
  const done = stage === 'sealed' || stage === 'confirmed'

  /* 동료가 블록을 내려놓으면 사슬이 한 칸 자라고 받침대에 파문이 인다.
     받침대에 얹히는 마지막 박자에 한 번만 잡는다.

     **끝난 뒤에는 아무것도 하지 않는다.** 동료들이 물러난 뒤에도 이 시계가
     돌면, 보이지도 않는 개미가 놓는 박자에 맞춰 빈자리에 파문이 일고 사슬이
     자란다. 사슬은 환호하는 개미의 손 높이에 겹쳐서 "다 놓고도 다시 들고
     있는" 것처럼 보인다 — 실제로 그렇게 보였다. */
  const placedRef = useRef<boolean[]>(FOLLOWERS.map(() => false))
  useEffect(() => {
    if (done) return
    FOLLOWERS.forEach((_, i) => {
      const t = at(i)
      if (t === null) return
      const landed = t >= T.in + T.place - MS.place && t < T.in + T.place + 300
      if (landed && !placedRef.current[i]) {
        setLinks((n) => Math.min(n + 1, MAX_LINKS))
        setRing((n) => n + 1)
      }
      placedRef.current[i] = landed
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [walkFrame])

  /* 내 블록을 나르는 한 바퀴. 서버가 받아 줬다고 말한 뒤에 시작한다. */
  const placeAt = useRef(0)
  useEffect(() => {
    let alive = true
    const sleep = (d: number) => new Promise<void>((r) => {
      timers.current.push(window.setTimeout(r, d))
    })

    /* 움직임을 줄여 달라는 설정이면 걷는 구간을 통째로 건너뛴다.
       걸어가는 그림은 필요한 정보를 담고 있지 않다. */
    if (REDUCED()) {
      const wait = async () => {
        while (alive && !settledRef.current) await sleep(250)
        if (!alive) return
        setHidden(true); setStage('sealed'); onSealed?.()
      }
      void wait()
      return () => { alive = false }
    }

    const run = async () => {
      // 입구에 세워 두되 아직 보이지 않는다 — 건물 안에서 차례를 기다리는 중이다
      setHidden(true); setStage('carry'); setMoveMs(0); setX(X.door)
      while (alive && !startedRef.current) await sleep(120)
      if (!alive) return

      // 커밋 해시 생성으로 넘어갔다. 나와서 받침대까지 걸어간다
      setHidden(false)
      await sleep(220); if (!alive) return
      const inMs = ms(X.door, X.stop)
      setMoveMs(inMs); setX(X.stop)
      await sleep(inMs); if (!alive) return

      // 걷는 동안 답이 왔을 것이다. 아직이면 그 한 박자만 기다린다 —
      // 맡기지도 않은 블록을 먼저 놓을 수는 없다
      while (alive && !settledRef.current) await sleep(120)
      if (!alive) return

      placeAt.current = performance.now() - startRef.current
      setStage('place')
      await sleep(PLACE_MS - MS.place); if (!alive) return
      // 블록이 받침대에 얹히는 박자에 맞춰 파문과 반짝임을 같이 낸다
      setRing((n) => n + 1)
      setSpark(FX.sparkSmall)
      setLinks((n) => Math.min(n + 1, MAX_LINKS))
      await sleep(MS.place); if (!alive) return
      setSpark(null)
      // 놓은 자세로 한 박자 머문다 — 바로 걸어 나가면 놓는 장면이 스쳐 지나간다
      await sleep(PLACE_HOLD); if (!alive) return
      setStage('sealed')
      setGait('stand')
      onSealed?.()
      // 한 틱 뒤에 옮기기 시작해야 CSS 가 옮겨 가는 과정을 그린다 —
      // 처음부터 가운데에 두면 그냥 순간이동한다
      await sleep(60); if (!alive) return
      setMoved(true)
      // 슬롯이 가운데에 자리 잡는 순간에 맞춰 터뜨린다
      await sleep(1600); if (!alive) return
      setSpark(FX.sparkBig)
      await sleep(800); if (!alive) return
      setSpark(null)
    }
    void run()
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /* 앵커는 서버가 말할 때만 움직인다. setTimeout 으로 확정을 지어내지 않는다 */
  useEffect(() => {
    if (stage !== 'sealed' || !anchor) return
    if (anchor === 'FAILED') { setStage('failed'); return }
    if (anchor !== 'CONFIRMED') return
    /* 확정은 몇 분 뒤에 온다. 그때는 이미 슬롯이 가운데에 자리 잡고 이야기가
       끝나 있으므로, 새 장면을 시작하지 않고 슬롯이 환해지는 것으로만 따라온다.
       (창이 그때까지 열려 있는 경우에만 보인다 — 보통은 먼저 닫힌다) */
    setStage('confirmed')
    if (REDUCED()) return
    const t = window.setTimeout(() => setSpark(FX.sparkBig), 150)
    const u = window.setTimeout(() => setSpark(null), 950)
    return () => { window.clearTimeout(t); window.clearTimeout(u) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [anchor, stage])

  /* 지금 내 개미가 무엇을 보이는가. 걸음이 상태보다 먼저다 —
     확정된 뒤에도 걸어 들어오는 동안은 걷는 그림이어야 한다.
     환호는 정면이라 옆으로 걷는 동작과 이어지지 않으므로 멈춘 뒤에만 쓴다. */
  const leadSrc =
    stage === 'place'
      ? PLACE[Math.min(Math.floor((now - placeAt.current) / MS.place), PLACE.length - 1)]
    : gait === 'carry' ? WALK_CARRY[walkFrame % WALK_CARRY.length]
    : gait === 'empty' ? WALK_EMPTY[walkFrame % WALK_EMPTY.length]
    // 놓자마자 환호한다. 앵커 확정을 기다리지 않는다 — 등록은 이미 끝났고,
    // 확정은 몇 분 뒤라 그때까지 참으면 아무도 못 본다
    : stage === 'sealed' || stage === 'confirmed' ? CHEER[cheerFrame]
    : WATCH

  /* 실패했을 때만 받침대 위에 블록이 남는다. 확정은 슬롯 그림이 통째로
     말해 주므로 따로 올리지 않는다. 기다리는 동안에도 비워 둔다 —
     대기 중에 블록을 올려 두면 이미 자리를 잡은 것처럼 보이고, 끝났을 때
     달라지는 것이 없어 끝났다는 느낌도 없어진다. */
  const badge = stage === 'failed' ? CUBE.failed : null

  /* 확정되면 받침대가 완성 슬롯으로 바뀐다. 밑동 너비가 같아 그 자리에서
     그대로 바뀐 것으로 읽힌다. */

  /* 캔버스 규격은 자동 생성 모듈이 알려준다. CSS 에 숫자를 적어 두면 자산을
     다시 뽑을 때마다 어긋난다 — 실제로 두 번 어긋나서 이렇게 바꿨다. */
  const vars = {
    '--bs-canvas-w': CANVAS.w,
    '--bs-anchor-x': BASELINE.x,
    '--bs-foot': CANVAS.h - BASELINE.y,
    '--bs-x-stop': X.stop,
    '--bs-pad-dx': PAD.dx,
    '--bs-pad-w': PAD.w,
    '--bs-pad-top': PAD.top,
    '--bs-slot-w': SLOT.w,
    '--bs-x-mid': X_MID,
    '--bs-chain-w': CHAIN[Math.max(links - 1, 0)].w,
    '--bs-b-left': BUILDING.left,
    '--bs-b-width': BUILDING.width,
  } as React.CSSProperties

  return (
    <div className="bs" data-stage={stage} style={vars} aria-hidden="true">
      <style>{MARCH}</style>

      <div className="bs-ground" />
      <img className="bs-building" src={SCENE.building} alt="" />

      {/* 동료들. 저마다 블록을 하나 두고 빈손으로 걸어 나간다.
          받침대보다 뒤에 그려야 받침대가 개미 다리를 가린다 */}
      {FOLLOWERS.map((f, i) => {
        const t = at(i)
        const step = follower(t ?? 0)
        return (
          <img
            key={i}
            className="bs-mascot bs-follow"
            src={step.src[(step.i + f.shift) % step.src.length]}
            alt=""
            style={{
              animationDuration: `${CYCLE}ms`,
              animationDelay: `${i * HEADWAY}ms`,
              // 차례가 오기 전에는 문 안에 있다. 요소는 계속 두어야 CSS 시계가
              // 모달이 열린 시각부터 흐른다 — 빼 버리면 지연이 다시 시작된다.
              // 확정되면 물러난다. 끝난 화면에 길 위를 지나는 개미가 남아 있으면
              // 가운데로 나온 슬롯과 환호하는 내 개미에서 눈이 흩어진다
              opacity: t === null || done ? 0 : 1,
            }}
          />
        )
      })}

      <img
        className="bs-mascot"
        src={leadSrc}
        alt=""
        style={{
          left: `${x}%`,
          transition: `left ${moveMs}ms linear, opacity 220ms ease`,
          opacity: hidden ? 0 : 1,
        }}
      />
      {/* 받침대는 개미들보다 앞에 그린다. 스프라이트 안의 받침대와 같은 그림이라
          정확히 포개지고, 개미는 그 뒤에서 몸을 숙여 블록을 얹는 것으로 읽힌다.
          확정되면 완성 슬롯으로 바뀌어 무대 가운데로 옮겨 간다 */}
      <img
        className={done ? 'bs-pad bs-slot' : 'bs-pad'}
        src={done ? SCENE.slot : SCENE.pad}
        alt=""
        data-moved={done && moved ? '' : undefined}
      />
      {ring > 0 && <img key={ring} className="bs-ring" src={FX.ring} alt="" />}
      {/* 받침대에 쌓인 블록들이 사슬로 엮인다. 남의 예측도 같은 배치에 묶인다.
          아직 아무도 놓지 않았으면 비어 있다 */}
      {links > 0 && !done && <img className="bs-chain" src={CHAIN[links - 1].src} alt="" />}
      {badge && <img className="bs-badge" src={badge} alt="" />}
      {spark && <img className="bs-spark" src={spark} alt="" />}
    </div>
  )
}
