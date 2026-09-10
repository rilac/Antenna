/* 예측 봉인 장면 — C-01 등록 진행 모달 안에서 도는 애니메이션
   설계서 docs/화면설계서.md §4 C-01

   무엇을 그리는가
   마스코트가 새 예측 블록을 가지러 달려가 집어 들고, 슬롯까지 날라 내려놓는다.
   내려놓으면 예측이 봉인되고(자물쇠 큐브), 나중에 배치가 앵커를 묶으면 확정
   큐브로 바뀐다.

   **없는 진행을 지어내지 않는다.** 이 장면은 모달이 이미 알고 있는 두 가지,
   phase(지갑 서명 → 서버 응답)와 anchor(서버가 말한 앵커 상태)만 따라간다.
   등록이 끝나기 전에는 블록을 집지 않고, 서버가 CONFIRMED 를 주기 전에는
   확정 큐브를 그리지 않는다. 시간이 지났다고 다음 단계로 넘어가는 곳은 없다.

   앵커 배치는 분 단위일 수 있다. 그동안 마스코트를 계속 뛰게 두면 "아직도
   처리 중" 으로 읽혀 불안하다 — 봉인이 끝나면 마스코트는 화면 밖으로 내보내고
   봉인된 큐브만 남긴다. 상태는 이미 PENDING 이고 퇴장은 뒷정리일 뿐이다. */
import { useEffect, useRef, useState } from 'react'
import type { ProofAnchorStatus } from '../../api/proof'
import type { CommitPhase } from './CommitProgressModal'
import {
  ALL, BASELINE, CANVAS, CUBE, DOCKED, FX, PICKUP, PLACE, PROP, RUN_CARRY, RUN_EMPTY, STAND,
} from './animationFrames'
import '../../styles/screens/blockchain-scene.css'

/* 장면이 지나는 자리. 이름은 사용자가 보는 사건에 맞춘다 */
type Stage =
  | 'approach'   // 블록까지 달려가 기다린다 (지갑 서명 · 서버 응답 대기)
  | 'pickup'     // 블록을 집는다
  | 'carry'      // 슬롯까지 나른다
  | 'place'      // 슬롯에 내려놓는다
  | 'sealed'     // 봉인됨 — 앵커 대기(WAITING · PENDING)
  | 'anchoring'  // 서버가 CONFIRMED 를 줬다. 연결 장면
  | 'confirmed'  // 블록체인 기록 확정
  | 'failed'     // 앵커 실패

/* 프레임 간격(ms). 빈손은 가볍게, 블록을 들면 무겁게 */
const MS = { empty: 80, carry: 100, pickup: 150, place: 150 }

/* 블록 앞에서 기다리는 자세. 지갑 승인이 오래 걸릴 수 있어 제자리 달리기로 두면
   재촉하는 것처럼 보인다 — 서서 기다린다.
   뒤집어 만든 idle 은 쓰지 않는다. 좌우 반전이라 더듬이 색(보라·파랑)이 바뀌어
   달리기 프레임과 이어지지 않는다. STAND 는 원래 오른쪽을 보고 그려진 것이다. */
const WAIT = [STAND]
/* 이동 시간(ms) */
const MOVE = { enter: 900, carry: 1200, exit: 800 }

/* 캐릭터가 서는 자리(무대 폭에 대한 %). 소품은 이 자리에서 PROP 만큼 떨어져 놓인다 —
   그 거리는 스프라이트 안에서 실제로 잰 값이라, 별도 큐브에서 줍기 스프라이트로
   넘어갈 때 큐브가 옆으로 튀지 않는다. */
const X = { block: 21, slot: 66, off: -28, gone: 128 }

const REDUCED = () =>
  typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

/** 스프라이트 프레임 재생. 화면 이동(transform)과는 분리한다 —
 *  프레임만 바꾸면 제자리에서 발버둥치고, 이동만 하면 미끄러진다. */
function useFrames(frames: string[], ms: number, playing = true) {
  const [i, setI] = useState(0)
  useEffect(() => { setI(0) }, [frames])
  useEffect(() => {
    if (!playing || frames.length < 2) return
    const t = setInterval(() => setI((n) => (n + 1) % frames.length), ms)
    return () => clearInterval(t)
  }, [frames, ms, playing])
  return frames[Math.min(i, frames.length - 1)]
}

type Props = {
  phase: CommitPhase
  /** 서버가 말한 앵커 상태. null 이면 아직 물어보지 못했다는 뜻이다 */
  anchor: ProofAnchorStatus | null
}

