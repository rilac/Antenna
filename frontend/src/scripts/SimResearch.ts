// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  (function () {
    document.querySelectorAll('.rs-source').forEach(function (button) {
      button.addEventListener('click', function () {
        document.querySelectorAll('.rs-source').forEach(function (b) {
          b.classList.toggle('on', b === button);
          b.setAttribute('aria-pressed', String(b === button));
        });
      });
    });
  })();}
