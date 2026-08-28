import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'

// 프로토타입 CSS 원본 그대로. 화면별 파일끼리 선택자 충돌이 없어 한 번에 올린다.
import './styles/app.css'
import './styles/auth.css'
import './styles/community.css'
import './styles/mypage.css'
import './styles/portfolio.css'
import './styles/sim-contest.css'
import './styles/sim-home.css'
import './styles/sim-play.css'
import './styles/sim-practice.css'
import './styles/sim-rank.css'
import './styles/sim-record.css'
import './styles/sim-research.css'
import './styles/sim-setup.css'
import './styles/watchlist.css'

// 포팅한 프로토타입 스크립트가 DOM 을 직접 만지므로 StrictMode 의 이중 실행은 켜지 않는다.
createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>
)
