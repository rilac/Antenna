import Carousel from "../components/Carousel"

export default function SimContest() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner contest"}>

          <figure className={"ct-mascot"}><img src={"/assets/antena_character.png"} alt={""} aria-hidden={"true"} /></figure>
          <svg className={"ct-spark a"} width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"currentColor"} aria-hidden={"true"}><path d={"m12 2 2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z"} /></svg>
          <svg className={"ct-spark b"} width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"} aria-hidden={"true"}><path d={"m12 2 2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z"} /></svg>
          <svg className={"ct-spark c"} width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"currentColor"} aria-hidden={"true"}><path d={"m12 2 2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z"} /></svg>

          <nav className={"ct-crumb"} aria-label={"위치"}>
            <a href={"/sim"}>모의투자 홈</a><i>›</i><span>대회</span>
          </nav>

          <header className={"ct-head"}>
            <h1>
              <svg width={"38"} height={"38"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                <path d={"M7 4h10v5a5 5 0 0 1-10 0z"} /><path d={"M7 6H4v1.5A3.5 3.5 0 0 0 7.5 11M17 6h3v1.5a3.5 3.5 0 0 1-3.5 3.5"} /><path d={"M10 19h4M12 14v5M8.5 21h7"} />
              </svg>
              대회 모드
            </h1>
            <p>실시간 랭킹과 보상을 걸고 경쟁해보세요.</p>
          </header>

          {/* 시즌 */}
          <section className={"ct-season"}>
            <svg className={"ct-shield"} viewBox={"0 0 122 122"} fill={"none"} aria-hidden={"true"}>
              <defs>
                <linearGradient id={"ctSh"} x1={".2"} y1={"0"} x2={".8"} y2={"1"}><stop offset={"0"} stopColor={"#8f88f2"} /><stop offset={"1"} stopColor={"#4f46e5"} /></linearGradient>
              </defs>
              <path d={"M22 34c-8 6-12 16-10 26M100 34c8 6 12 16 10 26"} stroke={"#cfcbf7"} strokeWidth={"7"} strokeLinecap={"round"} />
              <path d={"M30 44c-4 8-3 18 4 25M92 44c4 8 3 18-4 25"} stroke={"#ddd9fa"} strokeWidth={"6"} strokeLinecap={"round"} />
              <path d={"M61 12l36 14v34c0 22-15 38-36 46-21-8-36-24-36-46V26z"} fill={"url(#ctSh)"} />
              <path d={"M61 20l29 11v29c0 18-12 31-29 38-17-7-29-20-29-38V31z"} fill={"none"} stroke={"#b9b3fa"} strokeWidth={"2"} />
              <text x={"61"} y={"60"} textAnchor={"middle"} fill={"#fff"} fontFamily={"monospace"} fontSize={"30"} fontWeight={"800"}>S2</text>
              <path d={"m61 68 3.4 7 7.6 1.1-5.5 5.3 1.3 7.6L61 85.4l-6.8 3.6 1.3-7.6-5.5-5.3 7.6-1.1z"} fill={"#f7cf5c"} />
            </svg>

            <div className={"ct-season-main"}>
              <h2>시즌 2 · 실전 수익률 대회</h2>
              <p>실전과 동일한 시장에서 수익률을 겨루는 대회입니다.</p>
              <dl className={"ct-season-stats"}>
                <div>
                  <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} /></svg>
                  <div><dt>남은 기간</dt><dd>12일 18시간</dd></div>
                </div>
                <div>
                  <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"9"} cy={"8"} r={"3.2"} /><path d={"M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20"} /><path d={"M17.5 20v-1a3.5 3.5 0 0 0-3-3.4"} /></svg>
                  <div><dt>참가자 수</dt><dd>4,328명</dd></div>
                </div>
                <div>
                  <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"8"} width={"18"} height={"13"} rx={"2"} /><path d={"M2 8h20v4H2zM12 8v13"} /><path d={"M12 8S9.5 3 7.5 4.2 10 8 12 8zM12 8s2.5-5 4.5-3.8S14 8 12 8z"} /></svg>
                  <div><dt>누적 상금/보상</dt><dd>12,500,000 ANT</dd></div>
                </div>
              </dl>
            </div>

            <div className={"ct-season-cta"}>
              <a className={"ct-join"} href={"/sim/play?mode=contest&view=pro"}>대회 참가하기 <em>›</em></a>
              <a className={"ct-detail"} href={"#"}>대회 상세 정보 <i className={"ct-info"} title={"참가 조건과 집계 방식 안내"}>i</i></a>
            </div>
          </section>

          {/* 3분할 */}
          <div className={"ct-grid"}>

            {/* 리더보드 */}
            <section className={"ct-card ct-panel"}>
              <div className={"ct-panel-head"}>
                <h2>현재 리더보드</h2>
                <span className={"ct-live"}><i></i> 실시간 업데이트</span>
                <a className={"ct-more"} href={"/sim/ranking"}>전체 순위 보기 ›</a>
              </div>

              <div className={"ct-board-wrap"}>
              <table className={"ct-board"}>
                <thead>
                  <tr><th>순위</th><th>닉네임</th><th>수익률</th><th>경기 수</th><th>연승/레벨</th></tr>
                </thead>
                <tbody>
                  <tr>
                    <td><span className={"ct-medal ct-m1"}>1</span></td>
                    <td><span className={"ct-user"}><i className={"ct-avatar cv-1"}>수</i><b>수익의정석</b></span></td>
                    <td className={"num rise"}>+24.38%</td><td className={"num"}>18</td>
                    <td><span className={"ct-streak"}>🔥 12연승</span><span className={"ct-lv"}>Lv.12</span></td>
                  </tr>
                  <tr>
                    <td><span className={"ct-medal ct-m2"}>2</span></td>
                    <td><span className={"ct-user"}><i className={"ct-avatar cv-3"}>테</i><b>테슬라가즈아</b></span></td>
                    <td className={"num rise"}>+19.72%</td><td className={"num"}>16</td>
                    <td><span className={"ct-streak"}>🔥 8연승</span><span className={"ct-lv"}>Lv.10</span></td>
                  </tr>
                  <tr>
                    <td><span className={"ct-medal ct-m3"}>3</span></td>
                    <td><span className={"ct-user"}><i className={"ct-avatar cv-4"}>가</i><b>가치투자마스터</b></span></td>
                    <td className={"num rise"}>+17.05%</td><td className={"num"}>14</td>
                    <td><span className={"ct-streak"}>🔥 6연승</span><span className={"ct-lv"}>Lv.9</span></td>
                  </tr>
                  <tr>
                    <td className={"num"}>4</td>
                    <td><span className={"ct-user"}><i className={"ct-avatar cv-1"}>성</i><b>성장주헌터</b></span></td>
                    <td className={"num rise"}>+14.31%</td><td className={"num"}>15</td>
                    <td><span className={"ct-streak"}>🔥 5연승</span><span className={"ct-lv"}>Lv.8</span></td>
                  </tr>
                  <tr className={"me"}>
                    <td className={"num"}>5</td>
                    <td><span className={"ct-user"}><i className={"ct-avatar cv-5"}>안</i><b>안테나007</b><span className={"ct-me-tag"}>나</span></span></td>
                    <td className={"num rise"}>+12.18%</td><td className={"num"}>13</td>
                    <td><span className={"ct-streak"}>🔥 3연승</span><span className={"ct-lv"}>Lv.6</span></td>
                  </tr>
                </tbody>
              </table>
              </div>
            </section>

            {/* 내 대회 현황 */}
            <section className={"ct-card ct-panel"}>
              <div className={"ct-panel-head"}>
                <h2>내 대회 현황</h2>
                <i className={"ct-info"} title={"대회 기간 내 집계"}>i</i>
                <a className={"ct-more"} href={"/portfolio"}>성과 분석 ›</a>
              </div>

              <dl className={"ct-mine"}>
                <div><dt>내 순위</dt><dd className={"up"}>28위</dd><small>상위 0.64%</small></div>
                <div><dt>최고 수익률</dt><dd className={"up"}>+18.26%</dd><small>(시즌 최고)</small></div>
                <div><dt>최근 7일 수익률</dt><dd className={"up"}>+5.42%</dd><small>(상위 12%)</small></div>
              </dl>

              <p className={"ct-chart-title"}>최근 7일 수익률 추이</p>
              <svg className={"ct-chart"} id={"ct-chart"} viewBox={"0 0 540 230"} role={"img"} aria-label={"최근 7일 수익률 추이"}></svg>
            </section>

            {/* 규칙 & 보상 */}
            <section className={"ct-card ct-panel"}>
              <div className={"ct-panel-head"}><h2>대회 규칙 &amp; 보상</h2></div>

              <div className={"ct-tabs"} role={"group"} aria-label={"규칙 · 보상"}>
                <button className={"on"} type={"button"} data-tab={"rules"}>규칙</button>
                <button type={"button"} data-tab={"prizes"}>보상</button>
              </div>

              <ul className={"ct-rules"} id={"ct-rules"}>
                <li><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"m8.5 12.5 2.5 2.5 5-5"} /></svg>대회 기간 동안 수익률을 기준으로 순위를 경쟁합니다.</li>
                <li><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"m8.5 12.5 2.5 2.5 5-5"} /></svg>동일 수익률 시, 경기 수가 많은 참가자가 우선입니다.</li>
                <li><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"m8.5 12.5 2.5 2.5 5-5"} /></svg>부정 거래 적발 시, 참가 자격이 박탈될 수 있습니다.</li>
                <li><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"m8.5 12.5 2.5 2.5 5-5"} /></svg>대회 참가 후 중도 탈퇴는 불가합니다.</li>
              </ul>

              <ul className={"ct-rules"} id={"ct-prize-detail"} hidden>
                <li><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"m8.5 12.5 2.5 2.5 5-5"} /></svg>상금은 시즌 종료 후 3영업일 내 ANT로 일괄 지급됩니다.</li>
                <li><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"m8.5 12.5 2.5 2.5 5-5"} /></svg>상위 10% 이상은 전용 프로필 배지를 함께 받습니다.</li>
                <li><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"m8.5 12.5 2.5 2.5 5-5"} /></svg>참가 보상은 최소 5경기 이상 진행 시 지급됩니다.</li>
                <li><svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"m8.5 12.5 2.5 2.5 5-5"} /></svg>자격 박탈 시 해당 시즌 보상은 지급되지 않습니다.</li>
              </ul>

              <div className={"ct-prizes"}>
                <div className={"ct-prize"}>
                  <svg width={"34"} height={"34"} viewBox={"0 0 24 24"} fill={"none"} stroke={"#e0a41c"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M4 8h16l-1.5 9.5A2 2 0 0 1 16.5 19h-9a2 2 0 0 1-2-1.5z"} /><path d={"M8 8V6a4 4 0 0 1 8 0v2"} /><path d={"M12 12v3M10.5 13.5h3"} /></svg>
                  <small>1위</small><b>2,000,000 ANT</b>
                </div>
                <div className={"ct-prize"}>
                  <svg width={"34"} height={"34"} viewBox={"0 0 24 24"} fill={"none"} stroke={"#6355e8"} strokeWidth={"1.7"}><circle cx={"12"} cy={"12"} r={"9"} /><circle cx={"12"} cy={"12"} r={"5"} /><circle cx={"12"} cy={"12"} r={"1.7"} fill={"#6355e8"} /></svg>
                  <small>상위<br />10%</small><b>500,000 ANT</b>
                </div>
                <div className={"ct-prize"}>
                  <svg width={"34"} height={"34"} viewBox={"0 0 24 24"} fill={"none"} stroke={"#16a06a"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"8"} width={"18"} height={"13"} rx={"2"} /><path d={"M2 8h20v4H2zM12 8v13"} /><path d={"M12 8S9.5 3 7.5 4.2 10 8 12 8zM12 8s2.5-5 4.5-3.8S14 8 12 8z"} /></svg>
                  <small>참가<br />보상</small><b>5,000 ANT</b>
                </div>
              </div>
            </section>
          </div>

          {/* 대회 목록 */}
          <section>
            <div className={"ct-list-head"}>
              <h2>진행 중 &amp; 다가오는 대회</h2>
              <a className={"ct-more"} href={"#"}>모든 대회 보기 ›</a>
            </div>

            <Carousel className="ct-events">
              <a className={"ct-event now"} href={"/sim/play?mode=contest&view=pro"}>
                <div className={"ct-event-main"}>
                  <p className={"ct-event-title"}><span className={"ct-badge now"}>진행 중</span><b>시즌 2 · 실전 수익률 대회</b></p>
                  <p>05.01 ~ 05.25 (12일 남음)</p>
                  <small>
                    <svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"9"} cy={"8"} r={"3.2"} /><path d={"M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20"} /></svg>
                    4,328명 참여 중
                  </small>
                </div>
                <span className={"ct-event-go"} aria-hidden={"true"}>›</span>
              </a>

              <a className={"ct-event"} href={"#"}>
                <div className={"ct-event-main"}>
                  <p className={"ct-event-title"}><span className={"ct-badge soon"}>예정</span><b>섹터 챔피언십</b></p>
                  <p>05.20 ~ 06.03</p>
                  <small>
                    <svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"9"} cy={"8"} r={"3.2"} /><path d={"M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20"} /></svg>
                    1,982명 사전 신청
                  </small>
                </div>
                <span className={"ct-event-go"} aria-hidden={"true"}>›</span>
              </a>

              <a className={"ct-event"} href={"#"}>
                <div className={"ct-event-main"}>
                  <p className={"ct-event-title"}><span className={"ct-badge soon"}>예정</span><b>초단기 매매 챌린지</b></p>
                  <p>05.27 ~ 06.09</p>
                  <small>
                    <svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"9"} cy={"8"} r={"3.2"} /><path d={"M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20"} /></svg>
                    1,256명 사전 신청
                  </small>
                </div>
                <span className={"ct-event-go"} aria-hidden={"true"}>›</span>
              </a>

              <a className={"ct-event"} href={"#"}>
                <div className={"ct-event-main"}>
                  <p className={"ct-event-title"}><span className={"ct-badge soon"}>예정</span><b>글로벌 시장 대전</b></p>
                  <p>06.05 ~ 06.18</p>
                  <small>
                    <svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"9"} cy={"8"} r={"3.2"} /><path d={"M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20"} /></svg>
                    867명 사전 신청
                  </small>
                </div>
                <span className={"ct-event-go"} aria-hidden={"true"}>›</span>
              </a>
            </Carousel>
          </section>

        </div>
      </main>
    </>
  )
}
