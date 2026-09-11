/* E-05 마이페이지 · /me
   담당 스토리 [ANT-FE-MYPAGE]
   설계서 docs/화면설계서.md §3 E · §9.1 · §9.2

   설계 제약
   - 이 화면은 허브다. 스스로 데이터를 많이 만들지 않고 각 화면으로 보낸다.
   - 배지는 획득분만 표시한다. G-09 와 같은 컴포넌트를 쓴다.
   - 두지 않는 것: 핸들(@) · 구독자 수 · 작성 카운트 · 유저 레벨 · 신뢰도 점수 · 친구.
     /users/me 응답에 없고 사용자 등급 필드 자체가 없다. 배지와 랭킹 티어뿐이다.

   배지 섹션은 내려 두었다(S15P21A507-230) — 아래 해당 자리의 주석에 근거가 있다. */
import { Link } from 'react-router-dom'
import { useApiQuery } from '../api/useApiQuery'
import { platformLabel, type MyProfile } from '../api/account'
import ErrorState from '../components/state/ErrorState'
import { useAuth } from '../auth/context'
import '../styles/screens/mypage.css'

/* 허브가 보내는 곳. 목록 화면이 있는 것만 둔다 —
   커뮤니티 내 글은 GET /posts 에 작성자 필터가 없어(§9.2 F-04) 진입점을 만들 수 없다. */
const LINKS = [
  { to: '/me/predictions', label: '내 예측', desc: '등록한 예측과 판정 결과', icon: 'chart' },
  /* 예측 포트폴리오(/me/portfolio)는 여기 두지 않는다 — 사이드바에 늘 떠 있어서
     같은 화면으로 가는 길이 둘이었다. 사이드바가 정본이다(S15P21A507-230). */
  { to: '/me/subscriptions', label: '내 구독', desc: '구독 중인 채널과 갱신 상태', icon: 'people' },
  { to: '/me/wallet', label: '지갑 · 토큰', desc: 'ANT 잔액과 획득·사용 내역', icon: 'wallet' },
  /* 설계상 커밋 원장(D-01)은 C-04 예측 포트폴리오에서 들어간다. 그 화면이 아직
     자리표시자라 온체인 검증 화면 전체가 주소를 직접 쳐야만 닿는 상태여서,
     허브인 여기에 임시 진입점을 둔다. C-04 가 링크를 달면 이 줄은 빼도 된다. */
  { to: '/ledger', label: '커밋 원장', desc: '예측이 체인에 기록된 앵커 배치', icon: 'chain' },
  /* 채널 설정(E-04)을 환경 설정 안으로 합쳤다 — 둘 다 내 계정을 손보는 곳인데
     진입점이 둘로 갈려 있었고 아이콘까지 같아 구분되지 않았다. */
  { to: '/settings', label: '설정', desc: '프로필과 채널, 알림', icon: 'gear' },
] as const

const ICON: Record<string, React.ReactNode> = {
  chart: <><path d="M3 3v18h18" /><path d="M7 15l4-5 3 3 5-7" /></>,
  people: <><path d="M17 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9.5" cy="7" r="3.5" /><path d="M22 20v-2a4 4 0 0 0-3-3.87" /></>,
  wallet: <><path d="M3 7h15a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><path d="M3 7V6a2 2 0 0 1 2-2h11" /><circle cx="16" cy="13" r="1.4" /></>,
  // 사슬 — 온체인 기록
  chain: <><path d="M10 13a5 5 0 0 0 7.5.6l3-3a5 5 0 0 0-7-7l-1.7 1.7" /><path d="M14 11a5 5 0 0 0-7.5-.6l-3 3a5 5 0 0 0 7 7l1.7-1.7" /></>,
  gear: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3.4a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.7 6.3l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V2a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.2a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1" /></>,
}

export default function MyPage() {
  const { user } = useAuth()
  const profile = useApiQuery<MyProfile>('/users/me')

  /* 배열은 여기서 한 번 정규화한다. 백엔드 MeResponse 가 아직 이 필드를
     내려주지 않아 undefined 로 온다 — 아래에서 .length 를 바로 읽으면
     렌더 중 TypeError 가 나 앱 전체가 흰 화면이 된다. */
  const interests = profile.data?.interests ?? []
  const channels = profile.data?.channels ?? []

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

              {interests.length > 0 && (
                <ul className="mp-interests" aria-label="관심 섹터">
                  {interests.map((s) => <li key={s}>{s}</li>)}
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
        {channels.length > 0 && (
          <section className="mp-block">
            <h3>외부 채널</h3>
            <ul className="mp-channels">
              {channels.map((c) => (
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

        {/* ── 배지 ───────────────────────────────────────
            섹션째 내렸다(S15P21A507-230).

            GET /users/me/badges 가 아직 없다. UserBadge 엔티티·리포지토리는 있으나
            컨트롤러가 안 열렸다. 없는 경로라 500 이 와서, 전에는 ErrorState 대신
            "배지는 준비 중입니다" 안내를 그려 두고 있었다 — 서버 문제가 아니고 다시
            시도해도 영영 안 되므로 재시도 안내가 틀린 말이기 때문이다.

            그래도 화면 한 칸을 계속 "준비 중" 으로 채우는 것보다 비우는 편이 낫다.
            지금은 API 가 열려도 시즌 성과가 없어 빈 목록이다.

            API 가 열리면 되살린다 — 그때는
              const badges = useApiQuery<BadgeList>('/users/me/badges')
            와 <BadgeList badges={badges.data?.items ?? []} /> 를 여기에 되돌리고,
            500 분기 없이 ErrorState 를 그대로 쓰면 된다(진짜 서버 오류만 남는다).
            컴포넌트 components/BadgeList.tsx 는 G-09 가 계속 쓰고 있어 그대로 있다. */}

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
