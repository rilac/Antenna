export default function SimResearch() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner sim-research"}>

          {/* 시나리오 */}
          <section className={"rs-card rs-scenario"}>
            <div className={"rs-block"}>
              <span className={"rs-flag"}>현재 시나리오</span>
              <h1 className={"rs-title"}>시즌 7 · 2008 금융위기 <i className={"rs-info"} title={"리먼 브라더스 파산 전후 120영업일 구간"}>i</i></h1>
            </div>

            <dl className={"rs-block rs-day"}>
              <dd>DAY 4</dd>
              <dt>120영업일 중</dt>
            </dl>

            <dl className={"rs-block"}>
              <dt>현재 시점</dt>
              <dd>2008.10.06 <small>(월)</small></dd>
            </dl>

            <div className={"rs-mood"}>
              <svg width={"42"} height={"42"} viewBox={"0 0 44 44"} fill={"none"} aria-hidden={"true"}>
                <path d={"M4 8l10 12 8-6 10 14"} stroke={"#e0455a"} strokeWidth={"3"} strokeLinecap={"round"} strokeLinejoin={"round"} />
                <path d={"M40 20v10H30"} stroke={"#e0455a"} strokeWidth={"3"} strokeLinecap={"round"} strokeLinejoin={"round"} />
              </svg>
              <div>
                <b>시장 급락 구간 · 변동성 확대</b>
                <span>글로벌 금융 불안이 확산되며 증시 변동성이 커지고 있습니다.</span>
              </div>
            </div>

            <a className={"rs-guide"} href={"#"}>시나리오 가이드</a>
          </section>

          {/* 출처 */}
          <nav className={"rs-sources"} aria-label={"자료 출처"}>
            <button className={"rs-source on"} type={"button"} data-source={"DART"}>
              <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M6 2h8l4 4v16H6z"} /><path d={"M14 2v4h4"} /><path d={"M9 12h6M9 16h6"} /></svg>
              DART
            </button>
            <button className={"rs-source"} type={"button"} data-source={"IR"}>
              <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 9 12 4l9 5"} /><path d={"M5 9v9M9.7 9v9M14.3 9v9M19 9v9"} /><path d={"M3 20h18"} /></svg>
              IR
            </button>
            <button className={"rs-source"} type={"button"} data-source={"NEWS"}>
              <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"14"} rx={"2.5"} /><path d={"M7 9h5M7 13h5M7 16h3"} /><path d={"M16 9h1.5M16 13h1.5"} /></svg>
              NEWS
            </button>
            <button className={"rs-source"} type={"button"} data-source={"경제지표"}>
              <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M4 20V13M9 20V9M14 20v-4M19 20V5"} /><path d={"M3 4l6 5 4-3 7 5"} opacity={".55"} /></svg>
              경제지표
            </button>
          </nav>

          {/* 브리핑 · 뉴스 · 지표 */}
          <div className={"rs-grid"}>

            <section className={"rs-card rs-panel"}>
              <div className={"rs-panel-head"}>
                <h2>
                  <svg width={"23"} height={"23"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"4"} y={"8"} width={"16"} height={"12"} rx={"3"} /><path d={"M12 4v4M9 14v1M15 14v1M2 13v2M22 13v2"} /></svg>
                  AI 한줄 브리핑
                </h2>
                <i className={"rs-info"} title={"당일 자료를 요약한 결과입니다"}>i</i>
              </div>
              <ul className={"rs-bullets"}>
                <li><i><svg width={"13"} height={"13"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"3.4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></i>미국 금융주 부실 우려 확산으로 글로벌 증시 급락</li>
                <li><i><svg width={"13"} height={"13"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"3.4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></i>원/달러 환율 급등 및 외국인 순매도 확대</li>
                <li><i><svg width={"13"} height={"13"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"3.4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></i>정부의 긴급 유동성 공급 및 시장 안정화 조치 발표</li>
              </ul>
              <a className={"rs-detail"} href={"#"}>자세히 보기</a>
            </section>

            <section className={"rs-card rs-panel"}>
              <div className={"rs-panel-head"}>
                <h2>당시 주요 뉴스</h2>
                <i className={"rs-info"} title={"시나리오 시점 기준 보도"}>i</i>
                <a className={"rs-more"} href={"#"}>더 보기 ›</a>
              </div>
              <ul className={"rs-news"}>
                <li><dl><dt>연합뉴스</dt><dd>2008.10.06</dd></dl><a href={"#"}>코스피, 6%대 급락 마감… 1,400선 붕괴</a></li>
                <li><dl><dt>매일경제</dt><dd>2008.10.06</dd></dl><a href={"#"}>美 구제금융 합의 난항… 금융주 추가 하락</a></li>
                <li><dl><dt>한국경제</dt><dd>2008.10.06</dd></dl><a href={"#"}>정부, 10조원 규모 시장 안정화 대책 발표</a></li>
                <li><dl><dt>조선비즈</dt><dd>2008.10.06</dd></dl><a href={"#"}>원/달러 환율 1,300원 돌파… 연중 최고치</a></li>
              </ul>
            </section>

            <section className={"rs-card rs-panel"}>
              <div className={"rs-panel-head"}>
                <h2>당시 경제지표</h2>
                <i className={"rs-info"} title={"전 영업일 대비 등락"}>i</i>
                <a className={"rs-more"} href={"#"}>더 보기 ›</a>
              </div>
              <ul className={"rs-metrics"}>
                <li>
                  <i style={{ background: '#e8f1fe', color: '#2f6fe0' }}><svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 8l6 8 5-5 7 6"} /></svg></i>
                  <b>코스피</b><span className={"val"}>1,398.10</span><span className={"chg fall"}>-6.18%</span>
                </li>
                <li>
                  <i style={{ background: '#eeecfd', color: '#6355e8' }}><svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"14"} rx={"2.5"} /><path d={"M7 10h6M7 13h6"} /></svg></i>
                  <b>원/달러 환율</b><span className={"val"}>1,312.50</span><span className={"chg rise"}>+3.42%</span>
                </li>
                <li>
                  <i style={{ background: '#fdeef0', color: '#e0455a' }}><svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 4 20 12l-8 8-8-8z"} /><path d={"M9 12h6"} /></svg></i>
                  <b>국고채 3년</b><span className={"val"}>5.85%</span><span className={"chg rise"}>+0.21%</span>
                </li>
                <li>
                  <i style={{ background: '#e9f0fe', color: '#2a63c9' }}><svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3s6 6.6 6 11a6 6 0 0 1-12 0c0-4.4 6-11 6-11z"} /></svg></i>
                  <b>WTI 유가</b><span className={"val"}>$95.67</span><span className={"chg fall"}>-3.42%</span>
                </li>
                <li>
                  <i style={{ background: '#eceafd', color: '#5a52d8' }}><svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 16l4-8 4 5 4-9 6 12"} /></svg></i>
                  <b>VIX 지수</b><span className={"val"}>61.72</span><span className={"chg rise"}>+14.35%</span>
                </li>
              </ul>
            </section>
          </div>

          {/* 기업 브리핑 · 타임라인 */}
          <div className={"rs-bottom"}>

            <section className={"rs-card rs-panel"}>
              <div className={"rs-panel-head"}>
                <h2>기업 브리핑 <small>(선택 기업)</small></h2>
                <i className={"rs-info"} title={"모의 투자 중인 종목 기준"}>i</i>
                <a className={"rs-more"} href={"#"}>더 보기 ›</a>
              </div>

              <div className={"rs-company"}>
                <span className={"rs-logo"}>SAMSUNG</span>
                <div><b>삼성전자</b> <span>005930</span></div>
              </div>
              <p className={"rs-asof"}>2008.10.06 기준</p>

              <dl className={"rs-figures"}>
                <div><dt>주가</dt><dd>112,500원<small className={"fall"}>-2,550원 (-2.22%)</small></dd></div>
                <div><dt>시가총액</dt><dd>140.8조원</dd></div>
                <div><dt>PER (TTM)</dt><dd>8.41배</dd></div>
              </dl>

              <p className={"rs-note"}>3분기 잠정 실적 발표 예정. 반도체 수요 둔화 우려 지속.</p>
            </section>

            <section className={"rs-card rs-panel"}>
              <div className={"rs-panel-head"}>
                <h2>주요 역사적 사건 타임라인</h2>
                <i className={"rs-info"} title={"시나리오 구간의 주요 사건"}>i</i>
                <a className={"rs-more"} href={"#"}>더 보기 ›</a>
              </div>

              <ol className={"rs-timeline"}>
                <li className={"rs-event now"}>
                  <span className={"rs-dot"} aria-hidden={"true"}></span>
                  <div className={"rs-event-body"}>
                    <b>美 구제금융 합의 난항</b>
                    <small>의회의 세부 조건 이견으로 구제금융 협상 지연</small>
                  </div>
                  <time dateTime={"2008-09-29"}>2008.09.29</time>
                </li>
                <li className={"rs-event"}>
                  <span className={"rs-dot"} aria-hidden={"true"}></span>
                  <div className={"rs-event-body"}>
                    <b>리먼 브라더스 파산</b>
                    <small>글로벌 금융시장의 신뢰 급격히 악화</small>
                  </div>
                  <time dateTime={"2008-09-15"}>2008.09.15</time>
                </li>
                <li className={"rs-event"}>
                  <span className={"rs-dot"} aria-hidden={"true"}></span>
                  <div className={"rs-event-body"}>
                    <b>AIG 구제금융 결정</b>
                    <small>연방준비제도, AIG에 850억 달러 지원 결정</small>
                  </div>
                  <time dateTime={"2008-09-16"}>2008.09.16</time>
                </li>
                <li className={"rs-event"}>
                  <span className={"rs-dot"} aria-hidden={"true"}></span>
                  <div className={"rs-event-body"}>
                    <b>베어스턴스 위기</b>
                    <small>JP모건에 매각, 연준 긴급 지원 단행</small>
                  </div>
                  <time dateTime={"2008-03-14"}>2008.03.14</time>
                </li>
              </ol>
            </section>
          </div>

        </div>
      </main>
    </>
  )
}
