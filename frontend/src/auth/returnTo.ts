/* 비로그인 딥링크로 막힌 경로를 기억했다가 로그인 후 되돌려 준다.

   라우터 state 를 쓰지 않는 이유: A-01 로그인은 OAuth 라 인가 서버로 나갔다가
   전체 페이지 로드로 돌아온다. 그 왕복에서 메모리 state 는 사라진다.
   sessionStorage 는 탭이 살아 있는 동안 유지되고 탭을 닫으면 정리된다. */
const KEY = 'antena.returnTo'

export function rememberReturnTo(path: string) {
  // 로그인 화면 자신을 기억하면 로그인 후 다시 로그인으로 돌아온다
  if (path.startsWith('/login')) return
  try { sessionStorage.setItem(KEY, path) } catch { /* 무시 */ }
}

/** 기억해 둔 경로를 꺼내면서 지운다. 없으면 홈. */
export function takeReturnTo(): string {
  try {
    const saved = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    // 외부 주소로 튕기지 않도록 우리 경로만 허용한다
    if (saved && saved.startsWith('/') && !saved.startsWith('//')) return saved
  } catch { /* 무시 */ }
  return '/'
}