export default function BlockchainScene({ phase, anchor }: Props) {
  const [stage, setStage] = useState<Stage>('approach')
  const [entered, setEntered] = useState(false)
  const [spark, setSpark] = useState<string | null>(null)
  const [gone, setGone] = useState(false)      // 마스코트가 화면 밖으로 나갔나
  const timers = useRef<number[]>([])

  const settled = phase === 'settled'
  const after = (ms: number, fn: () => void) => {
    timers.current.push(window.setTimeout(fn, ms))
  }
  useEffect(() => () => { timers.current.forEach(clearTimeout) }, [])

  /* 그리기 전에 전부 받아 둔다. 첫 재생에서 프레임이 비면 애니메이션이 끊겨 보인다 */
  useEffect(() => { ALL.forEach((s) => { const im = new Image(); im.src = s }) }, [])

  /* 입장. 움직임을 줄여 달라는 설정이면 달려오는 구간을 통째로 건너뛴다 */
  useEffect(() => {
    if (REDUCED()) { setEntered(true); return }
    const t = window.setTimeout(() => setEntered(true), 30)
    return () => clearTimeout(t)
  }, [])

  /* 서버가 등록을 받아 주면 그때부터 집어서 나른다.
     여기가 이 장면의 유일한 자동 진행 구간이다 — 등록이 끝났다는 사실은
     이미 서버가 말해 줬고, 나머지는 그 하나의 사건을 풀어 보이는 것뿐이다. */
  useEffect(() => {
    if (!settled || stage !== 'approach') return
    if (REDUCED()) { setStage('sealed'); setGone(true); return }

    setStage('pickup')
    after(MS.pickup * PICKUP.length, () => {
      setStage('carry')
      after(MOVE.carry, () => {
        setStage('place')
        after(MS.place * 2, () => setSpark(FX.sparkSmall))
        after(MS.place * 3, () => setSpark(null))
        after(MS.place * PLACE.length, () => {
          setStage('sealed')
          after(MOVE.exit, () => setGone(true))
        })
      })
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [settled])

  /* 앵커는 서버가 말할 때만 움직인다. setTimeout 으로 확정을 지어내지 않는다 */
  useEffect(() => {
    if (stage !== 'sealed' || !anchor) return
    if (anchor === 'FAILED') { setStage('failed'); return }
    if (anchor !== 'CONFIRMED') return
    if (REDUCED()) { setStage('confirmed'); return }

    setStage('anchoring')
    after(600, () => setSpark(FX.sparkBig))
    after(900, () => { setSpark(null); setStage('confirmed') })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [anchor, stage])

  /* 지금 어떤 스프라이트를 도는가 */
  const clip =
    stage === 'pickup' ? PICKUP
    : stage === 'carry' ? RUN_CARRY
    : stage === 'place' ? PLACE
    : stage === 'sealed' && !gone ? RUN_EMPTY      // 퇴장
    : entered && !settled ? WAIT                   // 블록 옆에서 기다린다
    : RUN_EMPTY
  const clipMs =
    clip === PICKUP ? MS.pickup
    : clip === PLACE ? MS.place
    : clip === RUN_CARRY ? MS.carry
    : MS.empty
  /* 줍기·놓기는 한 번만 지나간다. 되돌아 돌면 집었다 놓았다 하는 것처럼 보인다 */
  const loop = clip !== PICKUP && clip !== PLACE
  const src = useFrames(clip, clipMs, loop || stage === 'approach' || stage === 'sealed')

  /* 캐릭터가 서는 x 자리(무대 폭에 대한 비율) */
  const x =
    !entered ? X.off
    : stage === 'approach' || stage === 'pickup' ? X.block
    : gone ? X.gone
    : X.slot

  const moveMs =
    !entered ? 0
    : stage === 'carry' ? MOVE.carry
    : stage === 'sealed' && gone ? MOVE.exit
    : MOVE.enter

  /* 큐브는 별도 레이어다. 다만 줍기·놓기 스프라이트에는 큐브와 슬롯이 이미
     그려져 있어, 그 구간에는 숨긴다 — 안 그러면 큐브가 둘로 보인다. */
  const showCube = stage === 'approach'
  const showSlot = stage === 'approach' || stage === 'pickup' || stage === 'carry'
  const docked =
    stage === 'failed' ? DOCKED.failed
    : stage === 'confirmed' ? DOCKED.anchored
    : stage === 'sealed' || stage === 'anchoring' ? DOCKED.sealed
    : null

  /* 캔버스 규격은 자동 생성 모듈이 알려준다. CSS 에 숫자를 적어 두면 자산을
     다시 뽑을 때마다 어긋난다 — 실제로 한 번 어긋나서(271 → 398) 이렇게 바꿨다. */
  const vars = {
    '--bs-canvas-w': CANVAS.w,
    '--bs-canvas-h': CANVAS.h,
    '--bs-anchor-x': BASELINE.x,
    '--bs-foot': CANVAS.h - BASELINE.y,
    '--bs-x-block': X.block,
    '--bs-x-slot': X.slot,
    '--bs-cube-dx': PROP.cube.dx,
    '--bs-cube-w': PROP.cube.w,
    '--bs-slot-dx': PROP.slot.dx,
    '--bs-slot-w': PROP.slot.w,
  } as React.CSSProperties

  return (
    <div className="bs" data-stage={stage} style={vars} aria-hidden="true">
      <div className="bs-ground" />

      {showSlot && <img className="bs-slot" src={CUBE.slot} alt="" />}
      {showCube && <img className="bs-cube" src={CUBE.draft} alt="" />}
      {docked && <img className="bs-docked" src={docked} alt="" />}
      {stage === 'anchoring' && (
        <>
          <img className="bs-ring" src={FX.ringActive} alt="" />
          <img className="bs-chain" src={FX.chainActive} alt="" />
        </>
      )}
      {spark && <img className="bs-spark" src={spark} alt="" />}

      <img
        className="bs-mascot"
        src={src}
        alt=""
        style={{
          left: `${x}%`,
          transition: `left ${moveMs}ms linear`,
          opacity: gone ? 0 : 1,
        }}
      />
    </div>
  )
}
