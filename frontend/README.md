# ANTENA 프론트엔드

정적 HTML 프로토타입(20화면)을 React + Vite SPA 로 재구성한 코드입니다.

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # tsc -b && vite build
npm run typecheck
npm run lint
```

## 구조

| 경로 | 내용 |
| --- | --- |
| `src/routes.ts` | 화면 20개의 라우트·제목·셸 모드·페이지 스크립트 레지스트리 |
| `src/App.tsx` | 라우터, 화면 래퍼(`Page`), `<a href="/…">` 를 SPA 이동으로 가로채는 핸들러 |
| `src/Layout.tsx` | 공용 셸 — 상단바(검색 포함)와 사이드바. 프로토타입 `app.js` 의 React 포팅 |
| `src/components/Carousel.tsx` | 캐러셀. 프로토타입 `carousel.js` 를 상태 기반 React 컴포넌트로 포팅 |
| `src/pages/*.tsx` | 프로토타입 HTML 을 JSX 로 변환한 화면 마크업 (클래스명·구조 원본 유지) |
| `src/scripts/*.ts` | 화면별 프로토타입 스크립트. 마운트 후 `useEffect` 에서 한 번 실행 |
| `src/styles/*.css` | 프로토타입 CSS 원본. 화면별 파일끼리 선택자 충돌이 없어 전역으로 한 번에 로드 |
| `public/assets/` | 이미지·SVG. 마크업에서 `/assets/…` 로 참조 |

## 알아둘 점

- `src/pages/*.tsx` 는 프로토타입 HTML 에서 기계 변환한 결과입니다. 지금부터는 이 tsx 가 원본이고, 자유롭게 손보면 됩니다.
- `src/scripts/*.ts` 는 프로토타입의 바닐라 JS 를 그대로 옮긴 것이라 DOM 을 직접 다룹니다. 대부분 차트 그리기와 목록 필터라서, 실제 API 를 붙이며 해당 화면을 다시 쓸 때 자연스럽게 걷어내게 됩니다. 이 스크립트들이 DOM 을 직접 만지기 때문에 `main.tsx` 에서 `StrictMode` 를 쓰지 않습니다(이중 실행 시 리스너가 중복 등록됨).
- 캐러셀만은 예외로 처음부터 React 컴포넌트로 만들었습니다. 데이터와 무관한 영구 UI 인데다, 유일하게 React 가 소유한 DOM 을 재구성하던 스크립트라 화면에 상태가 생기면 충돌합니다.
- 로그인은 프로토타입과 동일하게 `localStorage` 의 `antena.auth` 로만 흉내냅니다.
