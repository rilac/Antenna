// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  // SSO 버튼을 누르면 로그인 상태로 표시한다 (프로토타입)
  // 구글은 실제 OAuth 를 타므로 제외한다. 로그인 성공 시 lib/auth 가 표시한다.
  document.querySelectorAll('.sso-btn:not(.google)').forEach(function (button) {
    button.addEventListener('click', function () {
      try { localStorage.setItem('antena.auth', '1') } catch (e) {}
    })
  })
}
