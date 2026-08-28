export default function SimSetup() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner sim-setup"}>

          <figure className={"ss-mascot"}>
            <img src={"/assets/antena_character.png"} alt={""} aria-hidden={"true"} />
          </figure>
          <svg className={"ss-spark a"} width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"currentColor"} aria-hidden={"true"}><path d={"m12 2 2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z"} /></svg>
          <svg className={"ss-spark b"} width={"16"} height={"16"} viewBox={"0 0 24 24"} fill={"currentColor"} aria-hidden={"true"}><path d={"m12 2 2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z"} /></svg>
          <svg className={"ss-spark c"} width={"20"} height={"20"} viewBox={"0 0 24 24"} fill={"currentColor"} aria-hidden={"true"}><path d={"m12 2 2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z"} /></svg>

          <nav className={"ss-crumb"} aria-label={"위치"}>
            <a href={"/sim"}>모의투자 홈</a><i>›</i><span>모드 선택</span>
          </nav>

          <header className={"ss-head"}>
            <h1>어떤 모드로 시작할까요?</h1>
            <p>목적에 맞는 모드를 선택해 실전 감각을 익혀보세요.</p>
          </header>

          {/* 모드 선택 가이드 */}
          <section className={"ss-guide"}>
            <div className={"ss-guide-copy"}>
              <h2>
                <svg width={"21"} height={"21"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                  <path d={"M12 6.5C10.5 5 8.5 4.5 3 4.5v13C8.5 17.5 10.5 18 12 19.5"} />
                  <path d={"M12 6.5C13.5 5 15.5 4.5 21 4.5v13c-5.5 0-7.5.5-9 2"} />
                </svg>
                모드 선택 가이드
              </h2>
              <p>상황에 맞는 모드를 선택하면<br />더 효과적으로 모의투자를 경험할 수 있어요.</p>
            </div>

            <span className={"ss-guide-rule"} aria-hidden={"true"}></span>

            <div className={"ss-steps"}>
              <div className={"ss-step"}>
                <i style={{ background: '#e7f6ef', color: '#16a06a' }}>
                  <svg width={"30"} height={"30"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                    <path d={"M12 21v-8"} /><path d={"M12 13C12 9 9 6 5 6c0 4 3 7 7 7z"} /><path d={"M12 13c0-3.3 2.7-6 6-6 0 3.3-2.7 6-6 6z"} />
                  </svg>
                </i>
                <div><small>처음 써보면</small><b style={{ color: '#16a06a' }}>연습하기</b></div>
              </div>

              <span className={"ss-step-arrow"} aria-hidden={"true"}>⋯›</span>

              <div className={"ss-step"}>
                <i style={{ background: '#ebe9fc', color: '#5457e8' }}>
                  <svg width={"30"} height={"30"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                    <path d={"M7 4h10v5a5 5 0 0 1-10 0z"} /><path d={"M7 6H4v1.5A3.5 3.5 0 0 0 7.5 11M17 6h3v1.5a3.5 3.5 0 0 1-3.5 3.5"} /><path d={"M10 19h4M12 14v5M8.5 21h7"} />
                  </svg>
                </i>
                <div><small>경쟁하고 싶다면</small><b style={{ color: '#5457e8' }}>대회</b></div>
              </div>

              <span className={"ss-step-arrow"} aria-hidden={"true"}>⋯›</span>

              <div className={"ss-step"}>
                <i style={{ background: '#e8f1fe', color: '#2f7bf6' }}>
                  <svg width={"30"} height={"30"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                    <path d={"M13 2 4 14h7l-1 8 9-12h-7z"} />
                  </svg>
                </i>
                <div><small>빠르게</small><b style={{ color: '#2f7bf6' }}>보여주려면</b></div>
              </div>
            </div>
          </section>

          {/* 모드 3종 */}
          <section className={"ss-modes"}>

            <article className={"ss-mode learn"}>
              <span className={"ss-mode-icon"}>
                <svg width={"44"} height={"44"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                  <path d={"M22 9 12 4 2 9l10 5z"} /><path d={"M6 11.5V16c0 1.7 2.7 3 6 3s6-1.3 6-3v-4.5"} /><path d={"M22 9v5"} />
                </svg>
              </span>
              <h3>연습하기</h3>
              <p>부담 없이 전략을 연습하고<br />AI 힌트를 받아보세요.</p>
              <div className={"ss-chips"}><span>초보자 추천</span><span>AI 힌트</span><span>자유 연습</span></div>
              <div className={"ss-facts"}>
                <div className={"ss-fact"}>
                  <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"}><path d={"M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"} /><circle cx={"16"} cy={"6"} r={"2"} /><circle cx={"10"} cy={"12"} r={"2"} /><circle cx={"18"} cy={"18"} r={"2"} /></svg>
                  <span>난이도<br />선택 가능</span>
                </div>
                <div className={"ss-fact"}>
                  <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3 5 5.6v5.9c0 4.2 3 7.4 7 8.5 4-1.1 7-4.3 7-8.5V5.6z"} /><path d={"m9.5 9.5 5 5M14.5 9.5l-5 5"} /></svg>
                  <span>랭킹<br />반영 없음</span>
                </div>
                <div className={"ss-fact"}>
                  <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M5 21V4"} /><path d={"M5 4h11l-2 3.5L16 11H5z"} /></svg>
                  <span>자유<br />종료</span>
                </div>
              </div>
              <a className={"ss-cta"} href={"/sim/practice"}><b>연습하기 시작</b><span>›</span></a>
            </article>

            <article className={"ss-mode contest pick"}>
              <span className={"ss-badge"}>
                <svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"currentColor"} aria-hidden={"true"}><path d={"m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.2-5.4-2.9-5.4 2.9 1-6.2L3.2 9.5l6.1-.9z"} /></svg>
                추천
              </span>
              <span className={"ss-mode-icon"}>
                <svg width={"44"} height={"44"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                  <path d={"M7 4h10v5a5 5 0 0 1-10 0z"} /><path d={"M7 6H4v1.5A3.5 3.5 0 0 0 7.5 11M17 6h3v1.5a3.5 3.5 0 0 1-3.5 3.5"} /><path d={"M10 19h4M12 14v5M8.5 21h7"} />
                </svg>
              </span>
              <h3>대회</h3>
              <p>동일한 조건에서 다른 참가자와<br />실력을 겨뤄보세요.</p>
              <div className={"ss-chips"}><span>시즌 진행</span><span>랭킹</span><span>보상</span></div>
              <div className={"ss-facts"}>
                <div className={"ss-fact"}>
                  <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"9"} cy={"8"} r={"3"} /><path d={"M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20"} /><circle cx={"17.5"} cy={"9.5"} r={"2.5"} /><path d={"M21 20v-1a3.5 3.5 0 0 0-3.5-3.5"} /></svg>
                  <span>공통<br />시나리오</span>
                </div>
                <div className={"ss-fact"}>
                  <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"M12 7v5.5l3.5 2"} /></svg>
                  <span>기간<br />제한</span>
                </div>
                <div className={"ss-fact"}>
                  <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M4 20V12M10 20V5M16 20v-6M21 20H3"} /></svg>
                  <span>성과<br />집계</span>
                </div>
              </div>
              <a className={"ss-cta"} href={"/sim/contest"}><b>대회 참여하기</b><span>›</span></a>
            </article>

            <article className={"ss-mode demo"}>
              <span className={"ss-mode-icon"}>
                <svg width={"44"} height={"44"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                  <rect x={"2.5"} y={"4"} width={"19"} height={"13"} rx={"2"} /><path d={"M8 21h8M12 17v4"} /><path d={"m6.5 13 3-3.5 2.5 2 4.5-4.5"} />
                </svg>
              </span>
              <h3>시연</h3>
              <p>발표와 체험을 위해 빠르게<br />핵심 흐름을 살펴보세요.</p>
              <div className={"ss-chips"}><span>빠른 체험</span><span>발표용</span><span>가이드 포함</span></div>
              <div className={"ss-facts"}>
                <div className={"ss-fact"}>
                  <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"13"} r={"8"} /><path d={"M12 9v4l2.5 1.5M9 2h6M19 6l1.5-1.5"} /></svg>
                  <span>짧은<br />진행</span>
                </div>
                <div className={"ss-fact"}>
                  <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><circle cx={"12"} cy={"12"} r={"5"} /><circle cx={"12"} cy={"12"} r={"1.6"} fill={"currentColor"} /></svg>
                  <span>핵심 기능<br />중심</span>
                </div>
                <div className={"ss-fact"}>
                  <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M13 2 4 14h7l-1 8 9-12h-7z"} /></svg>
                  <span>즉시<br />시작</span>
                </div>
              </div>
              <a className={"ss-cta"} href={"/sim/play?mode=demo&view=basic"}><b>시연 시작하기</b><span>›</span></a>
            </article>

          </section>

          <p className={"ss-note"}>
            <svg width={"16"} height={"16"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"M12 11v5"} /><path d={"M12 7.8v.4"} /></svg>
            언제든지 다른 모드로 변경하여 새로운 경험을 이어갈 수 있어요.
          </p>

        </div>
      </main>
    </>
  )
}
