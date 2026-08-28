export default function SimPortfolio() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner sim-record"}>

          <header className={"sr-head"}>
            <div>
              <h1>게임 기록</h1>
              <p>지금까지의 모의 투자 여정을 확인하고, 더 나은 투자자로 성장하세요.</p>
            </div>
            <a className={"sr-guide"} href={"#"}>
              <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"4"} y={"10"} width={"16"} height={"11"} rx={"2.5"} /><path d={"M8 10V7a4 4 0 0 1 8 0v3"} /></svg>
              게임 기록 가이드
              <i className={"sr-info"} title={"점수·등급 산정 기준 안내"}>i</i>
            </a>
          </header>

          <div className={"sr-filters"}>
            <label className={"sr-filter"}>
              <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M8 3v4M16 3v4M3 10h18"} /></svg>
              <b>시즌</b>
              <select id={"f-season"}>
                <option value={"all"}>전체</option>
                <option value={"시즌 7"}>시즌 7</option>
                <option value={"시즌 6"}>시즌 6</option>
                <option value={"시즌 5"}>시즌 5</option>
                <option value={"시즌 4"}>시즌 4</option>
                <option value={"시즌 3"}>시즌 3</option>
              </select>
            </label>
            <label className={"sr-filter"}>
              <b>난이도</b>
              <select id={"f-level"}>
                <option value={"all"}>전체</option>
                <option value={"쉬움"}>쉬움</option>
                <option value={"보통"}>보통</option>
                <option value={"어려움"}>어려움</option>
              </select>
            </label>
            <label className={"sr-filter"}>
              <b>수익률</b>
              <select id={"f-return"}>
                <option value={"all"}>전체</option>
                <option value={"plus"}>플러스</option>
                <option value={"minus"}>마이너스</option>
              </select>
            </label>
          </div>

          {/* 상단 3분할 */}
          <section className={"sr-top"}>

            {/* 최근 완료 */}
            <article className={"sr-card sr-best"}>
              <div className={"sr-best-head"}><h2>최근 완료 모의 투자</h2><span className={"sr-tag"}>BEST</span></div>
              <div className={"sr-best-body"}>
                <svg className={"sr-trophy"} viewBox={"0 0 150 150"} fill={"none"} aria-hidden={"true"}>
                  <defs>
                    <linearGradient id={"trCup"} x1={"0"} y1={"0"} x2={"0"} y2={"1"}><stop offset={"0"} stopColor={"#a29bfa"} /><stop offset={"1"} stopColor={"#5f57e0"} /></linearGradient>
                    <radialGradient id={"trHalo"} cx={".5"} cy={".5"} r={".5"}><stop offset={"0"} stopColor={"#dedcfb"} /><stop offset={"1"} stopColor={"#eeedfd"} /></radialGradient>
                  </defs>
                  <circle cx={"75"} cy={"75"} r={"66"} fill={"url(#trHalo)"} />
                  <circle cx={"75"} cy={"75"} r={"66"} fill={"none"} stroke={"#fff"} strokeWidth={"4"} />
                  <path d={"M46 42h58v22a29 29 0 0 1-58 0z"} fill={"url(#trCup)"} />
                  <path d={"M46 46H33v6a17 17 0 0 0 15 16.9M104 46h13v6a17 17 0 0 1-15 16.9"} stroke={"#8e86ef"} strokeWidth={"6"} strokeLinecap={"round"} fill={"none"} />
                  <rect x={"67"} y={"92"} width={"16"} height={"16"} fill={"#6f66ec"} />
                  <path d={"M52 108h46l6 14H46z"} fill={"#2f2a6b"} />
                  <path d={"m75 52 3.6 7.4 8 1.2-5.8 5.7 1.4 8.1-7.2-3.8-7.2 3.8 1.4-8.1-5.8-5.7 8-1.2z"} fill={"#fff"} opacity={".92"} />
                  <path d={"M30 58c-6 12-4 26 5 35"} stroke={"#c9c5f8"} strokeWidth={"4"} strokeLinecap={"round"} fill={"none"} />
                  <path d={"M120 58c6 12 4 26-5 35"} stroke={"#c9c5f8"} strokeWidth={"4"} strokeLinecap={"round"} fill={"none"} />
                  <path d={"m112 30 2 5.4 5.4 2-5.4 2-2 5.4-2-5.4-5.4-2 5.4-2z"} fill={"#fff"} />
                </svg>

                <div className={"sr-best-main"}>
                  <b>시즌 7 · 2008 금융위기</b>
                  <p className={"sr-return"}>+25.3%</p>
                  <p className={"sr-return-label"}>최종 수익률</p>
                  <dl className={"sr-best-stats"}>
                    <div><dt>최종 점수</dt><dd>12,530점</dd></div>
                    <div><dt>최종 순위</dt><dd>상위 18% 🏆</dd></div>
                  </dl>
                  <a className={"sr-ai"} href={"#"}>
                    <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"4"} y={"8"} width={"16"} height={"12"} rx={"3"} /><path d={"M12 4v4M9 14v1M15 14v1M2 13v2M22 13v2"} /></svg>
                    AI 복기 보기 ✦
                  </a>
                </div>
              </div>
              <p className={"sr-best-foot"}>
                <span>완료일 2025.06.02</span><i>|</i><span>기간 20영업일</span><i>|</i><span>난이도 보통</span>
              </p>
            </article>

            {/* 수익률 추이 */}
            <article className={"sr-card sr-trend"}>
              <h2>수익률 추이 <small>(최근 10회)</small></h2>
              <p className={"sr-legend"}>
                <span><i></i>나의 수익률</span>
                <span><i className={"dash"}></i>벤치마크</span>
              </p>
              <svg id={"sr-chart"} viewBox={"0 0 620 300"} role={"img"} aria-label={"최근 10회 수익률 추이"}></svg>
            </article>

            {/* 획득 배지 */}
            <article className={"sr-card sr-badges"}>
              <div className={"sr-badges-head"}><h2>획득 배지</h2><span>전체 8개</span></div>
              <div className={"sr-badge-grid"}>
                <div className={"sr-badge"}>
                  <svg width={"66"} height={"66"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}>
                    <path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#f0951f"} />
                    <path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#ffb547"} />
                    <path d={"M36 24a5 5 0 1 1 0-10 5 5 0 0 1 0 10zM26 48l6-14h8l6 14-10-6z"} fill={"#fff"} />
                  </svg>
                  <b>위기 극복</b><small>Lv.2</small>
                </div>
                <div className={"sr-badge"}>
                  <svg width={"66"} height={"66"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}>
                    <path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#7b28d8"} />
                    <path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#a855f7"} />
                    <path d={"M36 18c4 6 10 8 10 16a10 10 0 0 1-20 0c0-8 6-10 10-16z"} fill={"#fff"} />
                    <path d={"M28 52h16"} stroke={"#fff"} strokeWidth={"4"} strokeLinecap={"round"} />
                  </svg>
                  <b>장기 투자자</b><small>Lv.1</small>
                </div>
                <div className={"sr-badge"}>
                  <svg width={"66"} height={"66"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}>
                    <path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#1f7fd8"} />
                    <path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#4aa8f5"} />
                    <path d={"M24 44l8-8 6 6 10-12"} stroke={"#fff"} strokeWidth={"5"} strokeLinecap={"round"} strokeLinejoin={"round"} fill={"none"} />
                    <path d={"M44 30h6v6"} stroke={"#fff"} strokeWidth={"5"} strokeLinecap={"round"} strokeLinejoin={"round"} fill={"none"} />
                  </svg>
                  <b>수익 달성</b><small>Lv.3</small>
                </div>
                <div className={"sr-badge locked"}>
                  <svg width={"66"} height={"66"} viewBox={"0 0 72 72"} fill={"none"} aria-hidden={"true"}>
                    <path d={"M36 4 62 19v34L36 68 10 53V19z"} fill={"#e8eaf1"} />
                    <path d={"M36 10 57 22v28L36 62 15 50V22z"} fill={"#f4f5f9"} />
                    <rect x={"28"} y={"34"} width={"16"} height={"13"} rx={"2.5"} fill={"#b7bdcb"} />
                    <path d={"M31 34v-4a5 5 0 0 1 10 0v4"} stroke={"#b7bdcb"} strokeWidth={"3.4"} fill={"none"} />
                  </svg>
                  <b>연승 달성</b><small>잠김</small>
                </div>
              </div>
              <a className={"sr-badge-all"} href={"#"}>모든 배지 보기 →</a>
            </article>
          </section>

          {/* 히스토리 */}
          <section className={"sr-history"}>
            <h2>모의 투자 히스토리</h2>
            <div className={"sr-table-wrap"}>
              <table className={"sr-table"}>
                <thead>
                  <tr><th>시나리오</th><th>시작일</th><th>기간</th><th>최종 수익률</th><th>최종 점수</th><th>등급</th><th>액션</th></tr>
                </thead>
                <tbody id={"sr-rows"}>

                  <tr data-season={"시즌 7"} data-level={"보통"} data-return={"25.3"}>
                    <td><span className={"sr-scenario"}>
                      <span className={"sr-thumb"}><svg viewBox={"0 0 44 44"} fill={"none"}><rect width={"44"} height={"44"} fill={"#243056"} /><path d={"M3 30h38v14H3z"} fill={"#161e39"} /><g fill={"#3a4a7c"}><rect x={"7"} y={"18"} width={"6"} height={"12"} /><rect x={"17"} y={"12"} width={"6"} height={"18"} /><rect x={"27"} y={"20"} width={"6"} height={"10"} /></g><path d={"M4 12l9 8 8-5 10 10"} stroke={"#e0455a"} strokeWidth={"2"} fill={"none"} /></svg></span>
                      시즌 7 · 2008 금융위기
                    </span></td>
                    <td className={"num"}>2025.06.02</td><td className={"num"}>20영업일</td>
                    <td className={"num rise"}>+25.3%</td><td className={"num"}>12,530점</td>
                    <td><span className={"sr-grade"} style={{ background: '#7b6cf0', clipPath: 'polygon(50% 0,100% 25%,100% 75%,50% 100%,0 75%,0 25%)' }}>A</span></td>
                    <td><span className={"sr-actions"}>
                      <a className={"sr-act brand"} href={"#"}>복기 보기</a>
                      <a className={"sr-act"} href={"/sim/play"}>다시 보기</a>
                      <button className={"sr-kebab"} type={"button"} aria-label={"더 보기"}>⋮</button>
                    </span></td>
                  </tr>

                  <tr data-season={"시즌 6"} data-level={"어려움"} data-return={"-8.6"}>
                    <td><span className={"sr-scenario"}>
                      <span className={"sr-thumb"}><svg viewBox={"0 0 44 44"} fill={"none"}><rect width={"44"} height={"44"} fill={"#1f3350"} /><circle cx={"22"} cy={"16"} r={"7"} fill={"#4f7fd0"} opacity={".8"} /><path d={"M3 30h38v14H3z"} fill={"#152337"} /><g fill={"#33507e"}><rect x={"9"} y={"22"} width={"6"} height={"8"} /><rect x={"19"} y={"17"} width={"6"} height={"13"} /><rect x={"29"} y={"24"} width={"6"} height={"6"} /></g></svg></span>
                      시즌 6 · 2003 IT 버블
                    </span></td>
                    <td className={"num"}>2025.05.18</td><td className={"num"}>15영업일</td>
                    <td className={"num fall"}>-8.6%</td><td className={"num"}>8,210점</td>
                    <td><span className={"sr-grade"} style={{ background: '#f0a72c', clipPath: 'polygon(50% 0,100% 25%,100% 75%,50% 100%,0 75%,0 25%)' }}>C</span></td>
                    <td><span className={"sr-actions"}>
                      <a className={"sr-act brand"} href={"#"}>복기 보기</a>
                      <a className={"sr-act"} href={"/sim/play"}>다시 보기</a>
                      <button className={"sr-kebab"} type={"button"} aria-label={"더 보기"}>⋮</button>
                    </span></td>
                  </tr>

                  <tr data-season={"시즌 5"} data-level={"어려움"} data-return={"32.1"}>
                    <td><span className={"sr-scenario"}>
                      <span className={"sr-thumb"}><svg viewBox={"0 0 44 44"} fill={"none"}><rect width={"44"} height={"44"} fill={"#2c2350"} /><circle cx={"30"} cy={"14"} r={"6"} fill={"#c084fc"} opacity={".85"} /><path d={"M3 30h38v14H3z"} fill={"#1c1738"} /><g fill={"#443a72"}><rect x={"8"} y={"20"} width={"7"} height={"10"} /><rect x={"19"} y={"14"} width={"6"} height={"16"} /><rect x={"29"} y={"23"} width={"6"} height={"7"} /></g></svg></span>
                      시즌 5 · 2020 코로나19
                    </span></td>
                    <td className={"num"}>2025.04.27</td><td className={"num"}>25영업일</td>
                    <td className={"num rise"}>+32.1%</td><td className={"num"}>13,840점</td>
                    <td><span className={"sr-grade"} style={{ background: '#e0a41c', clipPath: 'polygon(50% 0,100% 25%,100% 75%,50% 100%,0 75%,0 25%)' }}>S</span></td>
                    <td><span className={"sr-actions"}>
                      <a className={"sr-act brand"} href={"#"}>복기 보기</a>
                      <a className={"sr-act"} href={"/sim/play"}>다시 보기</a>
                      <button className={"sr-kebab"} type={"button"} aria-label={"더 보기"}>⋮</button>
                    </span></td>
                  </tr>

                  <tr data-season={"시즌 4"} data-level={"보통"} data-return={"12.7"}>
                    <td><span className={"sr-scenario"}>
                      <span className={"sr-thumb"}><svg viewBox={"0 0 44 44"} fill={"none"}><rect width={"44"} height={"44"} fill={"#4a2a3a"} /><circle cx={"14"} cy={"15"} r={"6"} fill={"#f0a72c"} opacity={".85"} /><path d={"M3 30h38v14H3z"} fill={"#301c28"} /><g fill={"#6a3d52"}><rect x={"10"} y={"21"} width={"6"} height={"9"} /><rect x={"20"} y={"16"} width={"7"} height={"14"} /><rect x={"31"} y={"23"} width={"6"} height={"7"} /></g></svg></span>
                      시즌 4 · 2015 중국 증시 변동
                    </span></td>
                    <td className={"num"}>2025.04.05</td><td className={"num"}>18영업일</td>
                    <td className={"num rise"}>+12.7%</td><td className={"num"}>9,430점</td>
                    <td><span className={"sr-grade"} style={{ background: '#4a7bf7', clipPath: 'polygon(50% 0,100% 25%,100% 75%,50% 100%,0 75%,0 25%)' }}>B</span></td>
                    <td><span className={"sr-actions"}>
                      <a className={"sr-act brand"} href={"#"}>복기 보기</a>
                      <a className={"sr-act"} href={"/sim/play"}>다시 보기</a>
                      <button className={"sr-kebab"} type={"button"} aria-label={"더 보기"}>⋮</button>
                    </span></td>
                  </tr>

                  <tr data-season={"시즌 3"} data-level={"쉬움"} data-return={"-3.2"}>
                    <td><span className={"sr-scenario"}>
                      <span className={"sr-thumb"}><svg viewBox={"0 0 44 44"} fill={"none"}><rect width={"44"} height={"44"} fill={"#3a2c4e"} /><circle cx={"33"} cy={"13"} r={"5"} fill={"#e0a4f7"} opacity={".8"} /><path d={"M3 30h38v14H3z"} fill={"#251c33"} /><g fill={"#54406e"}><rect x={"9"} y={"19"} width={"6"} height={"11"} /><rect x={"18"} y={"23"} width={"6"} height={"7"} /><rect x={"28"} y={"18"} width={"7"} height={"12"} /></g></svg></span>
                      시즌 3 · 2011 유럽 재정위기
                    </span></td>
                    <td className={"num"}>2025.03.16</td><td className={"num"}>16영업일</td>
                    <td className={"num fall"}>-3.2%</td><td className={"num"}>7,650점</td>
                    <td><span className={"sr-grade"} style={{ background: '#98a0b6', clipPath: 'polygon(50% 0,100% 25%,100% 75%,50% 100%,0 75%,0 25%)' }}>D</span></td>
                    <td><span className={"sr-actions"}>
                      <a className={"sr-act"} href={"/sim/play"}>다시 보기</a>
                      <button className={"sr-kebab"} type={"button"} aria-label={"더 보기"}>⋮</button>
                    </span></td>
                  </tr>

                </tbody>
              </table>
              <p className={"sr-empty"} id={"sr-empty"} hidden>조건에 맞는 기록이 없습니다.</p>
              <button className={"sr-more"} id={"sr-more"} type={"button"}>더 보기 (총 <span className={"num"}>16</span>회) ↓</button>
            </div>
          </section>

        </div>
      </main>
    </>
  )
}
