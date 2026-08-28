// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  /* AI Forecast Portfolio — 5각 역량 그래프와 적중률 추이 */
  (function () {
  
    // ── 5각 레이더
    (function radar() {
      var svg = document.getElementById('fc-radar');
      if (!svg) return;
  
      var axes = [
        { label: '펀더멘털',   value: 82 },
        { label: '수급 분석',   value: 74 },
        { label: '기술적 분석', value: 61 },
        { label: '이벤트 대응', value: 54 },
        { label: '위험 관리',   value: 67 }
      ];
      var cx = 140, cy = 130, R = 78;
      var point = function (i, r) {
        var a = -Math.PI / 2 + i * 2 * Math.PI / axes.length;
        return [cx + Math.cos(a) * r, cy + Math.sin(a) * r];
      };
      var poly = function (r) {
        return axes.map(function (_, i) { return point(i, typeof r === 'function' ? r(i) : r).map(function (n) { return n.toFixed(1); }).join(','); }).join(' ');
      };
  
      var out = '';
      // 격자
      [1, .72, .44].forEach(function (k) {
        out += '<polygon points="' + poly(R * k) + '" fill="none" stroke="#dcdaf3" stroke-width="1"/>';
      });
      axes.forEach(function (_, i) {
        var p = point(i, R);
        out += '<line x1="' + cx + '" y1="' + cy + '" x2="' + p[0].toFixed(1) + '" y2="' + p[1].toFixed(1) + '" stroke="#e4e2f5" stroke-width="1"/>';
      });
  
      // 값
      out += '<polygon points="' + poly(function (i) { return R * axes[i].value / 100; }) + '" fill="#7b6cf0" fill-opacity=".28" stroke="#6355e8" stroke-width="2.4" stroke-linejoin="round"/>';
      axes.forEach(function (a, i) {
        var p = point(i, R * a.value / 100);
        out += '<circle cx="' + p[0].toFixed(1) + '" cy="' + p[1].toFixed(1) + '" r="3.6" fill="#5b52e6"/>';
      });
  
      // 라벨
      axes.forEach(function (a, i) {
        var p = point(i, R + 30);
        var anchor = Math.abs(p[0] - cx) < 6 ? 'middle' : (p[0] > cx ? 'start' : 'end');
        var dx = anchor === 'middle' ? 0 : (anchor === 'start' ? -4 : 4);
        out += '<text x="' + (p[0] + dx).toFixed(1) + '" y="' + (p[1] - 4).toFixed(1) + '" text-anchor="' + anchor + '" fill="#5b6280" font-family="sans-serif" font-size="13" font-weight="750">' + a.label + '</text>';
        out += '<text x="' + (p[0] + dx).toFixed(1) + '" y="' + (p[1] + 14).toFixed(1) + '" text-anchor="' + anchor + '" fill="#141a3c" font-family="monospace" font-size="15" font-weight="850">' + a.value + '</text>';
      });
  
      // 반짝임
      out += '<path d="m246 42 2.6 6.6 6.6 2.6-6.6 2.6-2.6 6.6-2.6-6.6-6.6-2.6 6.6-2.6z" fill="#c9c5f8"/>';
      svg.innerHTML = out;
    })();
  
    // ── 적중률 추이
    (function trend() {
      var svg = document.getElementById('fc-trend');
      if (!svg) return;
  
      var mine = [55,58,63,60,66,62,68,65,64,72,70,74,71,73,70,72,69,73,71,74,72,75,73,71,74,76,73,70,68,66];
      var avg  = [37,36,40,38,42,39,44,42,41,46,45,47,44,46,43,45,42,48,46,49,47,50,48,46,49,51,49,47,48,50];
      var L = 74, R = 830, T = 22, B = 236;
  
      var x = function (i) { return L + i * (R - L) / (mine.length - 1); };
      var y = function (v) { return T + (100 - v) / 100 * (B - T); };
      var path = function (arr) {
        return arr.map(function (v, i) { return (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1); }).join(' ');
      };
  
      var out = '';
      [100, 75, 50, 25, 0].forEach(function (v) {
        out += '<line x1="' + L + '" y1="' + y(v) + '" x2="' + R + '" y2="' + y(v) + '" stroke="#f2f4f9"/>' +
               '<text x="' + (L - 14) + '" y="' + (y(v) + 5) + '" text-anchor="end" fill="#98a0b6" font-family="monospace" font-size="13" font-weight="700">' + v + '%</text>';
      });
  
      out += '<path d="' + path(mine) + ' L' + x(mine.length - 1).toFixed(1) + ' ' + B + ' L' + L + ' ' + B + ' Z" fill="#6355e8" opacity=".06"/>';
      out += '<path d="' + path(avg) + '" fill="none" stroke="#c2c8da" stroke-width="1.8" stroke-dasharray="5 4"/>';
      out += '<path d="' + path(mine) + '" fill="none" stroke="#5b52e6" stroke-width="2.4" stroke-linejoin="round"/>';
  
      avg.forEach(function (v, i) {
        out += '<circle cx="' + x(i).toFixed(1) + '" cy="' + y(v).toFixed(1) + '" r="2.6" fill="#fff" stroke="#c2c8da" stroke-width="1.4"/>';
      });
      mine.forEach(function (v, i) {
        var last = i === mine.length - 1;
        out += '<circle cx="' + x(i).toFixed(1) + '" cy="' + y(v).toFixed(1) + '" r="' + (last ? 6 : 3.2) + '" fill="' + (last ? '#5b52e6' : '#fff') + '" stroke="#5b52e6" stroke-width="' + (last ? 2 : 1.6) + '"/>';
      });
  
      // 마지막 값 배지
      var lx = x(mine.length - 1), ly = y(mine[mine.length - 1]);
      out += '<rect x="' + (lx + 12) + '" y="' + (ly - 46) + '" width="76" height="30" rx="9" fill="#fff" stroke="#cdc9f7"/>' +
             '<text x="' + (lx + 50) + '" y="' + (ly - 26) + '" text-anchor="middle" fill="#4f46e5" font-family="monospace" font-size="14.5" font-weight="850">66.0%</text>';
  
      ['12/02','01/02','02/02','03/02','04/02','05/02','06/02'].forEach(function (t, i) {
        var tx = L + i * (R - L) / 6;
        out += '<text x="' + tx.toFixed(1) + '" y="' + (B + 30) + '" text-anchor="middle" fill="#98a0b6" font-family="monospace" font-size="13" font-weight="650">' + t + '</text>';
      });
  
      svg.innerHTML = out;
    })();
  
    // ── 탭 (기간 · 지표)
    ['.fc-ranges button', '.fc-metrics button'].forEach(function (selector) {
      var buttons = document.querySelectorAll(selector);
      buttons.forEach(function (button) {
        button.addEventListener('click', function () {
          buttons.forEach(function (b) { b.classList.toggle('on', b === button); });
        });
      });
    });
  })();
}
