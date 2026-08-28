// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  (function () {
    var body = document.body;
    var KEY = 'antena.practiceLevel';
  
    // 최근 8회 성장 추이
    (function spark() {
      var svg = document.getElementById('pr-spark');
      if (!svg) return;
      var v = [18, 26, 34, 31, 42, 56, 49, 68];
      var L = 8, R = 250, T = 12, B = 76;
      var x = function (i) { return L + i * (R - L) / (v.length - 1); };
      var y = function (n) { return B - (n / 80) * (B - T); };
      var d = v.map(function (n, i) { return (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(n).toFixed(1); }).join(' ');
      var out = '<path d="' + d + '" fill="none" stroke="#7b6cf0" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round"/>';
      v.forEach(function (n, i) {
        out += '<circle cx="' + x(i).toFixed(1) + '" cy="' + y(n).toFixed(1) + '" r="3.6" fill="#7b6cf0"/>';
      });
      svg.innerHTML = out;
    })();
  
    // 난이도 전환 — 선택을 기억한다
    function setLevel(level, remember) {
      body.dataset.level = level;
      document.querySelectorAll('.pr-levels button').forEach(function (b) {
        b.classList.toggle('on', b.dataset.level === level);
      });
      var go = document.getElementById('pr-go');
      go.href = '/sim/play?mode=learn&view=' + (level === 'pro' ? 'pro' : 'basic');
      if (remember) { try { localStorage.setItem(KEY, level); } catch (e) {} }
    }
    document.querySelectorAll('.pr-levels button').forEach(function (button) {
      button.addEventListener('click', function () { setLevel(button.dataset.level, true); });
    });
    var saved = null;
    try { saved = localStorage.getItem(KEY); } catch (e) {}
    setLevel(new URLSearchParams(location.search).get('level') || saved || 'basic', false);
  })();}
