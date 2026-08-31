/* 라우트 표 — docs/화면설계서.md §3 의 화면 목록을 그대로 옮긴다.
   모달(M-*)은 라우트를 갖지 않고, A-04 오류·빈 상태도 라우트가 없다.

   화면 구현은 각 담당 스토리의 몫이라 여기서는 자리(Placeholder)만 잡는다.
   story 는 그 자리를 채울 Jira 스토리의 슬러그다. */
import type { ComponentType } from 'react'
import type { Mode } from './Layout'
import Login from './pages/Login'
import OAuthCallback from './pages/OAuthCallback'
import OnboardingNickname from './pages/OnboardingNickname'
import Search from './pages/Search'
import Home from './pages/Home'
import Stocks from './pages/Stocks'
import StockDetail from './pages/StockDetail'
import Watchlist from './pages/Watchlist'
import PredictNew from './pages/PredictNew'
import PredictList from './pages/PredictList'
import PredictDetail from './pages/PredictDetail'
import Portfolio from './pages/Portfolio'
import LedgerList from './pages/LedgerList'
import Anchor from './pages/Anchor'
import Verify from './pages/Verify'
import Ranking from './pages/Ranking'
import ChannelProfile from './pages/ChannelProfile'
import SubscriptionList from './pages/SubscriptionList'
import ChannelFee from './pages/ChannelFee'
import MyPage from './pages/MyPage'
import Reports from './pages/Reports'
import ReportDetail from './pages/ReportDetail'
import ReportNew from './pages/ReportNew'
import Posts from './pages/Posts'
import PostDetail from './pages/PostDetail'
import PostNew from './pages/PostNew'
import SeasonHome from './pages/SeasonHome'
import SeasonMode from './pages/SeasonMode'
import SeasonJoin from './pages/SeasonJoin'
import SeasonPlay from './pages/SeasonPlay'
import SeasonResearch from './pages/SeasonResearch'
import SeasonTrades from './pages/SeasonTrades'
import Leaderboard from './pages/Leaderboard'
import SeasonResult from './pages/SeasonResult'
import SeasonHistory from './pages/SeasonHistory'
import Wallet from './pages/Wallet'
import Notification from './pages/Notification'
import Settings from './pages/Settings'
import Ad from './pages/Ad'
import AdminAbuse from './pages/AdminAbuse'
import AdminUser from './pages/AdminUser'
import AdminSeason from './pages/AdminSeason'

export type Access =
  /** 비로그인도 볼 수 있다 — 설계서 §1 기준 A-01 하나뿐 */
  | 'public'
  /** JWT 필요 */
  | 'user'
  /** 관리자 전용, 그 외 403 */
  | 'admin'

export type PageMeta = {
  /** 화면설계서 화면 ID */
  screen: string
  /** 이 화면을 그리는 컴포넌트 */
  element: ComponentType
  path: string
  title: string
  access: Access
  /** 셸 모드. 없으면 셸 없이 단독 렌더한다(로그인) */
  mode?: Mode
  /** 사이드바에서 활성으로 표시할 메뉴 키 */
  nav?: string
  bodyClass?: string
  /** 이 화면을 구현할 Jira 스토리 슬러그 */
  story: string
}

