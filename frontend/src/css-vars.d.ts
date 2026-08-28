// 프로토타입 마크업이 인라인 style 로 CSS 변수(--per, --up …)를 넘긴다.
import 'react'

declare module 'react' {
  interface CSSProperties {
    [key: `--${string}`]: string | number
  }
}
