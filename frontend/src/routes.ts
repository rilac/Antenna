import type { ComponentType } from 'react'
import type { Mode } from './Layout'
import Home from './pages/Home'
import Stocks from './pages/Stocks'
import StockDetail from './pages/StockDetail'
import Market from './pages/Market'
import Reports from './pages/Reports'
import Portfolio from './pages/Portfolio'
import Community from './pages/Community'
import CommunityPost from './pages/CommunityPost'
import Watchlist from './pages/Watchlist'
import MyPage from './pages/MyPage'
import Login from './pages/Login'
import PredictionCreate from './pages/PredictionCreate'
import SimHome from './pages/SimHome'
import SimSetup from './pages/SimSetup'
import SimPlay from './pages/SimPlay'
import SimPortfolio from './pages/SimPortfolio'
import SimResearch from './pages/SimResearch'
import SimRanking from './pages/SimRanking'
import SimContest from './pages/SimContest'
import SimPractice from './pages/SimPractice'
import StocksScript from './scripts/Stocks'
import StockDetailScript from './scripts/StockDetail'
import MarketScript from './scripts/Market'
import ReportsScript from './scripts/Reports'
import PortfolioScript from './scripts/Portfolio'
import CommunityScript from './scripts/Community'
import CommunityPostScript from './scripts/CommunityPost'
import WatchlistScript from './scripts/Watchlist'
import MyPageScript from './scripts/MyPage'
import LoginScript from './scripts/Login'
import PredictionCreateScript from './scripts/PredictionCreate'
import SimPlayScript from './scripts/SimPlay'
import SimPortfolioScript from './scripts/SimPortfolio'
import SimResearchScript from './scripts/SimResearch'
import SimContestScript from './scripts/SimContest'
import SimPracticeScript from './scripts/SimPractice'

export type PageMeta = {
  path: string
  element: ComponentType
  title: string
  mode?: Mode
  nav?: string
  bodyClass?: string
  script?: () => void
}

export const ROUTES: PageMeta[] = [
  { path: '/', element: Home, title: "인사이트 홈 · ANTENA", mode: 'insight' as const, nav: 'home', bodyClass: 'insight-home' },
  { path: '/stocks', element: Stocks, title: "종목 보기 · ANTENA", mode: 'insight' as const, nav: 'stocks', bodyClass: 'stocks-page', script: StocksScript },
  { path: '/stock-detail', element: StockDetail, title: "종목 상세 · ANTENA", mode: 'insight' as const, nav: 'stocks', bodyClass: 'stock-detail-page', script: StockDetailScript },
  { path: '/market', element: Market, title: "예측가 둘러보기 · ANTENA", mode: 'insight' as const, nav: 'market', bodyClass: 'predictor-market-page', script: MarketScript },
  { path: '/reports', element: Reports, title: "리포트 · ANTENA", mode: 'insight' as const, nav: 'report', bodyClass: 'reports-page', script: ReportsScript },
  { path: '/portfolio', element: Portfolio, title: "AI Forecast Portfolio · ANTENA", mode: 'insight' as const, nav: 'portfolio', bodyClass: 'forecast-page', script: PortfolioScript },
  { path: '/community', element: Community, title: "커뮤니티 · ANTENA", mode: 'insight' as const, nav: 'community', bodyClass: 'community-list-page', script: CommunityScript },
  { path: '/community/post', element: CommunityPost, title: "반도체훈련소 HBM4 리포트 읽어본 사람? · ANTENA", mode: 'insight' as const, nav: 'community', bodyClass: 'community-page', script: CommunityPostScript },
  { path: '/watchlist', element: Watchlist, title: "찜 · ANTENA", mode: 'insight' as const, nav: 'watchlist', bodyClass: 'watchlist-page', script: WatchlistScript },
  { path: '/mypage', element: MyPage, title: "마이페이지 · ANTENA", mode: 'insight' as const, nav: 'portfolio', bodyClass: 'mypage-page', script: MyPageScript },
  { path: '/login', element: Login, title: "로그인 · ANTENA", script: LoginScript },
  { path: '/prediction/create', element: PredictionCreate, title: "예측 등록 · ANTENA", mode: 'insight' as const, nav: 'market', bodyClass: 'prediction-create-page', script: PredictionCreateScript },
  { path: '/sim', element: SimHome, title: "모의 투자 홈 · ANTENA", mode: 'sim' as const, nav: 'home', bodyClass: 'sim-home-page' },
  { path: '/sim/setup', element: SimSetup, title: "모드 선택 · ANTENA", mode: 'sim' as const, nav: 'play', bodyClass: 'sim-setup-page' },
  { path: '/sim/play', element: SimPlay, title: "모의 투자 진행 · ANTENA", mode: 'sim' as const, nav: 'play', bodyClass: 'sim-play-page', script: SimPlayScript },
  { path: '/sim/portfolio', element: SimPortfolio, title: "게임 기록 · ANTENA", mode: 'sim' as const, nav: 'portfolio', bodyClass: 'sim-record-page', script: SimPortfolioScript },
  { path: '/sim/research', element: SimResearch, title: "리서치 · ANTENA", mode: 'sim' as const, nav: 'research', bodyClass: 'sim-research-page', script: SimResearchScript },
  { path: '/sim/ranking', element: SimRanking, title: "모의 투자 랭크 · ANTENA", mode: 'sim' as const, nav: 'ranking', bodyClass: 'sim-rank-page' },
  { path: '/sim/contest', element: SimContest, title: "대회 모드 · ANTENA", mode: 'sim' as const, nav: 'play', bodyClass: 'sim-contest-page', script: SimContestScript },
  { path: '/sim/practice', element: SimPractice, title: "연습하기 모드 · ANTENA", mode: 'sim' as const, nav: 'play', bodyClass: 'sim-practice-page', script: SimPracticeScript },
]

