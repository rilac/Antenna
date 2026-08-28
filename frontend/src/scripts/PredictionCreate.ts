// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  // 종목 보기에서 넘어온 종목으로 화면을 채운다 (?code=&name=&price=&change=…)
  (function () {
    var q = new URLSearchParams(location.search);
    if (!q.get("code")) return;
  
    var name = q.get("name") || "";
    var code = q.get("code");
    var price = Number(q.get("price") || 0);
    var change = Number(q.get("change") || 0);
    var won = function (n) { return n.toLocaleString("ko-KR"); };
  
    document.title = name + " 예측 등록 · ANTENA";
  
    var logo = document.querySelector(".prediction-stock-logo");
    if (logo) {
      logo.textContent = q.get("mark") || name.slice(0, 1);
      if (q.get("logo")) logo.className = "prediction-stock-logo " + q.get("logo");
    }
  
    var nameEl = document.querySelector(".prediction-stock-name");
    if (nameEl) {
      nameEl.querySelector("strong").textContent = name;
      nameEl.querySelector(".num").textContent = code;
      var fav = nameEl.querySelector("button");
      if (fav) fav.setAttribute("aria-label", name + " 관심 종목 추가");
    }
  
    var priceEl = document.querySelector(".prediction-stock-price");
    if (priceEl && price) {
      var delta = Math.round(price * change / 100);
      var sign = delta >= 0 ? "+" : "-";
      priceEl.querySelector("strong").textContent = won(price);
      var b = priceEl.querySelector("b");
      b.textContent = sign + won(Math.abs(delta)) + " (" + sign + Math.abs(change).toFixed(2) + "%)";
      b.className = "num " + (delta >= 0 ? "rise" : "fall");
    }
  
    var meta = document.querySelector(".prediction-stock-meta");
    if (meta && q.get("market")) meta.firstChild.textContent = q.get("market") + " ";
  
    // 목표가 기본값 — 현재가의 +8%를 100원 단위로
    if (price) {
      var target = Math.round(price * 1.08 / 100) * 100;
      var input = document.getElementById("target-price");
      if (input) input.value = won(target);
      var preview = document.getElementById("preview-price");
      if (preview) preview.textContent = won(target) + "원";
    }
  
    document.querySelectorAll(".preview-details dt").forEach(function (dt) {
      if (dt.textContent.trim() === "종목") dt.nextElementSibling.textContent = name + " (" + code + ")";
    });
  })();
  
  (function () {
    var priceInput = document.getElementById('target-price');
    var previewPrice = document.getElementById('preview-price');
    var directDate = document.querySelector('.direct-date');
    var baseEvidenceCount = 2;
    var aiAdded = false;
  
    function formatPrice(value) {
      var digits = String(value).replace(/\D/g, '').slice(0, 9);
      return digits ? Number(digits).toLocaleString('ko-KR') : '';
    }
  
    function updateEvidenceCount() {
      var sourceCount = Array.from(document.querySelectorAll('.evidence-toggle:checked')).reduce(function (sum, input) { return sum + Number(input.closest('[data-evidence-count]').dataset.evidenceCount); }, 0);
      var metricCount = document.querySelectorAll('.metric-toggle:checked').length;
      var total = baseEvidenceCount + sourceCount + metricCount + (aiAdded ? 1 : 0);
      ['selected-count','summary-count','preview-count'].forEach(function (id) { document.getElementById(id).textContent = total; });
    }
  
    document.querySelectorAll('input[name="direction"]').forEach(function (input) {
      input.addEventListener('change', function () {
        document.querySelectorAll('.direction-option').forEach(function (option) { option.classList.toggle('selected', option.contains(input) && input.checked); });
        var preview = document.getElementById('preview-direction');
        var up = input.value === 'UP';
        preview.textContent = (up ? '↗  상승 (UP)' : '↘  하락 (DOWN)');
        preview.className = 'preview-direction ' + (up ? 'up' : 'down');
      });
    });
  
    priceInput.addEventListener('input', function () { priceInput.value = formatPrice(priceInput.value); previewPrice.textContent = (priceInput.value || '0') + '원'; });
  
    document.querySelectorAll('input[name="period"]').forEach(function (input) {
      input.addEventListener('change', function () {
        document.querySelectorAll('.period-option').forEach(function (option) { option.classList.toggle('selected', option.contains(input) && input.checked); });
        directDate.hidden = input.value !== 'direct';
        document.getElementById('preview-period').textContent = input.value === '10' ? '10영업일 (~ 2025.06.16)' : input.value === '20' ? '20영업일 (~ 2025.06.30)' : '직접 입력 (~ ' + document.getElementById('direct-date').value.replaceAll('-', '.') + ')';
      });
    });
    document.getElementById('direct-date').addEventListener('change', function (event) { if (document.querySelector('input[name="period"]:checked').value === 'direct') document.getElementById('preview-period').textContent = '직접 입력 (~ ' + event.target.value.replaceAll('-', '.') + ')'; });
  
    var judgment = document.getElementById('judgment-text');
    function updateJudgmentCount() { document.getElementById('judgment-count').textContent = judgment.value.length; }
    judgment.addEventListener('input', updateJudgmentCount); updateJudgmentCount();
    document.querySelectorAll('.editor-toolbar button').forEach(function (button) {
      button.addEventListener('click', function () {
        var start = judgment.selectionStart, end = judgment.selectionEnd, selected = judgment.value.slice(start, end);
        var insert = button.dataset.insert;
        var wrap = button.dataset.wrap;
        var replacement = insert || (wrap === '[]()' ? '[' + selected + ']()' : wrap + selected + wrap);
        judgment.setRangeText(replacement, start, end, 'end'); judgment.focus(); updateJudgmentCount();
      });
    });
  
    document.querySelectorAll('.evidence-toggle').forEach(function (input) { input.addEventListener('change', function () { input.closest('.evidence-source').classList.toggle('selected', input.checked); input.nextElementSibling.textContent = input.checked ? '✓ 선택됨' : '선택'; updateEvidenceCount(); }); });
    document.querySelectorAll('.metric-toggle').forEach(function (input) { input.addEventListener('change', updateEvidenceCount); });
    document.getElementById('add-ai-evidence').addEventListener('click', function (event) { if (aiAdded) return; aiAdded = true; event.currentTarget.textContent = 'AI 근거 추가됨 ✓'; event.currentTarget.classList.add('added'); document.getElementById('ai-thesis').classList.add('selected'); updateEvidenceCount(); });
  
    var modal = document.getElementById('prediction-modal');
    var modalDialog = modal.querySelector('.prediction-progress-dialog');
    var modalTitle = document.getElementById('progress-modal-title');
    var anchorStep = modal.querySelector('[data-progress="anchor"]');
    var goPredictions = document.getElementById('go-predictions');
    var submitButton = document.getElementById('prediction-submit');
    var modalTimers = [];
    var registrationComplete = false;
    var returnFocus = null;
  
    function clearModalTimers() { modalTimers.forEach(clearTimeout); modalTimers = []; }
    function openPredictionModal() {
      clearModalTimers(); registrationComplete = false; returnFocus = document.activeElement;
      modal.hidden = false; document.body.classList.add('modal-open');
      modalTitle.textContent = '예측을 블록에 기록하고 있어요';
      anchorStep.className = 'active'; anchorStep.querySelector('i').innerHTML = '<b></b>';
      anchorStep.querySelector('strong').textContent = '머클 앵커 대기';
      anchorStep.querySelector('small').textContent = '블록에 기록할 차례를 기다리고 있습니다.';
      goPredictions.disabled = true; document.getElementById('carrier-stage').classList.remove('complete');
      modal.classList.remove('is-active'); void modal.offsetWidth; modal.classList.add('is-active');
      modalDialog.focus(); submitButton.textContent = '등록 진행 중…';
      modalTimers.push(setTimeout(function () {
        registrationComplete = true; modalTitle.textContent = '예측 기록 준비가 완료됐어요';
        anchorStep.className = 'done'; anchorStep.querySelector('i').textContent = '✓';
        anchorStep.querySelector('strong').textContent = '머클 앵커 준비 완료';
        anchorStep.querySelector('small').textContent = '지갑 전송을 확인할 수 있는 상태입니다.';
        goPredictions.disabled = false; document.getElementById('carrier-stage').classList.add('complete');
      }, 4800));
    }
    function closePredictionModal() {
      clearModalTimers(); modal.classList.remove('is-active'); modal.hidden = true; document.body.classList.remove('modal-open');
      submitButton.textContent = registrationComplete ? '등록 준비 완료 ✓' : '예측 등록하기';
      submitButton.classList.toggle('ready', registrationComplete);
      if (registrationComplete) document.getElementById('chain-status').textContent = '지갑 전송을 진행할 수 있도록 준비되었습니다.';
      if (returnFocus) returnFocus.focus();
    }
  
    submitButton.addEventListener('click', openPredictionModal);
    modal.querySelectorAll('[data-modal-close]').forEach(function (button) { button.addEventListener('click', closePredictionModal); });
    goPredictions.addEventListener('click', closePredictionModal);
    document.addEventListener('keydown', function (event) { if (event.key === 'Escape' && !modal.hidden) closePredictionModal(); });
    updateEvidenceCount();
  })();}
