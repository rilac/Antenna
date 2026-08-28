export default function SimRanking() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner sim-rank"}>

          <header className={"rk-head"}>
            <div>
              <h1>모의 투자 랭크</h1>
              <p>전 세계 투자자들과 실력을 겨뤄보세요! <i className={"rk-info"} title={"시즌 종료 시점의 수익률로 순위가 확정됩니다"}>i</i></p>
            </div>
            <div className={"rk-head-right"}>
              <label className={"rk-season"}>
                <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} /></svg>
                <span className={"sr-only"}>시즌 선택</span>
                <select id={"rk-season"}>
                  <option>시즌 7 · 2008 금융위기</option>
                  <option>시즌 6 · 2003 IT 버블</option>
                  <option>시즌 5 · 2020 코로나19</option>
                  <option>시즌 4 · 2015 중국 증시 변동</option>
                </select>
              </label>
              <p className={"rk-left"}>
                <svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"M12 7v5.5l3.5 2"} /></svg>
                시즌 종료까지 120일 남음
              </p>
            </div>
          </header>

          {/* 요약 */}
          <dl className={"rk-card rk-strip"}>
            <div className={"rk-cell"}>
              <div className={"rk-my"}>
                <dt>내 현재 순위</dt>
                <dd>상위 <span>18%</span></dd>
                <small><b>12,534</b> / 68,742명</small>
              </div>
              <div className={"rk-tier"}>
                <svg width={"60"} height={"60"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}>
                  <path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#5f57e0"} />
                  <path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#8f88f2"} />
                  <path d={"M36 22 48 29v14l-12 7-12-7V29z"} fill={"#d7d4fb"} />
                  <path d={"M36 22v28M24 29l24 14M48 29 24 43"} stroke={"#fff"} strokeWidth={"1.6"} opacity={".8"} />
                </svg>
                <b>플래티넘 III</b>
              </div>
            </div>
            <div className={"rk-cell"}><dt>시즌 수익률</dt><dd className={"up"}>+25.30%</dd><small>수익금 <b>+12,530,000원</b></small></div>
            <div className={"rk-cell"}><dt>시즌 백분위</dt><dd>81.7%</dd><small>상위 <b>18.3%</b></small></div>
            <div className={"rk-cell"}><dt>랭크 변동</dt><dd className={"up"}>↑ 2</dd><small>지난 주 대비</small></div>
            <div className={"rk-cell"}><dt>총 모의 투자 횟수</dt><dd>24<span style={{ fontSize: '19px' }}>회</span></dd><small>평균 수익률 <b>+18.6%</b></small></div>
          </dl>

          <div className={"rk-body"}>

            {/* 왼쪽 */}
            <div className={"rk-col"}>

              <section className={"rk-card rk-panel"}>
                <div className={"rk-panel-head"}>
                  <h2>시즌 랭킹 TOP 10</h2>
                  <i className={"rk-info"} title={"매일 자정 기준으로 갱신됩니다"}>i</i>
                </div>

                <div className={"rk-table-wrap"}>
                  <table className={"rk-table"}>
                    <thead>
                      <tr><th>순위</th><th>투자자</th><th>수익률</th><th>수익금</th><th>모의 투자 횟수</th><th>랭크</th></tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td><span className={"rk-medal rk-m1"}>1</span></td>
                        <td><span className={"rk-user"}><i className={"rk-avatar av-c"}>투</i><b>투자의 신</b></span></td>
                        <td className={"num rise"}>+78.92%</td><td className={"num rise"}>+39,460,000원</td><td className={"num"}>28회</td>
                        <td><span className={"rk-badge tier-dia"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>다이아몬드 I</span></td>
                      </tr>
                      <tr>
                        <td><span className={"rk-medal rk-m2"}>2</span></td>
                        <td><span className={"rk-user"}><i className={"rk-avatar av-f"}>M</i><b>MarketWizard</b></span></td>
                        <td className={"num rise"}>+62.15%</td><td className={"num rise"}>+30,890,000원</td><td className={"num"}>26회</td>
                        <td><span className={"rk-badge tier-dia"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>다이아몬드 II</span></td>
                      </tr>
                      <tr>
                        <td><span className={"rk-medal rk-m3"}>3</span></td>
                        <td><span className={"rk-user"}><i className={"rk-avatar av-b"}>슈</i><b>슈퍼개미</b></span></td>
                        <td className={"num rise"}>+51.32%</td><td className={"num rise"}>+25,300,000원</td><td className={"num"}>25회</td>
                        <td><span className={"rk-badge tier-dia"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>다이아몬드 III</span></td>
                      </tr>
                      <tr>
                        <td className={"num"}>4</td>
                        <td><span className={"rk-user"}><i className={"rk-avatar av-a"}>가</i><b>가치투자자</b></span></td>
                        <td className={"num rise"}>+47.88%</td><td className={"num rise"}>+22,480,000원</td><td className={"num"}>24회</td>
                        <td><span className={"rk-badge tier-plat"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>플래티넘 I</span></td>
                      </tr>
                      <tr>
                        <td className={"num"}>5</td>
                        <td><span className={"rk-user"}><i className={"rk-avatar av-e"}>L</i><b>LongTermStar</b></span></td>
                        <td className={"num rise"}>+44.21%</td><td className={"num rise"}>+20,710,000원</td><td className={"num"}>23회</td>
                        <td><span className={"rk-badge tier-plat"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>플래티넘 II</span></td>
                      </tr>
                      <tr>
                        <td className={"num"}>6</td>
                        <td><span className={"rk-user"}><i className={"rk-avatar av-g"}>성</i><b>성공투자왕</b></span></td>
                        <td className={"num rise"}>+40.15%</td><td className={"num rise"}>+18,820,000원</td><td className={"num"}>22회</td>
                        <td><span className={"rk-badge tier-plat"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>플래티넘 II</span></td>
                      </tr>
                      <tr>
                        <td className={"num"}>7</td>
                        <td><span className={"rk-user"}><i className={"rk-avatar av-d"}>B</i><b>BullMarket</b></span></td>
                        <td className={"num rise"}>+36.78%</td><td className={"num rise"}>+16,320,000원</td><td className={"num"}>22회</td>
                        <td><span className={"rk-badge tier-plat"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>플래티넘 III</span></td>
                      </tr>

                      <tr className={"rk-divider"}><td colSpan={6}><hr /></td></tr>

                      <tr className={"rk-me"}>
                        <td className={"num"}>12,534</td>
                        <td><span className={"rk-user"}><i className={"rk-avatar av-f"}>안</i><b>안테나님 (나)</b></span></td>
                        <td className={"num rise"}>+25.30%</td><td className={"num rise"}>+12,530,000원</td><td className={"num"}>24회</td>
                        <td><span className={"rk-badge tier-plat"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>플래티넘 III</span></td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <a className={"rk-all"} href={"#"}>전체 랭킹 보기 ›</a>
              </section>

              <section className={"rk-card rk-panel"}>
                <div className={"rk-panel-head"}>
                  <h2>설정 요약</h2>
                  <i className={"rk-info"} title={"현재 세션에 적용 중인 설정"}>i</i>
                </div>
                <div className={"rk-settings"}>
                  <div className={"rk-setting"}>
                    <svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"2"} y={"7"} width={"20"} height={"11"} rx={"4"} /><path d={"M7 11v3M5.5 12.5h3M16 12h.01M18.5 14h.01"} /></svg>
                    <div><b>게임 모드</b><small>연습하기</small></div>
                  </div>
                  <div className={"rk-setting"}>
                    <svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"9"} /><path d={"M8.5 15c2-1 3-3.5 3-6.5"} /></svg>
                    <div><b>AI 힌트</b><small className={"on"}>ON</small></div>
                  </div>
                  <div className={"rk-setting"}>
                    <svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M4 18V14M9 18v-7M14 18V8M19 18V5"} /></svg>
                    <div><b>난이도</b><small>보통</small></div>
                  </div>
                  <div className={"rk-setting"}>
                    <svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M18 8a6 6 0 1 0-12 0c0 6-3 7-3 7h18s-3-1-3-7"} /><path d={"M13.7 21a2 2 0 0 1-3.4 0"} /></svg>
                    <div><b>알림 설정</b><small className={"on"}>ON</small></div>
                  </div>
                  <a className={"rk-manage"} href={"/sim/setup"}>
                    <svg width={"20"} height={"20"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"3"} /><path d={"M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3.4a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.7 6.3l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V2a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.2a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1"} /></svg>
                    설정 관리
                  </a>
                </div>
              </section>
            </div>

            {/* 오른쪽 */}
            <div className={"rk-col"}>

              <section className={"rk-card rk-panel"}>
                <div className={"rk-panel-head"}>
                  <h2>랭크 보상 안내</h2>
                  <i className={"rk-info"} title={"시즌 최종 순위 기준"}>i</i>
                </div>
                <ul className={"rk-rewards"}>
                  <li className={"rk-reward"}>
                    <svg width={"42"} height={"42"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}><path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#8b3fe0"} /><path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#b06bf5"} /><path d={"M22 30l6 12h16l6-12-8 6-6-9-6 9z"} fill={"#fff"} /><path d={"M26 46h20"} stroke={"#fff"} strokeWidth={"3.6"} strokeLinecap={"round"} /></svg>
                    <b>상위 1%</b><span>500 ANT + 전용 프로필 배지</span>
                  </li>
                  <li className={"rk-reward"}>
                    <svg width={"42"} height={"42"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}><path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#e0921c"} /><path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#f6b843"} /><path d={"m36 22 4.2 8.6 9.4 1.4-6.8 6.6 1.6 9.4L36 43.6 27.6 48l1.6-9.4-6.8-6.6 9.4-1.4z"} fill={"#fff"} /></svg>
                    <b>상위 5%</b><span>300 ANT + 모의 투자 마스터 배지</span>
                  </li>
                  <li className={"rk-reward"}>
                    <svg width={"42"} height={"42"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}><path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#1f5fd8"} /><path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#4a8df5"} /><circle cx={"36"} cy={"36"} r={"11"} fill={"none"} stroke={"#fff"} strokeWidth={"4.4"} /><path d={"M36 29v14"} stroke={"#fff"} strokeWidth={"4.4"} strokeLinecap={"round"} /></svg>
                    <b>상위 10%</b><span>200 ANT + 프리미엄 분석 리포트</span>
                  </li>
                  <li className={"rk-reward"}>
                    <svg width={"42"} height={"42"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}><path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#177f57"} /><path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#2eb37c"} /><path d={"M24 46V30h6v16zM33 46V24h6v22zM42 46V34h6v12z"} fill={"#fff"} /></svg>
                    <b>상위 25%</b><span>100 ANT + 고급 리서처 라벨</span>
                  </li>
                  <li className={"rk-reward"}>
                    <svg width={"42"} height={"42"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}><path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#9aa2b8"} /><path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#c3c9d6"} /><circle cx={"36"} cy={"36"} r={"10"} fill={"none"} stroke={"#fff"} strokeWidth={"4"} /><path d={"M36 31v10"} stroke={"#fff"} strokeWidth={"4"} strokeLinecap={"round"} /></svg>
                    <b>상위 50%</b><span>50 ANT + 기본 리워드 박스</span>
                  </li>
                </ul>
                <p className={"rk-reward-foot"}>
                  모의 투자 시즌 종료 후 일괄 지급됩니다.
                  <a href={"#"}>보상 전체 보기 ›</a>
                </p>
              </section>

              <section className={"rk-card rk-panel"}>
                <div className={"rk-panel-head"}><h2>친구 랭크 비교</h2></div>
                <ul className={"rk-friends"}>
                  <li className={"rk-friend"}>
                    <span className={"rk-friend-name"}><i className={"rk-avatar av-a"}>김</i><b>김투자</b><i className={"rk-online"} title={"접속 중"}></i></span>
                    <em className={"rise"}>+32.81%</em>
                    <span className={"pct"}>상위 12%</span>
                    <span className={"rk-badge tier-plat"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>플래티넘 II</span>
                  </li>
                  <li className={"rk-friend"}>
                    <span className={"rk-friend-name"}><i className={"rk-avatar av-e"}>배</i><b>배당왕</b></span>
                    <em className={"rise"}>+21.45%</em>
                    <span className={"pct"}>상위 24%</span>
                    <span className={"rk-badge tier-plat"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>플래티넘 III</span>
                  </li>
                  <li className={"rk-friend"}>
                    <span className={"rk-friend-name"}><i className={"rk-avatar av-b"}>성</i><b>성장투자자</b></span>
                    <em className={"rise"}>+8.92%</em>
                    <span className={"pct"}>상위 56%</span>
                    <span className={"rk-badge tier-gold"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"currentColor"}><path d={"M12 2 22 8v8l-10 6-10-6V8z"} opacity={".9"} /></svg>골드 II</span>
                  </li>
                </ul>
                <a className={"rk-friend-btn"} href={"#"}>
                  <svg width={"20"} height={"20"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"9"} cy={"8"} r={"3.2"} /><path d={"M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20"} /><path d={"M18 8h4M20 6v4"} /></svg>
                  친구 관리 및 더 보기
                </a>
              </section>
            </div>
          </div>

        </div>
      </main>
    </>
  )
}
