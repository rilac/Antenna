export default function SimPlay() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner sim-play"}>

          {/* 내 모의투자 현황 */}
          <section className={"sp-card sp-status"}>
            <h2 className={"basic-only"}>내 모의투자 현황 <i className={"sp-info"} title={"현재 세션 기준 자산 요약"}>i</i></h2>
            <dl className={"sp-kpis"}>
              <div className={"sp-kpi"}>
                <div><dt>남은 모의투자금 (현금)</dt><dd className={"brand"}>75,000,000원</dd></div>
                <i className={"tint-v"}><svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} /></svg></i>
              </div>
              <div className={"sp-kpi"}>
                <div><dt>총 자산</dt><dd id={"sp-total"}>105,620,000원<span className={"vs pro-only"}>전일 대비 <b>+1.24%</b></span></dd></div>
                <i className={"tint-b"}><svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 16l5-5 3.5 3.5L21 5"} /><path d={"M3 20h18"} /></svg></i>
              </div>
              <div className={"sp-kpi"}>
                <div><dt>주식 평가금액</dt><dd id={"sp-equity"}>30,620,000원<span className={"vs pro-only"}>전일 대비 <b>+1.56%</b></span></dd></div>
                <i className={"tint-g"}><svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M5 20V11M12 20V5M19 20v-6"} /></svg></i>
              </div>
              <div className={"sp-kpi"}>
                <div><dt>누적 손익 <i className={"sp-info"} title={"세션 시작 대비 손익"}>i</i></dt><dd className={"up"} id={"sp-pl"}>+5,620,000원<small>(+5.62%)</small></dd></div>
                <i className={"tint-g"}><svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 17l6-6 4 4 8-8"} /><path d={"M15 7h6v6"} /></svg></i>
              </div>
            </dl>
          </section>

          {/* 세션 바 */}
          <section className={"sp-card sp-session"}>
            <span className={"sp-season-thumb"} aria-hidden={"true"}>
              <svg viewBox={"0 0 62 62"} fill={"none"}>
                <rect width={"62"} height={"62"} fill={"#1b2036"} />
                <path d={"M4 18l10 12 9-6 10 14 9-9 16 17"} stroke={"#e0455a"} strokeWidth={"2.4"} strokeLinejoin={"round"} />
                <path d={"M4 44h54"} stroke={"#3a4166"} strokeWidth={"1.2"} />
                <text x={"31"} y={"56"} textAnchor={"middle"} fill={"#6d7699"} fontFamily={"monospace"} fontSize={"9"} fontWeight={"700"}>2008</text>
              </svg>
            </span>
            <div className={"sp-session-main"}>
              <p className={"sp-session-title"}>
                <b>시즌 7 · 2008 금융위기</b>
                <i className={"sp-info"} title={"리먼 브라더스 파산 전후 구간"}>i</i>
                <span className={"sp-rule basic-only"} aria-hidden={"true"}></span>
                <span className={"sp-day basic-only"}>DAY 4</span>
                <span className={"sp-date"}>
                  <svg width={"17"} height={"17"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} /></svg>
                  2008.09.19 (금)
                </span>
              </p>
              <p className={"basic-only"}>리먼 브라더스 파산과 AIG의 구제금융 여파로, 투자자들의 위험 회피 심리가 강해지고 있습니다.</p>
            </div>

            <div className={"sp-viewswitch"} role={"group"} aria-label={"화면 모드"}>
              <button className={"on"} type={"button"} data-view={"basic"}>
                <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"M9 14.5c1.6 1.6 4.4 1.6 6 0"} /><path d={"M9 9.5v.5M15 9.5v.5"} /></svg>
                초보자 모드
              </button>
              <button type={"button"} data-view={"pro"}>
                <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M4 20V13M9 20V8M14 20v-5M19 20V4"} /></svg>
                상급자용
              </button>
            </div>
          </section>

          {/* 차트 · 주문 */}
          <div className={"sp-main"}>

            <section className={"sp-card sp-chart-card"}>
              <div className={"sp-chart-head"}>
                <span className={"sp-logo"}>SAMSUNG</span>
                <b>삼성전자</b>
                <span className={"sp-code"}>005930</span>
                <span className={"sp-quote pro-only"}>78,500원</span>
                <span className={"sp-chg rise pro-only"}>▲ 1,300원 (+1.69%)</span>
                <span className={"sp-head-actions"}>
                  <button className={"sp-ghost"} id={"sp-fav"} type={"button"} aria-pressed={"false"}>☆ 관심</button>
                  <button className={"sp-ghost pro-only"} type={"button"}>지표 설정 ⌄</button>
                </span>
              </div>

              <p className={"sp-ohlc pro-only"}>
                <span>시가 <b>77,200</b></span><span>고가 <b>78,900</b></span><span>저가 <b>76,100</b></span><span>거래량 <b>12,532,104</b></span>
              </p>

              <div className={"sp-tabs"}>
                <span className={"sp-seg"} role={"group"} aria-label={"봉 종류"}>
                  <button className={"on"} type={"button"}>일봉</button>
                  <button type={"button"}>주봉</button>
                  <button type={"button"}>월봉</button>
                </span>
                <span className={"sp-seg line pro-only"} role={"group"} aria-label={"보조 지표"}>
                  <button className={"on"} type={"button"}>지표</button>
                  <button type={"button"}>MACD</button>
                  <button type={"button"}>볼린저밴드</button>
                </span>
              </div>

              <p className={"sp-ma pro-only"}>
                <span><i style={{ background: '#f0a72c' }}></i>MA5 77,840</span>
                <span><i style={{ background: '#7b6cf0' }}></i>MA20 77,120</span>
                <span><i style={{ background: '#16a06a' }}></i>MA60 76,230</span>
              </p>

              <svg className={"sp-chart basic-only"} id={"sp-chart-basic"} viewBox={"0 0 980 380"} role={"img"} aria-label={"삼성전자 일중 주가 추이"}></svg>
              <svg className={"sp-chart pro-only"} id={"sp-chart-pro"} viewBox={"0 0 980 460"} role={"img"} aria-label={"삼성전자 일봉 캔들 차트와 거래량"}></svg>

              <p className={"sp-chart-note basic-only"}>* 현재가는 20분이 지연될 수 있습니다.</p>

              <dl className={"sp-holding basic-only"}>
                <div><dt>평균 매입가</dt><dd>77,800원</dd></div>
                <div><dt>현재가</dt><dd>78,500원<small className={"rise"}>(+0.90%)</small></dd></div>
                <div><dt>평가 손익</dt><dd className={"rise"}>+700,000원<small className={"rise"}>(+0.90%)</small></dd></div>
                <div><dt>보유 수량</dt><dd>100주</dd></div>
              </dl>
            </section>

            <section className={"sp-card sp-order"}>
              <div className={"sp-order-tabs"} role={"group"} aria-label={"주문 구분"}>
                <button className={"on"} type={"button"} data-side={"buy"}>매수</button>
                <button type={"button"} data-side={"sell"}>매도</button>
              </div>

              <div className={"sp-field pro-only"}>
                <span className={"sp-label"}>주문 유형</span>
                <span className={"sp-seg"} role={"group"} aria-label={"주문 유형"}>
                  <button className={"on"} type={"button"} data-ordertype={"market"}>시장가</button>
                  <button type={"button"} data-ordertype={"limit"}>지정가</button>
                </span>
              </div>

              <div className={"sp-field"}>
                <span className={"sp-label"}>주문 수량</span>
                <span className={"sp-stepper"}>
                  <button type={"button"} id={"sp-minus"} aria-label={"수량 감소"}>−</button>
                  <label className={"sr-only"} htmlFor={"sp-qty"}>주문 수량</label>
                  <input id={"sp-qty"} type={"text"} inputMode={"numeric"} defaultValue={"100"} />
                  <span>주</span>
                  <button type={"button"} id={"sp-plus"} aria-label={"수량 증가"}>+</button>
                </span>
                <span className={"sp-quick"}>
                  <button type={"button"} data-add={"10"}>+10</button>
                  <button type={"button"} data-add={"100"}>+100</button>
                  <button type={"button"} data-add={"1000"}>+1,000</button>
                </span>
              </div>

              <div className={"sp-field pro-only"}>
                <span className={"sp-label"}>목표가 (선택)</span>
                <span className={"sp-input"}><input type={"text"} inputMode={"numeric"} defaultValue={"82,000"} aria-label={"목표가"} /><span>원</span><button type={"button"} aria-label={"목표가 지우기"}>×</button></span>
              </div>
              <div className={"sp-field pro-only"}>
                <span className={"sp-label"}>손절가 (선택)</span>
                <span className={"sp-input"}><input type={"text"} inputMode={"numeric"} defaultValue={"74,500"} aria-label={"손절가"} /><span>원</span><button type={"button"} aria-label={"손절가 지우기"}>×</button></span>
              </div>

              <dl>
                <div className={"sp-line basic-only"}><dt>현재가 <small>78,500원 (09.19. 18가)</small></dt><dd>78,500원</dd></div>
                <div className={"sp-line"}><dt>예상 주문 금액</dt><dd id={"sp-amount"}>7,850,000원</dd></div>
                <div className={"sp-line sm pro-only"}><dt>수수료(추정) <i className={"sp-info"} title={"0.02% 가정"}>i</i></dt><dd id={"sp-fee"}>1,570원</dd></div>
                <div className={"sp-line total pro-only"}><dt>총 예상 금액</dt><dd id={"sp-grand"}>7,851,570원</dd></div>
              </dl>

              <button className={"sp-submit"} id={"sp-submit"} type={"button"}>매수 주문</button>
              <a className={"sp-next"} href={"/sim/play"}>
                <svg width={"20"} height={"20"} viewBox={"0 0 24 24"} fill={"currentColor"} aria-hidden={"true"}><path d={"M4 5l7 7-7 7zM13 5l7 7-7 7z"} /></svg>
                다음 영업일 진행
              </a>
              <p className={"sp-order-note"}>
                <svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"M12 11v5"} /><path d={"M12 7.8v.4"} /></svg>
                주문은 다음 영업일에 반영됩니다.
              </p>
            </section>
          </div>

          {/* 하단 3분할 */}
          <div className={"sp-bottom"}>

            <section className={"sp-card sp-panel"}>
              <h2>
                <svg width={"21"} height={"21"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"4"} y={"8"} width={"16"} height={"12"} rx={"3"} /><path d={"M12 4v4M9 14v1M15 14v1M2 13v2M22 13v2"} /></svg>
                <span className={"basic-only"}>AI 힌트 요약</span><span className={"pro-only"}>AI 한줄 요약</span>
              </h2>
              <ul className={"sp-bullets basic-only"}>
                <li><i></i>글로벌 금융 불안으로 외국인 매도세 지속</li>
                <li><i></i>원/달러 환율 급등이 수익성에 부담 요인</li>
                <li><i></i>정부의 시장 안정화 정책 발표 기대감 존재</li>
              </ul>
              <ul className={"sp-bullets pro-only"}>
                <li><i></i>단기 반등 시도 구간, 76,000원대 지지 주목</li>
                <li><i></i>거래량 증가와 장중심 돌파 시 매수 신호 강화</li>
                <li><i></i>RSI 53.2, 과열·과매도 구간 아님 (중립)</li>
              </ul>
              <a className={"sp-panel-more"} href={"#"}><span className={"basic-only"}>자세히 보기</span><span className={"pro-only"}>더보기</span> ›</a>
            </section>

            <section className={"sp-card sp-panel"}>
              <h2>
                <svg width={"21"} height={"21"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"4"} y={"3"} width={"16"} height={"18"} rx={"2.5"} /><path d={"M8 8h8M8 12h8M8 16h5"} /></svg>
                <span className={"basic-only"}>당시 주요 이슈</span><span className={"pro-only"}>당시 주요 이슈 &amp; 경제지표</span>
              </h2>

              <ul className={"sp-issues basic-only"}>
                <li className={"sp-issue"}><i>1</i><div><b>글로벌 금융시장 불안 지속</b></div><time>2008.09.19</time></li>
                <li className={"sp-issue"}><i>2</i><div><b>원/달러 환율 1,300원 돌파</b></div><time>2008.09.18</time></li>
                <li className={"sp-issue"}><i>3</i><div><b>한국은행 기준금리 동결 (5.00%)</b></div><time>2008.09.18</time></li>
              </ul>

              <ul className={"sp-issues pro-only"}>
                <li className={"sp-issue"}>
                  <i>📄</i>
                  <div><b>美 리먼브라더스 파산 충격 확산</b><small>글로벌 금융시장 변동성 확대</small></div>
                  <time>2008.09.15</time>
                </li>
                <li className={"sp-issue"}>
                  <i>📄</i>
                  <div><b>美 연준, 긴급 금리 인하 (0.50pp)</b><small>금융시장 안정화 조치 시행</small></div>
                  <time>2008.09.16</time>
                </li>
                <li className={"sp-issue"}>
                  <i>📄</i>
                  <div><b>코스피 1,400선 붕괴</b><small>외국인 매도, 유동성 우려 지속</small></div>
                  <time>2008.09.19</time>
                </li>
              </ul>
              <a className={"sp-panel-more"} href={"#"}><span className={"basic-only"}>더 보기</span><span className={"pro-only"}>더보기</span> ›</a>
            </section>

            <section className={"sp-card sp-panel"}>
              <h2>
                <svg width={"21"} height={"21"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"M12 7v6l4 2"} /></svg>
                내 포트폴리오 요약
              </h2>
              <div className={"sp-portfolio"}>
                <div className={"sp-donut"}>
                  <svg viewBox={"0 0 42 42"} width={"150"} height={"150"} aria-hidden={"true"}>
                    <circle cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#f0f1f7"} strokeWidth={"7"} />
                    <circle className={"sp-arc"} cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#4a7bf7"} strokeWidth={"7"} strokeDasharray={"30.6 69.4"} strokeDashoffset={"0"} />
                    <circle className={"sp-arc"} cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#9b93f5"} strokeWidth={"7"} strokeDasharray={"71 29"} strokeDashoffset={"-30.6"} />
                    <circle className={"sp-arc"} cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#f0b429"} strokeWidth={"7"} strokeDasharray={"1.6 98.4"} strokeDashoffset={"-101.6"} />
                  </svg>
                  <span className={"sp-donut-mid"}><small>총 자산</small><b id={"sp-donut-total"}>105,620,000원</b></span>
                </div>
                <ul className={"sp-legend"}>
                  <li><i style={{ background: '#4a7bf7' }}></i><b>삼성전자</b><em>30.6%</em><span id={"sp-leg-stock"}>30,620,000원</span></li>
                  <li><i style={{ background: '#9b93f5' }}></i><b>현금</b><em id={"sp-leg-cash-pct"}>71.0%</em><span>75,000,000원</span></li>
                  <li><i style={{ background: '#f0b429' }}></i><b>기타</b><em id={"sp-leg-etc-pct"}>-1.6%</em><span id={"sp-leg-etc"}>-1,600,000원</span></li>
                </ul>
              </div>
              <a className={"sp-panel-more"} href={"/sim/portfolio"}>내 포트폴리오 보기 ›</a>
            </section>
          </div>

        </div>
      </main>
    </>
  )
}
