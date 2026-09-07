import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'

// 셸 CSS. 화면별 CSS 는 각 화면 스토리에서 함께 가져온다.
import './styles/app.css'
// 전 화면이 공유하는 오류·빈 상태·잠금 표현
import './styles/state.css'
import './styles/prediction.css'

createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <AuthProvider>
      <App />
    </AuthProvider>
  </BrowserRouter>
)
