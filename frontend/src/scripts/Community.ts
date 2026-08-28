// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  (function () {
    var list = document.getElementById('cl-list');
    var posts = Array.prototype.slice.call(list.children);
    var count = document.getElementById('cl-count');
    var empty = document.getElementById('cl-empty');
    var category = 'all';
    var scope = 'all';
  
    function apply() {
      var shown = 0;
      posts.forEach(function (post) {
        var ok = (scope === 'all' || post.dataset.mine === 'true') &&
                 (category === 'all' || post.dataset.cat === category);
        post.hidden = !ok;
        if (ok) shown++;
      });
      count.textContent = shown;
      empty.hidden = shown > 0;
    }
  
    document.querySelectorAll('.cl-scopes button').forEach(function (button) {
      button.addEventListener('click', function () {
        scope = button.dataset.scope;
        document.querySelectorAll('.cl-scopes button').forEach(function (b) {
          b.classList.toggle('on', b === button);
        });
        apply();
      });
    });
  
    document.querySelectorAll('.cl-cats button').forEach(function (button) {
      button.addEventListener('click', function () {
        category = button.dataset.cat;
        document.querySelectorAll('.cl-cats button').forEach(function (b) {
          b.classList.toggle('on', b === button);
        });
        apply();
      });
    });
  
    document.querySelectorAll('.cl-sorts button').forEach(function (button) {
      button.addEventListener('click', function () {
        document.querySelectorAll('.cl-sorts button').forEach(function (b) {
          b.classList.toggle('on', b === button);
        });
        var key = button.dataset.sort;
        posts.slice().sort(function (a, b) {
          return Number(b.dataset[key]) - Number(a.dataset[key]);
        }).forEach(function (post) { list.appendChild(post); });
      });
    });
  
    document.getElementById('cl-more').addEventListener('click', function (event) {
      event.currentTarget.textContent = '마지막 글까지 모두 불러왔어요';
      event.currentTarget.disabled = true;
    });
  
    apply();
  })();}
