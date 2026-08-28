// @ts-nocheck
// 프로토타입 원본 스크립트 — 마운트 후 DOM 위에서 그대로 실행한다.

export default function init() {
  /* 종목 상세 — 종목 보기에서 넘어온 쿼리(?code=&name=&price=…)로 화면을 채운다.
     쿼리가 없으면 HTML 에 적힌 기본값(삼성전자)이 그대로 남는다. */
  (function () {
    var q = new URLSearchParams(location.search);
    var code = q.get('code');
  
    // 종목별 샘플 지표 — 시가총액·배당수익률은 표본값, EPS·ROE 는 PER/PBR 에서 계산한다.
    var PROFILE = {
      '005930': { cap: '469.0조원', div: '2.32%' },
      '000660': { cap: '144.5조원', div: '1.05%' },
      '373220': { cap: '80.0조원',  div: '0.12%' },
      '247540': { cap: '16.3조원',  div: '0.18%' },
      '035420': { cap: '28.7조원',  div: '0.45%' },
      '005380': { cap: '51.2조원',  div: '4.85%' },
      '035720': { cap: '18.8조원',  div: '0.14%' },
      '068270': { cap: '42.1조원',  div: '0.32%' },
      '207940': { cap: '68.9조원',  div: '0.05%' },
      '105560': { cap: '34.6조원',  div: '5.12%' }
    };
  
    var SECTOR = {
      '반도체': {
        peer: ['19.42배', '1.48배'],
        brief: '메모리 반도체 업황 회복과 HBM 수요 증가로 실적 개선 기대가 지속되고 있습니다. 스마트폰·가전 등 세트 사업의 안정적인 수요와 메모리 경쟁력 강화가 긍정적으로 작용할 전망입니다.',
        news: [
          ['차세대 HBM3E 12단 검증 통과 (연합뉴스)', '2025.06.02'],
          ['글로벌 스마트폰 시장 회복세 뚜렷 (ZDNet)', '2025.06.01'],
          ['미국 반도체 보조금 최종 가이드라인 발표 (로이터)', '2025.05.30']
        ],
        good: ['HBM 등 고부가 메모리 수요 확대에 따른 실적 개선 기대', '글로벌 스마트폰·가전 수요 회복 및 프리미엄 제품 비중 확대', '파운드리 기술 경쟁력 강화 및 신규 고객사 확보 기대'],
        risk: ['글로벌 경기 둔화 및 수요 변동성 지속', '경쟁 심화에 따른 메모리 가격 변동성', '미·중 갈등 등 지정학적 리스크 지속'],
        check: ['메모리 업황 회복 속도 및 가격 추이', '스마트폰·가전 수요 회복 지속 여부', '차세대 공정 전환 및 파운드리 수주 성과']
      },
      '2차전지': {
        peer: ['38.60배', '3.12배'],
        brief: '전기차 수요 증가율이 둔화된 구간이지만, 북미 공장 가동률 회복과 ESS 수주 확대가 실적 하방을 지지하고 있습니다. 리튬 등 원재료 가격 안정화가 마진 개선 요인으로 작용할 전망입니다.',
        news: [
          ['북미 배터리 합작공장 가동률 상향 (전자신문)', '2025.06.02'],
          ['리튬 가격 3개월째 안정세 유지 (블룸버그)', '2025.05.31'],
          ['ESS 수주 잔고 사상 최대치 경신 (한국경제)', '2025.05.29']
        ],
        good: ['북미 IRA 보조금 수혜와 현지 생산 능력 확대', 'ESS·전력망 수요 확대에 따른 매출처 다변화', '원재료 가격 안정화에 따른 수익성 개선 기대'],
        risk: ['전기차 수요 증가율 둔화 및 재고 조정 지속', '중국 업체와의 가격 경쟁 심화', '대규모 설비 투자에 따른 재무 부담'],
        check: ['분기별 가동률과 출하량 추이', '주요 완성차 고객사의 생산 계획 변경 여부', '보조금 정책 변화와 수취 규모']
      },
      '인터넷': {
        peer: ['22.40배', '1.35배'],
        brief: '광고 시장 회복과 커머스 거래액 성장이 이어지는 가운데, AI 서비스의 수익화 속도가 실적 방향을 가를 전망입니다. 비용 효율화 기조가 이어지며 영업이익률 개선이 기대됩니다.',
        news: [
          ['생성형 AI 검색 베타 사용자 확대 (연합뉴스)', '2025.06.02'],
          ['1분기 광고 매출 전년 대비 두 자릿수 성장 (머니투데이)', '2025.05.30'],
          ['플랫폼 규제 법안 국회 논의 재개 (한겨레)', '2025.05.28']
        ],
        good: ['광고 경기 회복에 따른 핵심 매출 반등', '커머스 거래액과 멤버십 이용자 동반 증가', 'AI 서비스 도입에 따른 신규 수익원 기대'],
        risk: ['플랫폼 규제 강화 가능성', 'AI 인프라 투자에 따른 비용 부담 확대', '경쟁 플랫폼의 점유율 잠식'],
        check: ['AI 서비스의 실제 매출 기여 시점', '분기 광고 매출 성장률 지속 여부', '규제 법안의 최종 처리 방향']
      },
      '자동차': {
        peer: ['6.80배', '0.74배'],
        brief: '고수익 차종 중심의 믹스 개선과 우호적인 환율이 수익성을 지지하고 있습니다. 하이브리드 판매 호조가 전기차 수요 둔화를 상쇄하는 흐름이 이어질 전망입니다.',
        news: [
          ['하이브리드 판매 비중 사상 최대 (연합뉴스)', '2025.06.02'],
          ['북미 시장 점유율 상승세 지속 (로이터)', '2025.05.31'],
          ['주주환원 정책 확대 발표 (한국경제)', '2025.05.27']
        ],
        good: ['SUV·하이브리드 등 고수익 차종 판매 비중 확대', '우호적 환율에 따른 수익성 방어', '배당 확대 등 주주환원 정책 강화'],
        risk: ['글로벌 소비 위축에 따른 판매 둔화', '전기차 전환 투자에 따른 비용 증가', '관세·통상 정책 변화 리스크'],
        check: ['월별 글로벌 판매 대수 추이', '평균 판매 단가와 인센티브 수준', '전기차 전용 라인 가동 일정']
      },
      '바이오': {
        peer: ['45.30배', '3.85배'],
        brief: '위탁생산 수주 잔고가 견조하게 유지되는 가운데, 바이오시밀러 품목 확대가 중장기 성장 동력으로 작용할 전망입니다. 임상 결과와 허가 일정이 주가 변동성의 핵심 변수입니다.',
        news: [
          ['신규 바이오시밀러 유럽 허가 신청 (연합뉴스)', '2025.06.01'],
          ['글로벌 제약사와 위탁생산 계약 체결 (매일경제)', '2025.05.30'],
          ['3상 임상 중간 결과 학회 발표 (팜뉴스)', '2025.05.26']
        ],
        good: ['위탁생산 수주 잔고 확대와 신규 공장 가동', '바이오시밀러 품목 다변화로 매출처 확대', '주요 시장 허가 절차 순항'],
        risk: ['임상 실패 및 허가 지연 가능성', '높은 밸류에이션에 따른 조정 부담', '환율·원가 변동에 따른 마진 압박'],
        check: ['임상 단계별 결과 발표 일정', '신규 수주 계약 규모와 기간', '공장 가동률과 생산 능력 증설 진행']
      },
      '금융': {
        peer: ['6.50배', '0.62배'],
        brief: '금리 인하 국면에서 순이자마진 축소가 예상되지만, 비이자이익 확대와 안정적인 자본비율이 이를 완충할 전망입니다. 주주환원 확대 기조가 밸류에이션을 지지하고 있습니다.',
        news: [
          ['분기 배당 확대 및 자사주 매입 결정 (연합뉴스)', '2025.06.02'],
          ['기준금리 인하 기대 확산 (한국경제)', '2025.05.29'],
          ['가계대출 관리 방안 발표 (매일경제)', '2025.05.27']
        ],
        good: ['배당·자사주 매입 등 주주환원 정책 확대', '견조한 자본비율과 안정적인 건전성 지표', '비이자이익 비중 확대에 따른 수익 다변화'],
        risk: ['금리 인하에 따른 순이자마진 축소', '부동산 프로젝트파이낸싱 부실 우려', '가계대출 규제 강화 가능성'],
        check: ['분기 순이자마진 추이', '대손충당금 적립 규모', '주주환원율 목표 달성 여부']
      }
    };
  
    var won = function (n) { return Math.round(n).toLocaleString('ko-KR'); };
    var set = function (id, text) { var el = document.getElementById(id); if (el) el.textContent = text; };
    var fill = function (id, items, mark) {
      var ul = document.getElementById(id);
      if (!ul) return;
      ul.innerHTML = items.map(function (t) { return '<li><em>' + mark + '</em>' + t + '</li>'; }).join('');
    };
  
    // ── 찜하기 토글 (쿼리 유무와 무관)
    var fav = document.getElementById('sd-fav');
    if (fav) {
      fav.addEventListener('click', function () {
        var on = fav.getAttribute('aria-pressed') !== 'true';
        fav.setAttribute('aria-pressed', String(on));
        fav.querySelector('i').textContent = on ? '★' : '☆';
        fav.lastChild.textContent = on ? ' 찜함' : ' 찜하기';
      });
    }
  
    // ── CTA 는 현재 쿼리를 그대로 예측 등록으로 넘긴다
    var cta = document.getElementById('sd-cta');
    if (cta && location.search) cta.href = '/prediction/create' + location.search;
  
    if (!code) return;
  
    var name = q.get('name') || '';
    var price = Number(q.get('price') || 0);
    var change = Number(q.get('change') || 0);
    var per = Number(q.get('per') || 0);
    var pbr = Number(q.get('pbr') || 0);
    var sector = SECTOR[q.get('sector')] || SECTOR['반도체'];
    var profile = PROFILE[code] || {};
  
    document.title = name + ' 종목 상세 · ANTENA';
  
    // 아이덴티티
    var logo = document.getElementById('sd-logo');
    if (logo) {
      logo.textContent = q.get('mark') || name.slice(0, 1);
      if (q.get('logo')) logo.className = 'sd-logo ' + q.get('logo');
    }
    set('sd-name', name);
    set('sd-code', code);
  
    if (price) {
      var delta = Math.round(price * change / 100);
      var sign = delta >= 0 ? '+' : '-';
      set('sd-price', won(price));
      var d = document.getElementById('sd-delta');
      d.textContent = sign + won(Math.abs(delta)) + ' (' + sign + Math.abs(change).toFixed(2) + '%)';
      d.className = 'sd-delta num ' + (delta >= 0 ? 'rise' : 'fall');
    }
  
    // 지표 — EPS·ROE 는 PER/PBR 로 계산, 나머지는 표본값
    if (profile.cap) set('m-cap', profile.cap);
    if (profile.div) set('m-div', profile.div);
    if (per) set('m-per', per.toFixed(2) + '배');
    if (pbr) set('m-pbr', pbr.toFixed(2) + '배');
    if (per && price) set('m-eps', won(price / per) + '원');
    if (per && pbr) set('m-roe', (pbr / per * 100).toFixed(2) + '%');
    if (price) set('m-range', won(price * 0.79) + ' ~ ' + won(price * 1.24) + '원');
    set('m-speer', sector.peer[0]);
    set('m-spbr', sector.peer[1]);
  
    // 브리핑 · 뉴스 · 포인트
    set('sd-brief-text', sector.brief);
    var news = document.getElementById('sd-news');
    if (news) {
      news.innerHTML = sector.news.map(function (n, i) {
        var headline = (i === 0 ? name + ', ' : '') + n[0];
        return '<li><i></i><span>' + headline + '</span><time>' + n[1] + '</time></li>';
      }).join('');
    }
    fill('pt-good', sector.good, '✓');
    fill('pt-risk', sector.risk, '!');
    fill('pt-check', sector.check, '✓');
  })();
}
