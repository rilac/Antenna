export default function Stocks() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner stock-explorer"}>
          <header className={"explorer-hero"}>
            <div>
              <span className={"explorer-eyebrow"}>MARKET EXPLORER</span>
              <h1>어떤 섹터가 움직이고 있나요?</h1>
              <p>시장 지표와 검증 가능한 예측 흐름을 함께 비교해 보세요.</p>
            </div>
            <p className={"market-timestamp"}><i></i> 직전 영업일 종가 <span>·</span> 2025-06-16 15:30</p>
          </header>

          <section className={"sector-summary"} aria-label={"섹터별 시장 요약"}>
            <button className={"sector-card active"} data-summary-sector={"전체 종목"}><strong>전체 종목</strong><span>86개 <b className={"rise"}>+0.42%</b></span></button>
            <button className={"sector-card"} data-summary-sector={"반도체"}><strong>반도체</strong><span>14개 <b className={"rise"}>+1.84%</b></span></button>
            <button className={"sector-card"} data-summary-sector={"2차전지"}><strong>2차전지</strong><span>11개 <b className={"fall"}>-1.12%</b></span></button>
            <button className={"sector-card"} data-summary-sector={"자동차"}><strong>자동차</strong><span>9개 <b className={"rise"}>+0.77%</b></span></button>
            <button className={"sector-card"} data-summary-sector={"인터넷"}><strong>인터넷</strong><span>8개 <b className={"fall"}>-0.38%</b></span></button>
            <button className={"sector-card"} data-summary-sector={"바이오"}><strong>바이오</strong><span>17개 <b className={"rise"}>+0.23%</b></span></button>
            <button className={"sector-card"} data-summary-sector={"금융"}><strong>금융</strong><span>12개 <b className={"rise"}>+0.51%</b></span></button>
          </section>

          <div className={"explorer-body"}>
            <aside className={"filter-panel"} id={"filter-panel"} aria-labelledby={"filter-title"} hidden>
              <h2 id={"filter-title"}>상세 필터</h2>

              <fieldset className={"filter-group"}>
                <legend>시장</legend>
                <div className={"filter-options market-options"}>
                  <button className={"filter-chip active"} data-market={"전체"}>전체</button>
                  <button className={"filter-chip"} data-market={"KOSPI"}>KOSPI</button>
                  <button className={"filter-chip"} data-market={"KOSDAQ"}>KOSDAQ</button>
                </div>
              </fieldset>

              <fieldset className={"filter-group"}>
                <legend>섹터</legend>
                <div className={"filter-options sector-options"}>
                  <button className={"filter-chip active"} data-sector={"전체 종목"}>전체 종목</button>
                  <button className={"filter-chip"} data-sector={"반도체"}>반도체</button>
                  <button className={"filter-chip"} data-sector={"2차전지"}>2차전지</button>
                  <button className={"filter-chip"} data-sector={"자동차"}>자동차</button>
                  <button className={"filter-chip"} data-sector={"인터넷"}>인터넷</button>
                  <button className={"filter-chip"} data-sector={"바이오"}>바이오</button>
                  <button className={"filter-chip"} data-sector={"금융"}>금융</button>
                </div>
              </fieldset>

              <fieldset className={"filter-group per-filter"}>
                <legend>PER 범위</legend>
                <div className={"range-inputs"}>
                  <label><span className={"sr-only"}>최소 PER</span><input id={"per-min"} type={"number"} min={"0"} step={"0.1"} placeholder={"최소"} /></label>
                  <span>–</span>
                  <label><span className={"sr-only"}>최대 PER</span><input id={"per-max"} type={"number"} min={"0"} step={"0.1"} placeholder={"최대"} /></label>
                </div>
              </fieldset>

              <fieldset className={"filter-group extra-filter"}>
                <legend>추가 조건</legend>
                <label className={"check-option"}><input id={"ongoing-only"} type={"checkbox"} /><span></span> 내가 진행 중인 예측 있음</label>
                <label className={"check-option"}><input id={"favorite-only"} type={"checkbox"} /><span></span> 관심 종목만 보기</label>
              </fieldset>

              <div className={"filter-actions"}>
                <button id={"apply-filter"} className={"apply-filter"}>필터 적용</button>
                <button id={"reset-filter"} className={"reset-filter"}>필터 초기화</button>
              </div>
            </aside>

            <section className={"stock-list-panel"} aria-labelledby={"stock-list-title"}>
              <div className={"stock-list-toolbar"}>
                <h2 id={"stock-list-title"}>종목 리스트 <strong><span id={"visible-count"}>10</span>개 종목</strong></h2>
                <label className={"stock-search"}>
                  <span aria-hidden={"true"}>⌕</span>
                  <input id={"stock-search"} type={"search"} placeholder={"종목명 또는 종목코드 검색"} />
                </label>
                <label className={"sort-select"}><span className={"sr-only"}>정렬 기준</span><select id={"stock-sort"}><option value={"predictions"}>예측 많은 순</option><option value={"change"}>등락률 높은 순</option><option value={"price"}>현재가 높은 순</option><option value={"name"}>종목명 순</option></select></label>
                <button className={"filter-toggle"} id={"filter-toggle"} type={"button"} aria-expanded={"false"} aria-controls={"filter-panel"} aria-label={"상세 필터 열기"}>
                  <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 5h18l-7 8.5V19l-4 2v-7.5z"} /></svg>
                  <b className={"filter-badge"} id={"filter-badge"} hidden></b>
                </button>
              </div>

              <div className={"stock-list-scroll"}>
                <table className={"discovery-table"}>
                  <thead><tr><th>종목</th><th>현재가</th><th>등락률</th><th>PER</th><th>PBR</th><th>예측 방향</th><th>관심</th></tr></thead>
                  <tbody id={"stock-rows"}>
                    <tr data-name={"SK하이닉스"} data-code={"000660"} data-market={"KOSPI"} data-sector={"반도체"} data-per={"9.8"} data-price={"198500"} data-change={"2.85"} data-predictions={"22"} data-ongoing={"true"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-skhy"}>S</i><span><b>SK하이닉스</b><small>000660 · KOSPI · 반도체</small></span></span></td><td className={"num"}>198,500</td><td className={"num rise"}>+2.85%</td><td className={"num metric"}>9.8x</td><td className={"num metric"}>1.72x</td><td><div className={"prediction-cell"}><span><b className={"rise"}>UP 82%</b><small>22건</small></span><i style={{ '--up': '82%' }}></i></div></td><td><button className={"favorite-button active"} aria-label={"SK하이닉스 관심 종목 해제"} aria-pressed={"true"}>★</button></td>
                    </tr>
                    <tr data-name={"에코프로비엠"} data-code={"247540"} data-market={"KOSDAQ"} data-sector={"2차전지"} data-per={"48.5"} data-price={"167200"} data-change={"-1.36"} data-predictions={"19"} data-ongoing={"false"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-ecopro"}>에</i><span><b>에코프로비엠</b><small>247540 · KOSDAQ · 2차전지</small></span></span></td><td className={"num"}>167,200</td><td className={"num fall"}>-1.36%</td><td className={"num metric"}>48.5x</td><td className={"num metric"}>5.24x</td><td><div className={"prediction-cell"}><span><b className={"fall"}>DOWN 68%</b><small>19건</small></span><i style={{ '--up': '32%' }}></i></div></td><td><button className={"favorite-button"} aria-label={"에코프로비엠 관심 종목 추가"} aria-pressed={"false"}>☆</button></td>
                    </tr>
                    <tr data-name={"LG에너지솔루션"} data-code={"373220"} data-market={"KOSPI"} data-sector={"2차전지"} data-per={"72.4"} data-price={"342000"} data-change={"-1.87"} data-predictions={"19"} data-ongoing={"false"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-lgenergy"}>L</i><span><b>LG에너지솔루션</b><small>373220 · KOSPI · 2차전지</small></span></span></td><td className={"num"}>342,000</td><td className={"num fall"}>-1.87%</td><td className={"num metric"}>72.4x</td><td className={"num metric"}>3.61x</td><td><div className={"prediction-cell"}><span><b className={"fall"}>DOWN 68%</b><small>19건</small></span><i style={{ '--up': '32%' }}></i></div></td><td><button className={"favorite-button active"} aria-label={"LG에너지솔루션 관심 종목 해제"} aria-pressed={"true"}>★</button></td>
                    </tr>
                    <tr data-name={"삼성전자"} data-code={"005930"} data-market={"KOSPI"} data-sector={"반도체"} data-per={"14.3"} data-price={"71800"} data-change={"1.42"} data-predictions={"17"} data-ongoing={"true"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-samsung"}>삼</i><span><b>삼성전자</b><small>005930 · KOSPI · 반도체</small></span></span></td><td className={"num"}>71,800</td><td className={"num rise"}>+1.42%</td><td className={"num metric"}>14.3x</td><td className={"num metric"}>1.31x</td><td><div className={"prediction-cell"}><span><b className={"rise"}>UP 71%</b><small>17건</small></span><i style={{ '--up': '71%' }}></i></div></td><td><button className={"favorite-button active"} aria-label={"삼성전자 관심 종목 해제"} aria-pressed={"true"}>★</button></td>
                    </tr>
                    <tr data-name={"NAVER"} data-code={"035420"} data-market={"KOSPI"} data-sector={"인터넷"} data-per={"18.7"} data-price={"176300"} data-change={"-0.62"} data-predictions={"16"} data-ongoing={"false"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-naver2"}>N</i><span><b>NAVER</b><small>035420 · KOSPI · 인터넷</small></span></span></td><td className={"num"}>176,300</td><td className={"num fall"}>-0.62%</td><td className={"num metric"}>18.7x</td><td className={"num metric"}>1.12x</td><td><div className={"prediction-cell"}><span><b className={"fall"}>DOWN 56%</b><small>16건</small></span><i style={{ '--up': '44%' }}></i></div></td><td><button className={"favorite-button active"} aria-label={"NAVER 관심 종목 해제"} aria-pressed={"true"}>★</button></td>
                    </tr>
                    <tr data-name={"현대차"} data-code={"005380"} data-market={"KOSPI"} data-sector={"자동차"} data-per={"5.4"} data-price={"242000"} data-change={"0.83"} data-predictions={"15"} data-ongoing={"false"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-hyundai"}>현</i><span><b>현대차</b><small>005380 · KOSPI · 자동차</small></span></span></td><td className={"num"}>242,000</td><td className={"num rise"}>+0.83%</td><td className={"num metric"}>5.4x</td><td className={"num metric"}>0.68x</td><td><div className={"prediction-cell"}><span><b className={"rise"}>UP 60%</b><small>15건</small></span><i style={{ '--up': '60%' }}></i></div></td><td><button className={"favorite-button active"} aria-label={"현대차 관심 종목 해제"} aria-pressed={"true"}>★</button></td>
                    </tr>
                    <tr data-name={"카카오"} data-code={"035720"} data-market={"KOSPI"} data-sector={"인터넷"} data-per={"25.2"} data-price={"42150"} data-change={"-1.04"} data-predictions={"14"} data-ongoing={"false"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-kakao2"}>카</i><span><b>카카오</b><small>035720 · KOSPI · 인터넷</small></span></span></td><td className={"num"}>42,150</td><td className={"num fall"}>-1.04%</td><td className={"num metric"}>25.2x</td><td className={"num metric"}>1.08x</td><td><div className={"prediction-cell"}><span><b className={"fall"}>DOWN 61%</b><small>14건</small></span><i style={{ '--up': '39%' }}></i></div></td><td><button className={"favorite-button"} aria-label={"카카오 관심 종목 추가"} aria-pressed={"false"}>☆</button></td>
                    </tr>
                    <tr data-name={"셀트리온"} data-code={"068270"} data-market={"KOSPI"} data-sector={"바이오"} data-per={"41.9"} data-price={"194600"} data-change={"-0.28"} data-predictions={"12"} data-ongoing={"false"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-celltrion"}>셀</i><span><b>셀트리온</b><small>068270 · KOSPI · 바이오</small></span></span></td><td className={"num"}>194,600</td><td className={"num fall"}>-0.28%</td><td className={"num metric"}>41.9x</td><td className={"num metric"}>2.54x</td><td><div className={"prediction-cell"}><span><b className={"rise"}>UP 55%</b><small>12건</small></span><i style={{ '--up': '55%' }}></i></div></td><td><button className={"favorite-button"} aria-label={"셀트리온 관심 종목 추가"} aria-pressed={"false"}>☆</button></td>
                    </tr>
                    <tr data-name={"삼성바이오로직스"} data-code={"207940"} data-market={"KOSPI"} data-sector={"바이오"} data-per={"62.1"} data-price={"968000"} data-change={"0.37"} data-predictions={"9"} data-ongoing={"true"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-sambio"}>삼</i><span><b>삼성바이오로직스</b><small>207940 · KOSPI · 바이오</small></span></span></td><td className={"num"}>968,000</td><td className={"num rise"}>+0.37%</td><td className={"num metric"}>62.1x</td><td className={"num metric"}>6.48x</td><td><div className={"prediction-cell"}><span><b className={"rise"}>UP 67%</b><small>9건</small></span><i style={{ '--up': '67%' }}></i></div></td><td><button className={"favorite-button"} aria-label={"삼성바이오로직스 관심 종목 추가"} aria-pressed={"false"}>☆</button></td>
                    </tr>
                    <tr data-name={"KB금융"} data-code={"105560"} data-market={"KOSPI"} data-sector={"금융"} data-per={"6.2"} data-price={"87900"} data-change={"0.11"} data-predictions={"8"} data-ongoing={"false"}>
                      <td><span className={"company-cell"}><i className={"company-logo logo-kb"}>K</i><span><b>KB금융</b><small>105560 · KOSPI · 금융</small></span></span></td><td className={"num"}>87,900</td><td className={"num rise"}>+0.11%</td><td className={"num metric"}>6.2x</td><td className={"num metric"}>0.59x</td><td><div className={"prediction-cell"}><span><b className={"rise"}>UP 63%</b><small>8건</small></span><i style={{ '--up': '63%' }}></i></div></td><td><button className={"favorite-button"} aria-label={"KB금융 관심 종목 추가"} aria-pressed={"false"}>☆</button></td>
                    </tr>
                  </tbody>
                </table>
                <p id={"empty-stocks"} className={"empty-stocks"} hidden>조건에 맞는 종목이 없습니다.</p>
              </div>

              <nav className={"stock-pagination"} aria-label={"종목 목록 페이지"}><button aria-label={"이전 페이지"}>‹</button><button className={"active"} aria-current={"page"}>1</button><button>2</button><button>3</button><button aria-label={"다음 페이지"}>›</button></nav>
            </section>
          </div>
        </div>
      </main>
    </>
  )
}
