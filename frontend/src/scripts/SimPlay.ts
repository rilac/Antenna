// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  /* 모의 투자 진행 — 초보자/상급자 모드 전환, 차트 렌더, 주문 계산 */
  (function () {
    var body = document.body;
    var q = new URLSearchParams(location.search);
    var won = function (n) { return Math.round(n).toLocaleString('ko-KR'); };
    var PRICE = 78500;
  
    // ── 화면 모드 (초보자 / 상급자) — 선택을 기억한다
    var KEY = 'antena.simView';
    function stored() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
    function setView(view, remember) {
      body.dataset.view = view;
      document.querySelectorAll('[data-view]').forEach(function (b) {
        if (b.tagName === 'BUTTON') b.classList.toggle('on', b.dataset.view === view);
      });
      if (remember) { try { localStorage.setItem(KEY, view); } catch (e) {} }
    }
    document.querySelectorAll('.sp-viewswitch button').forEach(function (button) {
      button.addEventListener('click', function () { setView(button.dataset.view, true); });
    });
    setView(q.get('view') || stored() || 'basic', false);
  
    // ── 초보자 차트: 일중 라인
    (function drawBasic() {
      var svg = document.getElementById('sp-chart-basic');
      if (!svg) return;
      var W = 980, H = 380, L = 8, R = 900, T = 16, B = 300;
      var lo = 71000, hi = 85000;
      var pts = [83100,82400,83600,82700,81900,83200,82100,80900,81600,80200,79400,80100,79000,78200,
                 78800,77900,77100,77800,76900,76200,77000,76300,75600,76400,75800,76900,76200,77100,
                 76500,77400,76800,77600,76900,77800,77200,78000,77400,78300,77700,78500];
      var x = function (i) { return L + i * (R - L) / (pts.length - 1); };
      var y = function (v) { return T + (hi - v) / (hi - lo) * (B - T); };
      var line = pts.map(function (v, i) { return (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1); }).join(' ');
      var out = '';
      [84000, 80000, 76000, 72000].forEach(function (v) {
        out += '<line x1="' + L + '" y1="' + y(v) + '" x2="' + R + '" y2="' + y(v) + '" stroke="#f0f2f7"/>' +
               '<text x="' + (R + 14) + '" y="' + (y(v) + 5) + '" fill="#98a0b6" font-family="monospace" font-size="15" font-weight="700">' + won(v) + '</text>';
      });
      out += '<path d="' + line + ' L' + x(pts.length - 1) + ' ' + B + ' L' + L + ' ' + B + ' Z" fill="#5457e8" opacity=".05"/>';
      out += '<path d="' + line + '" fill="none" stroke="#5457e8" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round"/>';
      var lastX = x(pts.length - 1), lastY = y(pts[pts.length - 1]);
      out += '<circle cx="' + lastX + '" cy="' + lastY + '" r="5" fill="#5457e8"/>';
      out += '<rect x="' + (R + 6) + '" y="' + (lastY - 17) + '" width="94" height="34" rx="9" fill="#5457e8"/>' +
             '<text x="' + (R + 53) + '" y="' + (lastY + 6) + '" text-anchor="middle" fill="#fff" font-family="monospace" font-size="16" font-weight="800">' + won(PRICE) + '</text>';
      ['09:00','10:00','11:00','12:00','13:00','14:00','15:00'].forEach(function (t, i) {
        var tx = L + i * (R - L) / 6;
        out += '<text x="' + tx + '" y="' + (B + 34) + '" text-anchor="middle" fill="#98a0b6" font-family="monospace" font-size="15" font-weight="650">' + t + '</text>';
      });
      svg.innerHTML = out;
    })();
  
    // ── 상급자 차트: 일봉 캔들 + 이동평균 + 거래량
    (function drawPro() {
      var svg = document.getElementById('sp-chart-pro');
      if (!svg) return;
      var W = 980, L = 8, R = 880, T = 14, B = 300, VT = 340, VB = 430;
      var lo = 66000, hi = 87000;
      // [시가, 고가, 저가, 종가, 거래량(백만)]
      var seed = [77.2,78.4,77.9,79.1,80.3,79.6,81.2,82.4,81.7,83.1,82.2,80.9,81.8,80.1,78.6,79.4,
                  77.8,76.2,74.9,73.4,71.8,70.6,69.4,70.8,72.1,71.2,73.4,72.6,74.1,73.2,74.8,75.6,
                  74.9,76.1,75.4,76.8,76.1,77.2,76.4,77.6,76.9,77.4,78.1,77.5,78.9,78.2,77.8,78.5];
      var bars = seed.map(function (c, i) {
        var o = i ? seed[i - 1] : c - .4;
        var h = Math.max(o, c) + ((i * 37) % 7) / 10 + .2;
        var l = Math.min(o, c) - ((i * 53) % 7) / 10 - .2;
        var v = 6 + ((i * 29) % 11);
        return { o: o * 1000, h: h * 1000, l: l * 1000, c: c * 1000, v: v };
      });
      var step = (R - L) / bars.length;
      var cw = Math.max(4, step * .58);
      var y = function (v) { return T + (hi - v) / (hi - lo) * (B - T); };
      var maxV = 20;
      var vy = function (v) { return VB - v / maxV * (VB - VT); };
  
      var out = '';
      [86000, 82000, 78000, 76000, 72000, 68000].forEach(function (v) {
        out += '<line x1="' + L + '" y1="' + y(v) + '" x2="' + R + '" y2="' + y(v) + '" stroke="#f2f4f9"/>' +
               '<text x="' + (R + 12) + '" y="' + (y(v) + 5) + '" fill="#98a0b6" font-family="monospace" font-size="14" font-weight="700">' + won(v) + '</text>';
      });
  
      bars.forEach(function (b, i) {
        var cx = L + step * i + step / 2;
        var up = b.c >= b.o;
        var color = up ? '#e0455a' : '#2f6fe0';
        out += '<line x1="' + cx.toFixed(1) + '" y1="' + y(b.h).toFixed(1) + '" x2="' + cx.toFixed(1) + '" y2="' + y(b.l).toFixed(1) + '" stroke="' + color + '" stroke-width="1.2"/>';
        var top = y(Math.max(b.o, b.c)), bot = y(Math.min(b.o, b.c));
        out += '<rect x="' + (cx - cw / 2).toFixed(1) + '" y="' + top.toFixed(1) + '" width="' + cw.toFixed(1) + '" height="' + Math.max(1.5, bot - top).toFixed(1) + '" fill="' + (up ? color : '#fff') + '" stroke="' + color + '" stroke-width="1.2"/>';
        out += '<rect x="' + (cx - cw / 2).toFixed(1) + '" y="' + vy(b.v).toFixed(1) + '" width="' + cw.toFixed(1) + '" height="' + (VB - vy(b.v)).toFixed(1) + '" fill="' + color + '" opacity=".38"/>';
      });
  
      function ma(n, color) {
        var d = '';
        bars.forEach(function (b, i) {
          if (i < n - 1) return;
          var sum = 0;
          for (var k = i - n + 1; k <= i; k++) sum += bars[k].c;
          var cx = L + step * i + step / 2;
          d += (d ? 'L' : 'M') + cx.toFixed(1) + ' ' + y(sum / n).toFixed(1) + ' ';
        });
        return '<path d="' + d + '" fill="none" stroke="' + color + '" stroke-width="2" stroke-linejoin="round"/>';
      }
      out += ma(5, '#f0a72c') + ma(20, '#7b6cf0') + ma(60 > bars.length ? bars.length : 12, '#16a06a');
  
      out += '<text x="' + L + '" y="' + (VT - 8) + '" fill="#79809c" font-family="monospace" font-size="13" font-weight="700">거래량 12.53M</text>';
      [30, 15, 0].forEach(function (v) {
        out += '<text x="' + (R + 12) + '" y="' + (vy(v) + 5) + '" fill="#98a0b6" font-family="monospace" font-size="13" font-weight="650">' + (v ? v + 'M' : '0') + '</text>';
      });
  
      ['06/18','07/02','07/16','07/30','08/13','08/27','09/10','09/19'].forEach(function (t, i) {
        var tx = L + i * (R - L) / 7;
        out += '<text x="' + tx + '" y="' + (VB + 24) + '" text-anchor="middle" fill="#98a0b6" font-family="monospace" font-size="13.5" font-weight="650">' + t + '</text>';
      });
  
      var lastY = y(bars[bars.length - 1].c);
      out += '<rect x="' + (R + 4) + '" y="' + (lastY - 14) + '" width="86" height="28" rx="7" fill="#5457e8"/>' +
             '<text x="' + (R + 47) + '" y="' + (lastY + 5) + '" text-anchor="middle" fill="#fff" font-family="monospace" font-size="14" font-weight="800">' + won(PRICE) + '</text>';
      svg.innerHTML = out;
    })();
  
    // ── 주문
    var qty = document.getElementById('sp-qty');
    function value() { return Math.max(0, parseInt(String(qty.value).replace(/[^0-9]/g, ''), 10) || 0); }
    function render() {
      var n = value();
      qty.value = n.toLocaleString('ko-KR');
      var amount = n * PRICE;
      var fee = Math.round(amount * 0.0002);
      document.getElementById('sp-amount').textContent = won(amount) + '원';
      document.getElementById('sp-fee').textContent = won(fee) + '원';
      document.getElementById('sp-grand').textContent = won(amount + fee) + '원';
    }
    function bump(delta) { qty.value = String(value() + delta); render(); }
  
    document.getElementById('sp-minus').addEventListener('click', function () { bump(-10); });
    document.getElementById('sp-plus').addEventListener('click', function () { bump(10); });
    document.querySelectorAll('[data-add]').forEach(function (b) {
      b.addEventListener('click', function () { bump(Number(b.dataset.add)); });
    });
    qty.addEventListener('input', function () {
      var n = value();
      var amount = n * PRICE, fee = Math.round(amount * 0.0002);
      document.getElementById('sp-amount').textContent = won(amount) + '원';
      document.getElementById('sp-fee').textContent = won(fee) + '원';
      document.getElementById('sp-grand').textContent = won(amount + fee) + '원';
    });
    qty.addEventListener('blur', render);
  
    // 매수 / 매도
    var submit = document.getElementById('sp-submit');
    document.querySelectorAll('[data-side]').forEach(function (button) {
      button.addEventListener('click', function () {
        var sell = button.dataset.side === 'sell';
        document.querySelectorAll('[data-side]').forEach(function (b) { b.classList.toggle('on', b === button); });
        submit.textContent = sell ? '매도 주문' : '매수 주문';
        submit.classList.toggle('sell', sell);
      });
    });
  
    // 주문 유형 · 관심
    document.querySelectorAll('[data-ordertype]').forEach(function (button) {
      button.addEventListener('click', function () {
        document.querySelectorAll('[data-ordertype]').forEach(function (b) { b.classList.toggle('on', b === button); });
      });
    });
    var fav = document.getElementById('sp-fav');
    fav.addEventListener('click', function () {
      var on = fav.getAttribute('aria-pressed') !== 'true';
      fav.setAttribute('aria-pressed', String(on));
      fav.textContent = (on ? '★' : '☆') + ' 관심';
    });
  
    render();
  })();
}
