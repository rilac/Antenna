// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.
import { nav } from '../nav'

export default function init() {
  (function () {
    var rows = Array.from(document.querySelectorAll('#stock-rows tr'));
    var selectedMarket = '전체';
    var selectedSector = '전체 종목';
    var query = '';
  
    function setActive(selector, target) {
      document.querySelectorAll(selector).forEach(function (button) { button.classList.toggle('active', button === target); });
    }
  
    function applyFilters() {
      var min = parseFloat(document.getElementById('per-min').value);
      var max = parseFloat(document.getElementById('per-max').value);
      var ongoingOnly = document.getElementById('ongoing-only').checked;
      var favoriteOnly = document.getElementById('favorite-only').checked;
      var visible = 0;
  
      rows.forEach(function (row) {
        var per = parseFloat(row.dataset.per);
        var favorite = row.querySelector('.favorite-button').classList.contains('active');
        var matches = (selectedMarket === '전체' || row.dataset.market === selectedMarket) &&
          (selectedSector === '전체 종목' || row.dataset.sector === selectedSector) &&
          (!query || (row.dataset.name + row.dataset.code).toLowerCase().includes(query)) &&
          (isNaN(min) || per >= min) && (isNaN(max) || per <= max) &&
          (!ongoingOnly || row.dataset.ongoing === 'true') && (!favoriteOnly || favorite);
        row.hidden = !matches;
        if (matches) visible += 1;
      });
  
      document.getElementById('visible-count').textContent = visible;
      document.getElementById('empty-stocks').hidden = visible !== 0;
      updateFilterBadge();
    }
  
    // 적용된 상세 필터 개수를 아이콘 배지로 표시
    function updateFilterBadge() {
      var count = 0;
      if (selectedMarket !== '전체') count++;
      if (selectedSector !== '전체 종목') count++;
      if (document.getElementById('per-min').value !== '') count++;
      if (document.getElementById('per-max').value !== '') count++;
      if (document.getElementById('ongoing-only').checked) count++;
      if (document.getElementById('favorite-only').checked) count++;
      var badge = document.getElementById('filter-badge');
      badge.hidden = count === 0;
      badge.textContent = count;
      document.getElementById('filter-toggle').classList.toggle('has-filters', count > 0);
    }
  
    document.querySelectorAll('[data-market]').forEach(function (button) {
      button.addEventListener('click', function () { selectedMarket = button.dataset.market; setActive('[data-market]', button); });
    });
  
    document.querySelectorAll('[data-sector]').forEach(function (button) {
      button.addEventListener('click', function () { selectedSector = button.dataset.sector; setActive('[data-sector]', button); });
    });
  
    document.querySelectorAll('[data-summary-sector]').forEach(function (button) {
      button.addEventListener('click', function () {
        selectedSector = button.dataset.summarySector;
        setActive('[data-summary-sector]', button);
        var chip = document.querySelector('[data-sector="' + selectedSector + '"]');
        if (chip) setActive('[data-sector]', chip);
        applyFilters();
      });
    });
  
    document.getElementById('stock-search').addEventListener('input', function (event) { query = event.target.value.trim().toLowerCase(); applyFilters(); });
    // 필터 아이콘 → 패널 열고 닫기
    var filterPanel = document.getElementById('filter-panel');
    var filterToggle = document.getElementById('filter-toggle');
    function setFiltersOpen(open) {
      filterPanel.hidden = !open;
      filterToggle.setAttribute('aria-expanded', String(open));
      filterToggle.setAttribute('aria-label', open ? '상세 필터 닫기' : '상세 필터 열기');
      filterToggle.classList.toggle('on', open);
      document.querySelector('.explorer-body').classList.toggle('filters-open', open);
    }
    filterToggle.addEventListener('click', function () { setFiltersOpen(filterPanel.hidden); });
  
    document.getElementById('apply-filter').addEventListener('click', function () {
      applyFilters();
      setFiltersOpen(false);   // 적용하면 패널을 닫는다
    });
    document.getElementById('reset-filter').addEventListener('click', function () {
      selectedMarket = '전체'; selectedSector = '전체 종목'; query = '';
      document.getElementById('stock-search').value = '';
      document.getElementById('per-min').value = '';
      document.getElementById('per-max').value = '';
      document.getElementById('ongoing-only').checked = false;
      document.getElementById('favorite-only').checked = false;
      setActive('[data-market]', document.querySelector('[data-market="전체"]'));
      setActive('[data-sector]', document.querySelector('[data-sector="전체 종목"]'));
      setActive('[data-summary-sector]', document.querySelector('[data-summary-sector="전체 종목"]'));
      applyFilters();
    });
  
    document.querySelectorAll('.favorite-button').forEach(function (button) {
      button.addEventListener('click', function () {
        var active = button.classList.toggle('active');
        button.textContent = active ? '★' : '☆';
        button.setAttribute('aria-pressed', String(active));
        button.setAttribute('aria-label', button.closest('tr').dataset.name + (active ? ' 관심 종목 해제' : ' 관심 종목 추가'));
        if (document.getElementById('favorite-only').checked) applyFilters();
      });
    });
  
    document.getElementById('stock-sort').addEventListener('change', function (event) {
      var key = event.target.value;
      rows.sort(function (a, b) {
        if (key === 'name') return a.dataset.name.localeCompare(b.dataset.name, 'ko');
        return parseFloat(b.dataset[key]) - parseFloat(a.dataset[key]);
      }).forEach(function (row) { document.getElementById('stock-rows').appendChild(row); });
    });
  
    // 종목 행 클릭 → 종목 상세 화면 (관심 버튼 클릭은 제외)
    function openDetail(row) {
      var d = row.dataset;
      var logo = row.querySelector('.company-logo');
      var params = new URLSearchParams({
        code: d.code, name: d.name, market: d.market, sector: d.sector,
        price: d.price, change: d.change, predictions: d.predictions,
        per: d.per, pbr: (row.cells[4].textContent.trim().replace("x", "") || ""),
        logo: logo ? (logo.className.match(/logo-[a-z0-9]+/) || [''])[0] : '',
        mark: logo ? logo.textContent.trim() : ''
      });
      nav('/stock-detail?' + params.toString());
    }
  
    rows.forEach(function (row) {
      row.tabIndex = 0;
      row.setAttribute('role', 'link');
      row.setAttribute('aria-label', row.dataset.name + ' 상세 보기');
      row.addEventListener('click', function (event) {
        if (event.target.closest('.favorite-button')) return;
        openDetail(row);
      });
      row.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openDetail(row); }
      });
    });
  
    document.querySelectorAll('.stock-pagination button').forEach(function (button) {
      if (/^[1-3]$/.test(button.textContent)) button.addEventListener('click', function () { setActive('.stock-pagination button', button); button.setAttribute('aria-current', 'page'); });
    });
  })();}
