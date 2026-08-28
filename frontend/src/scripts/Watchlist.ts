// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  (function () {
    var KINDS = {
      stock:  { list: 'wl-stock-list',  item: '.wl-stock',  empty: 'wl-empty-stock'  },
      report: { list: 'wl-report-list', item: '.wl-report', empty: 'wl-empty-report' },
      user:   { list: 'wl-user-list',   item: '.wl-user',   empty: 'wl-empty-user'   }
    };
  
    // 탭 전환
    document.querySelectorAll('.wl-tabs button').forEach(function (button) {
      button.addEventListener('click', function () {
        var tab = button.dataset.tab;
        document.querySelectorAll('.wl-tabs button').forEach(function (b) { b.classList.toggle('on', b === button); });
        Object.keys(KINDS).forEach(function (kind) {
          document.getElementById('panel-' + kind).hidden = kind !== tab;
        });
      });
    });
  
    // 찜 해제 — 개수와 빈 상태를 함께 갱신
    function refresh(kind) {
      var conf = KINDS[kind];
      var left = document.querySelectorAll('#' + conf.list + ' ' + conf.item + ':not([hidden])').length;
      document.getElementById('wl-count-' + kind).innerHTML =
        left + '<small>' + (kind === 'stock' ? '개' : kind === 'report' ? '건' : '명') + '</small>';
      document.getElementById('wl-tab-' + kind).textContent = left;
      document.getElementById(conf.empty).hidden = left > 0;
    }
  
    Object.keys(KINDS).forEach(function (kind) {
      var conf = KINDS[kind];
      document.querySelectorAll('#' + conf.list + ' .wl-star').forEach(function (star) {
        star.addEventListener('click', function () {
          star.closest(conf.item).hidden = true;
          refresh(kind);
        });
      });
    });
  
    // 알림 토글
    document.querySelectorAll('.wl-toggle').forEach(function (toggle) {
      toggle.addEventListener('click', function () {
        toggle.setAttribute('aria-pressed', String(toggle.getAttribute('aria-pressed') !== 'true'));
      });
    });
  
    // 구독 토글
    document.querySelectorAll('.wl-solid').forEach(function (button) {
      button.addEventListener('click', function () {
        var on = button.getAttribute('aria-pressed') !== 'true';
        button.setAttribute('aria-pressed', String(on));
        button.textContent = on ? '구독 중' : '구독하기';
        var tag = button.closest('.wl-user').querySelector('.wl-sub');
        if (on && !tag) {
          var badge = document.createElement('span');
          badge.className = 'wl-sub';
          badge.textContent = '구독 중';
          button.closest('.wl-user').querySelector('.wl-user-name').appendChild(badge);
        } else if (!on && tag) {
          tag.remove();
        }
      });
    });
  })();}
