import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    /* api/client 가 /api/v1 상대경로로만 호출한다(같은 출처라 CORS 가 없다).
       배포 환경에서는 nginx 가 이 경로를 백엔드로 넘긴다. 로컬 개발 서버에도
       같은 규칙이 있어야 로그인이 동작한다. 없으면 /api/v1 요청이 5173 의
       SPA fallback 에 걸려 index.html 이 200 으로 돌아온다. */
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