export const ROUTES: PageMeta[] = [
  // ── A. 인증 · 공통 ────────────────────────────────────
  { screen: 'A-01', element: Login, path: '/login', title: '로그인', access: 'public', story: 'ANT-FE-LOGIN' },
  /* 구글이 인가 코드를 돌려주는 A-01 의 복귀 구간. 화면설계서에는 없는 경로다.
     access 가 'public' 이어야 한다 — 가드가 막으면 코드를 교환하기도 전에 /login 으로 튕긴다. */
  { screen: 'A-01', element: OAuthCallback, path: '/oauth/callback/google', title: '로그인 중', access: 'public', story: 'ANT-AUTH-01' },
  { screen: 'A-02', element: OnboardingNickname, path: '/onboarding/nickname', title: '닉네임 설정', access: 'user', story: 'ANT-AUTH-03' },
  { screen: 'A-03', element: Search, path: '/search', title: '통합 검색', access: 'user', mode: 'insight', nav: 'stocks', story: 'ANT-FE-SEARCH' },

  // ── B. 인사이트 ──────────────────────────────────────
  { screen: 'B-01', element: Home, path: '/', title: '인사이트 홈', access: 'user', mode: 'insight', nav: 'home', bodyClass: 'insight-home', story: 'ANT-FE-HOME' },
  { screen: 'B-02', element: Stocks, path: '/stocks', title: '종목 탐색', access: 'user', mode: 'insight', nav: 'stocks', bodyClass: 'stocks-page', story: 'ANT-FE-STOCKS' },
  { screen: 'B-03', element: StockDetail, path: '/stocks/:code', title: '종목 상세', access: 'user', mode: 'insight', nav: 'stocks', bodyClass: 'stock-detail-page', story: 'ANT-FE-STOCK-DETAIL' },
  { screen: 'B-04', element: Watchlist, path: '/watchlist', title: '관심 종목', access: 'user', mode: 'insight', nav: 'stocks', bodyClass: 'watchlist-page', story: 'ANT-FE-WATCHLIST' },

  // ── C. 예측 ──────────────────────────────────────────
  { screen: 'C-01', element: PredictNew, path: '/predict', title: '예측 등록', access: 'user', mode: 'insight', nav: 'market', bodyClass: 'prediction-create-page', story: 'ANT-FE-PREDICT-NEW' },
  { screen: 'C-02', element: PredictList, path: '/me/predictions', title: '내 예측', access: 'user', mode: 'insight', nav: 'portfolio', story: 'ANT-FE-PREDICT-LIST' },
  { screen: 'C-03', element: PredictDetail, path: '/predictions/:id', title: '예측 상세', access: 'user', mode: 'insight', nav: 'portfolio', story: 'ANT-FE-PREDICT-DETAIL' },
  { screen: 'C-04', element: Portfolio, path: '/me/portfolio', title: '예측 포트폴리오', access: 'user', mode: 'insight', nav: 'portfolio', bodyClass: 'forecast-page', story: 'ANT-FE-PORTFOLIO' },

  // ── D. 온체인 검증 ───────────────────────────────────
  { screen: 'D-01', element: LedgerList, path: '/ledger', title: '커밋 원장', access: 'user', mode: 'insight', nav: 'portfolio', story: 'ANT-FE-LEDGER-LIST' },
  { screen: 'D-02', element: Anchor, path: '/ledger/anchors/:id', title: '앵커 배치 상세', access: 'user', mode: 'insight', nav: 'portfolio', story: 'ANT-FE-ANCHOR' },
  { screen: 'D-03', element: Verify, path: '/ledger/verify/:predictionId', title: '3단계 검산', access: 'user', mode: 'insight', nav: 'portfolio', story: 'ANT-FE-VERIFY' },

  // ── E. 채널 · 구독 ───────────────────────────────────
  { screen: 'E-01', element: Ranking, path: '/rankings', title: '예측가 랭킹', access: 'user', mode: 'insight', nav: 'market', bodyClass: 'predictor-market-page', story: 'ANT-FE-RANKING' },
  { screen: 'E-02', element: ChannelProfile, path: '/channels/:userId', title: '채널 프로필', access: 'user', mode: 'insight', nav: 'market', story: 'ANT-FE-CHANNEL-PROFILE' },
  { screen: 'E-03', element: SubscriptionList, path: '/me/subscriptions', title: '내 구독', access: 'user', mode: 'insight', nav: 'portfolio', story: 'ANT-FE-SUBSCRIPTION-LIST' },
  { screen: 'E-04', element: ChannelFee, path: '/me/channel', title: '내 채널 설정', access: 'user', mode: 'insight', nav: 'portfolio', story: 'ANT-FE-CHANNEL-FEE' },
  { screen: 'E-05', element: MyPage, path: '/me', title: '마이페이지', access: 'user', mode: 'insight', nav: 'portfolio', bodyClass: 'mypage-page', story: 'ANT-FE-MYPAGE' },

  // ── F. 리포트 · 커뮤니티 ─────────────────────────────
  { screen: 'F-01', element: Reports, path: '/reports', title: '리포트 피드', access: 'user', mode: 'insight', nav: 'report', bodyClass: 'reports-page', story: 'ANT-FE-REPORTS' },
  { screen: 'F-02', element: ReportDetail, path: '/reports/:id', title: '리포트 상세', access: 'user', mode: 'insight', nav: 'report', story: 'ANT-FE-REPORT-DETAIL' },
  { screen: 'F-03', element: ReportNew, path: '/reports/new', title: '리포트 작성', access: 'user', mode: 'insight', nav: 'report', story: 'ANT-FE-REPORT-NEW' },
  { screen: 'F-04', element: Posts, path: '/posts', title: '커뮤니티 피드', access: 'user', mode: 'insight', nav: 'community', bodyClass: 'community-list-page', story: 'ANT-FE-POSTS' },
  { screen: 'F-05', element: PostDetail, path: '/posts/:id', title: '글 상세', access: 'user', mode: 'insight', nav: 'community', bodyClass: 'community-page', story: 'ANT-FE-POST-DETAIL' },
  { screen: 'F-06', element: PostNew, path: '/posts/new', title: '글 작성', access: 'user', mode: 'insight', nav: 'community', story: 'ANT-FE-POST-NEW' },

  // ── G. 모의투자 ──────────────────────────────────────
  { screen: 'G-01', element: SeasonHome, path: '/sim', title: '모의투자 홈', access: 'user', mode: 'sim', nav: 'home', bodyClass: 'sim-home-page', story: 'ANT-FE-SEASON-HOME' },
  { screen: 'G-02', element: SeasonMode, path: '/sim/modes', title: '모드 선택', access: 'user', mode: 'sim', nav: 'play', bodyClass: 'sim-setup-page', story: 'ANT-FE-SEASON-MODE' },
  { screen: 'G-03', element: SeasonJoin, path: '/sim/seasons/:id', title: '시즌 상세 · 참가', access: 'user', mode: 'sim', nav: 'play', story: 'ANT-FE-SEASON-JOIN' },
  { screen: 'G-04', element: SeasonPlay, path: '/sim/:id/play', title: '시즌 진행', access: 'user', mode: 'sim', nav: 'play', bodyClass: 'sim-play-page', story: 'ANT-FE-SEASON-PLAY' },
  // G-05 는 프로토타입에서 제거됐지만 설계서 §3 G 와 스토리가 아직 살아 있다 — 결론 나면 정리한다
  { screen: 'G-05', element: SeasonResearch, path: '/sim/:id/research', title: '시즌 리서치', access: 'user', mode: 'sim', nav: 'play', story: 'ANT-FE-SEASON-RESEARCH' },
  { screen: 'G-06', element: SeasonTrades, path: '/sim/:id/trades', title: '매매일지', access: 'user', mode: 'sim', nav: 'portfolio', story: 'ANT-FE-SEASON-TRADES' },
  { screen: 'G-07', element: Leaderboard, path: '/sim/:id/leaderboard', title: '시즌 리더보드', access: 'user', mode: 'sim', nav: 'ranking', bodyClass: 'sim-rank-page', story: 'ANT-FE-LEADERBOARD' },
  { screen: 'G-08', element: SeasonResult, path: '/sim/:id/result', title: '결과 · AI 복기', access: 'user', mode: 'sim', nav: 'portfolio', story: 'ANT-FE-SEASON-RESULT' },
  { screen: 'G-09', element: SeasonHistory, path: '/sim/history', title: '기록 · 배지', access: 'user', mode: 'sim', nav: 'portfolio', bodyClass: 'sim-record-page', story: 'ANT-FE-SEASON-HISTORY' },

  // ── H. 지갑 · 알림 · 설정 · 광고 ─────────────────────
  { screen: 'H-01', element: Wallet, path: '/me/wallet', title: '지갑 · 토큰', access: 'user', mode: 'insight', nav: 'portfolio', story: 'ANT-FE-WALLET' },
  { screen: 'H-02', element: Notification, path: '/notifications', title: '알림함', access: 'user', mode: 'insight', nav: 'home', story: 'ANT-FE-NOTIFICATION' },
  { screen: 'H-03', element: Settings, path: '/settings', title: '환경 설정', access: 'user', mode: 'insight', nav: 'home', story: 'ANT-FE-SETTINGS' },
  { screen: 'H-04', element: Ad, path: '/ads/new', title: '광고 등록', access: 'user', mode: 'insight', nav: 'home', story: 'ANT-FE-AD' },

  // ── I. 관리자 ────────────────────────────────────────
  { screen: 'I-01', element: AdminAbuse, path: '/admin/abuse-reports', title: '신고 처리', access: 'admin', mode: 'insight', nav: 'home', story: 'ANT-FE-ADMIN-ABUSE' },
  { screen: 'I-02', element: AdminUser, path: '/admin/users', title: '회원 제재', access: 'admin', mode: 'insight', nav: 'home', story: 'ANT-FE-ADMIN-USER' },
  { screen: 'I-03', element: AdminSeason, path: '/admin/seasons', title: '시즌 운영', access: 'admin', mode: 'sim', nav: 'home', story: 'ANT-FE-ADMIN-SEASON' },
]
