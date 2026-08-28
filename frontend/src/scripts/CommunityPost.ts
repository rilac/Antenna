// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  (function () {
    // 공감 · 북마크 토글
    var like = document.getElementById('cm-like');
    var count = document.getElementById('cm-like-count');
    like.addEventListener('click', function () {
      var on = like.getAttribute('aria-pressed') !== 'true';
      like.setAttribute('aria-pressed', String(on));
      count.textContent = on ? '202' : '201';
      like.querySelector('svg').setAttribute('fill', on ? 'currentColor' : 'none');
    });
  
    var mark = document.getElementById('cm-bookmark');
    mark.addEventListener('click', function () {
      var on = mark.getAttribute('aria-pressed') !== 'true';
      mark.setAttribute('aria-pressed', String(on));
      mark.querySelector('svg').setAttribute('fill', on ? 'currentColor' : 'none');
    });
  
    // 댓글 정렬 탭
    document.querySelectorAll('.cm-sort button').forEach(function (button) {
      button.addEventListener('click', function () {
        document.querySelectorAll('.cm-sort button').forEach(function (b) {
          b.classList.toggle('on', b === button);
          b.setAttribute('aria-pressed', String(b === button));
        });
      });
    });
  
    // 댓글 등록 (프로토타입 — 목록 맨 위에 추가)
    document.querySelector('.cm-form').addEventListener('submit', function () {
      var input = document.getElementById('cm-new');
      var text = input.value.trim();
      if (!text) return;
      var li = document.createElement('li');
      li.className = 'cm-item';
      li.innerHTML =
        '<i class="cm-avatar s av-4">나</i>' +
        '<div class="cm-item-main">' +
          '<p class="cm-item-name"><b>나</b> <span class="cm-lv">Lv.1</span></p>' +
          '<p class="cm-item-text"></p>' +
          '<p class="cm-item-meta"><time>방금 전</time><span>공감 <b class="num">0</b></span>' +
          '<button type="button">답글</button><button type="button">신고</button></p>' +
        '</div>' +
        '<div class="cm-item-side"><button type="button" aria-label="공감">♡</button>' +
        '<button type="button" aria-label="댓글 메뉴">⋮</button></div>';
      li.querySelector('.cm-item-text').textContent = text;
      var list = document.querySelector('.cm-list');
      list.insertBefore(li, list.firstElementChild);
      input.value = '';
    });
  })();}
