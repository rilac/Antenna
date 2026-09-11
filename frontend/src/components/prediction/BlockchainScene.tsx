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
  ALL, BASELINE, CANVAS, CUBE, DOCKED, FX, PICKUP, PLACE, PROP, RUN_CARRY, RUN_EMPTY,
} from './animationFrames'
import '../../styles/screens/blockchain-scene.css'

/* 장면이 지나는 자리. 이름은 사용자가 보는 사건에 맞춘다 */
type Stage =
  | 'approach'   // 블록 앞까지 달려와 기다린다 (지갑 서명 · 서버 응답 대기)
  | 'dash'       // 등록이 끝났다. 남은 몇 걸음을 달려 블록에 붙는다
  | 'pickup'     // 블록을 집는다
  | 'carry'      // 슬롯까지 나른다
  | 'place'      // 슬롯에 내려놓는다
  | 'sealed'     // 봉인됨 — 앵커 대기(WAITING · PENDING)
  | 'anchoring'  // 서버가 CONFIRMED 를 줬다. 연결 장면
  | 'confirmed'  // 블록체인 기록 확정
  | 'failed'     // 앵커 실패

/* 프레임 간격(ms). 빈손은 가볍게, 블록을 들면 무겁게 */
const MS = { empty: 80, carry: 110, pickup: 260, place: 240 }

/* 기다리는 동안에도 달린다(제자리 달리기).

   서 있는 자세(STAND)를 썼더니 줍기로 넘어갈 때 눈에 띄게 튀었다 — 줍기 첫 장이
   달려오는 자세라, 멈춰 있던 몸이 갑자기 달리는 몸으로 바뀌고 팔 위치도 어긋난다.
   "가만히 있던 애가 갑자기 블록을 든 애로 바뀐다" 로 읽히는 원인이 이것이었다.
   같은 달리기를 이어 쓰면 이음새가 보이지 않는다. */

/* 캐릭터가 서는 자리(무대 폭에 대한 %). 소품은 이 자리에서 PROP 만큼 떨어져 놓인다 —
   그 거리는 스프라이트 안에서 실제로 잰 값이라, 별도 큐브에서 줍기 스프라이트로
   넘어갈 때 큐브가 옆으로 튀지 않는다.

   wait 가 block 보다 왼쪽인 이유: 줍기 스프라이트는 캐릭터와 큐브가 겹치도록
   그려져 있다(팔을 뻗어 집는 그림이라 그게 맞다). 그런데 대기 중에 쓰는 빈손
   달리기는 팔이 몸 옆에 있어, 같은 자리에 두면 큐브를 몸으로 밀고 오는 것처럼
   보인다. 그래서 기다릴 때는 한 걸음 떨어져 있다가, 등록이 끝나면 그 몇 걸음을
   달려가(dash) 집는다 — 멀어졌다 붙는 편이 오히려 동작에 이유를 준다. */
const X = { wait: 13, block: 21, slot: 66, off: -28, gone: 128, rest: 39 }

/* 걸음 속도를 하나로 묶는다. 구간마다 이동 시간을 따로 적었더니 같은 달리기
   프레임을 도는데도 바닥 속도가 19 → 30 → 69%/초 로 뛰었다. 발은 같은 박자로
   구르는데 몸만 빨라지니 미끄러지고, 이어지는 데서 갑자기 빨라져 보인다.

   무대 폭의 몇 퍼센트를 1초에 가는가로 적고, 이동 시간은 거리에서 계산한다.
   블록을 들면 조금 느리다 — 무게가 실린 걸음으로 읽힌다. */
const SPEED = { empty: 34, carry: 30 }   // 무대 폭 대비 %/초
const travel = (from: number, to: number, pctPerSec: number) =>
  Math.round((Math.abs(to - from) / pctPerSec) * 1000)

const MOVE = {
  enter: travel(X.off, X.wait, SPEED.empty),
  dash:  travel(X.wait, X.block, SPEED.empty),
  carry: travel(X.block, X.slot, SPEED.carry),
  exit:  travel(X.slot, X.gone, SPEED.empty),
}

const REDUCED = () =>
  typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

/** 스프라이트 프레임 재생. 화면 이동(transform)과는 분리한다 —
 *  프레임만 바꾸면 제자리에서 발버둥치고, 이동만 하면 미끄러진다.
 *
 *  once 면 한 바퀴만 돌고 마지막 프레임에서 멈춘다. 줍기·놓기가 그렇다 —
 *  되돌아 돌면 집었다 놓았다 하는 것처럼 보인다. */
