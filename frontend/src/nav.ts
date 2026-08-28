// 라우터 밖(포팅한 프로토타입 스크립트)에서 SPA 이동을 쓰기 위한 통로.
type Navigate = (to: string) => void

let navigate: Navigate = (to) => { window.location.href = to }

export function setNavigate(fn: Navigate) {
  navigate = fn
}

export function nav(to: string) {
  navigate(to)
}
