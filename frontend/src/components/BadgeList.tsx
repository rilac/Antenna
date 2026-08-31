/* 획득 배지 목록.

   E-05 마이페이지와 G-09 기록·배지가 같은 컴포넌트를 쓴다(설계서 §4 E-05).
   미획득(잠긴) 배지는 그리지 않는다 — /users/me/badges 는 획득분만 준다.
   배지 레벨 개념도 없다(§9.2 G-09). */
import { badgeLabel, type Badge } from '../api/account'

export default function BadgeList({ badges }: { badges: Badge[] }) {
  if (badges.length === 0) {
    return <p className="badges-empty">아직 획득한 배지가 없습니다</p>
  }

  // 전시 pin 을 앞으로, 그다음 최근 획득 순
  const sorted = [...badges].sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
    return b.createdAt.localeCompare(a.createdAt)
  })

  return (
    <ul className="badges">
      {sorted.map((b) => (
        <li key={`${b.badgeCode}-${b.seasonId ?? 'x'}`} className={`badge-chip${b.pinned ? ' pinned' : ''}`}>
          <span className="badge-mark" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <path d="M7 4h10v5a5 5 0 0 1-10 0z" />
              <path d="M7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3" />
              <path d="M10 19h4M12 14v5M8 21h8" />
            </svg>
          </span>
          {badgeLabel(b.badgeCode)}
          {b.pinned && <em className="badge-pin" title="전시 중">고정</em>}
        </li>
      ))}
    </ul>
  )
}
