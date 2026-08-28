import Carousel from "../components/Carousel"

export default function SimHome() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner sim-home"}>

          {/* 현재 시즌 */}
          <section className={"sh-hero"}>
            <div className={"sh-hero-copy"}>
              <span className={"sh-flag"}>현재 시즌</span>
              <h1 className={"sh-title"}>시즌 7 · 2008 금융위기 <i className={"sh-info"} title={"2008년 9월 리먼 브라더스 파산 전후 120영업일 구간"}>i</i></h1>
              <p className={"sh-lead"}>2008년 글로벌 금융위기 시기를 모의 투자하며<br />투자 전략을 키워보세요.</p>

              <p className={"sh-progress-label"}>진행률</p>
              <div className={"sh-progress"}>
                <span className={"sh-bar"} role={"progressbar"} aria-valuenow={65} aria-valuemin={0} aria-valuemax={100} aria-label={"시즌 진행률"}>
                  <i style={{ width: '65%' }}></i>
                </span>
                <b>65%</b>
              </div>

              <div className={"sh-stats"}>
                <dl className={"sh-stat"}>
                  <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                    <rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} />
                  </svg>
                  <div><dt>현재 게임일</dt><dd className={"num"}>D+128 (2008.08.24)</dd></div>
                </dl>
                <dl className={"sh-stat"}>
                  <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                    <circle cx={"12"} cy={"12"} r={"9"} /><path d={"M12 7v5.5l3.5 2"} />
                  </svg>
                  <div><dt>남은 기간</dt><dd className={"num"}>20일 14시간</dd></div>
                </dl>
                <dl className={"sh-stat"}>
                  <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                    <path d={"M4 20V10M10 20V4M16 20v-7M22 20H2"} />
                  </svg>
                  <div><dt>총 기간</dt><dd className={"num"}>120일</dd></div>
                </dl>
              </div>
            </div>


            <div className={"sh-cta"}>
              <a className={"sh-btn solid"} href={"/sim/setup"}>
                <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}>
                  <circle cx={"12"} cy={"12"} r={"9"} /><path d={"m10 8.2 6 3.8-6 3.8z"} fill={"currentColor"} />
                </svg>
                모의 투자 시작하기
              </a>
              <a className={"sh-btn ghost"} href={"/sim/portfolio"}>
                <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                  <path d={"M3 12a9 9 0 1 0 2.6-6.4"} /><path d={"M3 4v5h5"} /><path d={"M12 8v4.5l3 1.8"} />
                </svg>
                지난 기록 보기
              </a>
            </div>
          </section>

          {/* 요약 지표 */}
          <section className={"sh-kpis"} aria-label={"모의 투자 요약"}>
            <dl className={"sh-kpi"}>
              <div><dt>최근 모의 투자 수</dt><dd className={"num"}>24<small>회</small></dd></div>
              <i className={"tint-brand"}>
                <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                  <rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} />
                </svg>
              </i>
            </dl>
            <dl className={"sh-kpi"}>
              <div><dt>평균 수익률</dt><dd className={"num up"}>+18.6%</dd></div>
              <i className={"tint-green"}>
                <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                  <path d={"M3 17l6-6 4 4 8-8"} /><path d={"M15 7h6v6"} />
                </svg>
              </i>
            </dl>
            <dl className={"sh-kpi"}>
              <div><dt>최고 수익률</dt><dd className={"num up"}>+35.7%</dd></div>
              <i className={"tint-green"}>
                <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                  <path d={"M3 17l6-6 4 4 8-8"} /><path d={"M15 7h6v6"} />
                </svg>
              </i>
            </dl>
            <dl className={"sh-kpi"}>
              <div><dt>승률</dt><dd className={"num"}>62.5%</dd></div>
              <i className={"tint-brand"}>
                <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}>
                  <circle cx={"12"} cy={"12"} r={"9"} /><circle cx={"12"} cy={"12"} r={"5"} /><circle cx={"12"} cy={"12"} r={"1.6"} fill={"currentColor"} />
                </svg>
              </i>
            </dl>
          </section>

          {/* 최근 / 인기 / 보상 */}
          <Carousel as="section" className="sh-panels">

            <article className={"sh-panel"}>
              <div className={"sh-panel-head"}>
                <h2>최근 모의 투자</h2>
                <a href={"/sim/portfolio"}>전체 보기 ›</a>
              </div>
              <ul className={"sh-runs"}>
                <li className={"sh-run"}>
                  <span className={"sh-state live"}>진행중</span>
                  <div className={"sh-run-main"}><b>시즌 7 · 2008 금융위기</b><small>D+128 (2008.08.24)</small></div>
                  <dl className={"sh-return"}><dt>수익률</dt><dd className={"rise"}>+25.3%</dd></dl>
                  <a className={"sh-replay"} href={"/sim/play"}>다시 플레이</a>
                </li>
                <li className={"sh-run"}>
                  <span className={"sh-state done"}>완료</span>
                  <div className={"sh-run-main"}><b>시즌 6 · 2000 닷컴 버블</b><small>2000.03.10 종료</small></div>
                  <dl className={"sh-return"}><dt>수익률</dt><dd className={"fall"}>-8.6%</dd></dl>
                  <a className={"sh-replay"} href={"/sim/play"}>다시 플레이</a>
                </li>
                <li className={"sh-run"}>
                  <span className={"sh-state done"}>완료</span>
                  <div className={"sh-run-main"}><b>시즌 5 · 1997 아시아 금융위기</b><small>1997.12.31 종료</small></div>
                  <dl className={"sh-return"}><dt>수익률</dt><dd className={"rise"}>+12.1%</dd></dl>
                  <a className={"sh-replay"} href={"/sim/play"}>다시 플레이</a>
                </li>
              </ul>
              <a className={"sh-panel-foot"} href={"/sim/portfolio"}>내 모의 투자 더보기 ›</a>
            </article>

            <article className={"sh-panel"}>
              <div className={"sh-panel-head"}>
                <h2>인기 시즌 / 추천 모의 투자</h2>
              </div>
              <ul className={"sh-seasons"}>
                <li className={"sh-season"}>
                  <span className={"sh-thumb"}>
                    <svg viewBox={"0 0 70 70"} fill={"none"} aria-hidden={"true"}>
                      <rect width={"70"} height={"70"} fill={"#2d3a63"} />
                      <circle cx={"35"} cy={"30"} r={"11"} fill={"#f2c86a"} opacity={".9"} />
                      <path d={"M0 46h70v24H0z"} fill={"#1d2748"} />
                      <g fill={"#3d4c7a"}><rect x={"8"} y={"34"} width={"9"} height={"14"} /><rect x={"21"} y={"28"} width={"8"} height={"20"} /><rect x={"41"} y={"30"} width={"9"} height={"18"} /><rect x={"54"} y={"36"} width={"8"} height={"12"} /></g>
                      <path d={"M0 58h70"} stroke={"#5a6a9e"} strokeWidth={"1.5"} opacity={".6"} />
                    </svg>
                  </span>
                  <div className={"sh-season-main"}>
                    <p className={"sh-season-name"}><span className={"sh-rank"}>1</span><b>시즌 6 · 2000 닷컴 버블</b></p>
                    <p className={"sh-season-desc"}>기술주 급등과 붕괴의 시기</p>
                  </div>
                  <a className={"sh-pick"} href={"/sim/play"}>추천</a>
                </li>
                <li className={"sh-season"}>
                  <span className={"sh-thumb"}>
                    <svg viewBox={"0 0 70 70"} fill={"none"} aria-hidden={"true"}>
                      <rect width={"70"} height={"70"} fill={"#3a3358"} />
                      <circle cx={"46"} cy={"24"} r={"9"} fill={"#e59a6a"} opacity={".9"} />
                      <path d={"M0 44h70v26H0z"} fill={"#251f3d"} />
                      <g fill={"#4b426e"}><rect x={"6"} y={"26"} width={"10"} height={"18"} /><rect x={"20"} y={"32"} width={"8"} height={"12"} /><rect x={"32"} y={"22"} width={"9"} height={"22"} /><rect x={"56"} y={"30"} width={"9"} height={"14"} /></g>
                      <path d={"M0 56h70"} stroke={"#6a5f96"} strokeWidth={"1.5"} opacity={".6"} />
                    </svg>
                  </span>
                  <div className={"sh-season-main"}>
                    <p className={"sh-season-name"}><span className={"sh-rank"}>2</span><b>시즌 5 · 1997 아시아 금융위기</b></p>
                    <p className={"sh-season-desc"}>아시아 통화위기와 시장 변동</p>
                  </div>
                  <a className={"sh-pick"} href={"/sim/play"}>추천</a>
                </li>
                <li className={"sh-season"}>
                  <span className={"sh-thumb"}>
                    <svg viewBox={"0 0 70 70"} fill={"none"} aria-hidden={"true"}>
                      <rect width={"70"} height={"70"} fill={"#4a3a52"} />
                      <circle cx={"24"} cy={"26"} r={"10"} fill={"#efb267"} opacity={".9"} />
                      <path d={"M0 46h70v24H0z"} fill={"#2f2537"} />
                      <g fill={"#5e4a68"}><rect x={"12"} y={"34"} width={"8"} height={"12"} /><rect x={"26"} y={"30"} width={"9"} height={"16"} /><rect x={"40"} y={"24"} width={"8"} height={"22"} /><rect x={"52"} y={"32"} width={"10"} height={"14"} /></g>
                      <path d={"M0 57h70"} stroke={"#82698f"} strokeWidth={"1.5"} opacity={".6"} />
                    </svg>
                  </span>
                  <div className={"sh-season-main"}>
                    <p className={"sh-season-name"}><span className={"sh-rank"}>3</span><b>시즌 4 · 1990 걸프전</b></p>
                    <p className={"sh-season-desc"}>지정학적 리스크와 유가 급등</p>
                  </div>
                  <a className={"sh-pick"} href={"/sim/play"}>추천</a>
                </li>
              </ul>
              <a className={"sh-panel-foot"} href={"/sim/ranking"}>모든 시즌 보기 ›</a>
            </article>

            <article className={"sh-panel"}>
              <div className={"sh-panel-head"}>
                <h2>
                  <svg width={"21"} height={"21"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                    <rect x={"3"} y={"8"} width={"18"} height={"13"} rx={"2"} /><path d={"M2 8h20v4H2zM12 8v13"} />
                    <path d={"M12 8S9.5 3 7.5 4.2 10 8 12 8zM12 8s2.5-5 4.5-3.8S14 8 12 8z"} />
                  </svg>
                  보상
                </h2>
                <a href={"/portfolio"}>자세히 보기 ›</a>
              </div>
              <ul className={"sh-rewards"}>
                <li className={"sh-reward"}>
                  <span className={"sh-hex"}>
                    <svg width={"34"} height={"34"} viewBox={"0 0 36 36"} fill={"none"} aria-hidden={"true"}>
                      <path d={"M18 2.5 31 10v16L18 33.5 5 26V10z"} fill={"#2f7bf6"} />
                      <path d={"M18 11.5l2 4.2 4.6.6-3.4 3.2.9 4.6L18 21.9l-4.1 2.2.9-4.6-3.4-3.2 4.6-.6z"} fill={"#fff"} />
                    </svg>
                  </span>
                  <b>시즌 참가</b>
                  <em className={"ant"}>+50 ANT</em>
                </li>
                <li className={"sh-reward"}>
                  <span className={"sh-hex"}>
                    <svg width={"34"} height={"34"} viewBox={"0 0 36 36"} fill={"none"} aria-hidden={"true"}>
                      <path d={"M18 2.5 31 10v16L18 33.5 5 26V10z"} fill={"#6355e8"} />
                      <path d={"M18 11.5l2 4.2 4.6.6-3.4 3.2.9 4.6L18 21.9l-4.1 2.2.9-4.6-3.4-3.2 4.6-.6z"} fill={"#fff"} />
                    </svg>
                  </span>
                  <b>상위 10% 달성</b>
                  <em className={"ant"}>+100 ANT</em>
                </li>
                <li className={"sh-reward"}>
                  <span className={"sh-hex"}>
                    <svg width={"34"} height={"34"} viewBox={"0 0 36 36"} fill={"none"} aria-hidden={"true"}>
                      <path d={"M18 2.5 31 10v16L18 33.5 5 26V10z"} fill={"#f0951f"} />
                      <path d={"M18 11.5l2 4.2 4.6.6-3.4 3.2.9 4.6L18 21.9l-4.1 2.2.9-4.6-3.4-3.2 4.6-.6z"} fill={"#fff"} />
                    </svg>
                  </span>
                  <b>시즌 배지</b>
                  <em className={"txt"}>특별 배지 지급</em>
                </li>
              </ul>
              <a className={"sh-panel-foot"} href={"/portfolio"}>내 보상 내역 보기 ›</a>
            </article>

          </Carousel>
        </div>
      </main>
    </>
  )
}
