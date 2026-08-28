import Carousel from "../components/Carousel"

export default function SimPractice() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner practice"}>

          <header className={"pr-head"}>
            <div>
              <h1>연습하기 모드</h1>
              <p>부담 없이 전략을 연습하고 투자 흐름을 익혀보세요.</p>
            </div>
            <div className={"pr-levels"} role={"group"} aria-label={"난이도"}>
              <button className={"on"} type={"button"} data-level={"basic"}>
                <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 21v-8"} /><path d={"M12 13C12 9 9 6 5 6c0 4 3 7 7 7z"} /><path d={"M12 13c0-3.3 2.7-6 6-6 0 3.3-2.7 6-6 6z"} /></svg>
                초보자용
              </button>
              <button type={"button"} data-level={"pro"}>
                <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 17l6-6 4 4 8-8"} /><path d={"M15 7h6v6"} /></svg>
                상급자용
              </button>
            </div>
          </header>

          {/* 요약 */}
          <dl className={"pr-card pr-stats"}>
            <div className={"pr-stat"}>
              <span className={"pr-ring"}>
                <svg viewBox={"0 0 42 42"} width={"78"} height={"78"} aria-hidden={"true"}>
                  <circle cx={"21"} cy={"21"} r={"17"} fill={"none"} stroke={"#e6f4ec"} strokeWidth={"5"} />
                  <circle cx={"21"} cy={"21"} r={"17"} fill={"none"} stroke={"#16a06a"} strokeWidth={"5"} strokeLinecap={"round"} strokeDasharray={"69.4 106.8"} />
                </svg>
                <span>65%</span>
              </span>
              <div>
                <dt>연습 진행률</dt>
                <small>이번 주 목표 10회 중<br />6회 완료</small>
              </div>
            </div>

            <div className={"pr-stat"}>
              <i className={"pr-tone-v"}>
                <svg width={"32"} height={"32"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} /><path d={"m9 15 2 2 4-4"} /></svg>
              </i>
              <div><dt>완료한 연습</dt><dd className={"brand"}>18회</dd><small>전체 누적</small></div>
            </div>

            <div className={"pr-stat"}>
              <i className={"pr-tone-o"}>
                <svg width={"32"} height={"32"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3s5 4.5 5 9a5 5 0 0 1-10 0c0-1.8 1-3.2 2-4.2 0 2 1 3.2 2 3.2 1.6 0 1.6-2.4 1-8z"} /><path d={"M9.5 15.5c0 1.5 1.1 2.5 2.5 2.5s2.5-1 2.5-2.5"} /></svg>
              </i>
              <div><dt>연속 학습</dt><dd className={"hot"}>7일</dd><small>최고 12일</small></div>
            </div>
          </dl>

          <div className={"pr-body"}>

            {/* 왼쪽 */}
            <div className={"pr-col"}>

              <section className={"pr-card pr-panel"}>
                <div className={"pr-panel-head"}>
                  <span className={"pr-num"}>1</span>
                  <h2>오늘의 연습 코스</h2>
                  <a className={"pr-more"} href={"#"}>모든 코스 보기 ›</a>
                </div>

                {/* 초보자용 */}
                <Carousel className="pr-courses basic-only">
                  <article className={"pr-course"}>
                    <i className={"pr-tone-v"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 16l5-6 4 4 3-4 6 7"} /></svg></i>
                    <b>차트 기초 연습</b>
                    <p>이동평균선, 거래량 등 기본 지표를 활용해보세요.</p>
                    <a className={"pr-start"} href={"/sim/play?mode=learn&view=basic"}>시작하기 <em>›</em></a>
                  </article>
                  <article className={"pr-course"}>
                    <i style={{ background: '#e6f7ef', color: '#16a06a' }}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M5 20V12M12 20V6M19 20v-5"} /></svg></i>
                    <b>종목 비교 연습</b>
                    <p>여러 종목을 비교 분석하고 투자 포인트를 찾아보세요.</p>
                    <a className={"pr-start"} href={"/sim/play?mode=learn&view=basic"}>시작하기 <em>›</em></a>
                  </article>
                  <article className={"pr-course"}>
                    <i style={{ background: '#e8f1fe', color: '#2f7bf6' }}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} /></svg></i>
                    <b>10일 예측 연습</b>
                    <p>10일 후 주가 흐름을 예측하고 전략을 세워보세요.</p>
                    <a className={"pr-start"} href={"/sim/play?mode=learn&view=basic"}>시작하기 <em>›</em></a>
                  </article>
                </Carousel>

                {/* 상급자용 */}
                <Carousel className="pr-courses pro-only">
                  <article className={"pr-course"}>
                    <i className={"pr-tone-v"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M4 18V9M9 18V5M14 18v-6M19 18V8"} /><path d={"M3 21h18"} /></svg></i>
                    <b>지표 조합 연습</b>
                    <p>MACD·볼린저밴드를 조합해 진입 시점을 잡아보세요.</p>
                    <a className={"pr-start"} href={"/sim/play?mode=learn&view=pro"}>시작하기 <em>›</em></a>
                  </article>
                  <article className={"pr-course"}>
                    <i style={{ background: '#e6f7ef', color: '#16a06a' }}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 12a9 9 0 1 0 2.6-6.4"} /><path d={"M3 4v5h5"} /><path d={"M12 8v4.5l3 1.8"} /></svg></i>
                    <b>시나리오 백테스트</b>
                    <p>같은 구간을 반복 재생하며 전략의 재현성을 확인하세요.</p>
                    <a className={"pr-start"} href={"/sim/play?mode=learn&view=pro"}>시작하기 <em>›</em></a>
                  </article>
                  <article className={"pr-course"}>
                    <i style={{ background: '#e8f1fe', color: '#2f7bf6' }}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} /><path d={"m8 15 3 3 5-5"} /></svg></i>
                    <b>60일 장기 예측</b>
                    <p>실적 사이클을 반영해 장기 방향을 예측해보세요.</p>
                    <a className={"pr-start"} href={"/sim/play?mode=learn&view=pro"}>시작하기 <em>›</em></a>
                  </article>
                </Carousel>
              </section>

              <section className={"pr-card pr-panel"}>
                <div className={"pr-panel-head"}>
                  <span className={"pr-num"}>3</span>
                  <h2>나의 연습 성과</h2>
                </div>

                <dl className={"pr-results"}>
                  <div className={"pr-result"}>
                    <dt><i><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><circle cx={"12"} cy={"12"} r={"9"} /><circle cx={"12"} cy={"12"} r={"5"} /><circle cx={"12"} cy={"12"} r={"1.7"} fill={"currentColor"} /></svg></i>적중률</dt>
                    <dd>62.4%<span>+8.7%p</span></dd>
                  </div>
                  <div className={"pr-result"}>
                    <dt><i><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 17l6-6 4 4 8-8"} /><path d={"M15 7h6v6"} /></svg></i>평균 수익률</dt>
                    <dd className={"brand"}>+4.21%<span>+1.35%p</span></dd>
                  </div>
                  <div className={"pr-result"}>
                    <dt><i><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"m8.5 12.5 2.5 2.5 5-5"} /></svg></i>완료 횟수</dt>
                    <dd>18회</dd>
                    <small>이번 달</small>
                  </div>
                  <div className={"pr-spark"}>
                    <p>최근 성장 추이 <small>(최근 8회)</small></p>
                    <svg id={"pr-spark"} viewBox={"0 0 260 90"} role={"img"} aria-label={"최근 8회 연습 성과 추이"}></svg>
                  </div>
                </dl>
              </section>
            </div>

            {/* 오른쪽 */}
            <div className={"pr-col"}>

              <section className={"pr-card pr-panel"}>
                <div className={"pr-panel-head"}>
                  <span className={"pr-num"}>2</span>
                  <h2>추천 연습 시나리오</h2>
                  <a className={"pr-more"} href={"#"}>더보기 ›</a>
                </div>

                <ul className={"pr-scenarios"}>
                  <li><a className={"pr-scenario"} href={"/sim/play?mode=learn"}>
                    <i className={"pr-tone-v"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"7"} y={"7"} width={"10"} height={"10"} rx={"1.5"} /><path d={"M10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4"} /></svg></i>
                    <div><b>반도체 실적 시즌</b><small>실적 발표를 앞둔 반도체 종목의 흐름을 예측해보세요.</small></div>
                    <em aria-hidden={"true"}>›</em>
                  </a></li>
                  <li><a className={"pr-scenario"} href={"/sim/play?mode=learn"}>
                    <i style={{ background: '#e6f7ef', color: '#16a06a' }}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"}><circle cx={"7.5"} cy={"7.5"} r={"2.5"} /><circle cx={"16.5"} cy={"16.5"} r={"2.5"} /><path d={"M19 5 5 19"} /></svg></i>
                    <div><b>금리 인하 기대감</b><small>금리 인하 기대감이 시장에 미치는 영향을 분석해보세요.</small></div>
                    <em aria-hidden={"true"}>›</em>
                  </a></li>
                  <li><a className={"pr-scenario"} href={"/sim/play?mode=learn"}>
                    <i style={{ background: '#e8f1fe', color: '#2f7bf6' }}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M4 15h16l-1.6-5.2A2 2 0 0 0 16.5 8h-9a2 2 0 0 0-1.9 1.8z"} /><path d={"M4 15v3h3M20 15v3h-3"} /><circle cx={"7.5"} cy={"18"} r={"1.6"} /><circle cx={"16.5"} cy={"18"} r={"1.6"} /></svg></i>
                    <div><b>전기차 섹터 변동성</b><small>전기차 섹터의 변동성 구간에서 전략을 연습해보세요.</small></div>
                    <em aria-hidden={"true"}>›</em>
                  </a></li>
                </ul>
              </section>

              <section className={"pr-ai"}>
                <div className={"pr-panel-head"}>
                  <span className={"pr-num"}>4</span>
                  <h2>AI 가이드</h2>
                </div>
                <div className={"pr-ai-inner"}>
                  <b className={"basic-only"}>AI가 단계별 힌트를 제공해요!</b>
                  <b className={"pro-only"}>AI가 매매 근거를 되짚어 줘요!</b>
                  <p className={"basic-only"}>연습 중 막히는 부분이 있다면<br />AI 힌트를 통해 차근차근 배워보세요.</p>
                  <p className={"pro-only"}>연습이 끝나면 판단 근거와 놓친 신호를<br />AI 복기로 확인해보세요.</p>
                  <a className={"pr-hint"} href={"#"}>✦ <span className={"basic-only"}>AI 힌트 받기</span><span className={"pro-only"}>AI 복기 받기</span></a>
                </div>
                <figure className={"pr-ai-art"}><img src={"/assets/antena_character.png"} alt={""} aria-hidden={"true"} /></figure>
              </section>

              <div className={"pr-cta"}>
                <a href={"/sim/play?mode=learn&view=basic"} id={"pr-go"}>연습 시작하기 <em>›</em></a>
              </div>
            </div>
          </div>

        </div>
      </main>
    </>
  )
}
