/* 밀어서 등록 — C-01 마지막 발판.

   왜 그냥 버튼이 아닌가
   예측은 등록하면 수정도 삭제도 안 되고 슬롯도 돌아오지 않는다. 한 번의 오클릭으로
   되돌릴 수 없는 일이 벌어지지 않게, 손잡이를 끝까지 밀어야 넘어가게 한다.

   **키보드로도 되어야 한다.** 끌기만 되는 컨트롤은 마우스가 없으면 못 쓴다.
   손잡이는 진짜 <button> 이라 Enter·Space 로도 넘어간다 — 그쪽에는 밀기라는 관문이
   없지만, 되돌릴 수 없다는 고지가 바로 위에 함께 떠 있다.

   비활성일 때는 끌리지도 눌리지도 않는다. 무엇이 모자란지는 이 컨트롤이 아니라
   위쪽 안내가 말한다 — 발판에 오류 문구까지 얹으면 읽을 것이 두 곳으로 갈린다. */
import { useCallback, useRef, useState } from 'react'
import '../../styles/screens/slide-to-sign.css'

type Props = {
  /** 끝까지 밀었을 때. 키보드로 눌렀을 때도 같은 것을 부른다 */
  onConfirm: () => void
  /** 필수 항목이 덜 찼다 */
  disabled?: boolean
  /** 이미 서명 흐름에 들어갔다. 손잡이 대신 진행 문구를 보여 준다 */
  busy?: boolean
  label?: string
  busyLabel?: string
}

/** 이만큼 밀면 넘어간다. 1 로 두면 끝을 정확히 맞춰야 해서 잘 안 넘어간다 */
const THRESHOLD = 0.9

export default function SlideToSign({
  onConfirm, disabled = false, busy = false,
  label = '밀어서 등록하기', busyLabel = '서명중입니다…',
}: Props) {
  const trackRef = useRef<HTMLDivElement>(null)
  const knobRef = useRef<HTMLButtonElement>(null)
  /** 0~1. 손잡이가 얼마나 갔나 */
  const [at, setAt] = useState(0)
  const dragging = useRef(false)
  /** 이번 끌기에서 이미 넘겼나 — 한 번의 끌기로 두 번 부르지 않게. 끌기마다 풀린다 */
  const fired = useRef(false)
  /** 방금 끌어서 넘겼나 — 그 직후 따라오는 합성 click 하나만 막는다.
      이 값을 안 두고 fired 로 막으면, 서명이 실패해 돌아온 뒤 키보드로 다시
      누를 때도 막힌다(fired 는 다음 끌기 전까지 서 있으므로). */
  const afterDrag = useRef(false)

  const locked = disabled || busy

  const move = useCallback((clientX: number) => {
    const track = trackRef.current, knob = knobRef.current
    if (!track || !knob) return
    const t = track.getBoundingClientRect()
    const w = knob.getBoundingClientRect().width
    /* 손잡이 폭을 빼야 끝까지 밀었을 때 트랙 안에 딱 맞는다 */
    const span = Math.max(1, t.width - w)
    const next = Math.min(1, Math.max(0, (clientX - t.left - w / 2) / span))
    setAt(next)
    if (next >= THRESHOLD && !fired.current) {
      fired.current = true
      afterDrag.current = true
      dragging.current = false
      /* 손잡이를 제자리로 돌려 둔다. 넘어간 직후에는 busy 화면이 덮으므로 보이지
         않고, 서명이 바로 실패해 돌아왔을 때 다 밀린 채로 남지 않는다. */
      setAt(0)
      onConfirm()
    }
  }, [onConfirm])

  const onPointerDown = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (locked) return
    dragging.current = true
    fired.current = false
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  const onPointerMove = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (!dragging.current || locked) return
    move(e.clientX)
  }
  const endDrag = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (!dragging.current) return
    dragging.current = false
    try { e.currentTarget.releasePointerCapture(e.pointerId) } catch { /* 이미 놓였다 */ }
    // 끝까지 못 밀었으면 되돌아온다
    if (!fired.current) setAt(0)
  }

  if (busy) {
    return (
      <div className="sts sts-busy" role="status">
        <span className="sts-spin" aria-hidden="true" />
        {busyLabel}
      </div>
    )
  }

  return (
    <div
      className={`sts${disabled ? ' is-off' : ''}`}
      ref={trackRef}
      /* 채워진 만큼 배경을 물들인다. 손잡이만 움직이면 얼마나 갔는지 잘 안 보인다 */
      style={{ ['--sts-at' as string]: String(at) }}
    >
      <span className="sts-label">{label}</span>
      <button
        type="button"
        ref={knobRef}
        className="sts-knob"
        style={{ transform: `translateX(calc(${at} * (100cqw - 100%)))` }}
        disabled={disabled}
        aria-label={`${label} — 오른쪽 끝까지 밀거나 Enter 를 누르세요`}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        onClick={(e) => {
          /* 끌어서 넘긴 직후에 click 이 한 번 더 온다 — 그 하나만 삼킨다.
             끌지 않고 그냥 누른 것(키보드·탭)은 그대로 통과시킨다. */
          if (afterDrag.current) { afterDrag.current = false; e.preventDefault(); return }
          if (!locked) onConfirm()
        }}
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M5 6l6 6-6 6M13 6l6 6-6 6" />
        </svg>
      </button>
    </div>
  )
}
