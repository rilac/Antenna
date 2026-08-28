// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  /* 게임 기록 — 수익률 추이 차트와 히스토리 필터 */
  (function () {
    // ── 수익률 추이 (최근 10회)
    (function drawTrend() {
      var svg = document.getElementById('sr-chart');
      if (!svg) return;
  
      var mine  = [8, 16, 10, 25, 9, 19, -2, 2, 12, 20];
      var bench = [-20, -19, -9, -7, -8, -3, -12, -9, -7, -5];
      var L = 56, R = 566, T = 26, B = 232, lo = -40, hi = 40;
  
      var x = function (i) { return L + i * (R - L) / (mine.length - 1); };
      var y = function (v) { return T + (hi - v) / (hi - lo) * (B - T); };
      var path = function (arr) {
        return arr.map(function (v, i) { return (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1); }).join(' ');
      };
  
      var out = '';
      // 배경 눈금
      [40, 20, 0, -20, -40].forEach(function (v) {
        out += '<line x1="' + L + '" y1="' + y(v) + '" x2="' + R + '" y2="' + y(v) + '" stroke="' + (v === 0 ? '#e4e7f0' : '#f2f4f9') + '"/>' +
               '<text x="' + (L - 12) + '" y="' + (y(v) + 5) + '" text-anchor="end" fill="#98a0b6" font-family="monospace" font-size="13.5" font-weight="700">' + v + '%</text>';
      });
  
      // 내 수익률 영역 + 선
      out += '<path d="' + path(mine) + ' L' + x(mine.length - 1) + ' ' + y(0) + ' L' + L + ' ' + y(0) + ' Z" fill="#5457e8" opacity=".07"/>';
      out += '<path d="' + path(bench) + '" fill="none" stroke="#b6bccd" stroke-width="2" stroke-dasharray="6 5" stroke-linejoin="round"/>';
      out += '<path d="' + path(mine) + '" fill="none" stroke="#5457e8" stroke-width="2.6" stroke-linejoin="round" stroke-linecap="round"/>';
  
      bench.forEach(function (v, i) {
        out += '<circle cx="' + x(i).toFixed(1) + '" cy="' + y(v).toFixed(1) + '" r="3.4" fill="#fff" stroke="#b6bccd" stroke-width="2"/>';
      });
      mine.forEach(function (v, i) {
        out += '<circle cx="' + x(i).toFixed(1) + '" cy="' + y(v).toFixed(1) + '" r="4.6" fill="#fff" stroke="#5457e8" stroke-width="2.6"/>';
      });
  
      // 마지막 회차 말풍선
      var lx = x(mine.length - 1), ly = y(mine[mine.length - 1]);
      out += '<rect x="' + (lx - 44) + '" y="' + (ly - 62) + '" width="88" height="32" rx="9" fill="#fff" stroke="#cdc9f7"/>' +
             '<text x="' + lx + '" y="' + (ly - 41) + '" text-anchor="middle" fill="#4f46e5" font-family="monospace" font-size="15" font-weight="800">+25.3%</text>' +
             '<path d="M' + (lx - 6) + ' ' + (ly - 30) + 'h12l-6 7z" fill="#fff" stroke="#cdc9f7"/>';
  
      // x축
      mine.forEach(function (v, i) {
        out += '<text x="' + x(i).toFixed(1) + '" y="' + (B + 32) + '" text-anchor="middle" fill="#98a0b6" font-family="monospace" font-size="13.5" font-weight="650">' + (i + 1) + '회</text>';
      });
  
      svg.innerHTML = out;
    })();
  
    // ── 히스토리 필터
    var rows = Array.prototype.slice.call(document.querySelectorAll('#sr-rows tr'));
    var empty = document.getElementById('sr-empty');
  
    function apply() {
      var season = document.getElementById('f-season').value;
      var level = document.getElementById('f-level').value;
      var ret = document.getElementById('f-return').value;
      var shown = 0;
  
      rows.forEach(function (row) {
        var value = Number(row.dataset.return);
        var ok =
          (season === 'all' || row.dataset.season === season) &&
          (level === 'all' || row.dataset.level === level) &&
          (ret === 'all' || (ret === 'plus' ? value >= 0 : value < 0));
        row.hidden = !ok;
        if (ok) shown++;
      });
  
      empty.hidden = shown > 0;
    }
  
    ['f-season', 'f-level', 'f-return'].forEach(function (id) {
      document.getElementById(id).addEventListener('change', apply);
    });
  
    document.getElementById('sr-more').addEventListener('click', function (event) {
      event.currentTarget.textContent = '마지막 기록까지 모두 불러왔어요';
      event.currentTarget.disabled = true;
    });
  
    apply();
  })();
}
