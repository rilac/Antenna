/* ANTENA 공용 캐러셀 (CSS scroll-snap 기반) — 프로토타입 assets/carousel.js 의 React 포팅.
   자식들이 그대로 슬라이드가 된다.
   - 한 번에 보이는 개수는 CSS 변수 --per, 간격은 --car-gap 으로 화면별 지정
   - 페이지가 하나뿐이면 컨트롤이 숨고 기존 그리드처럼 보인다 (.car-static) */
import { Children, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

type Props = {
  as?: 'div' | 'section'
  className?: string
  autoplay?: number   // ms, 0 이면 자동 순환 없음
  arrows?: boolean
  children: React.ReactNode
} & React.HTMLAttributes<HTMLElement>

export default function Carousel({ as: Tag = 'div', className, autoplay = 0, arrows = true, children, ...rest }: Props) {
  const trackRef = useRef<HTMLDivElement>(null)
  const holdRef = useRef(false)
  const count = Children.count(children)
  const [pages, setPages] = useState(1)
  const [page, setPage] = useState(0)

  const pageWidth = useCallback(() => {
    const track = trackRef.current
    if (!track) return 1
    const gap = parseFloat(getComputedStyle(track).columnGap) || 0
    return Math.max(1, track.clientWidth + gap)
  }, [])

  // --per 은 미디어 쿼리로 바뀌므로 잰다
  const measure = useCallback(() => {
    const track = trackRef.current
    if (!track) return
    const per = Math.max(1, Math.round(parseFloat(getComputedStyle(track).getPropertyValue('--per')) || 1))
    const next = Math.max(1, Math.ceil(count / per))
    setPages(next)
    setPage((p) => Math.min(p, next - 1))
  }, [count])

  useLayoutEffect(measure, [measure])

  useEffect(() => {
    const track = trackRef.current
    if (!track) return
    const observer = new ResizeObserver(measure)   // 숨김→표시 전환도 감지
    observer.observe(track)
    window.addEventListener('resize', measure)
    return () => { observer.disconnect(); window.removeEventListener('resize', measure) }
  }, [measure])

  const goTo = useCallback((target: number) => {
    const track = trackRef.current
    if (!track) return
    const next = ((target % pages) + pages) % pages
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches
    track.scrollTo({ left: next * pageWidth(), behavior: reduce ? 'auto' : 'smooth' })
    setPage(next)   // 스크롤 완료를 기다리지 않고 즉시 반영
  }, [pages, pageWidth])

  // 자동 순환 — 마우스·포커스·터치 중에는 멈춘다
  useEffect(() => {
    if (!autoplay || pages <= 1) return
    const timer = setInterval(() => {
      if (!holdRef.current && !document.hidden) goTo(page + 1)
    }, autoplay)
    return () => clearInterval(timer)
  }, [autoplay, pages, page, goTo])

  const hold = (on: boolean) => () => { holdRef.current = on }
  const cls = [className, pages <= 1 ? 'car-static' : ''].filter(Boolean).join(' ')

  return (
    <Tag
      {...rest}
      className={cls}
      data-carousel=""
      onMouseEnter={hold(true)} onMouseLeave={hold(false)}
      onFocus={hold(true)} onBlur={hold(false)}
      onTouchStart={hold(true)}
    >
      <div
        className="car-track" tabIndex={0} ref={trackRef}
        onScroll={() => {
          const track = trackRef.current
          if (track) setPage(Math.max(0, Math.min(pages - 1, Math.round(track.scrollLeft / pageWidth()))))
        }}
        onKeyDown={(event) => {
          if (event.key === 'ArrowLeft') { event.preventDefault(); goTo(page - 1) }
          if (event.key === 'ArrowRight') { event.preventDefault(); goTo(page + 1) }
        }}
      >
        {children}
      </div>

      {arrows && (
        <>
          <button type="button" className="car-arrow prev" aria-label="이전" disabled={page === 0} onClick={() => goTo(page - 1)}>‹</button>
          <button type="button" className="car-arrow next" aria-label="다음" disabled={page === pages - 1} onClick={() => goTo(page + 1)}>›</button>
        </>
      )}

      <div className="car-dots">
        {Array.from({ length: pages }, (_, i) => (
          <button
            key={i} type="button" className={i === page ? 'car-dot on' : 'car-dot'}
            aria-label={`${i + 1} / ${pages}`} onClick={() => goTo(i)}
          />
        ))}
      </div>
    </Tag>
  )
}
