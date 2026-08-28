// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  (function () {
    var body = document.getElementById('mp-rows');
    var rows = Array.prototype.slice.call(body.children);
    var empty = document.getElementById('mp-empty');
    var scope = 'all';
  
    function apply() {
      document.querySelectorAll(".mp-chain-row").forEach(function (r) { r.remove(); });
      document.querySelectorAll("[data-chain]").forEach(function (b) { b.setAttribute("aria-expanded", "false"); });
      var shown = 0;
      rows.forEach(function (row) {
        var ok = scope === 'all' || row.dataset.scope === scope;
        row.hidden = !ok;
        if (ok) shown++;
      });
      empty.hidden = shown > 0;
    }
  
    document.querySelectorAll('.mp-scopes button').forEach(function (button) {
      button.addEventListener('click', function () {
        scope = button.dataset.scope;
        document.querySelectorAll('.mp-scopes button').forEach(function (b) {
          b.classList.toggle('on', b === button);
        });
        apply();
      });
    });
  
    document.querySelectorAll('.mp-sorts button').forEach(function (button) {
      button.addEventListener('click', function () {
        document.querySelectorAll('.mp-sorts button').forEach(function (b) {
          b.classList.toggle('on', b === button);
        });
        var key = button.dataset.sort;
        rows.slice().sort(function (a, b) {
          return Number(b.dataset[key]) - Number(a.dataset[key]);
        }).forEach(function (row) { body.appendChild(row); });
      });
    });
  
    // ── 온체인 기록 펼치기
    var shorten = function (hash) { return hash.slice(0, 10) + "…" + hash.slice(-8); };
  
    function field(label, value, full) {
      return "<div><dt>" + label + "</dt><dd><span title=\"" + (full || value) + "\">" + value + "</span>" +
        (full ? "<button class=\"mp-copy\" type=\"button\" data-copy=\"" + full + "\" aria-label=\"" + label + " 복사\">⧉</button>" : "") +
        "</dd></div>";
    }
  
    function panel(row) {
      var d = row.dataset;
      var stock = row.querySelector(".mp-stock b").textContent;
      var code = row.querySelector(".mp-stock small").textContent;
      var dir = row.querySelector(".mp-dir").textContent;
      var span = row.cells[2].textContent;
      var target = row.cells[3].textContent;
      var settled = d.status === "done";
  
      return "<td colspan=\"8\"><div class=\"mp-chain\">" +
        "<div class=\"mp-chain-head\">" +
          "<h3><svg width=\"17\" height=\"17\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1.5 1.5\"/><path d=\"M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1.5-1.5\"/></svg>온체인 기록</h3>" +
          "<span class=\"mp-chain-state " + (settled ? "done" : "wait") + "\">" + (settled ? "앵커 · 정산 완료" : "앵커 완료 · 검증 대기") + "</span>" +
          "<span class=\"mp-chain-net\">Polygon PoS</span>" +
        "</div>" +
        "<dl class=\"mp-chain-grid\">" +
          field("커밋 트랜잭션", shorten(d.tx), d.tx) +
          field("블록 번호", "#" + d.block) +
          field("앵커 시각", d.anchored + " KST") +
          field("근거 해시", shorten(d.tx.slice(0, 2) + d.tx.slice(-64).split("").reverse().join("")), d.tx.slice(0, 2) + d.tx.slice(-64).split("").reverse().join("")) +
          (settled ? field("정산 트랜잭션", shorten(d.settle), d.settle) : field("정산 트랜잭션", "예측 종료 후 기록")) +
          field("확신도", d.conf) +
        "</dl>" +
        "<div class=\"mp-commit\">" +
          "<span>종목 <b>" + stock + " (" + code + ")</b></span>" +
          "<span>방향 <b>" + dir + "</b></span>" +
          "<span>기간 <b>" + span + "</b></span>" +
          "<span>목표가 <b>" + target + "</b></span>" +
        "</div>" +
        "<div class=\"mp-chain-foot\">" +
          "<a class=\"mp-explorer\" href=\"#\">익스플로러에서 보기 ↗</a>" +
        "</div>" +
        "<p class=\"mp-chain-note\"><svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.9\" stroke-linecap=\"round\"><circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M12 11v5\"/><path d=\"M12 7.8v.4\"/></svg>예측 내용은 등록 시점에 해시로 봉인되어 이후 수정할 수 없습니다.</p>" +
      "</div></td>";
    }
  
    document.querySelectorAll("[data-chain]").forEach(function (button) {
      button.addEventListener("click", function () {
        var row = button.closest("tr");
        var next = row.nextElementSibling;
        var open = next && next.classList.contains("mp-chain-row");
  
        if (open) {
          next.remove();
          button.setAttribute("aria-expanded", "false");
          return;
        }
  
        // 한 번에 하나만 펼친다
        document.querySelectorAll(".mp-chain-row").forEach(function (r) { r.remove(); });
        document.querySelectorAll("[data-chain]").forEach(function (b) { b.setAttribute("aria-expanded", "false"); });
  
        var tr = document.createElement("tr");
        tr.className = "mp-chain-row";
        tr.innerHTML = panel(row);
        row.parentNode.insertBefore(tr, row.nextSibling);
        button.setAttribute("aria-expanded", "true");
  
        tr.querySelectorAll(".mp-copy").forEach(function (copy) {
          copy.addEventListener("click", function () {
            var text = copy.dataset.copy;
            try { navigator.clipboard.writeText(text); } catch (e) {}
            copy.classList.add("done");
            copy.textContent = "✓";
            setTimeout(function () { copy.classList.remove("done"); copy.textContent = "⧉"; }, 1400);
          });
        });
      });
    });
  
    document.getElementById('mp-more').addEventListener('click', function (event) {
      event.currentTarget.textContent = '최근 8건만 표시합니다 (전체는 준비 중)';
      event.currentTarget.disabled = true;
    });
  
    apply();
  })();}
