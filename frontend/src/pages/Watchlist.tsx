export default function Watchlist() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner watch"}>

          <header className={"wl-head"}>
            <div>
              <h1>찜</h1>
              <p>관심 종목과 찜한 리포트 · 예측가를 한곳에서 확인합니다.</p>
            </div>
            <a className={"wl-alarm"} href={"#"}>
              <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M18 8a6 6 0 1 0-12 0c0 6-3 7-3 7h18s-3-1-3-7"} /><path d={"M13.7 21a2 2 0 0 1-3.4 0"} /></svg>
              알림 설정
            </a>
          </header>

          {/* 요약 */}
          <dl className={"wl-card wl-summary"}>
            <div className={"wl-sum"}>
              <i className={"wl-tone-v"}><svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 3v18h18"} /><path d={"M7 15l4-5 3 3 5-7"} /></svg></i>
              <div><dt>찜한 종목</dt><dd id={"wl-count-stock"}>5<small>개</small></dd></div>
            </div>
            <div className={"wl-sum"}>
              <i className={"wl-tone-b"}><svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M6 2h8l4 4v16H6z"} /><path d={"M14 2v4h4"} /><path d={"M9 12h6M9 16h6"} /></svg></i>
              <div><dt>찜한 리포트</dt><dd id={"wl-count-report"}>4<small>건</small></dd></div>
            </div>
            <div className={"wl-sum"}>
              <i className={"wl-tone-g"}><svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"9"} cy={"8"} r={"3.2"} /><path d={"M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20"} /><path d={"M17.5 20v-1a3.5 3.5 0 0 0-3-3.4"} /></svg></i>
              <div><dt>찜한 예측가</dt><dd id={"wl-count-user"}>4<small>명</small></dd></div>
            </div>
            <div className={"wl-sum"}>
              <i className={"wl-tone-o"}><svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M18 8a6 6 0 1 0-12 0c0 6-3 7-3 7h18s-3-1-3-7"} /><path d={"M13.7 21a2 2 0 0 1-3.4 0"} /></svg></i>
              <div><dt>새 소식</dt><dd>3<small>건</small></dd></div>
            </div>
          </dl>

          {/* 탭 */}
          <nav className={"wl-tabs"} role={"group"} aria-label={"찜 분류"}>
            <button className={"on"} type={"button"} data-tab={"stock"}>종목 <b id={"wl-tab-stock"}>5</b></button>
            <button type={"button"} data-tab={"report"}>리포트 <b id={"wl-tab-report"}>4</b></button>
            <button type={"button"} data-tab={"user"}>예측가 <b id={"wl-tab-user"}>4</b></button>
          </nav>

          {/* 종목 */}
          <section className={"wl-card wl-panel"} id={"panel-stock"}>
            <ul className={"wl-stocks"} id={"wl-stock-list"}>

              <li className={"wl-stock"}>
                <span className={"wl-name"}><i className={"wl-logo"} style={{ background: 'linear-gradient(145deg,#1887f1,#0b5ed7)' }}>삼</i><span><b>삼성전자</b><small>005930 · KOSPI</small></span></span>
                <span className={"wl-price"}>71,800원</span>
                <span className={"wl-chg rise"}>+1.42%</span>
                <span className={"wl-dist"}><span>UP 71%<em style={{ fontStyle: 'normal' }}>17건</em></span><i><em style={{ width: '71%' }}></em></i></span>
                <button className={"wl-toggle"} type={"button"} aria-pressed={"true"} aria-label={"삼성전자 알림"}></button>
                <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
              </li>

              <li className={"wl-stock"}>
                <span className={"wl-name"}><i className={"wl-logo"} style={{ background: 'linear-gradient(145deg,#404dff,#3b35d7)' }}>S</i><span><b>SK하이닉스</b><small>000660 · KOSPI</small></span></span>
                <span className={"wl-price"}>198,500원</span>
                <span className={"wl-chg rise"}>+2.85%</span>
                <span className={"wl-dist"}><span>UP 82%<em style={{ fontStyle: 'normal' }}>22건</em></span><i><em style={{ width: '82%' }}></em></i></span>
                <button className={"wl-toggle"} type={"button"} aria-pressed={"true"} aria-label={"SK하이닉스 알림"}></button>
                <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
              </li>

              <li className={"wl-stock"}>
                <span className={"wl-name"}><i className={"wl-logo"} style={{ background: 'linear-gradient(145deg,#12c883,#00a86b)' }}>N</i><span><b>NAVER</b><small>035420 · KOSPI</small></span></span>
                <span className={"wl-price"}>176,300원</span>
                <span className={"wl-chg fall"}>-0.62%</span>
                <span className={"wl-dist"}><span>DOWN 56%<em style={{ fontStyle: 'normal' }}>16건</em></span><i><em style={{ width: '44%' }}></em></i></span>
                <button className={"wl-toggle"} type={"button"} aria-pressed={"false"} aria-label={"NAVER 알림"}></button>
                <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
              </li>

              <li className={"wl-stock"}>
                <span className={"wl-name"}><i className={"wl-logo"} style={{ background: 'linear-gradient(145deg,#c02e67,#981743)' }}>L</i><span><b>LG에너지솔루션</b><small>373220 · KOSPI</small></span></span>
                <span className={"wl-price"}>342,000원</span>
                <span className={"wl-chg fall"}>-1.87%</span>
                <span className={"wl-dist"}><span>DOWN 68%<em style={{ fontStyle: 'normal' }}>19건</em></span><i><em style={{ width: '32%' }}></em></i></span>
                <button className={"wl-toggle"} type={"button"} aria-pressed={"true"} aria-label={"LG에너지솔루션 알림"}></button>
                <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
              </li>

              <li className={"wl-stock"}>
                <span className={"wl-name"}><i className={"wl-logo"} style={{ background: 'linear-gradient(145deg,#13aaa2,#087a7b)' }}>현</i><span><b>현대차</b><small>005380 · KOSPI</small></span></span>
                <span className={"wl-price"}>242,000원</span>
                <span className={"wl-chg rise"}>+0.83%</span>
                <span className={"wl-dist"}><span>UP 60%<em style={{ fontStyle: 'normal' }}>15건</em></span><i><em style={{ width: '60%' }}></em></i></span>
                <button className={"wl-toggle"} type={"button"} aria-pressed={"false"} aria-label={"현대차 알림"}></button>
                <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
              </li>

            </ul>
            <p className={"wl-empty"} id={"wl-empty-stock"} hidden>찜한 종목이 없습니다.</p>
          </section>

          {/* 리포트 */}
          <section className={"wl-card wl-panel"} id={"panel-report"} hidden>
            <ul className={"wl-reports"} id={"wl-report-list"}>

              <li className={"wl-report"}>
                <div className={"wl-report-main"}>
                  <p className={"wl-author"}>
                    <i className={"wl-avatar"} style={{ background: 'linear-gradient(145deg,#3f4a6b,#232a44)' }}>반</i>
                    <b>반도체훈련소</b>
                    <span className={"wl-check"}><svg width={"10"} height={"10"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
                    <span>신뢰도 82.4</span>
                  </p>
                  <h3>HBM4 경쟁 구도와 국내 수혜주</h3>
                  <p>HBM4 양산 일정과 주요 공급망 변화를 바탕으로 핵심 수혜 기업을 분석합니다.</p>
                  <div className={"wl-chips"}><span>반도체</span><span>HBM</span><span>산업분석</span></div>
                </div>
                <div className={"wl-report-side"}>
                  <small>2026.08.26 · 12분</small>
                  <a className={"wl-open"} href={"/reports"}>원문 보기 ↗</a>
                  <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
                </div>
              </li>

              <li className={"wl-report"}>
                <div className={"wl-report-main"}>
                  <p className={"wl-author"}>
                    <i className={"wl-avatar"} style={{ background: 'linear-gradient(145deg,#8b6a4a,#5d4630)' }}>실</i>
                    <b>실속요정</b>
                    <span className={"wl-check"}><svg width={"10"} height={"10"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
                    <span>적중률 72.4%</span>
                  </p>
                  <h3>메모리 업사이클, 어디까지 왔나</h3>
                  <p>메모리 가격 반등의 구조적 요인과 업사이클 지속 가능성을 점검합니다.</p>
                  <div className={"wl-chips"}><span className={"lock"}>🔒 구독자 전용</span><span>반도체</span><span>사이클</span></div>
                </div>
                <div className={"wl-report-side"}>
                  <small>2026.08.25 · 10분</small>
                  <a className={"wl-open"} href={"/reports"}>원문 보기 ↗</a>
                  <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
                </div>
              </li>

              <li className={"wl-report"}>
                <div className={"wl-report-main"}>
                  <p className={"wl-author"}>
                    <i className={"wl-avatar"} style={{ background: 'linear-gradient(145deg,#5b57e0,#332f9e)' }}>데</i>
                    <b>데이터헌터</b>
                    <span className={"wl-check"}><svg width={"10"} height={"10"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
                    <span>적중률 69.8%</span>
                  </p>
                  <h3>금리 인하 이후 성장주 전략</h3>
                  <p>금리 사이클 전환 구간에서 수혜가 기대되는 성장주 섹터와 종목을 제시합니다.</p>
                  <div className={"wl-chips"}><span>금융</span><span>성장주</span><span>전략</span></div>
                </div>
                <div className={"wl-report-side"}>
                  <small>2026.08.25 · 11분</small>
                  <a className={"wl-open"} href={"/reports"}>원문 보기 ↗</a>
                  <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
                </div>
              </li>

              <li className={"wl-report"}>
                <div className={"wl-report-main"}>
                  <p className={"wl-author"}>
                    <i className={"wl-avatar"} style={{ background: 'linear-gradient(145deg,#e5a63c,#c07f14)' }}>가</i>
                    <b>가치투자자</b>
                    <span className={"wl-check"}><svg width={"10"} height={"10"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
                    <span>적중률 68.1%</span>
                  </p>
                  <h3>2차전지 실적 바닥 확인하기</h3>
                  <p>2차전지 주요 기업들의 실적 바닥 신호와 회복 시점을 정리했습니다.</p>
                  <div className={"wl-chips"}><span className={"lock"}>🔒 구독자 전용</span><span>2차전지</span><span>실적</span></div>
                </div>
                <div className={"wl-report-side"}>
                  <small>2026.08.24 · 9분</small>
                  <a className={"wl-open"} href={"/reports"}>원문 보기 ↗</a>
                  <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
                </div>
              </li>

            </ul>
            <p className={"wl-empty"} id={"wl-empty-report"} hidden>찜한 리포트가 없습니다.</p>
          </section>

          {/* 예측가 */}
          <section className={"wl-card wl-panel"} id={"panel-user"} hidden>
            <ul className={"wl-users"} id={"wl-user-list"}>

              <li className={"wl-user"}>
                <span className={"pic"}>
                  <i className={"wl-avatar lg"} style={{ background: 'linear-gradient(145deg,#3f4a6b,#232a44)' }}>반</i>
                  <span className={"wl-check big"}><svg width={"12"} height={"12"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
                </span>
                <div className={"wl-user-main"}>
                  <p className={"wl-user-name"}><b>반도체훈련소</b><span className={"wl-sub"}>구독 중</span></p>
                  <p>반도체 사이클과 HBM 전망 분석</p>
                  <dl className={"wl-user-stats"}>
                    <div><dt>신뢰도</dt><dd>82.4</dd></div>
                    <div><dt>적중률</dt><dd>78.6%</dd></div>
                    <div><dt>구독자</dt><dd>12.3K</dd></div>
                  </dl>
                  <div className={"wl-user-actions"}>
                    <a className={"wl-ghost"} href={"/market"}>프로필 보기</a>
                    <button className={"wl-solid"} type={"button"} aria-pressed={"true"}>구독 중</button>
                  </div>
                </div>
                <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
              </li>

              <li className={"wl-user"}>
                <span className={"pic"}>
                  <i className={"wl-avatar lg"} style={{ background: 'linear-gradient(145deg,#8b6a4a,#5d4630)' }}>실</i>
                  <span className={"wl-check big"}><svg width={"12"} height={"12"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
                </span>
                <div className={"wl-user-main"}>
                  <p className={"wl-user-name"}><b>실속요정</b><span className={"wl-sub"}>구독 중</span></p>
                  <p>배당·가치주 중심의 중장기 전략</p>
                  <dl className={"wl-user-stats"}>
                    <div><dt>신뢰도</dt><dd>76.1</dd></div>
                    <div><dt>적중률</dt><dd>72.4%</dd></div>
                    <div><dt>구독자</dt><dd>8.7K</dd></div>
                  </dl>
                  <div className={"wl-user-actions"}>
                    <a className={"wl-ghost"} href={"/market"}>프로필 보기</a>
                    <button className={"wl-solid"} type={"button"} aria-pressed={"true"}>구독 중</button>
                  </div>
                </div>
                <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
              </li>

              <li className={"wl-user"}>
                <span className={"pic"}>
                  <i className={"wl-avatar lg"} style={{ background: 'linear-gradient(145deg,#5b57e0,#332f9e)' }}>데</i>
                  <span className={"wl-check big"}><svg width={"12"} height={"12"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
                </span>
                <div className={"wl-user-main"}>
                  <p className={"wl-user-name"}><b>데이터헌터</b></p>
                  <p>데이터 기반 스윙 매매와 수급 분석</p>
                  <dl className={"wl-user-stats"}>
                    <div><dt>신뢰도</dt><dd>73.1</dd></div>
                    <div><dt>적중률</dt><dd>69.8%</dd></div>
                    <div><dt>구독자</dt><dd>6.2K</dd></div>
                  </dl>
                  <div className={"wl-user-actions"}>
                    <a className={"wl-ghost"} href={"/market"}>프로필 보기</a>
                    <button className={"wl-solid"} type={"button"} aria-pressed={"false"}>구독하기</button>
                  </div>
                </div>
                <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
              </li>

              <li className={"wl-user"}>
                <span className={"pic"}>
                  <i className={"wl-avatar lg"} style={{ background: 'linear-gradient(145deg,#e5a63c,#c07f14)' }}>가</i>
                </span>
                <div className={"wl-user-main"}>
                  <p className={"wl-user-name"}><b>가치투자자</b></p>
                  <p>실적 기반 가치주 발굴</p>
                  <dl className={"wl-user-stats"}>
                    <div><dt>신뢰도</dt><dd>70.5</dd></div>
                    <div><dt>적중률</dt><dd>68.1%</dd></div>
                    <div><dt>구독자</dt><dd>4.1K</dd></div>
                  </dl>
                  <div className={"wl-user-actions"}>
                    <a className={"wl-ghost"} href={"/market"}>프로필 보기</a>
                    <button className={"wl-solid"} type={"button"} aria-pressed={"false"}>구독하기</button>
                  </div>
                </div>
                <button className={"wl-star"} type={"button"} aria-label={"찜 해제"}>★</button>
              </li>

            </ul>
            <p className={"wl-empty"} id={"wl-empty-user"} hidden>찜한 예측가가 없습니다.</p>
          </section>

        </div>
      </main>
    </>
  )
}
