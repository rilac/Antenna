// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  (function () {
    var list = document.getElementById('ranking-list');
    var rows = Array.from(list.querySelectorAll('.ranking-row'));
    var extrasVisible = false;
    var field = document.getElementById('field-filter');
    var accuracy = document.getElementById('accuracy-filter');
    var count = document.getElementById('count-filter');
    var sort = document.getElementById('sort-filter');
    var result = document.getElementById('filter-result');
    var empty = document.getElementById('ranking-empty');
    var load = document.getElementById('load-predictors');
  
    function applyFilters() {
      var visible = 0;
      rows.sort(function (a, b) { return Number(b.dataset[sort.value]) - Number(a.dataset[sort.value]); }).forEach(function (row) { list.appendChild(row); });
      rows.forEach(function (row) {
        var matches = (field.value === 'all' || row.dataset.field === field.value) && Number(row.dataset.accuracy) >= Number(accuracy.value) && Number(row.dataset.count) >= Number(count.value);
        var extraAllowed = extrasVisible || !row.classList.contains('extra-row');
        row.hidden = !(matches && extraAllowed);
        if (!row.hidden) visible += 1;
      });
      result.textContent = visible + '명 표시 중';
      empty.hidden = visible !== 0;
      load.hidden = extrasVisible || !rows.some(function (row) { return row.classList.contains('extra-row') && (field.value === 'all' || row.dataset.field === field.value); });
    }
  
    [field, accuracy, count, sort].forEach(function (select) { select.addEventListener('change', applyFilters); });
    document.getElementById('reset-market-filter').addEventListener('click', function () { field.value = 'all'; accuracy.value = '70'; count.value = '100'; sort.value = 'accuracy'; extrasVisible = false; applyFilters(); });
    load.addEventListener('click', function () { extrasVisible = true; applyFilters(); });
    document.querySelectorAll('[data-follow]').forEach(function (button) { button.addEventListener('click', function () { var following = button.classList.toggle('following'); button.textContent = following ? '팔로우 중 ✓' : '팔로우'; }); });
    applyFilters();
  })();}