function useFrames(frames: string[], ms: number, once = false) {
  const [i, setI] = useState(0)
  useEffect(() => {
    setI(0)
    if (frames.length < 2) return
    let n = 0
    const t = window.setInterval(() => {
      n += 1
      if (n >= frames.length) {
        if (once) { window.clearInterval(t); setI(frames.length - 1); return }
        n = 0
      }
      setI(n)
    }, ms)
    return () => window.clearInterval(t)
  }, [frames, ms, once])
  return frames[Math.min(i, frames.length - 1)]
}

type Props = {
  phase: CommitPhase
  /** 서버가 말한 앵커 상태. null 이면 아직 물어보지 못했다는 뜻이다 */
  anchor: ProofAnchorStatus | null
  /** 봉인이 화면에 다 그려졌을 때. 여기서 이야기가 끝나므로 모달이 비켜설 수 있다 */
  onSealed?: () => void
}

export default function BlockchainScene({ phase, anchor, onSealed }: Props) {
  const [stage, setStage] = useState<Stage>('approach')
  const [entered, setEntered] = useState(false)
  const [spark, setSpark] = useState<string | null>(null)
  const [gone, setGone] = useState(false)      // 마스코트가 화면 밖으로 나갔나
  const [hidden, setHidden] = useState(true)   // 한 바퀴를 다시 시작하는 사이
  const timers = useRef<number[]>([])

  const settled = phase === 'settled'
  useEffect(() => () => { timers.current.forEach(clearTimeout) }, [])

  /* 그리기 전에 전부 받아 둔다. 첫 재생에서 프레임이 비면 애니메이션이 끊겨 보인다 */
  useEffect(() => { ALL.forEach((s) => { const im = new Image(); im.src = s }) }, [])

  /* 한 바퀴를 통째로 반복한다.

     지갑 승인과 서버 응답을 기다리는 동안 화면이 멎어 있으면 진행이 끊긴 줄로
     읽힌다. 그렇다고 제자리에서 뛰게 두면 "왜 저기서 계속 뛰지" 가 된다 —
     처음부터 다시 달려 나오는 편이 기다리는 시간을 자연스럽게 채운다.

     **다만 봉인은 남기지 않는다.** 서버가 등록을 받아 줬다고 말하기 전에 봉인된
     큐브를 화면에 두면, 아직 일어나지 않은 일을 끝난 것처럼 보이게 된다.
     기다리는 동안에는 놓는 데까지만 돌고 조용히 처음으로 돌아간다.
     서버가 답한 뒤 맞는 한 바퀴에서만 봉인이 남고 마스코트가 퇴장한다. */
  const settledRef = useRef(settled)
  settledRef.current = settled

  useEffect(() => {
    let alive = true
    const sleep = (ms: number) => new Promise<void>((r) => {
      timers.current.push(window.setTimeout(r, ms))
    })

    /* 움직임을 줄여 달라는 설정이면 달리는 구간을 통째로 건너뛴다.
       달려가는 그림은 필요한 정보를 담고 있지 않다. */
    if (REDUCED()) {
      const wait = async () => {
        while (alive && !settledRef.current) await sleep(250)
        if (!alive) return
        setEntered(true); setStage('sealed'); setGone(true); onSealed?.()
      }
      void wait()
      return () => { alive = false }
    }

    const loop = async () => {
      while (alive) {
        // 화면 밖에서 다시 시작. 이동 없이 옮겨 두고 나타난다
        setHidden(true); setStage('approach'); setGone(false); setEntered(false)
        await sleep(260); if (!alive) return
        setHidden(false); setEntered(true)
        await sleep(MOVE.enter); if (!alive) return

        setStage('dash');   await sleep(MOVE.dash); if (!alive) return
        setStage('pickup'); await sleep(MS.pickup * PICKUP.length); if (!alive) return
        setStage('carry');  await sleep(MOVE.carry); if (!alive) return

        setStage('place')
        await sleep(MS.place * 2); if (!alive) return
        setSpark(FX.sparkSmall)
        await sleep(MS.place); if (!alive) return
        setSpark(null)
        await sleep(MS.place); if (!alive) return

        if (settledRef.current) {
          // 서버가 받아 줬다. 이 한 바퀴에서만 봉인이 남는다
          setStage('sealed')
          onSealed?.()
          await sleep(MOVE.exit); if (!alive) return
          setGone(true)
          return
        }
        // 아직이다. 봉인을 남기지 않고 한 박자 쉬었다 처음부터
        await sleep(600)
      }
    }
    void loop()
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /* 앵커는 서버가 말할 때만 움직인다. setTimeout 으로 확정을 지어내지 않는다 */
  useEffect(() => {
    if (stage !== 'sealed' || !anchor) return
    if (anchor === 'FAILED') { setStage('failed'); return }
    if (anchor !== 'CONFIRMED') return
    if (REDUCED()) { setStage('confirmed'); return }

    setStage('anchoring')
    const a = window.setTimeout(() => setSpark(FX.sparkBig), 600)
    const b = window.setTimeout(() => { setSpark(null); setStage('confirmed') }, 900)
    return () => { window.clearTimeout(a); window.clearTimeout(b) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [anchor, stage])

  /* 지금 어떤 스프라이트를 도는가 */
  const clip =
    stage === 'pickup' ? PICKUP
    : stage === 'carry' ? RUN_CARRY
    : stage === 'place' ? PLACE
    : stage === 'sealed' && !gone ? RUN_EMPTY      // 퇴장
    : RUN_EMPTY                                    // 대기 · 달려감 · 그 밖
  const clipMs =
    clip === PICKUP ? MS.pickup
    : clip === PLACE ? MS.place
    : clip === RUN_CARRY ? MS.carry
    : MS.empty   // 빈손 달리기 — 대기도 같은 clip 이라 간격이 이어진다
  /* 줍기·놓기는 한 번만 지나가고 나머지는 돈다.

     예전에는 이 자리에 playing 을 넘겼는데, 줍기일 때 그 값이 false 가 되어
     타이머가 아예 안 돌았다 — 1번 프레임(달려오는 자세)만 1초 떠 있다가
     운반으로 넘어가서, 앉았다 일어나는 2·3번이 통째로 건너뛰어졌다. */
  const src = useFrames(clip, clipMs, clip === PICKUP || clip === PLACE)

  /* 캐릭터가 서는 x 자리(무대 폭에 대한 비율) */
  const x =
    !entered ? X.off
    : stage === 'approach' ? X.wait
    : stage === 'dash' || stage === 'pickup' ? X.block
    : gone ? X.gone
    : X.slot

  const moveMs =
    !entered ? 0
    : stage === 'dash' ? MOVE.dash
    : stage === 'carry' ? MOVE.carry
    : stage === 'sealed' && gone ? MOVE.exit
    : MOVE.enter

  /* 큐브는 별도 레이어다. 다만 줍기·놓기 스프라이트에는 큐브와 슬롯이 이미
     그려져 있어, 그 구간에는 숨긴다 — 안 그러면 큐브가 둘로 보인다. */
  const showCube = stage === 'approach' || stage === 'dash'
  const showSlot = stage === 'approach' || stage === 'dash' || stage === 'pickup' || stage === 'carry'
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
    '--bs-x-rest': X.rest,
    '--bs-cube-dx': PROP.cube.dx,
    '--bs-cube-w': PROP.cube.w,
    '--bs-slot-dx': PROP.slot.dx,
    '--bs-slot-w': PROP.slot.w,
  } as React.CSSProperties

  return (
    <div className="bs" data-stage={stage} data-rest={gone || undefined} style={vars} aria-hidden="true">
      <div className="bs-ground" />

      {showSlot && <img className="bs-slot" src={CUBE.slot} alt="" />}
      {showCube && <img className="bs-cube" src={CUBE.draft} alt="" />}
      {/* 앵커를 기다리는 동안. 배치는 분 단위이고 정해진 시각에만 묶는 일도 있어
          몇 분씩 이 상태로 머문다. 화면이 완전히 멎어 있으면 진행이 끊긴 줄로
          읽히므로, 고리 두 장을 번갈아 비춰 아직 살아 있다는 것만 알린다.
          재촉하는 로딩 스피너가 되지 않도록 느리고 옅게 둔다. */}
      {stage === 'sealed' && (
        <>
          <img className="bs-wait bs-wait-a" src={FX.ringIdle} alt="" />
          <img className="bs-wait bs-wait-b" src={FX.ringActive} alt="" />
        </>
      )}

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
          opacity: gone || hidden ? 0 : 1,
        }}
      />
    </div>
  )
}
