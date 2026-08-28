// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  (function () {
    // 최근 7일 수익률 추이
    (function chart() {
      var svg = document.getElementById('ct-chart');
      if (!svg) return;
      var v = [-1.8, 0.6, 1.4, -1.5, 5.0, 0.9, 9.2];
      var labels = ['05/07', '05/08', '05/09', '05/10', '05/11', '05/12', '05/13'];
      var L = 62, R = 508, T = 18, B = 172, lo = -5, hi = 10;
      var x = function (i) { return L + i * (R - L) / (v.length - 1); };
      var y = function (n) { return T + (hi - n) / (hi - lo) * (B - T); };
  
      var out = '';
      [10, 5, 0, -5].forEach(function (n) {
        out += '<line x1="' + L + '" y1="' + y(n) + '" x2="' + R + '" y2="' + y(n) + '" stroke="' + (n === 0 ? '#e4e7f0' : '#f4f5fa') + '"/>' +
               '<text x="' + (L - 12) + '" y="' + (y(n) + 5) + '" text-anchor="end" fill="#98a0b6" font-family="monospace" font-size="13" font-weight="700">' +
               (n > 0 ? '+' + n : n) + '%</text>';
      });
  
      var d = v.map(function (n, i) { return (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(n).toFixed(1); }).join(' ');
      out += '<path d="' + d + ' L' + x(v.length - 1).toFixed(1) + ' ' + y(lo) + ' L' + L + ' ' + y(lo) + ' Z" fill="#6355e8" opacity=".05"/>';
      out += '<path d="' + d + '" fill="none" stroke="#5b52e6" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round"/>';
      v.forEach(function (n, i) {
        var last = i === v.length - 1;
        out += '<circle cx="' + x(i).toFixed(1) + '" cy="' + y(n).toFixed(1) + '" r="' + (last ? 5.5 : 4) + '" fill="' + (last ? '#5b52e6' : '#fff') + '" stroke="#5b52e6" stroke-width="2.2"/>';
      });
      labels.forEach(function (t, i) {
        out += '<text x="' + x(i).toFixed(1) + '" y="' + (B + 32) + '" text-anchor="middle" fill="#98a0b6" font-family="monospace" font-size="12.5" font-weight="650">' + t + '</text>';
      });
      svg.innerHTML = out;
    })();
  
    // 규칙 / 보상 탭
    var rules = document.getElementById('ct-rules');
    var prizes = document.getElementById('ct-prize-detail');
    document.querySelectorAll('.ct-tabs button').forEach(function (button) {
      button.addEventListener('click', function () {
        document.querySelectorAll('.ct-tabs button').forEach(function (b) { b.classList.toggle('on', b === button); });
        var showRules = button.dataset.tab === 'rules';
        rules.hidden = !showRules;
        prizes.hidden = showRules;
      });
    });
  })();}
