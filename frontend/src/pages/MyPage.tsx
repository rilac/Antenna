/* E-05 마이페이지 · /me
   담당 스토리 [ANT-FE-MYPAGE]
   설계서 docs/화면설계서.md §3 E · §9.1 · §9.2

   설계 제약
   - 이 화면은 허브다. 스스로 데이터를 많이 만들지 않고 각 화면으로 보낸다.
   - 배지는 획득분만 표시한다. G-09 와 같은 컴포넌트를 쓴다.
   - 두지 않는 것: 핸들(@) · 구독자 수 · 작성 카운트 · 유저 레벨 · 신뢰도 점수 · 친구.
     /users/me 응답에 없고 사용자 등급 필드 자체가 없다. 배지와 랭킹 티어뿐이다. */
import { Link } from 'react-router-dom'
import { useApiQuery } from '../api/useApiQuery'
import { platformLabel, type BadgeList as BadgeListResponse, type MyProfile } from '../api/account'
import BadgeList from '../components/BadgeList'
import ErrorState from '../components/state/ErrorState'
import { useAuth } from '../auth/context'
import '../styles/screens/mypage.css'

/* 허브가 보내는 곳. 목록 화면이 있는 것만 둔다 —
   커뮤니티 내 글은 GET /posts 에 작성자 필터가 없어(§9.2 F-04) 진입점을 만들 수 없다. */
const LINKS = [
  { to: '/me/predictions', label: '내 예측', desc: '등록한 예측과 판정 결과', icon: 'chart' },
  { to: '/me/portfolio', label: '예측 포트폴리오', desc: '누적 성과와 온체인 기록', icon: 'case' },
  { to: '/me/subscriptions', label: '내 구독', desc: '구독 중인 채널과 갱신 상태', icon: 'people' },
  { to: '/me/wallet', label: '지갑 · 토큰', desc: 'ANT 잔액과 획득·사용 내역', icon: 'wallet' },
  { to: '/me/channel', label: '내 채널 설정', desc: '구독료와 변경 이력', icon: 'gear' },
  { to: '/settings', label: '환경 설정', desc: '알림과 트레이딩 기본 뷰', icon: 'gear' },
] as const

const ICON: Record<string, React.ReactNode> = {
  chart: <><path d="M3 3v18h18" /><path d="M7 15l4-5 3 3 5-7" /></>,
  case: <><path d="M3 7h18v13H3z" /><path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2" /><path d="M3 12h18" /></>,
  people: <><path d="M17 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9.5" cy="7" r="3.5" /><path d="M22 20v-2a4 4 0 0 0-3-3.87" /></>,
  wallet: <><path d="M3 7h15a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><path d="M3 7V6a2 2 0 0 1 2-2h11" /><circle cx="16" cy="13" r="1.4" /></>,
  gear: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3.4a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.7 6.3l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V2a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.2a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1" /></>,
}

export default function MyPage() {
  const { user } = useAuth()
  const profile = useApiQuery<MyProfile>('/users/me')
  const badges = useApiQuery<BadgeListResponse>('/users/me/badges')

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>마이페이지</h1>
          <p>내 프로필과 활동을 한곳에서 확인합니다</p>
        </div>

        {/* ── 프로필 요약 ──────────────────────────────── */}
        {profile.error ? (
          <ErrorState error={profile.error} onRetry={profile.reload} />
        ) : (
          <section className="mp-profile">
            {/* /users/me 에 프로필 이미지 필드가 없다 — 셸이 쓰는 기본 이미지를 그대로 쓴다 */}
            <img className="mp-avatar" src={user?.avatarUrl} alt="" />

            <div className="mp-who">
              <h2>{profile.data ? profile.data.nickname : '…'}</h2>
              <p className="mp-introduce">
                {profile.data?.introduce || '소개가 아직 없습니다'}
              </p>

              {profile.data && profile.data.interests.length > 0 && (
                <ul className="mp-interests" aria-label="관심 섹터">
                  {profile.data.interests.map((s) => <li key={s}>{s}</li>)}
                </ul>
              )}
            </div>

            <div className="mp-actions">
              {/* 내가 쓴 리포트·예측은 내 채널 공개 페이지(E-02)에서 보인다 */}
              {profile.data && (
                <Link className="mp-btn solid" to={`/channels/${profile.data.id}`}>
                  내 채널 보기
                </Link>
              )}
              <Link className="mp-btn ghost" to="/settings">프로필 수정</Link>
            </div>
          </section>
        )}

        {/* ── 외부 채널 ────────────────────────────────── */}
        {profile.data && profile.data.channels.length > 0 && (
          <section className="mp-block">
            <h3>외부 채널</h3>
            <ul className="mp-channels">
              {profile.data.channels.map((c) => (
                <li key={c.url}>
                  {/* 명세상 https 만 저장된다. 외부로 나가므로 새 탭 + noreferrer */}
                  <a href={c.url} target="_blank" rel="noreferrer noopener">
                    <span className="mp-channel-platform">{platformLabel(c.platform)}</span>
                    <span className="mp-channel-url">{c.url}</span>
                  </a>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* ── 배지 ─────────────────────────────────────── */}
        <section className="mp-block">
          <h3>배지</h3>
          {badges.error
            ? <ErrorState error={badges.error} onRetry={badges.reload} inline />
            : <BadgeList badges={badges.data?.items ?? []} />}
        </section>

        {/* ── 활동 진입점 ──────────────────────────────── */}
        <section className="mp-block">
          <h3>내 활동</h3>
          <ul className="mp-links">
            {LINKS.map((l) => (
              <li key={l.to}>
                <Link to={l.to}>
                  <span className="mp-link-ic">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                      {ICON[l.icon]}
                    </svg>
                  </span>
                  <span className="mp-link-text">
                    <b>{l.label}</b>
                    <small>{l.desc}</small>
                  </span>
                  <em className="mp-link-go" aria-hidden="true">›</em>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </main>
  )
}
