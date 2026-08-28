export default function Market() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner predictor-market"}>
          <header className={"predictor-market-hero"}>
            <div>
              <h1><i aria-hidden={"true"}>♟</i> 예측가 둘러보기</h1>
              <p>정확도, 스타일, 분야별로 믿을 수 있는 예측가를 비교하고 팔로우해보세요.</p>
            </div>
            <aside className={"trust-score-note"}><i>♜</i><span><strong>신뢰는 기록으로 증명됩니다</strong><small>모든 예측가는 과거 예측 정확도와 공개된 근거 기록을 기반으로 신뢰 점수를 산정합니다.</small></span></aside>
          </header>

          <form className={"predictor-filters"} id={"predictor-filters"}>
            <label><span>분야</span><select id={"field-filter"}><option value={"all"}>전체 분야</option><option value={"반도체"}>반도체</option><option value={"성장주"}>성장주</option><option value={"배당주"}>배당주</option><option value={"데이터"}>데이터/스윙</option><option value={"가치주"}>가치주</option><option value={"차트"}>차트/기술적</option></select></label>
            <label><span>적중률</span><select id={"accuracy-filter"}><option value={"70"}>70% 이상</option><option value={"75"}>75% 이상</option><option value={"0"}>전체</option></select></label>
            <label><span>예측 수</span><select id={"count-filter"}><option value={"100"}>100건 이상</option><option value={"1000"}>1,000건 이상</option><option value={"0"}>전체</option></select></label>
            <label><span>기간</span><select id={"period-filter"}><option>최근 3개월</option><option>최근 6개월</option><option>최근 1년</option></select></label>
            <label><span>정렬</span><select id={"sort-filter"}><option value={"accuracy"}>적중률순</option><option value={"count"}>예측 많은 순</option><option value={"performance"}>최근 성과순</option></select></label>
            <button id={"reset-market-filter"} type={"button"}>↻ <span>필터 초기화</span></button>
          </form>

          <div className={"predictor-market-grid"}>
            <div className={"predictor-market-main"}>
              <section className={"featured-predictor"} aria-labelledby={"featured-title"}>
                <h2 id={"featured-title"}>★ <span>추천 예측가</span></h2>
                <div className={"featured-profile"}>
                  <div className={"predictor-avatar avatar-man"}><i></i></div>
                  <div className={"featured-identity"}><h3>반도체훈련소 <b>✓</b> <small>공식 파트너</small></h3><p>반도체 사이클과 HBM 전망 분석</p><div className={"predictor-tags"}><span>반도체</span><span>HBM</span><span>AI</span><span>기업분석</span></div><div className={"featured-social"}><i>▶</i><i>◎</i><i>N</i></div></div>
                  <dl className={"featured-metrics"}><div><dt>적중률</dt><dd>78.6%</dd><small>상위 3%</small></div><div><dt>누적 예측 수</dt><dd>1,282건</dd><small>최근 3개월 128건</small></div><div><dt>신뢰 점수</dt><dd>92.4<em>/100</em></dd><small>상위 2%</small></div><div><dt>팔로워</dt><dd>12.3K</dd><small>+1,024명 (최근 30일)</small></div></dl>
                  <div className={"featured-actions"}><button type={"button"} className={"follow-button"} data-follow>팔로우</button><a className={"view-predictions"} href={"/prediction/create"}>예측 보기 <span>→</span></a></div>
                </div>
              </section>

              <section className={"predictor-ranking"} aria-labelledby={"ranking-title"}>
                <div className={"ranking-head"}><h2 id={"ranking-title"}>예측가 랭킹 <span>전체 <b id={"visible-predictor-count"}>156</b>명</span></h2><p id={"filter-result"} aria-live={"polite"}></p></div>
                <div className={"ranking-columns"} aria-hidden={"true"}><span>예측가</span><span>핵심 분야</span><span>적중률</span><span>예측 수</span><span>최근 3개월 성과</span><span>대표 태그</span><span>액션</span></div>
                <div className={"ranking-list"} id={"ranking-list"}>
                  <article className={"ranking-row"} data-field={"반도체"} data-accuracy={"78.6"} data-count={"1282"} data-performance={"18.6"}><b className={"rank gold"}>1</b><div className={"rank-profile"}><i className={"rank-avatar face-1"}>반</i><span><strong>반도체훈련소 <em>✓</em></strong><small>반도체 사이클과 HBM 전망 분석</small></span></div><span className={"rank-field"}>반도체</span><strong className={"rank-accuracy"}>78.6%</strong><strong className={"rank-count"}>1,282건</strong><div className={"rank-performance"}><b>+18.6%</b><span className={"mini-trend"}><i></i><i></i><i></i><i></i><i></i><i></i></span></div><div className={"rank-tags"}><span>반도체</span><span>HBM</span></div><button type={"button"} data-follow>팔로우</button></article>
                  <article className={"ranking-row"} data-field={"성장주"} data-accuracy={"76.2"} data-count={"1056"} data-performance={"14.3"}><b className={"rank silver"}>2</b><div className={"rank-profile"}><i className={"rank-avatar face-2"}>투</i><span><strong>투자왕보 <em>✓</em></strong><small>성장주 중심의 가치 발굴</small></span></div><span className={"rank-field"}>성장주</span><strong className={"rank-accuracy"}>76.2%</strong><strong className={"rank-count"}>1,056건</strong><div className={"rank-performance"}><b>+14.3%</b><span className={"mini-trend"}><i></i><i></i><i></i><i></i><i></i><i></i></span></div><div className={"rank-tags"}><span>성장주</span><span>모멘텀</span></div><button type={"button"} data-follow>팔로우</button></article>
                  <article className={"ranking-row"} data-field={"배당주"} data-accuracy={"74.8"} data-count={"942"} data-performance={"12.1"}><b className={"rank bronze"}>3</b><div className={"rank-profile"}><i className={"rank-avatar face-3"}>숙</i><span><strong>실속요정</strong><small>배당·가치주 안정적 투자</small></span></div><span className={"rank-field"}>배당주</span><strong className={"rank-accuracy"}>74.8%</strong><strong className={"rank-count"}>942건</strong><div className={"rank-performance"}><b>+12.1%</b><span className={"mini-trend"}><i></i><i></i><i></i><i></i><i></i><i></i></span></div><div className={"rank-tags"}><span>배당</span><span>가치주</span></div><button type={"button"} data-follow>팔로우</button></article>
                  <article className={"ranking-row"} data-field={"데이터"} data-accuracy={"73.1"} data-count={"1118"} data-performance={"9.8"}><b className={"rank"}>4</b><div className={"rank-profile"}><i className={"rank-avatar face-4"}>데</i><span><strong>데이터헌터 <em>✓</em></strong><small>데이터 기반 단기 스윙</small></span></div><span className={"rank-field"}>데이터</span><strong className={"rank-accuracy"}>73.1%</strong><strong className={"rank-count"}>1,118건</strong><div className={"rank-performance"}><b>+9.8%</b><span className={"mini-trend"}><i></i><i></i><i></i><i></i><i></i><i></i></span></div><div className={"rank-tags"}><span>데이터</span><span>스윙</span></div><button type={"button"} data-follow>팔로우</button></article>
                  <article className={"ranking-row"} data-field={"가치주"} data-accuracy={"72.4"} data-count={"987"} data-performance={"7.6"}><b className={"rank"}>5</b><div className={"rank-profile"}><i className={"rank-avatar face-5"}>가</i><span><strong>가치투자자</strong><small>기업 내재가치 분석</small></span></div><span className={"rank-field"}>가치주</span><strong className={"rank-accuracy"}>72.4%</strong><strong className={"rank-count"}>987건</strong><div className={"rank-performance"}><b>+7.6%</b><span className={"mini-trend"}><i></i><i></i><i></i><i></i><i></i><i></i></span></div><div className={"rank-tags"}><span>가치주</span><span>기업분석</span></div><button type={"button"} data-follow>팔로우</button></article>
                  <article className={"ranking-row"} data-field={"차트"} data-accuracy={"70.9"} data-count={"856"} data-performance={"6.1"}><b className={"rank"}>6</b><div className={"rank-profile"}><i className={"rank-avatar face-6"}>차</i><span><strong>차트마스터</strong><small>차트 패턴과 추세 매매</small></span></div><span className={"rank-field"}>차트</span><strong className={"rank-accuracy"}>70.9%</strong><strong className={"rank-count"}>856건</strong><div className={"rank-performance"}><b>+6.1%</b><span className={"mini-trend"}><i></i><i></i><i></i><i></i><i></i><i></i></span></div><div className={"rank-tags"}><span>차트</span><span>기술분석</span></div><button type={"button"} data-follow>팔로우</button></article>
                  <article className={"ranking-row"} data-field={"데이터"} data-accuracy={"70.2"} data-count={"1034"} data-performance={"5.4"}><b className={"rank"}>7</b><div className={"rank-profile"}><i className={"rank-avatar face-7"}>A</i><span><strong>안테나프로 <em>✓</em></strong><small>거시경제와 산업 트렌드 분석</small></span></div><span className={"rank-field"}>거시경제</span><strong className={"rank-accuracy"}>70.2%</strong><strong className={"rank-count"}>1,034건</strong><div className={"rank-performance"}><b>+5.4%</b><span className={"mini-trend"}><i></i><i></i><i></i><i></i><i></i><i></i></span></div><div className={"rank-tags"}><span>거시경제</span><span>트렌드</span></div><button type={"button"} data-follow>팔로우</button></article>
                  <article className={"ranking-row extra-row"} data-field={"성장주"} data-accuracy={"70.1"} data-count={"774"} data-performance={"4.9"} hidden><b className={"rank"}>8</b><div className={"rank-profile"}><i className={"rank-avatar face-8"}>성</i><span><strong>성장레이더</strong><small>신산업 성장 모멘텀 추적</small></span></div><span className={"rank-field"}>성장주</span><strong className={"rank-accuracy"}>70.1%</strong><strong className={"rank-count"}>774건</strong><div className={"rank-performance"}><b>+4.9%</b><span className={"mini-trend"}><i></i><i></i><i></i><i></i><i></i><i></i></span></div><div className={"rank-tags"}><span>성장주</span><span>산업분석</span></div><button type={"button"} data-follow>팔로우</button></article>
                  <article className={"ranking-row extra-row"} data-field={"반도체"} data-accuracy={"70.0"} data-count={"702"} data-performance={"4.1"} hidden><b className={"rank"}>9</b><div className={"rank-profile"}><i className={"rank-avatar face-9"}>칩</i><span><strong>칩인사이트</strong><small>글로벌 반도체 공급망 분석</small></span></div><span className={"rank-field"}>반도체</span><strong className={"rank-accuracy"}>70.0%</strong><strong className={"rank-count"}>702건</strong><div className={"rank-performance"}><b>+4.1%</b><span className={"mini-trend"}><i></i><i></i><i></i><i></i><i></i><i></i></span></div><div className={"rank-tags"}><span>반도체</span><span>공급망</span></div><button type={"button"} data-follow>팔로우</button></article>
                </div>
                <p className={"ranking-empty"} id={"ranking-empty"} hidden>조건에 맞는 예측가가 없습니다.</p>
                <button className={"load-predictors"} id={"load-predictors"} type={"button"}>더 많은 예측가 보기 <span>⌄</span></button>
              </section>
            </div>

            <aside className={"predictor-market-side"}>
              <section className={"predictor-stats-card"}><h2>예측가 통계</h2><div className={"stats-grid"}><div><span>활동 중 예측가</span><strong>156<small>명</small></strong><b>+12 <em>(지난 30일)</em></b></div><div><span>총 예측 수</span><strong>28,347<small>건</small></strong><b>+2,841 <em>(지난 30일)</em></b></div><div><span>평균 적중률</span><strong>71.4<small>%</small></strong><b>+1.8%p <em>(지난 30일)</em></b></div><div><span>팔로워 총합</span><strong>254,981<small>명</small></strong><b>+18,732 <em>(지난 30일)</em></b></div></div></section>
              <section className={"category-leaders"}><div className={"side-card-head"}><h2>카테고리별 상위 예측가</h2><button type={"button"}>더보기 ›</button></div><ul><li style={{ '--leader': '#7347e7', '--score': '78.6%' }}><i>▥</i><span>반도체</span><strong>반도체훈련소</strong><b><em></em></b><small>78.6%</small></li><li style={{ '--leader': '#b051e6', '--score': '76.2%' }}><i>◉</i><span>성장주</span><strong>투자왕보</strong><b><em></em></b><small>76.2%</small></li><li style={{ '--leader': '#5395ec', '--score': '74.8%' }}><i>♧</i><span>배당주</span><strong>실속요정</strong><b><em></em></b><small>74.8%</small></li><li style={{ '--leader': '#1daecf', '--score': '73.1%' }}><i>✣</i><span>데이터/스윙</span><strong>데이터헌터</strong><b><em></em></b><small>73.1%</small></li><li style={{ '--leader': '#57b957', '--score': '72.4%' }}><i>▨</i><span>가치주</span><strong>가치투자자</strong><b><em></em></b><small>72.4%</small></li><li style={{ '--leader': '#ff9f13', '--score': '70.9%' }}><i>▧</i><span>차트/기술적</span><strong>차트마스터</strong><b><em></em></b><small>70.9%</small></li></ul></section>
            </aside>
          </div>
        </div>
      </main>
    </>
  )
}
